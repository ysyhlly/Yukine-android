package app.yukine;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.net.URI;

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

    void showSourceManager() {
        final List<LuoxueImportedSource> sources = importController.importedSources();
        if (sources.isEmpty()) {
            EchoDialog.builder(context)
                    .setTitle(text("streaming.lx.source.manager"))
                    .setMessage(text("streaming.lx.source.empty"))
                    .setNegativeButton(text("cancel"), null)
                    .setPositiveButton(text("streaming.lx.source.import.new"),
                            (dialog, which) -> showImportDialog())
                    .show();
            return;
        }
        int activeCount = 0;
        String[] items = new String[sources.size() + 3];
        for (int index = 0; index < sources.size(); index++) {
            LuoxueImportedSource source = sources.get(index);
            if (source.getEnabled() && !source.getScript().isEmpty()) {
                activeCount++;
            }
            items[index] = sourceLabel(source, index);
        }
        items[sources.size()] = "+ " + text("streaming.lx.source.import.new");
        items[sources.size() + 1] = text("streaming.lx.source.enable.all");
        items[sources.size() + 2] = text("streaming.lx.source.disable.all");
        String summary = sourceSummary(activeCount, sources.size());
        EchoDialog.builder(context)
                .setTitle(text("streaming.lx.source.manager"))
                .setMessage(summary + "\n" + text("streaming.lx.source.manager.hint"))
                .setItems(items, (dialog, which) -> {
                    if (which < sources.size()) {
                        showSourceActions(sources, which);
                    } else if (which == sources.size()) {
                        showImportDialog();
                    } else {
                        importController.setAllSourcesEnabled(which == sources.size() + 1);
                        showSourceManager();
                    }
                })
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
        if (isNetworkOrigin(source.getOrigin())) {
            labels.add(text("streaming.lx.source.update"));
            actions.add(ACTION_UPDATE);
        }
        labels.add(text("streaming.lx.source.remove"));
        actions.add(ACTION_REMOVE);
        EchoDialog.builder(context)
                .setTitle(source.getName())
                .setMessage(sourceDetails(source, sourceIndex))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    if (action == ACTION_REMOVE) {
                        showRemoveConfirm(source);
                        return;
                    }
                    if (action == ACTION_UPDATE) {
                        importController.importFromUrls(source.getOrigin());
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

    private String sourceLabel(LuoxueImportedSource source, int index) {
        String state = text(!source.getEnabled()
                ? "streaming.lx.source.disabled"
                : source.getScript().isEmpty()
                        ? "streaming.lx.source.missing.script"
                        : "streaming.lx.source.enabled");
        String detail = sourceKinds(source);
        if (!source.getVersion().isEmpty()) {
            detail += " · " + source.getVersion();
        }
        if (source.getAuthor() != null && !source.getAuthor().isEmpty()) {
            detail += " · " + source.getAuthor();
        }
        return "#" + (index + 1) + " · " + state + " · " + source.getName() + "\n" + detail;
    }

    private String sourceDetails(LuoxueImportedSource source, int sourceIndex) {
        String unspecified = text("streaming.lx.source.unspecified");
        String author = source.getAuthor().isEmpty() ? unspecified : source.getAuthor();
        String version = source.getVersion().isEmpty() ? unspecified : source.getVersion();
        String origin = source.getOrigin().isEmpty() ? unspecified : source.getOrigin();
        return text("streaming.lx.source.priority") + "：#" + (sourceIndex + 1) + "\n"
                + text("streaming.lx.source.capabilities") + "：" + sourceKinds(source) + "\n"
                + text("streaming.lx.source.author") + "：" + author + "\n"
                + text("streaming.lx.source.version") + "：" + version + "\n"
                + text("streaming.lx.source.origin") + "：" + origin;
    }

    private String sourceKinds(LuoxueImportedSource source) {
        if (source.getSourceKinds().isEmpty()) {
            return text("streaming.lx.source.unspecified");
        }
        ArrayList<String> kinds = new ArrayList<>();
        for (String kind : source.getSourceKinds()) {
            if (kind != null && !kind.trim().isEmpty()) {
                kinds.add(kind.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return kinds.isEmpty()
                ? text("streaming.lx.source.unspecified")
                : android.text.TextUtils.join(" / ", kinds);
    }

    private String sourceSummary(int enabled, int total) {
        return text("streaming.lx.source.enabled.count") + enabled + "/" + total;
    }

    private boolean isNetworkOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return false;
        }
        try {
            String scheme = URI.create(origin.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String text(String key) {
        return languageProvider.text(key);
    }

    private static final int ACTION_TOGGLE = 0;
    private static final int ACTION_MOVE_UP = 1;
    private static final int ACTION_MOVE_DOWN = 2;
    private static final int ACTION_REMOVE = 3;
    private static final int ACTION_UPDATE = 4;
}
