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

    private class RecordingDelegate : PlaybackSessionPlayer.Delegate {
        val events = mutableListOf<String>()

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

        override fun currentTrack(): Track? = null

        override fun mediaMetadataForTrack(track: Track): MediaMetadata? = null
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



