package app.echo.next.data;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.echo.next.model.Playlist;
import app.echo.next.model.PlaylistImportResult;
import app.echo.next.model.PlaybackQueueState;
import app.echo.next.model.RemoteSource;
import app.echo.next.model.StreamImportResult;
import app.echo.next.model.Track;
import app.echo.next.model.TrackPlayRecord;
import app.echo.next.model.WebDavSyncResult;
import app.echo.next.StreamingQualityPreference;
import app.echo.next.streaming.StreamingPlaybackAdapter;
import app.echo.next.streaming.StreamingProviderName;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class MusicLibraryRepository {
    private final EchoDatabaseHelper database;
    private final MediaStoreMusicScanner scanner;
    private final DocumentMusicImporter documentImporter;
    private final AudioSpecParser audioSpecParser;
    private final WebDavClient webDavClient;

    @Inject
    public MusicLibraryRepository(@ApplicationContext Context context) {
        Context appContext = context.getApplicationContext();
        database = new EchoDatabaseHelper(appContext);
        scanner = new MediaStoreMusicScanner(appContext);
        documentImporter = new DocumentMusicImporter(appContext);
        audioSpecParser = new AudioSpecParser(appContext);
        webDavClient = new WebDavClient();
    }

    public List<Track> loadCachedTracks() {
        return database.loadTracks();
    }

    public PlaybackQueueState loadPlaybackQueue() {
        return new PlaybackQueueState(database.loadPlaybackQueueTracks(), database.loadPlaybackQueueIndex());
    }

    public void savePlaybackQueue(List<Track> tracks, int currentIndex) {
        database.savePlaybackQueue(tracks, currentIndex);
    }

    public long loadPlaybackPositionTrackId() {
        return database.loadPlaybackPositionTrackId();
    }

    public long loadPlaybackPositionMs() {
        return database.loadPlaybackPositionMs();
    }

    public void savePlaybackPosition(long trackId, long positionMs) {
        database.savePlaybackPosition(trackId, positionMs);
    }

    public boolean loadShuffleEnabled() {
        return database.loadShuffleEnabled();
    }

    public void saveShuffleEnabled(boolean enabled) {
        database.saveShuffleEnabled(enabled);
    }

    public int loadRepeatMode() {
        return normalizeRepeatMode(database.loadRepeatMode());
    }

    public void saveRepeatMode(int repeatMode) {
        database.saveRepeatMode(normalizeRepeatMode(repeatMode));
    }

    public List<RemoteSource> loadRemoteSources() {
        return database.loadRemoteSources();
    }

    public RemoteSource loadRemoteSource(long sourceId) {
        return database.loadRemoteSource(sourceId);
    }

    public long saveWebDavSource(String name, String baseUrl, String username, String password, String rootPath) {
        return saveWebDavSource(-1L, name, baseUrl, username, password, rootPath);
    }

    public long saveWebDavSource(long sourceId, String name, String baseUrl, String username, String password, String rootPath) {
        if (sourceId > 0L) {
            database.deleteRemoteSourceTracks(sourceId);
        }
        RemoteSource source = new RemoteSource(
                sourceId,
                RemoteSource.TYPE_WEBDAV,
                name,
                normalizeBaseUrl(baseUrl),
                username,
                password,
                rootPath,
                sourceId > 0L ? "已更新，等待测试" : "已保存，等待测试",
                System.currentTimeMillis()
        );
        return database.saveRemoteSource(source);
    }

    public String testRemoteSource(long sourceId) {
        RemoteSource source = database.loadRemoteSource(sourceId);
        if (source == null) {
            return "远程源不存在";
        }
        String status = webDavClient.test(source);
        database.updateRemoteSourceStatus(sourceId, status);
        return status;
    }

    public WebDavSyncResult syncRemoteSource(long sourceId) {
        RemoteSource source = database.loadRemoteSource(sourceId);
        if (source == null) {
            return new WebDavSyncResult(Collections.<Track>emptyList(), 0, 0, 0);
        }
        try {
            List<Track> oldTracks = database.loadRemoteSourceTracks(sourceId);
            List<Track> tracks = webDavClient.listAudioTracks(source);
            WebDavSyncResult result = syncResult(oldTracks, tracks);
            database.replaceRemoteSourceTracks(sourceId, tracks);
            database.updateRemoteSourceStatus(sourceId, "已同步 WebDAV：" + result.summary());
            return result;
        } catch (RuntimeException error) {
            database.updateRemoteSourceStatus(sourceId, "同步失败：" + safeMessage(error));
            throw error;
        }
    }

    private WebDavSyncResult syncResult(List<Track> oldTracks, List<Track> newTracks) {
        Set<String> oldPaths = dataPathSet(oldTracks);
        Set<String> newPaths = dataPathSet(newTracks);
        int kept = 0;
        for (String dataPath : oldPaths) {
            if (newPaths.contains(dataPath)) {
                kept++;
            }
        }
        int added = Math.max(0, newPaths.size() - kept);
        int removed = Math.max(0, oldPaths.size() - kept);
        return new WebDavSyncResult(newTracks, added, removed, kept);
    }

    private Set<String> dataPathSet(List<Track> tracks) {
        Set<String> paths = new java.util.HashSet<>();
        if (tracks == null) {
            return paths;
        }
        for (Track track : tracks) {
            if (track != null && track.dataPath != null && !track.dataPath.isEmpty()) {
                paths.add(track.dataPath);
            }
        }
        return paths;
    }

    public void deleteRemoteSource(long sourceId) {
        database.deleteRemoteSource(sourceId);
    }

    public Track addStreamUrl(String title, String url) {
        String cleanUrl = normalizeBaseUrl(url);
        if (cleanUrl.isEmpty()) {
            return null;
        }
        String cleanTitle = title == null || title.trim().isEmpty() ? "网络流媒体" : title.trim();
        Track track = M3uPlaylistParser.streamTrack(cleanTitle, cleanUrl);
        ArrayList<Track> tracks = new ArrayList<>();
        tracks.add(track);
        database.upsertTracks(tracks);
        return track;
    }

    public boolean streamUrlExists(String url) {
        String cleanUrl = normalizeBaseUrl(url);
        if (cleanUrl.isEmpty()) {
            return false;
        }
        String dataPath = "stream:" + cleanUrl;
        for (Track track : database.loadTracks()) {
            if (dataPath.equals(track.dataPath)) {
                return true;
            }
        }
        return false;
    }

    public Track updateStreamUrl(long oldTrackId, String title, String url) {
        String cleanUrl = normalizeBaseUrl(url);
        if (cleanUrl.isEmpty()) {
            return null;
        }
        String cleanTitle = title == null || title.trim().isEmpty() ? "网络流媒体" : title.trim();
        Track track = M3uPlaylistParser.streamTrack(cleanTitle, cleanUrl);
        database.replaceTrackAndMigrateReferences(oldTrackId, track);
        return track;
    }

    public String loadStreamingTrackMatch(Track track, String provider) {
        String cleanProvider = provider == null ? "" : provider;
        for (String key : streamingTrackMatchKeys(track)) {
            String match = database.loadStreamingTrackMatch(key, cleanProvider);
            if (match != null && !match.trim().isEmpty()) {
                return match.trim();
            }
        }
        return "";
    }

    public void saveStreamingTrackMatch(Track track, String provider, String providerTrackId) {
        String cleanProvider = provider == null ? "" : provider;
        String cleanProviderTrackId = providerTrackId == null ? "" : providerTrackId.trim();
        for (String key : streamingTrackMatchKeys(track)) {
            database.saveStreamingTrackMatch(key, cleanProvider, cleanProviderTrackId, track);
        }
    }

    private void cacheStreamingTrackMatches(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        for (Track track : tracks) {
            cacheStreamingTrackMatch(track);
        }
    }

    private void cacheStreamingTrackMatch(Track track) {
        if (track == null || track.dataPath == null || track.dataPath.isEmpty()) {
            return;
        }
        StreamingProviderName provider = StreamingPlaybackAdapter.INSTANCE.providerName(track.dataPath);
        if (provider != StreamingProviderName.NETEASE) {
            return;
        }
        String providerTrackId = StreamingPlaybackAdapter.INSTANCE.providerTrackId(track.dataPath);
        if (providerTrackId == null || providerTrackId.trim().isEmpty()) {
            return;
        }
        saveStreamingTrackMatch(track, provider.getWireName(), providerTrackId.trim());
    }

    private List<String> streamingTrackMatchKeys(Track track) {
        ArrayList<String> keys = new ArrayList<>();
        if (track == null) {
            return keys;
        }
        if (track.dataPath != null && !track.dataPath.trim().isEmpty()) {
            addStreamingTrackMatchKey(keys, "path:" + track.dataPath.trim());
        }
        if (track.contentUri != null && !Uri.EMPTY.equals(track.contentUri) && !track.contentUri.toString().trim().isEmpty()) {
            addStreamingTrackMatchKey(keys, "uri:" + track.contentUri.toString().trim());
        }
        if (track.id > 0L) {
            addStreamingTrackMatchKey(keys, "id:" + track.id);
        }
        String title = track.title == null ? "" : track.title.trim().toLowerCase(Locale.ROOT);
        String artist = track.artist == null ? "" : track.artist.trim().toLowerCase(Locale.ROOT);
        if (!title.isEmpty() || !artist.isEmpty()) {
            addStreamingTrackMatchKey(keys, "meta:" + title + "|" + artist + "|" + track.durationMs);
            addStreamingTrackMatchKey(keys, "meta:" + title + "|" + artist);
        }
        return keys;
    }

    private void addStreamingTrackMatchKey(ArrayList<String> keys, String key) {
        if (key != null && !key.isEmpty() && !keys.contains(key)) {
            keys.add(key);
        }
    }

    public List<Track> importM3uPlaylist(String playlistUrl) {
        return importM3uPlaylistWithResult(playlistUrl).tracks;
    }

    public StreamImportResult importM3uPlaylistWithResult(String playlistUrl) {
        String cleanUrl = normalizeBaseUrl(playlistUrl);
        if (cleanUrl.isEmpty()) {
            return emptyStreamImportResult();
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(cleanUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "ECHO-NEXT-Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return emptyStreamImportResult();
            }
            StringBuilder playlistText = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    playlistText.append(line).append('\n');
                }
            }
            return importParsedStreamTracks(M3uPlaylistParser.parse(playlistText.toString(), cleanUrl));
        } catch (Exception ignored) {
            return emptyStreamImportResult();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public List<Track> importM3uText(String playlistText) {
        return importM3uTextWithResult(playlistText).tracks;
    }

    public StreamImportResult importM3uTextWithResult(String playlistText) {
        return importParsedStreamTracks(M3uPlaylistParser.parse(playlistText, ""));
    }

    public PlaylistImportResult importM3uTextAsPlaylist(String playlistText, String fallbackName) {
        List<Track> parsedTracks = M3uPlaylistParser.parse(playlistText, "", true);
        String playlistName = M3uPlaylistParser.playlistName(playlistText, fallbackName);
        if (parsedTracks.isEmpty()) {
            return new PlaylistImportResult(-1L, playlistName, 0, 0, 0, 0);
        }
        long playlistId = ensurePlaylistNamed(playlistName);
        if (playlistId < 0L) {
            return new PlaylistImportResult(-1L, playlistName, parsedTracks.size(), 0, 0, parsedTracks.size());
        }

        List<Track> existingTracks = database.loadTracks();
        ArrayList<Track> newStreamTracks = new ArrayList<>();
        ArrayList<Track> playlistTracks = new ArrayList<>();
        Set<String> existingStreamPaths = streamDataPaths(existingTracks);
        int streamAddedCount = 0;
        int duplicateCount = 0;

        for (Track parsed : parsedTracks) {
            Track resolved = resolvePlaylistImportTrack(parsed, existingTracks);
            if (resolved == null && parsed.dataPath.startsWith("stream:")) {
                resolved = parsed;
                if (!existingStreamPaths.contains(parsed.dataPath)) {
                    newStreamTracks.add(parsed);
                    existingStreamPaths.add(parsed.dataPath);
                    existingTracks.add(parsed);
                    streamAddedCount++;
                }
            }
            if (resolved == null) {
                duplicateCount++;
                continue;
            }
            playlistTracks.add(resolved);
        }

        if (!newStreamTracks.isEmpty()) {
            database.upsertTracks(newStreamTracks);
        }

        int playlistAddedCount = 0;
        for (Track track : playlistTracks) {
            if (database.addTrackToPlaylist(playlistId, track.id)) {
                playlistAddedCount++;
            } else {
                duplicateCount++;
            }
        }

        return new PlaylistImportResult(
                playlistId,
                playlistName,
                parsedTracks.size(),
                streamAddedCount,
                playlistAddedCount,
                duplicateCount
        );
    }

    public int deleteAllStreams() {
        return database.deleteStreamTracks();
    }

    /**
     * Syncs a streaming-linked playlist: replaces its tracks with the given streaming tracks.
     * Upserts the placeholder tracks into the library and rebuilds the playlist membership.
     * Returns the number of tracks now in the playlist.
     */
    public int syncStreamingPlaylist(long playlistId, List<Track> streamingTracks) {
        if (streamingTracks == null) {
            return 0;
        }
        // Upsert all streaming tracks
        ArrayList<Track> toUpsert = new ArrayList<>();
        for (Track track : streamingTracks) {
            if (track != null && track.dataPath != null && track.dataPath.startsWith("streaming:")) {
                toUpsert.add(track);
            }
        }
        if (!toUpsert.isEmpty()) {
            database.upsertTracks(toUpsert);
        }
        cacheStreamingTrackMatches(streamingTracks);
        // Replace playlist tracks
        database.clearPlaylistTracks(playlistId);
        int added = 0;
        for (Track track : streamingTracks) {
            if (track != null && database.addTrackToPlaylist(playlistId, track.id)) {
                added++;
            }
        }
        return added;
    }

    /**
     * Imports a streaming playlist into the local library: upserts the (placeholder) streaming
     * tracks and adds them to a newly-created or existing local playlist with the given name.
     * The tracks are expected to use the "streaming:" dataPath prefix; their real playback URL is
     * resolved lazily at play time.
     */
    public PlaylistImportResult importStreamingPlaylist(String playlistName, List<Track> streamingTracks) {
        String cleanName = playlistName == null || playlistName.trim().isEmpty()
                ? "流媒体歌单"
                : playlistName.trim();
        if (streamingTracks == null || streamingTracks.isEmpty()) {
            return new PlaylistImportResult(-1L, cleanName, 0, 0, 0, 0);
        }
        long playlistId = ensurePlaylistNamed(cleanName);
        if (playlistId < 0L) {
            return new PlaylistImportResult(-1L, cleanName, streamingTracks.size(), 0, 0, streamingTracks.size());
        }

        List<Track> existingTracks = database.loadTracks();
        Set<String> existingDataPaths = dataPathSet(existingTracks);
        ArrayList<Track> newTracks = new ArrayList<>();
        int streamAddedCount = 0;
        for (Track track : streamingTracks) {
            if (track == null) {
                continue;
            }
            if (!existingDataPaths.contains(track.dataPath)) {
                newTracks.add(track);
                existingDataPaths.add(track.dataPath);
                streamAddedCount++;
            }
        }
        if (!newTracks.isEmpty()) {
            database.upsertTracks(newTracks);
        }
        cacheStreamingTrackMatches(streamingTracks);

        int playlistAddedCount = 0;
        int duplicateCount = 0;
        for (Track track : streamingTracks) {
            if (track == null) {
                continue;
            }
            if (database.addTrackToPlaylist(playlistId, track.id)) {
                playlistAddedCount++;
            } else {
                duplicateCount++;
            }
        }

        return new PlaylistImportResult(
                playlistId,
                cleanName,
                streamingTracks.size(),
                streamAddedCount,
                playlistAddedCount,
                duplicateCount
        );
    }

    public int deleteTrack(long trackId) {
        return database.deleteTrack(trackId);
    }

    public String loadThemeMode() {
        return database.loadThemeMode();
    }

    public void saveThemeMode(String mode) {
        database.saveThemeMode(mode);
    }

    public String loadAccentMode() {
        return database.loadAccentMode();
    }

    public void saveAccentMode(String mode) {
        database.saveAccentMode(mode);
    }

    public String loadLanguageMode() {
        return database.loadLanguageMode();
    }

    public void saveLanguageMode(String mode) {
        database.saveLanguageMode(mode);
    }

    public float loadPlaybackSpeed() {
        return normalizePlaybackSpeed(database.loadPlaybackSpeed());
    }

    public void savePlaybackSpeed(float speed) {
        database.savePlaybackSpeed(normalizePlaybackSpeed(speed));
    }

    public float loadAppVolume() {
        return normalizeAppVolume(database.loadAppVolume());
    }

    public void saveAppVolume(float volume) {
        database.saveAppVolume(normalizeAppVolume(volume));
    }

    public String loadStreamingAudioQuality() {
        return StreamingQualityPreference.normalize(database.loadStreamingAudioQuality());
    }

    public void saveStreamingAudioQuality(String quality) {
        database.saveStreamingAudioQuality(StreamingQualityPreference.normalize(quality));
    }

    public boolean loadOnlineLyricsEnabled() {
        return database.loadOnlineLyricsEnabled();
    }

    public void saveOnlineLyricsEnabled(boolean enabled) {
        database.saveOnlineLyricsEnabled(enabled);
    }

    public boolean loadConcurrentPlaybackEnabled() {
        return database.loadConcurrentPlaybackEnabled();
    }

    public void saveConcurrentPlaybackEnabled(boolean enabled) {
        database.saveConcurrentPlaybackEnabled(enabled);
    }

    public long loadLyricsOffsetMs() {
        return normalizeLyricsOffsetMs(database.loadLyricsOffsetMs());
    }

    public void saveLyricsOffsetMs(long offsetMs) {
        database.saveLyricsOffsetMs(normalizeLyricsOffsetMs(offsetMs));
    }

    public List<Track> refreshFromDevice() {
        List<Track> tracks = scanner.scan();
        database.replaceTracks(tracks);
        return database.loadTracks();
    }

    public List<Track> importAudioUris(List<Uri> uris) {
        List<Track> tracks = documentImporter.importAudioUris(uris);
        database.upsertTracks(tracks);
        return tracks;
    }

    public List<Track> importAudioTree(Uri treeUri) {
        List<Track> tracks = documentImporter.importAudioTree(treeUri);
        database.upsertTracks(tracks);
        return tracks;
    }

    public int parseMissingAudioSpecs() {
        List<Track> tracks = database.loadTracks();
        ArrayList<Track> enriched = new ArrayList<>();
        for (Track track : tracks) {
            if (track == null || !track.needsAudioSpecParsing()) {
                continue;
            }
            Track parsed = audioSpecParser.enrich(track);
            if (parsed != null && parsed.hasAudioSpec()) {
                enriched.add(parsed);
            }
        }
        return database.updateAudioSpecs(enriched);
    }

    public List<Track> search(List<Track> source, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(source);
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Track> matches = new ArrayList<>();
        for (Track track : source) {
            if (track.title.toLowerCase(Locale.ROOT).contains(normalized)
                    || track.artist.toLowerCase(Locale.ROOT).contains(normalized)
                    || track.album.toLowerCase(Locale.ROOT).contains(normalized)) {
                matches.add(track);
            }
        }
        return matches;
    }

    public void markPlayed(long trackId) {
        database.markPlayed(trackId);
    }

    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        return database.loadRecentlyPlayed(limit);
    }

    public List<TrackPlayRecord> loadPlayedSince(long startMs, int limit) {
        return database.loadPlayedSince(startMs, limit);
    }

    public List<TrackPlayRecord> loadMostPlayed(int limit) {
        return database.loadMostPlayed(limit);
    }

    public int clearPlayHistory() {
        return database.clearPlayHistory();
    }

    public void setFavorite(long trackId, boolean favorite) {
        database.setFavorite(trackId, favorite);
    }

    public void setFavorite(Track track, boolean favorite) {
        if (track == null) {
            return;
        }
        if (favorite && track.dataPath != null && track.dataPath.startsWith("streaming:")) {
            ArrayList<Track> tracks = new ArrayList<>();
            tracks.add(track);
            database.upsertTracks(tracks);
            cacheStreamingTrackMatches(tracks);
        }
        database.setFavorite(track.id, favorite);
    }

    public boolean isFavorite(long trackId) {
        return database.isFavorite(trackId);
    }

    public Set<Long> loadFavoriteIds() {
        return database.loadFavoriteIds();
    }

    public List<Track> loadFavoriteTracks() {
        return database.loadFavoriteTracks();
    }

    public List<Playlist> loadPlaylists() {
        return database.loadPlaylists();
    }

    public long createPlaylist(String name) {
        return database.createPlaylist(name);
    }

    private long ensurePlaylistNamed(String name) {
        long created = database.createPlaylist(name);
        if (created >= 0L) {
            return created;
        }
        for (Playlist playlist : database.loadPlaylists()) {
            if (playlist.name.equals(name)) {
                return playlist.id;
            }
        }
        return -1L;
    }

    public boolean renamePlaylist(long playlistId, String name) {
        return database.renamePlaylist(playlistId, name);
    }

    public boolean deletePlaylist(long playlistId) {
        return database.deletePlaylist(playlistId);
    }

    public long ensureDefaultPlaylist() {
        List<Playlist> playlists = database.loadPlaylists();
        if (!playlists.isEmpty()) {
            return playlists.get(0).id;
        }
        long created = database.createPlaylist("我的 ECHO 歌单");
        if (created != -1L) {
            return created;
        }
        playlists = database.loadPlaylists();
        return playlists.isEmpty() ? -1L : playlists.get(0).id;
    }

    public boolean addTrackToPlaylist(long playlistId, long trackId) {
        return database.addTrackToPlaylist(playlistId, trackId);
    }

    public void removeTrackFromPlaylist(long playlistId, long trackId) {
        database.removeTrackFromPlaylist(playlistId, trackId);
    }

    public boolean movePlaylistTrack(long playlistId, long trackId, int direction) {
        return database.movePlaylistTrack(playlistId, trackId, direction);
    }

    public boolean movePlaylistTrackAt(long playlistId, int trackIndex, int direction) {
        return database.movePlaylistTrackAt(playlistId, trackIndex, direction);
    }

    public List<Track> loadPlaylistTracks(long playlistId) {
        return database.loadPlaylistTracks(playlistId);
    }

    public List<Track> immutableCopy(List<Track> tracks) {
        return Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    private StreamImportResult importParsedStreamTracks(List<Track> parsedTracks) {
        if (parsedTracks == null || parsedTracks.isEmpty()) {
            return emptyStreamImportResult();
        }
        Set<String> existingDataPaths = streamDataPaths(database.loadTracks());
        ArrayList<Track> newTracks = new ArrayList<>();
        int duplicateCount = 0;
        for (Track track : parsedTracks) {
            if (existingDataPaths.contains(track.dataPath)) {
                duplicateCount++;
            } else {
                newTracks.add(track);
                existingDataPaths.add(track.dataPath);
            }
        }
        database.upsertTracks(newTracks);
        return new StreamImportResult(newTracks, parsedTracks.size(), newTracks.size(), duplicateCount);
    }

    private Track resolvePlaylistImportTrack(Track parsed, List<Track> existingTracks) {
        if (parsed == null || existingTracks == null) {
            return null;
        }
        if (parsed.dataPath.startsWith("stream:")) {
            for (Track track : existingTracks) {
                if (parsed.dataPath.equals(track.dataPath)) {
                    return track;
                }
            }
            return null;
        }
        String location = parsed.dataPath == null ? "" : parsed.dataPath.trim();
        if (location.isEmpty()) {
            return null;
        }
        String filePath = "";
        try {
            Uri uri = Uri.parse(location);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                filePath = uri.getPath() == null ? "" : uri.getPath();
            }
        } catch (Exception ignored) {
            filePath = "";
        }
        for (Track track : existingTracks) {
            if (location.equals(track.dataPath)
                    || location.equals(track.contentUri.toString())
                    || (!filePath.isEmpty() && filePath.equals(track.dataPath))) {
                return track;
            }
        }
        return null;
    }

    private Set<String> streamDataPaths(List<Track> tracks) {
        java.util.HashSet<String> paths = new java.util.HashSet<>();
        if (tracks == null) {
            return paths;
        }
        for (Track track : tracks) {
            if (track.dataPath.startsWith("stream:")) {
                paths.add(track.dataPath);
            }
        }
        return paths;
    }

    private StreamImportResult emptyStreamImportResult() {
        return new StreamImportResult(Collections.<Track>emptyList(), 0, 0, 0);
    }

    private String normalizeBaseUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return M3uPlaylistParser.normalizeStreamUrl(trimmed);
        }
        return trimmed.isEmpty() ? "" : M3uPlaylistParser.normalizeStreamUrl("https://" + trimmed);
    }

    private float normalizePlaybackSpeed(float speed) {
        if (speed < 0.5f) {
            return 0.5f;
        }
        if (speed > 2.0f) {
            return 2.0f;
        }
        return Math.round(speed * 100.0f) / 100.0f;
    }

    private float normalizeAppVolume(float volume) {
        if (volume < 0.0f) {
            return 0.0f;
        }
        if (volume > 1.0f) {
            return 1.0f;
        }
        return Math.round(volume * 100.0f) / 100.0f;
    }

    private long normalizeLyricsOffsetMs(long offsetMs) {
        if (offsetMs < -5000L) {
            return -5000L;
        }
        if (offsetMs > 5000L) {
            return 5000L;
        }
        return Math.round(offsetMs / 100.0) * 100L;
    }

    private int normalizeRepeatMode(int repeatMode) {
        if (repeatMode < 0 || repeatMode > 2) {
            return 0;
        }
        return repeatMode;
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
    }

}
