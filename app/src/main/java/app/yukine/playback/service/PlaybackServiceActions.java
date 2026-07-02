package app.yukine.playback.service;

public final class PlaybackServiceActions {
    public static final String PLAY = "app.yukine.action.PLAY";
    public static final String PAUSE = "app.yukine.action.PAUSE";
    public static final String PREVIOUS = "app.yukine.action.PREVIOUS";
    public static final String NEXT = "app.yukine.action.NEXT";
    public static final String STOP = "app.yukine.action.STOP";
    public static final String TOGGLE_FAVORITE = "app.yukine.action.TOGGLE_FAVORITE";
    public static final String RESTORE = "app.yukine.action.RESTORE";
    public static final String RESTORE_AND_PLAY = "app.yukine.action.RESTORE_AND_PLAY";

    private PlaybackServiceActions() {
    }

    public static boolean isPlaybackServiceAction(String action) {
        return PLAY.equals(action)
                || PAUSE.equals(action)
                || PREVIOUS.equals(action)
                || NEXT.equals(action)
                || TOGGLE_FAVORITE.equals(action)
                || RESTORE.equals(action)
                || RESTORE_AND_PLAY.equals(action)
                || STOP.equals(action);
    }
}
