package app.yukine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import app.yukine.model.Track;
import app.yukine.identity.LyricSourceBinding;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public final class LyricsRepositoryTest {
    @Test
    public void successfulSidecarLyricsPersistCanonicalProviderBinding() throws Exception {
        Path directory = Files.createTempDirectory("echo-lyrics-binding");
        Path audio = directory.resolve("song.flac");
        Path lyrics = directory.resolve("song.lrc");
        Files.write(audio, new byte[]{0});
        Files.write(lyrics, "[00:01.00]第一行\n[00:02.00]第二行\n".getBytes(StandardCharsets.UTF_8));
        ArrayList<LyricSourceBinding> saved = new ArrayList<>();
        LyricsRepository repository = new LyricsRepository(new LyricsRepository.BindingStore() {
            @Override
            public List<LyricSourceBinding> load(long trackId) {
                return Collections.emptyList();
            }

            @Override
            public void save(long trackId, LyricSourceBinding binding) {
                assertEquals(7L, trackId);
                saved.add(binding);
            }
        });
        Track track = new Track(
                7L,
                "Song",
                "Artist",
                "Album",
                120000L,
                Uri.EMPTY,
                audio.toString()
        );

        assertEquals(2, repository.loadForTrack(track, false, "").size());
        assertEquals(1, saved.size());
        assertEquals("local", saved.get(0).getProvider());
        assertEquals(lyrics.toFile().getAbsolutePath(), saved.get(0).getProviderLyricId());
        assertEquals(64, saved.get(0).getChecksum().length());
        assertTrue(saved.get(0).getSynced());
    }

    @Test
    public void neteaseSearchQueriesTryTitleOnlyAndArtistTitleFallbacks() throws Exception {
        LyricsRepository repository = new LyricsRepository();
        Method method = LyricsRepository.class.getDeclaredMethod("neteaseSearchQueries", Track.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> queries = (List<String>) method.invoke(
                repository,
                track("君と (Live Version)", "あたらよ", "Album")
        );

        assertTrue(queries.contains("君と (Live Version) あたらよ"));
        assertTrue(queries.contains("君と (Live Version)"));
        assertTrue(queries.contains("君と あたらよ"));
        assertTrue(queries.contains("君と"));
        assertTrue(queries.contains("あたらよ 君と (Live Version)"));
    }

    @Test
    public void neteaseMatchScoreAcceptsSongAliasesAndStrippedVersionTitle() throws Exception {
        LyricsRepository repository = new LyricsRepository();
        Method method = LyricsRepository.class.getDeclaredMethod("neteaseMatchScore", Track.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject song = new JSONObject()
                .put("name", "君と")
                .put("alia", new org.json.JSONArray().put("君と Live"))
                .put("ar", new org.json.JSONArray().put(new JSONObject().put("name", "あたらよ")))
                .put("dt", 229000L);

        int score = (Integer) method.invoke(
                repository,
                track("君と (Live Version)", "あたらよ", ""),
                song
        );

        assertTrue(score >= 95);
    }

    @Test
    public void stripVersionTextRemovesCommonSuffixes() throws Exception {
        LyricsRepository repository = new LyricsRepository();
        Method method = LyricsRepository.class.getDeclaredMethod("stripVersionText", String.class);
        method.setAccessible(true);

        assertEquals("Song", method.invoke(repository, "Song (Live Version)"));
        assertEquals("Song", method.invoke(repository, "Song [Remix]"));
        assertEquals("Song", method.invoke(repository, "Song feat. Someone"));
    }

    @Test
    public void neteaseSongIdIsExtractedBeforeLyricsRequest() throws Exception {
        LyricsRepository repository = new LyricsRepository();
        Method method = LyricsRepository.class.getDeclaredMethod("neteaseSongId", String.class);
        method.setAccessible(true);

        assertEquals("260600", method.invoke(repository, "260600"));
        assertEquals("260601", method.invoke(repository, "https://music.163.com/#/song?id=260601"));
        assertEquals("260602", method.invoke(repository, "songId=260602"));
        assertEquals("", method.invoke(repository, "wrong-provider-id"));
    }

    @Test
    public void neteaseProviderTrackIdReadsSongIdFromStreamingDataPath() throws Exception {
        LyricsRepository repository = new LyricsRepository();
        Method method = LyricsRepository.class.getDeclaredMethod("neteaseProviderTrackId", Track.class);
        method.setAccessible(true);

        Track track = new Track(
                1L,
                "Song",
                "Artist",
                "Album",
                1000L,
                Uri.EMPTY,
                "streaming:netease:260603?quality=lossless"
        );

        assertEquals("260603", method.invoke(repository, track));
    }

    private static Track track(String title, String artist, String album) {
        return new Track(1L, title, artist, album, 229000L, Uri.EMPTY, "file:track.mp3");
    }
}
