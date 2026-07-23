package transfer

import (
	"fmt"
	"testing"
	"time"

	"github.com/pion/logging"
	"github.com/pion/transport/v4/vnet"
	"github.com/pion/webrtc/v4"
)

// vnetPeerPair creates two real *webrtc.PeerConnections wired through a
// simulated WAN with the given one-way propagation delay (so RTT is
// roughly 2x this value), using pion's own vnet package — the same
// mechanism pion/webrtc's own test suite uses for deterministic
// network-condition testing. This lets us measure the effect of RTT on
// the data-channel backpressure window without needing OS-level netem
// (unavailable on macOS) or a real long-haul network link.
func vnetPeerPair(t *testing.B, oneWayDelay time.Duration) (a, b *webrtc.PeerConnection, closeFn func()) {
	t.Helper()
	wan, err := vnet.NewRouter(&vnet.RouterConfig{
		CIDR:          "1.2.3.0/24",
		MinDelay:      oneWayDelay,
		LoggerFactory: logging.NewDefaultLoggerFactory(),
	})
	if err != nil {
		t.Fatal(err)
	}

	netA, err := vnet.NewNet(&vnet.NetConfig{StaticIPs: []string{"1.2.3.4"}})
	if err != nil {
		t.Fatal(err)
	}
	if err := wan.AddNet(netA); err != nil {
		t.Fatal(err)
	}
	netB, err := vnet.NewNet(&vnet.NetConfig{StaticIPs: []string{"1.2.3.5"}})
	if err != nil {
		t.Fatal(err)
	}
	if err := wan.AddNet(netB); err != nil {
		t.Fatal(err)
	}
	if err := wan.Start(); err != nil {
		t.Fatal(err)
	}

	seA := webrtc.SettingEngine{}
	seA.SetNet(netA)
	seB := webrtc.SettingEngine{}
	seB.SetNet(netB)

	pcA, err := webrtc.NewAPI(webrtc.WithSettingEngine(seA)).NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatal(err)
	}
	pcB, err := webrtc.NewAPI(webrtc.WithSettingEngine(seB)).NewPeerConnection(webrtc.Configuration{})
	if err != nil {
		t.Fatal(err)
	}
	return pcA, pcB, func() {
		pcA.Close()
		pcB.Close()
		wan.Stop() //nolint:errcheck
	}
}

// negotiate performs a direct in-process offer/answer exchange (no
// signaling transport needed — this is a unit-level experiment, not an
// end-to-end junto session) and waits for both ends' ICE gathering to
// complete, mirroring junto's own non-trickle awaitGathering pattern.
func negotiate(t *testing.B, offerer, answerer *webrtc.PeerConnection) {
	t.Helper()
	offer, err := offerer.CreateOffer(nil)
	if err != nil {
		t.Fatal(err)
	}
	offerGatherComplete := webrtc.GatheringCompletePromise(offerer)
	if err := offerer.SetLocalDescription(offer); err != nil {
		t.Fatal(err)
	}
	<-offerGatherComplete

	if err := answerer.SetRemoteDescription(*offerer.LocalDescription()); err != nil {
		t.Fatal(err)
	}
	answer, err := answerer.CreateAnswer(nil)
	if err != nil {
		t.Fatal(err)
	}
	answerGatherComplete := webrtc.GatheringCompletePromise(answerer)
	if err := answerer.SetLocalDescription(answer); err != nil {
		t.Fatal(err)
	}
	<-answerGatherComplete

	if err := offerer.SetRemoteDescription(*answerer.LocalDescription()); err != nil {
		t.Fatal(err)
	}
}

// runBufferedMaxTrial streams size bytes from pcA to pcB over a single
// data channel, honoring backpressure exactly like sender.streamRange
// does (pause once BufferedAmount exceeds bufferedMax, resume once it
// drops below bufferedLow), and returns the elapsed time. This isolates
// the one variable under test (does raising the backpressure window
// unlock more throughput at a given simulated RTT) from junto's own
// control-frame protocol, which is irrelevant to this specific question.
func runBufferedMaxTrial(t *testing.B, pcA, pcB *webrtc.PeerConnection, size int64, low, maxBuf uint64) time.Duration {
	t.Helper()
	done := make(chan struct{})
	var received int64

	pcB.OnDataChannel(func(dc *webrtc.DataChannel) {
		dc.OnMessage(func(msg webrtc.DataChannelMessage) {
			received += int64(len(msg.Data))
			if received >= size {
				close(done)
			}
		})
	})

	dc, err := pcA.CreateDataChannel("bench", nil)
	if err != nil {
		t.Fatal(err)
	}
	opened := make(chan struct{})
	dc.OnOpen(func() { close(opened) })

	lowSignal := make(chan struct{}, 1)
	dc.SetBufferedAmountLowThreshold(low)
	dc.OnBufferedAmountLow(func() {
		select {
		case lowSignal <- struct{}{}:
		default:
		}
	})

	negotiate(t, pcA, pcB)
	<-opened

	buf := make([]byte, 16*1024)
	start := time.Now()
	var sent int64
	for sent < size {
		toSend := int64(len(buf))
		if size-sent < toSend {
			toSend = size - sent
		}
		if err := dc.Send(buf[:toSend]); err != nil {
			t.Fatal(err)
		}
		sent += toSend
		for dc.BufferedAmount() > maxBuf {
			<-lowSignal
		}
	}
	<-done
	return time.Since(start)
}

// BenchmarkBufferedMaxUnderRTT is the other half of the roadmap's
// "throughput tuning pass": does BDP-scaling bufferedMax (currently a
// fixed 8 MiB) from the measured RTT unlock more throughput on
// high-latency links? Simulated over a real pion vnet network (the same
// mechanism pion/webrtc's own test suite uses for deterministic
// network-condition testing — a virtual router with a configurable
// propagation delay), since OS-level latency injection (netem) isn't
// available on macOS and a real long-haul link isn't reproducible in CI.
//
// Naive bandwidth-delay-product theory says a fixed window W should cap
// throughput at W/RTT, predicting a large headroom increase from 8 MiB to
// 128 MiB at high RTT. Measured result: no such effect. At every RTT
// tested (20/150/300ms), 8 MiB, 32 MiB, and 128 MiB windows produce
// statistically identical throughput — flat within noise. At 300ms RTT
// throughput lands around 2.9 MB/s (~23 Mbps) regardless of window size,
// consistent with pion's underlying SCTP congestion control (its own
// RTT-dependent flow control, analogous to TCP's) being the actual
// ceiling — a layer below where bufferedMax operates. Raising the
// application-level buffer just queues more data waiting for SCTP to be
// *able* to send it; it doesn't make SCTP send faster. Conclusion:
// bufferedMax/bufferedLow stay as they are — BDP-scaling them would add
// real complexity (measuring RTT via GetStats, wiring a dynamic value
// through sender) for zero measured benefit.
func BenchmarkBufferedMaxUnderRTT(b *testing.B) {
	const size = 64 << 20 // 64 MiB — enough to reach steady state past the initial congestion ramp-up
	rtts := []time.Duration{20 * time.Millisecond, 150 * time.Millisecond, 300 * time.Millisecond}
	windows := []uint64{bufferedMax, 32 << 20, 128 << 20}

	for _, rtt := range rtts {
		for _, w := range windows {
			name := fmt.Sprintf("rtt=%v/window=%dMiB", rtt, w>>20)
			b.Run(name, func(b *testing.B) {
				pcA, pcB, closeFn := vnetPeerPair(b, rtt/2) // one-way delay; RTT ~= 2x
				defer closeFn()
				elapsed := runBufferedMaxTrial(b, pcA, pcB, size, bufferedLow, w)
				mbps := float64(size) / elapsed.Seconds() / (1 << 20)
				b.ReportMetric(mbps, "MB/s")
				b.Logf("rtt=%v window=%dMiB elapsed=%v throughput=%.2f MB/s (%.1f Mbps)", rtt, w>>20, elapsed, mbps, mbps*8)
			})
		}
	}
}
