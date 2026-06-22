package app.yukine

import android.net.Uri
import app.yukine.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingEffectControllerTest {
    @Test
    fun handlesAllNowPlayingEffectsInOrder() {
        val listener = FakeListener()
        val controller = NowPlayingEffectController(listener)
        val track = track(5L)

        controller.handle(
            listOf(
                NowPlayingEffect.OpenQueue,
                NowPlayingEffect.OpenAddToPlaylist(track),
                NowPlayingEffect.ShareTrack(track),
                NowPlayingEffect.DownloadTrack(track),
                NowPlayingEffect.ShowMessage("Done")
            )
        )

        assertEquals(listOf("queue", "playlist:5", "share:5", "download:5", "message:Done"), listener.calls)
    }

    @Test
    fun ignoresNullAndEmptyEffects() {
        val listener = FakeListener()
        val controller = NowPlayingEffectController(listener)

        controller.handle(null)
        controller.handle(emptyList())

        assertEquals(emptyList<String>(), listener.calls)
    }

    private class FakeListener : NowPlayingEffectController.Listener {
        val calls = mutableListOf<String>()

        override fun openQueue() {
            calls += "queue"
        }

        override fun openAddToPlaylist(effect: NowPlayingEffect.OpenAddToPlaylist) {
            calls += "playlist:${effect.track.id}"
        }

        override fun shareTrack(effect: NowPlayingEffect.ShareTrack) {
            calls += "share:${effect.track.id}"
        }

        override fun downloadTrack(effect: NowPlayingEffect.DownloadTrack) {
            calls += "download:${effect.track.id}"
        }

        override fun switchSource(effect: NowPlayingEffect.SwitchSource) {
            calls += "source:${effect.track.id}"
        }

        override fun showMessage(message: String) {
            calls += "message:$message"
        }
    }

    private fun track(id: Long): Track =
        Track(id, "Track $id", "Artist", "Album", 1000L, Uri.EMPTY, "file:$id")
}
