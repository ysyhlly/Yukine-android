package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.LyricsPublisher;
import app.yukine.playback.manager.PlaybackNotificationManager;

import java.util.function.Supplier;

final class PlaybackNotificationLyricsTextOwner implements PlaybackNotificationManager.LyricsTextProvider {
    private final Supplier<LyricsPublisher> lyricsPublisherSupplier;

    PlaybackNotificationLyricsTextOwner(Supplier<LyricsPublisher> lyricsPublisherSupplier) {
        this.lyricsPublisherSupplier = lyricsPublisherSupplier;
    }

    @Override
    public String currentNotificationLyric(Track track) {
        LyricsPublisher publisher = lyricsPublisher();
        return publisher == null ? "" : publisher.notificationLyricText(track);
    }

    @Override
    public String sanitizeNotificationLyric(String value) {
        LyricsPublisher publisher = lyricsPublisher();
        return publisher == null ? "" : publisher.sanitizeNotificationLyric(value);
    }

    private LyricsPublisher lyricsPublisher() {
        return lyricsPublisherSupplier == null ? null : lyricsPublisherSupplier.get();
    }
}
