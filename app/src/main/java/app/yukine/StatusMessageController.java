package app.yukine;

import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

final class StatusMessageController {
    private final StatusMessageViewModel viewModel;
    private final Supplier<String> languageModeProvider;
    private final RawStatusUpdater rawStatusUpdater;
    private final MessageTextResolver textResolver;
    private final BooleanSupplier debugPromptsEnabledProvider;

    StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater
    ) {
        this(viewModel, languageModeProvider, rawStatusUpdater, new MessageTextResolver(
                languageModeProvider == null ? () -> AppLanguage.MODE_SYSTEM : languageModeProvider
        ), () -> true);
    }

    StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater,
            BooleanSupplier debugPromptsEnabledProvider
    ) {
        this(viewModel, languageModeProvider, rawStatusUpdater, new MessageTextResolver(
                languageModeProvider == null ? () -> AppLanguage.MODE_SYSTEM : languageModeProvider
        ), debugPromptsEnabledProvider);
    }

    StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater,
            MessageTextResolver textResolver
    ) {
        this(viewModel, languageModeProvider, rawStatusUpdater, textResolver, () -> true);
    }

    private StatusMessageController(
            StatusMessageViewModel viewModel,
            Supplier<String> languageModeProvider,
            RawStatusUpdater rawStatusUpdater,
            MessageTextResolver textResolver,
            BooleanSupplier debugPromptsEnabledProvider
    ) {
        this.viewModel = viewModel;
        this.languageModeProvider = languageModeProvider;
        this.rawStatusUpdater = rawStatusUpdater;
        this.textResolver = textResolver;
        this.debugPromptsEnabledProvider = debugPromptsEnabledProvider;
    }

    void setStatus(String status) {
        boolean debugPromptsEnabled = debugPromptsEnabledProvider != null
                && debugPromptsEnabledProvider.getAsBoolean();
        if (!debugPromptsEnabled && !isImportantStatus(status)) {
            return;
        }
        publishStatus(status);
    }

    private void publishStatus(String status) {
        if (languageModeProvider == null || rawStatusUpdater == null) {
            return;
        }
        rawStatusUpdater.update(viewModel.applyStatus(status, languageModeProvider.get()));
    }

    void showFeedback(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        publishStatus(message);
    }

    void setStatusKey(String key) {
        setStatus(textResolver.text(key));
    }

    static String localize(String status, String languageMode) {
        return StatusMessageViewModel.localize(status, languageMode);
    }

    private boolean isImportantStatus(String status) {
        if (status == null || languageModeProvider == null) {
            return false;
        }
        String message = localize(status, languageModeProvider.get()).toLowerCase(java.util.Locale.ROOT);
        return message.contains("fail")
                || message.contains("error")
                || message.contains("unable")
                || message.contains("unavailable")
                || message.contains("required")
                || message.contains("denied")
                || message.contains("not connected")
                || message.contains("not ready")
                || message.contains("no tracks")
                || message.contains("not found")
                || message.contains("\u5931\u8d25")
                || message.contains("\u9519\u8bef")
                || message.contains("\u65e0\u6cd5")
                || message.contains("\u4e0d\u53ef\u7528")
                || message.contains("\u9700\u8981")
                || message.contains("\u6743\u9650")
                || message.contains("\u672a\u8fde\u63a5")
                || message.contains("\u672a\u5c31\u7eea")
                || message.contains("\u6ca1\u6709\u53ef\u64ad\u653e")
                || message.contains("\u672a\u627e\u5230");
    }
}
