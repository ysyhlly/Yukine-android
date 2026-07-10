package app.yukine.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import org.junit.Assert.assertFalse
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackSessionPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.function.LongSupplier

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlaybackSessionPlayerTest {
    @Test
    fun lockScreenTransportCommandsDelegateToPlaybackOwner() {
        val delegate = RecordingDelegate()
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)

        player.play()
        player.pause()
        player.setPlayWhenReady(true)
        player.setPlayWhenReady(false)
        player.seekTo(1200L)
        player.seekTo(2, 3400L)
        player.seekToPrevious()
        player.seekToPreviousMediaItem()
        player.seekToNext()
        player.seekToNextMediaItem()
        player.setRepeatMode(Player.REPEAT_MODE_ONE)
        player.setRepeatMode(Player.REPEAT_MODE_OFF)
        player.setRepeatMode(Player.REPEAT_MODE_ALL)
        player.stop()

        assertEquals(
            listOf(
                "play",
                "pause",
                "play",
                "pause",
                "seek:1200",
                "seek:3400",
                "previous",
                "previous",
                "next",
                "next",
                "repeat:1",
                "repeat:2",
                "repeat:0",
                "stop"
            ),
            delegate.events
        )
    }

    @Test
    fun seekCommandsClampNegativePositionsBeforeDelegating() {
        val delegate = RecordingDelegate()
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)

        player.seekTo(-500L)
        player.seekTo(3, -1200L)
        player.seekTo(2500L)

        assertEquals(
            listOf(
                "seek:0",
                "seek:0",
                "seek:2500"
            ),
            delegate.events
        )
    }

    @Test
    fun lockScreenQueueNavigationAndRepeatCommandsAreAdvertised() {
        val player = PlaybackSessionPlayer(fakePlayer(), RecordingDelegate())

        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(player.isCommandAvailable(Player.COMMAND_SEEK_BACK))

        val commands = player.availableCommands
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(commands.contains(Player.COMMAND_SET_REPEAT_MODE))
        assertFalse(commands.contains(Player.COMMAND_SEEK_BACK))
    }

    @Test
    fun controllerMediaItemCommandsRouteThroughDelegateBeforeFallback() {
        val delegate = RecordingDelegate()
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)
        val mediaItem = MediaItem.fromUri("content://tracks/1")

        player.setMediaItem(mediaItem)
        player.setMediaItem(mediaItem, 99L)
        player.setMediaItem(mediaItem, true)
        player.setMediaItem(mediaItem, false)
        player.setMediaItems(mutableListOf(mediaItem))
        player.setMediaItems(mutableListOf(mediaItem), true)
        player.setMediaItems(mutableListOf(mediaItem), false)
        player.setMediaItems(mutableListOf(mediaItem), 2, 3000L)

        assertEquals(
            listOf(
                "items:1:0:${C.TIME_UNSET}",
                "items:1:0:99",
                "items:1:0:0",
                "items:1:0:${C.TIME_UNSET}",
                "items:1:0:${C.TIME_UNSET}",
                "items:1:0:0",
                "items:1:0:${C.TIME_UNSET}",
                "items:1:2:3000"
            ),
            delegate.events
        )
    }

    @Test
    fun sessionPositionUsesDelegateCompensatedPlaybackState() {
        val delegate = RecordingDelegate().apply {
            reportedPositionMs = 4_200L
            reportedSessionPositionMs = 4_200L
            reportedDurationMs = 9_000L
        }
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)

        assertEquals(4_200L, player.currentPosition)
        assertEquals(4_200L, player.contentPosition)
        assertEquals(9_000L, player.duration)
    }

    @Test
    fun sessionPositionIsThrottledForSystemMediaReads() {
        val clock = MutableClock(1_000L)
        val delegate = RecordingDelegate().apply {
            reportedSessionPositionMs = 1_000L
        }
        val player = PlaybackSessionPlayer(fakePlayer(), delegate, clock)

        assertEquals(1_000L, player.currentPosition)
        delegate.reportedSessionPositionMs = 1_250L
        clock.nowMs = 1_250L

        assertEquals(1_000L, player.currentPosition)

        delegate.reportedSessionPositionMs = 2_000L
        clock.nowMs = 2_000L

        assertEquals(2_000L, player.currentPosition)
    }

    @Test
    fun sessionPlayerKeepsUnderlyingTimelineInsteadOfSynthesizingTheLargeAppQueue() {
        val track = Track(
            4_001L,
            "Track 4001",
            "Artist",
            "Album",
            60_000L,
            android.net.Uri.EMPTY,
            "file:4001"
        )
        val mediaItem = MediaItem.Builder()
            .setMediaId("underlying-current")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()
        val timeline = singleItemTimeline(mediaItem)
        val delegate = RecordingDelegate().apply {
            currentTrackValue = track
        }
        val player = PlaybackSessionPlayer(fakePlayer(FakeQueueState(timeline, mediaItem)), delegate)

        assertSame(timeline, player.currentTimeline)
        assertEquals(1, player.mediaItemCount)
        assertEquals(0, player.currentMediaItemIndex)
        assertSame(mediaItem, player.currentMediaItem)
        assertSame(mediaItem, player.getMediaItemAt(0))
        assertFalse(
            PlaybackSessionPlayer.Delegate::class.java.declaredMethods.any {
                it.name.startsWith("sessionQueue")
            }
        )
        assertEquals(0, delegate.metadataReads)

        assertEquals("Track 4001", player.mediaMetadata.title.toString())
        assertEquals(1, delegate.metadataReads)
    }

    @Test
    fun refreshMediaMetadataPublishesEveryDynamicMetadataChangeToRegisteredListeners() {
        val track = Track(
            4_002L,
            "Track 4002",
            "Artist",
            "Album",
            60_000L,
            android.net.Uri.EMPTY,
            "file:4002"
        )
        val delegate = RecordingDelegate().apply {
            currentTrackValue = track
            metadataTitle = "first lyric"
        }
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)
        val listener = RecordingMetadataListener()
        player.addListener(listener)

        player.refreshMediaMetadata()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        delegate.metadataTitle = "second lyric"
        player.refreshMediaMetadata()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("first lyric", "second lyric"), listener.titles)

        player.removeListener(listener)
        delegate.metadataTitle = "third lyric"
        player.refreshMediaMetadata()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("first lyric", "second lyric"), listener.titles)
    }

    private class RecordingDelegate : PlaybackSessionPlayer.Delegate {
        val events = mutableListOf<String>()
        var reportedPositionMs: Long = 0L
        var reportedSessionPositionMs: Long = 0L
        var reportedDurationMs: Long = 0L
        var currentTrackValue: Track? = null
        var metadataReads: Int = 0
        var metadataTitle: String? = null

        override fun play() {
            events += "play"
        }

        override fun pause() {
            events += "pause"
        }

        override fun seekTo(positionMs: Long) {
            events += "seek:$positionMs"
        }

        override fun skipToPrevious() {
            events += "previous"
        }

        override fun skipToNext() {
            events += "next"
        }

        override fun setRepeatMode(appRepeatMode: Int) {
            events += "repeat:$appRepeatMode"
        }

        override fun stopAndClear() {
            events += "stop"
        }

        override fun setControllerMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): Boolean {
            events += "items:${mediaItems.size}:$startIndex:$startPositionMs"
            return true
        }

        override fun currentTrack(): Track? = currentTrackValue

        override fun mediaMetadataForTrack(track: Track): MediaMetadata? {
            metadataReads++
            return MediaMetadata.Builder()
                .setTitle(metadataTitle ?: track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build()
        }

        override fun positionMs(): Long = reportedPositionMs

        override fun sessionPositionMs(): Long = reportedSessionPositionMs

        override fun durationMs(): Long = reportedDurationMs
    }

    private class MutableClock(var nowMs: Long) : LongSupplier {
        override fun getAsLong(): Long = nowMs
    }

    private data class FakeQueueState(
        val timeline: Timeline,
        val mediaItem: MediaItem
    )

    private class RecordingMetadataListener : Player.Listener {
        val titles = mutableListOf<String>()

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            titles += mediaMetadata.title?.toString().orEmpty()
        }
    }

    companion object {
        private fun fakePlayer(queueState: FakeQueueState? = null): Player {
            return Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getAvailableCommands" -> Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build()
                    "getApplicationLooper" -> Looper.getMainLooper()
                    "isCommandAvailable" -> false
                    "getMediaMetadata" -> MediaMetadata.Builder().build()
                    "getCurrentTimeline" -> queueState?.timeline ?: Timeline.EMPTY
                    "getMediaItemCount" -> if (queueState == null) 0 else 1
                    "getCurrentMediaItemIndex", "getCurrentPeriodIndex", "getCurrentWindowIndex" -> 0
                    "getCurrentMediaItem", "getMediaItemAt" -> queueState?.mediaItem
                    else -> defaultReturnValue(method.returnType)
                }
            } as Player
        }

        private fun singleItemTimeline(mediaItem: MediaItem): Timeline = object : Timeline() {
            override fun getWindowCount(): Int = 1

            override fun getWindow(
                windowIndex: Int,
                window: Window,
                defaultPositionProjectionUs: Long
            ): Window = window.set(
                "window-0",
                mediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                true,
                false,
                null,
                0L,
                C.TIME_UNSET,
                0,
                0,
                0L
            )

            override fun getPeriodCount(): Int = 1

            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
                period.set(
                    if (setIds) "period-0" else null,
                    if (setIds) "period-0" else null,
                    0,
                    C.TIME_UNSET,
                    0L
                )

            override fun getIndexOfPeriod(uid: Any): Int =
                if (uid == "period-0") 0 else C.INDEX_UNSET

            override fun getUidOfPeriod(periodIndex: Int): Any = "period-0"
        }

        private fun defaultReturnValue(returnType: Class<*>): Any? {
            return when (returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Byte.TYPE -> 0.toByte()
                java.lang.Character.TYPE -> 0.toChar()
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0.0f
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Short.TYPE -> 0.toShort()
                java.lang.Void.TYPE -> null
                else -> null
            }
        }
    }
}



