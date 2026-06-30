package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.LyricsPublisher;
import app.yukine.playback.manager.PlaybackNotificationManager;

final class PlaybackNotificationLyricsTextOwner implements PlaybackNotificationManager.LyricsTextProvider {
    interface LyricsPublisherProvider {
        LyricsPublisher lyricsPublisher();
    }

    private final LyricsPublisherProvider lyricsPublisherProvider;

    PlaybackNotificationLyricsTextOwner(LyricsPublisherProvider lyricsPublisherProvider) {
        this.lyricsPublisherProvider = lyricsPublisherProvider;
    }

    @Override
    public String currentNotificationLyric(Track track) {
        LyricsPublisher publisher = lyricsPublisherProvider.lyricsPublisher();
        return publisher == null ? "" : publisher.notificationLyricText(track);
    }

    @Override
    public String sanitizeNotificationLyric(String value) {
        LyricsPublisher publisher = lyricsPublisherProvider.lyricsPublisher();
        return publisher == null ? "" : publisher.sanitizeNotificationLyric(value);
    }
}
