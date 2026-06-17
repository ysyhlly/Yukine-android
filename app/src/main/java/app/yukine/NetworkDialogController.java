package app.yukine;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.LinearLayout;

import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

final class NetworkDialogController {
    interface LanguageProvider {
        String languageMode();
    }

    interface Listener {
        void addStream(String title, String url);

        void importM3u(String url);

        void updateStream(Track track, String title, String url);

        void saveWebDavSource(
                long sourceId,
                String name,
                String baseUrl,
                String username,
                String password,
                String rootPath
        );
    }

    private final Context context;
    private final LanguageProvider languageProvider;
    private final Listener listener;

    NetworkDialogController(Context context, LanguageProvider languageProvider, Listener listener) {
        this.context = context;
        this.languageProvider = languageProvider;
        this.listener = listener;
    }

    void showAddStream() {
        final LinearLayout form = dialogForm();
        final EditText title = dialogInput(text("input"));
        final EditText url = dialogInput("https://example.com/audio.mp3");
        form.addView(title);
        form.addView(url);
        EchoDialog.builder(context)
                .setTitle(text("add.stream.url.title"))
                .setView(form)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.addStream(title.getText().toString(), url.getText().toString());
                    }
                })
                .show();
    }

    void showImportM3u() {
        final LinearLayout form = dialogForm();
        final EditText url = dialogInput("https://example.com/playlist.m3u8");
        form.addView(url);
        EchoDialog.builder(context)
                .setTitle(text("import.m3u.title"))
                .setView(form)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.importM3u(url.getText().toString());
                    }
                })
                .show();
    }

    void showEditStream(final Track track) {
        if (track == null) {
            return;
        }
        final LinearLayout form = dialogForm();
        final EditText title = dialogInput(text("input"), track.title);
        final EditText url = dialogInput("https://example.com/audio.mp3", track.contentUri.toString());
        form.addView(title);
        form.addView(url);
        EchoDialog.builder(context)
                .setTitle(text("edit.stream"))
                .setView(form)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.updateStream(track, title.getText().toString(), url.getText().toString());
                    }
                })
                .show();
    }

    void showAddWebDav() {
        showWebDav(null);
    }

    void showEditWebDav(RemoteSource source) {
        if (source == null) {
            return;
        }
        showWebDav(source);
    }

    private void showWebDav(final RemoteSource source) {
        final LinearLayout form = dialogForm();
        final EditText name = dialogInput(text("name"), source == null ? "" : source.name);
        final EditText baseUrl = dialogInput("https://example.com/dav", source == null ? "" : source.baseUrl);
        final EditText username = dialogInput(text("username"), source == null ? "" : source.username);
        final EditText password = dialogInput(text("password"), source == null ? "" : source.password);
        final EditText rootPath = dialogInput("Music", source == null ? "" : source.rootPath);
        form.addView(name);
        form.addView(baseUrl);
        form.addView(username);
        form.addView(password);
        form.addView(rootPath);
        EchoDialog.builder(context)
                .setTitle(source == null ? text("add.webdav.source") : text("edit.webdav.source"))
                .setView(form)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.saveWebDavSource(
                                source == null ? -1L : source.id,
                                name.getText().toString(),
                                baseUrl.getText().toString(),
                                username.getText().toString(),
                                password.getText().toString(),
                                rootPath.getText().toString()
                        );
                    }
                })
                .show();
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), dp(8), dp(12), dp(4));
        return form;
    }

    private EditText dialogInput(String hint) {
        return dialogInput(hint, "");
    }

    private EditText dialogInput(String hint, String value) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        if (value != null && !value.isEmpty()) {
            input.setText(value);
            input.setSelection(input.getText().length());
        }
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        input.setPadding(dp(8), dp(6), dp(8), dp(6));
        return input;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String text(String key) {
        return AppLanguage.text(languageProvider.languageMode(), key);
    }
}
