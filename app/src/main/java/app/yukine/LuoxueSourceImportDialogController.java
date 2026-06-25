package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

final class LuoxueSourceImportDialogController {
    interface LanguageProvider {
        String text(String key);
    }

    private final Context context;
    private final LanguageProvider languageProvider;
    private final LuoxueSourceImportController importController;

    LuoxueSourceImportDialogController(
            Context context,
            LanguageProvider languageProvider,
            LuoxueSourceImportController importController
    ) {
        this.context = context;
        this.languageProvider = languageProvider;
        this.importController = importController;
    }

    void showImportDialog() {
        String[] actions = new String[]{
                text("streaming.lx.source.file"),
                text("streaming.lx.source.url")
        };
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.import.source"))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        importController.openFilePicker();
                    } else {
                        showUrlDialog();
                    }
                })
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void showUrlDialog() {
        final EditText input = new EditText(context);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(text("streaming.lx.source.url.hint"));
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.source.url"))
                .setView(input)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), (dialog, which) ->
                        importController.importFromUrls(input.getText().toString()))
                .show();
    }

    private String text(String key) {
        return languageProvider.text(key);
    }
}
