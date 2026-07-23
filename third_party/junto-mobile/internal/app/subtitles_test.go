package app

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestDiscoverSubs(t *testing.T) {
	dir := t.TempDir()
	write := func(name string) {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write("movie.mkv")
	write("movie.srt")    // exact match
	write("movie.en.ass") // language-suffixed match
	write("movie.fr.vtt") // another match
	write("other.srt")    // different base — must NOT match
	write("movie.txt")    // not a subtitle extension
	write("movie.nfo")    // ditto

	got := discoverSubsForVideos([]string{filepath.Join(dir, "movie.mkv")})
	want := []string{
		filepath.Join(dir, "movie.en.ass"),
		filepath.Join(dir, "movie.fr.vtt"),
		filepath.Join(dir, "movie.srt"),
	}
	if !reflect.DeepEqual(got[0], want) {
		t.Errorf("discoverSubsForVideos = %v, want %v", got[0], want)
	}
}

// TestDiscoverSubsLongestMatchWins is the regression test for the
// double-attach bug: matching each video against the whole directory
// independently let a subtitle like "movie.extended.en.srt" attach to
// both "movie.mkv" (base "movie") and "movie.extended.mkv" (base
// "movie.extended"), since the shorter base is a valid prefix match too.
// The second attachment then collided with the first as a "duplicate
// file name" once both videos claimed the same physical file. Only the
// video with the longest matching base — the more specific one — must
// claim it.
func TestDiscoverSubsLongestMatchWins(t *testing.T) {
	dir := t.TempDir()
	write := func(name string) {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write("movie.mkv")
	write("movie.extended.mkv")
	write("movie.srt")             // belongs to movie.mkv only
	write("movie.extended.en.srt") // belongs to movie.extended.mkv only — NOT also movie.mkv

	got := discoverSubsForVideos([]string{
		filepath.Join(dir, "movie.mkv"),
		filepath.Join(dir, "movie.extended.mkv"),
	})

	wantMovie := []string{filepath.Join(dir, "movie.srt")}
	wantExtended := []string{filepath.Join(dir, "movie.extended.en.srt")}
	if !reflect.DeepEqual(got[0], wantMovie) {
		t.Errorf("movie.mkv subs = %v, want %v (must not also claim the extended cut's subtitle)", got[0], wantMovie)
	}
	if !reflect.DeepEqual(got[1], wantExtended) {
		t.Errorf("movie.extended.mkv subs = %v, want %v", got[1], wantExtended)
	}
}

func TestFlattenSubs(t *testing.T) {
	videos := []protocol.FileMeta{
		{Name: "a.mkv", Subs: []protocol.FileMeta{{Name: "a.srt"}, {Name: "a.en.srt"}}},
		{Name: "b.mkv"}, // no subs
	}
	flat, videoIdx, subsFor := flattenSubs(videos)

	// Subtitles come before their video; order is a.srt, a.en.srt, a.mkv, b.mkv.
	names := make([]string, len(flat))
	for i, f := range flat {
		names[i] = f.Name
	}
	wantNames := []string{"a.srt", "a.en.srt", "a.mkv", "b.mkv"}
	if !reflect.DeepEqual(names, wantNames) {
		t.Errorf("flat order = %v, want %v", names, wantNames)
	}
	if !reflect.DeepEqual(videoIdx, []int{2, 3}) {
		t.Errorf("videoIdx = %v, want [2 3]", videoIdx)
	}
	if !reflect.DeepEqual(subsFor, [][]int{{0, 1}, nil}) {
		t.Errorf("subsFor = %v, want [[0 1] []]", subsFor)
	}
	// Flattened video entries must not nest subs (they're separate entries now).
	if flat[2].Subs != nil {
		t.Error("flattened video should not carry nested Subs")
	}
}
