package app.yukine.model;

public final class LyricsLine {
    public final long timeMs;
    public final String text;

    public LyricsLine(long timeMs, String text) {
        this.timeMs = Math.max(timeMs, 0L);
        this.text = text == null ? "" : text.trim();
    }
}
