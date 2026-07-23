package transfer

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestImportVerifiedAcceptsMatchingLocalFile(t *testing.T) {
	source := filepath.Join(t.TempDir(), "source.flac")
	payload := bytes.Repeat([]byte("verified-audio"), 4096)
	if err := os.WriteFile(source, payload, 0o600); err != nil {
		t.Fatal(err)
	}
	hashes, err := HashForServe(source)
	if err != nil {
		t.Fatal(err)
	}
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{
		Name: "room.flac", Size: int64(len(payload)), SHA256: hashes.SHA256,
		Bao: hashes.BaoRoot, BaoGroup: hashes.BaoGroup, BaoOb: hashes.BaoObSHA,
	}})
	if err != nil {
		t.Fatal(err)
	}
	imported, err := store.ImportVerified(0, source)
	if err != nil {
		t.Fatal(err)
	}
	if !imported || !store.Done(0) || !store.AllDone() {
		t.Fatal("matching local file was not installed as a completed verified file")
	}
	got, err := os.ReadFile(store.PathFor(0))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, payload) {
		t.Fatal("imported file differs from source")
	}
}

func TestImportVerifiedRejectsDifferentContentWithSameSize(t *testing.T) {
	dir := t.TempDir()
	expected := filepath.Join(dir, "expected.flac")
	candidate := filepath.Join(dir, "candidate.flac")
	if err := os.WriteFile(expected, bytes.Repeat([]byte{1}, 64*1024), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(candidate, bytes.Repeat([]byte{2}, 64*1024), 0o600); err != nil {
		t.Fatal(err)
	}
	hashes, err := HashForServe(expected)
	if err != nil {
		t.Fatal(err)
	}
	store, err := NewFileStore(t.TempDir(), []protocol.FileMeta{{
		Name: "room.flac", Size: 64 * 1024, SHA256: hashes.SHA256,
		Bao: hashes.BaoRoot, BaoGroup: hashes.BaoGroup, BaoOb: hashes.BaoObSHA,
	}})
	if err != nil {
		t.Fatal(err)
	}
	imported, err := store.ImportVerified(0, candidate)
	if err != nil {
		t.Fatal(err)
	}
	if imported || store.Done(0) {
		t.Fatal("mismatched local file must not be trusted")
	}
}
