package mobile

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"github.com/swayam-mishra/junto/internal/mpv"
)

const (
	observePause       = 1
	observeSpeed       = 2
	observeSubDelay    = 3
	observeSid         = 4
	observePlaylistPos = 5
	observeBuffering   = 6
	observeAid         = 7
)

type mobilePlayer struct {
	cb     Callback
	events chan mpv.Event
	done   chan error
	mu     sync.Mutex
	paused bool
	speed  float64
	pos    float64
	at     time.Time
	idx    int
	closed bool
}

func newMobilePlayer(cb Callback) *mobilePlayer {
	return &mobilePlayer{
		cb: cb, events: make(chan mpv.Event, 64), done: make(chan error, 1),
		paused: true, speed: 1, at: time.Now(),
	}
}

func (p *mobilePlayer) ObserveProperty(_ context.Context, id int, name string) error {
	var value any
	p.mu.Lock()
	switch id {
	case observePause:
		value = p.paused
	case observeSpeed:
		value = p.speed
	case observeSubDelay:
		value = 0.0
	case observeSid, observeAid:
		value = 0
	case observePlaylistPos:
		value = p.idx
	case observeBuffering:
		value = false
	}
	p.mu.Unlock()
	p.property(id, name, value)
	return nil
}

func (p *mobilePlayer) SetPause(_ context.Context, paused bool) error {
	p.mu.Lock()
	p.projectLocked()
	p.paused = paused
	p.at = time.Now()
	p.mu.Unlock()
	p.command(map[string]any{"type": "pause", "paused": paused})
	p.property(observePause, "pause", paused)
	return nil
}

func (p *mobilePlayer) SeekAbsolute(_ context.Context, seconds float64) error {
	if seconds < 0 {
		seconds = 0
	}
	p.mu.Lock()
	p.pos = seconds
	p.at = time.Now()
	p.mu.Unlock()
	p.command(map[string]any{"type": "seek", "position_ms": int64(seconds * 1000)})
	p.push(mpv.Event{Event: "seek"})
	p.push(mpv.Event{Event: "playback-restart"})
	return nil
}

func (p *mobilePlayer) SetSpeed(_ context.Context, speed float64) error {
	p.mu.Lock()
	p.projectLocked()
	p.speed = speed
	p.at = time.Now()
	p.mu.Unlock()
	p.command(map[string]any{"type": "speed", "speed": speed})
	p.property(observeSpeed, "speed", speed)
	return nil
}

func (p *mobilePlayer) SetSubDelay(_ context.Context, seconds float64) error {
	p.property(observeSubDelay, "sub-delay", seconds)
	return nil
}

func (p *mobilePlayer) SetSid(_ context.Context, sid int) error {
	p.property(observeSid, "sid", sid)
	return nil
}

func (p *mobilePlayer) SetAid(_ context.Context, aid int) error {
	p.property(observeAid, "aid", aid)
	return nil
}

func (p *mobilePlayer) SetPlaylistPos(_ context.Context, index int) error {
	p.mu.Lock()
	p.idx = index
	p.pos = 0
	p.at = time.Now()
	p.mu.Unlock()
	p.command(map[string]any{"type": "index", "index": index})
	p.property(observePlaylistPos, "playlist-pos", index)
	p.push(mpv.Event{Event: "playback-restart"})
	return nil
}

func (p *mobilePlayer) AddSubtitle(context.Context, string) error { return nil }

func (p *mobilePlayer) GetPlaybackTime(context.Context) (float64, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return 0, errors.New("player closed")
	}
	p.projectLocked()
	return p.pos, nil
}

func (p *mobilePlayer) ShowText(_ context.Context, text string, _ int) error {
	if p.cb != nil {
		data, _ := json.Marshal(map[string]any{"type": "notice", "message": text})
		p.cb.OnEvent(string(data))
	}
	return nil
}

func (p *mobilePlayer) Events() <-chan mpv.Event { return p.events }
func (p *mobilePlayer) Done() <-chan error       { return p.done }

func (p *mobilePlayer) Close() error {
	p.mu.Lock()
	if p.closed {
		p.mu.Unlock()
		return nil
	}
	p.closed = true
	close(p.events)
	close(p.done)
	p.mu.Unlock()
	return nil
}

func (p *mobilePlayer) index() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.idx
}

func (p *mobilePlayer) notify(raw string) {
	var event struct {
		Type       string  `json:"type"`
		Paused     bool    `json:"paused"`
		PositionMs int64   `json:"position_ms"`
		Speed      float64 `json:"speed"`
		Index      int     `json:"index"`
		Buffering  bool    `json:"buffering"`
	}
	if json.Unmarshal([]byte(raw), &event) != nil {
		return
	}
	switch event.Type {
	case "pause":
		p.mu.Lock()
		p.projectLocked()
		p.paused = event.Paused
		p.at = time.Now()
		p.mu.Unlock()
		p.property(observePause, "pause", event.Paused)
	case "seek":
		p.mu.Lock()
		p.pos = float64(event.PositionMs) / 1000
		p.at = time.Now()
		p.mu.Unlock()
		p.push(mpv.Event{Event: "seek"})
		p.push(mpv.Event{Event: "playback-restart"})
	case "speed":
		p.mu.Lock()
		p.projectLocked()
		p.speed = event.Speed
		p.at = time.Now()
		p.mu.Unlock()
		p.property(observeSpeed, "speed", event.Speed)
	case "index":
		p.mu.Lock()
		p.idx = event.Index
		p.pos = 0
		p.at = time.Now()
		p.mu.Unlock()
		p.property(observePlaylistPos, "playlist-pos", event.Index)
		p.push(mpv.Event{Event: "playback-restart"})
	case "buffering":
		p.property(observeBuffering, "paused-for-cache", event.Buffering)
	case "playback_restart":
		p.push(mpv.Event{Event: "playback-restart"})
	}
}

func (p *mobilePlayer) command(value any) {
	if p.cb == nil {
		return
	}
	data, err := json.Marshal(value)
	if err == nil {
		p.cb.OnCommand(string(data))
	}
}

func (p *mobilePlayer) property(id int, name string, value any) {
	data, _ := json.Marshal(value)
	p.push(mpv.Event{Event: "property-change", ID: id, Name: name, Data: data})
}

func (p *mobilePlayer) push(event mpv.Event) {
	p.mu.Lock()
	closed := p.closed
	p.mu.Unlock()
	if closed {
		return
	}
	select {
	case p.events <- event:
	default:
		// Property updates are level-triggered by later Media3 events and heartbeats; never block
		// a gomobile callback thread.
	}
}

func (p *mobilePlayer) projectLocked() {
	now := time.Now()
	if !p.paused {
		p.pos += now.Sub(p.at).Seconds() * p.speed
	}
	p.at = now
}
