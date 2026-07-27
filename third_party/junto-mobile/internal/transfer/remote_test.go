package transfer

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHTTPRangeReaderRefreshesWithoutMovingPosition(t *testing.T) {
	var refreshedRange string
	fresh := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		refreshedRange = r.Header.Get("Range")
		if r.Header.Get("X-Token") != "fresh" || r.Header.Get("Host") == "evil.invalid" {
			t.Errorf("sanitized headers = %#v", r.Header)
		}
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte("cd"))
	}))
	defer fresh.Close()
	expired := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer expired.Close()

	reader := &httpRangeReader{
		ctx: context.Background(),
		source: HTTPRangeSource{
			URL: expired.URL, Size: 4,
			Refresh: func(context.Context) (HTTPRangeSource, error) {
				return HTTPRangeSource{
					URL: fresh.URL, Size: 4,
					Headers: map[string]string{"X-Token": "fresh", "Host": "evil.invalid", "Range": "bytes=0-0"},
				}, nil
			},
		},
		client: http.DefaultClient,
	}
	if _, err := reader.Seek(2, io.SeekStart); err != nil {
		t.Fatal(err)
	}
	buffer := make([]byte, 2)
	n, err := reader.Read(buffer)
	if err != nil || n != 2 || string(buffer) != "cd" {
		t.Fatalf("Read = %d, %q, %v", n, buffer, err)
	}
	if refreshedRange != "bytes=2-3" {
		t.Fatalf("refresh changed read position: Range = %q", refreshedRange)
	}
}
