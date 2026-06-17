package app.yukine;

final class StatusMessageController {
    interface Host {
        String languageMode();

        void updateStatus(String message);
    }

    private final Host host;

    StatusMessageController(Host host) {
        this.host = host;
    }

    void setStatus(String status) {
        host.updateStatus(localize(status, host.languageMode()));
    }

    static String localize(String status, String languageMode) {
        if (status == null || status.trim().isEmpty()) {
            return "";
        }
        String value = status.trim();
        if (value.equals("Status")) {
            return AppLanguage.text(languageMode, "status");
        }
        if (value.equals("Loading library")) {
            return AppLanguage.text(languageMode, "loading.library");
        }
        if (value.equals("Audio permission required")) {
            return AppLanguage.text(languageMode, "audio.permission.required");
        }
        if (value.equals("No tracks to play")) {
            return AppLanguage.text(languageMode, "no.tracks.to.play");
        }
        if (value.equals("Queue is not connected")) {
            return AppLanguage.text(languageMode, "queue.not.connected");
        }
        if (value.equals("Playback service is not connected")) {
            return AppLanguage.text(languageMode, "playback.service.not.connected");
        }
        if (value.equals("Cookie is empty") || value.equals("Cookie \u4e3a\u7a7a")) {
            return AppLanguage.text(languageMode, "streaming.cookie.empty");
        }
        if (value.equals("Cookie saved") || value.equals("Cookie \u5df2\u4fdd\u5b58")) {
            return AppLanguage.text(languageMode, "streaming.cookie.saved");
        }
        if (value.equals("Choose a streaming provider to sign in")) {
            return AppLanguage.text(languageMode, "streaming.choose.login.provider");
        }
        if (value.startsWith("Status: ")) {
            return AppLanguage.text(languageMode, "status") + ": " + value.substring("Status: ".length());
        }
        return PlaybackErrorMessageLocalizer.localize(value, languageMode);
    }
}
