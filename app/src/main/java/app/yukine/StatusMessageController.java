package app.yukine;

final class StatusMessageController {
    interface Host {
        String languageMode();

        void updateStatus(String message);
    }

    private final StatusMessageViewModel viewModel;
    private final Host host;
    private final MessageTextResolver textResolver;

    StatusMessageController(StatusMessageViewModel viewModel, Host host) {
        this(viewModel, host, new MessageTextResolver(
                host == null ? () -> AppLanguage.MODE_SYSTEM : host::languageMode
        ));
    }

    StatusMessageController(StatusMessageViewModel viewModel, Host host, MessageTextResolver textResolver) {
        this.viewModel = viewModel;
        this.host = host;
        this.textResolver = textResolver;
    }

    void setStatus(String status) {
        if (host == null) {
            return;
        }
        host.updateStatus(viewModel.applyStatus(status, host.languageMode()));
    }

    void showFeedback(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        setStatus(message);
    }

    void setStatusKey(String key) {
        setStatus(textResolver.text(key));
    }

    static String localize(String status, String languageMode) {
        return StatusMessageViewModel.localize(status, languageMode);
    }
}
