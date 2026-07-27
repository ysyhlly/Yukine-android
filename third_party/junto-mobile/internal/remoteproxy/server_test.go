package remoteproxy

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestProxyKeepsHeadersPrivateAndForwardsRange(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := r.Header.Get("Range"); got != "bytes=1-2" {
			t.Errorf("Range = %q", got)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer local-secret" {
			t.Errorf("Authorization = %q", got)
		}
		w.Header().Set("Content-Range", "bytes 1-2/4")
		w.Header().Set("Content-Length", "2")
		w.Header().Set("Set-Cookie", "must-not-escape=1")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte("bc"))
	}))
	defer upstream.Close()

	proxy, err := New(map[int]Source{
		3: {URL: upstream.URL, Size: 4, Headers: map[string]string{"Authorization": "Bearer local-secret"}},
	})
	if err != nil {
		t.Fatal(err)
	}
	proxy.Start()
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = proxy.Close(ctx)
	})

	request, _ := http.NewRequest(http.MethodGet, proxy.URL(3), nil)
	request.Header.Set("Range", "bytes=1-2")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)
	if response.StatusCode != http.StatusPartialContent || string(body) != "bc" {
		t.Fatalf("status/body = %d/%q", response.StatusCode, body)
	}
	if got := response.Header.Get("Set-Cookie"); got != "" {
		t.Fatalf("private upstream cookie escaped proxy: %q", got)
	}
}

func TestProxyFallsBackToHostRelayAtSameURL(t *testing.T) {
	unavailable := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer unavailable.Close()
	fallback := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Range") != "bytes=0-1" {
			t.Fatalf("fallback Range = %q", r.Header.Get("Range"))
		}
		w.Header().Set("Content-Range", "bytes 0-1/2")
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte("ok"))
	}))
	defer fallback.Close()

	proxy, err := New(map[int]Source{
		0: {
			URL: unavailable.URL, Size: 2, FallbackURL: fallback.URL,
			Refresh: func(context.Context) (Source, error) {
				return Source{}, errors.New("account unavailable")
			},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	proxy.Start()
	t.Cleanup(func() {
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		_ = proxy.Close(ctx)
	})
	stableURL := proxy.URL(0)
	request, _ := http.NewRequest(http.MethodGet, stableURL, nil)
	request.Header.Set("Range", "bytes=0-1")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)
	if response.StatusCode != http.StatusPartialContent || string(body) != "ok" {
		t.Fatalf("fallback status/body = %d/%q", response.StatusCode, body)
	}
	if proxy.URL(0) != stableURL {
		t.Fatal("logical playback URL changed during fallback")
	}
}
