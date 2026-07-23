package transfer

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"

	"lukechampine.com/blake3/bao"
)

// Per-chunk verification (BLAKE3/Bao). The host computes a bao root and an
// "outboard" Merkle tree for each file at create time; joiners fetch the
// (small) outboard once and then verify every chunk group against the
// 32-byte root as it arrives — so bytes can be accepted from any untrusted
// peer, not just the host, without waiting for the whole-file hash at the
// end. Verification happens before a byte is written to disk or marked
// received, so the stream server can never serve unverified data.
//
// bao.VerifyChunk requires the verified range to start on a chunk-group
// boundary and cover whole groups (only the file's final group may be
// short), so every range the downloader requests is group-aligned.

// baoGroupLog is the chunk-group size exponent the host encodes with:
// log2 chunks per group, chunks being BLAKE3's fixed 1 KiB leaves. 8 ⇒
// 256 KiB groups, making the outboard ~1/4096 of the file (≈5 MB for a
// 20 GB file) at a verification granularity well below the transfer
// window. This is a wire-level constant: both sides derive group byte
// math from the announced FileMeta value, but the host always writes 8.
const baoGroupLog = 8

// verifyError marks a failure attributable to the sending peer: bytes
// that don't match the announced root, or protocol violations only a
// broken or hostile sender produces (unaligned offsets, data past EOF).
// Callers use it to distinguish "this peer can't be trusted" (strike it,
// or drop it as a swarm source) from local IO errors, which are terminal.
type verifyError struct{ msg string }

func (e verifyError) Error() string { return e.msg }

// errObUnavailable is a peer's nak to an outboard request: it doesn't
// have the tree (yet). Retryable — a promoted host may still be computing
// it, or another peer may have it.
var errObUnavailable = fmt.Errorf("peer doesn't have the verification tree")

// baoParams carries everything needed to verify one file's bytes against
// its announced bao root.
type baoParams struct {
	root       [32]byte
	group      int   // log2 chunks per group, from FileMeta
	groupBytes int64 // 1024 << group
	obSHA      [32]byte
	obLen      int   // exact outboard byte length, computed — never on the wire
	size       int64 // file size, from FileMeta
}

// newBaoParams validates and decodes the wire-announced verification
// fields. rootHex and obSHAHex are 64-char hex strings (32 bytes each).
func newBaoParams(rootHex string, group int, obSHAHex string, size int64) (baoParams, error) {
	var p baoParams
	if group < 0 || group > 10 {
		return p, fmt.Errorf("bao group %d out of range", group)
	}
	if size < 0 {
		return p, fmt.Errorf("negative file size %d", size)
	}
	root, err := hex.DecodeString(rootHex)
	if err != nil || len(root) != 32 {
		return p, fmt.Errorf("bad bao root %q", rootHex)
	}
	obSHA, err := hex.DecodeString(obSHAHex)
	if err != nil || len(obSHA) != 32 {
		return p, fmt.Errorf("bad outboard hash %q", obSHAHex)
	}
	p.group = group
	p.groupBytes = int64(1024) << group
	p.size = size
	p.obLen = bao.EncodedSize(int(size), group, true)
	copy(p.root[:], root)
	copy(p.obSHA[:], obSHA)
	return p, nil
}

// verifyOutboard checks a fetched outboard blob against the announced
// length and hash, so the tree itself can be fetched from any peer.
func verifyOutboard(ob []byte, p baoParams) bool {
	return len(ob) == p.obLen && sha256.Sum256(ob) == p.obSHA
}

// alignDown rounds x down to a multiple of g. alignUp rounds x up,
// additionally clamping to size (the file's final group is short).
func alignDown(x, g int64) int64 { return x - x%g }

func alignUp(x, g, size int64) int64 {
	if r := x % g; r != 0 {
		x += g - r
	}
	if x > size {
		return size
	}
	return x
}

// groupVerifier accumulates in-order bytes for one connection and releases
// them a verified chunk group at a time. It is touched only from the data
// channel's OnMessage goroutine, so it needs no lock. base is always
// group-aligned; reset (an "at" frame) discards any partial group so a
// redirect can never splice bytes from two ranges into one group.
type groupVerifier struct {
	p        baoParams
	outboard []byte
	buf      []byte // < groupBytes of not-yet-verifiable bytes
	base     int64  // absolute offset of buf[0]
}

func newGroupVerifier(p baoParams, outboard []byte) *groupVerifier {
	return &groupVerifier{p: p, outboard: outboard, base: -1}
}

// reset repositions the verifier at a new absolute offset, discarding any
// partial group accumulated so far (the gap it covered stays unmarked and
// is simply re-fetched). It returns how many bytes were discarded (for
// logging) and rejects an unaligned offset — senders only ever answer
// group-aligned requests, so misalignment means a broken or hostile peer.
func (v *groupVerifier) reset(at int64) (discarded int, err error) {
	if at%v.p.groupBytes != 0 {
		return 0, verifyError{fmt.Sprintf("range starts at %d, not on a %d-byte verification boundary", at, v.p.groupBytes)}
	}
	if at < 0 || at > v.p.size {
		return 0, verifyError{fmt.Sprintf("range starts at %d, outside file of %d bytes", at, v.p.size)}
	}
	discarded = len(v.buf)
	v.buf = v.buf[:0]
	v.base = at
	return discarded, nil
}

// feed consumes bytes arriving in order at the current position and calls
// emit once per fully verified group with its absolute offset. The data
// slice passed to emit is only valid for the duration of the call. A
// verification failure is terminal for the connection: the bytes never
// reach emit, and the caller must drop the peer.
func (v *groupVerifier) feed(b []byte, emit func(off int64, data []byte) error) error {
	if v.base < 0 {
		return verifyError{"data before any range offset was announced"}
	}
	if v.base+int64(len(v.buf))+int64(len(b)) > v.p.size {
		return verifyError{"data past end of file"}
	}
	v.buf = append(v.buf, b...)
	g := int(v.p.groupBytes)
	for len(v.buf) >= g || v.finalShortGroup() {
		n := g
		if len(v.buf) < g {
			n = len(v.buf) // the file's final, legitimately short group
		}
		if !bao.VerifyChunk(v.buf[:n], v.outboard, v.p.group, uint64(v.base), v.p.root) {
			return verifyError{fmt.Sprintf("bytes at %d failed verification against the announced hash", v.base)}
		}
		if err := emit(v.base, v.buf[:n]); err != nil {
			return err
		}
		v.base += int64(n)
		v.buf = append(v.buf[:0], v.buf[n:]...)
	}
	return nil
}

// pending reports how many accumulated bytes are still waiting for their
// group to complete (they will be discarded if the range ends here).
func (v *groupVerifier) pending() int { return len(v.buf) }

// finalShortGroup reports whether the buffered bytes are the file's last,
// short group — complete by reaching end of file rather than group size.
func (v *groupVerifier) finalShortGroup() bool {
	return len(v.buf) > 0 && v.base+int64(len(v.buf)) == v.p.size
}

// hashAndBao computes a file's whole-file SHA-256, bao root, and outboard
// tree in one sequential read. The outboard is returned in memory (it is
// ~1/4096 of the file at baoGroupLog=8) for the host to serve to joiners.
func hashAndBao(path string) (sha string, root [32]byte, outboard []byte, err error) {
	f, err := os.Open(path)
	if err != nil {
		return "", root, nil, err
	}
	defer f.Close()
	fi, err := f.Stat()
	if err != nil {
		return "", root, nil, err
	}
	size := fi.Size()
	h := sha256.New()
	outboard = make([]byte, bao.EncodedSize(int(size), baoGroupLog, true))
	root, err = bao.Encode(&bufWriterAt{buf: outboard}, io.TeeReader(f, h), size, baoGroupLog, true)
	if err != nil {
		return "", root, nil, err
	}
	return hex.EncodeToString(h.Sum(nil)), root, outboard, nil
}

// FileHashes is everything a host announces (and serves) so joiners can
// verify its bytes: the legacy whole-file SHA-256, the bao root and group
// for FileMeta, the outboard tree's own hash, and the tree itself.
type FileHashes struct {
	SHA256   string
	BaoRoot  string // hex, for FileMeta.Bao
	BaoGroup int    // for FileMeta.BaoGroup
	BaoObSHA string // hex sha256 of Outboard, for FileMeta.BaoOb
	Outboard []byte
}

// HashForServe computes all of a file's announce-time hashes in one
// sequential read (the SHA-256 pass the host already paid for now also
// yields the verification tree).
func HashForServe(path string) (FileHashes, error) {
	sha, root, ob, err := hashAndBao(path)
	if err != nil {
		return FileHashes{}, err
	}
	obSHA := sha256.Sum256(ob)
	return FileHashes{
		SHA256:   sha,
		BaoRoot:  hex.EncodeToString(root[:]),
		BaoGroup: baoGroupLog,
		BaoObSHA: hex.EncodeToString(obSHA[:]),
		Outboard: ob,
	}, nil
}

// OutboardForRoot recomputes a file's outboard tree and checks it against
// the announced root — how a promoted host that received the playlist
// (rather than minting it) obtains a servable tree from its local copy.
// A mismatch means the local file differs from what the room's metadata
// promises, so there is no valid tree to serve.
func OutboardForRoot(path, rootHex string) ([]byte, error) {
	_, root, ob, err := hashAndBao(path)
	if err != nil {
		return nil, err
	}
	if hex.EncodeToString(root[:]) != rootHex {
		return nil, fmt.Errorf("local file doesn't match the room's announced verification root")
	}
	return ob, nil
}

// bufWriterAt adapts a pre-sized []byte to io.WriterAt for bao.Encode,
// which writes the tree non-sequentially.
type bufWriterAt struct{ buf []byte }

func (w *bufWriterAt) WriteAt(p []byte, off int64) (int, error) {
	if off < 0 || off+int64(len(p)) > int64(len(w.buf)) {
		return 0, fmt.Errorf("write at %d+%d outside buffer of %d bytes", off, len(p), len(w.buf))
	}
	return copy(w.buf[off:], p), nil
}
