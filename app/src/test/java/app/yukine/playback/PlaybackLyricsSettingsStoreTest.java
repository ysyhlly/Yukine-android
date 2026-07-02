package app.yukine.playback;

import app.yukine.model.Track;
import app.yukine.playback.manager.LyricsPublisher;

import org.junit.Test;

import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public final class PlaybackLyricsSettingsStoreTest {
    @Test
    public void restoreAppliesSavedStatusBarLyricsSetting() {
        FakeLyricsSettings settings = new FakeLyricsSettings();
        settings.statusBarLyricsEnabled = false;
        FakeLyricsPublisher publisher = new FakeLyricsPublisher();

        new PlaybackLyricsSettingsStore(settings).restoreInto(publisher);

        assertEquals(1, publisher.setStatusBarLyricsEnabledCalls);
        assertEquals(false, publisher.statusBarLyricsEnabled);
    }

    @Test
    public void restoreIgnoresMissingPublisher() {
        FakeLyricsSettings settings = new FakeLyricsSettings();

        new PlaybackLyricsSettingsStore(settings).restoreInto(null);

        assertEquals(0, settings.loadCalls);
    }

    @Test
    public void statusBarLyricsEnabledActionDelegatesToPublisher() {
        FakeLyricsPublisher publisher = new FakeLyricsPublisher();
        Consumer<Boolean> action =
                PlaybackLyricsSettingsStore.statusBarLyricsEnabledActionFromSupplier(() -> publisher);

        action.accept(false);

        assertEquals(1, publisher.setStatusBarLyricsEnabledCalls);
        assertEquals(false, publisher.statusBarLyricsEnabled);
    }

    @Test
    public void statusBarLyricsEnabledActionIgnoresMissingPublisher() {
        Consumer<Boolean> action =
                PlaybackLyricsSettingsStore.statusBarLyricsEnabledActionFromSupplier(() -> null);

        action.accept(false);
    }

    private static final class FakeLyricsSettings implements PlaybackLyricsSettingsStore.LyricsSettings {
        boolean statusBarLyricsEnabled = true;
        int loadCalls;

        @Override
        public boolean loadStatusBarLyricsEnabled() {
            loadCalls++;
            return statusBarLyricsEnabled;
        }
    }

    private static final class FakeLyricsPublisher implements LyricsPublisher {
        int setStatusBarLyricsEnabledCalls;
        boolean statusBarLyricsEnabled = true;

        @Override
        public void bind() {
        }

        @Override
        public void release() {
        }

        @Override
        public void setStatusBarLyricsEnabled(boolean enabled) {
            setStatusBarLyricsEnabledCalls++;
            statusBarLyricsEnabled = enabled;
        }

        @Override
        public void syncFloatingLyricsPlaybackState(PlaybackStateSnapshot snapshot) {
        }

        @Override
        public String notificationLyricText(Track track) {
            return "";
        }

        @Override
        public String sanitizeNotificationLyric(String value) {
            return "";
        }
    }
}
