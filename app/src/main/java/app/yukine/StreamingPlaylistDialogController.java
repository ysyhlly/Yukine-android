package app.yukine;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

import app.yukine.model.Track;
import app.yukine.streaming.StreamingPlaylist;
import app.yukine.streaming.StreamingProviderDescriptor;
import app.yukine.streaming.StreamingProviderName;
import app.yukine.streaming.StreamingTrack;
import app.yukine.ui.EchoDialog;
import app.yukine.ui.EchoTheme;

final class StreamingPlaylistDialogController {
    interface LanguageProvider {
        String languageMode();

        String text(String key);
    }

    interface Listener {
        void setStatus(String status);

        void runStreamingPlaylistImport(StreamingProviderName provider, String playlistName, List<Track> tracks);

        void importSelectedAccountPlaylists(StreamingProviderName provider, List<StreamingPlaylist> playlists);

        void previewAccountPlaylist(StreamingProviderName provider, StreamingPlaylist playlist);

        void importStreamingLikedTracks(StreamingProviderName provider);

        void importSelectedStreamingTracks(
                StreamingProviderName provider,
                String providerPlaylistId,
                String playlistName,
                List<StreamingTrack> tracks,
                boolean linkToProviderPlaylist
        );
    }

    private final Context context;
    private final StreamingViewModel streamingViewModel;
    private final LanguageProvider languageProvider;
    private final Listener listener;

    StreamingPlaylistDialogController(
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

    void showStreamingProviderPicker(String playlistName, List<Track> tracks) {
        StreamingProviderPickerRequest request = streamingViewModel.playlistOwner().prepareStreamingImportProviderPickerRequest(
                streamingViewModel.getStreaming().getValue().getProviders(),
                true,
                languageMode()
        );
        if (!request.getValid()) {
            listener.setStatus(request.getEmptyStatus());
            return;
        }
        EchoDialog.builder(context)
                .setTitle(request.getTitle())
                .setItems(request.getPickerState().getLabels(), (dialog, which) -> {
                    StreamingProviderDescriptor descriptor =
                            request.getPickerState().getProviders().get(which);
                    listener.runStreamingPlaylistImport(descriptor.getName(), playlistName, tracks);
                })
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    void showStreamingPlaylistLoadedDialog(String message) {
        EchoDialog.builder(context)
                .setTitle(streamingViewModel.playlistOwner().streamingPlaylistLoadedDialogTitle(languageMode()))
                .setMessage(message)
                .setPositiveButton(text("ok"), null)
                .show();
    }

    void showStreamingPlaylistImportPreview(
            StreamingProviderName provider,
            String providerPlaylistId,
            String playlistName,
            List<StreamingTrack> tracks
    ) {
        if (tracks == null || tracks.isEmpty()) {
            listener.setStatus(text("streaming.no.tracks.to.import"));
            return;
        }
        final ArrayList<StreamingTrack> available = new ArrayList<>();
        for (StreamingTrack track : tracks) {
            if (track != null && track.getProviderTrackId() != null
                    && !track.getProviderTrackId().trim().isEmpty()) {
                available.add(track);
            }
        }
        if (available.isEmpty()) {
            listener.setStatus(text("streaming.no.tracks.to.import"));
            return;
        }

        final ArrayList<CheckBox> boxes = new ArrayList<>();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int verticalPad = Math.round(6 * context.getResources().getDisplayMetrics().density);

        CheckBox selectAll = new CheckBox(context);
        selectAll.setChecked(true);
        selectAll.setText(text("library.select.all") + " · " + available.size());
        selectAll.setTextColor(EchoTheme.textArgb(context));
        selectAll.setButtonTintList(ColorStateList.valueOf(EchoTheme.accentArgb(context)));
        selectAll.setPadding(0, verticalPad, 0, verticalPad);
        list.addView(selectAll);

        for (StreamingTrack track : available) {
            CheckBox box = new CheckBox(context);
            box.setChecked(true);
            box.setText(streamingTrackLabel(track));
            box.setTextColor(EchoTheme.textArgb(context));
            box.setButtonTintList(ColorStateList.valueOf(EchoTheme.accentArgb(context)));
            box.setPadding(0, verticalPad, 0, verticalPad);
            boxes.add(box);
            list.addView(box, new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        selectAll.setOnCheckedChangeListener((button, checked) -> {
            for (CheckBox box : boxes) {
                box.setChecked(checked);
            }
        });

        String title = playlistName == null || playlistName.trim().isEmpty()
                ? text("streaming.import.playlist.from")
                : playlistName;
        EchoDialog.builder(context)
                .setTitle(title)
                .setMessage(available.size() + " " + text("songs"))
                .setView(list)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("streaming.account.playlists.import.confirm"), (dialog, which) -> {
                    ArrayList<StreamingTrack> selected = new ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) {
                        if (boxes.get(i).isChecked()) {
                            selected.add(available.get(i));
                        }
                    }
                    if (selected.isEmpty()) {
                        listener.setStatus(text("streaming.no.tracks.to.import"));
                        return;
                    }
                    listener.importSelectedStreamingTracks(
                            provider,
                            providerPlaylistId,
                            title,
                            selected,
                            selected.size() == available.size()
                    );
                })
                .show();
    }

    void showAccountPlaylistImportPicker(StreamingProviderName provider, List<StreamingPlaylist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            listener.setStatus(StreamingAccountPlaylistImportText.noAccountPlaylists(languageMode()));
            return;
        }
        final ArrayList<StreamingPlaylist> available = new ArrayList<>();
        for (StreamingPlaylist playlist : playlists) {
            if (playlist != null && playlist.getProviderPlaylistId() != null
                    && !playlist.getProviderPlaylistId().trim().isEmpty()) {
                available.add(playlist);
            }
        }
        if (available.isEmpty()) {
            listener.setStatus(StreamingAccountPlaylistImportText.noAccountPlaylists(languageMode()));
            return;
        }
        if (provider == StreamingProviderName.BILIBILI) {
            CharSequence[] labels = new CharSequence[available.size()];
            for (int i = 0; i < available.size(); i++) {
                labels[i] = accountPlaylistLabel(available.get(i));
            }
            EchoDialog.builder(context)
                    .setTitle(StreamingAccountPlaylistImportText.title(languageMode()))
                    .setMessage(StreamingAccountPlaylistImportText.message(languageMode()))
                    .setItems(labels, (dialog, which) ->
                            listener.previewAccountPlaylist(provider, available.get(which)))
                    .setNegativeButton(text("cancel"), null)
                    .show();
            return;
        }
        final ArrayList<CheckBox> boxes = new ArrayList<>();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int verticalPad = Math.round(8 * context.getResources().getDisplayMetrics().density);
        for (StreamingPlaylist playlist : available) {
            CheckBox box = new CheckBox(context);
            box.setChecked(true);
            box.setText(accountPlaylistLabel(playlist));
            box.setTextColor(EchoTheme.textArgb(context));
            box.setButtonTintList(ColorStateList.valueOf(EchoTheme.accentArgb(context)));
            box.setPadding(0, verticalPad, 0, verticalPad);
            boxes.add(box);
            list.addView(box, new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
        EchoDialog.builder(context)
                .setTitle(StreamingAccountPlaylistImportText.title(languageMode()))
                .setMessage(StreamingAccountPlaylistImportText.message(languageMode()))
                .setView(list)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(StreamingAccountPlaylistImportText.confirm(languageMode()),
                        (dialog, which) -> {
                            ArrayList<StreamingPlaylist> selected = new ArrayList<>();
                            for (int i = 0; i < boxes.size(); i++) {
                                if (boxes.get(i).isChecked()) {
                                    selected.add(available.get(i));
                                }
                            }
                            listener.importSelectedAccountPlaylists(provider, selected);
                        })
                .show();
    }

    void showImportStreamingFavoritesProviderPicker() {
        StreamingProviderPickerRequest request = streamingViewModel.playlistOwner().prepareStreamingImportProviderPickerRequest(
                streamingViewModel.getStreaming().getValue().getProviders(),
                false,
                languageMode()
        );
        if (!request.getValid()) {
            listener.setStatus(request.getEmptyStatus());
            return;
        }
        EchoDialog.builder(context)
                .setTitle(request.getTitle())
                .setItems(request.getPickerState().getLabels(), (dialog, which) ->
                        listener.importStreamingLikedTracks(request.getPickerState().getProviders().get(which).getName()))
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private String accountPlaylistLabel(StreamingPlaylist playlist) {
        String title = playlist.getTitle() == null || playlist.getTitle().trim().isEmpty()
                ? text("playlist")
                : playlist.getTitle();
        Integer count = playlist.getTrackCount();
        if (count == null || count < 0) {
            return title;
        }
        return title + " · " + count + StreamingAccountPlaylistImportText.trackCountSuffix(languageMode());
    }

    private String streamingTrackLabel(StreamingTrack track) {
        String title = track.getTitle() == null || track.getTitle().trim().isEmpty()
                ? track.getProviderTrackId()
                : track.getTitle();
        String artist = track.getArtist();
        if (artist == null || artist.trim().isEmpty()) {
            return title;
        }
        return title + "\n" + artist;
    }

    private String languageMode() {
        return languageProvider.languageMode();
    }

    private String text(String key) {
        return languageProvider.text(key);
    }
}
