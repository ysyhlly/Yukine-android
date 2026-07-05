# tmp music scheduling analysis

Date: 2026-06-09

Scope: static inspection of `tmp/LX Music..apk` and NetEase Cloud Music (`tmp/网易云音乐..apk`), then mapping useful playback scheduling ideas into ECHO Next Android.

## Evidence

`LX Music..apk`

- Package: `cn.toside.music.mobile`, version `1.8.2`.
- Manifest contains `android.permission.FOREGROUND_SERVICE`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, storage permissions, and media button integration.
- Manifest declares `com.guichaguri.trackplayer.service.MusicService` and `androidx.media.session.MediaButtonReceiver`.
- APK contents include React Native/Hermes libraries and `assets/index.android.bundle`.
- DEX/bundle strings include `TrackPlayer`, `MusicService`, `PlaybackService`, `ExoPlayer`, `LoadControl`, `SimpleCache`, `OkHttp`, `AudioFocus`, `Download`, `preload`, and `queue`.

Interpretation: LX Music delegates Android playback scheduling to the React Native TrackPlayer stack, which is ExoPlayer-backed. Its robust bits are foreground service ownership, media-button routing, wake lock capability, ExoPlayer buffering/cache primitives, and queue/preload operations exposed to JS.

NetEase Cloud Music (`网易云音乐..apk`)

- Package: `com.netease.cloudmusic`, version `9.5.20`.
- Manifest contains `FOREGROUND_SERVICE`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `READ_MEDIA_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`, and related media/device permissions.
- APK contains Cronet, FFmpeg, and many native audio/player libraries: `libcronet.72.0.3626.122.so`, `libffmpeg.so`, `libaudioplayer.so`, `libncmaudioplayer.so`, `libncmusbaudio.so`, `libnecmMediaPlayer.so`, `libnecmFlacDec.so`, `libnecmMP3Dec.so`, `libAudioHook.so`, and `libNMCommonCache.so`.
- DEX/native strings include `AudioTrack`, `AudioFocus`, `MediaPlayer`, `Cronet`, `OkHttp`, `CacheDataSource`, `SimpleCache`, `Download`, `preload`, `buffer`, `quality`, `bitrate`, `ThreadPool`, `Executor`, `Worker`, `latency`, and `underrun`.
- Assets include first-screen/cache strategy files such as `default_cache_strategy_schema.json`, `music_first_cache.json`, `mix_search_first_cache.json`, and audio quality modules.

Interpretation: NetEase uses a heavier native-first stack: network acceleration through Cronet, native decoder/player libraries, explicit cache strategy assets, bitrate/quality selection, preload, and underrun/latency-aware player code. This is more than ExoPlayer defaults; it treats network, decoding, and cache as separate scheduling layers.

## Transferable scheduling ideas for ECHO Next

1. Playback service owns the timeline.
   ECHO Next already uses `EchoPlaybackService` + `MediaSessionService`; this matches both APKs' foreground/media-button model.

2. Quality is a scheduling input, not just a UI option.
   NetEase evidence shows quality/bitrate is part of runtime scheduling. ECHO Next now stores a streaming quality preference and uses it for playback URL resolution.

3. Resolve, preflight, then play.
   NetEase's cache/network stack and LX's ExoPlayer-backed URL flow both point to separating URL resolution from playback. ECHO Next now does CDN preflight after resolving playback sources.

4. Treat buffering as a recovery signal.
   NetEase has underrun/latency/player-native evidence; ECHO Next now listens for `STATE_BUFFERING` and can downgrade + re-resolve a streaming track while preserving position.

5. Preload the next queue item, but keep it bounded.
   LX exposes queue/preload semantics and NetEase has preload/cache libraries. ECHO Next now resolves the next streaming item near playback and precaches a small HTTP range.

6. Avoid runaway background work.
   NetEase exposes thread-pool/worker evidence rather than ad-hoc per-task threads. ECHO Next precache now uses a single service-owned executor and skips duplicate precache keys.

## ECHO Next changes already mapped

- Streaming quality preference: Auto, Standard, High, Lossless, Hi-Res.
- Playback resolution uses the selected quality instead of fixed Lossless.
- CDN preflight checks stream URL reachability before creating the playback track.
- Buffering recovery downgrades by one active quality tier and re-resolves the URL.
- Next-track streaming pre-resolution triggers a bounded 128 KiB HTTP range precache.
- Precache scheduling moved from ad-hoc thread creation to a single daemon executor.
- Duplicate precache of the same cache key is skipped.
- Auto-quality recovery now downgrades from the active network-selected quality, avoiding weak-network upshifts.

## Remaining opportunities

- Replace the manual HTTP range precache with Media3 `CacheWriter` so bytes definitely land in `SimpleCache`.
- Add structured playback diagnostics: buffering count, recovered quality, preflight latency/status, precache bytes, and CDN host.
- Add network-adaptive cooldowns: shorter retry on Wi-Fi, longer retry on slow cellular.
- Add a small priority scheduler: current playback recovery > current URL resolve > next-track resolve > next-track precache.
- Consider Cronet-backed HTTP data source if startup/rebuffer traces still show network stack overhead.
