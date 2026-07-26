package syncer

import (
	"context"
	"errors"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestRunReturnsRelayDisconnectedWhenInboxCloses(t *testing.T) {
	inbox := make(chan protocol.Message)
	close(inbox)
	engine := New(Deps{
		Mpv:     newFakeMpv(),
		Send:    func(context.Context, protocol.Message) error { return nil },
		Inbox:   inbox,
		Printf:  func(string, ...any) {},
		SelfPub: "self",
		Nick:    "tester",
	})

	if err := engine.Run(context.Background()); !errors.Is(err, ErrRelayDisconnected) {
		t.Fatalf("Run() error = %v; want ErrRelayDisconnected", err)
	}
}

func TestRunReturnsKickedWhenKickTargetsSelf(t *testing.T) {
	inbox := make(chan protocol.Message, 1)
	inbox <- protocol.Message{
		Type:   protocol.MsgKick,
		From:   "host",
		Kicked: "self",
		Nick:   "host",
	}
	engine := New(Deps{
		Mpv:     newFakeMpv(),
		Send:    func(context.Context, protocol.Message) error { return nil },
		Inbox:   inbox,
		Printf:  func(string, ...any) {},
		SelfPub: "self",
		Nick:    "tester",
	})

	if err := engine.Run(context.Background()); !errors.Is(err, ErrKicked) {
		t.Fatalf("Run() error = %v; want ErrKicked", err)
	}
}
