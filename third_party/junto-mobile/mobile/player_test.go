package mobile

import (
	"context"
	"encoding/json"
	"testing"
	"time"
)

type callbackRecorder struct {
	commands chan string
	events   chan string
}

func (c *callbackRecorder) OnCommand(value string) { c.commands <- value }
func (c *callbackRecorder) OnEvent(value string)   { c.events <- value }

func TestRemotePauseProducesOneCommandAndOneSuppressionEcho(t *testing.T) {
	cb := &callbackRecorder{commands: make(chan string, 2), events: make(chan string, 2)}
	player := newMobilePlayer(cb)
	defer player.Close()
	if err := player.SetPause(context.Background(), false); err != nil {
		t.Fatal(err)
	}
	select {
	case raw := <-cb.commands:
		var command map[string]any
		if err := json.Unmarshal([]byte(raw), &command); err != nil {
			t.Fatal(err)
		}
		if command["type"] != "pause" || command["paused"] != false {
			t.Fatalf("unexpected command: %s", raw)
		}
	case <-time.After(time.Second):
		t.Fatal("missing command callback")
	}
	select {
	case event := <-player.Events():
		if event.ID != observePause {
			t.Fatalf("unexpected echo: %+v", event)
		}
	case <-time.After(time.Second):
		t.Fatal("missing property echo")
	}
}

func TestLocalSeekUpdatesProjectedPosition(t *testing.T) {
	player := newMobilePlayer(nil)
	defer player.Close()
	player.notify(`{"v":1,"type":"seek","position_ms":12500}`)
	position, err := player.GetPlaybackTime(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if position < 12.49 || position > 12.51 {
		t.Fatalf("position=%v; want 12.5", position)
	}
}
