// Package mobile exposes junto's protocol, sync and verified transfer core through a
// gomobile-compatible API. Only primitive values and versioned JSON cross the boundary.
package mobile

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/swayam-mishra/junto/internal/nostrx"
	"github.com/swayam-mishra/junto/internal/protocol"
	"github.com/swayam-mishra/junto/internal/room"
	"github.com/swayam-mishra/junto/internal/streamserver"
	"github.com/swayam-mishra/junto/internal/syncer"
	"github.com/swayam-mishra/junto/internal/transfer"
)

// Callback is implemented by Kotlin. Calls may arrive on Go-owned threads.
type Callback interface {
	OnEvent(eventJSON string)
	OnCommand(commandJSON string)
}

type options struct {
	Version        int      `json:"v"`
	Nickname       string   `json:"nickname"`
	Relays         []string `json:"relays"`
	TurnURL        string   `json:"turn_url"`
	TurnUsername   string   `json:"turn_username"`
	TurnPassword   string   `json:"turn_password"`
	CacheDirectory string   `json:"cache_directory"`
}

type queueItem struct {
	ID     string `json:"id"`
	Title  string `json:"title"`
	Artist string `json:"artist"`
	URI    string `json:"uri"`
	Size   int64  `json:"size"`
	Root   string `json:"root"`
}

// Session owns one room transport, sync engine and optional file-transfer pipeline.
type Session struct {
	cb       Callback
	code     string
	cancel   context.CancelFunc
	done     chan struct{}
	player   *mobilePlayer
	mu       sync.RWMutex
	store    *transfer.FileStore
	server   *streamserver.Server
	files    []protocol.FileMeta
	fileIDs  []string
	closed   bool
	closeOne sync.Once
}

// Create starts a host session. queueJSON contains absolute readable paths prepared by Android.
func Create(configJSON, queueJSON string, cb Callback) (*Session, error) {
	cfg, items, err := decodeInputs(configJSON, queueJSON)
	if err != nil {
		return nil, err
	}
	if len(items) == 0 {
		return nil, errors.New("empty playlist")
	}
	rm, err := room.New()
	if err != nil {
		return nil, err
	}
	s := newSession(cb, rm.Code())
	ctx, cancel := context.WithCancel(context.Background())
	s.cancel = cancel
	go s.runHost(ctx, rm, cfg, items)
	return s, nil
}

// Join starts a streaming join session. Local matches are reserved for verified matching and
// host migration; the first mobile release streams unmatched files through localhost.
func Join(configJSON, roomCode, localMatchesJSON string, cb Callback) (*Session, error) {
	cfg, matches, err := decodeInputs(configJSON, localMatchesJSON)
	if err != nil {
		return nil, err
	}
	rm, err := room.Parse(roomCode)
	if err != nil {
		return nil, err
	}
	s := newSession(cb, rm.Code())
	ctx, cancel := context.WithCancel(context.Background())
	s.cancel = cancel
	go s.runJoin(ctx, rm, cfg, matches)
	return s, nil
}

// Preview returns the remote audio queue without starting playback or transfer. Android uses it
// for space confirmation and explicit local-file matching before Join.
func Preview(configJSON, roomCode string) (string, error) {
	cfg, _, err := decodeInputs(configJSON, "")
	if err != nil {
		return "", err
	}
	rm, err := room.Parse(roomCode)
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Second)
	defer cancel()
	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		return "", fmt.Errorf("relay connection: %w", err)
	}
	defer t.Close()
	if err := t.Send(ctx, protocol.Message{Type: protocol.MsgHello, Nick: cfg.Nickname}); err != nil {
		return "", fmt.Errorf("joining room: %w", err)
	}
	_, files, err := waitForHost(ctx, t)
	if err != nil {
		return "", err
	}
	if err := validateAudioRoom(files); err != nil {
		return "", err
	}
	items := make([]map[string]any, 0, len(files))
	ids := makeFileIDs(files)
	for index, file := range files {
		items = append(items, map[string]any{
			"id": ids[index], "file_id": ids[index], "title": file.Name,
			"name": file.Name, "size": file.Size, "root": file.Bao,
		})
	}
	data, err := json.Marshal(map[string]any{"v": 1, "items": items})
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// TestConnection verifies that at least one configured Nostr relay accepts an encrypted room
// message. TURN credentials remain in memory and are exercised later by the peer ICE handshake.
func TestConnection(configJSON string) (string, error) {
	cfg, _, err := decodeInputs(configJSON, "")
	if err != nil {
		return "", err
	}
	rm, err := room.New()
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		return "", fmt.Errorf("relay connection: %w", err)
	}
	defer t.Close()
	if err := t.Send(ctx, protocol.Message{Type: protocol.MsgHello, Nick: cfg.Nickname}); err != nil {
		return "", fmt.Errorf("relay send: %w", err)
	}
	status := "relay_ok"
	if cfg.TurnURL != "" {
		status = "relay_ok_turn_configured"
	}
	return status, nil
}

func newSession(cb Callback, code string) *Session {
	s := &Session{cb: cb, code: code, done: make(chan struct{})}
	s.player = newMobilePlayer(cb)
	return s
}

func decodeInputs(configJSON, queueJSON string) (options, []queueItem, error) {
	var cfg options
	if err := json.Unmarshal([]byte(configJSON), &cfg); err != nil {
		return cfg, nil, fmt.Errorf("invalid options: %w", err)
	}
	if cfg.Version != 1 {
		return cfg, nil, fmt.Errorf("unsupported mobile options version %d", cfg.Version)
	}
	cfg.Nickname = protocol.SafeText(cfg.Nickname, 64)
	if cfg.Nickname == "" {
		cfg.Nickname = "ECHO"
	}
	var items []queueItem
	if queueJSON != "" {
		if err := json.Unmarshal([]byte(queueJSON), &items); err != nil {
			return cfg, nil, fmt.Errorf("invalid queue: %w", err)
		}
	}
	return cfg, items, nil
}

func (s *Session) runHost(ctx context.Context, rm *room.Room, cfg options, items []queueItem) {
	defer s.finish()
	metas := make([]protocol.FileMeta, 0, len(items))
	paths := make([]string, 0, len(items))
	outboards := make([][]byte, 0, len(items))
	fileIDs := make([]string, 0, len(items))
	for index, item := range items {
		path, err := filepath.Abs(item.URI)
		if err != nil {
			s.fail(fmt.Errorf("resolving item %d: %w", index, err), false)
			return
		}
		info, err := os.Stat(path)
		if err != nil || !info.Mode().IsRegular() {
			s.fail(fmt.Errorf("opening %s: %w", filepath.Base(path), err), false)
			return
		}
		s.emit(map[string]any{"type": "preparing_file", "index": index, "total": len(items)})
		hashes, err := transfer.HashForServe(path)
		if err != nil {
			s.fail(fmt.Errorf("hashing %s: %w", filepath.Base(path), err), false)
			return
		}
		meta := protocol.FileMeta{
			Name:     protocol.SafeText(filepath.Base(path), 255),
			Size:     info.Size(),
			SHA256:   hashes.SHA256,
			Bao:      hashes.BaoRoot,
			BaoGroup: hashes.BaoGroup,
			BaoOb:    hashes.BaoObSHA,
		}
		if duration, ok := transfer.ProbeDuration(path, info.Size()); ok {
			meta.DurationSecs = duration
		}
		metas = append(metas, meta)
		paths = append(paths, path)
		outboards = append(outboards, hashes.Outboard)
		fileIDs = append(fileIDs, item.ID)
	}
	s.mu.Lock()
	s.files = metas
	s.fileIDs = fileIDs
	s.mu.Unlock()

	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		s.fail(fmt.Errorf("relay connection: %w", err), true)
		return
	}
	defer t.Close()
	router := newSignalRouter(t)
	ice := transfer.DefaultICEConfig().WithTURN(cfg.TurnURL, cfg.TurnUsername, cfg.TurnPassword)
	fair := transfer.NewUploadFairness()
	onFileReq := func(from string, index int, _, _ int64) {
		if index < 0 || index >= len(paths) {
			return
		}
		go func() {
			signaler, signals := router.open(from)
			defer router.release(from, signals)
			outboard := func(context.Context) ([]byte, error) { return outboards[index], nil }
			err := transfer.ServeFile(
				ctx, signaler, from, paths[index], ice, s.notice, nil, nil, outboard, fair, index,
			)
			if err != nil && ctx.Err() == nil {
				s.notice("transfer failed: %v", err)
			}
		}()
	}
	s.emitQueue(metas, nil)
	s.runEngine(ctx, t, cfg, true, metas, len(metas), onFileReq, router.deliver, engineExtras{})
}

func (s *Session) runJoin(ctx context.Context, rm *room.Room, cfg options, matches []queueItem) {
	defer s.finish()
	t, err := nostrx.New(cfg.Relays, rm)
	if err != nil {
		s.fail(fmt.Errorf("relay connection: %w", err), true)
		return
	}
	defer t.Close()
	if err := t.Send(ctx, protocol.Message{Type: protocol.MsgHello, Nick: cfg.Nickname}); err != nil {
		s.fail(fmt.Errorf("joining room: %w", err), true)
		return
	}
	host, files, err := waitForHost(ctx, t)
	if err != nil {
		s.fail(err, true)
		return
	}
	if len(files) == 0 {
		s.fail(errors.New("this room has no transferable audio files"), false)
		return
	}
	if err := validateAudioRoom(files); err != nil {
		s.fail(err, false)
		return
	}
	if cfg.CacheDirectory == "" {
		s.fail(errors.New("cache directory is required"), false)
		return
	}
	store, err := transfer.NewFileStore(cfg.CacheDirectory, files)
	if err != nil {
		s.fail(err, false)
		return
	}
	for index, file := range files {
		for _, match := range matches {
			if match.Root == "" || match.Root != file.Bao || match.URI == "" {
				continue
			}
			imported, importErr := store.ImportVerified(index, match.URI)
			if importErr != nil {
				s.notice("local match verification failed for %s", file.Name)
			} else if imported {
				s.emit(map[string]any{
					"type": "transfer", "file_id": makeFileIDs(files)[index],
					"file_name": file.Name, "verified": file.Size, "total": file.Size,
					"bytes_per_second": int64(0), "complete": true, "local_match": true,
				})
			}
			break
		}
	}
	server, err := streamserver.New(store)
	if err != nil {
		s.fail(err, true)
		return
	}
	s.mu.Lock()
	s.store = store
	s.server = server
	s.files = files
	s.fileIDs = makeFileIDs(files)
	s.mu.Unlock()
	defer func() {
		shutdown, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = server.Close(shutdown)
	}()

	signalCh := make(chan transfer.AddressedSignal, 8)
	signaler := &transportSignaler{transport: t, signals: signalCh}
	sendRequest := func(callCtx context.Context, peer string, index int, offset, length int64) error {
		return t.Send(callCtx, protocol.Message{
			Type: protocol.MsgFileReq, To: peer, Nick: cfg.Nickname,
			FileIndex: index, Offset: offset, Length: length,
		})
	}
	ice := transfer.DefaultICEConfig().WithTURN(cfg.TurnURL, cfg.TurnUsername, cfg.TurnPassword)
	downloader := transfer.NewDownloader(signaler, host, store, ice, sendRequest, s.notice)
	downloadRouter := newSignalRouter(t)
	downloader.EnableSwarm(func(peer string) (transfer.Signaler, func()) {
		auxSignaler, signals := downloadRouter.open(peer)
		return auxSignaler, func() { downloadRouter.release(peer, signals) }
	})
	downloader.OnProgress(func(progress transfer.Progress) {
		index := fileIndexByName(files, progress.Name)
		if index < 0 {
			return
		}
		s.emit(map[string]any{
			"type": "transfer", "file_id": s.fileID(index),
			"file_name": progress.Name, "verified": progress.Received,
			"total": progress.Total, "bytes_per_second": int64(progress.BytesPerSec),
			"complete": progress.Received >= progress.Total && progress.Total > 0,
		})
	})

	buffer := make(chan bool, 8)
	server.SetStallReporter(func(stalled bool) {
		select {
		case buffer <- stalled:
		default:
		}
	}, downloader.Prioritize)
	downloader.SetPlayheadFunc(server.CurrentReadPos)
	server.Start()
	urls := server.URLs()
	s.emitQueue(files, urls)
	s.emit(map[string]any{"type": "stream_urls", "urls": urls})

	ready := make(chan struct{})
	go func() {
		if err := store.WaitBuffered(ctx, 0, transfer.PreBufferBytes(store.Meta(0))); err == nil {
			close(ready)
		}
	}()
	go func() {
		if err := downloader.Run(ctx); err != nil && ctx.Err() == nil {
			s.fail(fmt.Errorf("download failed: %w", err), true)
		}
	}()
	serveRouter := newSignalRouter(t)
	serveGate := newMobileServeGate(4)
	fair := transfer.NewUploadFairness()
	onFileReq := func(from string, index int, _, _ int64) {
		if index < 0 || index >= store.Len() || !serveGate.acquire(from, index) {
			return
		}
		go func() {
			defer serveGate.release(from, index)
			sourceSignaler, signals := serveRouter.open(from)
			defer serveRouter.release(from, signals)
			if err := transfer.ServeFromStore(
				ctx, sourceSignaler, from, store, index, ice, s.notice, nil, nil, fair,
			); err != nil && ctx.Err() == nil {
				s.notice("verified peer transfer failed: %v", err)
			}
		}()
	}
	onSignal := func(from string, signal protocol.Signal) {
		switch signal.Kind {
		case "offer":
			if from == downloader.GetHost() {
				select {
				case signalCh <- transfer.AddressedSignal{From: from, Sig: signal}:
				default:
				}
				return
			}
			downloadRouter.deliver(from, signal)
		case "answer":
			serveRouter.deliver(from, signal)
		}
	}
	s.runEngine(ctx, t, cfg, false, nil, len(files), onFileReq, onSignal, engineExtras{
		buffer:         buffer,
		ready:          ready,
		progress:       store.Progress,
		canHostDynamic: store.AllDone,
		onHostChange:   downloader.SetHost,
		haveSnapshot:   store.AdvertSnapshot,
		onPeerHave:     downloader.UpdatePeerHave,
		onPeerGone:     downloader.PeerGone,
		onBecomeHost: func([]protocol.FileMeta) (
			func(string, int, int64, int64),
			func(string, protocol.Signal),
		) {
			return onFileReq, onSignal
		},
	})
}

type engineExtras struct {
	buffer         <-chan bool
	ready          <-chan struct{}
	progress       func() float64
	canHostDynamic func() bool
	onHostChange   func(string)
	onBecomeHost   func([]protocol.FileMeta) (
		func(string, int, int64, int64),
		func(string, protocol.Signal),
	)
	haveSnapshot func() ([]protocol.HaveEntry, [][2]int)
	onPeerHave   func(string, []protocol.HaveEntry, [][2]int)
	onPeerGone   func(string)
}

func (s *Session) runEngine(
	ctx context.Context,
	t *nostrx.Transport,
	cfg options,
	host bool,
	files []protocol.FileMeta,
	playlistLen int,
	onFileReq func(string, int, int64, int64),
	onSignal func(string, protocol.Signal),
	extras engineExtras,
) {
	lines := make(chan string)
	engine := syncer.New(syncer.Deps{
		Mpv:              s.player,
		Send:             t.Send,
		Inbox:            t.Receive(),
		Lines:            lines,
		Printf:           s.notice,
		SelfPub:          t.SelfPubKey(),
		Nick:             cfg.Nickname,
		Host:             host,
		Files:            files,
		PlaylistLen:      playlistLen,
		OnFileReq:        onFileReq,
		OnSignal:         onSignal,
		CanHost:          host,
		CanHostDynamic:   extras.canHostDynamic,
		OnBecomeHost:     extras.onBecomeHost,
		OnHostChange:     extras.onHostChange,
		HaveSnapshot:     extras.haveSnapshot,
		OnPeerHave:       extras.onPeerHave,
		OnPeerGone:       extras.onPeerGone,
		ReadyCh:          extras.ready,
		Buffer:           extras.buffer,
		DownloadProgress: extras.progress,
		Receiving:        t.Receiving,
		OnSnapshot:       s.onSnapshot,
	})
	err := engine.Run(ctx)
	if err != nil && !errors.Is(err, context.Canceled) {
		s.fail(err, true)
	}
}

func validateAudioRoom(files []protocol.FileMeta) error {
	for _, file := range files {
		if len(file.Subs) > 0 {
			return errors.New("Android audio version does not support rooms with subtitles")
		}
		switch strings.ToLower(filepath.Ext(file.Name)) {
		case ".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".wmv", ".flv":
			return errors.New("Android audio version does not support video rooms")
		}
	}
	return nil
}

func waitForHost(ctx context.Context, t *nostrx.Transport) (string, []protocol.FileMeta, error) {
	timeout := time.NewTimer(45 * time.Second)
	defer timeout.Stop()
	for {
		select {
		case <-ctx.Done():
			return "", nil, ctx.Err()
		case <-timeout.C:
			return "", nil, errors.New("timed out waiting for the room host")
		case message, ok := <-t.Receive():
			if !ok {
				return "", nil, errors.New("lost all relay connections")
			}
			if message.Type == protocol.MsgHello && message.From != t.SelfPubKey() && len(message.Files) > 0 {
				return message.From, message.Files, nil
			}
		}
	}
}

func (s *Session) onSnapshot(snapshot syncer.Snapshot) {
	members := make([]map[string]any, 0, len(snapshot.Peers)+1)
	members = append(members, map[string]any{
		"id_hash": shortHash(snapshot.SelfNick), "nickname": snapshot.SelfNick,
		"ready": true, "buffering": false, "download_percent": 0,
	})
	var aggregateDrift *int64
	for _, peer := range snapshot.Peers {
		member := map[string]any{
			"id_hash": shortHash(peer.Nick), "nickname": peer.Nick, "ready": peer.Ready,
			"buffering": peer.Buffering, "download_percent": peer.DL,
		}
		if peer.DriftKnown {
			drift := int64(peer.Drift * 1000)
			member["drift_ms"] = drift
			if aggregateDrift == nil {
				aggregateDrift = &drift
			}
		}
		members = append(members, member)
	}
	event := map[string]any{
		"type": "snapshot", "members": members, "current_index": s.player.index(),
		"paused": snapshot.Paused, "buffering": false,
		"connection": map[bool]string{true: "turn", false: "direct"}[snapshot.Relayed],
	}
	if aggregateDrift != nil {
		event["drift_ms"] = *aggregateDrift
	}
	s.emit(event)
}

func (s *Session) emitQueue(files []protocol.FileMeta, urls []string) {
	items := make([]map[string]any, 0, len(files))
	for index, file := range files {
		url := ""
		if index < len(urls) {
			url = urls[index]
		}
		items = append(items, map[string]any{
			"id": s.fileID(index), "file_id": s.fileID(index), "title": file.Name,
			"name": file.Name, "size": file.Size, "root": file.Bao, "stream_url": url,
		})
	}
	s.emit(map[string]any{"type": "queue", "items": items})
}

func (s *Session) fileID(index int) string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if index >= 0 && index < len(s.fileIDs) && s.fileIDs[index] != "" {
		return s.fileIDs[index]
	}
	if index >= 0 && index < len(s.files) {
		return shortHash(s.files[index].Bao + s.files[index].SHA256)
	}
	return fmt.Sprintf("file-%d", index)
}

func makeFileIDs(files []protocol.FileMeta) []string {
	ids := make([]string, len(files))
	for i, file := range files {
		ids[i] = shortHash(file.Bao + file.SHA256)
	}
	return ids
}

func fileIndexByName(files []protocol.FileMeta, name string) int {
	for index, file := range files {
		if file.Name == name {
			return index
		}
	}
	return -1
}

func shortHash(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:8])
}

func (s *Session) notice(format string, args ...any) {
	s.emit(map[string]any{"type": "notice", "message": fmt.Sprintf(format, args...)})
}

func (s *Session) fail(err error, recoverable bool) {
	if err == nil {
		return
	}
	s.emit(map[string]any{"type": "failed", "message": err.Error(), "recoverable": recoverable})
}

func (s *Session) emit(value any) {
	if s.cb == nil {
		return
	}
	data, err := json.Marshal(value)
	if err == nil {
		s.cb.OnEvent(string(data))
	}
}

func (s *Session) finish() {
	s.closeOne.Do(func() { close(s.done) })
}

// RoomCode returns the secret room code. Callers must never persist or log it.
func (s *Session) RoomCode() string { return s.code }

// NotifyPlayback forwards a local Media3 event into junto's sync engine.
func (s *Session) NotifyPlayback(eventJSON string) {
	s.player.notify(eventJSON)
}

// ReceivedFilePath returns a path only after the FileStore has fully verified the file.
func (s *Session) ReceivedFilePath(fileID string) string {
	s.mu.RLock()
	store := s.store
	ids := append([]string(nil), s.fileIDs...)
	s.mu.RUnlock()
	if store == nil {
		return ""
	}
	for index, id := range ids {
		if id == fileID && store.Done(index) {
			return store.PathFor(index)
		}
	}
	return ""
}

// ReceivedFileRoot returns the announced Bao content root for duplicate-safe saving.
func (s *Session) ReceivedFileRoot(fileID string) string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for index, id := range s.fileIDs {
		if id == fileID && index < len(s.files) {
			return s.files[index].Bao
		}
	}
	return ""
}

// Leave sends junto's normal leave message through engine cancellation and releases resources.
func (s *Session) Leave() {
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return
	}
	s.closed = true
	cancel := s.cancel
	s.mu.Unlock()
	if cancel != nil {
		cancel()
	}
	s.player.Close()
	select {
	case <-s.done:
	case <-time.After(4 * time.Second):
	}
}

type transportSignaler struct {
	transport *nostrx.Transport
	signals   <-chan transfer.AddressedSignal
}

func (s *transportSignaler) SendSignal(ctx context.Context, to string, signal protocol.Signal) error {
	return s.transport.Send(ctx, protocol.Message{Type: protocol.MsgSignal, To: to, Signal: &signal})
}
func (s *transportSignaler) Signals() <-chan transfer.AddressedSignal { return s.signals }

type signalRouter struct {
	transport *nostrx.Transport
	mu        sync.Mutex
	channels  map[string]chan transfer.AddressedSignal
}

func newSignalRouter(t *nostrx.Transport) *signalRouter {
	return &signalRouter{transport: t, channels: make(map[string]chan transfer.AddressedSignal)}
}
func (r *signalRouter) open(peer string) (transfer.Signaler, chan transfer.AddressedSignal) {
	channel := make(chan transfer.AddressedSignal, 4)
	r.mu.Lock()
	r.channels[peer] = channel
	r.mu.Unlock()
	return &transportSignaler{transport: r.transport, signals: channel}, channel
}
func (r *signalRouter) release(peer string, channel chan transfer.AddressedSignal) {
	r.mu.Lock()
	if r.channels[peer] == channel {
		delete(r.channels, peer)
	}
	r.mu.Unlock()
}
func (r *signalRouter) deliver(from string, signal protocol.Signal) {
	r.mu.Lock()
	channel := r.channels[from]
	r.mu.Unlock()
	if channel != nil {
		select {
		case channel <- transfer.AddressedSignal{From: from, Sig: signal}:
		default:
		}
	}
}

type mobileServeGate struct {
	mu     sync.Mutex
	active map[string]struct{}
	max    int
}

func newMobileServeGate(max int) *mobileServeGate {
	return &mobileServeGate{active: make(map[string]struct{}), max: max}
}

func (g *mobileServeGate) acquire(peer string, index int) bool {
	g.mu.Lock()
	defer g.mu.Unlock()
	key := fmt.Sprintf("%s/%d", peer, index)
	if _, exists := g.active[key]; exists || len(g.active) >= g.max {
		return false
	}
	g.active[key] = struct{}{}
	return true
}

func (g *mobileServeGate) release(peer string, index int) {
	g.mu.Lock()
	delete(g.active, fmt.Sprintf("%s/%d", peer, index))
	g.mu.Unlock()
}
