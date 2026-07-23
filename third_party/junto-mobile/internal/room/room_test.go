package room

import (
	"encoding/hex"
	"strings"
	"testing"
)

func TestCodeRoundTrip(t *testing.T) {
	r, err := New()
	if err != nil {
		t.Fatal(err)
	}
	code := r.Code()
	if !strings.HasPrefix(code, "jun1") {
		t.Fatalf("code %q missing prefix", code)
	}
	r2, err := Parse(code)
	if err != nil {
		t.Fatalf("parse own code %q: %v", code, err)
	}
	if r2.Secret != r.Secret || r2.ID != r.ID || r2.ConvKey != r.ConvKey {
		t.Error("round trip mismatch")
	}
	// Codes are case-insensitive on input (people read them aloud).
	if r3, err := Parse(strings.ToUpper(code[:3]) + strings.ToUpper(code[3:])); err != nil || r3.ID != r.ID {
		t.Errorf("uppercase parse failed: %v", err)
	}
}

func TestParseRejects(t *testing.T) {
	for _, bad := range []string{
		"",
		"xx1abcdefghijklmnopqrstuvwxya",        // wrong prefix
		"jun1abc",                              // too short
		"jun1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // too long
		"jun1!!!!!!!!!!!!!!!!!!!!!!!!!!",       // invalid chars
	} {
		if _, err := Parse(bad); err == nil {
			t.Errorf("Parse(%q): expected error", bad)
		}
	}
}

// FuzzParse checks Parse never panics on arbitrary input and, whenever
// it does report success, produces a Room whose invariants (32-byte
// secret material derived into a 64-hex-char ID, a non-empty ConvKey)
// hold — a decode boundary fed directly from a user-pasted string, so a
// malformed or adversarial room code must fail cleanly, never crash.
func FuzzParse(f *testing.F) {
	seeds := []string{
		"",
		"jun1",
		"jun1abc",
		"xx1abcdefghijklmnopqrstuvwxya",
		"JUN1ABCDEFGHIJKLMNOPQRSTUVWXYA",
		"jun1!!!!!!!!!!!!!!!!!!!!!!!!!!",
		"jun1\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00",
	}
	if r, err := New(); err == nil {
		seeds = append(seeds, r.Code())
	}
	for _, s := range seeds {
		f.Add(s)
	}
	f.Fuzz(func(t *testing.T, code string) {
		r, err := Parse(code)
		if err != nil {
			if r != nil {
				t.Errorf("Parse(%q) returned a non-nil Room alongside an error", code)
			}
			return
		}
		if len(r.ID) != 64 {
			t.Errorf("Parse(%q) produced an ID of length %d, want 64", code, len(r.ID))
		}
		if r.ConvKey == ([32]byte{}) {
			t.Errorf("Parse(%q) produced an all-zero ConvKey", code)
		}
	})
}

func TestDerivationDeterministicAndSeparated(t *testing.T) {
	r, err := New()
	if err != nil {
		t.Fatal(err)
	}
	again := fromSecret(r.Secret)
	if again.ID != r.ID || again.ConvKey != r.ConvKey {
		t.Error("derivation not deterministic")
	}
	if r.ID == hex.EncodeToString(r.ConvKey[:]) {
		t.Error("room ID equals conv key: missing domain separation")
	}
	if len(r.ID) != 64 {
		t.Errorf("room ID length %d, want 64 hex chars", len(r.ID))
	}
}
