package protocol

import (
	"encoding/json"
	"strings"
	"testing"
)

var (
	hexRoot = strings.Repeat("ab", 32)
	hexOb   = strings.Repeat("cd", 32)
)

func TestBaoMetaRoundTrip(t *testing.T) {
	m := Message{Type: MsgHello, Files: []FileMeta{{
		Name: "a.mkv", Size: 1 << 30, SHA256: "x",
		Bao: hexRoot, BaoGroup: 8, BaoOb: hexOb,
		Subs: []FileMeta{{Name: "a.srt", Size: 100, SHA256: "y", Bao: hexRoot, BaoGroup: 8, BaoOb: hexOb}},
	}}}
	b, err := Encode(m)
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	got, err := Decode(b)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	f := got.Files[0]
	if f.Bao != hexRoot || f.BaoGroup != 8 || f.BaoOb != hexOb {
		t.Fatalf("bao fields lost in round-trip: %+v", f)
	}
	if f.Subs[0].Bao != hexRoot {
		t.Fatal("sub bao root lost in round-trip")
	}
}

func TestBaoMetaOptional(t *testing.T) {
	// A pre-swarm hello (no bao fields) must stay valid, and the fields
	// must be omitted from the wire when unset so old binaries see
	// byte-identical hellos.
	m := Message{Type: MsgHello, Files: []FileMeta{{Name: "a.mkv", Size: 1, SHA256: "x"}}}
	b, err := Encode(m)
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	for _, field := range []string{"bao", "baog", "baoob", "hv", "hvd"} {
		if strings.Contains(string(b), `"`+field+`"`) {
			t.Fatalf("unset swarm field %q serialized: %s", field, b)
		}
	}
	if _, err := Decode(b); err != nil {
		t.Fatalf("decode: %v", err)
	}
}

func TestHaveRoundTrip(t *testing.T) {
	m := Message{Type: MsgState, Nick: "n",
		State:    &PlayState{Position: 1, SentAt: 1, Version: 1},
		Have:     []HaveEntry{{File: 0, Pieces: [][2]int{{0, 10}, {50, 51}}}},
		HaveDone: [][2]int{{1, 3}},
	}
	b, err := Encode(m)
	if err != nil {
		t.Fatalf("encode: %v", err)
	}
	got, err := Decode(b)
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	if len(got.Have) != 1 || got.Have[0].Pieces[1] != [2]int{50, 51} {
		t.Fatalf("have lost in round-trip: %+v", got.Have)
	}
	if len(got.HaveDone) != 1 || got.HaveDone[0] != [2]int{1, 3} {
		t.Fatalf("have-done lost in round-trip: %+v", got.HaveDone)
	}
}

// TestSwarmFieldsIgnoredByOldStruct pins the backward-compatibility
// mechanism itself: a message carrying the new fields must unmarshal
// cleanly into a struct that predates them (Go's decoder ignores unknown
// fields), because old binaries in the room will receive these heartbeats.
func TestSwarmFieldsIgnoredByOldStruct(t *testing.T) {
	b, err := Encode(Message{Type: MsgState, State: &PlayState{Position: 1, SentAt: 1, Version: 1},
		Have: []HaveEntry{{File: 0, Pieces: [][2]int{{0, 1}}}}})
	if err != nil {
		t.Fatal(err)
	}
	var old struct {
		Type  MsgType    `json:"type"`
		State *PlayState `json:"state"`
	}
	if err := json.Unmarshal(b, &old); err != nil {
		t.Fatalf("old-shape struct rejected new fields: %v", err)
	}
	if old.State == nil || old.State.Position != 1 {
		t.Fatal("old-shape struct lost the state payload")
	}
}

func TestSwarmRejects(t *testing.T) {
	state := `"state":{"paused":false,"pos":1,"at":1,"ver":1}`
	manyPieces := strings.Repeat(`[0,1],`, MaxHavePieces)
	manyFiles := strings.Repeat(`{"f":0,"p":[[0,1]]},`, MaxHaveFiles)
	manyDone := strings.Repeat(`[0,1],`, MaxHaveDone)
	cases := []struct {
		name string
		data string
	}{
		{"partial bao: root only", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"` + hexRoot + `"}]}`},
		{"partial bao: group without root", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","baog":8}]}`},
		{"partial bao: ob hash without root", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","baoob":"` + hexOb + `"}]}`},
		{"short bao root", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"abcd","baog":8,"baoob":"` + hexOb + `"}]}`},
		{"non-hex bao root", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"` + strings.Repeat("zz", 32) + `","baog":8,"baoob":"` + hexOb + `"}]}`},
		{"non-hex ob hash", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"` + hexRoot + `","baog":8,"baoob":"` + strings.Repeat("zz", 32) + `"}]}`},
		{"bao group negative", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"` + hexRoot + `","baog":-1,"baoob":"` + hexOb + `"}]}`},
		{"bao group oversized", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","bao":"` + hexRoot + `","baog":11,"baoob":"` + hexOb + `"}]}`},
		{"bad bao in sub", `{"type":"hello","files":[{"name":"a","size":1,"sha256":"x","subs":[{"name":"s","size":1,"sha256":"y","bao":"abcd","baog":8,"baoob":"` + hexOb + `"}]}]}`},
		{"too many have files", `{"type":"state",` + state + `,"hv":[` + manyFiles + `{"f":0,"p":[[0,1]]}]}`},
		{"have file out of range", `{"type":"state",` + state + `,"hv":[{"f":99999,"p":[[0,1]]}]}`},
		{"have file negative", `{"type":"state",` + state + `,"hv":[{"f":-1,"p":[[0,1]]}]}`},
		{"too many piece spans", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[` + manyPieces + `[0,1]]}]}`},
		{"piece span empty", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[5,5]]}]}`},
		{"piece span inverted", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[5,2]]}]}`},
		{"piece span negative", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[-1,2]]}]}`},
		{"piece span huge", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[0,4294967296]]}]}`},
		{"piece spans unsorted", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[10,20],[0,5]]}]}`},
		{"piece spans overlapping", `{"type":"state",` + state + `,"hv":[{"f":0,"p":[[0,10],[5,15]]}]}`},
		{"too many done ranges", `{"type":"state",` + state + `,"hvd":[` + manyDone + `[0,1]]}`},
		{"done range inverted", `{"type":"state",` + state + `,"hvd":[[3,1]]}`},
		{"done range out of range", `{"type":"state",` + state + `,"hvd":[[0,99999]]}`},
		{"done ranges overlapping", `{"type":"state",` + state + `,"hvd":[[0,5],[3,8]]}`},
		{"have on non-state bounded too", `{"type":"chat","text":"hi","hv":[{"f":-1,"p":[[0,1]]}]}`},
	}
	for _, c := range cases {
		if _, err := Decode([]byte(c.data)); err == nil {
			t.Errorf("%s: expected error, got none", c.name)
		}
	}
}

func TestSwarmAcceptsAtCaps(t *testing.T) {
	// Exactly at every cap must pass — the caps are limits, not off-by-one
	// traps for a legitimately fragmented download.
	var have []HaveEntry
	for i := 0; i < MaxHaveFiles; i++ {
		var pieces [][2]int
		for j := 0; j < MaxHavePieces; j++ {
			pieces = append(pieces, [2]int{j * 2, j*2 + 1})
		}
		have = append(have, HaveEntry{File: i, Pieces: pieces})
	}
	var done [][2]int
	for i := 0; i < MaxHaveDone; i++ {
		done = append(done, [2]int{i * 2, i*2 + 1})
	}
	m := Message{Type: MsgState, State: &PlayState{Position: 1, SentAt: 1, Version: 1}, Have: have, HaveDone: done}
	b, err := Encode(m)
	if err != nil {
		t.Fatalf("at-cap advertisement rejected: %v", err)
	}
	if _, err := Decode(b); err != nil {
		t.Fatalf("at-cap advertisement rejected on decode: %v", err)
	}
}
