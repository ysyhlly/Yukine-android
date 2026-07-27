package mobile

import (
	"testing"

	"github.com/swayam-mishra/junto/internal/protocol"
)

func TestPublicQueueMetadataUsesDisplayFields(t *testing.T) {
	file := protocol.FileMeta{
		Name:         "fallback.flac",
		PublicTitle:  "海底列车",
		DurationSecs: 235.4,
		PublicAlbum:  "真实专辑",
		PublicArtist: "PIKASONIC / なこたんまる",
	}
	if got := displayTitle(file); got != "海底列车" {
		t.Fatalf("displayTitle = %q", got)
	}
	if got := displayDurationMS(file); got != 235400 {
		t.Fatalf("displayDurationMS = %d", got)
	}
}

func TestPublicArtworkURIOnlyAllowsNetworkArtwork(t *testing.T) {
	for _, test := range []struct {
		input string
		want  string
	}{
		{"https://cdn.example/cover.jpg", "https://cdn.example/cover.jpg"},
		{"http://cdn.example/cover.jpg", "http://cdn.example/cover.jpg"},
		{"content://media/cover/1", ""},
		{"file:///private/cover.jpg", ""},
		{"data:image/png;base64,secret", ""},
		{"https://user:password@example.com/cover.jpg", ""},
	} {
		if got := publicArtworkURI(test.input); got != test.want {
			t.Errorf("publicArtworkURI(%q) = %q, want %q", test.input, got, test.want)
		}
	}
}
