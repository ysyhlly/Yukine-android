package app

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// TestNewLocalServe pins the pre-promotion serving rules for a local-file
// joiner: only name+size-matched copies are served or advertised, and
// matched indices coalesce into HaveDone runs.
func TestNewLocalServe(t *testing.T) {
	dir := t.TempDir()
	write := func(name string, size int) string {
		p := filepath.Join(dir, name)
		if err := os.WriteFile(p, make([]byte, size), 0o644); err != nil {
			t.Fatal(err)
		}
		return p
	}
	byName := map[string]string{
		"a.mkv": write("a.mkv", 100),
		"b.mkv": write("b.mkv", 999), // size differs from the announced 200
		"c.mkv": write("c.mkv", 300),
		// d.mkv missing entirely
		"e.srt": write("e.srt", 50),
	}
	files := []protocol.FileMeta{
		{Name: "a.mkv", Size: 100, SHA256: "x"},
		{Name: "b.mkv", Size: 200, SHA256: "x"},
		{Name: "c.mkv", Size: 300, SHA256: "x", Subs: []protocol.FileMeta{{Name: "e.srt", Size: 50, SHA256: "x"}}},
		{Name: "d.mkv", Size: 400, SHA256: "x"},
	}

	ls := newLocalServe(files, byName, func(string, ...any) {}, nil)

	// flattenSubs order: a.mkv, b.mkv, e.srt (sub before its video), c.mkv, d.mkv.
	wantPaths := []bool{true, false, true, true, false}
	if len(ls.flatPaths) != len(wantPaths) {
		t.Fatalf("flatPaths = %v", ls.flatPaths)
	}
	for i, want := range wantPaths {
		if (ls.flatPaths[i] != "") != want {
			t.Fatalf("index %d serveable=%v, want %v (paths %v)", i, ls.flatPaths[i] != "", want, ls.flatPaths)
		}
	}
	if len(ls.doneRuns) != 2 || ls.doneRuns[0] != [2]int{0, 1} || ls.doneRuns[1] != [2]int{2, 4} {
		t.Fatalf("doneRuns = %v, want [[0,1],[2,4]]", ls.doneRuns)
	}
}
