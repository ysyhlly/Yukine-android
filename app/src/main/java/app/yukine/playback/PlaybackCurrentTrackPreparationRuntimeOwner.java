package app.yukine.playback;

import app.yukine.playback.manager.PlaybackRuntimeStateManager;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PlaybackCurrentTrackPreparationRuntimeOwner
        implements PlaybackCurrentTrackPreparationOwner.RuntimeStateController {
    private final Consumer<Boolean> setPreparing;
    private final Consumer<String> setErrorMessage;
    private final BooleanSupplier preparing;

    static PlaybackCurrentTrackPreparationRuntimeOwner fromRuntimeStateManager(
            PlaybackRuntimeStateManager runtimeStateManager
    ) {
        PlaybackRuntimeStateManager runtimeStateOwner =
                Objects.requireNonNull(runtimeStateManager, "runtimeStateManager");
        return new PlaybackCurrentTrackPreparationRuntimeOwner(
                runtimeStateOwner::setPreparing,
                runtimeStateOwner::setErrorMessage,
                runtimeStateOwner::preparing
        );
    }

    PlaybackCurrentTrackPreparationRuntimeOwner(
            Consumer<Boolean> setPreparing,
            Consumer<String> setErrorMessage,
            BooleanSupplier preparing
    ) {
        this.setPreparing = setPreparing;
        this.setErrorMessage = setErrorMessage;
        this.preparing = preparing;
    }

    @Override
    public void setPreparing(boolean preparing) {
        if (setPreparing != null) {
            setPreparing.accept(preparing);
        }
    }

    @Override
    public void setErrorMessage(String message) {
        if (setErrorMessage != null) {
            setErrorMessage.accept(message);
        }
    }

    void beginPreparing() {
        setPreparing(true);
    }

    void markPlaybackReady() {
        setPreparing(false);
        setErrorMessage("");
    }

    void markUnableToOpenCurrentTrack() {
        setPreparing(false);
        setErrorMessage("Unable to open this track.");
    }

    boolean preparing() {
        return preparing != null && preparing.getAsBoolean();
    }
}
