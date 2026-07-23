// Package room handles the shared room secret: the human-shareable
// room code, the public room ID used as a relay tag, and the NIP-44
// conversation key. Knowing the room ID reveals nothing about the
// encryption key thanks to domain-separated derivations.
package room

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base32"
	"encoding/hex"
	"fmt"
	"strings"
)

const codePrefix = "jun1"

// Lowercase RFC-4648 alphabet, no padding: 16 secret bytes -> 26 chars.
var codeEncoding = base32.NewEncoding("abcdefghijklmnopqrstuvwxyz234567").WithPadding(base32.NoPadding)

type Room struct {
	Secret  [16]byte
	ID      string   // hex sha256, the public "r" tag value
	ConvKey [32]byte // nip44 conversation key
}

func New() (*Room, error) {
	var secret [16]byte
	if _, err := rand.Read(secret[:]); err != nil {
		return nil, fmt.Errorf("generating room secret: %w", err)
	}
	return fromSecret(secret), nil
}

func Parse(code string) (*Room, error) {
	code = strings.ToLower(strings.TrimSpace(code))
	if code == "" {
		return nil, fmt.Errorf("no room code given — ask the host to share theirs (it looks like %sabc…)", codePrefix)
	}
	if !strings.HasPrefix(code, codePrefix) {
		return nil, fmt.Errorf("that doesn't look like a junto room code — it should start with %q; double-check what the host shared", codePrefix)
	}
	raw, err := codeEncoding.DecodeString(code[len(codePrefix):])
	if err != nil || len(raw) != 16 {
		return nil, fmt.Errorf("that room code looks incomplete or garbled — recheck it was copied in full")
	}
	var secret [16]byte
	copy(secret[:], raw)
	return fromSecret(secret), nil
}

func (r *Room) Code() string {
	return codePrefix + codeEncoding.EncodeToString(r.Secret[:])
}

func fromSecret(secret [16]byte) *Room {
	id := sha256.Sum256(append([]byte("junto/room-id/v1"), secret[:]...))
	key := sha256.Sum256(append([]byte("junto/nip44-key/v1"), secret[:]...))
	return &Room{Secret: secret, ID: hex.EncodeToString(id[:]), ConvKey: key}
}
