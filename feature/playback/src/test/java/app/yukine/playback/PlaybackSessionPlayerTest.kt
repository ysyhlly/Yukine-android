package app.yukine.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackSessionPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun sessionQueueExposesLargeAppQueueWithoutMirroringDelegatePlayer() {
        val delegate = RecordingDelegate().apply {
            queueTracks = (1L..80L).map { id ->
                Track(id, "Track $id", "Artist $id", "Album", 60_000L, android.net.Uri.EMPTY, "streaming:$id")
            }
            queueCurrentIndex = 65
        }
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)

        assertEquals(80, player.mediaItemCount)
        assertEquals(65, player.currentMediaItemIndex)
        assertEquals("queue:65:66", player.currentMediaItem?.mediaId)
        assertEquals("queue:79:80", player.getMediaItemAt(79).mediaId)
        assertEquals(80, player.currentTimeline.windowCount)
        assertEquals(80, player.currentTimeline.periodCount)
        assertEquals("Track 66", player.currentMediaItem?.mediaMetadata?.title.toString())
    }

    @Test
    fun repeatedSessionQueueReadsReuseTimelineAndPreferNarrowAccess() {
        val delegate = RecordingDelegate().apply {
            queueTracks = (1L..4L).map { id ->
                Track(id, "Track $id", "Artist", "Album", 60_000L, android.net.Uri.EMPTY, "streaming:$id")
            }
            queueCurrentIndex = 1
        }
        val player = PlaybackSessionPlayer(fakePlayer(), delegate)

        assertEquals(4, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("queue:2:3", player.getMediaItemAt(2).mediaId)
        assertEquals(0, delegate.queueSnapshotReads)

        assertEquals(4, player.currentTimeline.windowCount)
        assertEquals(4, player.currentTimeline.windowCount)
        assertEquals(1, delegate.queueSnapshotReads)

        delegate.queueTracks = delegate.queueTracks + Track(
            5L,
            "Track 5",
            "Artist",
            "Album",
            60_000L,
            android.net.Uri.EMPTY,
            "streaming:5"
        )

        assertEquals(5, player.currentTimeline.windowCount)
        assertEquals(2, delegate.queueSnapshotReads)
    }

    private class RecordingDelegate : PlaybackSessionPlayer.Delegate {
        val events = mutableListOf<String>()
        var reportedPositionMs: Long = 0L
        var reportedSessionPositionMs: Long = 0L
        var reportedDurationMs: Long = 0L
        var queueTracks: List<Track> = emptyList()
        var queueCurrentIndex: Int = -1
        var queueSnapshotReads: Int = 0

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

        override fun sessionQueueTracks(): List<Track> {
            queueSnapshotReads++
            return queueTracks
        }

        override fun sessionQueueSize(): Int = queueTracks.size

        override fun sessionQueueCurrentIndex(): Int = queueCurrentIndex

        override fun sessionQueueTrackAt(index: Int): Track? = queueTracks.getOrNull(index)

        override fun currentTrack(): Track? = null

        override fun mediaMetadataForTrack(track: Track): MediaMetadata? =
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build()

        override fun positionMs(): Long = reportedPositionMs

        override fun sessionPositionMs(): Long = reportedSessionPositionMs

        override fun durationMs(): Long = reportedDurationMs
    }

    private class MutableClock(var nowMs: Long) : LongSupplier {
        override fun getAsLong(): Long = nowMs
    }

    companion object {
        private fun fakePlayer(): Player {
            return Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getAvailableCommands" -> Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build()
                    "isCommandAvailable" -> false
                    "getMediaMetadata" -> MediaMetadata.Builder().build()
                    else -> defaultReturnValue(method.returnType)
                }
            } as Player
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



