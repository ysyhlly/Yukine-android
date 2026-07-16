package app.yukine.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.net.Uri;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import app.yukine.model.Track;
import app.yukine.playback.diagnostics.PlaybackStreamingDiagnostics;

public class PlaybackStreamingDiagnosticsTest {
    @Test
    public void recordsBufferingRecoveryAndPrecacheEvents() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();
        Track track = new Track(
                1L,
                "Echo",
                "Tester",
                "Album",
                0L,
                Uri.EMPTY,
                "streaming:netease:track-1"
        );

        diagnostics.recordBuffering(track, 1200L);
        diagnostics.recordRecovery(track, 1200L, "high");
        diagnostics.recordPrecacheQueued(track);
        diagnostics.recordPrecacheComplete(track, 131072L);
        diagnostics.recordPrecacheSegmentComplete(track, 524288L, 262144L);

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.bufferingEvents);
        assertEquals(1, snapshot.recoveryEvents);
        assertEquals(1, snapshot.precacheAttempts);
        assertEquals(1, snapshot.precacheSuccesses);
        assertEquals(0, snapshot.precacheFailures);
        assertEquals(1, snapshot.precacheSegmentSuccesses);
        assertEquals(0, snapshot.precacheSegmentFailures);
        assertEquals(5, snapshot.recentEvents.size());
        assertEquals("precache_segment_complete", snapshot.recentEvents.get(0).type);
        assertEquals(524288L, snapshot.recentEvents.get(0).positionMs);
        assertEquals("buffering", snapshot.recentEvents.get(4).type);
    }

    @Test
    public void recordsPrepareToReadyPercentiles() {
        AtomicLong now = new AtomicLong(1000L);
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics(now::get);
        Track track = new Track(
                1L,
                "Echo",
                "Tester",
                "Album",
                0L,
                Uri.EMPTY,
                "streaming:netease:track-1"
        );

        long[] samples = {100L, 200L, 300L, 400L, 900L};
        for (long sample : samples) {
            diagnostics.recordPrepareStarted(track);
            now.addAndGet(sample);
            diagnostics.recordPlayerReady(track);
            now.incrementAndGet();
        }

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(5, snapshot.prepareToReady.sampleCount);
        assertEquals(300L, snapshot.prepareToReady.p50Ms);
        assertEquals(900L, snapshot.prepareToReady.p95Ms);
        assertEquals("first_audio_ready", snapshot.recentEvents.get(0).type);
        assertEquals(900L, snapshot.recentEvents.get(0).durationMs);
    }

    @Test
    public void recordsResolveUrlAndEndToEndPercentiles() {
        AtomicLong now = new AtomicLong(2000L);
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics(now::get);
        Track unresolved = new Track(
                8L, "Echo", "Tester", "Album", 0L, Uri.EMPTY, "streaming:netease:track-8"
        );
        Track resolved = new Track(
                8L, "Echo", "Tester", "Album", 0L,
                Uri.parse("https://audio.example/8.flac"), "streaming:netease:track-8"
        );

        diagnostics.recordResolveStarted(unresolved);
        now.addAndGet(40L);
        diagnostics.recordActiveSourceLookupCompleted(unresolved);
        now.addAndGet(160L);
        diagnostics.recordPlaybackUrlReady(resolved, "title_search");
        diagnostics.recordPrepareStarted(resolved);
        now.addAndGet(300L);
        diagnostics.recordPlayerReady(resolved);

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.resolveToUrl.sampleCount);
        assertEquals(200L, snapshot.resolveToUrl.p50Ms);
        assertEquals(1, snapshot.clickToReady.sampleCount);
        assertEquals(500L, snapshot.clickToReady.p95Ms);
        assertEquals(500L, snapshot.sourceToReady.get("cold_resolve").p95Ms);
        assertEquals("cold_resolve", snapshot.recentEvents.get(0).sourceCategory);
        assertEquals("first_audio_ready", snapshot.recentEvents.get(0).type);
    }

    @Test
    public void recordsFirstPcmOutputSeparatelyFromPlayerReady() {
        AtomicLong now = new AtomicLong(5000L);
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics(now::get);
        Track track = track(
                15L,
                "https://audio.example/15.flac",
                "streaming:netease:15"
        );

        diagnostics.recordPlaybackRequested(track);
        now.addAndGet(120L);
        diagnostics.recordPrepareStarted(track);
        now.addAndGet(180L);
        diagnostics.recordPlayerReady(track);
        now.addAndGet(70L);
        diagnostics.recordFirstAudioOutput(track);

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.prepareToFirstAudio.sampleCount);
        assertEquals(250L, snapshot.prepareToFirstAudio.p50Ms);
        assertEquals(1, snapshot.clickToFirstAudio.sampleCount);
        assertEquals(370L, snapshot.clickToFirstAudio.p95Ms);
        assertEquals(370L, snapshot.sourceToFirstAudio.get("cache_hit").p50Ms);
        assertEquals("first_pcm_output", snapshot.recentEvents.get(0).type);
    }

    @Test
    public void recordsSanitizedProviderStagePercentilesAndOutcomeCounts() {
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics();

        diagnostics.recordProviderStage(
                "url_resolve",
                "netease",
                120L,
                true,
                false,
                false,
                true,
                "",
                "url_cache",
                0
        );
        diagnostics.recordProviderStage(
                "title_search",
                "qqmusic",
                800L,
                false,
                true,
                false,
                false,
                "SOURCE_UNAVAILABLE?secret=must-not-survive",
                "title_search",
                5
        );
        diagnostics.recordProviderStage(
                "url_resolve",
                "luoxue",
                400L,
                false,
                false,
                true,
                false,
                "",
                "known_provider_id",
                0
        );

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(1, snapshot.providerStageFailures);
        assertEquals(1, snapshot.providerStageTimeouts);
        assertEquals(1, snapshot.providerStageCancellations);
        assertEquals(120L, snapshot.providerStages.get("url_resolve:netease").p50Ms);
        assertEquals(800L, snapshot.providerStages.get("title_search:qqmusic").p95Ms);
        assertEquals(0, snapshot.providerStages.getOrDefault(
                "url_resolve:luoxue",
                snapshot.prepareToReady
        ).sampleCount);
        assertFalse(snapshot.recentEvents.get(1).message.contains("?"));
        assertFalse(snapshot.recentEvents.get(1).message.contains("must-not-survive"));
    }

    @Test
    public void recordsLocalWebDavCacheAndColdResolvePercentilesSeparately() {
        AtomicLong now = new AtomicLong(3000L);
        PlaybackStreamingDiagnostics diagnostics = new PlaybackStreamingDiagnostics(now::get);
        Track local = track(11L, "content://media/audio/11", "/music/local.flac");
        Track webDav = track(12L, "https://dav.example/12.flac", "webdav:2:/12.flac");
        Track cached = track(13L, "https://audio.example/13.flac", "streaming:netease:13");
        Track cold = track(14L, "https://audio.example/14.flac", "streaming:qq:14");

        recordReadySample(diagnostics, now, local, "", 100L);
        recordReadySample(diagnostics, now, webDav, "", 200L);
        recordReadySample(diagnostics, now, cached, "url_cache", 300L);
        recordReadySample(diagnostics, now, cold, "known_provider_id", 400L);

        PlaybackStreamingDiagnostics.Snapshot snapshot = diagnostics.snapshot();
        assertEquals(100L, snapshot.sourceToReady.get("local").p50Ms);
        assertEquals(200L, snapshot.sourceToReady.get("webdav").p50Ms);
        assertEquals(300L, snapshot.sourceToReady.get("cache_hit").p50Ms);
        assertEquals(400L, snapshot.sourceToReady.get("cold_resolve").p50Ms);
    }

    private static void recordReadySample(
            PlaybackStreamingDiagnostics diagnostics,
            AtomicLong now,
            Track track,
            String resolutionPath,
            long durationMs
    ) {
        diagnostics.recordPlaybackRequested(track);
        if (!resolutionPath.isEmpty()) {
            diagnostics.recordPlaybackUrlReady(track, resolutionPath);
        }
        diagnostics.recordPrepareStarted(track);
        now.addAndGet(durationMs);
        diagnostics.recordPlayerReady(track);
        now.incrementAndGet();
    }

    private static Track track(long id, String uri, String dataPath) {
        return new Track(id, "Track " + id, "Artist", "Album", 0L, Uri.parse(uri), dataPath);
    }
}
