package app.yukine.data;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import app.yukine.model.Track;

@RunWith(AndroidJUnit4.class)
public final class M3uPlaylistParserInstrumentedTest {
    @Test
    public void parseImportsHttpTracksAndDeduplicatesUrls() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,第一首\n"
                + "https://example.com/audio/a.mp3\n"
                + "#EXTINF:-1,重复标题不应覆盖\n"
                + "https://example.com/audio/a.mp3\n"
                + "#EXTINF:-1,第二首\n"
                + "http://example.com/audio/b.flac\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(2, tracks.size());
        assertEquals("第一首", tracks.get(0).title);
        assertEquals("https://example.com/audio/a.mp3", tracks.get(0).contentUri.toString());
        assertEquals("stream:https://example.com/audio/a.mp3", tracks.get(0).dataPath);
        assertEquals("第二首", tracks.get(1).title);
        assertEquals("http://example.com/audio/b.flac", tracks.get(1).contentUri.toString());
    }

    @Test
    public void parseFallsBackToFileNameWhenExtInfTitleIsMissing() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,\n"
                + "https://example.com/live/station.aac\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(1, tracks.size());
        assertEquals("station.aac", tracks.get(0).title);
        assertEquals("流媒体", tracks.get(0).artist);
        assertEquals("网络", tracks.get(0).album);
    }

    @Test
    public void parseRecognizesMixedCaseTagsAndHttpSchemes() {
        String playlist = "#EXTM3U\n"
                + "#extinf:-1,大小写曲目\n"
                + "HTTPS://example.com:443/live/MixedCase.mp3\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(1, tracks.size());
        assertEquals("大小写曲目", tracks.get(0).title);
        assertEquals("https://example.com/live/MixedCase.mp3", tracks.get(0).contentUri.toString());
        assertEquals("stream:https://example.com/live/MixedCase.mp3", tracks.get(0).dataPath);
    }

    @Test
    public void parseKeepsNonDefaultStreamPorts() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,端口曲目\n"
                + "http://example.com:8080/live/a.mp3\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(1, tracks.size());
        assertEquals("http://example.com:8080/live/a.mp3", tracks.get(0).contentUri.toString());
        assertEquals("stream:http://example.com:8080/live/a.mp3", tracks.get(0).dataPath);
    }

    @Test
    public void parsePreservesStreamUrlCredentials() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,认证曲目\n"
                + "HTTPS://user:pass@EXAMPLE.com:443/live/a.mp3\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(1, tracks.size());
        assertEquals("https://user:pass@example.com/live/a.mp3", tracks.get(0).contentUri.toString());
        assertEquals("stream:https://user:pass@example.com/live/a.mp3", tracks.get(0).dataPath);
    }

    @Test
    public void parseResolvesRelativeEntriesFromPlaylistUrl() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,相对曲目\n"
                + "../audio/song.mp3\n";

        List<Track> tracks = M3uPlaylistParser.parse(
                playlist,
                "https://example.com/music/lists/main.m3u"
        );

        assertEquals(1, tracks.size());
        assertEquals("相对曲目", tracks.get(0).title);
        assertEquals("https://example.com/music/audio/song.mp3", tracks.get(0).contentUri.toString());
    }

    @Test
    public void parseHlsPlaylistUrlAsSingleTrackWithoutSegments() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXTINF:10,\n"
                + "segment-001.ts\n"
                + "#EXTINF:10,\n"
                + "segment-002.ts\n";

        List<Track> tracks = M3uPlaylistParser.parse(
                playlist,
                "https://stream.example.com/live/channel.m3u8"
        );

        assertEquals(1, tracks.size());
        assertEquals("channel.m3u8", tracks.get(0).title);
        assertEquals("https://stream.example.com/live/channel.m3u8", tracks.get(0).contentUri.toString());
        assertEquals("stream:https://stream.example.com/live/channel.m3u8", tracks.get(0).dataPath);
    }

    @Test
    public void parseMixedCaseHlsTagAsSinglePlaylistTrack() {
        String playlist = "#EXTM3U\n"
                + "#ext-x-version:3\n"
                + "#EXTINF:10,\n"
                + "segment-001.ts\n";

        List<Track> tracks = M3uPlaylistParser.parse(
                playlist,
                "HTTPS://stream.example.com/live/channel.m3u8"
        );

        assertEquals(1, tracks.size());
        assertEquals("channel.m3u8", tracks.get(0).title);
        assertEquals("https://stream.example.com/live/channel.m3u8", tracks.get(0).contentUri.toString());
    }

    @Test
    public void parseLocalHlsTextWithoutUrlReturnsNoTracks() {
        String playlist = "#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:10,\n"
                + "segment-001.ts\n";

        List<Track> tracks = M3uPlaylistParser.parse(playlist, "");

        assertEquals(0, tracks.size());
    }

    @Test
    public void playlistNameUsesPlaylistTagBeforeFallback() {
        String playlist = "\uFEFF#EXTM3U\n"
                + "#playlist:收藏歌单\n"
                + "#EXTINF:-1,本地曲目\n"
                + "content://test/local-track\n";

        assertEquals("收藏歌单", M3uPlaylistParser.playlistName(playlist, "Fallback"));
        assertEquals("Fallback", M3uPlaylistParser.playlistName("#EXTM3U\n", "Fallback"));
        assertEquals("导入的播放列表", M3uPlaylistParser.playlistName("#EXTM3U\n", ""));
    }

    @Test
    public void parseLocalEntriesOnlyWhenPlaylistImportAllowsThem() {
        String playlist = "#EXTM3U\n"
                + "#EXTINF:-1,内容 URI 曲目\n"
                + "content://test/local-track\n"
                + "#EXTINF:-1,文件路径曲目\n"
                + "file:///storage/emulated/0/Music/local-track.mp3\n"
                + "#EXTINF:-1,重复文件路径\n"
                + "file:///storage/emulated/0/Music/local-track.mp3\n";

        List<Track> streamOnlyTracks = M3uPlaylistParser.parse(playlist, "", false);
        List<Track> playlistTracks = M3uPlaylistParser.parse(playlist, "", true);

        assertEquals(0, streamOnlyTracks.size());
        assertEquals(2, playlistTracks.size());
        assertEquals("内容 URI 曲目", playlistTracks.get(0).title);
        assertEquals("content://test/local-track", playlistTracks.get(0).dataPath);
        assertEquals("文件路径曲目", playlistTracks.get(1).title);
        assertEquals("file:///storage/emulated/0/Music/local-track.mp3", playlistTracks.get(1).dataPath);
    }
}
