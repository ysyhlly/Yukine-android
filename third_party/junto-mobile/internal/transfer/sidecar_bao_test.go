package transfer

import (
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// TestLoadProgressBaoStaleness pins the resume rules across verification
// regimes: spans recorded under one bao root (or none) must never be
// trusted in a room announcing a different one — a pre-swarm session's
// sidecar can claim bytes that were never per-chunk verified, and its
// spans need not be group-aligned. Matching regimes keep resuming as
// before.
func TestLoadProgressBaoStaleness(t *testing.T) {
	const size = 100
	root := strings.Repeat("ab", 32)
	otherRoot := strings.Repeat("ef", 32)

	newEntry := func(t *testing.T, meta protocol.FileMeta, sidecar string) *fileEntry {
		dir := t.TempDir()
		e := &fileEntry{meta: meta, partPath: filepath.Join(dir, "f.part"), metaPath: filepath.Join(dir, "f.part.junto")}
		e.cond = sync.NewCond(&e.mu)
		if err := os.WriteFile(e.partPath, make([]byte, size), 0o644); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(e.metaPath, []byte(sidecar), 0o644); err != nil {
			t.Fatal(err)
		}
		return e
	}

	legacyMeta := protocol.FileMeta{Size: size, SHA256: "deadbeef"}
	baoMeta := protocol.FileMeta{Size: size, SHA256: "deadbeef", Bao: root, BaoGroup: 8, BaoOb: strings.Repeat("cd", 32)}

	cases := []struct {
		name    string
		meta    protocol.FileMeta
		sidecar string
		resume  bool
	}{
		{"legacy room, legacy sidecar", legacyMeta,
			`{"size": 100, "sha256": "deadbeef", "spans": [[0, 10]]}`, true},
		{"bao room, rootless sidecar", baoMeta,
			`{"size": 100, "sha256": "deadbeef", "spans": [[0, 10]]}`, false},
		{"bao room, matching root", baoMeta,
			`{"size": 100, "sha256": "deadbeef", "spans": [[0, 10]], "bao": "` + root + `"}`, true},
		{"bao room, different root", baoMeta,
			`{"size": 100, "sha256": "deadbeef", "spans": [[0, 10]], "bao": "` + otherRoot + `"}`, false},
		{"legacy room, bao sidecar", legacyMeta,
			`{"size": 100, "sha256": "deadbeef", "spans": [[0, 10]], "bao": "` + root + `"}`, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := newEntry(t, tc.meta, tc.sidecar)
			resumed := e.loadProgress()
			if resumed != tc.resume {
				t.Fatalf("resumed = %v, want %v", resumed, tc.resume)
			}
			if !tc.resume {
				if _, err := os.Stat(e.metaPath); !os.IsNotExist(err) {
					t.Fatal("stale sidecar was not cleared")
				}
			}
		})
	}
}

// TestSaveProgressRecordsBaoRoot checks the sidecar a bao-room session
// writes carries the root, so its own later sessions can resume while a
// regime change can't.
func TestSaveProgressRecordsBaoRoot(t *testing.T) {
	const size = 100
	root := strings.Repeat("ab", 32)
	dir := t.TempDir()
	e := &fileEntry{
		meta:     protocol.FileMeta{Size: size, SHA256: "deadbeef", Bao: root, BaoGroup: 8, BaoOb: strings.Repeat("cd", 32)},
		partPath: filepath.Join(dir, "f.part"), metaPath: filepath.Join(dir, "f.part.junto"),
	}
	e.cond = sync.NewCond(&e.mu)
	if err := os.WriteFile(e.partPath, make([]byte, size), 0o644); err != nil {
		t.Fatal(err)
	}
	e.add(0, 10)
	e.saveProgress(nil)

	fresh := &fileEntry{meta: e.meta, partPath: e.partPath, metaPath: e.metaPath}
	fresh.cond = sync.NewCond(&fresh.mu)
	if !fresh.loadProgress() {
		t.Fatal("bao-room sidecar written by saveProgress did not resume in the same room")
	}
	if got := fresh.covered(); got != 10 {
		t.Fatalf("covered = %d, want 10", got)
	}
}
