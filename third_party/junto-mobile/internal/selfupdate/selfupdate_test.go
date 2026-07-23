package selfupdate

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestAssetFor(t *testing.T) {
	assets := []Asset{
		{Name: "junto_1.2.3_darwin_all.tar.gz", URL: "d"},
		{Name: "junto_1.2.3_linux_amd64.tar.gz", URL: "la"},
		{Name: "junto_1.2.3_linux_arm64.tar.gz", URL: "lr"},
		{Name: "checksums.txt", URL: "c"},
	}
	cases := []struct {
		goos, goarch, wantURL string
		ok                    bool
	}{
		{"darwin", "arm64", "d", true}, // universal: arch ignored
		{"darwin", "amd64", "d", true},
		{"linux", "amd64", "la", true},
		{"linux", "arm64", "lr", true},
		{"windows", "amd64", "", false},
		{"linux", "riscv64", "", false}, // no such asset
	}
	for _, c := range cases {
		a, ok := AssetFor(c.goos, c.goarch, assets)
		if ok != c.ok || a.URL != c.wantURL {
			t.Errorf("AssetFor(%s,%s) = %q,%v; want %q,%v", c.goos, c.goarch, a.URL, ok, c.wantURL, c.ok)
		}
	}
}

func TestSameVersion(t *testing.T) {
	if !SameVersion("1.2.3", "v1.2.3") {
		t.Error("1.2.3 should match v1.2.3")
	}
	if !SameVersion("v1.2.3", "1.2.3") {
		t.Error("leading v ignored both ways")
	}
	if SameVersion("dev", "v1.2.3") {
		t.Error("dev should not match a release")
	}
	if SameVersion("1.2.3", "v1.2.4") {
		t.Error("different versions should not match")
	}
}

func TestInstallMethod(t *testing.T) {
	t.Setenv("HOME", t.TempDir()) // isolate ~/go/bin
	t.Setenv("GOPATH", "")
	gobin := t.TempDir()
	t.Setenv("GOBIN", gobin)

	cases := []struct {
		path string
		want Method
	}{
		{"/usr/local/Cellar/junto/1.0/bin/junto", Brew},
		{"/opt/homebrew/Cellar/junto/1.0/bin/junto", Brew},
		// junto ships as a Homebrew cask (see .goreleaser.yaml), whose
		// `binary` stanza symlinks into Caskroom rather than Cellar.
		{"/opt/homebrew/Caskroom/junto/1.0/junto", Brew},
		{"/usr/local/Caskroom/junto/1.0/junto", Brew},
		{filepath.Join(gobin, "junto"), GoInstall},
		{"/opt/custom/bin/junto", Standalone},
		{"/usr/local/bin/junto", Standalone},
	}
	for _, c := range cases {
		if got := InstallMethod(c.path); got != c.want {
			t.Errorf("InstallMethod(%q) = %d, want %d", c.path, got, c.want)
		}
	}
}

func TestChecksumFor(t *testing.T) {
	sums := "aaa  junto_1.0_linux_amd64.tar.gz\nbbb  junto_1.0_darwin_all.tar.gz\n"
	if s, ok := checksumFor("junto_1.0_darwin_all.tar.gz", sums); !ok || s != "bbb" {
		t.Errorf("checksumFor darwin = %q,%v; want bbb,true", s, ok)
	}
	if _, ok := checksumFor("missing.tar.gz", sums); ok {
		t.Error("missing asset should be ok=false")
	}
}

func TestExtractBinary(t *testing.T) {
	want := []byte("i am the junto binary")
	tgz := makeTarGz(t, map[string][]byte{"README.md": []byte("readme"), "junto": want})
	got, err := extractBinary(tgz)
	if err != nil {
		t.Fatalf("extractBinary: %v", err)
	}
	if !bytes.Equal(got, want) {
		t.Errorf("extracted %q, want %q", got, want)
	}
	if _, err := extractBinary(makeTarGz(t, map[string][]byte{"other": []byte("x")})); err == nil {
		t.Error("expected an error when no junto binary is present")
	}
}

func TestLatestFrom(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("User-Agent") == "" {
			t.Error("missing User-Agent header")
		}
		fmt.Fprint(w, `{"tag_name":"v2.0.0","assets":[{"name":"junto_2.0.0_darwin_all.tar.gz","browser_download_url":"http://x/a"}]}`)
	}))
	defer srv.Close()
	tag, assets, err := latestFrom(context.Background(), srv.URL)
	if err != nil || tag != "v2.0.0" || len(assets) != 1 || assets[0].Name != "junto_2.0.0_darwin_all.tar.gz" {
		t.Fatalf("latestFrom = %q,%v,%v", tag, assets, err)
	}
}

func TestDownloadBinary(t *testing.T) {
	want := []byte("the real binary")
	tgz := makeTarGz(t, map[string][]byte{"junto": want})
	sum := sha256.Sum256(tgz)
	assetName := "junto_1.0_darwin_all.tar.gz"

	mux := http.NewServeMux()
	mux.HandleFunc("/a.tar.gz", func(w http.ResponseWriter, r *http.Request) { w.Write(tgz) })
	mux.HandleFunc("/checksums.txt", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "%s  %s\n", hex.EncodeToString(sum[:]), assetName)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	assets := []Asset{
		{Name: assetName, URL: srv.URL + "/a.tar.gz"},
		{Name: "checksums.txt", URL: srv.URL + "/checksums.txt"},
	}
	got, err := DownloadBinary(context.Background(), assets[0], assets)
	if err != nil {
		t.Fatalf("DownloadBinary: %v", err)
	}
	if !bytes.Equal(got, want) {
		t.Errorf("got %q, want %q", got, want)
	}

	// Tampered checksum → refuse.
	bad := []Asset{
		{Name: assetName, URL: srv.URL + "/a.tar.gz"},
		{Name: "checksums.txt", URL: srv.URL + "/bad"},
	}
	mux.HandleFunc("/bad", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "%s  %s\n", "deadbeef", assetName)
	})
	if _, err := DownloadBinary(context.Background(), bad[0], bad); err == nil {
		t.Error("expected a checksum-mismatch error")
	}
}

// TestDownloadBinaryRefusesWhenUnverifiable is the regression test for the
// silently-skipped-verification bug: DownloadBinary used to fall through
// to installing the binary unverified whenever checksum verification
// couldn't be completed for any reason (no checksums.txt asset, a failed
// fetch of it, or no matching entry) — reopening the exact
// tampered/corrupt-download risk the checksum check exists to close.
// Every one of those cases must now be a hard error instead.
func TestDownloadBinaryRefusesWhenUnverifiable(t *testing.T) {
	tgz := makeTarGz(t, map[string][]byte{"junto": []byte("the real binary")})
	sum := sha256.Sum256(tgz)
	assetName := "junto_1.0_darwin_all.tar.gz"

	mux := http.NewServeMux()
	mux.HandleFunc("/a.tar.gz", func(w http.ResponseWriter, r *http.Request) { w.Write(tgz) })
	mux.HandleFunc("/checksums.txt", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "%s  %s\n", hex.EncodeToString(sum[:]), assetName)
	})
	mux.HandleFunc("/checksums-other-asset.txt", func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintf(w, "%s  %s\n", hex.EncodeToString(sum[:]), "some-other-file.tar.gz")
	})
	mux.HandleFunc("/missing-checksums.txt", func(w http.ResponseWriter, r *http.Request) {
		http.NotFound(w, r)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	binAsset := Asset{Name: assetName, URL: srv.URL + "/a.tar.gz"}

	cases := []struct {
		name   string
		assets []Asset
	}{
		{
			"no checksums.txt asset in the release at all",
			[]Asset{binAsset}, // no checksums.txt listed
		},
		{
			"checksums.txt asset listed but fails to fetch",
			[]Asset{binAsset, {Name: "checksums.txt", URL: srv.URL + "/missing-checksums.txt"}},
		},
		{
			"checksums.txt fetched but has no entry for this asset",
			[]Asset{binAsset, {Name: "checksums.txt", URL: srv.URL + "/checksums-other-asset.txt"}},
		},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			if _, err := DownloadBinary(context.Background(), binAsset, c.assets); err == nil {
				t.Error("expected a hard error rather than installing an unverified download")
			}
		})
	}
}

func TestReplaceExecutable(t *testing.T) {
	dir := t.TempDir()
	dst := filepath.Join(dir, "junto")
	if err := os.WriteFile(dst, []byte("old"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := ReplaceExecutable(dst, []byte("new binary")); err != nil {
		t.Fatalf("ReplaceExecutable: %v", err)
	}
	got, err := os.ReadFile(dst)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != "new binary" {
		t.Errorf("contents = %q, want %q", got, "new binary")
	}
	fi, _ := os.Stat(dst)
	if fi.Mode()&0o100 == 0 {
		t.Error("replaced file should be executable")
	}
}

func makeTarGz(t *testing.T, files map[string][]byte) []byte {
	t.Helper()
	var buf bytes.Buffer
	gw := gzip.NewWriter(&buf)
	tw := tar.NewWriter(gw)
	for name, content := range files {
		if err := tw.WriteHeader(&tar.Header{Name: name, Mode: 0o755, Size: int64(len(content)), Typeflag: tar.TypeReg}); err != nil {
			t.Fatal(err)
		}
		if _, err := tw.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	tw.Close()
	gw.Close()
	return buf.Bytes()
}
