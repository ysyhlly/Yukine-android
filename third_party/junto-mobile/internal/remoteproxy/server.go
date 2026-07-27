package remoteproxy

import (
	"context"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Source is private device-local state. URL and Headers must never enter room messages.
// FallbackURL points to Junto's loopback sparse-file server.
type Source struct {
	URL         string
	Headers     map[string]string
	Size        int64
	FallbackURL string
	Refresh     func(context.Context) (Source, error)
}

// proxyHTTPClient bounds upstream hangs so a stuck origin cannot pin proxy goroutines forever.
// Timeout covers the whole request; ResponseHeaderTimeout bounds header wait on slow streams.
var proxyHTTPClient = &http.Client{
	Timeout: 30 * time.Second,
	Transport: &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		ResponseHeaderTimeout: 10 * time.Second,
		IdleConnTimeout:       90 * time.Second,
	},
}

type sourceState struct {
	mu     sync.RWMutex
	source Source
}

// Server keeps a stable loopback URL while refreshing upstream URLs or falling back to host relay.
type Server struct {
	ln      net.Listener
	srv     *http.Server
	sources map[int]*sourceState
	cancel  context.CancelFunc
}

func New(sources map[int]Source) (*Server, error) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, fmt.Errorf("binding remote proxy: %w", err)
	}
	states := make(map[int]*sourceState, len(sources))
	for index, source := range sources {
		if index < 0 || source.Size <= 0 || !validHTTPURL(source.URL) {
			_ = ln.Close()
			return nil, fmt.Errorf("invalid remote proxy source %d", index)
		}
		states[index] = &sourceState{source: cloneSource(source)}
	}
	baseCtx, cancel := context.WithCancel(context.Background())
	server := &Server{ln: ln, sources: states, cancel: cancel}
	mux := http.NewServeMux()
	mux.HandleFunc("/", server.handle)
	server.srv = &http.Server{
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
		BaseContext:       func(net.Listener) context.Context { return baseCtx },
	}
	return server, nil
}

func (s *Server) Start() { go s.srv.Serve(s.ln) }

func (s *Server) URL(index int) string {
	return fmt.Sprintf("http://127.0.0.1:%d/%d", s.ln.Addr().(*net.TCPAddr).Port, index)
}

func (s *Server) Close(ctx context.Context) error {
	s.cancel()
	return s.srv.Shutdown(ctx)
}

func (s *Server) handle(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	index, err := strconv.Atoi(strings.TrimPrefix(r.URL.Path, "/"))
	state := s.sources[index]
	if err != nil || state == nil {
		http.NotFound(w, r)
		return
	}
	response, err := state.open(r.Context(), r.Method, r.Header.Get("Range"))
	if err != nil {
		http.Error(w, "cloud source unavailable", http.StatusBadGateway)
		return
	}
	defer response.Body.Close()
	for _, name := range []string{
		"Accept-Ranges", "Content-Length", "Content-Range", "Content-Type", "ETag", "Last-Modified",
	} {
		if value := response.Header.Get(name); value != "" {
			w.Header().Set(name, value)
		}
	}
	w.WriteHeader(response.StatusCode)
	if r.Method == http.MethodHead {
		return
	}
	_, _ = io.Copy(w, response.Body)
}

func (s *sourceState) open(ctx context.Context, method, rangeHeader string) (*http.Response, error) {
	var lastErr error
	for attempt := 0; attempt < 3; attempt++ {
		source := s.snapshot()
		response, err := openSource(ctx, source, method, rangeHeader)
		if err == nil {
			return response, nil
		}
		lastErr = err
		if attempt == 0 && source.Refresh != nil {
			refreshed, refreshErr := source.Refresh(ctx)
			if refreshErr == nil && refreshed.Size == source.Size && validHTTPURL(refreshed.URL) {
				refreshed.FallbackURL = source.FallbackURL
				refreshed.Refresh = source.Refresh
				s.replace(refreshed)
				continue
			}
		}
		if source.FallbackURL != "" && source.URL != source.FallbackURL {
			s.replace(Source{URL: source.FallbackURL, Size: source.Size})
			continue
		}
		break
	}
	return nil, lastErr
}

func openSource(ctx context.Context, source Source, method, rangeHeader string) (*http.Response, error) {
	request, err := http.NewRequestWithContext(ctx, method, source.URL, nil)
	if err != nil {
		return nil, err
	}
	for key, value := range source.Headers {
		if strings.EqualFold(key, "Host") || strings.EqualFold(key, "Range") {
			continue
		}
		request.Header.Set(key, value)
	}
	request.Header.Set("Accept-Encoding", "identity")
	if rangeHeader != "" {
		request.Header.Set("Range", rangeHeader)
	}
	response, err := proxyHTTPClient.Do(request)
	if err != nil {
		return nil, err
	}
	validStatus := response.StatusCode == http.StatusOK ||
		response.StatusCode == http.StatusPartialContent
	if rangeHeader != "" {
		validStatus = response.StatusCode == http.StatusPartialContent
	}
	if !validStatus {
		response.Body.Close()
		return nil, fmt.Errorf("upstream status %d", response.StatusCode)
	}
	return response, nil
}

func (s *sourceState) snapshot() Source {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return cloneSource(s.source)
}

func (s *sourceState) replace(source Source) {
	s.mu.Lock()
	s.source = cloneSource(source)
	s.mu.Unlock()
}

func cloneSource(source Source) Source {
	cloned := source
	if source.Headers != nil {
		cloned.Headers = make(map[string]string, len(source.Headers))
		for key, value := range source.Headers {
			cloned.Headers[key] = value
		}
	}
	return cloned
}

func validHTTPURL(value string) bool {
	parsed, err := url.Parse(value)
	return err == nil &&
		(parsed.Scheme == "http" || parsed.Scheme == "https") &&
		parsed.Host != ""
}
