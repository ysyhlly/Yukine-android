package syncer

import (
	"context"
	"strings"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestResolvePeer(t *testing.T) {
	e := New(Deps{SelfPub: "me", Printf: func(string, ...any) {}})
	e.peers["aaaa1111"] = &peerInfo{nick: "alice"}
	e.peers["bbbb2222"] = &peerInfo{nick: "bob"}

	if pub, ok, amb := e.resolvePeer("alice"); !ok || amb || pub != "aaaa1111" {
		t.Errorf("by nick: got %q ok=%v amb=%v", pub, ok, amb)
	}
	if pub, ok, amb := e.resolvePeer("bbbb"); !ok || amb || pub != "bbbb2222" {
		t.Errorf("by pubkey prefix: got %q ok=%v amb=%v", pub, ok, amb)
	}
	if _, ok, _ := e.resolvePeer("nobody"); ok {
		t.Error("unknown name should not resolve")
	}
	// Two peers share a nick → ambiguous.
	e.peers["cccc3333"] = &peerInfo{nick: "alice"}
	if _, _, amb := e.resolvePeer("alice"); !amb {
		t.Error("duplicate nick should be ambiguous")
	}
}

func TestIgnoreSuppressesChat(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{SelfPub: "me", Printf: pf})
	ctx := context.Background()
	e.peers["bob"] = &peerInfo{nick: "bob"}

	e.handleMessage(ctx, protocol.Message{Type: protocol.MsgChat, From: "bob", Nick: "bob", Text: "before"})
	e.ignorePeer(ctx, "bob")
	e.handleMessage(ctx, protocol.Message{Type: protocol.MsgChat, From: "bob", Nick: "bob", Text: "after"})

	out := strings.Join(lines(), "\n")
	if !strings.Contains(out, "before") {
		t.Error("chat before ignore should show")
	}
	if strings.Contains(out, "after") {
		t.Error("chat after ignore should be suppressed")
	}
}

func TestKickTargetingSelfQuits(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{SelfPub: "me", Printf: pf})
	e.peers["host"] = &peerInfo{nick: "host"}

	e.handleMessage(context.Background(), protocol.Message{Type: protocol.MsgKick, From: "host", Kicked: "me"})
	if !e.quitRequested {
		t.Error("a kick targeting us should request quit")
	}
	if !strings.Contains(strings.Join(lines(), "\n"), "kicked") {
		t.Error("expected a 'you were kicked' notice")
	}
}

func TestKickOtherAutoIgnores(t *testing.T) {
	pf, _ := capture()
	e := New(Deps{SelfPub: "me", Printf: pf})
	e.peers["host"] = &peerInfo{nick: "host"}
	e.peers["bob"] = &peerInfo{nick: "bob"}

	e.handleMessage(context.Background(), protocol.Message{Type: protocol.MsgKick, From: "host", Kicked: "bob"})
	if !e.ignored["bob"] {
		t.Error("a kick of another peer should auto-ignore them locally")
	}
	if e.quitRequested {
		t.Error("a kick of someone else must not quit us")
	}
}

func TestSnapshotDownloadPercent(t *testing.T) {
	e := New(Deps{SelfPub: "me", Printf: func(string, ...any) {}, DownloadProgress: func() float64 { return 0.42 }})
	if got := e.snapshot(0).DL; got != 42 {
		t.Errorf("DL = %d, want 42", got)
	}
	// Complete download omits DL (0).
	e.d.DownloadProgress = func() float64 { return 1.0 }
	if got := e.snapshot(0).DL; got != 0 {
		t.Errorf("DL at 100%% should be 0 (omitted), got %d", got)
	}
	// No downloader (host) → no DL.
	e.d.DownloadProgress = nil
	if got := e.snapshot(0).DL; got != 0 {
		t.Errorf("host snapshot DL should be 0, got %d", got)
	}
}

func TestSyncNothingToDo(t *testing.T) {
	pf, lines := capture()
	e := New(Deps{SelfPub: "me", Printf: pf})
	e.resync(context.Background()) // haveLast == false
	if !strings.Contains(strings.Join(lines(), "\n"), "only one here") {
		t.Error("resync with no peers should say there's nothing to sync to")
	}
}
