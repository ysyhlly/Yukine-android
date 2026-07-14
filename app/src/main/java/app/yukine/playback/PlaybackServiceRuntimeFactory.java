package app.yukine.playback;

import javax.inject.Inject;

import app.yukine.StreamingRepositorySource;
import app.yukine.data.MusicLibraryRepository;
import app.yukine.streaming.StreamingPlaybackHeaderStore;

final class PlaybackServiceRuntimeFactory {
    private final MusicLibraryRepository repository;
    private final StreamingPlaybackHeaderStore streamingPlaybackHeaderStore;
    private final StreamingRepositorySource streamingRepositorySource;
    private final PlaybackPersistenceOwner persistenceOwner;

    @Inject
    PlaybackServiceRuntimeFactory(
            MusicLibraryRepository repository,
            StreamingPlaybackHeaderStore streamingPlaybackHeaderStore,
            StreamingRepositorySource streamingRepositorySource,
            PlaybackPersistenceOwner persistenceOwner
    ) {
        this.repository = repository;
        this.streamingPlaybackHeaderStore = streamingPlaybackHeaderStore;
        this.streamingRepositorySource = streamingRepositorySource;
        this.persistenceOwner = persistenceOwner;
    }

    PlaybackServiceRuntime create(EchoPlaybackService service) {
        return new PlaybackServiceRuntime(
                service,
                repository,
                streamingPlaybackHeaderStore,
                streamingRepositorySource,
                persistenceOwner
        );
    }
}
