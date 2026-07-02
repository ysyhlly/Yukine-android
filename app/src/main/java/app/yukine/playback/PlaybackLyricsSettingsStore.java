package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.LyricsPublisher;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackLyricsSettingsStore {
    interface LyricsSettings {
        boolean loadStatusBarLyricsEnabled();
    }

    private final LyricsSettings lyricsSettings;

    PlaybackLyricsSettingsStore(LyricsSettings lyricsSettings) {
        this.lyricsSettings = lyricsSettings;
    }

    static PlaybackLyricsSettingsStore fromRepository(MusicLibraryRepository repository) {
        return new PlaybackLyricsSettingsStore(repository::loadStatusBarLyricsEnabled);
    }

    static Consumer<Boolean> statusBarLyricsEnabledActionFromSupplier(
            Supplier<? extends LyricsPublisher> lyricsPublisherSupplier
    ) {
        return enabled -> {
            LyricsPublisher lyricsPublisher =
                    lyricsPublisherSupplier == null ? null : lyricsPublisherSupplier.get();
            if (lyricsPublisher != null && enabled != null) {
                lyricsPublisher.setStatusBarLyricsEnabled(enabled);
            }
        };
    }

    void restoreInto(LyricsPublisher lyricsPublisher) {
        if (lyricsPublisher == null) {
            return;
        }
        lyricsPublisher.setStatusBarLyricsEnabled(lyricsSettings.loadStatusBarLyricsEnabled());
    }
}
