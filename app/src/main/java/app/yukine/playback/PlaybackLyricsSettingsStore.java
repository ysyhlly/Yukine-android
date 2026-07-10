package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.LyricsPublisher;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class PlaybackLyricsSettingsStore {
    interface LyricsSettings {
        boolean loadStatusBarLyricsEnabled();

        boolean loadSystemMediaLyricsTitleEnabled();
    }

    private final LyricsSettings lyricsSettings;

    PlaybackLyricsSettingsStore(LyricsSettings lyricsSettings) {
        this.lyricsSettings = lyricsSettings;
    }

    static PlaybackLyricsSettingsStore fromRepository(MusicLibraryRepository repository) {
        return new PlaybackLyricsSettingsStore(new LyricsSettings() {
            @Override
            public boolean loadStatusBarLyricsEnabled() {
                return repository.loadStatusBarLyricsEnabled();
            }

            @Override
            public boolean loadSystemMediaLyricsTitleEnabled() {
                return repository.loadSystemMediaLyricsTitleEnabled();
            }
        });
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

    static Consumer<Boolean> systemMediaLyricsTitleEnabledActionFromSupplier(
            Supplier<? extends LyricsPublisher> lyricsPublisherSupplier
    ) {
        return enabled -> {
            LyricsPublisher lyricsPublisher =
                    lyricsPublisherSupplier == null ? null : lyricsPublisherSupplier.get();
            if (lyricsPublisher != null && enabled != null) {
                lyricsPublisher.setSystemMediaLyricsTitleEnabled(enabled);
            }
        };
    }

    void restoreInto(LyricsPublisher lyricsPublisher) {
        if (lyricsPublisher == null) {
            return;
        }
        lyricsPublisher.setStatusBarLyricsEnabled(lyricsSettings.loadStatusBarLyricsEnabled());
        lyricsPublisher.setSystemMediaLyricsTitleEnabled(
                lyricsSettings.loadSystemMediaLyricsTitleEnabled()
        );
    }
}
