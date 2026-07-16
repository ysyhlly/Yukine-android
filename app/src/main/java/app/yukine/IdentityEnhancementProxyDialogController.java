package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import java.util.function.Consumer;
import java.util.function.Supplier;

import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

/** Activity-bound editor for the optional MusicBrainz proxy endpoint. */
final class IdentityEnhancementProxyDialogController {
    private final Context context;
    private final IdentityEnhancementSettingsStore store;
    private final Supplier<String> languageMode;
    private final Consumer<String> status;

    IdentityEnhancementProxyDialogController(
            Context context,
            IdentityEnhancementSettingsStore store,
            Supplier<String> languageMode,
            Consumer<String> status
    ) {
        this.context = context;
        this.store = store;
        this.languageMode = languageMode;
        this.status = status;
    }

    void show() {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(text("musicbrainz.proxy.hint"));
        input.setText(store.musicBrainzProxy());
        input.setSelection(input.length());
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        EchoDialog.builder(context)
                .setTitle(text("musicbrainz.proxy"))
                .setView(input)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), (dialog, which) -> save(input.getText().toString()))
                .show();
    }

    private void save(String value) {
        String trimmed = value == null ? "" : value.trim();
        String normalized = IdentityEnhancementSettingsStore.normalizeMusicBrainzProxy(trimmed);
        if (!trimmed.isEmpty() && normalized.isEmpty()) {
            status.accept(text("musicbrainz.proxy.invalid"));
            return;
        }
        store.setMusicBrainzProxy(normalized);
        IdentityEnhancementScheduler.INSTANCE.schedule(context);
        status.accept(text("musicbrainz.proxy.saved"));
    }

    private String text(String key) {
        return AppLanguage.text(languageMode.get(), key);
    }
}
