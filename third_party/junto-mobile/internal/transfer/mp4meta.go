package transfer

import (
	"encoding/binary"
	"os"
)

// parseBoxHeader decodes one ISO-BMFF box header at pos within a container
// ending at limit (an absolute offset): the 32-bit size, the 64-bit
// largesize form (size field == 1), and the to-EOF form (size field == 0).
// Shared by findMoovBox (walking a file's top-level boxes) and findMvhdBox
// (walking moov's children), so the hardened overflow-safety fix from the
// July 2026 audit lives in exactly one place. The bounds check is
// boxLen > limit-pos, not pos+boxLen > limit — a hostile 64-bit largesize
// near math.MaxInt64 would overflow the latter and wrap negative, making
// the check wrongly pass; limit-pos can never overflow since pos never
// exceeds limit in a well-formed walk. ok is false (nil err) for anything
// truncated or malformed; readHeader's own error propagates as err.
func parseBoxHeader(pos, limit int64, readHeader func(off, n int64) ([]byte, error)) (boxLen int64, typ string, ok bool, err error) {
	if pos+8 > limit {
		return 0, "", false, nil
	}
	n := int64(16) // enough for a 64-bit largesize header
	if limit-pos < n {
		n = limit - pos
	}
	hdr, herr := readHeader(pos, n)
	if herr != nil {
		return 0, "", false, herr
	}
	if len(hdr) < 8 {
		return 0, "", false, nil
	}
	boxLen = int64(binary.BigEndian.Uint32(hdr[0:4]))
	typ = string(hdr[4:8])
	switch {
	case boxLen == 1: // 64-bit extended size follows the type
		if len(hdr) < 16 {
			return 0, "", false, nil
		}
		boxLen = int64(binary.BigEndian.Uint64(hdr[8:16]))
	case boxLen == 0: // box runs to EOF
		boxLen = limit - pos
	}
	if boxLen < 8 || boxLen > limit-pos {
		return 0, "", false, nil // malformed
	}
	return boxLen, typ, true, nil
}

// mvhdWalkLimit bounds how many of moov's immediate children findMvhdBox
// inspects. mvhd is conventionally moov's very first child; a small bound
// tolerates a stray leading box without opening the door to a pathological
// moov driving unbounded header reads.
const mvhdWalkLimit = 16

// findMvhdBox locates mvhd's [off, off+length) among moov's immediate
// children, given moov's own [moovOff, moovOff+moovLen) as already located
// by findMoovBox. moov is a plain Box (not a FullBox), so its content
// starting at moovOff+8 is a straight sequence of child boxes. ok is false
// (nil err) when moov is too short, malformed, or has no mvhd within
// mvhdWalkLimit boxes; callers must treat that as "duration unknown,"
// never as an error.
func findMvhdBox(moovOff, moovLen int64, readHeader func(off, n int64) ([]byte, error)) (off, length int64, ok bool, err error) {
	end := moovOff + moovLen
	pos := moovOff + 8 // skip moov's own header
	for box := 0; box < mvhdWalkLimit && pos+8 <= end; box++ {
		boxLen, typ, hok, herr := parseBoxHeader(pos, end, readHeader)
		if herr != nil {
			return 0, 0, false, herr
		}
		if !hok {
			return 0, 0, false, nil
		}
		if typ == "mvhd" {
			return pos, boxLen, true, nil
		}
		pos += boxLen
	}
	return 0, 0, false, nil
}

// mvhd (Movie Header Box) field layout, ISO/IEC 14496-12. Offsets are from
// the box's own header start (box[0:4]=size, box[4:8]="mvhd"):
//
//	field              v0 offset:width   v1 offset:width
//	version            box[8]:1          box[8]:1
//	flags              box[9:12]:3       box[9:12]:3
//	creation_time      box[12:16]:4      box[12:20]:8
//	modification_time  box[16:20]:4      box[20:28]:8
//	timescale          box[20:24]:4      box[28:32]:4
//	duration           box[24:28]:4      box[32:40]:8
const (
	mvhdV0MinLen = 28 // header(8)+version/flags(4)+times(8)+timescale(4)+duration(4)
	mvhdV1MinLen = 40 // header(8)+version/flags(4)+times(16)+timescale(4)+duration(8)
)

// maxPlausibleProbedDurationSecs bounds a locally-parsed mvhd duration
// before it's ever attached to an outgoing FileMeta — mirrors
// protocol.maxPlausibleDurationSecs (kept independent rather than
// exported cross-package: both are safety nets, not exact business logic,
// so minor drift is harmless). This guard exists so a parse oddity never
// produces a value that would make the host's own outgoing hello fail
// protocol.Encode's validation — safer to just call it unknown.
const maxPlausibleProbedDurationSecs = 1e7 // ~115 days

// parseMvhdDuration decodes timescale/duration from mvhd's raw bytes (box
// header included, as returned by findMvhdBox) and returns the derived
// duration in seconds. Handles both version 0 (32-bit fields) and version
// 1 (64-bit fields) — real encoders emit both depending on whether
// anything else in the file needs 64-bit offsets. ok is false for: a box
// too short for its declared version, an unrecognized version, a zero
// timescale (divide-by-zero), the spec's reserved "unknown duration"
// sentinel (all bits set), or a derived value outside a plausible range.
// Never panics on hostile/truncated input.
func parseMvhdDuration(box []byte) (durationSecs float64, ok bool) {
	if len(box) < 9 {
		return 0, false
	}
	var timescale uint32
	var duration uint64
	switch box[8] { // version
	case 0:
		if len(box) < mvhdV0MinLen {
			return 0, false
		}
		timescale = binary.BigEndian.Uint32(box[20:24])
		duration = uint64(binary.BigEndian.Uint32(box[24:28]))
		if duration == 0xFFFFFFFF {
			return 0, false // spec sentinel for "unknown duration"
		}
	case 1:
		if len(box) < mvhdV1MinLen {
			return 0, false
		}
		timescale = binary.BigEndian.Uint32(box[28:32])
		duration = binary.BigEndian.Uint64(box[32:40])
		if duration == 0xFFFFFFFFFFFFFFFF {
			return 0, false // spec sentinel for "unknown duration"
		}
	default:
		return 0, false
	}
	if timescale == 0 {
		return 0, false
	}
	secs := float64(duration) / float64(timescale)
	if secs <= 0 || secs > maxPlausibleProbedDurationSecs {
		return 0, false
	}
	return secs, true
}

// ProbeDuration attempts to read an mp4/mov file's duration by parsing its
// local moov/mvhd boxes from disk — no network fetch needed, since this
// runs on the host, which already has the whole file when announcing it.
// ok is false for anything that isn't a cleanly parseable mp4 duration
// (non-mp4-family file, unlocatable/malformed moov or mvhd, implausible
// result) — callers must treat that as "duration unknown" (today's fixed
// pre-buffer cushion applies), never as an error; this must never block
// hosting a file.
func ProbeDuration(path string, size int64) (durationSecs float64, ok bool) {
	f, err := os.Open(path)
	if err != nil {
		return 0, false
	}
	defer f.Close()
	readHeader := func(off, n int64) ([]byte, error) {
		buf := make([]byte, n)
		if _, err := f.ReadAt(buf, off); err != nil {
			return nil, err
		}
		return buf, nil
	}
	moovOff, moovLen, moovOK, err := findMoovBox(size, readHeader)
	if err != nil || !moovOK {
		return 0, false
	}
	mvOff, mvLen, mvOK, err := findMvhdBox(moovOff, moovLen, readHeader)
	if err != nil || !mvOK {
		return 0, false
	}
	n := mvLen
	if n > mvhdV1MinLen {
		n = mvhdV1MinLen // never need more than v1's fixed fields
	}
	box, err := readHeader(mvOff, n)
	if err != nil {
		return 0, false
	}
	return parseMvhdDuration(box)
}
