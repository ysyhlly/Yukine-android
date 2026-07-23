package transfer

import (
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// rarestFixture builds a Downloader whose coverage table makes pieces 4–6
// the rarest and 0–3 more common, for the target peer (which advertises
// all seven). pieceGroups=1 with a 1 KiB group ⇒ 1 KiB pieces, 7 total.
func rarestFixture(t *testing.T) *Downloader {
	t.Helper()
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{Name: "a", Size: 7 * 1024, SHA256: "x"}})
	if err != nil {
		t.Fatal(err)
	}
	store.files[0].bao = &baoParams{groupBytes: 1024, size: 7 * 1024}
	d := &Downloader{store: store}
	d.cov.byPeer = map[string]peerCov{
		// target has all seven pieces.
		"target": {pieces: map[int][][2]int{0: {{0, 7}}}},
		// peerB also has 0–3, making those more common (rarity 3) than
		// 4–6 (rarity 2: the host + target).
		"peerB": {pieces: map[int][][2]int{0: {{0, 4}}}},
	}
	return d
}

func TestRarestCandidatesRarestFirst(t *testing.T) {
	origPG := pieceGroups
	pieceGroups = 1
	defer func() { pieceGroups = origPG }()
	orig := randUint32
	defer func() { randUint32 = orig }()
	randUint32 = func() uint32 { return 0 }

	d := rarestFixture(t)
	got := d.rarestCandidates(0, "target")
	if len(got) != 7 {
		t.Fatalf("got %d candidates, want 7", len(got))
	}
	// The three rarest pieces (4,5,6) must all come before the four
	// more-common ones (0,1,2,3), regardless of the tie-break.
	rare := map[int]bool{4: true, 5: true, 6: true}
	for i, p := range got {
		if i < 3 && !rare[p] {
			t.Fatalf("position %d is piece %d, expected one of the rarest {4,5,6}: %v", i, p, got)
		}
		if i >= 3 && rare[p] {
			t.Fatalf("a rarest piece %d appeared after a common one: %v", p, got)
		}
	}
}

// TestRarestCandidatesTieBreakIsRandom proves the within-rarity order is
// driven by randUint32 (so different joiners diverge), not by piece
// index: two different tie streams yield two different orderings of the
// equally-rare group.
func TestRarestCandidatesTieBreakIsRandom(t *testing.T) {
	origPG := pieceGroups
	pieceGroups = 1
	defer func() { pieceGroups = origPG }()
	orig := randUint32
	defer func() { randUint32 = orig }()

	d := rarestFixture(t)

	// Ties are assigned in piece order (0..6). An ascending stream orders
	// the rarest group {4,5,6} as 4,5,6; a descending stream reverses it.
	rarestThree := func(ties func() uint32) []int {
		randUint32 = ties
		got := d.rarestCandidates(0, "target")
		return got[:3]
	}

	var n uint32
	asc := rarestThree(func() uint32 { n++; return n })
	n = 1000
	desc := rarestThree(func() uint32 { n--; return n })

	wantAsc, wantDesc := []int{4, 5, 6}, []int{6, 5, 4}
	if !equalInts(asc, wantAsc) {
		t.Fatalf("ascending ties gave %v, want %v", asc, wantAsc)
	}
	if !equalInts(desc, wantDesc) {
		t.Fatalf("descending ties gave %v, want %v (tie-break must follow randUint32, not index)", desc, wantDesc)
	}
}

func equalInts(a, b []int) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}
