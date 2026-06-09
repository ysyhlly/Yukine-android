package app.echo.next;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.EditText;

import java.util.List;

import app.echo.next.model.Playlist;
import app.echo.next.model.Track;
import app.echo.next.ui.EchoDialog;
import app.echo.next.ui.EchoTheme;

final class PlaylistDialogController {
    interface LanguageProvider {
        String languageMode();
    }

    interface Listener {
        void createPlaylist(String name);

        void renamePlaylist(long playlistId, String name);

        void deletePlaylist(long playlistId, String name);

        void addTrackToPlaylist(long playlistId, long trackId);
    }

    private final Context context;
    private final LanguageProvider languageProvider;
    private final Listener listener;

    PlaylistDialogController(Context context, LanguageProvider languageProvider, Listener listener) {
        this.context = context;
        this.languageProvider = languageProvider;
        this.listener = listener;
    }

    void showCreatePlaylist() {
        final EditText input = playlistInput("");
        EchoDialog.builder(context)
                .setTitle(text("create.playlist"))
                .setView(input)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.createPlaylist(input.getText().toString());
                    }
                })
                .show();
    }

    void showRenamePlaylist(final Playlist playlist) {
        if (playlist == null) {
            return;
        }
        final EditText input = playlistInput(playlist.name);
        EchoDialog.builder(context)
                .setTitle(text("rename.playlist"))
                .setView(input)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.renamePlaylist(playlist.id, input.getText().toString());
                    }
                })
                .show();
    }

    void confirmDeletePlaylist(final Playlist playlist) {
        if (playlist == null) {
            return;
        }
        EchoDialog.builder(context)
                .setTitle(text("delete.playlist"))
                .setMessage(text("delete.playlist.message.prefix") + playlist.name + text("delete.message.suffix"))
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.deletePlaylist(playlist.id, playlist.name);
                    }
                })
                .show();
    }

    void showAddToPlaylist(final Track track, final List<Playlist> playlists) {
        if (track == null || playlists == null || playlists.isEmpty()) {
            return;
        }
        final String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) {
            Playlist playlist = playlists.get(i);
            names[i] = playlist.name + " (" + playlist.trackCount + ")";
        }
        EchoDialog.builder(context)
                .setTitle(text("choose.playlist"))
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        listener.addTrackToPlaylist(playlists.get(which).id, track.id);
                    }
                })
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private EditText playlistInput(String value) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(text("input"));
        if (value != null && !value.isEmpty()) {
            input.setText(value);
            input.setSelection(input.getText().length());
        }
        input.setTextColor(EchoTheme.textArgb(context));
        input.setHintTextColor(EchoTheme.mutedArgb(context));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        return input;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private String text(String key) {
        return AppLanguage.text(languageProvider.languageMode(), key);
    }
}
