package app.yukine.playback;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.playback.manager.LyricsPublisher;

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

    void restoreInto(LyricsPublisher lyricsPublisher) {
        if (lyricsPublisher == null) {
            return;
        }
        lyricsPublisher.setStatusBarLyricsEnabled(lyricsSettings.loadStatusBarLyricsEnabled());
    }
}
