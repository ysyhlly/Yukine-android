package protocol

import (
	"reflect"
	"strings"
	"testing"
)

func TestRemoteQueueV2DoesNotSerializePrivatePlaybackState(t *testing.T) {
	message := Message{
		Type: MsgHello,
		V:    RemoteQueueVersion,
		Files: []FileMeta{{
			Name: "cloud.flac", Size: 1024, SHA256: strings.Repeat("a", 64),
			PublicTitle: "海底列车", PublicArtist: "PIKASONIC / なこたんまる",
			PublicAlbum: "真实专辑", PublicArtworkURI: "https://cdn.example/cover.jpg",
			Remote: &RemoteMeta{
				Provider: "qqmusic", TrackID: "song-mid", Quality: "lossless", DurationMS: 180000,
			},
		}},
	}
	encoded, err := Encode(message)
	if err != nil {
		t.Fatal(err)
	}
	wire := string(encoded)
	for _, secretField := range []string{"\"url\"", "\"headers\"", "cookie", "vkey", "turn_password"} {
		if strings.Contains(strings.ToLower(wire), secretField) {
			t.Fatalf("private playback state escaped protocol: %s", wire)
		}
	}
	decoded, err := Decode(encoded)
	if err != nil {
		t.Fatal(err)
	}
	file := decoded.Files[0]
	if decoded.V != RemoteQueueVersion || file.Remote.TrackID != "song-mid" ||
		file.PublicTitle != "海底列车" || file.PublicArtist != "PIKASONIC / なこたんまる" ||
		file.PublicAlbum != "真实专辑" || file.PublicArtworkURI != "https://cdn.example/cover.jpg" {
		t.Fatalf("remote queue round trip = %#v", decoded)
	}
}

func TestDecodeSanitizesPublicDisplayMetadata(t *testing.T) {
	encoded := []byte(`{"type":"hello","files":[{"name":"song.flac","size":1,"sha256":"x","title":"海\u001b底","artist":"歌\t手","album":"专辑","artwork_uri":"file:///private/cover.jpg"}]}`)
	decoded, err := Decode(encoded)
	if err != nil {
		t.Fatal(err)
	}
	file := decoded.Files[0]
	if file.PublicTitle != "海底" || file.PublicArtist != "歌 手" || file.PublicAlbum != "专辑" {
		t.Fatalf("display metadata was not sanitized: %#v", file)
	}
	if file.PublicArtworkURI != "" {
		t.Fatalf("device-local artwork escaped decode boundary: %q", file.PublicArtworkURI)
	}
}

func TestEncodeRejectsUnsafePublicDisplayMetadata(t *testing.T) {
	for _, artwork := range []string{
		"file:///private/cover.jpg",
		"data:image/png;base64,secret",
		"https://user:password@example.com/cover.jpg",
	} {
		message := Message{Type: MsgHello, Files: []FileMeta{{
			Name: "song.flac", Size: 1, SHA256: "x", PublicArtworkURI: artwork,
		}}}
		if _, err := Encode(message); err == nil {
			t.Fatalf("unsafe public artwork URI accepted: %q", artwork)
		}
	}
}

func TestRoundTrip(t *testing.T) {
	msgs := []Message{
		{Type: MsgHello, Nick: "alice", Files: []FileMeta{
			{Name: "a.mkv", Size: 42, SHA256: "ab"},
			{Name: "b.mp4", Size: 43, SHA256: "cd", DurationSecs: 125.5},
		}},
		{Type: MsgLeave},
		{Type: MsgState, State: &PlayState{
			Paused: true, Position: 12.5, SentAt: 1000, Version: 999,
			Speed: 1.5, Index: 2, Sid: 3, SubDelay: -0.25,
		}},
		{Type: MsgState, State: &PlayState{
			Paused: false, Position: 30, SentAt: 2000, Version: 1001, Speed: 1.0, Buffering: true,
		}},
		{Type: MsgChat, Text: "hi there"},
		{Type: MsgReady, Nick: "alice"},
		{Type: MsgFileReq, To: "deadbeef", FileIndex: 1, Offset: 65536},
		{Type: MsgSignal, To: "deadbeef", Signal: &Signal{Kind: "offer", SDP: "v=0..."}},
	}
	for _, in := range msgs {
		data, err := Encode(in)
		if err != nil {
			t.Fatalf("encode %s: %v", in.Type, err)
		}
		out, err := Decode(data)
		if err != nil {
			t.Fatalf("decode %s: %v", in.Type, err)
		}
		if out.Type != in.Type || out.To != in.To || out.Text != in.Text ||
			out.Nick != in.Nick || out.FileIndex != in.FileIndex || out.Offset != in.Offset {
			t.Errorf("%s: round trip mismatch: %+v != %+v", in.Type, out, in)
		}
		if in.State != nil && (out.State == nil || *out.State != *in.State) {
			t.Errorf("%s: state mismatch: %+v != %+v", in.Type, out.State, in.State)
		}
		if in.Files != nil && !reflect.DeepEqual(out.Files, in.Files) {
			t.Errorf("%s: files mismatch", in.Type)
		}
		if in.Signal != nil && (out.Signal == nil || *out.Signal != *in.Signal) {
			t.Errorf("%s: signal mismatch", in.Type)
		}
	}
}

func TestSpeedNormalization(t *testing.T) {
	// A state encoded at the default rate omits speed; decode must
	// restore 1.0 so engines never see a zero rate.
	data, err := Encode(Message{Type: MsgState, State: &PlayState{Position: 5, SentAt: 1, Version: 1}})
	if err != nil {
		t.Fatal(err)
	}
	out, err := Decode(data)
	if err != nil {
		t.Fatal(err)
	}
	if out.State.Speed != 1.0 {
		t.Errorf("decoded speed = %v, want 1.0", out.State.Speed)
	}
}

// TestZeroVersionAccepted guards the legitimate "no action taken yet"
// sentinel: a freshly joined peer's heartbeat carries Version: 0 before
// its first explicit user action, and this must not be rejected as an
// implausible version.
func TestZeroVersionAccepted(t *testing.T) {
	data, err := Encode(Message{Type: MsgState, State: &PlayState{Position: 0, SentAt: 1, Version: 0}})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := Decode(data); err != nil {
		t.Errorf("Version: 0 must decode cleanly, got %v", err)
	}
}

func TestFromNotSerialized(t *testing.T) {
	data, err := Encode(Message{Type: MsgChat, From: "spoofed", Text: "x"})
	if err != nil {
		t.Fatal(err)
	}
	out, err := Decode(data)
	if err != nil {
		t.Fatal(err)
	}
	if out.From != "" {
		t.Errorf("From leaked through serialization: %q", out.From)
	}
}

func TestRejects(t *testing.T) {
	cases := []struct {
		name string
		data string
	}{
		{"unknown type", `{"type":"nope"}`},
		{"state without payload", `{"type":"state"}`},
		{"signal without payload", `{"type":"signal"}`},
		{"bad signal kind", `{"type":"signal","signal":{"kind":"candidate","sdp":"x"}}`},
		{"not json", `garbage`},
		{"speed too high", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":1,"speed":200}}`},
		{"speed too low", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":1,"speed":0.001}}`},
		{"negative index", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":1,"idx":-1}}`},
		{"negative sid", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":1,"sid":-2}}`},
		{"negative file index", `{"type":"file-req","fidx":-1}`},
		{"negative offset", `{"type":"file-req","off":-1}`},
		{"negative version", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":-1}}`},
		{"implausible version", `{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":9223372036854775807}}`},
		{"implausible position", `{"type":"state","state":{"paused":false,"pos":1e18,"at":1,"ver":1}}`},
		{"negative position", `{"type":"state","state":{"paused":false,"pos":-1,"at":1,"ver":1}}`},
		{"oversized signal SDP", `{"type":"signal","signal":{"kind":"offer","sdp":"` + strings.Repeat("x", maxSDPLen+1) + `"}}`},
		{"negative file size", `{"type":"hello","files":[{"name":"a.mkv","size":-1,"sha256":"x"}]}`},
		{"implausible file size", `{"type":"hello","files":[{"name":"a.mkv","size":99999999999999,"sha256":"x"}]}`},
		{"negative sub size", `{"type":"hello","files":[{"name":"a.mkv","size":1,"sha256":"x","subs":[{"name":"a.srt","size":-1,"sha256":"y"}]}]}`},
		{"negative duration", `{"type":"hello","files":[{"name":"a.mp4","size":1,"sha256":"x","dur":-1}]}`},
		{"implausible duration", `{"type":"hello","files":[{"name":"a.mp4","size":1,"sha256":"x","dur":99999999999}]}`},
	}
	for _, c := range cases {
		if _, err := Decode([]byte(c.data)); err == nil {
			t.Errorf("%s: expected error, got none", c.name)
		}
	}
	if _, err := Encode(Message{Type: "bogus"}); err == nil {
		t.Error("encode bogus type: expected error")
	}
}

// FuzzDecode checks Decode never panics on arbitrary bytes — every
// field it touches (Files/Subs recursion, string sanitization, numeric
// bounds) runs on wire data straight from a relay, so a malformed or
// adversarial payload must fail cleanly with an error, never crash the
// process reading it.
func FuzzDecode(f *testing.F) {
	seeds := []string{
		`{"type":"hello","files":[{"name":"a.mkv","size":1,"sha256":"x"}]}`,
		`{"type":"state","state":{"paused":false,"pos":1,"at":1,"ver":1}}`,
		`{"type":"chat","text":"hi"}`,
		`{"type":"signal","signal":{"kind":"offer","sdp":"v=0"}}`,
		`{"type":"file-req","fidx":1,"off":0,"len":0}`,
		`{"type":"kick","kicked":"abc"}`,
		`{"type":"nope"}`,
		`garbage`,
		``,
		`{`,
		`null`,
		`[]`,
		`{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","subs":[{"name":"b","size":1,"sha256":"y"}]}]}`,
	}
	for _, s := range seeds {
		f.Add(s)
	}
	f.Fuzz(func(t *testing.T, data string) {
		_, _ = Decode([]byte(data))
	})
}

// TestPositionBoundsAcceptsPlausibleValues guards against an overly
// tight position bound rejecting ordinary playback positions, including
// a fresh 0 and a many-hour-long stream.
func TestPositionBoundsAcceptsPlausibleValues(t *testing.T) {
	for _, pos := range []float64{0, 12.5, 3600 * 10} {
		data, err := Encode(Message{Type: MsgState, State: &PlayState{Position: pos, SentAt: 1, Version: 1}})
		if err != nil {
			t.Fatalf("encode position %v: %v", pos, err)
		}
		if _, err := Decode(data); err != nil {
			t.Errorf("position %v must decode cleanly, got %v", pos, err)
		}
	}
}

// TestDurationBoundsAcceptsPlausibleValues guards against an overly tight
// duration bound rejecting ordinary files, including an unset (unknown)
// duration and a many-hour-long movie.
func TestDurationBoundsAcceptsPlausibleValues(t *testing.T) {
	for _, dur := range []float64{0, 125.5, 3600 * 3} {
		data, err := Encode(Message{Type: MsgHello, Files: []FileMeta{
			{Name: "a.mp4", Size: 1, SHA256: "x", DurationSecs: dur},
		}})
		if err != nil {
			t.Fatalf("encode duration %v: %v", dur, err)
		}
		if _, err := Decode(data); err != nil {
			t.Errorf("duration %v must decode cleanly, got %v", dur, err)
		}
	}
}

// TestSignalSDPAtCapAccepted guards against an off-by-one in the SDP
// length bound rejecting a legitimately large (but not hostile) offer —
// real SDPs with many ICE candidates can run several KB.
func TestSignalSDPAtCapAccepted(t *testing.T) {
	data, err := Encode(Message{Type: MsgSignal, To: "deadbeef", Signal: &Signal{Kind: "offer", SDP: strings.Repeat("x", maxSDPLen)}})
	if err != nil {
		t.Fatalf("encode at cap: %v", err)
	}
	if _, err := Decode(data); err != nil {
		t.Errorf("SDP exactly at maxSDPLen must decode cleanly, got %v", err)
	}
}
