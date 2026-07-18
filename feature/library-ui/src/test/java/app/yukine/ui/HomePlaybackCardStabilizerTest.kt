package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePlaybackCardStabilizerTest {
    private val next = card(title = "下一首")
    private val replacement = card(title = "替换候选")

    @Test
    fun transientCandidateChangeKeepsNextSongCard() {
        val stabilizer = HomePlaybackCardStabilizer(settleMillis = 1_200L, initial = next)

        assertEquals(next, stabilizer.update(replacement, nowMillis = 100L))
        assertEquals(next, stabilizer.update(next, nowMillis = 500L))
    }

    @Test
    fun sustainedCandidateChangeEventuallyUpdatesCard() {
        val stabilizer = HomePlaybackCardStabilizer(settleMillis = 1_200L, initial = next)

        assertEquals(next, stabilizer.update(replacement, nowMillis = 100L))
        assertEquals(replacement, stabilizer.update(replacement, nowMillis = 1_300L))
    }

    @Test
    fun rapidlyChangingNextSongDoesNotReplaceDisplayedCard() {
        val replacementA = card(title = "候选 A")
        val replacementB = card(title = "候选 B")
        val stabilizer = HomePlaybackCardStabilizer(settleMillis = 1_200L, initial = next)

        assertEquals(next, stabilizer.update(replacementA, nowMillis = 100L))
        assertEquals(next, stabilizer.update(replacementB, nowMillis = 500L))
        assertEquals(next, stabilizer.update(replacementB, nowMillis = 1_600L))
        assertEquals(replacementB, stabilizer.update(replacementB, nowMillis = 1_700L))
    }

    @Test
    fun transientMissingQueueEntryDoesNotHideCard() {
        val stabilizer = HomePlaybackCardStabilizer(settleMillis = 1_200L, initial = next)

        assertEquals(next, stabilizer.update(null, nowMillis = 100L))
        assertEquals(next, stabilizer.update(next, nowMillis = 500L))
    }

    @Test
    fun pausedAndPlayingStatesBothPresentTheUpcomingTrack() {
        val paused = HomeDashboardUiState(
            continuePlaying = false,
            continueTitle = "当前歌曲",
            nextTitle = "真正的下一首",
            nextSubtitle = "下一位艺人"
        ).playbackCardState()
        val playing = HomeDashboardUiState(
            continuePlaying = true,
            continueTitle = "当前歌曲",
            nextTitle = "真正的下一首",
            nextSubtitle = "下一位艺人"
        ).playbackCardState()

        assertEquals("真正的下一首", paused?.title)
        assertEquals("即将播放", paused?.detail)
        assertEquals(paused, playing)
    }

    @Test
    fun cardTapAlwaysTogglesPlaybackInsteadOfSkipping() {
        var toggles = 0
        var skips = 0
        val actions = HomeDashboardActions(
            onOpenStat = emptyList(),
            onContinue = Runnable { toggles++ },
            onOpenNowPlaying = Runnable { },
            onPlayRecent = emptyList(),
            onRefresh = Runnable { },
            onViewQueue = Runnable { },
            onShuffleAll = Runnable { },
            onRecentTabChanged = {},
            onNext = Runnable { skips++ }
        )

        next.action(actions).run()

        assertEquals(1, toggles)
        assertEquals(0, skips)
    }

    private fun card(title: String) = HomePlaybackCardState(
        title = title,
        subtitle = "艺人",
        detail = "即将播放",
        albumArtUri = null,
        progress = 0f
    )
}
