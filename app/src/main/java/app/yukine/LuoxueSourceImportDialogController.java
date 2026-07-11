package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

import app.yukine.streaming.LuoxueImportedSource;
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
                text("streaming.lx.source.url"),
                text("streaming.lx.source.manage")
        };
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.import.source"))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        importController.openFilePicker();
                    } else if (which == 1) {
                        showUrlDialog();
                    } else {
                        showSourceManager();
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

    private void showSourceManager() {
        final List<LuoxueImportedSource> sources = importController.importedSources();
        if (sources.isEmpty()) {
            EchoDialog.builder(context)
                    .setTitle(text("streaming.lx.source.manager"))
                    .setMessage(text("streaming.lx.source.empty"))
                    .setNegativeButton(text("cancel"), null)
                    .show();
            return;
        }
        String[] items = new String[sources.size()];
        for (int index = 0; index < sources.size(); index++) {
            items[index] = sourceLabel(sources.get(index));
        }
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.source.manager"))
                .setItems(items, (dialog, which) -> showSourceActions(sources, which))
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void showSourceActions(List<LuoxueImportedSource> sources, int sourceIndex) {
        if (sourceIndex < 0 || sourceIndex >= sources.size()) {
            return;
        }
        final LuoxueImportedSource source = sources.get(sourceIndex);
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        labels.add(text(source.getEnabled()
                ? "streaming.lx.source.disable"
                : "streaming.lx.source.enable"));
        actions.add(ACTION_TOGGLE);
        if (sourceIndex > 0) {
            labels.add(text("streaming.lx.source.move.up"));
            actions.add(ACTION_MOVE_UP);
        }
        if (sourceIndex < sources.size() - 1) {
            labels.add(text("streaming.lx.source.move.down"));
            actions.add(ACTION_MOVE_DOWN);
        }
        labels.add(text("streaming.lx.source.remove"));
        actions.add(ACTION_REMOVE);
        EchoDialog.builder(context)
                .setTitle(source.getName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    if (action == ACTION_REMOVE) {
                        showRemoveConfirm(source);
                        return;
                    }
                    boolean changed;
                    if (action == ACTION_TOGGLE) {
                        changed = importController.setSourceEnabled(source.getId(), !source.getEnabled());
                    } else {
                        changed = importController.moveSource(
                                source.getId(),
                                action == ACTION_MOVE_UP ? -1 : 1
                        );
                    }
                    if (changed) {
                        showSourceManager();
                    }
                })
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void showRemoveConfirm(LuoxueImportedSource source) {
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.source.remove"))
                .setMessage(text("streaming.lx.source.remove.confirm") + "\n" + source.getName())
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("streaming.lx.source.remove"), (dialog, which) -> {
                    if (importController.removeSource(source.getId())) {
                        showSourceManager();
                    }
                })
                .show();
    }

    private String sourceLabel(LuoxueImportedSource source) {
        String state = text(source.getEnabled()
                ? "streaming.lx.source.enabled"
                : "streaming.lx.source.disabled");
        String detail = source.getVersion();
        if (source.getAuthor() != null && !source.getAuthor().isEmpty()) {
            detail = detail.isEmpty() ? source.getAuthor() : detail + " · " + source.getAuthor();
        }
        return source.getName() + " · " + state + (detail.isEmpty() ? "" : " · " + detail);
    }

    private String text(String key) {
        return languageProvider.text(key);
    }

    private static final int ACTION_TOGGLE = 0;
    private static final int ACTION_MOVE_UP = 1;
    private static final int ACTION_MOVE_DOWN = 2;
    private static final int ACTION_REMOVE = 3;
}
