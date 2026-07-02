package app.yukine;

import java.util.function.Supplier;

final class StatusMessageController {
    private final StatusMessageViewModel viewModel;
    private final Supplier<String> languageModeProvider;
    private final RawStatusUpdater rawStatusUpdater;
    private final MessageTextResolver textResolver;

    StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater
    ) {
        this(viewModel, languageModeProvider, rawStatusUpdater, new MessageTextResolver(
                languageModeProvider == null ? () -> AppLanguage.MODE_SYSTEM : languageModeProvider
        ));
    }

    StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater,
            MessageTextResolver textResolver
    ) {
        this.viewModel = viewModel;
        this.languageModeProvider = languageModeProvider;
        this.rawStatusUpdater = rawStatusUpdater;
        this.textResolver = textResolver;
    }

    void setStatus(String status) {
        if (languageModeProvider == null || rawStatusUpdater == null) {
            return;
        }
        rawStatusUpdater.update(viewModel.applyStatus(status, languageModeProvider.get()));
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
