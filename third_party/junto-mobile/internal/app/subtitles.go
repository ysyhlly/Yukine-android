package app

import (
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/swayam-mishra/junto/internal/protocol"
)

// subExts are the subtitle file extensions junto shares alongside video.
var subExts = map[string]bool{".srt": true, ".ass": true, ".ssa": true, ".vtt": true}

// discoverSubsForVideos finds subtitle sidecar files for a whole
// playlist at once: same directory as the video, base name matching
// either exactly (movie.srt) or with a language/label suffix
// (movie.en.srt). Returns one sorted subtitle-path list per entry,
// aligned with videoPaths.
//
// A subtitle is assigned to exactly the video whose base name is the
// *longest* matching prefix. Matching each video independently (the
// pre-fix behavior) let a subtitle like "movie.extended.en.srt" attach
// to both "movie.mkv" (base "movie") and "movie.extended.mkv" (base
// "movie.extended") whenever both are in the same playlist — the same
// physical file counted as a duplicate name once both bases claimed it,
// aborting create with a misleading "duplicate file name" error. Ranking
// candidates by base length makes an exact match (stem == base) win
// automatically too, since it's definitionally the longest a base can
// ever match.
func discoverSubsForVideos(videoPaths []string) [][]string {
	type videoRef struct {
		idx  int
		base string
	}
	byDir := make(map[string][]videoRef)
	for i, p := range videoPaths {
		dir := filepath.Dir(p)
		base := strings.TrimSuffix(filepath.Base(p), filepath.Ext(p))
		byDir[dir] = append(byDir[dir], videoRef{idx: i, base: base})
	}

	result := make([][]string, len(videoPaths))
	for dir, videos := range byDir {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, ent := range entries {
			if ent.IsDir() {
				continue
			}
			name := ent.Name()
			if !subExts[strings.ToLower(filepath.Ext(name))] {
				continue
			}
			stem := strings.TrimSuffix(name, filepath.Ext(name))

			var best []videoRef
			bestLen := -1
			for _, v := range videos {
				if stem != v.base && !strings.HasPrefix(stem, v.base+".") {
					continue
				}
				switch {
				case len(v.base) > bestLen:
					bestLen = len(v.base)
					best = []videoRef{v}
				case len(v.base) == bestLen:
					best = append(best, v)
				}
			}
			for _, v := range best {
				result[v.idx] = append(result[v.idx], filepath.Join(dir, name))
			}
		}
	}
	for i := range result {
		sort.Strings(result[i])
	}
	return result
}

// flattenSubs turns the announced per-video metadata (each carrying its
// Subs) into the flat transfer list both host and joiner build
// identically. Each video's subtitles come *before* the video so they
// download first (they're tiny) and are ready when playback starts. It
// returns the flat metas, the flat index of each playlist video
// (videoIdx), and, per playlist position, the flat indices of that
// video's subtitles (subsFor).
func flattenSubs(videos []protocol.FileMeta) (flat []protocol.FileMeta, videoIdx []int, subsFor [][]int) {
	for _, v := range videos {
		subs := v.Subs
		v.Subs = nil // the flattened video entry doesn't nest its subs
		var these []int
		for _, s := range subs {
			these = append(these, len(flat))
			flat = append(flat, s)
		}
		videoIdx = append(videoIdx, len(flat))
		flat = append(flat, v)
		subsFor = append(subsFor, these)
	}
	return flat, videoIdx, subsFor
}
