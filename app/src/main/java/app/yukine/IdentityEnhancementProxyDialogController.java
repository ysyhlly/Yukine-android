package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.function.Consumer;
import java.util.function.Supplier;

import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

/** Activity-bound editor for shared, custom and fully-offline metadata modes. */
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
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        RadioGroup modes = new RadioGroup(context);
        RadioButton shared = option(text("metadata.gateway.shared"), MetadataGatewayMode.SHARED.ordinal() + 1);
        RadioButton custom = option(text("metadata.gateway.custom"), MetadataGatewayMode.CUSTOM.ordinal() + 1);
        RadioButton offline = option(text("metadata.gateway.offline"), MetadataGatewayMode.OFFLINE.ordinal() + 1);
        shared.setEnabled(!IdentityEnhancementSettingsStore.normalizeMetadataGatewayEndpoint(
                BuildConfig.ECHO_METADATA_GATEWAY_URL).isEmpty());
        modes.addView(shared);
        modes.addView(custom);
        modes.addView(offline);
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(text("metadata.gateway.hint"));
        input.setText(store.customGatewayEndpoint());
        input.setSelection(input.length());
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        input.setPadding(pad, pad, pad, pad);
        content.addView(modes);
        content.addView(input);
        MetadataGatewayMode current = store.mode();
        modes.check(current.ordinal() + 1);
        input.setEnabled(current == MetadataGatewayMode.CUSTOM);
        modes.setOnCheckedChangeListener((group, checkedId) ->
                input.setEnabled(checkedId == MetadataGatewayMode.CUSTOM.ordinal() + 1));
        EchoDialog.builder(context)
                .setTitle(text("metadata.gateway"))
                .setView(content)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), (dialog, which) ->
                        save(modes.getCheckedRadioButtonId(), input.getText().toString()))
                .show();
    }

    private RadioButton option(String label, int id) {
        RadioButton button = new RadioButton(context);
        button.setId(id);
        button.setText(label);
        button.setTextColor(EchoTheme.textArgb(context));
        return button;
    }

    private void save(int selected, String value) {
        int selectedOrdinal = selected - 1;
        MetadataGatewayMode mode = MetadataGatewayMode.values()[Math.max(0,
                Math.min(selectedOrdinal, MetadataGatewayMode.values().length - 1))];
        String trimmed = value == null ? "" : value.trim();
        String normalized = IdentityEnhancementSettingsStore.normalizeMetadataGatewayEndpoint(trimmed);
        if (mode == MetadataGatewayMode.CUSTOM && normalized.isEmpty()) {
            status.accept(text("metadata.gateway.invalid"));
            return;
        }
        if (mode == MetadataGatewayMode.CUSTOM) {
            store.setCustomGatewayEndpoint(normalized);
        } else {
            store.setMode(mode);
        }
        IdentityEnhancementScheduler.INSTANCE.schedule(context);
        status.accept(text("metadata.gateway.saved"));
    }

    private String text(String key) {
        return AppLanguage.text(languageMode.get(), key);
    }
}
