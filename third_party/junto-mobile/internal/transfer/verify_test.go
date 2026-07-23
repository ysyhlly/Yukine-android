package transfer

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"lukechampine.com/blake3/bao"
)

// testBaoData builds deterministic pseudo-random content, its bao
// encoding at the given group, and matching baoParams. Small groups keep
// the tests fast; the verifier only ever reads group math from params.
func testBaoData(t *testing.T, size int64, group int) ([]byte, []byte, baoParams) {
	t.Helper()
	data := make([]byte, size)
	x := uint32(2463534242)
	for i := range data {
		x ^= x << 13
		x ^= x >> 17
		x ^= x << 5
		data[i] = byte(x)
	}
	ob, root := bao.EncodeBuf(data, group, true)
	p := baoParams{
		root:       root,
		group:      group,
		groupBytes: int64(1024) << group,
		obSHA:      sha256.Sum256(ob),
		obLen:      len(ob),
		size:       size,
	}
	return data, ob, p
}

// feedAll pushes data[from:to] through the verifier in fragments of the
// given size (mimicking data-channel messages that need not align with
// group boundaries) and returns the verified bytes keyed by offset.
func feedAll(t *testing.T, v *groupVerifier, data []byte, from, to int64, frag int) map[int64][]byte {
	t.Helper()
	got := map[int64][]byte{}
	for off := from; off < to; off += int64(frag) {
		end := off + int64(frag)
		if end > to {
			end = to
		}
		err := v.feed(data[off:end], func(o int64, b []byte) error {
			got[o] = append([]byte(nil), b...)
			return nil
		})
		if err != nil {
			t.Fatalf("feed at %d: %v", off, err)
		}
	}
	return got
}

func TestGroupVerifierRoundTrip(t *testing.T) {
	// 5.5 groups: exercises whole groups and the final short group, fed
	// in fragments that straddle group boundaries.
	const group = 0 // 1 KiB groups
	size := int64(5*1024 + 512)
	data, ob, p := testBaoData(t, size, group)

	v := newGroupVerifier(p, ob)
	if _, err := v.reset(0); err != nil {
		t.Fatalf("reset(0): %v", err)
	}
	got := feedAll(t, v, data, 0, size, 100)

	var covered int64
	for off, b := range got {
		if !bytes.Equal(b, data[off:off+int64(len(b))]) {
			t.Fatalf("verified bytes at %d differ from source", off)
		}
		covered += int64(len(b))
	}
	if covered != size {
		t.Fatalf("verified %d bytes, want %d", covered, size)
	}
	if v.pending() != 0 {
		t.Fatalf("pending = %d after full feed, want 0", v.pending())
	}
}

func TestGroupVerifierMidFileRange(t *testing.T) {
	// A range fetched from the middle of the file (group-aligned), like a
	// seek redirect or a swarm piece.
	const group = 0
	size := int64(8 * 1024)
	data, ob, p := testBaoData(t, size, group)

	v := newGroupVerifier(p, ob)
	if _, err := v.reset(3 * 1024); err != nil {
		t.Fatalf("reset: %v", err)
	}
	got := feedAll(t, v, data, 3*1024, 6*1024, 333)
	if len(got) != 3 {
		t.Fatalf("verified %d groups, want 3", len(got))
	}
	for _, off := range []int64{3 * 1024, 4 * 1024, 5 * 1024} {
		if _, ok := got[off]; !ok {
			t.Fatalf("group at %d not verified", off)
		}
	}
}

func TestGroupVerifierFinalShortGroupAlone(t *testing.T) {
	const group = 0
	size := int64(4*1024 + 200)
	data, ob, p := testBaoData(t, size, group)

	v := newGroupVerifier(p, ob)
	if _, err := v.reset(4 * 1024); err != nil {
		t.Fatalf("reset: %v", err)
	}
	got := feedAll(t, v, data, 4*1024, size, 64)
	if len(got) != 1 || len(got[4*1024]) != 200 {
		t.Fatalf("final short group not verified: %v", len(got))
	}
}

func TestGroupVerifierRejectsUnalignedReset(t *testing.T) {
	_, ob, p := testBaoData(t, 4*1024, 0)
	v := newGroupVerifier(p, ob)
	if _, err := v.reset(513); err == nil {
		t.Fatal("reset(513) accepted an unaligned offset")
	}
	if _, err := v.reset(-1024); err == nil {
		t.Fatal("reset(-1024) accepted a negative offset")
	}
	if _, err := v.reset(8 * 1024); err == nil {
		t.Fatal("reset past EOF accepted")
	}
}

func TestGroupVerifierRejectsDataBeforeReset(t *testing.T) {
	_, ob, p := testBaoData(t, 4*1024, 0)
	v := newGroupVerifier(p, ob)
	err := v.feed([]byte{1, 2, 3}, func(int64, []byte) error { return nil })
	if err == nil {
		t.Fatal("feed before reset accepted data")
	}
}

func TestGroupVerifierRejectsDataPastEOF(t *testing.T) {
	data, ob, p := testBaoData(t, 2*1024, 0)
	v := newGroupVerifier(p, ob)
	if _, err := v.reset(1024); err != nil {
		t.Fatal(err)
	}
	err := v.feed(append(append([]byte{}, data[1024:]...), 0xff), func(int64, []byte) error { return nil })
	if err == nil {
		t.Fatal("feed past EOF accepted data")
	}
}

func TestGroupVerifierDetectsCorruptByte(t *testing.T) {
	const group = 0
	size := int64(4 * 1024)
	data, ob, p := testBaoData(t, size, group)

	corrupt := append([]byte(nil), data...)
	corrupt[1500] ^= 0x01 // inside the second group

	v := newGroupVerifier(p, ob)
	if _, err := v.reset(0); err != nil {
		t.Fatal(err)
	}
	var emitted []int64
	var feedErr error
	for off := int64(0); off < size && feedErr == nil; off += 256 {
		feedErr = v.feed(corrupt[off:off+256], func(o int64, b []byte) error {
			emitted = append(emitted, o)
			return nil
		})
	}
	if feedErr == nil {
		t.Fatal("corrupt byte passed verification")
	}
	for _, o := range emitted {
		if o == 1024 {
			t.Fatal("corrupt group was emitted")
		}
	}
	// The clean first group must still have made it through.
	if len(emitted) != 1 || emitted[0] != 0 {
		t.Fatalf("emitted %v, want just the clean group at 0", emitted)
	}
}

func TestGroupVerifierDetectsCorruptOutboard(t *testing.T) {
	data, ob, p := testBaoData(t, 4*1024, 0)
	bad := append([]byte(nil), ob...)
	bad[len(bad)-1] ^= 0x01 // a parent-node byte

	v := newGroupVerifier(p, bad)
	if _, err := v.reset(0); err != nil {
		t.Fatal(err)
	}
	err := v.feed(data, func(int64, []byte) error { return nil })
	if err == nil {
		t.Fatal("forged outboard passed verification")
	}
}

func TestGroupVerifierResetDiscardsPartial(t *testing.T) {
	data, ob, p := testBaoData(t, 4*1024, 0)
	v := newGroupVerifier(p, ob)
	if _, err := v.reset(0); err != nil {
		t.Fatal(err)
	}
	if err := v.feed(data[:700], func(int64, []byte) error { return nil }); err != nil {
		t.Fatal(err)
	}
	discarded, err := v.reset(2 * 1024)
	if err != nil {
		t.Fatal(err)
	}
	if discarded != 700 {
		t.Fatalf("discarded = %d, want 700", discarded)
	}
	got := feedAll(t, v, data, 2*1024, 3*1024, 128)
	if len(got) != 1 {
		t.Fatalf("post-reset group not verified")
	}
}

func TestGroupVerifierEmitErrorPropagates(t *testing.T) {
	data, ob, p := testBaoData(t, 2*1024, 0)
	v := newGroupVerifier(p, ob)
	if _, err := v.reset(0); err != nil {
		t.Fatal(err)
	}
	want := fmt.Errorf("disk full")
	err := v.feed(data[:1024], func(int64, []byte) error { return want })
	if err != want {
		t.Fatalf("emit error not propagated: %v", err)
	}
}

func TestVerifyOutboard(t *testing.T) {
	_, ob, p := testBaoData(t, 4*1024, 0)
	if !verifyOutboard(ob, p) {
		t.Fatal("genuine outboard rejected")
	}
	bad := append([]byte(nil), ob...)
	bad[3] ^= 0xff
	if verifyOutboard(bad, p) {
		t.Fatal("tampered outboard accepted")
	}
	if verifyOutboard(ob[:len(ob)-1], p) {
		t.Fatal("truncated outboard accepted")
	}
}

func TestNewBaoParams(t *testing.T) {
	root := hex.EncodeToString(make([]byte, 32))
	obsha := hex.EncodeToString(make([]byte, 32))
	p, err := newBaoParams(root, 8, obsha, 20<<30)
	if err != nil {
		t.Fatalf("valid params rejected: %v", err)
	}
	if p.groupBytes != 256*1024 {
		t.Fatalf("groupBytes = %d, want 256 KiB", p.groupBytes)
	}
	if p.obLen != bao.EncodedSize(20<<30, 8, true) {
		t.Fatalf("obLen = %d", p.obLen)
	}
	for _, bad := range []struct {
		root, ob string
		group    int
		size     int64
	}{
		{"zz", obsha, 8, 1},      // non-hex root
		{root[:62], obsha, 8, 1}, // short root
		{root, "zz", 8, 1},       // non-hex outboard hash
		{root, obsha[:10], 8, 1}, // short outboard hash
		{root, obsha, -1, 1},     // negative group
		{root, obsha, 11, 1},     // oversized group
		{root, obsha, 8, -5},     // negative size
	} {
		if _, err := newBaoParams(bad.root, bad.group, bad.ob, bad.size); err == nil {
			t.Fatalf("accepted bad params %+v", bad)
		}
	}
}

func TestAlignHelpers(t *testing.T) {
	if got := alignDown(1000, 256); got != 768 {
		t.Fatalf("alignDown = %d", got)
	}
	if got := alignDown(768, 256); got != 768 {
		t.Fatalf("alignDown exact = %d", got)
	}
	if got := alignUp(1000, 256, 1<<20); got != 1024 {
		t.Fatalf("alignUp = %d", got)
	}
	if got := alignUp(1024, 256, 1<<20); got != 1024 {
		t.Fatalf("alignUp exact = %d", got)
	}
	if got := alignUp(1000, 256, 1010); got != 1010 {
		t.Fatalf("alignUp clamps to size: %d", got)
	}
}

func TestHashAndBao(t *testing.T) {
	dir := t.TempDir()
	// Larger than one production group so the outboard has real parent
	// nodes, with a short final group.
	data := make([]byte, 300*1024+123)
	for i := range data {
		data[i] = byte(i * 31)
	}
	path := filepath.Join(dir, "media.bin")
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}

	sha, root, ob, err := hashAndBao(path)
	if err != nil {
		t.Fatal(err)
	}
	wantSHA := sha256.Sum256(data)
	if sha != hex.EncodeToString(wantSHA[:]) {
		t.Fatal("sha256 mismatch with direct computation")
	}
	if len(ob) != bao.EncodedSize(len(data), baoGroupLog, true) {
		t.Fatalf("outboard length %d", len(ob))
	}
	// The encoding it produced must verify: first (only) full group and
	// the final short group.
	g := 1024 << baoGroupLog
	if !bao.VerifyChunk(data[:g], ob, baoGroupLog, 0, root) {
		t.Fatal("first group failed verification against hashAndBao output")
	}
	if !bao.VerifyChunk(data[g:], ob, baoGroupLog, uint64(g), root) {
		t.Fatal("final short group failed verification against hashAndBao output")
	}
}

func TestHashAndBaoTinyFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "tiny.srt")
	if err := os.WriteFile(path, []byte("1\n00:00:01,000 --> 00:00:02,000\nhi\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	_, root, ob, err := hashAndBao(path)
	if err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(path)
	if !bao.VerifyChunk(data, ob, baoGroupLog, 0, root) {
		t.Fatal("sub-group-size file failed verification")
	}
}
