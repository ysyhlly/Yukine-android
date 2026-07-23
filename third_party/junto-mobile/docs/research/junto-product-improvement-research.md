# **Strategic Architecture and Product Evolution Report for Junto**

## **Executive Summary**

The transition of a peer-to-peer (P2P), open-source media synchronization tool from a specialized command-line interface (CLI) to a mainstream, resilient consumer product requires resolving systemic challenges across the entire application stack. While the current iteration of Junto relies on a robust foundation—Nostr-based NIP-44 coordination, WebRTC data channels, and mpv inter-process communication (IPC)—it is constrained by Network Address Translation (NAT) traversal failures, synchronization race conditions, upload bandwidth bottlenecks, and high onboarding friction.  
This comprehensive analysis deconstructs the necessary architectural upgrades to materially improve Junto. The research is divided into six core domains: User Experience (UX), Transport and NAT Traversal, Swarm Distribution, Synchronization Algorithms, Media Layer Integration, and Distribution Mechanics. Concrete, prioritized recommendations are provided, emphasizing verifiable data streaming, advanced network hole-punching, deterministic state reconciliation, and streamlined installation processes.

## **User Experience (UX): Interaction Patterns for Effortless Co-Watching**

The primary barrier to entry for non-technical users in P2P co-watching applications is the anxiety surrounding connectivity state and initial configuration. Tools like Teleparty, Kosmi, and Apple SharePlay abstract the network layer entirely, presenting users with immediate visual feedback regarding peer presence and synchronization status1. Conversely, CLI-based tools inherently limit the total addressable market and exacerbate "is it working?" anxiety by relying on external application configurations.

### **Reducing First-Run Friction and Discovery**

The user experience of successful platforms relies heavily on URL-based room discovery and intuitive onboarding. Kosmi and Watch2Gether utilize virtual rooms accessible via a simple web link, removing the requirement for browser extensions or manual connection strings2. Apple SharePlay utilizes a "TouchPort" concept—an embodied sharing protocol that collapses discovery, consent, and spatial colocation into a single interaction5.  
To replicate this zero-friction discovery in a decentralized desktop application, the UX must utilize operating system protocol handlers (e.g., junto://room-id). This allows users to click a web link or chat message and instantly launch the application, automatically parsing NIP-44 encrypted Nostr room coordinates to join a session without terminal interaction.

### **Room State, Trust, and "Ready" Indicators**

Shared joy hinges on simultaneity—not just visual alignment, but the visceral punch of collective reactions6. Syncplay mitigates playback anxiety through a strict "Ready" state mechanism. Playback is intentionally inhibited until all connected peers acknowledge sufficient buffer health and media readiness7.  
Furthermore, Syncplay implements a transparent Heads-Up Display (HUD) overlay over the video player, dynamically showing which users are lagging, seeking, or buffering7. This shifts the blame from the software to network conditions, managing user expectations. When users experience buffering, transparently displaying a message such as "Waiting for Alice to buffer" eliminates the anxiety of a frozen application. The UX must also accommodate edge cases where a user has chronic connectivity issues; features like a "readiness override" allow a host to force playback despite a lagging peer, ensuring the session is not permanently stalled8.

### **Prioritized UX Recommendations**

| Recommendation | Problem Solved | Technique / Pattern | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **Protocol Handlers & Nostr URIs** | Eliminates CLI connection friction and manual coordinate copying. | Register junto:// OS protocol handlers to parse NIP-44 encrypted room coordinates directly from standard web links. | High / Open | Medium | Requires OS-level registry/plist modifications during the installation and packaging phase. |
| **Explicit "Ready" State Gate** | Prevents desynchronized starts and initial buffering anxiety. | Require all clients to broadcast a {"ready": true} state before the host can initiate playback, matching Syncplay's robust UX. | High / Syncplay-proven | Low | May frustrate users if one peer has chronic buffer issues, necessitating the implementation of a host-level "override" feature. |
| **In-Video HUD Overlay** | Solves "is it working?" opacity and visualizes network states. | Render transparent OSD (On-Screen Display) indicators via mpv IPC showing peer latency, readiness, and buffer percentage. | High / Native to mpv | Medium | OSD rendering can clutter the video canvas if not designed with intelligent auto-hide timeouts and minimal intrusion. |

## **P2P Transport and NAT Traversal**

Junto’s current reliance on raw WebRTC (via the pion library) exposes it to the inherent fragility of Session Traversal Utilities for NAT (STUN). WebRTC was designed primarily for real-time lossy media in browsers, bringing a highly complex stack: Interactive Connectivity Establishment (ICE) for NAT traversal, Datagram Transport Layer Security (DTLS) for encryption, and Stream Control Transmission Protocol (SCTP) for data channels9. When both peers are behind Symmetric NATs (which assign a different external port mapping for each unique destination) or Carrier-Grade NATs (CGNAT), direct hole-punching fails10. WebRTC traditionally falls back to Traversal Using Relays around NAT (TURN), but maintaining public TURN infrastructure introduces centralization, high latency, and significant financial cost9.

### **Evaluating libp2p vs. QUIC**

While libp2p provides robust discovery via its Kademlia-based Distributed Hash Table (DHT), its NAT traversal capabilities (DCUtR) often fail in practice, causing connections to fall back to relays frequently9. Modern decentralized systems are migrating toward QUIC (TLS 1.3 over UDP) for raw data transport. QUIC natively supports stream multiplexing, eliminates Head-of-Line blocking, and handles connection migration smoothly9.  
The Iroh project has demonstrated that QUIC, combined with simultaneous outbound connection techniques, can successfully traverse roughly 90% of real-world NAT configurations without WebRTC's heavy negotiation overhead9. Iroh utilizes Designated Encrypted Relay for Packets (DERP), a protocol originally pioneered by Tailscale. In the DERP model, peers initially communicate through a lightweight HTTPS relay. They exchange network coordinates and simultaneously send UDP datagrams to each other's public, reflective addresses. Since both send on the same 4-tuple (source IP, source port, destination IP, destination port), the respective firewalls register the outbound packets, effectively opening a bidirectional hole for direct QUIC traffic15.

### **Go-Native DERP and Tailscale Integration**

While Iroh provides superior NAT traversal, its core is written in Rust, and the official Foreign Function Interface (FFI) bindings for Go (iroh-ffi) have been deprecated due to ecosystem fragmentation concerns17. Relying on unmaintained FFI bindings introduces severe technical debt to Junto's Go core.  
Instead, the Tailscale tsnet library offers a native Go path forward. tsnet embeds a fully self-contained Tailscale node inside a Go process using a userspace TCP/IP stack (gVisor)19. This allows the application to join a tailnet and accept or dial connections without running a separate daemon or requiring root privileges20. Tailscale's open-source DERP server/client implementations provide the exact same resilient hole-punching and encrypted fallback relay mechanism without sacrificing Junto's pure-Go architecture22.

### **Prioritized Transport Recommendations**

| Recommendation | Problem Solved | Technique / Library | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **QUIC \+ DERP Fallback** | Eliminates Symmetric NAT connection failures entirely. | Utilize Tailscale's open-source derp client/server protocol for coordination and guaranteed encrypted fallback when direct connections fail. | High / BSD-3 | Large | Replaces the entire WebRTC data channel stack; requires hosting lightweight DERP relays instead of maintaining TURN infrastructure. |
| **Simultaneous Outbound Hole-Punching** | Increases direct P2P success rates over CGNAT environments. | Implement STUN-like UDP packet firing based on Tailscale's NAT traversal specifications, coordinated initially over Nostr or DERP. | High / Production-proven | Medium | Network timing can be sensitive; fallback to DERP is still strictly required for the \~10% of networks that block hole-punching. |

## **Swarm Distribution and Verified Streaming**

Currently, Junto relies on a single-host upload model. If the host possesses 10 Mbps of upload bandwidth and the media file requires 5 Mbps, the host can only support two peers before the stream throttles and buffering ensues. To scale beyond a 1:1 architectural limitation and distribute the network load, Junto must implement swarm distribution, allowing peers to fetch byte ranges from any other peer who has them.

### **Adapting BitTorrent Piece Selection for Linear Media**

Traditional BitTorrent utilizes a "rarest-first" algorithm to ensure maximum piece diversity and swarm health26. While excellent for static file distribution, it is fundamentally incompatible with linear video streaming, which requires sequential bytes immediately ahead of the playhead.  
The optimal approach is a hybrid algorithm, comprehensively documented in the BitTorrent Streaming (BiToS) protocol and BitTorrent-Assisted Streaming System (BASS)26. BiToS modifies the standard piece picker by dividing the piece selection into disjoint sets: the High Priority (HP) set and the Remaining Pieces (RP) set26. Pieces immediately required for the playback buffer (e.g., the next 10 seconds of video) are assigned to the HP set, while the remaining non-downloaded pieces are placed in the RP set. The algorithm requests from the HP set with a high probability ![][image1], and from the RP set with probability ![][image2] (typically employing a lowest-replicated rarity search)28. This ensures playback does not stall for the user, while still seeding the swarm with diverse chunks to relieve the host's upload burden.

### **Content Addressing and Verified Streaming via BLAKE3**

If peers are instructed to fetch arbitrary byte ranges from untrusted secondary peers (other watchers in the room), the data must be cryptographically verified. Without verification, a malicious actor could inject corrupted bytes, crashing the video decoder or introducing exploits. Traditional hashes like SHA-256 require hashing an entire file sequentially before verification, which precludes streaming30.  
The solution is Bao, a protocol built on the BLAKE3 cryptographic hash function30. BLAKE3 organizes 1 KiB chunks into a Merkle tree, allowing for an unbounded degree of parallelism and streaming verification32. The Bao encoding format interleaves the original media bytes with the tree's parent nodes34. When a peer joins a Junto room, the host transmits the 32-byte BLAKE3 root hash over the encrypted Nostr channel. As the new peer streams 1 KiB chunks from any other peer in the swarm, the native Go lukechampine.com/blake3/bao package validates the chunk mathematically against the root hash in real-time30. If a chunk fails verification, the connection is dropped, and the piece is requested from another peer, guaranteeing absolute data integrity without sacrificing streaming speed.

### **Prioritized Swarm Recommendations**

| Recommendation | Problem Solved | Technique / Library | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **BLAKE3 Verified Streaming (Bao)** | Secures multi-peer byte-range fetching without full-file hashing. | Use lukechampine.com/blake3/bao to stream and verify 1 KiB chunks against a known 32-byte root hash in real-time. | High / MIT | Medium | Media files must be indexed/encoded into the Bao format locally before streaming begins, causing a slight start delay for the host. |
| **BiToS Piece Selection** | Allows swarm scaling without buffering the playhead. | Implement a probabilistic High Priority (HP) queue for sequential chunks and a Remaining Pieces (RP) queue for rare chunks. | Academic / Custom | Large | Highly complex to tune the probability ![][image1] perfectly against fluctuating swarm bandwidth and individual peer latency. |

## **Synchronization Algorithms: Managing Drift, Skew, and Race Conditions**

Junto currently relies on a simplistic Last-Writer-Wins (LWW) model combined with a 2-second heartbeat and rate nudging. While functional in low-latency environments, this approach breaks down under unpredictable network jitter and leads to race conditions when multiple users manipulate the timeline simultaneously.

### **Clock Skew and RTT-Weighted Timestamping**

Media playback is governed by hardware audio oscillators. Consumer-grade oscillators naturally drift by 20 to 100 parts per million (ppm)36. Over a 10-minute session, this physical clock deviation can introduce upwards of 30ms of desynchronization independent of any network latency36.  
To counter this, the synchronization protocol must calculate Round-Trip Time (RTT) continuously. Syncplay achieves this by broadcasting lightweight JSON "ping" objects containing clientRtt and floating-point timestamps representing seconds since the epoch38. The server uses this to establish a standard Network Time Protocol (NTP) style offset: ![][image3]37. By accurately establishing this offset, the application calculates the true relative playhead position, rather than the apparent position which is inherently delayed by packet transit time.

### **Overcoming Seek Race Conditions**

In a leaderless LWW system, two users seeking simultaneously will create an infinite loop of overlapping state updates, resulting in severe rubber-banding of the playhead. Syncplay solves this elegantly via an ignoringOnTheFly mechanism38. When a user initiates a seek, their client broadcasts a state message containing an integer flag (e.g., {"ignoringOnTheFly": {"client": 1}}). This acts as a distributed lock. The client will aggressively ignore all incoming position updates from the network until the other peers acknowledge receipt of this exact integer flag38. This deterministic state reconciliation ensures that forced state changes do not cascade into infinite feedback loops.

### **Jitter Buffers and Rate Nudging**

When drift exceeds human perception thresholds (roughly 50ms), a hard seek causes jarring audio artifacts. Instead, dynamic rate adjustment (nudging) must be employed. Modern approaches, such as the delay compensation algorithms seen in highly synchronous multiplayer systems (like Overwatch), calculate a Proportional-Integral-Derivative (PID) correction factor39. If a client is 200ms behind, the playback rate is subtly increased to 1.05x until the delta approaches zero, at which point it returns to 1.0x7. To prevent "phantom rewinds"7, the algorithm must establish a maximum permissible desynchronization window; if network jitter places a client outside this window, they are forced to pause until they buffer, rather than dragging the entire room backward.

### **Prioritized Sync Recommendations**

| Recommendation | Problem Solved | Technique / Pattern | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **ignoringOnTheFly Distributed Locks** | Prevents LWW rubber-banding during simultaneous user actions. | Append a sequence integer to seek commands; ignore state overwrites until the sequence is globally acknowledged by the swarm. | High / Syncplay | Medium | Requires rewriting the LWW state machine to support transactional network locks and state tracking. |
| **RTT-Weighted Timestamping** | Fixes baseline inaccuracy caused by unpredictable network latency. | Append NTP-style latency calculations to all 2s heartbeats; adjust received playhead positions by ![][image4] RTT. | High / Academic | Medium | Requires strict adherence to UTC epoch timestamps across disparate operating systems and hardware clocks. |
| **Deterministic Rate Nudging** | Smooths out oscillator drift without jarring visual or auditory skips. | Temporarily set mpv playback rate to 0.95x or 1.05x when drift is between 50ms and 500ms, natively utilizing mpv's audio pitch correction. | High / Native mpv | Low | Audio pitch correction algorithms inside mpv must be explicitly active to prevent distortion during rate changes. |

## **Media Layer Integration: Embedded Video GUI**

The current IPC-based control of an external mpv instance restricts the audience to power users and breaks the immersion of a unified application. Moving to a dedicated Graphical User Interface (GUI) with an embedded video player is critical for product maturity and market viability.

### **Evaluating libmpv and Rendering Contexts**

The industry standard for embedding the mpv engine is the libmpv C API. libmpv provides an mpv\_render\_context which allows the host application to map video frames directly onto an OpenGL, Vulkan, or Direct3D surface40. This is the approach utilized by sophisticated media players like IINA on macOS and various Jellyfin wrappers42.  
However, integrating libmpv directly into a Go-based GUI framework presents distinct challenges. Frameworks like Fyne are strictly designed to compile without complex CGO dependencies and lack native, performant video rendering widgets44. Attempting to bind mpv\_render\_context\_render to a Fyne OpenGL canvas requires significant low-level hacking, restricts cross-platform compilation, and introduces severe performance penalties without hardware-accelerated decoding44. Furthermore, statically linking video decoders introduces potential LGPL licensing contamination, forcing the entire application to inherit strict GPL constraints44.

### **Architectural Divergence: Flutter vs. Webview vs. CGO**

If Junto is to maintain its Go core for transport and networking, the architecture must bifurcate the daemon from the presentation layer to achieve a polished embedded GUI:

1. **Flutter with media\_kit**: The media\_kit package for Flutter provides a flawless, production-ready implementation of libmpv across Windows, macOS, and Linux46. The package leverages C APIs for rendering hardware-accelerated video output using OpenGL and Direct3D47. The Go core could be compiled as a background binary or C-shared library, communicating with a beautiful Flutter frontend via local gRPC or sockets.  
2. **Webview (Wails/Tauri)**: The Go core serves a local HTTP byte-stream to a web frontend. While easier to build, browser-based media elements suffer from strict codec limitations (e.g., lack of native MKV/HEVC support without real-time transcoding), fundamentally defeating Junto's local-file superiority.  
3. **Go native (Gio/Fyne \+ CGO)**: Writing custom CGO bindings for libmpv. This maintains a single binary but vastly complicates the build pipeline and risks LGPL licensing contamination44.

### **Prioritized Media Layer Recommendations**

| Recommendation | Problem Solved | Technique / Library | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **Decoupled Flutter GUI \+ media\_kit** | Provides a modern, embedded media player without CGO nightmares in Go. | Run the Go P2P engine as a daemon; build the UI in Flutter using the highly mature media\_kit libmpv wrapper. | High / MIT | Large | Introduces a second language (Dart) to the repository; IPC or socket communication between Go and Flutter is required. |
| **libmpv via CGO (If strictly staying in Go)** | Embeds video natively in a Go UI framework. | Bind mpv\_render\_context\_render to an OpenGL context in Go (e.g., using Gio). | Medium / LGPL | Large | Compilation becomes incredibly complex; breaks seamless cross-compilation; LGPL licensing constraints apply. |

## **Distribution, Onboarding, and Auto-Updating**

Distributing a P2P binary to consumer operating systems introduces aggressive security interventions. On macOS, unsigned binaries are blocked by Gatekeeper, presenting severe installation friction that instantly deters non-technical users.

### **Code Signing and Notarization**

To bypass macOS Gatekeeper, the application must be signed with an Apple Developer ID Application certificate and notarized via Apple's servers48. Because building on macOS runners can be expensive and slow in CI/CD pipelines, cross-platform tools like quill or gon can be integrated directly into a goreleaser GitHub Action workflow49. By storing the .p12 certificate and App Store Connect .p8 key as base64-encoded GitHub Secrets, the CI pipeline can cryptographically sign and notarize the Darwin binaries from a standard Linux runner, attaching the full certificate chain automatically50.

### **Cryptographic Auto-Updating**

A product's lifecycle relies on silent, robust updates to push bug fixes and new features. Standard binary replacement on operating systems (particularly Windows) is prone to file-locking errors51. The go-selfupdate library provides a sophisticated mechanism for downloading a new binary, verifying its authenticity, and performing an atomic file swap51.  
Because P2P applications are prime targets for supply-chain attacks, simple TLS transit is insufficient for updates. go-selfupdate integrates with minisign, a lightweight cryptographic tool utilizing Ed25519 public-key signatures54. The developer signs the release binary offline with a private key. The corresponding public key is hardcoded into the Junto binary. Upon detecting an update, Junto downloads the binary, verifies the Ed25519 signature locally, and atomically swaps the executable, ensuring absolute cryptographic integrity of the upgrade path51.

### **Prioritized Distribution Recommendations**

| Recommendation | Problem Solved | Technique / Library | Maturity & License | Integration Effort | Tradeoffs |
| :---- | :---- | :---- | :---- | :---- | :---- |
| **Automated macOS Notarization** | Resolves Gatekeeper installation blocks for Mac users. | Use goreleaser combined with quill or gon in GitHub Actions for cross-platform notarization. | High / Open | Medium | Requires an active $99/yr Apple Developer account and careful security management of CI secrets. |
| **Self-Updating via Minisign** | Enables secure, seamless iteration of the product. | Implement go-selfupdate combined with minisign Ed25519 signature verification for atomic binary upgrades. | High / MIT | Low | If the private signature key is lost, the developer permanently loses the ability to push auto-updates to existing users. |

## **Top 3 Highest Leverage Moves**

To transition Junto from a niche CLI tool to a polished, massively scalable consumer product, development efforts should be strictly triaged. The highest Return on Investment (ROI) architecture moves are:

1. **Migrate to a Decoupled GUI using Flutter and media\_kit**: The CLI and external IPC window are the absolute maximum points of friction. Encapsulating the existing Go core as a background engine and wrapping it in a Flutter/libmpv GUI will instantly solve UI immersion, allow for transparent OSD sync indicators, and eliminate the requirement for users to install and configure mpv independently.  
2. **Implement BLAKE3/Bao Verified Swarming with BiToS**: The single-host upload bottleneck restricts the core utility of a watch party. By adopting the BiToS piece-selection algorithm over QUIC, and securing the byte-ranges with BLAKE3 Bao streaming, Junto can scale to dozens of peers effortlessly while retaining a completely trustless, server-free architecture.  
3. **Transition to QUIC with DERP Fallback**: Raw WebRTC STUN fails too frequently on symmetric NATs, resulting in silent connection failures for end-users. Embedding a Tailscale-style DERP fallback implementation guarantees that every user who joins a room will successfully connect, fundamentally resolving "is it working?" anxiety and laying the groundwork for reliable swarm distribution.

#### **Works cited**

1. Netflix Party is now Teleparty \- Chrome Web Store, [https://chromewebstore.google.com/detail/netflix-party-is-now-tele/oocalimimngaihdkbihfgmpkcpnmlaoa](https://chromewebstore.google.com/detail/netflix-party-is-now-tele/oocalimimngaihdkbihfgmpkcpnmlaoa)  
2. Watch Party Watch Videos Together Online \- Kosmi, [https://kosmi.io/watchparty/](https://kosmi.io/watchparty/)  
3. Kosmi \- Free Watch Party & Virtual Hangout Platform, [https://kosmi.io/](https://kosmi.io/)  
4. How to use Watch2Gether?, [https://community.w2g.tv/t/how-to-use-watch2gether/736](https://community.w2g.tv/t/how-to-use-watch2gether/736)  
5. Security and Privacy for Augmented Reality Systems | Request PDF \- ResearchGate, [https://www.researchgate.net/publication/262235821\_Security\_and\_Privacy\_for\_Augmented\_Reality\_Systems](https://www.researchgate.net/publication/262235821_Security_and_Privacy_for_Augmented_Reality_Systems)  
6. Anime Watch Party Platforms vs Discord: Fix Sync Now \- LifeTips, [https://lifetips.alibaba.com/tech-efficiency/anime-watch-party-platforms-vs-discord-fix-sync-now](https://lifetips.alibaba.com/tech-efficiency/anime-watch-party-platforms-vs-discord-fix-sync-now)  
7. Releases · yuroyami/syncplay-mobile \- GitHub, [https://github.com/yuroyami/syncplay-mobile/releases](https://github.com/yuroyami/syncplay-mobile/releases)  
8. Syncplay Changelog, [https://syncplay.pl/changelog/](https://syncplay.pl/changelog/)  
9. FAQ \- Iroh Docs, [https://docs.iroh.computer/about/faq](https://docs.iroh.computer/about/faq)  
10. What's the state of dcutr? · libp2p rust-libp2p · Discussion \#5910 \- GitHub, [https://github.com/libp2p/rust-libp2p/discussions/5910](https://github.com/libp2p/rust-libp2p/discussions/5910)  
11. NAT Traversal: How It Works \- DEV Community, [https://dev.to/alakkadshaw/nat-traversal-how-it-works-4dnc](https://dev.to/alakkadshaw/nat-traversal-how-it-works-4dnc)  
12. NAT Traversal: How It Works \- Medium, [https://medium.com/@jamesbordane57/nat-traversal-how-it-works-90cfe7a2ec97](https://medium.com/@jamesbordane57/nat-traversal-how-it-works-90cfe7a2ec97)  
13. Show HN: connet – A P2P reverse proxy with NAT traversal \- Hacker News, [https://news.ycombinator.com/item?id=42575841](https://news.ycombinator.com/item?id=42575841)  
14. Using QUIC \- Iroh Docs, [https://docs.iroh.computer/protocols/using-quic](https://docs.iroh.computer/protocols/using-quic)  
15. NAT Traversal \- Iroh Docs, [https://docs.iroh.computer/concepts/nat-traversal](https://docs.iroh.computer/concepts/nat-traversal)  
16. Peer-to-Peer Networking: Build a VPN Tunnel with Wintun on Windows \- Part 2, [https://www.0xmm.in/posts/peer-to-peer-windows-part2/](https://www.0xmm.in/posts/peer-to-peer-windows-part2/)  
17. Update On FFI Bindings \- Iroh, [https://www.iroh.computer/blog/ffi-updates](https://www.iroh.computer/blog/ffi-updates)  
18. n0-computer/iroh-ffi: FFI bindings for iroh \- GitHub, [https://github.com/n0-computer/iroh-ffi](https://github.com/n0-computer/iroh-ffi)  
19. tsnet · Tailscale Docs, [https://tailscale.com/docs/features/tsnet](https://tailscale.com/docs/features/tsnet)  
20. tsnet package \- tailscale.com/tsnet \- Go Packages, [https://pkg.go.dev/tailscale.com/tsnet](https://pkg.go.dev/tailscale.com/tsnet)  
21. tailscale/tsnet/tsnet.go at main \- GitHub, [https://github.com/tailscale/tailscale/blob/main/tsnet/tsnet.go](https://github.com/tailscale/tailscale/blob/main/tsnet/tsnet.go)  
22. tailscale/tailscale-rs: Rust implementation of Tailscale (preview, experimental) \- GitHub, [https://github.com/tailscale/tailscale-rs](https://github.com/tailscale/tailscale-rs)  
23. From Beginner to Advanced: Remote Networking and Internet Access with Tailscale \+ ShellCrash, [https://blog.l3zc.com/en/2025/04/tailscale-setup-recap/](https://blog.l3zc.com/en/2025/04/tailscale-setup-recap/)  
24. tailscale/tailcfg/tailcfg.go at main \- GitHub, [https://github.com/tailscale/tailscale/blob/main/tailcfg/tailcfg.go](https://github.com/tailscale/tailscale/blob/main/tailcfg/tailcfg.go)  
25. mmozeiko/derpnet: Network library in C for Windows \- GitHub, [https://github.com/mmozeiko/derpnet](https://github.com/mmozeiko/derpnet)  
26. Evaluation of Swarm Video Streaming \- Diva-Portal.org, [https://www.diva-portal.org/smash/get/diva2:835378/FULLTEXT01.pdf](https://www.diva-portal.org/smash/get/diva2:835378/FULLTEXT01.pdf)  
27. HOW BitTORRENT ACTUALLY WORKS. Distributed Systems | P2P Networking |… | by Dipankar\~c2o | Medium, [https://medium.com/@Dipankarc2o/how-bittorrent-actually-works-a9bd2f27d2f1](https://medium.com/@Dipankarc2o/how-bittorrent-actually-works-a9bd2f27d2f1)  
28. (PDF) On Piece Selection for Streaming BitTorrent \- ResearchGate, [https://www.researchgate.net/publication/30498635\_On\_Piece\_Selection\_for\_Streaming\_BitTorrent](https://www.researchgate.net/publication/30498635_On_Piece_Selection_for_Streaming_BitTorrent)  
29. Simulating Large-Scale P2P Assisted Video Streaming \- Computer Science, [http://www.cs.rpi.edu/\~chrisc/publications/lafortune-hicss-2009.pdf](http://www.cs.rpi.edu/~chrisc/publications/lafortune-hicss-2009.pdf)  
30. oconnor663/bao: an implementation of BLAKE3 verified streaming \- GitHub, [https://github.com/oconnor663/bao](https://github.com/oconnor663/bao)  
31. blake3 package \- lukechampine.com/blake3 \- Go Packages, [https://pkg.go.dev/lukechampine.com/blake3](https://pkg.go.dev/lukechampine.com/blake3)  
32. BLAKE3 \- School of Computer Science and Engineering, [http://www.cse.unsw.edu.au/\~cs4601/refs/papers/blake3.pdf](http://www.cse.unsw.edu.au/~cs4601/refs/papers/blake3.pdf)  
33. BLAKE3 \- GitHub, [https://raw.githubusercontent.com/BLAKE3-team/BLAKE3-specs/master/blake3.pdf](https://raw.githubusercontent.com/BLAKE3-team/BLAKE3-specs/master/blake3.pdf)  
34. Bao: A verified streaming tool based on BLAKE3 \- Hacker News, [https://news.ycombinator.com/item?id=22029618](https://news.ycombinator.com/item?id=22029618)  
35. blake3 package \- github.com/glycerine/blake3 \- Go Packages, [https://pkg.go.dev/github.com/glycerine/blake3](https://pkg.go.dev/github.com/glycerine/blake3)  
36. How Do You Synchronize Audio and Video in Real-Time Streams? \- GetStream.io, [https://getstream.io/blog/av-sync-webrtc-streams/](https://getstream.io/blog/av-sync-webrtc-streams/)  
37. Clock Synchronization \- Distributed Computing, [https://disco.ethz.ch/courses/hs15/distsys/lecture/chapter9.pdf](https://disco.ethz.ch/courses/hs15/distsys/lecture/chapter9.pdf)  
38. The Syncplay Protocol, [https://syncplay.pl/about/protocol/](https://syncplay.pl/about/protocol/)  
39. Overwatch Network Sync Algorithms: Deep Dive for Tech Pros \- Varidata, [https://www.varidata.com/blog-en/overwatch-network-sync-algorithms-deep-dive-for-tech-pros/](https://www.varidata.com/blog-en/overwatch-network-sync-algorithms-deep-dive-for-tech-pros/)  
40. libmpv API \- mpv, [https://mpv-player-mpv.mintlify.app/embedding/libmpv](https://mpv-player-mpv.mintlify.app/embedding/libmpv)  
41. libmpv: mpv-dev-x86\_64-20200718/include/render.h File Reference \- C Code Run, [https://www.ccoderun.ca/programming/doxygen/mpv/render\_8h.html](https://www.ccoderun.ca/programming/doxygen/mpv/render_8h.html)  
42. Plugin System | IINA \- The modern media player for macOS, [https://iina.io/plugins/](https://iina.io/plugins/)  
43. Desktop Client Discussion \- Future of MPV Shim Desktop? : r/jellyfin \- Reddit, [https://www.reddit.com/r/jellyfin/comments/ino7ob/desktop\_client\_discussion\_future\_of\_mpv\_shim/](https://www.reddit.com/r/jellyfin/comments/ino7ob/desktop_client_discussion_future_of_mpv_shim/)  
44. Multimedia support for fyne · Issue \#449 \- GitHub, [https://github.com/fyne-io/fyne/issues/449](https://github.com/fyne-io/fyne/issues/449)  
45. 在Fyne.io中集成mpv进行媒体播放 \- Aynakeya, [https://www.aynakeya.com/articles/coding/use-libmpv-in-fyne-gui-framework/](https://www.aynakeya.com/articles/coding/use-libmpv-in-fyne-gui-framework/)  
46. noelex/media\_kit: A complete video & audio library for Flutter & Dart. \- GitHub, [https://github.com/noelex/media\_kit](https://github.com/noelex/media_kit)  
47. drwankingstein/media\_kit: \[WIP\] A complete video & audio library for Flutter & Dart. \- GitHub, [https://github.com/drwankingstein/media\_kit](https://github.com/drwankingstein/media_kit)  
48. Apple Code Signing for macOS Binaries in CI/CD Pipeline · Issue \#511 \- GitHub, [https://github.com/GoogleCloudPlatform/khi/issues/511](https://github.com/GoogleCloudPlatform/khi/issues/511)  
49. GitHub \- mitchellh/gon: Sign, notarize, and package macOS CLI tools and applications written in any language. Available as both a CLI and a Go library., [https://github.com/mitchellh/gon](https://github.com/mitchellh/gon)  
50. Notarize macOS Applications \- GoReleaser, [https://goreleaser.com/customization/sign/notarize/](https://goreleaser.com/customization/sign/notarize/)  
51. update package \- github.com/creativeprojects/go-selfupdate/update \- Go Packages, [https://pkg.go.dev/github.com/creativeprojects/go-selfupdate/update](https://pkg.go.dev/github.com/creativeprojects/go-selfupdate/update)  
52. Self-updating binaries \- what is current stage and recommended practices : r/golang \- Reddit, [https://www.reddit.com/r/golang/comments/1poccfb/selfupdating\_binaries\_what\_is\_current\_stage\_and/](https://www.reddit.com/r/golang/comments/1poccfb/selfupdating_binaries_what_is_current_stage_and/)  
53. minio/selfupdate: Build self-updating Go programs \- GitHub, [https://github.com/minio/selfupdate](https://github.com/minio/selfupdate)  
54. Minisign by Frank Denis \- GitHub Pages, [https://jedisct1.github.io/minisign/](https://jedisct1.github.io/minisign/)  
55. Is it possible to live update a compiled executable ? : r/golang \- Reddit, [https://www.reddit.com/r/golang/comments/15qahg7/is\_it\_possible\_to\_live\_update\_a\_compiled/](https://www.reddit.com/r/golang/comments/15qahg7/is_it_possible_to_live_update_a_compiled/)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAoAAAAbCAYAAABFuB6DAAAAm0lEQVR4XmNgGAUDAr4C8VsgPgPEgkD8H4gvQWkWmCI3IHYGYiWoxAOYBBAcBeJ/MM4XKN3FAFGIDLqxiDH8xiL4EIsYWOA6FjGsCsOxiD1DFgB5CF1nNFSMA1nwOFTQB8pngvLD4CqgACR4C4gvQ9mgkJBCUQEFIEmQVXiBHwOm+7CCDwwQhVlALI4mhwJcGCC+DgBiRjS54QkAahspjFGixIQAAAAASUVORK5CYII=>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAC4AAAAaCAYAAADIUm6MAAABKUlEQVR4XmNgGAWjYOSBEiDORBccrGA5EP8C4v9QnIUqPTTAqMPpDQbS4QuB2AXKZgbiBUDcBJclAAbK4V+hNMj+fiDeD+WXQsUIApCibHRBGgMmIF4NZYPsP4okBxPzQBPDACBFueiCOIAuEJsQiXmgerABVSDmZoCoAdkviSoNFqtFE8MAIEV56II4gBsQ+xGJRaF68IF2BsxkEQkVU0ETxwAgRQXognQCvxkwHf4YixhWAFJUiC5IJwCyew4WMVAFiReIMEAU9qBL0AHA0vdHJLEnQPwCiY8BQDn6NQNEIShqQPRLBkgzgF6ggwHicCcoPZAxTxL4w0BkWh5sAOToeeiCgxloAvEMBojDQXQyqvTgBYZA7ArEzkDsA8QWqNKjYBSMgiEFAIykRg9B51uaAAAAAElFTkSuQmCC>

[image3]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAANEAAAAaCAYAAAApFbmYAAAEq0lEQVR4Xu2aa8hURRjHHy9pmVfSrNA0TPFSJGofrIgMgzJN9IvkBfGDt9CoFERR0OpTSiFWUih5wcQIEkUERVAMAj8oBvZBP0REWd4Sfb1i5fP3mfGdfZw9e/ac3bOzMj/48+78Z/ad/+6eOWfmzCGKRCKRSCQSiUQi9zkPsYZrM3C6skZoMxJpBNdZ/xs1A8NY/5LkvajqCuEq64g2A6eZMuOH7aLNJgC592ozcJB5iTaLAB2/p83AaZbMC6mYs/lrrOe1mRPkHq3NgBlCkvkBXVFvniPpuK2uCJhmynyeihlEr1NtB9E7VEzuWvIdFZz5JdY41n6SjieYcsg0U+Y3SLIh51HWm6yhJS1qy3jKP4imsL5l9WH9TQUfkBl5l7WR9SBJXkzzC+MD1mKSjnG2RBkKmWbKjFxLSbJ+ZMqDS1rUFpxQsg6ijiQ5x5hyiykfuNsiPJ4kydiPZPqG19AKt1FRoGPM25N4nPWoNlOAM9vWMtrC2sT6huRMsoG19s67KpMmcwhUux6ar40qyDOIkHGbU+5pvJcdzwem089qswDsoJ/teJONh7pCwReAjtvoCsUt1k1tNohKmT9lXWL9yVrH+oLkzLrdbVSGDqxRKTXSvCeJs5R+EC2n9G11FghXupkeH0riMN3b7xyP5wOf7zNtlkFnSlIlcAtb58M0VHsAs4CTrBusz41+JfncNeF78nfs0p/1Ckm7J0pqGkOazKh/weNVmk49zHorpXDmrwT6/EGbZcCgr/S5LDoLhINlkceHkkCfut8/PJ7mGdY5kvVpGnSmJLUz7ykHsp32eNjb8oGp/zLlof085WUC/+iCNhUYwQA/crWLtrGsT6rQx/K2RNJk1gdAe49Xb3ClRJ9pdvyPk5ys8mTMOp1Dn/oqDe+Q8jS/kUzH/1J+vRlEkm+u8uGtUp4FdTgGtNdLeZnAP1rslH90XoPHqHWx2Ymk/SOt1Q2hUma7V2B5ypQHOF4RLKDSHB+SrDU0+D7t2qNRg+hFj/cqyYlgs6oDK81fbGrmyZwFfIe6z6eNh8eUBrJmlVaXtO9Gsjyp2V1d/HMEAPaK49KiyrgsNuSRCodKmXewfmJNJJka4CbG+pIWxbCaWn+8HqxjTp2L+33qg6Ma8gwi3NRxyzbH1+TfuLTroEmUL3NW0Od087qLKdscv5i/FtyswRrZHg84me0uaZGTNSSdYy6pL3fdSfYeXDCK0b6z8oskKTNAnd5l/4+1U3lFYG8slFsXTWXtI6lHvjwHZNZBBK6Q9P2zKR805U2m7HKCtYskM6Z8eTJnBVccXE3Q9x7j2XUcboq4nCHZanDBQPtHeXWh3LrjMhU/D64G348K70ttBoB+Lg05sdGZhTyDKC296d4TlO/7Dgnkw11XF2wk6ytWzcFIx77BDNY01tuO3id/sBDAI/D6R8ViU3shoNdyADnxZEOo+O5+hfjdWrCPpfNhSqe9uvA7SUdJOnW3dRhgHwvTNpsPr3HJDu2JBmwGYvqEO53ubVqs73CVx4H6leOHAG6fY32MjHZDGI8w4TMgM777vsYPBeTSx8M1kjVRJBKJRCKRSCQSiUQikUgkEmlqbgMpK0M3NcvkkgAAAABJRU5ErkJggg==>

[image4]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAaCAYAAACD+r1hAAAAhElEQVR4XmNgwA62ogvgAj1A7A/E/9ElCIFRDfjATiB+D8RvgPgdEP9GlR70AORRXHiAwSMGiDNWoEtgA1+R2LeB+DESHysAmewBZVtC+USDUgYSNYAUa6IL4gIXgVgHXRAXWADE4lB2MZI4VpAJxHlAnADE6UD8A0UWC0BPEm9RpekBAPeIJ/0G36lgAAAAAElFTkSuQmCC>