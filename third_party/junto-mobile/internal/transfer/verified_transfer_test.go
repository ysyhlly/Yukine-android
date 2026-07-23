package transfer

import (
	"context"
	"fmt"
	"io"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// baoMetaFor computes a file's real announce-time hashes, exactly as the
// host does at create.
func baoMetaFor(t *testing.T, path, name string) (protocol.FileMeta, OutboardFn) {
	t.Helper()
	h, err := HashForServe(path)
	if err != nil {
		t.Fatal(err)
	}
	fi, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	m := protocol.FileMeta{Name: name, Size: fi.Size(), SHA256: h.SHA256, Bao: h.BaoRoot, BaoGroup: h.BaoGroup, BaoOb: h.BaoObSHA}
	return m, func(context.Context) ([]byte, error) { return h.Outboard, nil }
}

// corruptSeeker flips one byte at target as it passes through Read,
// standing in for a host whose disk bytes don't match what it announced.
type corruptSeeker struct {
	f      io.ReadSeeker
	pos    int64
	target int64
}

func (c *corruptSeeker) Seek(off int64, whence int) (int64, error) {
	n, err := c.f.Seek(off, whence)
	c.pos = n
	return n, err
}

func (c *corruptSeeker) Read(p []byte) (int, error) {
	n, err := c.f.Read(p)
	if n > 0 && c.target >= c.pos && c.target < c.pos+int64(n) {
		p[c.target-c.pos] ^= 0x01
	}
	c.pos += int64(n)
	return n, err
}

// shrinkConnectTimeout tightens the offer/answer + stall budget so a test
// whose reconnect flow burns one window (the in-process host double
// re-offers unprompted, which drainSignals may discard) doesn't wait out
// the production 30s. Loopback signaling completes in milliseconds.
func shrinkConnectTimeout(t *testing.T) {
	t.Helper()
	orig := connectTimeout
	connectTimeout = 3 * time.Second
	t.Cleanup(func() { connectTimeout = orig })
}

// TestVerifiedTransferLoopback runs a real WebRTC transfer in a bao room:
// the outboard travels over the data channel, every chunk group is
// verified on arrival, and the tree is persisted as a .bao sidecar for
// later serving.
func TestVerifiedTransferLoopback(t *testing.T) {
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, (1<<20)+300*1024+789) // ~1.3 MiB: several 256 KiB groups + a short final one
	rand.New(rand.NewSource(2)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, ob := baoMetaFor(t, src, "media.bin")

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, ob, nil, 0)
	}()

	path, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), t.Logf)
	if err != nil {
		t.Fatalf("FetchFile: %v", err)
	}
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFile: %v", err)
	}

	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(data) {
		t.Fatal("received file differs from source")
	}
	if _, err := os.Stat(path + ".bao"); err != nil {
		t.Errorf("outboard sidecar not persisted: %v", err)
	}
}

// TestCorruptHostVerification pins the whole point of per-chunk
// verification: the same corrupt host that today's SHA-256-at-the-end
// path lets finish with just a warning is now caught the moment the bad
// chunk arrives, and the download fails instead of playing garbage.
func TestCorruptHostVerification(t *testing.T) {
	makeSrc := func(t *testing.T) (string, []byte) {
		src := filepath.Join(t.TempDir(), "media.bin")
		data := make([]byte, 700*1024)
		rand.New(rand.NewSource(3)).Read(data)
		if err := os.WriteFile(src, data, 0o644); err != nil {
			t.Fatal(err)
		}
		return src, data
	}
	corrupt := func(t *testing.T) func() {
		orig := wrapServeReader
		wrapServeReader = func(f *os.File) io.ReadSeeker { return &corruptSeeker{f: f, target: 300 * 1024} }
		return func() { wrapServeReader = orig }
	}

	t.Run("bao room fails hard", func(t *testing.T) {
		shrinkConnectTimeout(t)
		src, _ := makeSrc(t)
		meta, ob := baoMetaFor(t, src, "media.bin")
		defer corrupt(t)()

		hostSig, joinSig := newSignalerPair("host", "joiner")
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cancel()
		// The joiner reconnects once after the first strike; serve every
		// attempt so the second strike comes from verification, not a
		// missing host. The loop must be fully drained before the test
		// returns — it reads package vars the cleanup restores.
		serveDone := make(chan struct{})
		go func() {
			defer close(serveDone)
			for ctx.Err() == nil {
				_ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, ob, nil, 0)
			}
		}()
		defer func() { cancel(); <-serveDone }()

		path, err := FetchFile(ctx, joinSig, "host", meta, t.TempDir(), noICE(), t.Logf)
		if err == nil {
			t.Fatalf("corrupt host's transfer completed at %s; want a verification failure", path)
		}
		if !strings.Contains(err.Error(), "announced") {
			t.Fatalf("unexpected error: %v", err)
		}
	})

	t.Run("legacy room only warns (pre-swarm behavior)", func(t *testing.T) {
		src, data := makeSrc(t)
		sum, err := hashFileOnDisk(src)
		if err != nil {
			t.Fatal(err)
		}
		meta := protocol.FileMeta{Name: "media.bin", Size: int64(len(data)), SHA256: sum}
		defer corrupt(t)()

		hostSig, joinSig := newSignalerPair("host", "joiner")
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cancel()
		go func() { _ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, nil, nil, 0) }()

		pf, lines := capturePrintf()
		if _, err := FetchFile(ctx, joinSig, "host", meta, t.TempDir(), noICE(), pf); err != nil {
			t.Fatalf("legacy transfer failed outright: %v", err)
		}
		warned := false
		for _, l := range lines() {
			if strings.Contains(l, "WARNING") && strings.Contains(l, "sha256") {
				warned = true
			}
		}
		if !warned {
			t.Fatal("legacy path lost its sha256 warning")
		}
	})
}

// TestCorruptOutboardRejected: a host serving a forged verification tree
// is caught by the tree's own announced hash before any file bytes are
// accepted, and two strikes end the download.
func TestCorruptOutboardRejected(t *testing.T) {
	shrinkConnectTimeout(t)
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 400*1024)
	rand.New(rand.NewSource(4)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, _ := baoMetaFor(t, src, "media.bin")
	forged := func(context.Context) ([]byte, error) {
		h, err := HashForServe(src)
		if err != nil {
			return nil, err
		}
		h.Outboard[len(h.Outboard)-1] ^= 0xff
		return h.Outboard, nil
	}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	serveDone := make(chan struct{})
	go func() {
		defer close(serveDone)
		for ctx.Err() == nil {
			_ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, forged, nil, 0)
		}
	}()
	defer func() { cancel(); <-serveDone }()

	_, err := FetchFile(ctx, joinSig, "host", meta, t.TempDir(), noICE(), t.Logf)
	if err == nil {
		t.Fatal("forged outboard was accepted")
	}
	if !strings.Contains(err.Error(), "announced") {
		t.Fatalf("unexpected error: %v", err)
	}
}

// TestOutboardNakRetries: a peer that doesn't have the tree yet (a
// promoted host still computing it) naks the request; the joiner treats
// that as retryable and succeeds once the tree becomes available.
func TestOutboardNakRetries(t *testing.T) {
	shrinkConnectTimeout(t)
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, 300*1024)
	rand.New(rand.NewSource(5)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	meta, real := baoMetaFor(t, src, "media.bin")

	var calls int
	flaky := func(ctx context.Context) ([]byte, error) {
		calls++
		if calls == 1 {
			return nil, fmt.Errorf("still computing")
		}
		return real(ctx)
	}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	serveDone := make(chan struct{})
	go func() {
		defer close(serveDone)
		for ctx.Err() == nil {
			_ = ServeFile(ctx, hostSig, "joiner", src, noICE(), t.Logf, nil, nil, flaky, nil, 0)
		}
	}()
	defer func() { cancel(); <-serveDone }()

	path, err := FetchFile(ctx, joinSig, "host", meta, t.TempDir(), noICE(), t.Logf)
	if err != nil {
		t.Fatalf("nak wasn't retried: %v", err)
	}
	got, _ := os.ReadFile(path)
	if string(got) != string(data) {
		t.Fatal("received file differs from source")
	}
}
