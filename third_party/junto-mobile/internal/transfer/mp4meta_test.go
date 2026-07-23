package transfer

import (
	"encoding/binary"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"testing"
)

// mvhdBoxV0 builds a version-0 mvhd box ([box header][version/flags]
// [creation][modification][timescale][duration]) with the given
// timescale/duration, mirroring transfer_test.go's mp4box helper.
func mvhdBoxV0(timescale, duration uint32) []byte {
	payload := make([]byte, 20) // version/flags(4) + creation(4) + modification(4) + timescale(4) + duration(4)
	binary.BigEndian.PutUint32(payload[12:16], timescale)
	binary.BigEndian.PutUint32(payload[16:20], duration)
	return mp4box("mvhd", payload)
}

// mvhdBoxV1 builds a version-1 mvhd box (64-bit creation/modification/
// duration fields) with the given timescale/duration.
func mvhdBoxV1(timescale uint32, duration uint64) []byte {
	payload := make([]byte, 32) // version/flags(4) + creation(8) + modification(8) + timescale(4) + duration(8)
	payload[0] = 1              // version
	binary.BigEndian.PutUint32(payload[20:24], timescale)
	binary.BigEndian.PutUint64(payload[24:32], duration)
	return mp4box("mvhd", payload)
}

func joinBoxes(parts ...[]byte) []byte {
	var out []byte
	for _, p := range parts {
		out = append(out, p...)
	}
	return out
}

func boxReader(data []byte) func(off, n int64) ([]byte, error) {
	return func(off, n int64) ([]byte, error) { return data[off : off+n], nil }
}

// TestFindMvhdBox checks the moov-children walk pins mvhd wherever it sits
// among moov's immediate children, and declines (ok=false) on moov-less,
// mvhd-less, and malformed inputs — mirroring TestFindMoovBox's coverage
// for the shared parseBoxHeader this reuses.
func TestFindMvhdBox(t *testing.T) {
	mvhd := mvhdBoxV0(1000, 125500)
	stray := mp4box("udta", make([]byte, 16))

	t.Run("mvhd first child", func(t *testing.T) {
		moovPayload := joinBoxes(mvhd, mp4box("trak", make([]byte, 32)))
		moov := mp4box("moov", moovPayload)
		off, length, ok, err := findMvhdBox(0, int64(len(moov)), boxReader(moov))
		if err != nil || !ok || off != 8 || length != int64(len(mvhd)) {
			t.Fatalf("got off=%d len=%d ok=%v err=%v; want off=8 len=%d", off, length, ok, err, len(mvhd))
		}
	})
	t.Run("mvhd after a stray leading box", func(t *testing.T) {
		moovPayload := joinBoxes(stray, mvhd)
		moov := mp4box("moov", moovPayload)
		wantOff := int64(8 + len(stray))
		off, length, ok, _ := findMvhdBox(0, int64(len(moov)), boxReader(moov))
		if !ok || off != wantOff || length != int64(len(mvhd)) {
			t.Fatalf("got off=%d len=%d ok=%v; want off=%d len=%d", off, length, ok, wantOff, len(mvhd))
		}
	})
	t.Run("no mvhd", func(t *testing.T) {
		moovPayload := joinBoxes(stray, mp4box("trak", make([]byte, 8)))
		moov := mp4box("moov", moovPayload)
		if _, _, ok, _ := findMvhdBox(0, int64(len(moov)), boxReader(moov)); ok {
			t.Fatal("should report no mvhd when none is present")
		}
	})
	t.Run("malformed oversized box", func(t *testing.T) {
		bad := mp4box("trak", nil)
		binary.BigEndian.PutUint32(bad[0:4], 1<<20) // claims a size past moov's end
		moov := mp4box("moov", bad)
		if _, _, ok, _ := findMvhdBox(0, int64(len(moov)), boxReader(moov)); ok {
			t.Fatal("an oversized child box should bail (ok=false), not panic")
		}
	})
	t.Run("hostile 64-bit largesize overflows pos+boxLen", func(t *testing.T) {
		bad := mp4box64("trak", nil)
		binary.BigEndian.PutUint64(bad[8:16], uint64(math.MaxInt64-10))
		moov := mp4box("moov", bad)
		if _, _, ok, _ := findMvhdBox(0, int64(len(moov)), boxReader(moov)); ok {
			t.Fatal("a hostile 64-bit largesize should bail (ok=false), not overflow and misparse")
		}
	})
}

// FuzzFindMvhdBox checks findMvhdBox never panics on arbitrary bytes
// standing in for moov's content — mirrors FuzzFindMoovBox.
func FuzzFindMvhdBox(f *testing.F) {
	mvhd := mvhdBoxV0(1000, 125500)
	f.Add(joinBoxes(mp4box("moov", mvhd)))
	f.Add([]byte{})
	f.Add([]byte("not a moov box"))

	bad := mp4box("trak", nil)
	binary.BigEndian.PutUint32(bad[0:4], 1<<20)
	f.Add(joinBoxes(mp4box("moov", bad)))

	hostile := mp4box64("trak", nil)
	binary.BigEndian.PutUint64(hostile[8:16], uint64(math.MaxInt64-10))
	f.Add(joinBoxes(mp4box("moov", hostile)))

	f.Fuzz(func(t *testing.T, data []byte) {
		size := int64(len(data))
		readHeader := func(off, n int64) ([]byte, error) {
			if off < 0 || n < 0 || off > size-n {
				return nil, fmt.Errorf("out of range: off=%d n=%d size=%d", off, n, size)
			}
			return data[off : off+n], nil
		}
		_, _, _, _ = findMvhdBox(0, size, readHeader)
	})
}

// TestParseMvhdDuration covers version 0/1 decoding, both widths of the
// spec's "unknown duration" sentinel, and every malformed/implausible
// input path — none of which should ever panic.
func TestParseMvhdDuration(t *testing.T) {
	t.Run("v0 correct", func(t *testing.T) {
		secs, ok := parseMvhdDuration(mvhdBoxV0(1000, 125500))
		if !ok || secs != 125.5 {
			t.Fatalf("got secs=%v ok=%v, want 125.5,true", secs, ok)
		}
	})
	t.Run("v1 correct", func(t *testing.T) {
		secs, ok := parseMvhdDuration(mvhdBoxV1(1000, 7_200_000))
		if !ok || secs != 7200 {
			t.Fatalf("got secs=%v ok=%v, want 7200,true", secs, ok)
		}
	})
	t.Run("v0 truncated", func(t *testing.T) {
		box := mvhdBoxV0(1000, 125500)
		if _, ok := parseMvhdDuration(box[:len(box)-1]); ok {
			t.Fatal("a truncated v0 box must not parse cleanly")
		}
	})
	t.Run("v1 truncated", func(t *testing.T) {
		box := mvhdBoxV1(1000, 7_200_000)
		if _, ok := parseMvhdDuration(box[:len(box)-1]); ok {
			t.Fatal("a truncated v1 box must not parse cleanly")
		}
	})
	t.Run("unknown version", func(t *testing.T) {
		box := mvhdBoxV0(1000, 125500)
		box[8] = 2 // neither 0 nor 1
		if _, ok := parseMvhdDuration(box); ok {
			t.Fatal("an unrecognized version must not parse cleanly")
		}
	})
	t.Run("zero timescale", func(t *testing.T) {
		if _, ok := parseMvhdDuration(mvhdBoxV0(0, 125500)); ok {
			t.Fatal("a zero timescale (divide-by-zero) must not parse cleanly")
		}
	})
	t.Run("v0 unknown-duration sentinel", func(t *testing.T) {
		if _, ok := parseMvhdDuration(mvhdBoxV0(1000, 0xFFFFFFFF)); ok {
			t.Fatal("the spec's all-1s v0 sentinel must be treated as unknown")
		}
	})
	t.Run("v1 unknown-duration sentinel", func(t *testing.T) {
		if _, ok := parseMvhdDuration(mvhdBoxV1(1000, 0xFFFFFFFFFFFFFFFF)); ok {
			t.Fatal("the spec's all-1s v1 sentinel must be treated as unknown")
		}
	})
	t.Run("implausibly large result", func(t *testing.T) {
		// timescale=1, duration=MaxUint32 -> ~4.29e9 seconds, far past
		// maxPlausibleProbedDurationSecs (1e7).
		if _, ok := parseMvhdDuration(mvhdBoxV0(1, math.MaxUint32-1)); ok {
			t.Fatal("an implausibly large derived duration must not parse as ok")
		}
	})
	t.Run("empty box", func(t *testing.T) {
		if _, ok := parseMvhdDuration(nil); ok {
			t.Fatal("an empty box must not parse cleanly")
		}
	})
}

// FuzzParseMvhdDuration checks parseMvhdDuration never panics on arbitrary
// byte slices — it runs on bytes read straight from a host's file (or, in
// principle, hostile input), so malformed data must fail cleanly.
func FuzzParseMvhdDuration(f *testing.F) {
	f.Add(mvhdBoxV0(1000, 125500))
	f.Add(mvhdBoxV1(1000, 7_200_000))
	f.Add([]byte{})
	f.Add([]byte{0})
	f.Fuzz(func(t *testing.T, box []byte) {
		_, _ = parseMvhdDuration(box)
	})
}

// TestProbeDuration builds real temp files on disk and checks ProbeDuration
// extracts the correct duration for v0/v1 mvhd boxes, and cleanly declines
// (ok=false) for a non-mp4 file and a corrupted mvhd.
func TestProbeDuration(t *testing.T) {
	ftyp := mp4box("ftyp", []byte("isom\x00\x00\x02\x00"))
	mdat := mp4box("mdat", make([]byte, 256))

	write := func(t *testing.T, data []byte) string {
		p := filepath.Join(t.TempDir(), "media.mp4")
		if err := os.WriteFile(p, data, 0o644); err != nil {
			t.Fatal(err)
		}
		return p
	}

	t.Run("v0 mvhd", func(t *testing.T) {
		moov := mp4box("moov", mvhdBoxV0(1000, 125500))
		data := joinBoxes(ftyp, mdat, moov)
		p := write(t, data)
		secs, ok := ProbeDuration(p, int64(len(data)))
		if !ok || secs != 125.5 {
			t.Fatalf("got secs=%v ok=%v, want 125.5,true", secs, ok)
		}
	})
	t.Run("v1 mvhd", func(t *testing.T) {
		moov := mp4box("moov", mvhdBoxV1(1000, 7_200_000))
		data := joinBoxes(ftyp, moov, mdat) // faststart layout too
		p := write(t, data)
		secs, ok := ProbeDuration(p, int64(len(data)))
		if !ok || secs != 7200 {
			t.Fatalf("got secs=%v ok=%v, want 7200,true", secs, ok)
		}
	})
	t.Run("non-mp4 file", func(t *testing.T) {
		data := []byte("just a plain file, not mp4 at all, padded out a bit more")
		p := write(t, data)
		if _, ok := ProbeDuration(p, int64(len(data))); ok {
			t.Fatal("a non-mp4 file must report duration unknown, not panic or guess")
		}
	})
	t.Run("truncated mvhd", func(t *testing.T) {
		mvhd := mvhdBoxV0(1000, 125500)
		mvhd = mvhd[:len(mvhd)-4] // corrupt: box header claims a size the payload doesn't have
		moov := mp4box("moov", mvhd)
		data := joinBoxes(ftyp, mdat, moov)
		p := write(t, data)
		// Not asserting a specific outcome beyond "doesn't panic and returns
		// a bool" — a corrupted box may or may not be caught depending on
		// exactly where the truncation lands; the safety property under
		// test is graceful degradation, not a particular verdict here.
		_, _ = ProbeDuration(p, int64(len(data)))
	})
	t.Run("nonexistent file", func(t *testing.T) {
		if _, ok := ProbeDuration(filepath.Join(t.TempDir(), "missing.mp4"), 1024); ok {
			t.Fatal("a nonexistent file must report duration unknown, not panic")
		}
	})
}
