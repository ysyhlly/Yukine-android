package app.yukine;

import android.content.DialogInterface;
import android.os.Handler;

import androidx.activity.ComponentActivity;

import java.util.ArrayList;
import java.util.List;

import app.yukine.data.MusicLibraryRepository;
import app.yukine.identity.ArtistSourceMapping;
import app.yukine.identity.CanonicalArtist;
import app.yukine.ui.EchoDialog;

/** Activity-scoped UI owner for explicit, user-confirmed artist identity corrections. */
final class ArtistIdentityDialogController {
    private static final int MAX_MERGE_TARGETS = 100;

    private final ComponentActivity activity;
    private final Handler mainHandler;
    private final DialogLanguageProvider languageProvider;
    private final MusicLibraryRepository repository;
    private final Runnable identitySnapshotRefresher;
    private final Runnable libraryRefresher;
    private final StatusMessageController statusMessages;

    ArtistIdentityDialogController(
            ComponentActivity activity,
            Handler mainHandler,
            DialogLanguageProvider languageProvider,
            MusicLibraryRepository repository,
            Runnable identitySnapshotRefresher,
            Runnable libraryRefresher,
            StatusMessageController statusMessages
    ) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.languageProvider = languageProvider;
        this.repository = repository;
        this.identitySnapshotRefresher = identitySnapshotRefresher;
        this.libraryRefresher = libraryRefresher;
        this.statusMessages = statusMessages;
    }

    void show(String artistId, String artistName) {
        if (artistId == null || artistId.isBlank()) {
            return;
        }
        runInBackground("LoadArtistIdentityActions", () -> {
            List<CanonicalArtist> targets = repository.loadArtistMergeTargets(artistId, MAX_MERGE_TARGETS);
            List<ArtistSourceMapping> mappings = repository.loadArtistSourceMappings(artistId);
            mainHandler.post(() -> showActions(artistId, artistName, targets, mappings));
        });
    }

    private void showActions(
            String artistId,
            String artistName,
            List<CanonicalArtist> targets,
            List<ArtistSourceMapping> mappings
    ) {
        if (!canShowDialog()) {
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        if (!targets.isEmpty()) {
            labels.add(text("artist.identity.merge"));
            actions.add(() -> showMergeTargets(artistId, artistName, targets));
        }
        if (!mappings.isEmpty()) {
            labels.add(text("artist.identity.split"));
            actions.add(() -> showSplitMappings(mappings));
        }
        if (actions.isEmpty()) {
            statusMessages.showFeedback(text("artist.identity.none.merge") + " / " + text("artist.identity.none.split"));
            return;
        }
        EchoDialog.builder(activity)
                .setTitle(text("artist.identity.manage") + " · " + artistName)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void showMergeTargets(String sourceArtistId, String sourceName, List<CanonicalArtist> targets) {
        if (!canShowDialog()) {
            return;
        }
        String[] labels = targets.stream()
                .map(this::artistLabel)
                .toArray(String[]::new);
        EchoDialog.builder(activity)
                .setTitle(text("artist.identity.merge.choose"))
                .setItems(labels, (dialog, which) -> confirmMerge(sourceArtistId, sourceName, targets.get(which)))
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void confirmMerge(String sourceArtistId, String sourceName, CanonicalArtist target) {
        EchoDialog.builder(activity)
                .setTitle(text("artist.identity.merge"))
                .setMessage(text("artist.identity.merge.confirm") + " “" + target.getDisplayName() + "”？\n" + sourceName)
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), (dialog, which) -> runMutation(
                        "MergeArtistIdentity",
                        () -> repository.mergeArtistIdentities(sourceArtistId, target.getArtistId())
                ))
                .show();
    }

    private void showSplitMappings(List<ArtistSourceMapping> mappings) {
        if (!canShowDialog()) {
            return;
        }
        String[] labels = mappings.stream()
                .map(this::mappingLabel)
                .toArray(String[]::new);
        EchoDialog.builder(activity)
                .setTitle(text("artist.identity.split.choose"))
                .setItems(labels, (dialog, which) -> confirmSplit(mappings.get(which)))
                .setNegativeButton(text("cancel"), null)
                .show();
    }

    private void confirmSplit(ArtistSourceMapping mapping) {
        EchoDialog.builder(activity)
                .setTitle(text("artist.identity.split"))
                .setMessage(text("artist.identity.split.confirm") + "？\n" + mappingLabel(mapping))
                .setNegativeButton(text("cancel"), null)
                .setPositiveButton(text("ok"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        runMutation(
                                "SplitArtistIdentity",
                                () -> repository.splitArtistSourceMapping(mapping.getMappingId())
                        );
                    }
                })
                .show();
    }

    private void runMutation(String threadName, Runnable mutation) {
        runInBackground(threadName, () -> {
            try {
                mutation.run();
                identitySnapshotRefresher.run();
                mainHandler.post(() -> {
                    statusMessages.showFeedback(text("artist.identity.updated"));
                    libraryRefresher.run();
                });
            } catch (RuntimeException error) {
                mainHandler.post(() -> statusMessages.showFeedback(
                        text("artist.identity.update.failed") + ": " + error.getMessage()
                ));
            }
        });
    }

    private void runInBackground(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
    }

    private boolean canShowDialog() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private String artistLabel(CanonicalArtist artist) {
        String suffix = artist.getCountryCode().isBlank() ? "" : " · " + artist.getCountryCode();
        return artist.getDisplayName() + suffix;
    }

    private String mappingLabel(ArtistSourceMapping mapping) {
        String name = mapping.getDisplayName().isBlank() ? mapping.getProviderArtistId() : mapping.getDisplayName();
        return mapping.getProvider() + " · " + name + " (" + mapping.getProviderArtistId() + ")";
    }

    private String text(String key) {
        return AppLanguage.text(languageProvider.languageMode(), key);
    }
}
