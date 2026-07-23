// Package ytdlp fetches a video from a URL using the external yt-dlp
// binary, so `junto create <url>` can host a YouTube (or any
// yt-dlp-supported) video as if it were a local file. It downloads the
// finished file to a cache directory and returns its path; junto then
// serves it over the normal P2P file path. It does not stream live —
// open-ended live URLs are rejected up front.
package ytdlp

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// progressInterval throttles how often the progress callback fires. A
// var so tests can drop it.
var progressInterval = 300 * time.Millisecond

// Options configure a fetch. DestDir must exist or be creatable.
type Options struct {
	BinPath string // yt-dlp binary; default "yt-dlp"
	Quality string // quality keyword (see formatArg); default "1080p"
	DestDir string // directory to download into
}

// Progress is one download progress update.
type Progress struct {
	Downloaded  int64
	Total       int64 // 0 when yt-dlp hasn't reported a total yet
	BytesPerSec float64
	ETA         time.Duration
}

// Fetch downloads url into opts.DestDir and returns the resulting local
// file path and the video title. A file already present from a previous
// fetch (the output name carries the video id) is reused by yt-dlp
// without re-downloading. progress may be nil.
func Fetch(ctx context.Context, url string, opts Options, progress func(Progress)) (path, title string, err error) {
	bin := opts.BinPath
	if bin == "" {
		bin = "yt-dlp"
	}
	binPath, err := exec.LookPath(bin)
	if err != nil {
		return "", "", fmt.Errorf("yt-dlp not found on your PATH — install it (`brew install yt-dlp`, or `pipx install yt-dlp`), or point junto at it with -yt-dlp-path. (Only needed for URL sources.)")
	}

	title, id, live, err := preflight(ctx, binPath, url)
	if err != nil {
		return "", "", err
	}
	if live {
		return "", "", fmt.Errorf("%s is a live stream — junto can't host an open-ended live feed yet (only finite videos). Watch the replay/VOD once it's available", url)
	}

	if opts.DestDir != "" {
		if err := os.MkdirAll(opts.DestDir, 0o755); err != nil {
			return "", "", fmt.Errorf("creating download dir %s: %w", opts.DestDir, err)
		}
	}

	path, err = download(ctx, binPath, url, id, opts, progress)
	if err != nil {
		return "", "", err
	}
	if title == "" { // fall back to the file stem if metadata gave nothing
		title = strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))
	}
	return path, title, nil
}

// preflightTimeout bounds the metadata-only probe. It's a var so tests
// can shrink it. --simulate does no downloading, just extraction, so a
// real site answers in a few seconds; past this, a stalled extractor
// (a slow site, a hung network call, an interactive prompt yt-dlp is
// waiting on) would otherwise hang `create` silently with no feedback
// and no way out short of Ctrl-C.
var preflightTimeout = 30 * time.Second

// killWaitDelay bounds how long Cancel's process-group kill gets before
// WaitDelay force-closes the I/O pipes as a backstop. A var so tests can
// shrink it — the orphaned-child regression test waits on the same order
// of duration to observe Fetch return, so a shrunk value gives that wait
// real margin instead of racing the exact same duration on a loaded
// machine; production never changes it.
var killWaitDelay = 5 * time.Second

// preflight does a cheap metadata-only call to get the title, video id,
// and detect a live stream before committing to a download. id scopes
// cleanPartials to this download alone (see download).
func preflight(ctx context.Context, bin, url string) (title, id string, live bool, err error) {
	ctx, cancel := context.WithTimeout(ctx, preflightTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, bin,
		"--simulate", "--no-playlist", "--no-warnings",
		"--print", "%(is_live)s", "--print", "%(title)s", "--print", "%(id)s",
		"--", url) // -- stops flag parsing (see buildArgs)
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	out, err := cmd.Output()
	if err != nil {
		if ctx.Err() == context.DeadlineExceeded {
			return "", "", false, fmt.Errorf("yt-dlp took too long (over %s) to fetch metadata for %s — the site may be slow, unreachable, or need authentication", preflightTimeout, url)
		}
		if ctx.Err() != nil {
			return "", "", false, ctx.Err()
		}
		return "", "", false, classifyErr(url, stderr.String(), err)
	}
	lines := strings.Split(strings.TrimRight(string(out), "\n"), "\n")
	if len(lines) >= 1 {
		live = strings.EqualFold(strings.TrimSpace(lines[0]), "True")
	}
	if len(lines) >= 2 {
		title = strings.TrimSpace(lines[1])
	}
	if len(lines) >= 3 {
		id = strings.TrimSpace(lines[2])
	}
	return title, id, live, nil
}

// download runs the actual yt-dlp download, streaming progress and
// returning the produced file path (from --print after_move:filepath).
// id (from preflight) scopes cleanPartials to this download alone.
func download(ctx context.Context, bin, url, id string, opts Options, progress func(Progress)) (string, error) {
	cmd := exec.CommandContext(ctx, bin, buildArgs(url, opts)...)
	setProcessGroup(cmd)
	// The default CommandContext behavior kills only cmd.Process (the
	// direct yt-dlp child) on cancellation, leaving any subprocess it
	// spawned (ffmpeg, for merging/postprocessing) running as an orphan —
	// it keeps writing to the cache, and since it inherited the same
	// stdout pipe, the scan loop below never sees EOF. Cancel kills the
	// whole process group instead; WaitDelay is a backstop that force-
	// closes the pipe if the group kill somehow doesn't free it promptly.
	cmd.Cancel = func() error { return killGroup(cmd) }
	cmd.WaitDelay = killWaitDelay
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return "", err
	}
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	if err := cmd.Start(); err != nil {
		return "", fmt.Errorf("starting yt-dlp: %w", err)
	}

	var lastPath string
	var lastEmit time.Time
	sc := bufio.NewScanner(stdout)
	sc.Buffer(make([]byte, 0, 64*1024), 1<<20)
	for sc.Scan() {
		line := sc.Text()
		if p, ok := parseProgress(line); ok {
			if progress != nil && time.Since(lastEmit) >= progressInterval {
				progress(p)
				lastEmit = time.Now()
			}
			continue
		}
		// --print after_move:filepath emits the final path on its own line.
		if t := strings.TrimSpace(line); filepath.IsAbs(t) {
			lastPath = t
		}
	}
	scanErr := sc.Err()

	err = cmd.Wait()
	if ctx.Err() != nil { // cancelled (Ctrl-C): leave no half-files behind
		cleanPartials(opts.DestDir, id)
		return "", ctx.Err()
	}
	if err != nil {
		return "", classifyErr(url, stderr.String(), err)
	}
	if scanErr != nil {
		// A read error (or a line over the 1 MiB buffer) could have cut
		// off the final --print after_move:filepath line, so a "no output
		// file" error below wouldn't say why. yt-dlp itself exited 0 here,
		// so this is worth reporting on its own rather than falling
		// through and blaming a missing lastPath.
		return "", fmt.Errorf("reading yt-dlp output: %w", scanErr)
	}
	if lastPath == "" {
		return "", fmt.Errorf("yt-dlp finished but didn't report an output file for %s", url)
	}
	return lastPath, nil
}

// buildArgs constructs the yt-dlp download argv. Pure, for testing.
func buildArgs(url string, opts Options) []string {
	return []string{
		"-f", formatArg(opts.Quality),
		"--merge-output-format", "mp4",
		"--no-playlist",
		"--restrict-filenames",
		"--no-warnings",
		"-P", opts.DestDir,
		"-o", "%(title).80s [%(id)s].%(ext)s",
		"--newline",
		"--progress-template", "download:JUNTO %(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.speed)s %(progress.eta)s",
		"--print", "after_move:filepath",
		"--no-simulate",
		// SECURITY: "--" stops option parsing so a URL beginning with "-"
		// can't be smuggled in as a yt-dlp flag (e.g. --exec, which runs a
		// shell command). The URL is local-user input, but this closes
		// argument injection cheaply.
		"--",
		url,
	}
}

// formatArg maps a quality keyword to a yt-dlp -f format selector. The
// trailing /b fallback picks a pre-muxed progressive stream when no
// separate video+audio pair exists (so it works without ffmpeg).
// Unknown values are passed through as a raw yt-dlp format string.
func formatArg(quality string) string {
	switch quality {
	case "", "1080p":
		return "bv*[height<=1080]+ba/b[height<=1080]/b"
	case "720p":
		return "bv*[height<=720]+ba/b[height<=720]/b"
	case "480p":
		return "bv*[height<=480]+ba/b[height<=480]/b"
	case "best":
		return "bv*+ba/b"
	case "audio":
		return "ba/b"
	default:
		return quality
	}
}

// parseProgress parses a "JUNTO <downloaded> <total> <speed> <eta>" line
// emitted by our --progress-template. Fields may be "NA". ok is false
// for any non-progress line.
func parseProgress(line string) (Progress, bool) {
	rest, ok := strings.CutPrefix(line, "JUNTO ")
	if !ok {
		return Progress{}, false
	}
	f := strings.Fields(rest)
	if len(f) != 4 {
		return Progress{}, false
	}
	down, ok := parseIntNA(f[0])
	if !ok {
		return Progress{}, false // downloaded must be a real number
	}
	total, _ := parseIntNA(f[1])
	speed, _ := parseFloatNA(f[2])
	etaSecs, _ := parseFloatNA(f[3])
	return Progress{
		Downloaded:  down,
		Total:       total,
		BytesPerSec: speed,
		ETA:         time.Duration(etaSecs * float64(time.Second)),
	}, true
}

func parseIntNA(s string) (int64, bool) {
	if s == "NA" || s == "None" || s == "" {
		return 0, false
	}
	// yt-dlp may format as a float ("1234.0"); parse leniently.
	if v, err := strconv.ParseFloat(s, 64); err == nil {
		return int64(v), true
	}
	return 0, false
}

func parseFloatNA(s string) (float64, bool) {
	if s == "NA" || s == "None" || s == "" {
		return 0, false
	}
	v, err := strconv.ParseFloat(s, 64)
	return v, err == nil
}

// classifyErr turns a yt-dlp failure into an actionable, plain-English
// error, recognizing the common ffmpeg-missing and auth-required cases.
func classifyErr(url, stderr string, err error) error {
	s := strings.TrimSpace(stderr)
	low := strings.ToLower(s)
	switch {
	case strings.Contains(low, "ffmpeg") && (strings.Contains(low, "not installed") || strings.Contains(low, "requested merging") || strings.Contains(low, "postprocessing")):
		return fmt.Errorf("yt-dlp needs ffmpeg to merge this video's audio and video — install ffmpeg (`brew install ffmpeg`, or your package manager), or retry with -quality 480p for a single-file stream")
	case strings.Contains(low, "private") || strings.Contains(low, "sign in") || strings.Contains(low, "age-restricted") || strings.Contains(low, "confirm your age"):
		return fmt.Errorf("yt-dlp can't fetch %s: %s\n(this may need a logged-in session — yt-dlp's --cookies; junto doesn't manage that for you)", url, lastLine(s))
	case s != "":
		return fmt.Errorf("yt-dlp failed on %s: %s", url, lastLine(s))
	default:
		return fmt.Errorf("yt-dlp failed on %s: %w", url, err)
	}
}

func lastLine(s string) string {
	lines := strings.Split(strings.TrimRight(s, "\n"), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		if t := strings.TrimSpace(lines[i]); t != "" {
			return t
		}
	}
	return s
}

// cleanPartials removes this download's in-progress fragments so a
// cancelled download doesn't masquerade as complete next time. Scoped to
// id (the video id from preflight, embedded in every filename by
// buildArgs' "-o" template as "[%(id)s]") — DestDir is a shared cache
// directory, so an unscoped glob would also delete another concurrent
// session's partial download.
func cleanPartials(dir, id string) {
	if dir == "" || id == "" {
		return
	}
	for _, pat := range []string{"*[" + id + "]*.part", "*[" + id + "]*.ytdl", "*[" + id + "]*.part-Frag*", "*[" + id + "]*.temp"} {
		matches, _ := filepath.Glob(filepath.Join(dir, pat))
		for _, m := range matches {
			os.Remove(m)
		}
	}
}
