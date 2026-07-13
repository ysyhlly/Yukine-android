package app.yukine;

import android.content.Context;
import android.widget.EditText;

import app.yukine.streaming.StreamingProviderName;
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

final class StreamingPlaylistImportDialogController {
    interface LanguageProvider {
        String languageMode();

        String text(String key);
    }

    interface Listener {
        StreamingProviderName selectedProvider();

        void showLuoxueSourceImportDialog();

        void importStreamingPlaylistFromLink(String linkOrId);
    }

    private final Context context;
    private final StreamingViewModel streamingViewModel;
    private final LanguageProvider languageProvider;
    private final Listener listener;

    StreamingPlaylistImportDialogController(
            Context context,
            StreamingViewModel streamingViewModel,
            LanguageProvider languageProvider,
            Listener listener
    ) {
        this.context = context;
        this.streamingViewModel = streamingViewModel;
        this.languageProvider = languageProvider;
        this.listener = listener;
    }

    void showImportDialog() {
        if (listener.selectedProvider() == StreamingProviderName.LUOXUE) {
            listener.showLuoxueSourceImportDialog();
            return;
        }
        StreamingPlaylistImportDialogState dialogState =
                streamingViewModel.playlistOwner().prepareStreamingPlaylistImportDialogState(languageProvider.languageMode());
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(dialogState.getHint());
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(context)
                .setTitle(dialogState.getTitle())
                .setView(input)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), (dialog, which) ->
                        listener.importStreamingPlaylistFromLink(input.getText().toString()))
                .show();
    }

    private String text(String key) {
        return languageProvider.text(key);
    }
}
