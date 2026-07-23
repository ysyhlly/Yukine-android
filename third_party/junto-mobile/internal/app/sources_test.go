package app

import (
	"strings"
	"testing"
	"unicode/utf8"
)

func TestIsURL(t *testing.T) {
	urls := []string{"http://example.com/v.mp4", "https://youtu.be/x"}
	for _, u := range urls {
		if !isURL(u) {
			t.Errorf("isURL(%q) = false, want true", u)
		}
	}
	notURLs := []string{"movie.mkv", "./clip.mp4", "/abs/path.mp4", "-", "C:\\v.mp4", "ftp://x", "magnet:?xt=urn:x"}
	for _, p := range notURLs {
		if isURL(p) {
			t.Errorf("isURL(%q) = true, want false", p)
		}
	}
}

// TestShortURLTruncatesOnRuneBoundary is the regression test for the
// invalid-UTF-8 bug: shortURL sliced the string at a fixed byte offset
// (39), which could land in the middle of a multi-byte UTF-8 character
// and produce invalid UTF-8. This input places a 2-byte rune (é) at
// bytes 38-39 after the scheme is stripped — exactly straddling the old
// cut point, so u[:39] used to keep only é's leading byte.
func TestShortURLTruncatesOnRuneBoundary(t *testing.T) {
	const prefix = "example.com/" // 12 bytes once "https://" is stripped
	long := "https://" + prefix + strings.Repeat("a", 26) + "é" + strings.Repeat("b", 20)
	got := shortURL(long)
	if !utf8.ValidString(got) {
		t.Fatalf("shortURL produced invalid UTF-8: %q", got)
	}
	if !strings.HasSuffix(got, "…") {
		t.Errorf("expected truncation to end with an ellipsis, got %q", got)
	}
}

func TestShortURLStripsSchemeAndWWW(t *testing.T) {
	cases := map[string]string{
		"https://example.com/v":     "example.com/v",
		"http://example.com/v":      "example.com/v",
		"https://www.example.com/v": "example.com/v",
	}
	for in, want := range cases {
		if got := shortURL(in); got != want {
			t.Errorf("shortURL(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestShortURLLeavesShortURLsUntouched(t *testing.T) {
	short := "example.com/short"
	if got := shortURL("https://" + short); got != short {
		t.Errorf("shortURL should not truncate a URL already under the limit, got %q", got)
	}
}
