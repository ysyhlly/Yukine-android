// Package selfupdate implements `junto update`: it checks GitHub for the
// latest release, and for a standalone binary install downloads the
// matching archive (checksum-verified) and atomically swaps the running
// executable. Homebrew and `go install` installs are delegated to their
// own tools rather than overwritten. It uses only the standard library.
package selfupdate

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const (
	owner   = "swayam-mishra"
	repo    = "junto"
	binName = "junto"
)

// Asset is one GitHub release asset.
type Asset struct {
	Name string `json:"name"`
	URL  string `json:"browser_download_url"`
}

type release struct {
	TagName string  `json:"tag_name"`
	Assets  []Asset `json:"assets"`
}

func client() *http.Client { return &http.Client{Timeout: 60 * time.Second} }

// LatestRelease fetches the latest release's tag and assets from GitHub.
func LatestRelease(ctx context.Context) (tag string, assets []Asset, err error) {
	return latestFrom(ctx, fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", owner, repo))
}

// latestFrom is the testable core of LatestRelease (the URL is injected).
func latestFrom(ctx context.Context, apiURL string) (string, []Asset, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return "", nil, err
	}
	req.Header.Set("User-Agent", "junto-selfupdate") // GitHub requires a UA
	req.Header.Set("Accept", "application/vnd.github+json")
	resp, err := client().Do(req)
	if err != nil {
		return "", nil, fmt.Errorf("reaching GitHub: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusForbidden && resp.Header.Get("X-RateLimit-Remaining") == "0" {
		return "", nil, fmt.Errorf("GitHub API rate limit reached — try again in a little while")
	}
	if resp.StatusCode != http.StatusOK {
		return "", nil, fmt.Errorf("GitHub returned %s when checking for the latest release", resp.Status)
	}
	var rel release
	if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
		return "", nil, fmt.Errorf("parsing the release info: %w", err)
	}
	if rel.TagName == "" {
		return "", nil, fmt.Errorf("no published release found")
	}
	return rel.TagName, rel.Assets, nil
}

// AssetFor selects the release archive for the given platform: the macOS
// universal build, or the linux build for goarch. ok is false on an
// unsupported platform or if no matching asset is present.
func AssetFor(goos, goarch string, assets []Asset) (Asset, bool) {
	var suffix string
	switch goos {
	case "darwin":
		suffix = "_darwin_all.tar.gz" // universal binary — arch-independent
	case "linux":
		suffix = "_linux_" + goarch + ".tar.gz"
	default:
		return Asset{}, false
	}
	for _, a := range assets {
		if strings.HasSuffix(a.Name, suffix) {
			return a, true
		}
	}
	return Asset{}, false
}

// SameVersion reports whether the running version matches the release
// tag, ignoring a leading "v" on either side.
func SameVersion(current, tag string) bool {
	return strings.TrimPrefix(current, "v") == strings.TrimPrefix(tag, "v")
}

// Method is how junto was installed.
type Method int

const (
	Standalone Method = iota // a plain downloaded binary — safe to self-replace
	Brew                     // Homebrew — delegate to `brew`
	GoInstall                // `go install` — delegate to the go toolchain
)

// InstallMethod classifies how the binary at execPath was installed, so
// `update`/`uninstall` can self-act or delegate. Pure given the path +
// environment (GOBIN/GOPATH/HOME).
func InstallMethod(execPath string) Method {
	resolved := execPath
	if p, err := filepath.EvalSymlinks(execPath); err == nil {
		resolved = p
	}
	// Homebrew keeps formula binaries under Cellar and cask binaries under
	// Caskroom (junto ships as a cask — see .goreleaser.yaml — but Cellar
	// is still checked in case a user's tap or install predates that).
	if strings.Contains(resolved, "/Cellar/") || strings.Contains(resolved, "/Caskroom/") {
		return Brew
	}
	for _, d := range goBinDirs() {
		if d != "" && strings.HasPrefix(resolved, d+string(os.PathSeparator)) {
			return GoInstall
		}
	}
	return Standalone
}

func goBinDirs() []string {
	var dirs []string
	if b := os.Getenv("GOBIN"); b != "" {
		dirs = append(dirs, b)
	}
	if p := os.Getenv("GOPATH"); p != "" {
		for _, part := range filepath.SplitList(p) {
			dirs = append(dirs, filepath.Join(part, "bin"))
		}
	}
	if home, err := os.UserHomeDir(); err == nil {
		dirs = append(dirs, filepath.Join(home, "go", "bin"))
	}
	return dirs
}

// DownloadBinary fetches asset, verifies its sha256 against the release's
// checksums.txt, and extracts the junto binary from the tar.gz. assets is
// the full asset list (to locate checksums.txt). Verification is
// mandatory, not best-effort: a release missing checksums.txt, a failed
// fetch of it, or no matching entry for asset are all hard errors rather
// than a silent skip — installing unverified would reopen exactly the
// tampered/corrupt-download (and decompression-bomb) risk the checksum
// check exists to close.
func DownloadBinary(ctx context.Context, asset Asset, assets []Asset) ([]byte, error) {
	data, err := httpGet(ctx, asset.URL)
	if err != nil {
		return nil, fmt.Errorf("downloading %s: %w", asset.Name, err)
	}
	sumAsset, ok := findChecksums(assets)
	if !ok {
		return nil, fmt.Errorf("release has no checksums.txt — refusing to install an unverified download")
	}
	sums, err := httpGet(ctx, sumAsset.URL)
	if err != nil {
		return nil, fmt.Errorf("fetching checksums.txt: %w", err)
	}
	want, ok := checksumFor(asset.Name, string(sums))
	if !ok {
		return nil, fmt.Errorf("no checksum listed for %s — refusing to install an unverified download", asset.Name)
	}
	got := sha256.Sum256(data)
	if !strings.EqualFold(hex.EncodeToString(got[:]), want) {
		return nil, fmt.Errorf("checksum mismatch for %s — refusing to install a tampered or corrupt download", asset.Name)
	}
	return extractBinary(data)
}

func findChecksums(assets []Asset) (Asset, bool) {
	for _, a := range assets {
		if strings.HasSuffix(a.Name, "checksums.txt") {
			return a, true
		}
	}
	return Asset{}, false
}

// checksumFor finds the sha256 for assetName in a `<sha>  <name>` file.
func checksumFor(assetName, sums string) (string, bool) {
	for _, line := range strings.Split(sums, "\n") {
		f := strings.Fields(line)
		if len(f) == 2 && f[1] == assetName {
			return f[0], true
		}
	}
	return "", false
}

// extractBinary pulls the junto executable out of a .tar.gz archive.
func extractBinary(targz []byte) ([]byte, error) {
	gz, err := gzip.NewReader(bytes.NewReader(targz))
	if err != nil {
		return nil, fmt.Errorf("opening archive: %w", err)
	}
	defer gz.Close()
	tr := tar.NewReader(gz)
	for {
		hdr, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, fmt.Errorf("reading archive: %w", err)
		}
		if filepath.Base(hdr.Name) == binName && hdr.Typeflag == tar.TypeReg {
			b, err := io.ReadAll(tr)
			if err != nil {
				return nil, fmt.Errorf("reading %s from archive: %w", binName, err)
			}
			return b, nil
		}
	}
	return nil, fmt.Errorf("no %s binary found in the release archive", binName)
}

func httpGet(ctx context.Context, url string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "junto-selfupdate")
	resp, err := client().Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GitHub returned %s", resp.Status)
	}
	return io.ReadAll(resp.Body)
}

// ReplaceExecutable atomically swaps the file at dst with bin: it writes
// a temp file in the same directory, makes it executable, and renames it
// over dst. Replacing a running executable by path is allowed on Unix.
func ReplaceExecutable(dst string, bin []byte) error {
	dir := filepath.Dir(dst)
	tmp, err := os.CreateTemp(dir, ".junto-update-*")
	if err != nil {
		return fmt.Errorf("can't write to %s (need permission?): %w\ntry: sudo junto update, or reinstall from https://github.com/%s/%s/releases/latest", dir, err, owner, repo)
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName) // harmless no-op once the rename succeeds
	if _, err := tmp.Write(bin); err != nil {
		tmp.Close()
		return err
	}
	// Sync before the rename below: Close alone doesn't guarantee the
	// written bytes have reached durable storage, so a crash between a
	// successful rename and the OS actually flushing them could leave the
	// binary at dst truncated or full of garbage — the very thing this
	// process would need to run correctly to recover from.
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return fmt.Errorf("syncing %s: %w", tmpName, err)
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Chmod(tmpName, 0o755); err != nil {
		return err
	}
	if err := os.Rename(tmpName, dst); err != nil {
		return fmt.Errorf("replacing %s: %w", dst, err)
	}
	return nil
}
