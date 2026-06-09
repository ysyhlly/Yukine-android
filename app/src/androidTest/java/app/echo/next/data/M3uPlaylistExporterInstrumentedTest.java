package app.echo.next.data;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import app.echo.next.model.Track;

@RunWith(AndroidJUnit4.class)
public final class M3uPlaylistExporterInstrumentedTest {
    @Test
    public void buildM3u8PlaylistExportsSupportedTrackLocations() {
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(new Track(
                1L,
                "Stream\nTitle",
                "Stream\rArtist",
                "Network",
                125000L,
                Uri.parse("https://example.com/live/a.mp3"),
                "stream:https://example.com/live/a.mp3",
                0L,
                null
        ));
        tracks.add(new Track(
                2L,
                "Document Track",
                "Local Artist",
                "Local",
                0L,
                Uri.parse("content://test/document-track"),
                "document:content://test/document-track",
                0L,
                null
        ));
        tracks.add(new Track(
                3L,
                "Scanned Track",
                "Device Artist",
                "Device",
                92000L,
                Uri.parse("content://media/external/audio/media/3"),
                "/storage/emulated/0/Music/scanned-track.mp3",
                0L,
                null
        ));
        tracks.add(new Track(
                4L,
                "WebDAV Track",
                "Remote Artist",
                "Remote",
                61000L,
                Uri.parse("https://dav.example.com/music/webdav.flac"),
                "webdav:42:https://dav.example.com/music/webdav.flac",
                0L,
                null
        ));

        String playlist = M3uPlaylistExporter.buildM3u8Playlist("My\nPlaylist", tracks);

        assertEquals("#EXTM3U\n"
                + "#PLAYLIST:My Playlist\n"
                + "#EXTINF:125,Stream Artist - Stream Title\n"
                + "https://example.com/live/a.mp3\n"
                + "#EXTINF:-1,Local Artist - Document Track\n"
                + "content://test/document-track\n"
                + "#EXTINF:92,Device Artist - Scanned Track\n"
                + "/storage/emulated/0/Music/scanned-track.mp3\n"
                + "#EXTINF:61,Remote Artist - WebDAV Track\n"
                + "https://dav.example.com/music/webdav.flac\n", playlist);
    }

    @Test
    public void safeExportFileNameRemovesUnsafePathCharacters() {
        assertEquals("Rock _ Pop_ 2026", M3uPlaylistExporter.safeExportFileName("Rock / Pop: 2026"));
        assertEquals("ECHO playlist", M3uPlaylistExporter.safeExportFileName("\n"));
    }
}
