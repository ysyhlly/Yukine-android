package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/swayam-mishra/junto/internal/config"
)

func TestParseFlagsAnywhere(t *testing.T) {
	cases := []struct {
		name    string
		args    []string
		wantPos []string
		wantOut string
	}{
		{"flags first", []string{"-out", "d", "code"}, []string{"code"}, "d"},
		{"flags after", []string{"code", "-out", "d"}, []string{"code"}, "d"},
		{"flags between", []string{"a.mkv", "-out", "d", "b.mkv"}, []string{"a.mkv", "b.mkv"}, "d"},
		{"no flags", []string{"code", "f1", "f2"}, []string{"code", "f1", "f2"}, "."},
		{"only flags", []string{"-out", "d"}, nil, "d"},
		{"double dash stops parsing", []string{"code", "--", "-weird.mkv"}, []string{"code", "-weird.mkv"}, "."},
		{"double dash after flags", []string{"-out", "d", "--", "-x"}, []string{"-x"}, "d"},
		{"empty", nil, nil, "."},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			fs := flag.NewFlagSet("test", flag.ContinueOnError)
			fs.SetOutput(io.Discard)
			out := fs.String("out", ".", "")
			pos, err := parseFlagsAnywhere(fs, c.args)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if !reflect.DeepEqual(pos, c.wantPos) {
				t.Errorf("positionals = %v, want %v", pos, c.wantPos)
			}
			if *out != c.wantOut {
				t.Errorf("-out = %q, want %q", *out, c.wantOut)
			}
		})
	}

	// An unknown flag still errors.
	fs := flag.NewFlagSet("test", flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	if _, err := parseFlagsAnywhere(fs, []string{"code", "-bogus"}); err == nil {
		t.Error("unknown flag should error")
	}
}

// TestRunCreateChecksMpvBeforeDownloading is the regression test for the
// wasted-download bug: `junto create <url>` used to resolve (and
// potentially download, multi-GB) the URL via yt-dlp before ever
// checking whether mpv is even installed, so a missing mpv was only
// discovered after a long download completed for nothing. checkMpvEarly
// must run first — a missing mpv must fail before yt-dlp is ever
// invoked.
func TestRunCreateChecksMpvBeforeDownloading(t *testing.T) {
	dir := t.TempDir()
	ytdlpCalled := filepath.Join(dir, "ytdlp-was-called")
	stub := filepath.Join(dir, "fake-yt-dlp")
	script := "#!/bin/sh\ntouch \"" + ytdlpCalled + "\"\nexit 1\n"
	if err := os.WriteFile(stub, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}

	args := []string{
		"-mpv-path", filepath.Join(dir, "definitely-not-a-real-mpv-binary"),
		"-yt-dlp-path", stub,
		"https://example.com/video",
	}
	err := runCreate(context.Background(), args, config.Config{})
	if err == nil {
		t.Fatal("expected an error (mpv missing)")
	}
	if !strings.Contains(err.Error(), "mpv not found") {
		t.Errorf("expected an mpv-not-found error, got: %v", err)
	}
	if _, statErr := os.Stat(ytdlpCalled); statErr == nil {
		t.Error("yt-dlp ran before the mpv check — a missing mpv must be caught before starting a download")
	}
}

// TestOutcomeOfTreatsCancellationAsNotAFailure is the regression test
// for the misleading-telemetry bug: a user-initiated Ctrl-C (or the
// equivalent timeout) used to be recorded as outcome "failure", just
// like a real bug, skewing failure-rate telemetry with every
// intentional quit.
func TestOutcomeOfTreatsCancellationAsNotAFailure(t *testing.T) {
	if got := outcomeOf(nil); got != "success" {
		t.Errorf("outcomeOf(nil) = %q, want success", got)
	}
	if got := outcomeOf(context.Canceled); got != "cancelled" {
		t.Errorf("outcomeOf(context.Canceled) = %q, want cancelled", got)
	}
	if got := outcomeOf(context.DeadlineExceeded); got != "cancelled" {
		t.Errorf("outcomeOf(context.DeadlineExceeded) = %q, want cancelled", got)
	}
	wrapped := fmt.Errorf("waiting for host: %w", context.Canceled)
	if got := outcomeOf(wrapped); got != "cancelled" {
		t.Errorf("outcomeOf(wrapped context.Canceled) = %q, want cancelled", got)
	}
	if got := outcomeOf(fmt.Errorf("boom")); got != "failure" {
		t.Errorf("outcomeOf(a real error) = %q, want failure", got)
	}
}
