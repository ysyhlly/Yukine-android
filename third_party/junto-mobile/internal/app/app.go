// Package app wires the pieces into the two session flows: hosting a
// room (create) and joining one, including the optional P2P file fetch
// before playback starts.
package app

import (
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"
	"time"

	"github.com/swayam-mishra/junto/internal/debug"
	"github.com/swayam-mishra/junto/internal/doctor"
	"github.com/swayam-mishra/junto/internal/human"
	"github.com/swayam-mishra/junto/internal/mpv"
	"github.com/swayam-mishra/junto/internal/nostrx"
	"github.com/swayam-mishra/junto/internal/protocol"
	"github.com/swayam-mishra/junto/internal/room"
	"github.com/swayam-mishra/junto/internal/streamserver"
	"github.com/swayam-mishra/junto/internal/syncer"
	"github.com/swayam-mishra/junto/internal/telemetry"
	"github.com/swayam-mishra/junto/internal/transfer"
	"github.com/swayam-mishra/junto/internal/tui"
)

type Config struct {
	Relays     []string
	MpvPath    string
	Nick       string
	OutDir     string
	NoTransfer bool
	TurnURL    string
	TurnUser   string
	TurnPass   string
	Log        *debug.Logger
	Telemetry  *telemetry.Collector
}

func (c Config) iceConfig() transfer.ICEConfig {
	return transfer.DefaultICEConfig().WithTURN(c.TurnURL, c.TurnUser, c.TurnPass)
}

// Create hosts a new room around mediaPaths (a playlist, in order) and
// runs the session until the user quits.
// plainHooks routes session output through the app's existing terminal
// writer (the non-TUI path). Nil Lines/OnSnapshot/SessionInfo mean the
// session behaves exactly as before the TUI existed.
func plainHooks() tui.Hooks {
	return tui.Hooks{
		Printf:        printLine,
		ShowProgress:  showProgress,
		ClearProgress: term.ClearStatus,
	}
}

// sendInfo pushes session metadata into the TUI info panel, if a TUI is
// running (no-op on the plain path).
func sendInfo(h tui.Hooks, si tui.SessionInfo) {
	if h.SessionInfo != nil {
		h.SessionInfo(si)
	}
}

// fileNames returns the display names of a flat playlist, for the info
// panel.
func fileNames(metas []protocol.FileMeta) []string {
	names := make([]string, len(metas))
	for i, m := range metas {
		names[i] = m.Name
	}
	return names
}

// Create hosts a new room around mediaPaths (a playlist, in order) and
// runs the session until the user quits. On a real terminal it runs
// inside the interactive TUI; otherwise it uses the plain line writer.
func Create(ctx context.Context, mediaPaths []string, cfg Config) error {
	if tui.UseTUI() {
		return tui.RunSession(ctx, func(ctx context.Context, h tui.Hooks) error {
			return createInner(ctx, mediaPaths, cfg, h)
		})
	}
	return createInner(ctx, mediaPaths, cfg, plainHooks())
}

func createInner(ctx context.Context, mediaPaths []string, cfg Config, h tui.Hooks) error {
	// Shadow the package-level output funcs with the hooks for this
	// session, so the body below is unchanged whether it runs plain or in
	// the TUI.
	printLine := h.Printf
	go transfer.InitBuiltinRelay(ctx)
	var infos []os.FileInfo
	for i, p := range mediaPaths {
		abs, err := filepath.Abs(p)
		if err != nil {
			return err
		}
		info, err := os.Stat(abs)
		if err != nil {
			return fmt.Errorf("can't open that media file: %w", err)
		}
		mediaPaths[i] = abs
		infos = append(infos, info)
	}

	// metas are the announced videos (each carrying its discovered
	// subtitle sidecars); flatPaths is the file the host serves for each
	// transfer index, in flattenSubs order (subs before their video).
	// flatMetas is that same flattened list (not just the paths), kept in
	// scope for the upload-capacity tracker's per-index bitrate lookups.
	var metas []protocol.FileMeta
	var flatPaths []string
	var flatMetas []protocol.FileMeta
	var flatObs [][]byte // bao outboard trees, aligned with flatPaths
	if !cfg.NoTransfer {
		// Joiners download every file into one directory, so names — both
		// videos and subtitles — must not collide.
		seen := map[string]bool{}
		note := func(name string) error {
			if seen[name] {
				return fmt.Errorf("duplicate file name %q — joiners' downloads would collide; rename one or use -no-transfer", name)
			}
			seen[name] = true
			return nil
		}
		var byName = map[string]string{} // file name -> abs path
		obByName := map[string][]byte{}  // file name -> bao outboard tree
		stamp := func(m *protocol.FileMeta, h transfer.FileHashes) {
			m.SHA256 = h.SHA256
			m.Bao = h.BaoRoot
			m.BaoGroup = h.BaoGroup
			m.BaoOb = h.BaoObSHA
			obByName[m.Name] = h.Outboard
		}
		allSubs := discoverSubsForVideos(mediaPaths)
		for i, p := range mediaPaths {
			if err := note(filepath.Base(p)); err != nil {
				return err
			}
			printLine("hashing %d/%d: %s...", i+1, len(mediaPaths), filepath.Base(p))
			hashes, err := transfer.HashForServe(p)
			if err != nil {
				return fmt.Errorf("hashing %s: %w", p, err)
			}
			vm := protocol.FileMeta{Name: filepath.Base(p), Size: infos[i].Size()}
			stamp(&vm, hashes)
			if dur, ok := transfer.ProbeDuration(p, vm.Size); ok {
				vm.DurationSecs = dur
				cfg.Log.E("duration_probed", "idx", i, "secs", int64(dur))
			} else {
				cfg.Log.E("duration_unknown", "idx", i)
			}
			byName[vm.Name] = p
			for _, sp := range allSubs[i] {
				si, err := os.Stat(sp)
				if err != nil {
					continue
				}
				if err := note(filepath.Base(sp)); err != nil {
					return err
				}
				shashes, err := transfer.HashForServe(sp)
				if err != nil {
					return fmt.Errorf("hashing %s: %w", sp, err)
				}
				sm := protocol.FileMeta{Name: filepath.Base(sp), Size: si.Size()}
				stamp(&sm, shashes)
				vm.Subs = append(vm.Subs, sm)
				byName[filepath.Base(sp)] = sp
				printLine("  + subtitles: %s", filepath.Base(sp))
			}
			metas = append(metas, vm)
		}
		// Order the served paths exactly as both sides flatten the metas,
		// so a joiner's file-req index maps to the right file here.
		flatMetas, _, _ = flattenSubs(metas)
		for _, fm := range flatMetas {
			flatPaths = append(flatPaths, byName[fm.Name])
			flatObs = append(flatObs, obByName[fm.Name])
		}
	}

	rm, err := room.New()
	if err != nil {
		return err
	}
	link := "https://junto.watch/join#" + rm.Code()
	// Fill the TUI info panel now that the room identity and playlist are
	// known (no-op on the plain path, which prints the lines below).
	sendInfo(h, tui.SessionInfo{
		RoomCode: rm.Code(),
		JoinLink: link,
		Nick:     cfg.Nick,
		Host:     true,
		Playlist: fileNames(metas),
	})
	printLine("room code: %s", rm.Code())
	printLine("join link: %s", link)
	printLine("           (anyone with the link or code can join and decrypt the room — share only with your watch party)")
	if copyToClipboard(link) {
		printLine("           (link copied to your clipboard — paste it to your friends; the page shows them how to join)")
	}
	for _, m := range metas {
		if m.DurationSecs > 0 {
			bitrate := float64(m.Size) / m.DurationSecs
			printLine("serving:   %s (%s, ~%s/s)", m.Name, human.Bytes(m.Size), human.Bytes(int64(bitrate)))
		} else {
			printLine("serving:   %s (%s)", m.Name, human.Bytes(m.Size))
		}
	}

	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		return fmt.Errorf("setting up the relay connection: %w", err)
	}
	t.SetLogger(cfg.Log)
	defer func() {
		if cfg.Telemetry != nil {
			cfg.Telemetry.SetRelayStats(relaySnaps(t.RelayStats()))
		}
		t.Close()
	}()

	cfg.Log.E("session_start", "event", "create", "relays", len(cfg.Relays))
	iceCfg := cfg.iceConfig()
	router := &signalRouter{t: t, chans: make(map[string]chan transfer.AddressedSignal)}
	var onFileReq func(string, int, int64, int64)
	if len(flatPaths) > 0 {
		tracker := newUploadTracker(flatMetas, printLine, cfg.Log)
		// One fairness coordinator for the whole host, shared across every
		// concurrent viewer's serve, so the uplink favors whoever's closest
		// to stalling.
		fair := transfer.NewUploadFairness()
		onFileReq = func(from string, idx int, _, _ int64) {
			if idx < 0 || idx >= len(flatPaths) {
				printLine("* %.8s requested file %d which doesn't exist", from, idx)
				return
			}
			go func() {
				// One persistent serve per request: byte ranges are negotiated
				// on the data channel, so offset/length on the relay message
				// are unused. A reconnect arrives as a new request; the router
				// replaces this peer's signal channel for it.
				sig, ch := router.open(from)
				defer router.release(from, ch)
				stop := tracker.start(idx)
				defer stop()
				reportRate := func(r float64) { tracker.report(idx, r) }
				ob := func(context.Context) ([]byte, error) { return flatObs[idx], nil }
				if err := transfer.ServeFile(ctx, sig, from, flatPaths[idx], iceCfg, printLine, cfg.Log, reportRate, ob, fair, idx); err != nil {
					printLine("* transfer to %.8s failed: %v", from, err)
				}
			}()
		}
	}

	return runSession(ctx, t, mediaPaths, metas, true, cfg, onFileReq, router.deliver, nil, nil, nil, sessionExtras{}, h)
}

// Join enters an existing room. With no localPaths, the host's files
// are streamed: mpv plays them via a local HTTP server while they
// download in the background. On a real terminal it runs inside the
// interactive TUI; otherwise it uses the plain line writer.
func Join(ctx context.Context, code string, localPaths []string, cfg Config) error {
	if tui.UseTUI() {
		return tui.RunSession(ctx, func(ctx context.Context, h tui.Hooks) error {
			return joinInner(ctx, code, localPaths, cfg, h)
		})
	}
	return joinInner(ctx, code, localPaths, cfg, plainHooks())
}

func joinInner(ctx context.Context, code string, localPaths []string, cfg Config, h tui.Hooks) error {
	printLine := h.Printf
	rm, err := room.Parse(code)
	if err != nil {
		return err
	}
	sendInfo(h, tui.SessionInfo{
		RoomCode: rm.Code(),
		JoinLink: "https://junto.watch/join#" + rm.Code(),
		Nick:     cfg.Nick,
	})
	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		return fmt.Errorf("setting up the relay connection: %w", err)
	}
	t.SetLogger(cfg.Log)
	defer func() {
		if cfg.Telemetry != nil {
			cfg.Telemetry.SetRelayStats(relaySnaps(t.RelayStats()))
		}
		t.Close()
	}()
	cfg.Log.E("session_start", "event", "join", "relays", len(cfg.Relays))

	if len(localPaths) > 0 {
		for i, p := range localPaths {
			abs, err := filepath.Abs(p)
			if err != nil {
				return err
			}
			if _, err := os.Stat(abs); err != nil {
				return fmt.Errorf("can't open that media file: %w", err)
			}
			localPaths[i] = abs
		}
		// Build a name→path lookup so the OnBecomeHost closure can map the
		// original host's FileMeta names to our local file paths.
		byName := make(map[string]string, len(localPaths))
		for _, p := range localPaths {
			byName[filepath.Base(p)] = p
		}
		iceCfg := cfg.iceConfig()
		router := &signalRouter{t: t, chans: make(map[string]chan transfer.AddressedSignal)}
		// Serving state, built once the room's announced playlist is known
		// (OnHostFiles) so the local-file joiner can seed the swarm — and
		// answer direct requests — while the original host is still around.
		// Promotion later reuses the exact same handlers.
		var serveState atomic.Pointer[localServe]
		setupServe := func(files []protocol.FileMeta) *localServe {
			if ls := serveState.Load(); ls != nil {
				return ls
			}
			ls := newLocalServe(files, byName, printLine, cfg.Log)
			serveState.Store(ls)
			return ls
		}
		gate := newServeGate(maxServeConns)
		fair := transfer.NewUploadFairness() // shared across this node's viewers
		onFileReq := func(from string, idx int, _, _ int64) {
			ls := serveState.Load()
			if ls == nil || idx < 0 || idx >= len(ls.flatPaths) || ls.flatPaths[idx] == "" {
				printLine("* %.8s requested file %d which doesn't exist locally", from, idx)
				return
			}
			if !gate.tryAcquire(from, idx) {
				cfg.Log.E("serve_rejected", "peer", from[:min(8, len(from))], "idx", idx, "reason", "at_capacity")
				return
			}
			go func() {
				defer gate.release(from, idx)
				sig, ch := router.open(from)
				defer router.release(from, ch)
				stop := ls.tracker.start(idx)
				defer stop()
				reportRate := func(r float64) { ls.tracker.report(idx, r) }
				if err := transfer.ServeFile(ctx, sig, from, ls.flatPaths[idx], iceCfg, printLine, cfg.Log, reportRate, ls.outboards.forIndex(idx), fair, idx); err != nil {
					printLine("* transfer to %.8s failed: %v", from, err)
				}
			}()
		}
		onBecomeHost := func(files []protocol.FileMeta) (
			func(string, int, int64, int64),
			func(string, protocol.Signal),
		) {
			setupServe(files) // usually already built by OnHostFiles
			return onFileReq, router.deliver
		}
		// Advertise the matching local copies as complete on each heartbeat
		// so streaming joiners pick this peer as a swarm source without
		// waiting for a host drop.
		haveSnapshot := func() ([]protocol.HaveEntry, [][2]int) {
			if ls := serveState.Load(); ls != nil {
				return nil, ls.doneRuns
			}
			return nil, nil
		}
		// onHostFiles validates the user's local copies against the room's
		// announced playlist. files are the host's videos (each with its own
		// nested subtitles), in the same order as the room's playlist, so
		// localPaths[i] should correspond to files[i]. Names and sizes are
		// compared (instant); SHA-256 is not, to keep join fast. It also
		// builds the serving state above, since this is the first moment
		// the room's flat file list is known.
		onHostFiles := func(files []protocol.FileMeta) {
			setupServe(files)
			if len(files) != len(localPaths) {
				printLine("* heads up: this room has %d file(s) but you provided %d — the playlist may not line up", len(files), len(localPaths))
			}
			for i, fm := range files {
				if i >= len(localPaths) {
					break
				}
				p := localPaths[i]
				fi, err := os.Stat(p)
				if err != nil {
					printLine("* can't read local file %q: %v", filepath.Base(p), err)
					continue
				}
				if filepath.Base(p) != fm.Name {
					printLine("* file %d: you have %q but the room is playing %q — playback may be out of sync", i+1, filepath.Base(p), fm.Name)
				}
				if fi.Size() != fm.Size {
					printLine("* file %d (%s): your copy is %s, the room's is %s — likely a different cut, expect desync", i+1, fm.Name, human.Bytes(fi.Size()), human.Bytes(fm.Size))
				}
			}
		}
		return runSession(ctx, t, localPaths, nil, false, cfg, onFileReq, router.deliver, nil, nil, nil,
			sessionExtras{canHost: true, onBecomeHost: onBecomeHost, onHostFiles: onHostFiles, haveSnapshot: haveSnapshot}, h)
	}
	return joinStreaming(ctx, t, code, cfg, h)
}

// joinStreaming finds the host, then launches mpv against a local HTTP
// server immediately while a background Downloader fills the playlist
// files in order. The session engine is the sole reader of the room
// inbox; it forwards the host's WebRTC signals to the Downloader via
// the OnSignal hook (no competing consumer of t.Receive()).
func joinStreaming(ctx context.Context, t *nostrx.Transport, code string, cfg Config, h tui.Hooks) error {
	printLine := h.Printf
	go transfer.InitBuiltinRelay(ctx)
	if err := t.Send(ctx, protocol.Message{Type: protocol.MsgHello, Nick: cfg.Nick}); err != nil {
		return fmt.Errorf("couldn't reach any relay to join the room — check your internet connection: %w", err)
	}
	host, videos, err := waitForHost(ctx, t)
	if err != nil {
		return hostWaitError(ctx, err, code)
	}
	// The room's playlist is now known — fill it into the info panel and
	// mark this a streaming session.
	sendInfo(h, tui.SessionInfo{Streaming: true, Playlist: fileNames(videos)})
	checkDiskSpace(printLine, cfg.OutDir, videos)

	// Flatten videos + their shared subtitles into the transfer list both
	// sides agree on; only the videos become playlist URLs for mpv.
	flat, videoIdx, subsFor := flattenSubs(videos)
	store, err := transfer.NewFileStore(cfg.OutDir, flat)
	if err != nil {
		return err
	}
	ss, err := streamserver.New(store)
	if err != nil {
		return err
	}
	ss.SetPlayable(videoIdx)
	defer func() {
		shctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = ss.Close(shctx)
	}()

	sigCh := make(chan transfer.AddressedSignal, 8)
	var dl *transfer.Downloader
	sendReq := func(c context.Context, peer string, i int, off, length int64) error {
		return t.Send(c, protocol.Message{Type: protocol.MsgFileReq, To: peer, Nick: cfg.Nick, FileIndex: i, Offset: off, Length: length})
	}
	dl = transfer.NewDownloader(&transportSignaler{t: t, ch: sigCh}, host, store, cfg.iceConfig(), sendReq, printLine)
	dl.SetLogger(cfg.Log)
	dl.OnProgress(h.ShowProgress)
	// Swarm: aux sources get per-peer signal channels via dlRouter (the
	// primary keeps sigCh — its offers are filtered to the current host
	// in onSignal below, so the two flows never eat each other's SDP).
	dlRouter := &signalRouter{t: t, chans: make(map[string]chan transfer.AddressedSignal)}
	dl.EnableSwarm(func(peer string) (transfer.Signaler, func()) {
		sig, ch := dlRouter.open(peer)
		return sig, func() { dlRouter.release(peer, ch) }
	})

	// Shared subtitles: when a downloaded subtitle file completes, hand
	// its local path to the engine to load into mpv for the right
	// playlist item.
	subCh := make(chan syncer.SubReady, 8)
	subOwner := map[int]int{} // flat sub index -> playlist position
	for vp, subs := range subsFor {
		for _, si := range subs {
			subOwner[si] = vp
		}
	}
	if len(subOwner) > 0 {
		dl.OnFileDone(func(flatIdx int) {
			if vp, ok := subOwner[flatIdx]; ok {
				select {
				case subCh <- syncer.SubReady{Index: vp, Path: store.PathFor(flatIdx)}:
				default:
				}
			}
		})
	}

	// When mpv reads past the download frontier the stream server stalls
	// and steers the downloader to that offset via Prioritize. The room
	// pause itself is driven by mpv's paused-for-cache property (observed
	// in the sync engine), not by the stall, so onStall is nil here.
	ss.SetStallReporter(nil, dl.Prioritize)
	dl.SetPlayheadFunc(ss.CurrentReadPos)
	ss.Start()

	ttffStart := time.Now()
	dlCtx, cancelDL := context.WithCancel(ctx)
	defer cancelDL()
	go func() {
		err := dl.Run(dlCtx)
		if h.ClearProgress != nil {
			h.ClearProgress()
		}
		if cfg.Telemetry != nil {
			cfg.Telemetry.RecordTransferOutcome(err != nil && dlCtx.Err() == nil)
		}
		if err != nil && dlCtx.Err() == nil {
			printLine("* download failed: %v", err)
			printLine("  (you can re-run with the files downloaded another way: junto join %s <file>...)", code)
		}
	}()
	// Signals are routed by kind: sources serving US send offers (the
	// downloader answers), peers WE serve send answers to our offers (the
	// serve router fans them out per peer). Splitting on kind keeps the
	// two flows from consuming each other's SDP when the same peer is on
	// both sides of a transfer at once.
	serveRouter := &signalRouter{t: t, chans: make(map[string]chan transfer.AddressedSignal)}
	onSignal := func(from string, sig protocol.Signal) {
		switch sig.Kind {
		case "offer":
			// Offers are from peers serving US: the current host's go to
			// the primary's channel, everyone else's to the per-source
			// router (dropped unless an aux dial is waiting on that peer).
			if from == dl.GetHost() {
				select {
				case sigCh <- transfer.AddressedSignal{From: from, Sig: sig}:
				default:
				}
				return
			}
			dlRouter.deliver(from, sig)
		case "answer":
			serveRouter.deliver(from, sig)
		}
	}
	// Serve what we have to any peer that asks (verified bytes only — the
	// store's coverage gate naks the rest). This is how a completed
	// streaming joiner feeds late joiners after promotion, with the same
	// handler doing double duty before promotion for direct requests.
	gate := newServeGate(maxServeConns)
	tracker := newUploadTracker(flat, printLine, cfg.Log)
	fair := transfer.NewUploadFairness() // shared across this node's viewers
	iceCfg := cfg.iceConfig()
	onFileReq := func(from string, idx int, _, _ int64) {
		if idx < 0 || idx >= store.Len() {
			cfg.Log.E("serve_rejected", "peer", from[:min(8, len(from))], "idx", idx, "reason", "bad_index")
			return
		}
		if !gate.tryAcquire(from, idx) {
			cfg.Log.E("serve_rejected", "peer", from[:min(8, len(from))], "idx", idx, "reason", "at_capacity")
			return
		}
		go func() {
			defer gate.release(from, idx)
			sig, ch := serveRouter.open(from)
			defer serveRouter.release(from, ch)
			stop := tracker.start(idx)
			defer stop()
			reportRate := func(r float64) { tracker.report(idx, r) }
			if err := transfer.ServeFromStore(ctx, sig, from, store, idx, iceCfg, printLine, cfg.Log, reportRate, fair); err != nil {
				cfg.Log.E("serve_from_store_failed", "peer", from[:min(8, len(from))], "idx", idx, "err", err.Error())
			}
		}()
	}
	// Hold the readiness gate until a real cushion of contiguous bytes is
	// buffered from the start of the first playable file, so the host
	// can't open the gate before the joiner can actually play. The cushion
	// is sized to a few seconds of the file's own bitrate when it's known
	// (mp4/mov only, from the host's announced DurationSecs) and falls
	// back to the previous fixed 4 MB floor otherwise (PreBufferBytes).
	firstMeta := store.Meta(videoIdx[0])
	preBuffer := transfer.PreBufferBytes(firstMeta)
	bitrateBps := 0.0
	if firstMeta.DurationSecs > 0 {
		bitrateBps = float64(firstMeta.Size) / firstMeta.DurationSecs
	}
	cfg.Log.E("prebuffer_sized", "bytes", preBuffer, "bitrate_known", firstMeta.DurationSecs > 0)
	readyCh := make(chan struct{})
	go signalReadyOrFail(dlCtx, readyCh, func(ctx context.Context) error {
		return store.WaitBuffered(ctx, videoIdx[0], preBuffer)
	})

	printLine("* streaming %d file(s) from %.8s while you watch", len(videos), host)
	// TTFF: time from joinStreaming entry to mpv launch (runSession call).
	if cfg.Telemetry != nil {
		cfg.Telemetry.RecordTTFF(time.Since(ttffStart))
	}
	return runSession(ctx, t, ss.URLs(), nil, false, cfg, onFileReq, onSignal, nil, store.Progress, subCh,
		sessionExtras{
			onHostChange: func(newPub string) { dl.SetHost(newPub) },
			readyCh:      readyCh,
			bitrateBps:   bitrateBps,
			currentRate:  dl.CurrentRate,
			// A fully-downloaded streaming joiner can step up as host: the
			// engine advertises eligibility once every file is done and
			// verified, and promotion just keeps the serving handlers that
			// are already wired above.
			canHostDynamic: store.AllDone,
			// Swarm coverage: advertise what we hold on each heartbeat, and
			// track everyone else's advertisements for source selection.
			haveSnapshot: store.AdvertSnapshot,
			onPeerHave:   dl.UpdatePeerHave,
			onPeerGone:   dl.PeerGone,
			onBecomeHost: func([]protocol.FileMeta) (
				func(string, int, int64, int64),
				func(string, protocol.Signal),
			) {
				return onFileReq, onSignal
			},
		}, h)
}

// diskFreeFn is doctor.DiskFree, overridable in tests so a low- or
// plenty-of-space scenario doesn't depend on the real test machine's
// actual free space.
var diskFreeFn = doctor.DiskFree

// checkDiskSpace warns (does not block joining) when the playlist's
// total announced size exceeds the free space at dir — at the 20 GB+
// file sizes this project targets, a streaming joiner would otherwise
// only discover this via a write error deep into a multi-hour transfer.
// A disk-free lookup failure (e.g. dir doesn't exist yet) isn't this
// check's concern and is silently skipped; the download itself will
// surface that problem clearly the moment it tries to create the file.
func checkDiskSpace(printf func(string, ...any), dir string, videos []protocol.FileMeta) {
	free, err := diskFreeFn(dir)
	if err != nil {
		return
	}
	var total int64
	for _, v := range videos {
		total += v.Size
		for _, s := range v.Subs {
			total += s.Size
		}
	}
	if total > 0 && uint64(total) > free {
		printf("* heads up: this room needs %s but %s has %s free — the download will likely run out of space partway through",
			human.Bytes(total), dir, human.Bytes(int64(free)))
	}
}

// signalReadyOrFail runs wait (WaitBuffered) and closes readyCh once
// it's safe to stop holding the readiness gate: either the buffer
// target was reached, or the download failed for a real reason while
// the session is still live. It only leaves readyCh open when wait
// failed because dlCtx itself is done — the session is ending, so
// nothing is listening on readyCh anymore anyway. Without the failure
// case also closing it, a download that dies mid-session (host gone,
// disk full, unrecoverable corruption) left this peer permanently stuck
// "not ready", even though the room keeps running and needs every peer
// to clear the gate.
func signalReadyOrFail(dlCtx context.Context, readyCh chan struct{}, wait func(context.Context) error) {
	err := wait(dlCtx)
	if err != nil && dlCtx.Err() != nil {
		return
	}
	close(readyCh)
}

// waitForHost reads the room until someone's hello announces files.
func waitForHost(ctx context.Context, t *nostrx.Transport) (string, []protocol.FileMeta, error) {
	timeout := time.NewTimer(10 * time.Second)
	defer timeout.Stop()
	sawAnyone := false
	for {
		select {
		case <-ctx.Done():
			return "", nil, ctx.Err()
		case <-timeout.C:
			if sawAnyone {
				return "", nil, fmt.Errorf("someone's in the room but no one is sharing files yet — is the host still starting up?")
			}
			return "", nil, fmt.Errorf("nobody answered on that room code — check the host is running and the code is exactly right")
		case m, ok := <-t.Receive():
			if !ok {
				return "", nil, fmt.Errorf("lost connection to all relays")
			}
			sawAnyone = true
			if m.Type == protocol.MsgHello && len(m.Files) > 0 {
				return m.From, m.Files, nil
			}
		}
	}
}

// hostWaitError turns a waitForHost failure into the error joinStreaming
// returns. A Ctrl-C while waiting is a clean exit, not a real failure —
// the "get the files another way" retry hint would be irrelevant noise
// for someone who just quit on purpose, and (via errors.Is upstream)
// must stay recognizable as a cancellation rather than a plain error.
func hostWaitError(ctx context.Context, err error, code string) error {
	if ctx.Err() != nil {
		return ctx.Err()
	}
	return fmt.Errorf("%w\nget the files another way, then run: junto join %s <file>...", err, code)
}

// sessionExtras holds the host-migration callbacks. Using a struct avoids
// growing runSession's already-long parameter list; callers that don't need
// migration pass a zero value.
type sessionExtras struct {
	canHost        bool
	canHostDynamic func() bool // live eligibility; streaming joiners pass store.AllDone
	onBecomeHost   func([]protocol.FileMeta) (func(string, int, int64, int64), func(string, protocol.Signal))
	onHostChange   func(string)
	onHostFiles    func([]protocol.FileMeta)
	readyCh        <-chan struct{}
	bitrateBps     float64        // known bitrate of the file gating the pre-buffer; 0 = unknown
	currentRate    func() float64 // measured download throughput; nil unless streaming
	// Swarm coverage plumbing (streaming joiners only).
	haveSnapshot func() ([]protocol.HaveEntry, [][2]int)
	onPeerHave   func(string, []protocol.HaveEntry, [][2]int)
	onPeerGone   func(string)
}

func runSession(ctx context.Context, t *nostrx.Transport, mediaPaths []string, metas []protocol.FileMeta, host bool, cfg Config, onFileReq func(string, int, int64, int64), onSignal func(string, protocol.Signal), buffer <-chan bool, downloadProgress func() float64, subs <-chan syncer.SubReady, extras sessionExtras, h tui.Hooks) error {
	printLine := h.Printf
	// A streaming joiner (the only caller that passes a download-progress
	// hook) reads from the local stream server as bytes arrive, so give
	// mpv a large demuxer buffer to ride out download jitter.
	mpvc, err := mpv.Launch(ctx, mpv.Options{MpvPath: cfg.MpvPath, MediaPaths: mediaPaths, StartPaused: true, NetworkCache: downloadProgress != nil})
	if err != nil {
		return err
	}
	defer mpvc.Close()
	mpvc.SetLogger(cfg.Log)

	// The TUI feeds its own input channel; the plain path spawns the
	// stdin scanner exactly as before.
	lines := h.Lines
	if lines == nil {
		ch := make(chan string)
		go func() {
			scanner := bufio.NewScanner(os.Stdin)
			for scanner.Scan() {
				ch <- scanner.Text()
			}
			if err := scanner.Err(); err != nil {
				// Report stdin read errors to stderr.
				fmt.Fprintln(os.Stderr, "stdin:", err)
			}
			// stdin EOF: leave the channel open; /quit, Ctrl-C or closing
			// mpv still end the session.
		}()
		lines = ch
	}

	printLine("* connected — type to chat; /pause /play /seek <time> /speed <rate> /peers /quit")
	engine := syncer.New(syncer.Deps{
		Mpv:              mpvc,
		Send:             t.Send,
		Inbox:            t.Receive(),
		Receiving:        t.Receiving,
		Lines:            lines,
		Printf:           printLine,
		OnSnapshot:       h.OnSnapshot,
		SelfPub:          t.SelfPubKey(),
		Nick:             cfg.Nick,
		Host:             host,
		Files:            metas,
		PlaylistLen:      len(mediaPaths),
		Colors:           detectColors(),
		OnFileReq:        onFileReq,
		OnSignal:         onSignal,
		Buffer:           buffer,
		DownloadProgress: downloadProgress,
		Subs:             subs,
		Log:              cfg.Log,
		CanHost:          extras.canHost,
		CanHostDynamic:   extras.canHostDynamic,
		OnBecomeHost:     extras.onBecomeHost,
		OnHostChange:     extras.onHostChange,
		OnHostFiles:      extras.onHostFiles,
		ReadyCh:          extras.readyCh,
		Bitrate:          extras.bitrateBps,
		CurrentRate:      extras.currentRate,
		HaveSnapshot:     extras.haveSnapshot,
		OnPeerHave:       extras.onPeerHave,
		OnPeerGone:       extras.onPeerGone,
	})
	err = engine.Run(ctx)
	if err == context.Canceled || err == context.DeadlineExceeded {
		err = nil
	}
	printLine("* bye")
	return err
}

// detectColors enables nick colors only on a real terminal that hasn't
// opted out.
func detectColors() bool {
	if os.Getenv("NO_COLOR") != "" || os.Getenv("TERM") == "dumb" {
		return false
	}
	return isTTY(os.Stdout)
}

// signalRouter fans incoming WebRTC signals out to the per-joiner
// transfer goroutines on the host.
type signalRouter struct {
	t     *nostrx.Transport
	mu    sync.Mutex
	chans map[string]chan transfer.AddressedSignal
}

// open registers a fresh signal channel for peer, replacing any stale
// one (e.g. from a transfer that dropped and is being resumed). It
// returns the channel so release can verify identity.
func (r *signalRouter) open(peer string) (transfer.Signaler, chan transfer.AddressedSignal) {
	ch := make(chan transfer.AddressedSignal, 4)
	r.mu.Lock()
	r.chans[peer] = ch
	r.mu.Unlock()
	return &transportSignaler{t: r.t, ch: ch}, ch
}

// release removes peer's channel only if it's still the one ch refers
// to — so a resumed transfer that already replaced it isn't clobbered
// when the old, dead serve goroutine finally exits.
func (r *signalRouter) release(peer string, ch chan transfer.AddressedSignal) {
	r.mu.Lock()
	if r.chans[peer] == ch {
		delete(r.chans, peer)
	}
	r.mu.Unlock()
}

// deliver is called from the engine loop and must not block.
func (r *signalRouter) deliver(from string, s protocol.Signal) {
	r.mu.Lock()
	ch := r.chans[from]
	r.mu.Unlock()
	if ch == nil {
		return
	}
	select {
	case ch <- transfer.AddressedSignal{From: from, Sig: s}:
	default:
	}
}

// maxServeConns caps how many transfers a streaming joiner will serve at
// once — its upstream also feeds its own watching, and a malicious room
// member could otherwise spam file-reqs to open unbounded connections. A
// var per the test-shrinkability convention.
var maxServeConns = 4

// localServe is the serving state a local-file joiner builds once the
// room's playlist is known: which flat transfer index maps to which local
// path (name+size matched — a mismatched copy is never served, it could
// only fail verification or desync a fetcher), the lazily-computed
// outboard trees, and the HaveDone ranges advertised on heartbeats.
type localServe struct {
	flat      []protocol.FileMeta
	flatPaths []string
	outboards *outboardCache
	tracker   *uploadTracker
	doneRuns  [][2]int
}

func newLocalServe(files []protocol.FileMeta, byName map[string]string, printf func(string, ...any), log *debug.Logger) *localServe {
	flat, _, _ := flattenSubs(files)
	ls := &localServe{flat: flat, flatPaths: make([]string, len(flat))}
	for i, fm := range flat {
		p, ok := byName[fm.Name]
		if !ok {
			printf("* warning: can't find local file %q — won't serve it to new joiners", fm.Name)
			continue
		}
		if fi, err := os.Stat(p); err != nil || fi.Size() != fm.Size {
			// The size-mismatch warning itself is printed by onHostFiles;
			// here it just disqualifies the copy from being served.
			continue
		}
		ls.flatPaths[i] = p
	}
	// Contiguous runs of serveable indices become the heartbeat's
	// HaveDone advertisement (bounded by the wire cap; excess runs are
	// simply not advertised — under-advertising is always safe).
	runStart := -1
	for i := 0; i <= len(ls.flatPaths); i++ {
		if i < len(ls.flatPaths) && ls.flatPaths[i] != "" {
			if runStart < 0 {
				runStart = i
			}
			continue
		}
		if runStart >= 0 && len(ls.doneRuns) < protocol.MaxHaveDone {
			ls.doneRuns = append(ls.doneRuns, [2]int{runStart, i})
		}
		runStart = -1
	}
	ls.outboards = newOutboardCache(flat, ls.flatPaths, log)
	ls.tracker = newUploadTracker(flat, printf, log)
	log.E("local_serve_ready", "files", len(flat), "serveable", len(ls.doneRuns))
	return ls
}

// serveGate enforces the streaming joiner's serve caps: at most
// maxServeConns concurrent serves in total, and one per (peer, file) —
// a reconnect arrives as a new request after the old serve ends.
type serveGate struct {
	mu     sync.Mutex
	active map[string]bool
	max    int
}

func newServeGate(max int) *serveGate {
	return &serveGate{active: make(map[string]bool), max: max}
}

func (g *serveGate) key(peer string, idx int) string { return fmt.Sprintf("%s/%d", peer, idx) }

func (g *serveGate) tryAcquire(peer string, idx int) bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	k := g.key(peer, idx)
	if g.active[k] || len(g.active) >= g.max {
		return false
	}
	g.active[k] = true
	return true
}

func (g *serveGate) release(peer string, idx int) {
	g.mu.Lock()
	delete(g.active, g.key(peer, idx))
	g.mu.Unlock()
}

// outboardCache lazily computes bao outboard trees for locally-held
// files, so a promoted local-file host can answer ob_req in a room whose
// metas were minted by the original host. Per index the tree is computed
// at most once (a full sequential read of the file) and served only when
// the recomputed root matches the room's announced one — a mismatched
// copy yields naks instead of a tree that could never verify, and
// joiners keep rejecting its mismatched bytes against the original root.
type outboardCache struct {
	metas []protocol.FileMeta // flat order, matching paths/idx
	paths []string
	log   *debug.Logger
	once  []sync.Once
	obs   [][]byte
}

func newOutboardCache(metas []protocol.FileMeta, paths []string, log *debug.Logger) *outboardCache {
	return &outboardCache{metas: metas, paths: paths, log: log, once: make([]sync.Once, len(metas)), obs: make([][]byte, len(metas))}
}

// forIndex returns the OutboardFn to serve file idx with, or nil in a
// pre-swarm room (no root announced — nothing to serve or verify).
func (c *outboardCache) forIndex(idx int) transfer.OutboardFn {
	if c.metas[idx].Bao == "" || c.paths[idx] == "" {
		return nil
	}
	return func(context.Context) ([]byte, error) {
		c.once[idx].Do(func() {
			ob, err := transfer.OutboardForRoot(c.paths[idx], c.metas[idx].Bao)
			if err != nil {
				c.log.E("outboard_compute_failed", "idx", idx, "err", err.Error())
				return
			}
			c.log.E("outboard_computed", "idx", idx, "bytes", len(ob))
			c.obs[idx] = ob
		})
		if c.obs[idx] == nil {
			return nil, fmt.Errorf("no verification tree for this file")
		}
		return c.obs[idx], nil
	}
}

// uploadTracker estimates, from real measured upload throughput, whether
// the host can keep more than one streaming viewer fed at a file's known
// bitrate — no synthetic speed test: a lone viewer's own connection is the
// best available proxy for the host's deliverable upload before a second
// one ever joins. One instance per hosting session (Create(), or per
// host-promotion in Join()'s onBecomeHost); each concurrently-served
// joiner reports from its own ServeFile goroutine, so access is locked.
// Tracked per flat file index, since different playlist entries can have
// different bitrates.
type uploadTracker struct {
	mu     sync.Mutex
	metas  []protocol.FileMeta // flat order, matching flatPaths/idx
	files  map[int]*fileUploadState
	printf func(string, ...any)
	log    *debug.Logger
}

type fileUploadState struct {
	active   int     // viewers currently streaming this file index
	soloRate float64 // high-water EMA observed while active==1; 0 = not measured yet
	warned   bool
}

func newUploadTracker(metas []protocol.FileMeta, printf func(string, ...any), log *debug.Logger) *uploadTracker {
	return &uploadTracker{metas: metas, files: make(map[int]*fileUploadState), printf: printf, log: log}
}

func (u *uploadTracker) bitrateFor(idx int) float64 {
	if idx < 0 || idx >= len(u.metas) || u.metas[idx].DurationSecs <= 0 {
		return 0
	}
	return float64(u.metas[idx].Size) / u.metas[idx].DurationSecs
}

// start records a new streaming viewer of file idx and returns a func to
// call when it stops (deferred by the caller). A no-op when the file's
// bitrate isn't known — no prediction is possible, so there's nothing to
// track.
func (u *uploadTracker) start(idx int) (stop func()) {
	if u.bitrateFor(idx) <= 0 {
		return func() {}
	}
	u.mu.Lock()
	st := u.stateLocked(idx)
	st.active++
	u.maybeWarnLocked(idx, st)
	u.mu.Unlock()
	return func() {
		u.mu.Lock()
		st.active--
		u.mu.Unlock()
	}
}

// report records idx's latest measured upload EMA and re-evaluates the
// capacity warning. Only updates the solo high-water mark while exactly
// one viewer of this file is active — once a second joins, every
// connection's measured rate reflects a shared, contended upload, no
// longer a "how fast can one viewer go" measurement.
func (u *uploadTracker) report(idx int, rate float64) {
	if u.bitrateFor(idx) <= 0 {
		return
	}
	u.mu.Lock()
	defer u.mu.Unlock()
	st := u.stateLocked(idx)
	if st.active == 1 && rate > st.soloRate {
		st.soloRate = rate
	}
	u.maybeWarnLocked(idx, st)
}

func (u *uploadTracker) stateLocked(idx int) *fileUploadState {
	st, ok := u.files[idx]
	if !ok {
		st = &fileUploadState{}
		u.files[idx] = st
	}
	return st
}

// maybeWarnLocked fires the one-time oversubscription warning once at
// least two viewers of idx are streaming concurrently and the best rate
// ever observed for a single viewer can't cover all of them at the file's
// bitrate. mu must be held.
func (u *uploadTracker) maybeWarnLocked(idx int, st *fileUploadState) {
	if st.warned || st.active < 2 || st.soloRate <= 0 {
		return
	}
	bitrate := u.bitrateFor(idx)
	if st.soloRate >= float64(st.active)*bitrate {
		return
	}
	st.warned = true
	supports := int(st.soloRate / bitrate)
	if supports < 1 {
		supports = 1
	}
	u.log.E("upload_oversubscribed", "idx", idx, "active", st.active, "bitrate_bps", int64(bitrate), "solo_rate_bps", int64(st.soloRate), "supports", supports)
	u.printf("* your upload looks like it supports about %d streaming viewer(s) of %s at ~%s/s — %d are watching now; expect stalls for some of them",
		supports, u.metas[idx].Name, human.Bytes(int64(bitrate)), st.active)
}

// transportSignaler adapts the nostr transport to transfer.Signaler.
type transportSignaler struct {
	t  *nostrx.Transport
	ch chan transfer.AddressedSignal
}

func (s *transportSignaler) SendSignal(ctx context.Context, to string, sig protocol.Signal) error {
	return s.t.Send(ctx, protocol.Message{Type: protocol.MsgSignal, To: to, Signal: &sig})
}

func (s *transportSignaler) Signals() <-chan transfer.AddressedSignal { return s.ch }

// relaySnaps converts nostrx health snapshots to the telemetry type so
// the telemetry package doesn't import nostrx (avoiding a cycle).
func relaySnaps(stats []nostrx.RelayHealth) []telemetry.RelaySnapshot {
	out := make([]telemetry.RelaySnapshot, len(stats))
	for i, s := range stats {
		out[i] = telemetry.RelaySnapshot{LatMs: s.LatMs, Healthy: s.Healthy}
	}
	return out
}

func hashFile(path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}
