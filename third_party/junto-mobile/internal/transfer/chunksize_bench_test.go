package transfer

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// runTransferOnce transfers a file of the given size once over a real
// loopback WebRTC connection and returns the elapsed time.
func runTransferOnce(t *testing.B, size int64) time.Duration {
	src := filepath.Join(t.TempDir(), "media.bin")
	data := make([]byte, size)
	rand.New(rand.NewSource(1)).Read(data)
	if err := os.WriteFile(src, data, 0o644); err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(data)
	meta := protocol.FileMeta{Name: "media.bin", Size: size, SHA256: hex.EncodeToString(sum[:])}

	hostSig, joinSig := newSignalerPair("host", "joiner")
	outDir := t.TempDir()
	ctx, cancel := context.WithTimeout(context.Background(), 120*time.Second)
	defer cancel()

	serveErr := make(chan error, 1)
	start := time.Now()
	go func() {
		serveErr <- ServeFile(ctx, hostSig, "joiner", src, noICE(), func(string, ...any) {}, nil, nil, nil, nil, 0)
	}()

	if _, err := FetchFile(ctx, joinSig, "host", meta, outDir, noICE(), func(string, ...any) {}); err != nil {
		t.Fatalf("FetchFile: %v", err)
	}
	elapsed := time.Since(start)
	if err := <-serveErr; err != nil {
		t.Fatalf("ServeFile: %v", err)
	}
	return elapsed
}

// BenchmarkChunkSize is the "throughput tuning pass" measured experiment
// from the roadmap: does raising chunkSize (16 KiB) reduce per-message
// overhead enough to matter? Measured on real loopback WebRTC (where
// throughput is bound by CPU/per-message overhead, not network capacity,
// making this the best case for a chunk-size win to show up): 16/32/64/
// 128 KiB all land in the same 66-69 MB/s band, and which one comes out
// "fastest" flips between repeated runs — the apparent few-percent gaps
// are run-to-run noise, not a real effect. Conclusion: chunkSize stays at
// 16 KiB. (chunkSize is a var, not a const, solely so this benchmark can
// vary it — production never changes it.)
func BenchmarkChunkSize(b *testing.B) {
	const size = 256 << 20 // 256 MiB
	for _, cs := range []int{16 * 1024, 32 * 1024, 64 * 1024, 128 * 1024} {
		b.Run(fmt.Sprintf("%dKiB", cs/1024), func(b *testing.B) {
			savedChunk := chunkSize
			chunkSize = cs
			defer func() { chunkSize = savedChunk }()

			var total time.Duration
			runs := 5
			for i := 0; i < runs; i++ {
				total += runTransferOnce(b, size)
			}
			avg := total / time.Duration(runs)
			mbps := float64(size) / avg.Seconds() / (1 << 20)
			b.ReportMetric(mbps, "MB/s")
			b.Logf("chunk=%dKiB avg=%v throughput=%.1f MB/s (%.1f Mbps)", cs/1024, avg, mbps, mbps*8)
		})
	}
}
