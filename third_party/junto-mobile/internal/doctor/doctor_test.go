package doctor

import (
	"context"
	"net"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/swayam-mishra/junto/internal/transfer"
)

func TestCheckMpvMissing(t *testing.T) {
	r := checkMpv(context.Background(), "definitely-not-a-real-binary-xyz")
	if r.ok {
		t.Fatal("a missing binary should fail the check")
	}
	if r.fix == "" {
		t.Error("a failed check should carry a fix")
	}
	if !strings.Contains(r.fix, "install") {
		t.Errorf("fix should tell the user to install mpv, got %q", r.fix)
	}
}

func TestCheckRelaysUnreachable(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	// Nothing listens on port 1; the dial fails fast and locally.
	results := checkRelays(ctx, []string{"ws://127.0.0.1:1"}, func(result) {})
	if len(results) != 1 {
		t.Fatalf("want 1 result, got %d", len(results))
	}
	if results[0].ok {
		t.Error("an unreachable relay should fail the check")
	}
	if results[0].fix == "" {
		t.Error("a failed relay check should carry a fix")
	}
}

func TestCheckDisk(t *testing.T) {
	r := checkDisk(t.TempDir())
	if !r.ok {
		t.Errorf("disk check on a real directory should pass, got %+v", r)
	}
	if !strings.Contains(r.detail, "free") {
		t.Errorf("detail should report free space, got %q", r.detail)
	}

	if r := checkDisk("/definitely/not/a/real/path"); r.ok {
		t.Error("disk check on a missing directory should fail")
	}
}

// gatherCandidateTypes with no ICE servers still yields host candidates
// (the local interfaces) and no srflx/relay — the classification the
// STUN/TURN checks depend on.
func TestGatherNoServers(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	types, err := gatherCandidateTypes(ctx, nil)
	if err != nil {
		t.Fatalf("gather: %v", err)
	}
	if len(types) == 0 {
		t.Fatal("expected at least host candidates")
	}
}

func TestRunPrintsChecklist(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	var lines []string
	ok := Run(ctx, Options{
		MpvPath: "definitely-not-a-real-binary-xyz",
		Relays:  []string{"ws://127.0.0.1:1"},
		OutDir:  t.TempDir(),
	}, func(f string, a ...any) {
		lines = append(lines, strings.TrimSpace(strings.ReplaceAll(f, "%s", "")))
		_ = a
	})
	if ok {
		t.Error("Run should report failure when checks fail")
	}
	if len(lines) == 0 {
		t.Error("Run should print results")
	}
}

// TestCheckRelaysStreamsPerRelay is the regression test for the
// blank-screen bug: Run used to buffer every check's result and print
// the whole checklist only after the last one finished, which for a
// relay fan-out or an ICE gathering pass could mean nothing on screen
// for the better part of a minute. checkRelays must invoke onResult
// once per relay as each dial completes (not just once for the whole
// batch at the end), with content matching what it ultimately returns.
func TestCheckRelaysStreamsPerRelay(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	relays := []string{"ws://127.0.0.1:1", "ws://127.0.0.1:2"}
	var mu sync.Mutex
	var streamed []result
	got := checkRelays(ctx, relays, func(r result) {
		mu.Lock()
		streamed = append(streamed, r)
		mu.Unlock()
	})

	if len(streamed) != len(relays) {
		t.Fatalf("onResult fired %d times, want once per relay (%d)", len(streamed), len(relays))
	}
	seen := make(map[string]bool, len(got))
	for _, r := range got {
		seen[r.detail] = true
	}
	for _, r := range streamed {
		if !seen[r.detail] {
			t.Errorf("onResult delivered a result not present in the final returned slice: %+v", r)
		}
	}
}

// TestCheckICEStreamsEachResult is checkRelays' sequential counterpart:
// checkICE's gathering passes run one after another (not concurrently),
// so onResult must fire for each one as it finishes, in the same order
// as the returned slice.
func TestCheckICEStreamsEachResult(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	// No relay configured on either the -turn or built-in path takes the
	// fast, network-free branches (empty direct config still runs one
	// real — but serverless, so quick — gathering pass for host
	// candidates, matching TestGatherNoServers).
	var streamed []result
	got := checkICE(ctx, transfer.ICEConfig{}, transfer.ICEConfig{}, transfer.ICEConfig{}, func(r result) {
		streamed = append(streamed, r)
	})

	if len(got) < 2 {
		t.Fatalf("expected at least 2 results (direct STUN + built-in unavailable), got %d: %+v", len(got), got)
	}
	if !reflect.DeepEqual(streamed, got) {
		t.Errorf("onResult should fire once per result, in order, matching the returned slice\nstreamed: %+v\ngot:      %+v", streamed, got)
	}
}

// TestCheckRelaysReportsFastRelayBeforeSlowOneFinishes is the sharper
// regression test for the blank-screen bug: matching call count/content
// (as above) still passes even if onResult were called for every relay
// only after the whole batch finishes. This test pairs a relay that
// fails instantly with one that hangs for the full relayTimeout (a local
// listener that accepts the connection and never responds) and checks
// the fast result is actually delivered while the slow one is still
// pending — not held back and delivered at the same time.
func TestCheckRelaysReportsFastRelayBeforeSlowOneFinishes(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	go func() {
		for {
			conn, err := ln.Accept()
			if err != nil {
				return // listener closed
			}
			// Accept and hold the connection open without ever completing
			// the WebSocket handshake or closing it.
			_ = conn
		}
	}()
	slowURL := "ws://" + ln.Addr().String()

	ctx, cancel := context.WithTimeout(context.Background(), relayTimeout+3*time.Second)
	defer cancel()

	start := time.Now()
	var mu sync.Mutex
	elapsed := make(map[string]time.Duration)
	checkRelays(ctx, []string{"ws://127.0.0.1:1", slowURL}, func(r result) {
		mu.Lock()
		defer mu.Unlock()
		elapsed[r.detail] = time.Since(start)
	})

	var fastElapsed, slowElapsed time.Duration
	var haveFast, haveSlow bool
	for detail, d := range elapsed {
		if strings.Contains(detail, "127.0.0.1:1") {
			fastElapsed, haveFast = d, true
		} else {
			slowElapsed, haveSlow = d, true
		}
	}
	if !haveFast || !haveSlow {
		t.Fatalf("expected a result for both relays, got %v", elapsed)
	}
	if fastElapsed > relayTimeout/2 {
		t.Errorf("fast (instantly-refused) relay result arrived after %v — onResult must fire as each relay finishes, not held until the slow one also completes", fastElapsed)
	}
	if slowElapsed < relayTimeout {
		t.Errorf("slow (hung) relay result arrived after only %v, want it to take the full relayTimeout (%v)", slowElapsed, relayTimeout)
	}
}
