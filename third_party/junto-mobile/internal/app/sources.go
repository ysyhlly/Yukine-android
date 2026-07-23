package app

import (
	"context"
	"fmt"
	"strings"
	"unicode/utf8"

	"github.com/swayam-mishra/junto/internal/config"
	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/ytdlp"
)

// FetchOptions configure URL resolution for `junto create`.
type FetchOptions struct {
	YtDlpPath string // path to yt-dlp; empty means "yt-dlp" on PATH
	Quality   string // quality keyword passed to yt-dlp; empty means default (1080p)
}

// ResolveURLs turns a create argument list into local file paths: any
// http(s) URL is downloaded via yt-dlp into junto's cache directory and
// replaced by the resulting file path; local-file args pass through
// unchanged. Order (the playlist order) is preserved. The yt-dlp
// download progress reuses the same sticky status line as the P2P
// transfer bar. It lives in package app (rather than cmd/junto) only so
// it can share that console; app.Create itself stays file-only.
func ResolveURLs(ctx context.Context, args []string, opts FetchOptions) ([]string, error) {
	resolved := make([]string, 0, len(args))
	for _, a := range args {
		if !isURL(a) {
			resolved = append(resolved, a)
			continue
		}
		path, title, err := ytdlp.Fetch(ctx, a, ytdlp.Options{
			BinPath: opts.YtDlpPath,
			Quality: opts.Quality,
			DestDir: config.CacheDir(),
		}, func(p ytdlp.Progress) {
			term.SetStatus(renderFetch(a, p))
		})
		term.ClearStatus()
		if err != nil {
			return nil, err
		}
		printLine("fetched %s — hosting now", title)
		resolved = append(resolved, path)
	}
	return resolved, nil
}

// isURL reports whether s is an http(s) URL (vs a local file path).
func isURL(s string) bool {
	return strings.HasPrefix(s, "http://") || strings.HasPrefix(s, "https://")
}

// renderFetch draws the yt-dlp download as the sticky bar, matching the
// transfer bar's look. The title isn't known until the download
// finishes, so the source URL labels the bar.
func renderFetch(url string, p ytdlp.Progress) string {
	var frac float64
	if p.Total > 0 {
		frac = float64(p.Downloaded) / float64(p.Total)
		if frac > 1 {
			frac = 1
		}
	}
	filled := int(frac * progressBarWidth)
	bar := strings.Repeat("█", filled) + strings.Repeat("░", progressBarWidth-filled)
	total := "?"
	pct := "  ?"
	if p.Total > 0 {
		total = human.Bytes(p.Total)
		pct = fmt.Sprintf("%3.0f%%", frac*100)
	}
	eta := "--"
	if p.ETA > 0 {
		eta = fmtETA(p.ETA)
	}
	return fmt.Sprintf("⬇ downloading %s  [%s] %s  %s/%s  %s/s  ETA %s",
		shortURL(url), bar, pct, human.Bytes(p.Downloaded), total,
		human.Bytes(int64(p.BytesPerSec)), eta)
}

// shortURL trims a URL to something that fits on the status line,
// truncating on a rune boundary so a multi-byte UTF-8 character (an
// internationalized domain, a non-ASCII path segment, ...) straddling
// the cut point doesn't get sliced in half into invalid UTF-8.
func shortURL(u string) string {
	u = strings.TrimPrefix(u, "https://")
	u = strings.TrimPrefix(u, "http://")
	u = strings.TrimPrefix(u, "www.")
	const max = 39
	if utf8.RuneCountInString(u) <= max {
		return u
	}
	n := 0
	for i := range u {
		if n == max {
			return u[:i] + "…"
		}
		n++
	}
	return u
}
