package syncer

import (
	"fmt"
	"hash/fnv"
)

// nickPalette holds ANSI-256 codes chosen to stay readable on both
// dark and light terminals and to be distinguishable from each other.
var nickPalette = []uint8{39, 42, 78, 81, 105, 135, 167, 172, 205, 214}

func colorFor(pub string) uint8 {
	h := fnv.New32a()
	h.Write([]byte(pub))
	return nickPalette[h.Sum32()%uint32(len(nickPalette))]
}

// colorize wraps s in the pubkey's color when enabled; the same pubkey
// always gets the same color.
func colorize(s, pub string, on bool) string {
	if !on {
		return s
	}
	return colorizeCode(s, colorFor(pub), on)
}

// colorizeCode wraps s in an explicit ANSI-256 color code (used when the
// code is already known, e.g. from a Snapshot's PeerStatus.Color).
func colorizeCode(s string, code uint8, on bool) string {
	if !on {
		return s
	}
	return fmt.Sprintf("\x1b[38;5;%dm%s\x1b[0m", code, s)
}
