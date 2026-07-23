// junto synchronizes mpv playback between friends over public
// nostr relays, optionally sending the media file itself peer-to-peer.
package main

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"strings"
	"syscall"

	"github.com/swayam-mishra/junto/internal/app"
	"github.com/swayam-mishra/junto/internal/config"
	jdebug "github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/doctor"
	"github.com/swayam-mishra/junto/internal/nostrx"
	"github.com/swayam-mishra/junto/internal/selfupdate"
	"github.com/swayam-mishra/junto/internal/telemetry"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// Injected by goreleaser at release time (-X main.version=...).
var (
	version = "dev"
	commit  = "none"
	date    = "unknown"
)

// issuesURL is where users report bugs. junto is in active development;
// a crash (panic) is always a bug worth reporting.
const issuesURL = "https://github.com/swayam-mishra/junto/issues/new"

func main() {
	// A panic is an unexpected failure (a real bug, not user error) — catch
	// it, point the user at the issue tracker, and include the details that
	// make a report actionable. Expected, user-actionable failures go
	// through the err path below with their own plain-English messages and
	// are not treated as bugs.
	defer func() {
		if r := recover(); r != nil {
			fmt.Fprintf(os.Stderr, `
junto hit an unexpected error — that's a bug, not your fault.
Please report it so we can fix it for everyone:
  %s
Include your junto version (%s) and what you were doing. Details:

%v
%s`, issuesURL, version, r, debug.Stack())
			os.Exit(1)
		}
	}()

	if len(os.Args) < 2 {
		usage()
		os.Exit(2)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	var err error
	switch os.Args[1] {
	case "create", "join", "doctor":
		// Persisted defaults; explicit flags override them. A malformed
		// file is fatal, not silently ignored — like an unknown config
		// key, it must surface immediately rather than quietly falling
		// back to built-in defaults, which for telemetry = false would
		// mean silently discarding the user's opt-out. Loaded only here
		// (not for version/update/uninstall/help) so an unrelated config
		// typo doesn't block commands that never consult it.
		fileCfg, cfgErr := config.Load()
		if cfgErr != nil {
			fmt.Fprintf(os.Stderr, "error: %s: %v\n", config.Path(), cfgErr)
			os.Exit(1)
		}
		switch os.Args[1] {
		case "create":
			err = runCreate(ctx, os.Args[2:], fileCfg)
		case "join":
			err = runJoin(ctx, os.Args[2:], fileCfg)
		case "doctor":
			err = runDoctor(ctx, os.Args[2:], fileCfg)
		}
	case "update":
		err = runUpdate(ctx, os.Args[2:])
	case "uninstall":
		err = runUninstall(ctx, os.Args[2:])
	case "version", "-v", "-version", "--version":
		fmt.Printf("junto %s (commit %s, built %s)\n", version, commit, date)
	case "-h", "--help", "help":
		usage()
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n", os.Args[1])
		usage()
		os.Exit(2)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

type commonOpts struct {
	relays, mpvPath, nick    *string
	turn, turnUser, turnPass *string
	debugLog                 *bool
}

// commonFlags defines the shared flags, using the config file's values
// as defaults so an explicit flag overrides the file and the file
// overrides the built-in default.
func commonFlags(fs *flag.FlagSet, c config.Config) commonOpts {
	nickDefault := c.Nick
	if nickDefault == "" {
		nickDefault = os.Getenv("USER")
	}
	mpvDefault := c.MpvPath
	if mpvDefault == "" {
		mpvDefault = "mpv"
	}
	return commonOpts{
		relays:   fs.String("relays", strings.Join(c.Relays, ","), "comma-separated relay URLs (default: "+strings.Join(nostrx.DefaultRelays, ",")+")"),
		mpvPath:  fs.String("mpv-path", mpvDefault, "path to the mpv binary"),
		nick:     fs.String("nick", nickDefault, "name shown to other watchers"),
		turn:     fs.String("turn", c.Turn, "TURN relay URL for when direct connection fails (e.g. turn:host:3478)"),
		turnUser: fs.String("turn-user", c.TurnUser, "TURN username"),
		turnPass: fs.String("turn-pass", c.TurnPass, "TURN password"),
		debugLog: fs.Bool("debug", false, "write structured debug log to ~/.cache/junto/debug-<timestamp>.log"),
	}
}

func buildConfig(o commonOpts) app.Config {
	cfg := app.Config{
		MpvPath:  *o.mpvPath,
		Nick:     *o.nick,
		OutDir:   ".",
		TurnURL:  *o.turn,
		TurnUser: *o.turnUser,
		TurnPass: *o.turnPass,
	}
	if *o.relays != "" {
		for _, r := range strings.Split(*o.relays, ",") {
			if r = strings.TrimSpace(r); r != "" {
				cfg.Relays = append(cfg.Relays, r)
			}
		}
	}
	if *o.debugLog {
		cfg.Log = jdebug.New(config.CacheDir())
	}
	return cfg
}

// parseFlagsAnywhere parses fs against args accepting flags before,
// between, and after positional arguments — `junto join <code> -out d`
// works the same as `junto join -out d <code>`. The standard library
// stops at the first positional, which silently turns trailing flags
// into file names. A bare "--" still ends flag parsing for good.
func parseFlagsAnywhere(fs *flag.FlagSet, args []string) ([]string, error) {
	var pos []string
	for len(args) > 0 {
		// Peel off leading positionals ("-" alone is stdin-style, positional).
		if args[0] == "-" || !strings.HasPrefix(args[0], "-") {
			pos = append(pos, args[0])
			args = args[1:]
			continue
		}
		if args[0] == "--" {
			return append(pos, args[1:]...), nil
		}
		if err := fs.Parse(args); err != nil {
			return nil, err
		}
		rest := fs.Args()
		// If Parse stopped by consuming a "--" terminator, the rest is
		// positional verbatim.
		if consumed := len(args) - len(rest); consumed > 0 && args[consumed-1] == "--" {
			return append(pos, rest...), nil
		}
		args = rest
	}
	return pos, nil
}

func runCreate(ctx context.Context, args []string, fileCfg config.Config) error {
	fs := flag.NewFlagSet("create", flag.ExitOnError)
	opts := commonFlags(fs, fileCfg)
	noTransfer := fs.Bool("no-transfer", false, "do not offer the file to joiners")
	quality := fs.String("quality", "", "max quality for URL sources: 1080p|720p|480p|best|audio (default 1080p)")
	ytDlpPath := fs.String("yt-dlp-path", "", "path to the yt-dlp binary (default: yt-dlp on PATH)")
	noTelemetry := fs.Bool("no-telemetry", false, "disable anonymous usage telemetry")
	fs.Usage = func() {
		fmt.Fprintln(os.Stderr, "usage: junto create [flags] <file|url>...")
		fs.PrintDefaults()
	}
	srcs, err := parseFlagsAnywhere(fs, args)
	if err != nil {
		return err
	}
	if len(srcs) < 1 {
		fs.Usage()
		os.Exit(2)
	}
	// Fail fast on a missing mpv before committing to a potentially
	// multi-GB yt-dlp download — there's no point downloading a video
	// this process can't play.
	if err := checkMpvEarly(*opts.mpvPath); err != nil {
		return err
	}
	// Resolve any http(s) URL to a local file via yt-dlp before hosting;
	// local paths pass through. A Ctrl-C during a download is a clean exit.
	files, err := app.ResolveURLs(ctx, srcs, app.FetchOptions{YtDlpPath: *ytDlpPath, Quality: *quality})
	if err != nil {
		if ctx.Err() != nil {
			return nil
		}
		return err
	}
	cfg := buildConfig(opts)
	cfg.NoTransfer = *noTransfer
	defer cfg.Log.Close()

	telEnabled := !fileCfg.NoTelemetry && !*noTelemetry
	if telEnabled {
		cfgDir := filepath.Dir(config.Path())
		telemetry.PrintNoticeIfFirstRun(cfgDir, func(f string, a ...any) { fmt.Printf(f+"\n", a...) })
		cfg.Telemetry = telemetry.New("create")
	}
	err = hintDoctor(app.Create(ctx, files, cfg))
	if telEnabled {
		cfg.Telemetry.Fire(version, outcomeOf(err), telemetry.ClassifyError(err))
	}
	return err
}

func runJoin(ctx context.Context, args []string, fileCfg config.Config) error {
	fs := flag.NewFlagSet("join", flag.ExitOnError)
	opts := commonFlags(fs, fileCfg)
	outDir := fs.String("out", ".", "directory for downloaded files")
	noTelemetry := fs.Bool("no-telemetry", false, "disable anonymous usage telemetry")
	fs.Usage = func() {
		fmt.Fprintln(os.Stderr, "usage: junto join [flags] <roomcode> [file...]")
		fs.PrintDefaults()
	}
	pos, err := parseFlagsAnywhere(fs, args)
	if err != nil {
		return err
	}
	if len(pos) < 1 {
		fs.Usage()
		os.Exit(2)
	}
	cfg := buildConfig(opts)
	cfg.OutDir = *outDir
	defer cfg.Log.Close()
	if err := checkMpvEarly(cfg.MpvPath); err != nil {
		return err
	}

	telEnabled := !fileCfg.NoTelemetry && !*noTelemetry
	if telEnabled {
		cfgDir := filepath.Dir(config.Path())
		telemetry.PrintNoticeIfFirstRun(cfgDir, func(f string, a ...any) { fmt.Printf(f+"\n", a...) })
		cfg.Telemetry = telemetry.New("join")
	}
	err = hintDoctor(app.Join(ctx, pos[0], pos[1:], cfg))
	if telEnabled {
		cfg.Telemetry.Fire(version, outcomeOf(err), telemetry.ClassifyError(err))
	}
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return nil // Ctrl-C while waiting for the host: a clean exit, not an error to report
	}
	return err
}

func outcomeOf(err error) string {
	if err == nil {
		return "success"
	}
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		// A user-initiated Ctrl-C (or the equivalent timeout) isn't a
		// product failure — recording it as one skews failure-rate
		// telemetry with every intentional quit.
		return "cancelled"
	}
	return "failure"
}

// checkMpvEarly verifies mpv is on PATH before attempting any relay
// connections, so the user gets an immediate, actionable error instead
// of a ~10 s timeout followed by a cryptic failure.
func checkMpvEarly(mpvPath string) error {
	if _, err := exec.LookPath(mpvPath); err != nil {
		return fmt.Errorf("mpv not found — install it first:\n" +
			"  macOS:  brew install mpv\n" +
			"  Linux:  apt/dnf/pacman install mpv\n" +
			"or point junto at it with -mpv-path\n" +
			"  run `junto doctor` for a full preflight check")
	}
	return nil
}

// hintDoctor wraps connection/relay/NAT errors with a suggestion to run
// junto doctor, which gives the user a complete diagnostic in one step.
func hintDoctor(err error) error {
	if err == nil {
		return nil
	}
	msg := strings.ToLower(err.Error())
	for _, kw := range []string{"relay", "connect", "peer-to-peer", "no one answered", "lost connection", "couldn't reach"} {
		if strings.Contains(msg, kw) {
			return fmt.Errorf("%w\n  run `junto doctor` to diagnose connection and relay issues", err)
		}
	}
	return err
}

func runDoctor(ctx context.Context, args []string, fileCfg config.Config) error {
	fs := flag.NewFlagSet("doctor", flag.ExitOnError)
	opts := commonFlags(fs, fileCfg)
	outDir := fs.String("out", ".", "directory downloads would land in")
	ytDlpPath := fs.String("yt-dlp-path", "", "path to the yt-dlp binary (default: yt-dlp on PATH)")
	fs.Usage = func() {
		fmt.Fprintln(os.Stderr, "usage: junto doctor [flags]")
		fs.PrintDefaults()
	}
	if _, err := parseFlagsAnywhere(fs, args); err != nil {
		return err
	}
	cfg := buildConfig(opts)
	relays := cfg.Relays
	if len(relays) == 0 {
		relays = nostrx.DefaultRelays
	}
	direct := transfer.DefaultICEConfig()
	// Fetch built-in relay credentials synchronously: doctor is a
	// diagnostic tool and the user expects a complete picture. The 1 s
	// timeout in InitBuiltinRelay bounds the wait.
	transfer.InitBuiltinRelay(ctx)
	relayed := direct.WithTURN(cfg.TurnURL, cfg.TurnUser, cfg.TurnPass)
	builtin := direct.WithFallbackRelay()
	ok := doctor.Run(ctx, doctor.Options{
		MpvPath:        cfg.MpvPath,
		YtDlpPath:      *ytDlpPath,
		Relays:         relays,
		Direct:         direct,
		Relayed:        relayed,
		BuiltinRelayed: builtin,
		OutDir:         *outDir,
		TelemetryOn:    !fileCfg.NoTelemetry,
	}, func(format string, a ...any) { fmt.Printf(format+"\n", a...) })
	if !ok {
		os.Exit(1)
	}
	return nil
}

// runUpdate checks GitHub for a newer release. For a standalone binary
// it downloads and swaps it in place; Homebrew and `go install` builds
// are handed back to their own update command.
func runUpdate(ctx context.Context, args []string) error {
	fs := flag.NewFlagSet("update", flag.ExitOnError)
	yes := fs.Bool("y", false, "skip the confirmation prompt")
	fs.Usage = func() {
		fmt.Fprintln(os.Stderr, "usage: junto update [-y]")
		fs.PrintDefaults()
	}
	if err := fs.Parse(args); err != nil {
		return err
	}

	tag, assets, err := selfupdate.LatestRelease(ctx)
	if err != nil {
		return err
	}
	if selfupdate.SameVersion(version, tag) {
		fmt.Printf("already on the latest (%s)\n", tag)
		return nil
	}

	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("finding the junto binary: %w", err)
	}

	switch method := selfupdate.InstallMethod(exe); {
	case method == selfupdate.Brew:
		fmt.Printf("a newer junto is available (%s → %s).\n", version, tag)
		fmt.Println("junto was installed with Homebrew — update it with:\n  brew upgrade swayam-mishra/tap/junto")
		return nil
	case method == selfupdate.GoInstall || version == "dev":
		fmt.Printf("a newer junto is available (%s → %s).\n", version, tag)
		fmt.Println("install it with:\n  go install github.com/swayam-mishra/junto/cmd/junto@latest")
		return nil
	}

	asset, ok := selfupdate.AssetFor(runtime.GOOS, runtime.GOARCH, assets)
	if !ok {
		return fmt.Errorf("no %s/%s build in release %s — download it from https://github.com/swayam-mishra/junto/releases/latest", runtime.GOOS, runtime.GOARCH, tag)
	}
	if !*yes && !confirm(fmt.Sprintf("update junto %s → %s?", version, tag)) {
		fmt.Println("cancelled.")
		return nil
	}
	fmt.Println("downloading…")
	bin, err := selfupdate.DownloadBinary(ctx, asset, assets)
	if err != nil {
		return err
	}
	if err := selfupdate.ReplaceExecutable(exe, bin); err != nil {
		return err
	}
	fmt.Printf("updated to %s — run `junto version` to confirm\n", tag)
	return nil
}

// runUninstall removes junto. A Homebrew install is handed back to brew;
// a standalone/go-install binary is deleted, optionally along with the
// config and cache directories.
func runUninstall(ctx context.Context, args []string) error {
	fs := flag.NewFlagSet("uninstall", flag.ExitOnError)
	yes := fs.Bool("y", false, "skip confirmation prompts")
	purge := fs.Bool("purge", false, "also remove ~/.config/junto and ~/.cache/junto")
	fs.Usage = func() {
		fmt.Fprintln(os.Stderr, "usage: junto uninstall [-y] [-purge]")
		fs.PrintDefaults()
	}
	if err := fs.Parse(args); err != nil {
		return err
	}

	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("finding the junto binary: %w", err)
	}
	if selfupdate.InstallMethod(exe) == selfupdate.Brew {
		fmt.Println("junto was installed with Homebrew — remove it with:\n  brew uninstall junto")
		fmt.Println("(that leaves the mpv dependency in place)")
		return nil
	}

	if !*yes && !confirm(fmt.Sprintf("remove the junto binary at %s?", exe)) {
		fmt.Println("cancelled.")
		return nil
	}
	if err := os.Remove(exe); err != nil {
		return fmt.Errorf("removing %s (need permission?): %w\ntry: sudo junto uninstall", exe, err)
	}
	fmt.Println("removed the junto binary.")

	removeData := *purge
	if !*purge && !*yes {
		removeData = confirm("also remove junto's config and cached downloads (~/.config/junto, ~/.cache/junto)?")
	}
	if removeData {
		var dirs []string
		if p := config.Path(); p != "" {
			dirs = append(dirs, filepath.Dir(p))
		}
		if cd := config.CacheDir(); cd != "" {
			dirs = append(dirs, cd)
		}
		for _, d := range dirs {
			if err := os.RemoveAll(d); err != nil {
				fmt.Fprintf(os.Stderr, "  couldn't remove %s: %v\n", d, err)
			} else {
				fmt.Printf("  removed %s\n", d)
			}
		}
	}
	fmt.Println("junto uninstalled. (mpv/yt-dlp/ffmpeg, if installed separately, are untouched.)")
	return nil
}

// confirm prompts the user for a yes/no answer on stdin, defaulting to no.
func confirm(prompt string) bool {
	fmt.Printf("%s [y/N]: ", prompt)
	sc := bufio.NewScanner(os.Stdin)
	if !sc.Scan() {
		return false
	}
	a := strings.ToLower(strings.TrimSpace(sc.Text()))
	return a == "y" || a == "yes"
}

func usage() {
	fmt.Fprint(os.Stderr, `junto — watch media in sync with friends, no server needed

usage:
  junto create [flags] <file|url>...          host a room around files or yt-dlp URLs
  junto join   [flags] <roomcode>             join; stream the files P2P from the host
  junto join   [flags] <roomcode> <file>...   join with your own copies of the files
  junto doctor [flags]                        check mpv, relays, NAT, and disk space
  junto update [-y]                           update to the latest release
  junto uninstall [-y] [-purge]               remove junto (and optionally its data)
  junto version                               print the version

Flags may appear anywhere: junto join <roomcode> -out dir also works.

A create argument that's an http(s) URL is downloaded via yt-dlp (any
site yt-dlp supports) into ~/.cache/junto and hosted like a local file,
so "junto create https://youtu.be/..." works (needs yt-dlp, and ffmpeg
to merge HD streams).

Everyone starts paused; the host presses Enter to start the room once
peers are ready. Playback in the mpv window — play, pause, seek, speed,
subtitle track and delay, playlist switches — is mirrored to everyone.
Typed lines are chat. Coordination travels encrypted over public nostr
relays; the files themselves go directly peer-to-peer. A joiner without
local copies starts watching immediately while the files download.

flags (after the subcommand): -relays, -mpv-path, -nick, -turn/-turn-user/-turn-pass,
  -no-transfer/-quality/-yt-dlp-path (create), -out (join)

junto is in active development — hit a bug or something unusual?
Please report it: https://github.com/swayam-mishra/junto/issues/new
`)
}
