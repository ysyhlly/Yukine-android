// Package doctor preflight-checks everything a watch party needs —
// mpv, relay reachability, NAT traversal, the TURN fallback, and disk
// space — and prints a fix for whatever fails. It exists to turn
// "junto doesn't work" into a checklist anyone can act on.
package doctor

import (
	"context"
	"fmt"
	"os/exec"
	"strings"
	"sync"
	"time"

	"github.com/nbd-wtf/go-nostr"
	"github.com/pion/webrtc/v4"

	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/transfer"
)

const (
	relayTimeout  = 6 * time.Second
	gatherTimeout = 12 * time.Second
	// lowDiskBytes is the threshold below which the disk check warns: a
	// typical movie is 1–8 GiB and streams to disk in full.
	lowDiskBytes = 5 << 30
)

// Options selects what to check. All fields are required.
type Options struct {
	MpvPath        string
	YtDlpPath      string             // yt-dlp binary, for the optional URL-source check
	Relays         []string           // nostr relay URLs
	Direct         transfer.ICEConfig // STUN-only config, for the direct-connection check
	Relayed        transfer.ICEConfig // config including user-supplied -turn, if any
	BuiltinRelayed transfer.ICEConfig // config including the built-in relay, if fetched
	OutDir         string             // where downloads would land, for the disk check
	TelemetryOn    bool               // whether anonymous telemetry would fire (for the status row)
}

// result is one check's outcome. ok=false with a fix is a failure the
// user can act on; a warning keeps ok=true but still shows a hint.
type result struct {
	name   string
	ok     bool
	detail string
	fix    string
}

// Run executes every check and prints a checklist via printf, one
// result at a time as each check completes rather than all at once at
// the end — checkRelays fans out concurrently and checkICE's ICE
// gathering passes can each take up to gatherTimeout, so buffering
// every result until the last one finishes could leave the user staring
// at a blank screen for the better part of a minute. It returns true
// when nothing failed (warnings don't count as failures).
func Run(ctx context.Context, opts Options, printf func(string, ...any)) bool {
	var mu sync.Mutex
	var results []result
	print := func(r result) {
		mu.Lock()
		defer mu.Unlock()
		mark := "✓"
		if !r.ok {
			mark = "✗"
		}
		printf("%s %-18s %s", mark, r.name, r.detail)
		if r.fix != "" {
			printf("  %-18s fix: %s", "", r.fix)
		}
		results = append(results, r)
	}

	mpvResult := checkMpv(ctx, opts.MpvPath)
	print(mpvResult)
	relayResults := checkRelays(ctx, opts.Relays, print)
	iceResults := checkICE(ctx, opts.Direct, opts.Relayed, opts.BuiltinRelayed, print)
	print(checkDisk(opts.OutDir))
	print(checkYtDlp(ctx, opts.YtDlpPath))
	print(checkFfmpeg(ctx))
	print(checkTelemetry(opts.TelemetryOn))

	mu.Lock()
	allOK := true
	for _, r := range results {
		if !r.ok {
			allOK = false
		}
	}
	mu.Unlock()

	printf("")
	printf(streamingSummary(allOK, mpvResult, relayResults, iceResults))
	return allOK
}

// streamingSummary computes the final status line. It distinguishes
// between "all clean", "safe to stream despite minor issues", and
// "not ready". Streaming requires mpv, at least one reachable relay,
// and at least one working P2P path (STUN or TURN).
func streamingSummary(allOK bool, mpv result, relays, ice []result) string {
	if allOK {
		return "✓ safe to stream — all checks passed"
	}

	relayOK := false
	for _, r := range relays {
		if r.ok {
			relayOK = true
			break
		}
	}
	p2pOK := false
	for _, r := range ice {
		if r.ok {
			p2pOK = true
			break
		}
	}

	if !mpv.ok {
		return "✗ not ready to stream — mpv is required; fix the error above first"
	}
	if !relayOK {
		return "✗ not ready to stream — no nostr relays reachable; fix the errors above first"
	}
	if !p2pOK {
		return "✗ not ready to stream — no P2P path available; fix the error above first"
	}

	// Core is fine; count the non-critical failures for context.
	var failures int
	for _, r := range relays {
		if !r.ok {
			failures++
		}
	}
	noun := "issue"
	if failures > 1 {
		noun = "issues"
	}
	return fmt.Sprintf("✓ safe to stream — %d non-critical %s above won't block a watch party", failures, noun)
}

// checkMpv verifies the player binary exists and runs.
func checkMpv(ctx context.Context, mpvPath string) result {
	bin, err := exec.LookPath(mpvPath)
	if err != nil {
		return result{name: "mpv", detail: fmt.Sprintf("%q not found on PATH", mpvPath),
			fix: "install it (`brew install mpv` on macOS, your package manager on Linux), or point junto at it with -mpv-path"}
	}
	vctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(vctx, bin, "--version").Output()
	if err != nil {
		return result{name: "mpv", detail: fmt.Sprintf("%s exists but won't run: %v", bin, err),
			fix: "reinstall mpv"}
	}
	version, _, _ := strings.Cut(string(out), "\n")
	return result{name: "mpv", ok: true, detail: fmt.Sprintf("%s (%s)", strings.TrimSpace(version), bin)}
}

// checkYtDlp reports whether yt-dlp is available. It's optional — only
// needed for `junto create <url>` — so absence is a warning, not a
// failure (ok stays true).
func checkYtDlp(ctx context.Context, ytDlpPath string) result {
	bin := ytDlpPath
	if bin == "" {
		bin = "yt-dlp"
	}
	path, err := exec.LookPath(bin)
	if err != nil {
		return result{name: "yt-dlp", ok: true, detail: "not found — only needed for `junto create <url>`",
			fix: "install it (`brew install yt-dlp`, or `pipx install yt-dlp`) to host YouTube/other URLs"}
	}
	vctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(vctx, path, "--version").Output()
	if err != nil {
		return result{name: "yt-dlp", ok: true, detail: fmt.Sprintf("%s exists but won't run: %v", path, err),
			fix: "reinstall yt-dlp"}
	}
	return result{name: "yt-dlp", ok: true, detail: fmt.Sprintf("%s (%s)", strings.TrimSpace(string(out)), path)}
}

// checkFfmpeg reports whether ffmpeg is available. yt-dlp needs it to
// merge separate HD video+audio streams; absence is a warning.
func checkFfmpeg(ctx context.Context) result {
	path, err := exec.LookPath("ffmpeg")
	if err != nil {
		return result{name: "ffmpeg", ok: true, detail: "not found — yt-dlp needs it to merge HD video+audio",
			fix: "install ffmpeg (`brew install ffmpeg`) for the best URL quality; without it, URL sources fall back to lower-res single-file streams"}
	}
	return result{name: "ffmpeg", ok: true, detail: path}
}

// checkTelemetry reports whether anonymous telemetry would fire. It's
// informational (always ok) — junto never phones home without disclosure,
// so surfacing the current setting and how to change it belongs in the
// preflight.
func checkTelemetry(on bool) result {
	if on {
		return result{name: "telemetry", ok: true,
			detail: "on — anonymous usage stats (version, OS, outcome, relay latency); no PII, no room secret, no file names",
			fix:    "disable with --no-telemetry or  telemetry = false  in config.toml"}
	}
	return result{name: "telemetry", ok: true, detail: "off — nothing is sent"}
}

// checkRelays dials every nostr relay concurrently and reports each with
// its round-trip latency, invoking onResult as each dial completes
// (rather than only once every relay has finished) so a caller can print
// progress instead of holding it all until the slowest relay times out.
func checkRelays(ctx context.Context, relays []string, onResult func(result)) []result {
	results := make([]result, len(relays))
	var wg sync.WaitGroup
	for i, url := range relays {
		wg.Add(1)
		go func(i int, url string) {
			defer wg.Done()
			rctx, cancel := context.WithTimeout(ctx, relayTimeout)
			defer cancel()
			start := time.Now()
			r, err := nostr.RelayConnect(rctx, url)
			var res result
			if err != nil {
				res = result{name: "nostr relay", detail: fmt.Sprintf("%s — %v", url, err),
					fix: "check your internet connection, or swap it out with -relays"}
			} else {
				ms := time.Since(start).Milliseconds()
				r.Close()
				res = result{
					name:   "nostr relay",
					ok:     true,
					detail: fmt.Sprintf("%s — reachable (%d ms)", url, ms),
				}
			}
			results[i] = res
			onResult(res)
		}(i, url)
	}
	wg.Wait()
	return results
}

// checkICE gathers ICE candidates for each path and reports whether
// direct connections, a user-supplied TURN relay, and the built-in
// relay are possible from this network. Each gathering pass can take up
// to gatherTimeout, so onResult is invoked as each of the (up to three)
// results is ready instead of only after the last one completes.
func checkICE(ctx context.Context, direct, relayed, builtin transfer.ICEConfig, onResult func(result)) []result {
	var results []result
	add := func(r result) {
		results = append(results, r)
		onResult(r)
	}

	types, err := gatherCandidateTypes(ctx, direct.Servers)
	switch {
	case err != nil:
		add(result{name: "direct p2p (STUN)", detail: err.Error(),
			fix: "check your internet connection and firewall"})
	case len(types[webrtc.ICECandidateTypeSrflx]) > 0:
		add(result{name: "direct p2p (STUN)", ok: true,
			detail: "reachable — your public address is " + types[webrtc.ICECandidateTypeSrflx][0]})
	default:
		add(result{name: "direct p2p (STUN)",
			detail: "no public address found (UDP may be blocked) — direct transfers won't work from this network",
			fix:    "route transfers through a TURN relay with -turn, or fetch files another way and join with them"})
	}

	// User-supplied -turn (only shown when explicitly configured).
	if relayed.HasRelay() {
		types, err = gatherCandidateTypes(ctx, relayed.Servers)
		switch {
		case err != nil:
			add(result{name: "relay (-turn)", detail: err.Error(),
				fix: "check your internet connection"})
		case len(types[webrtc.ICECandidateTypeRelay]) > 0:
			add(result{name: "relay (-turn)", ok: true,
				detail: "reachable — transfers will work even when hole-punching fails"})
		default:
			add(result{name: "relay (-turn)",
				detail: "no relay candidate — the relay may be down, or the credentials wrong",
				fix:    "double-check -turn/-turn-user/-turn-pass"})
		}
	}

	// Built-in relay (always shown; ok:true when unavailable so it
	// doesn't block "safe to stream" for something outside the user's control).
	if !builtin.HasRelay() {
		add(result{name: "relay (built-in)", ok: true,
			detail: "unavailable — credential fetch timed out (direct connections only)"})
		return results
	}
	types, err = gatherCandidateTypes(ctx, builtin.Servers)
	switch {
	case err != nil:
		add(result{name: "relay (built-in)", detail: err.Error(),
			fix: "check your internet connection"})
	case len(types[webrtc.ICECandidateTypeRelay]) > 0:
		add(result{name: "relay (built-in)", ok: true,
			detail: "reachable — junto will fall back automatically if hole-punching fails"})
	default:
		add(result{name: "relay (built-in)",
			detail: "no relay candidate — the built-in relay may be temporarily down",
			fix:    "add -turn to supply your own relay"})
	}
	return results
}

// gatherCandidateTypes runs one ICE gathering pass against servers and
// returns the candidate addresses found, grouped by type (host, srflx
// for STUN, relay for TURN).
func gatherCandidateTypes(ctx context.Context, servers []webrtc.ICEServer) (map[webrtc.ICECandidateType][]string, error) {
	pc, err := webrtc.NewPeerConnection(webrtc.Configuration{ICEServers: servers})
	if err != nil {
		return nil, fmt.Errorf("creating peer connection: %w", err)
	}
	defer pc.Close()

	var mu sync.Mutex
	types := make(map[webrtc.ICECandidateType][]string)
	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		mu.Lock()
		types[c.Typ] = append(types[c.Typ], c.Address)
		mu.Unlock()
	})

	if _, err := pc.CreateDataChannel("probe", nil); err != nil {
		return nil, fmt.Errorf("creating data channel: %w", err)
	}
	offer, err := pc.CreateOffer(nil)
	if err != nil {
		return nil, fmt.Errorf("creating offer: %w", err)
	}
	if err := pc.SetLocalDescription(offer); err != nil {
		return nil, fmt.Errorf("starting gathering: %w", err)
	}

	select {
	case <-webrtc.GatheringCompletePromise(pc):
	case <-time.After(gatherTimeout):
	case <-ctx.Done():
		return nil, ctx.Err()
	}
	mu.Lock()
	defer mu.Unlock()
	return types, nil
}

// checkDisk reports free space where downloads would land.
func checkDisk(dir string) result {
	free, err := DiskFree(dir)
	switch {
	case err != nil:
		return result{name: "disk space", detail: fmt.Sprintf("couldn't stat %s: %v", dir, err),
			fix: "make sure the download directory exists (-out)"}
	case free < lowDiskBytes:
		return result{name: "disk space", ok: true,
			detail: fmt.Sprintf("%s free in %s — tight; a movie is typically 1–8 GiB", human.Bytes(int64(free)), dir)}
	default:
		return result{name: "disk space", ok: true,
			detail: fmt.Sprintf("%s free in %s", human.Bytes(int64(free)), dir)}
	}
}
