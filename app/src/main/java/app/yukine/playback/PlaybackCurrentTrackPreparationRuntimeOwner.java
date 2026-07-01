package app.yukine.playback;

import app.yukine.playback.manager.PlaybackRuntimeStateManager;

final class PlaybackCurrentTrackPreparationRuntimeOwner
        implements PlaybackCurrentTrackPreparationOwner.RuntimeStateController {
    interface RuntimeStateOperations {
        void setPreparing(boolean preparing);

        void setErrorMessage(String message);

        boolean preparing();
    }

    interface RuntimeStateOperationsProvider {
        RuntimeStateOperations runtimeStateOperations();
    }

    private final RuntimeStateOperationsProvider runtimeStateOperationsProvider;

    static PlaybackCurrentTrackPreparationRuntimeOwner fromRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackCurrentTrackPreparationRuntimeOwner(
                () -> runtimeStateManager == null
                        ? null
                        : new PlaybackRuntimeStateManagerOperations(runtimeStateManager)
        );
    }

    PlaybackCurrentTrackPreparationRuntimeOwner(RuntimeStateOperationsProvider runtimeStateOperationsProvider) {
        this.runtimeStateOperationsProvider = runtimeStateOperationsProvider;
    }

    @Override
    public void setPreparing(boolean preparing) {
        RuntimeStateOperations runtimeStateOperations = runtimeStateOperations();
        if (runtimeStateOperations != null) {
            runtimeStateOperations.setPreparing(preparing);
        }
    }

    @Override
    public void setErrorMessage(String message) {
        RuntimeStateOperations runtimeStateOperations = runtimeStateOperations();
        if (runtimeStateOperations != null) {
            runtimeStateOperations.setErrorMessage(message);
        }
    }

    void beginPreparing() {
        setPreparing(true);
    }

    void markUnableToOpenCurrentTrack() {
        RuntimeStateOperations runtimeStateOperations = runtimeStateOperations();
        if (runtimeStateOperations != null) {
            runtimeStateOperations.setPreparing(false);
            runtimeStateOperations.setErrorMessage("Unable to open this track.");
        }
    }

    boolean preparing() {
        RuntimeStateOperations runtimeStateOperations = runtimeStateOperations();
        return runtimeStateOperations != null && runtimeStateOperations.preparing();
    }

    private RuntimeStateOperations runtimeStateOperations() {
        return runtimeStateOperationsProvider == null ? null : runtimeStateOperationsProvider.runtimeStateOperations();
    }

    private static final class PlaybackRuntimeStateManagerOperations implements RuntimeStateOperations {
        private final PlaybackRuntimeStateManager runtimeStateManager;

        private PlaybackRuntimeStateManagerOperations(PlaybackRuntimeStateManager runtimeStateManager) {
            this.runtimeStateManager = runtimeStateManager;
        }

        @Override
        public void setPreparing(boolean preparing) {
            runtimeStateManager.setPreparing(preparing);
        }

        @Override
        public void setErrorMessage(String message) {
            runtimeStateManager.setErrorMessage(message);
        }

        @Override
        public boolean preparing() {
            return runtimeStateManager.preparing();
        }
    }
}
