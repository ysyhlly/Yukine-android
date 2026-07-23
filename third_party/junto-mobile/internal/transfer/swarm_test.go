package transfer

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// advertStore builds a multi-file store for AdvertSnapshot tests. Each
// meta gets a pre-sized .part; spans[i] marks received ranges; done[i]
// finishes (renames) the file.
func advertStore(t *testing.T, metas []protocol.FileMeta, spans map[int][][2]int64, done map[int]bool) *FileStore {
	t.Helper()
	store, err := NewFileStore(t.TempDir(), metas)
	if err != nil {
		t.Fatal(err)
	}
	for i, e := range store.files {
		if err := os.WriteFile(e.partPath, make([]byte, e.meta.Size), 0o644); err != nil {
			t.Fatal(err)
		}
		for _, sp := range spans[i] {
			e.add(sp[0], sp[1])
		}
		if done[i] {
			e.add(0, e.meta.Size)
			if err := e.finishRename(); err != nil {
				t.Fatal(err)
			}
		}
	}
	return store
}

// baoTestMeta builds a FileMeta announcing group=0 (1 KiB groups) with
// placeholder hashes — AdvertSnapshot only reads sizes and group math,
// never verifies.
func baoTestMeta(name string, size int64) protocol.FileMeta {
	return protocol.FileMeta{
		Name: name, Size: size, SHA256: "x",
		Bao: strings.Repeat("ab", 32), BaoGroup: 0, BaoOb: strings.Repeat("cd", 32),
	}
}

func TestAdvertSnapshotPieceConversion(t *testing.T) {
	// pieceGroups=4 at group 0 ⇒ 4 KiB pieces.
	orig := pieceGroups
	pieceGroups = 4
	defer func() { pieceGroups = orig }()
	const p = 4 * 1024

	size := int64(25 * p / 10 * 10) // 100 KiB = 25 pieces
	store := advertStore(t,
		[]protocol.FileMeta{baoTestMeta("a.mkv", size)},
		map[int][][2]int64{0: {
			{0, 10 * 1024},          // pieces 0,1 full; piece 2 partial → excluded
			{18 * 1024, 30 * 1024},  // pieces 5,6 full (20–28 KiB); edges partial
			{96 * 1024, 100 * 1024}, // the file tail: short-piece rule ⇒ piece 24
		}},
		nil)

	have, done := store.AdvertSnapshot()
	if len(done) != 0 {
		t.Fatalf("nothing is done, got %v", done)
	}
	if len(have) != 1 || have[0].File != 0 {
		t.Fatalf("have = %+v", have)
	}
	want := [][2]int{{0, 2}, {5, 7}, {24, 25}}
	if len(have[0].Pieces) != len(want) {
		t.Fatalf("pieces = %v, want %v", have[0].Pieces, want)
	}
	for i, w := range want {
		if have[0].Pieces[i] != w {
			t.Fatalf("pieces = %v, want %v", have[0].Pieces, want)
		}
	}
}

func TestAdvertSnapshotDoneRunsAndLegacy(t *testing.T) {
	orig := pieceGroups
	pieceGroups = 4
	defer func() { pieceGroups = orig }()

	legacy := protocol.FileMeta{Name: "old.mkv", Size: 64 * 1024, SHA256: "x"}
	store := advertStore(t,
		[]protocol.FileMeta{
			baoTestMeta("a.mkv", 64*1024), // 0: done
			baoTestMeta("b.mkv", 64*1024), // 1: done
			baoTestMeta("c.mkv", 64*1024), // 2: downloading with coverage
			legacy,                        // 3: legacy — never advertised
			baoTestMeta("d.srt", 64*1024), // 4: done
		},
		map[int][][2]int64{
			2: {{0, 16 * 1024}},
			3: {{0, 64 * 1024}},
		},
		map[int]bool{0: true, 1: true, 4: true})

	have, done := store.AdvertSnapshot()
	if len(done) != 2 || done[0] != [2]int{0, 2} || done[1] != [2]int{4, 5} {
		t.Fatalf("done runs = %v, want [[0,2],[4,5]]", done)
	}
	if len(have) != 1 || have[0].File != 2 {
		t.Fatalf("have = %+v, want only file 2 (legacy file must not advertise)", have)
	}
}

func TestAdvertSnapshotCoarsensFragmentedCoverage(t *testing.T) {
	orig := pieceGroups
	pieceGroups = 1 // 1 KiB pieces at group 0
	defer func() { pieceGroups = orig }()

	// 100 isolated single-piece spans with one wide 30-piece span: the
	// wide one must survive coarsening.
	size := int64(1024 * 1024)
	var spans [][2]int64
	for i := 0; i < 100; i++ {
		lo := int64(i * 8 * 1024)
		spans = append(spans, [2]int64{lo, lo + 1024})
	}
	spans = append(spans, [2]int64{900 * 1024, 930 * 1024})
	store := advertStore(t, []protocol.FileMeta{baoTestMeta("a.mkv", size)}, map[int][][2]int64{0: spans}, nil)

	have, _ := store.AdvertSnapshot()
	if len(have) != 1 {
		t.Fatalf("have = %+v", have)
	}
	pieces := have[0].Pieces
	if len(pieces) > protocol.MaxHavePieces {
		t.Fatalf("advertisement exceeds the wire cap: %d spans", len(pieces))
	}
	foundWide := false
	prev := -1
	for _, p := range pieces {
		if p[0] <= prev {
			t.Fatalf("pieces unsorted after coarsening: %v", pieces)
		}
		prev = p[1]
		if p == [2]int{900, 930} {
			foundWide = true
		}
	}
	if !foundWide {
		t.Fatal("coarsening dropped the widest span")
	}
}

func TestAdvertSnapshotCapsFiles(t *testing.T) {
	orig := pieceGroups
	pieceGroups = 4
	defer func() { pieceGroups = orig }()

	var metas []protocol.FileMeta
	spans := map[int][][2]int64{}
	for i := 0; i < protocol.MaxHaveFiles+3; i++ {
		metas = append(metas, baoTestMeta(filepath.Base(strings.Repeat("f", i+1)+".mkv"), 64*1024))
		spans[i] = [][2]int64{{0, 32 * 1024}}
	}
	store := advertStore(t, metas, spans, nil)
	have, _ := store.AdvertSnapshot()
	if len(have) != protocol.MaxHaveFiles {
		t.Fatalf("advertised %d files, cap is %d", len(have), protocol.MaxHaveFiles)
	}
}

func TestPeerCoverageTable(t *testing.T) {
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{Name: "a", Size: 10, SHA256: "x"}})
	if err != nil {
		t.Fatal(err)
	}
	d := NewDownloader(nil, "host", store, ICEConfig{}, nil, func(string, ...any) {})

	d.UpdatePeerHave("peerA", []protocol.HaveEntry{{File: 0, Pieces: [][2]int{{0, 4}}}}, [][2]int{{1, 3}})
	d.cov.mu.Lock()
	cov, ok := d.cov.byPeer["peerA"]
	d.cov.mu.Unlock()
	if !ok || !cov.done[1] || !cov.done[2] || cov.done[3] {
		t.Fatalf("done set wrong: %+v", cov.done)
	}
	if len(cov.pieces[0]) != 1 || cov.pieces[0][0] != [2]int{0, 4} {
		t.Fatalf("pieces wrong: %+v", cov.pieces)
	}

	// A later advertisement replaces, not merges.
	d.UpdatePeerHave("peerA", nil, [][2]int{{0, 1}})
	d.cov.mu.Lock()
	cov = d.cov.byPeer["peerA"]
	d.cov.mu.Unlock()
	if len(cov.pieces) != 0 || !cov.done[0] || cov.done[1] {
		t.Fatalf("advertisement did not replace: %+v", cov)
	}

	d.PeerGone("peerA")
	d.cov.mu.Lock()
	_, ok = d.cov.byPeer["peerA"]
	d.cov.mu.Unlock()
	if ok {
		t.Fatal("PeerGone left the coverage entry behind")
	}
}
