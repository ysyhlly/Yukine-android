package app.yukine

import app.yukine.streaming.StreamingTrack

internal fun interface DailyRecommendationTrackListPlayer {
    fun play(presentation: StreamingRecommendationPresentation)
}

internal fun interface DailyRecommendationPresentationBuilder {
    fun build(
        streamingTracks: List<StreamingTrack>,
        emptyStatus: String,
        title: String
    ): StreamingRecommendationPresentation
}

internal class DailyRecommendationBindings(
    private val presentationBuilder: DailyRecommendationPresentationBuilder,
    private val trackListPlayer: DailyRecommendationTrackListPlayer,
    private val statusSink: QueueStatusSink
) : DailyRecommendationController.Listener {
    override fun playRecommendationTracks(
        streamingTracks: List<StreamingTrack>,
        emptyStatus: String,
        title: String
    ) {
        trackListPlayer.play(presentationBuilder.build(streamingTracks, emptyStatus, title))
    }

    override fun setStatus(status: String) {
        statusSink.set(status)
    }
}
