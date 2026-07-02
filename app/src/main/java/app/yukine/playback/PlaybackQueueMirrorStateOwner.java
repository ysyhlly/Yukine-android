package app.yukine.playback;

import app.yukine.playback.manager.PlaybackQueueRuntimeStateManager;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PlaybackQueueMirrorStateOwner {
    private final BooleanSupplier playerMirrorsQueue;
    private final Consumer<Boolean> setPlayerMirrorsQueue;

    static PlaybackQueueMirrorStateOwner fromRuntimeStateManager(
            PlaybackQueueRuntimeStateManager runtimeStateManager
    ) {
        return new PlaybackQueueMirrorStateOwner(
                runtimeStateManager == null ? null : runtimeStateManager::playerMirrorsQueue,
                runtimeStateManager == null ? null : runtimeStateManager::setPlayerMirrorsQueue
        );
    }

    PlaybackQueueMirrorStateOwner(
            BooleanSupplier playerMirrorsQueue,
            Consumer<Boolean> setPlayerMirrorsQueue
    ) {
        this.playerMirrorsQueue = playerMirrorsQueue;
        this.setPlayerMirrorsQueue = setPlayerMirrorsQueue;
    }

    boolean playerMirrorsQueue() {
        return playerMirrorsQueue != null && playerMirrorsQueue.getAsBoolean();
    }

    void setPlayerMirrorsQueue(boolean enabled) {
        if (setPlayerMirrorsQueue != null) {
            setPlayerMirrorsQueue.accept(enabled);
        }
    }
}
