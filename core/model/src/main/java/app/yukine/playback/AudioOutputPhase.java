package app.yukine.playback;

/** Lifecycle of the currently selected audio output. */
public enum AudioOutputPhase {
    IDLE,
    NEGOTIATING,
    ACTIVE,
    FALLBACK,
    ERROR
}
