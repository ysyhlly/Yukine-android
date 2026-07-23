package syncer

import (
	"strings"
	"testing"
)

func TestColorForDeterministic(t *testing.T) {
	pub := "f07ca797deadbeef"
	c := colorFor(pub)
	for i := 0; i < 10; i++ {
		if colorFor(pub) != c {
			t.Fatal("colorFor not deterministic")
		}
	}
	found := false
	for _, p := range nickPalette {
		if p == c {
			found = true
		}
	}
	if !found {
		t.Errorf("color %d not in palette", c)
	}
}

func TestColorizeOnOff(t *testing.T) {
	plain := colorize("alice", "abc123", false)
	if plain != "alice" {
		t.Errorf("disabled colorize altered the string: %q", plain)
	}
	colored := colorize("alice", "abc123", true)
	if !strings.Contains(colored, "alice") || !strings.HasPrefix(colored, "\x1b[38;5;") || !strings.HasSuffix(colored, "\x1b[0m") {
		t.Errorf("unexpected colored form: %q", colored)
	}
}

func TestDifferentPubkeysCanDiffer(t *testing.T) {
	// Not guaranteed for any pair (10 buckets), but these two known
	// inputs hash to different palette entries; failure means the
	// hashing changed unintentionally.
	if colorFor("alice-pubkey") == colorFor("bob-pubkey-xyz") {
		t.Error("expected these pubkeys to map to different colors")
	}
}
