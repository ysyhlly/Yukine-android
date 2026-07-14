package app.yukine

import android.net.Uri
import app.yukine.model.Track
import app.yukine.streaming.StreamingAudioQuality
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.function.Consumer

class NowPlayingEffectOwnerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun eventIsReducedAndEveryTypedEffectReachesItsPlatformAction() {
        val calls = mutableListOf<String>()
        val viewModel = NowPlayingViewModel()
        val owner = owner(viewModel, calls)
        val current = track(7L, "current")
        val replacement = track(8L, "replacement")

        owner.handle(NowPlayingEvent.OpenQueue)
        owner.dispatch(NowPlayingEffect.OpenAddToPlaylist(current))
        owner.dispatch(NowPlayingEffect.ShareTrack(current))
        owner.dispatch(NowPlayingEffect.DownloadTrack(current))
        owner.dispatch(
            NowPlayingEffect.SwitchSource(
                current,
                StreamingProviderName.QQ_MUSIC,
                "qq-7",
                StreamingAudioQuality.HIGH,
                11L
            )
        )
        owner.dispatch(NowPlayingEffect.SwitchLibrarySource(current, replacement, 12L))
        owner.dispatch(NowPlayingEffect.ShowMessage("ready"))

        assertEquals(
            listOf(
                "queue",
                "playlist:7",
                "share:7",
                "download:7",
                "streaming:11",
                "library:12",
                "message:ready"
            ),
            calls
        )
        assertEquals(emptyList<NowPlayingEffect>(), viewModel.drainEffects())
    }

    private fun owner(viewModel: NowPlayingViewModel, calls: MutableList<String>) =
        NowPlayingEffectOwner(
            viewModel,
            Runnable { calls += "queue" },
            Consumer { calls += "playlist:${it.id}" },
            Consumer { calls += "share:${it.id}" },
            Consumer { calls += "download:${it.id}" },
            Consumer { calls += "streaming:${it.requestId}" },
            Consumer { calls += "library:${it.requestId}" },
            Consumer { calls += "message:$it" }
        )

    private fun track(id: Long, path: String) = Track(
        id,
        "Song $id",
        "Artist",
        "Album",
        180_000L,
        Uri.EMPTY,
        path
    )
}
