package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import app.yukine.streaming.StreamingProviderName;
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

final class StreamingManualCookieDialogController {
    interface LanguageProvider {
        String text(String key);
    }

    interface ConfirmAction {
        void save(StreamingProviderName provider, String cookieHeader);
    }

    private final Context context;
    private final LanguageProvider languageProvider;
    private final ConfirmAction confirmAction;

    StreamingManualCookieDialogController(
            Context context,
            LanguageProvider languageProvider,
            ConfirmAction confirmAction
    ) {
        this.context = context;
        this.languageProvider = languageProvider;
        this.confirmAction = confirmAction;
    }

    void show(StreamingManualCookieDialogState dialogState) {
        final EditText input = new EditText(context);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
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
                        confirmAction.save(dialogState.getProvider(), input.getText().toString()))
                .show();
    }

    private String text(String key) {
        return languageProvider.text(key);
    }
}
