package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TrackShareLauncherTest {
    @Test
    fun nullTrackPublishesLocalizedNoTrackMessage() {
        val sink = RecordingStatusSink()
        val launcher = launcher(statusSink = sink)

        launcher.share(null)

        assertEquals(listOf("feedback:未选择歌曲"), sink.events)
    }

    @Test
    fun unavailableShareServicePublishesUnavailableMessageWithoutOpeningChooser() {
        val sink = RecordingStatusSink()
        val starter = RecordingActivityStarter()
        val launcher = launcher(
            operations = RecordingShareOperations(available = false),
            statusSink = sink,
            starter = starter
        )

        launcher.share(track())

        assertEquals(listOf("feedback:分享服务暂不可用"), sink.events)
        assertTrue(starter.started.isEmpty())
    }

    @Test
    fun platformCardUsesNativeShareWhenAvailable() {
        val sink = RecordingStatusSink()
        val starter = RecordingActivityStarter()
        val operations = RecordingShareOperations(nativeResult = true)
        val launcher = launcher(
            style = TrackShareStyle.PLATFORM_CARD,
            operations = operations,
            statusSink = sink,
            starter = starter
        )

        launcher.share(track())

        assertEquals(listOf("feedback:正在打开分享面板：Song", "status:已发起原生音乐卡片分享：Song"), sink.events)
        assertEquals(1, operations.nativeShares)
        assertTrue(starter.started.isEmpty())
    }

    @Test
    fun textShareOpensChooserAndPublishesOpenedStatus() {
        val sink = RecordingStatusSink()
        val starter = RecordingActivityStarter()
        val operations = RecordingShareOperations()
        val launcher = launcher(
            style = TrackShareStyle.TEXT,
            operations = operations,
            statusSink = sink,
            starter = starter
        )

        launcher.share(track())

        val chooser = starter.started.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("分享到", chooser.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(listOf("feedback:正在打开分享面板：Song", "status:已打开分享面板：Song"), sink.events)
        assertEquals(listOf(TrackShareStyle.TEXT), operations.shareIntentStyles)
    }

    @Test
    fun startActivityFailurePublishesUnableToOpenMessage() {
        val sink = RecordingStatusSink()
        val launcher = launcher(
            statusSink = sink,
            starter = TrackShareActivityStarter { throw RuntimeException("boom") }
        )

        launcher.share(track())

        assertEquals(listOf("feedback:正在打开分享面板：Song", "feedback:无法打开分享面板"), sink.events)
    }

    private fun launcher(
        style: String = TrackShareStyle.defaultValue(),
        operations: RecordingShareOperations = RecordingShareOperations(),
        statusSink: RecordingStatusSink = RecordingStatusSink(),
        starter: TrackShareActivityStarter = RecordingActivityStarter()
    ): TrackShareLauncher =
        TrackShareLauncher(
            activity = Robolectric.buildActivity(Activity::class.java).setup().get(),
            operations = operations,
            languageProvider = TrackShareLanguageProvider { AppLanguage.MODE_CHINESE },
            shareStyleProvider = TrackShareStyleProvider { style },
            statusSink = statusSink,
            activityStarter = starter
        )

    private fun track(): Track =
        Track(
            1L,
            "Song",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://cdn.example/song.mp3"),
            "streaming:netease:186016"
        )

    private class RecordingShareOperations(
        private val available: Boolean = true,
        private val nativeResult: Boolean = false
    ) : TrackShareOperations {
        var nativeShares = 0
        val shareIntentStyles = mutableListOf<String>()

        override fun isShareAvailable(): Boolean =
            available

        override fun musicSharePayload(track: Track): TrackMusicSharePayload =
            TrackMusicSharePayload(
                StreamingProviderName.NETEASE,
                "186016",
                "https://music.163.com/song?id=186016",
                """{"type":"music","data":{"type":"163","id":"186016"}}"""
            )

        override fun shareNative(activity: Activity, track: Track, payload: TrackMusicSharePayload?): Boolean {
            nativeShares += 1
            return nativeResult
        }

        override fun shareIntent(context: Context, track: Track, style: String): Intent {
            shareIntentStyles += style
            return Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, track.title)
        }
    }

    private class RecordingStatusSink : TrackShareStatusSink {
        val events = mutableListOf<String>()

        override fun showFeedback(message: String) {
            events += "feedback:$message"
        }

        override fun setStatus(message: String) {
            events += "status:$message"
        }
    }

    private class RecordingActivityStarter : TrackShareActivityStarter {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
        }
    }
}
