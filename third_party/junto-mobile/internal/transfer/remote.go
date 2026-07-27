package transfer

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/swayam-mishra/junto/internal/debug"
)

// HTTPRangeSource is private host state. Callers must never put URL or Headers in protocol
// messages or logs; peers only see the encrypted bytes served through the existing data channel.
type HTTPRangeSource struct {
	URL     string
	Headers map[string]string
	Size    int64
	Refresh func(context.Context) (HTTPRangeSource, error)
}

// remoteHTTPClient bounds hung origins. No overall Timeout so multi-chunk range reads for large
// files can continue; ResponseHeaderTimeout still prevents a silent hang before first byte.
var remoteHTTPClient = &http.Client{
	Transport: &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		ResponseHeaderTimeout: 15 * time.Second,
		IdleConnTimeout:       90 * time.Second,
	},
}

// ServeHTTPRange feeds the existing encrypted P2P sender from a temporary HTTP Range source.
// It opens no persistent full-file download and therefore remains compatible with sparse caches.
func ServeHTTPRange(
	ctx context.Context,
	sig Signaler,
	peer string,
	source HTTPRangeSource,
	ice ICEConfig,
	printf func(string, ...any),
	log *debug.Logger,
	reportRate func(float64),
	fair *UploadFairness,
	idx int,
) error {
	if source.URL == "" || source.Size <= 0 {
		return fmt.Errorf("invalid remote range source")
	}
	src := serveSource{
		open: func() (io.ReadSeeker, int64, func() error, error) {
			r := &httpRangeReader{ctx: ctx, source: source, client: remoteHTTPClient}
			return r, source.Size, func() error { return nil }, nil
		},
		fair: fair, fileIdx: idx,
	}
	return serve(ctx, sig, peer, src, ice, printf, log, reportRate)
}

type httpRangeReader struct {
	ctx    context.Context
	source HTTPRangeSource
	client *http.Client
	pos    int64
}

func (r *httpRangeReader) Seek(offset int64, whence int) (int64, error) {
	var next int64
	switch whence {
	case io.SeekStart:
		next = offset
	case io.SeekCurrent:
		next = r.pos + offset
	case io.SeekEnd:
		next = r.source.Size + offset
	default:
		return r.pos, fmt.Errorf("invalid seek mode")
	}
	if next < 0 || next > r.source.Size {
		return r.pos, fmt.Errorf("remote seek out of range")
	}
	r.pos = next
	return r.pos, nil
}

func (r *httpRangeReader) Read(p []byte) (int, error) {
	if len(p) == 0 {
		return 0, nil
	}
	if r.pos >= r.source.Size {
		return 0, io.EOF
	}
	n, err := r.readOnce(p)
	if err == nil || r.source.Refresh == nil {
		return n, err
	}
	refreshed, refreshErr := r.source.Refresh(r.ctx)
	if refreshErr != nil || refreshed.URL == "" || refreshed.Size != r.source.Size {
		return n, err
	}
	refreshed.Refresh = r.source.Refresh
	r.source = refreshed
	return r.readOnce(p)
}

func (r *httpRangeReader) readOnce(p []byte) (int, error) {
	end := min(r.source.Size-1, r.pos+int64(len(p))-1)
	req, err := http.NewRequestWithContext(r.ctx, http.MethodGet, r.source.URL, nil)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Range", "bytes="+strconv.FormatInt(r.pos, 10)+"-"+strconv.FormatInt(end, 10))
	req.Header.Set("Accept-Encoding", "identity")
	for name, value := range r.source.Headers {
		if strings.EqualFold(name, "Range") || strings.EqualFold(name, "Host") {
			continue
		}
		req.Header.Set(name, value)
	}
	resp, err := r.client.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusPartialContent {
		return 0, fmt.Errorf("remote source rejected range request with status %d", resp.StatusCode)
	}
	want := int(end-r.pos) + 1
	n, err := io.ReadFull(resp.Body, p[:want])
	r.pos += int64(n)
	if err == io.ErrUnexpectedEOF && n > 0 {
		err = nil
	}
	return n, err
}
