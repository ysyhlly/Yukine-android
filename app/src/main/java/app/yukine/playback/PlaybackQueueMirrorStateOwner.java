package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueRuntimeStateManager;

final class PlaybackQueueMirrorStateOwner {
    interface MirrorStateOperations {
        boolean playerMirrorsQueue();

        void setPlayerMirrorsQueue(boolean enabled);
    }

    interface MirrorStateOperationsProvider {
        MirrorStateOperations mirrorStateOperations();
    }

    private final MirrorStateOperationsProvider mirrorStateOperationsProvider;

    static PlaybackQueueMirrorStateOwner fromRuntimeStateManager(
            PlaybackQueueRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackQueueMirrorStateOwner(
                () -> runtimeStateManager == null
                        ? null
                        : new PlaybackQueueRuntimeStateManagerOperations(runtimeStateManager)
        );
    }

    PlaybackQueueMirrorStateOwner(MirrorStateOperationsProvider mirrorStateOperationsProvider) {
        this.mirrorStateOperationsProvider = mirrorStateOperationsProvider;
    }

    boolean playerMirrorsQueue() {
        MirrorStateOperations operations = mirrorStateOperations();
        return operations != null && operations.playerMirrorsQueue();
    }

    void setPlayerMirrorsQueue(boolean enabled) {
        MirrorStateOperations operations = mirrorStateOperations();
        if (operations != null) {
            operations.setPlayerMirrorsQueue(enabled);
        }
    }

    private MirrorStateOperations mirrorStateOperations() {
        return mirrorStateOperationsProvider == null ? null : mirrorStateOperationsProvider.mirrorStateOperations();
    }

    private static final class PlaybackQueueRuntimeStateManagerOperations implements MirrorStateOperations {
        private final PlaybackQueueRuntimeStateManager runtimeStateManager;

        private PlaybackQueueRuntimeStateManagerOperations(
                PlaybackQueueRuntimeStateManager runtimeStateManager
        ) {
            this.runtimeStateManager = runtimeStateManager;
        }

        @Override
        public boolean playerMirrorsQueue() {
            return runtimeStateManager.playerMirrorsQueue();
        }

        @Override
        public void setPlayerMirrorsQueue(boolean enabled) {
            runtimeStateManager.setPlayerMirrorsQueue(enabled);
        }
    }
}
