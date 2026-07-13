package app.yukine.data;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

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

import app.yukine.model.Playlist;
import app.yukine.model.PlaylistImportResult;
import app.yukine.model.PlaybackQueueState;
import app.yukine.model.RemoteSource;
import app.yukine.model.StreamImportResult;
import app.yukine.model.Track;
import app.yukine.model.TrackPlayRecord;
import app.yukine.model.WebDavSyncResult;
import app.yukine.PageBackgrounds;
import app.yukine.LibraryRefreshPhase;
import app.yukine.LibraryRefreshProgress;
import app.yukine.LibraryRefreshProgressListener;
import app.yukine.playback.AudioEffectSettings;
import app.yukine.streaming.StreamingQualityPreference;
import app.yukine.TrackShareStyle;
import app.yukine.common.StreamingDataPathParser;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class MusicLibraryRepository {
    private static final String TAG = "MusicLibraryRepository";
    private static final int AUDIO_SPEC_UPDATE_BATCH_SIZE = 24;

    private final EchoDatabaseHelper database;
    private final EchoSettingsStore settingsStore;
    private final MediaStoreMusicScanner scanner;
    private final DocumentMusicImporter documentImporter;
    private final AudioSpecParser audioSpecParser;
    private final WebDavClient webDavClient;
    private final StreamingDataPathParser streamingDataPathParser;

    @Inject
    public MusicLibraryRepository(@ApplicationContext Context context, StreamingDataPathParser streamingDataPathParser) {
        Context appContext = context.getApplicationContext();
        database = new EchoDatabaseHelper(appContext);
        settingsStore = new EchoSettingsStore(database);
        scanner = new MediaStoreMusicScanner(appContext);
        documentImporter = new DocumentMusicImporter(appContext);
        audioSpecParser = new AudioSpecParser(appContext);
        webDavClient = new WebDavClient();
        this.streamingDataPathParser = streamingDataPathParser;
    }

    public List<Track> loadCachedTracks() {
        return database.loadTracks();
    }

    public List<Track> loadRecentlyAdded(int limit) {
        return database.loadRecentlyAdded(limit);
    }

    public List<Track> loadLongUnplayed(int limit) {
        return database.loadLongUnplayed(limit);
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

    public boolean loadPlaybackResumeRequested() {
        return settingsStore.loadPlaybackResumeRequested();
    }

    public void savePlaybackResumeRequested(boolean requested) {
        settingsStore.savePlaybackResumeRequested(requested);
    }

    public AudioEffectSettings loadAudioEffectSettings() {
        return settingsStore.loadAudioEffectSettings();
    }

    public void saveAudioEffectSettings(AudioEffectSettings settings) {
        settingsStore.saveAudioEffectSettings(settings);
    }

    public boolean loadStatusBarLyricsEnabled() {
        return settingsStore.loadStatusBarLyricsEnabled();
    }

    public void saveStatusBarLyricsEnabled(boolean enabled) {
        settingsStore.saveStatusBarLyricsEnabled(enabled);
    }

    public boolean loadSystemMediaLyricsTitleEnabled() {
        return settingsStore.loadSystemMediaLyricsTitleEnabled();
    }

    public void saveSystemMediaLyricsTitleEnabled(boolean enabled) {
        settingsStore.saveSystemMediaLyricsTitleEnabled(enabled);
    }

    public boolean loadFloatingLyricsEnabled() {
        return settingsStore.loadFloatingLyricsEnabled();
    }

    public void saveFloatingLyricsEnabled(boolean enabled) {
        settingsStore.saveFloatingLyricsEnabled(enabled);
    }

    public boolean loadNowPlayingGesturesEnabled() {
        return settingsStore.loadNowPlayingGesturesEnabled();
    }

    public void saveNowPlayingGesturesEnabled(boolean enabled) {
        settingsStore.saveNowPlayingGesturesEnabled(enabled);
    }

    public boolean loadPlaybackRestoreEnabled() {
        return settingsStore.loadPlaybackRestoreEnabled();
    }

    public void savePlaybackRestoreEnabled(boolean enabled) {
        settingsStore.savePlaybackRestoreEnabled(enabled);
    }

    public boolean loadReplayGainEnabled() {
        return settingsStore.loadReplayGainEnabled();
    }

    public void saveReplayGainEnabled(boolean enabled) {
        settingsStore.saveReplayGainEnabled(enabled);
    }

    public boolean loadDebugPromptsEnabled() {
        return settingsStore.loadDebugPromptsEnabled();
    }

    public void saveDebugPromptsEnabled(boolean enabled) {
        settingsStore.saveDebugPromptsEnabled(enabled);
    }

    public boolean loadCustomBackgroundBlurEnabled() {
        return settingsStore.loadCustomBackgroundBlurEnabled();
    }

    public float loadCustomBackgroundBlurRadiusDp() {
        return settingsStore.loadCustomBackgroundBlurRadiusDp();
    }

    public void saveCustomBackgroundBlurEnabled(boolean enabled) {
        settingsStore.saveCustomBackgroundBlurEnabled(enabled);
    }

    public void saveCustomBackgroundBlurRadiusDp(float radiusDp) {
        settingsStore.saveCustomBackgroundBlurRadiusDp(radiusDp);
    }

    public boolean loadGlassBlurEnabled() {
        return settingsStore.loadGlassBlurEnabled();
    }

    public float loadGlassBlurRadiusDp() {
        return settingsStore.loadGlassBlurRadiusDp();
    }

    public void saveGlassBlurEnabled(boolean enabled) {
        settingsStore.saveGlassBlurEnabled(enabled);
    }

    public void saveGlassBlurRadiusDp(float radiusDp) {
        settingsStore.saveGlassBlurRadiusDp(radiusDp);
    }

    public float loadGlassSurfaceOpacity() {
        return settingsStore.loadGlassSurfaceOpacity();
    }

    public void saveGlassSurfaceOpacity(float opacity) {
        settingsStore.saveGlassSurfaceOpacity(opacity);
    }

    public String loadShareStyle() {
        return settingsStore.loadShareStyle();
    }

    public void saveShareStyle(String style) {
        settingsStore.saveShareStyle(style);
    }

    public PageBackgrounds loadPageBackgrounds() {
        return settingsStore.loadPageBackgrounds();
    }

    public void savePageBackgrounds(PageBackgrounds backgrounds) {
        settingsStore.savePageBackgrounds(backgrounds);
    }

    public boolean loadOnboardingCompleted() {
        return settingsStore.loadOnboardingCompleted();
    }

    public void saveOnboardingCompleted(boolean completed) {
        settingsStore.saveOnboardingCompleted(completed);
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
        // 使用 SQL EXISTS 查询代替全表加载，O(1) 而非 O(n)。
        return database.trackExistsByDataPath(dataPath);
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
        String providerName = streamingDataPathParser.providerName(track.dataPath);
        if (!"netease".equals(providerName)) {
            return;
        }
        String providerTrackId = streamingDataPathParser.providerTrackId(track.dataPath);
        if (providerTrackId == null || providerTrackId.trim().isEmpty()) {
            return;
        }
        saveStreamingTrackMatch(track, providerName, providerTrackId.trim());
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
            connection.setRequestProperty("User-Agent", "Yukine-Android");
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

    public int hideTracks(List<Track> tracks) {
        return database.hideTracks(tracks);
    }

    public List<LibraryExclusion> loadLibraryExclusions() {
        return database.loadLibraryExclusions();
    }

    public boolean restoreLibraryExclusion(String sourceKey) {
        return database.restoreLibraryExclusion(sourceKey);
    }

    public int restoreAllLibraryExclusions() {
        return database.restoreAllLibraryExclusions();
    }

    public String loadThemeMode() {
        return settingsStore.loadThemeMode();
    }

    public void saveThemeMode(String mode) {
        settingsStore.saveThemeMode(mode);
    }

    public String loadAccentMode() {
        return settingsStore.loadAccentMode();
    }

    public void saveAccentMode(String mode) {
        settingsStore.saveAccentMode(mode);
    }

    public String loadLanguageMode() {
        return settingsStore.loadLanguageMode();
    }

    public void saveLanguageMode(String mode) {
        settingsStore.saveLanguageMode(mode);
    }

    public float loadPlaybackSpeed() {
        return normalizePlaybackSpeed(settingsStore.loadPlaybackSpeed());
    }

    public void savePlaybackSpeed(float speed) {
        settingsStore.savePlaybackSpeed(normalizePlaybackSpeed(speed));
    }

    public float loadAppVolume() {
        return normalizeAppVolume(settingsStore.loadAppVolume());
    }

    public void saveAppVolume(float volume) {
        settingsStore.saveAppVolume(normalizeAppVolume(volume));
    }

    public String loadStreamingAudioQuality() {
        return StreamingQualityPreference.normalize(settingsStore.loadStreamingAudioQuality());
    }

    public void saveStreamingAudioQuality(String quality) {
        settingsStore.saveStreamingAudioQuality(StreamingQualityPreference.normalize(quality));
    }

    public boolean loadRefuseAutomaticQualityDowngrade() {
        return settingsStore.loadRefuseAutomaticQualityDowngrade();
    }

    public void saveRefuseAutomaticQualityDowngrade(boolean refuse) {
        settingsStore.saveRefuseAutomaticQualityDowngrade(refuse);
    }

    public boolean loadOnlineLyricsEnabled() {
        return settingsStore.loadOnlineLyricsEnabled();
    }

    public void saveOnlineLyricsEnabled(boolean enabled) {
        settingsStore.saveOnlineLyricsEnabled(enabled);
    }

    public boolean loadConcurrentPlaybackEnabled() {
        return settingsStore.loadConcurrentPlaybackEnabled();
    }

    public void saveConcurrentPlaybackEnabled(boolean enabled) {
        settingsStore.saveConcurrentPlaybackEnabled(enabled);
    }

    public long loadLyricsOffsetMs() {
        return normalizeLyricsOffsetMs(settingsStore.loadLyricsOffsetMs());
    }

    public void saveLyricsOffsetMs(long offsetMs) {
        settingsStore.saveLyricsOffsetMs(normalizeLyricsOffsetMs(offsetMs));
    }

    public List<Track> refreshFromDevice() {
        return refreshFromDevice(null);
    }

    /**
     * Refreshes MediaStore-backed tracks while keeping the existing replacement transaction
     * atomic. Progress is phase-level on purpose: it makes slow scans diagnosable without
     * creating high-frequency UI updates for large libraries.
     */
    public List<Track> refreshFromDevice(LibraryRefreshProgressListener progressListener) {
        final long startedAtNanos = System.nanoTime();
        reportRefreshProgress(progressListener, LibraryRefreshPhase.CHECKING, -1, startedAtNanos);
        throwIfRefreshInterrupted();
        long generation = scanner.generation();
        if (generation >= 0L && generation == database.loadMediaStoreGeneration()) {
            // The full DB list also contains document, stream, streaming and WebDAV rows, so it
            // remains the single source of truth while avoiding a redundant MediaStore rewrite.
            reportRefreshProgress(progressListener, LibraryRefreshPhase.RELOADING, -1, startedAtNanos);
            List<Track> cachedTracks = database.loadTracks();
            reportRefreshCompleted(cachedTracks.size(), startedAtNanos, true);
            return cachedTracks;
        }
        reportRefreshProgress(progressListener, LibraryRefreshPhase.SCANNING, -1, startedAtNanos);
        List<Track> tracks = scanner.scan();
        throwIfRefreshInterrupted();
        reportRefreshProgress(progressListener, LibraryRefreshPhase.REPLACING, tracks.size(), startedAtNanos);
        database.replaceTracks(tracks);
        throwIfRefreshInterrupted();
        // Persist only after the replacement transaction succeeds. If the scan or write fails,
        // the old token forces a safe retry next time.
        database.saveMediaStoreGeneration(generation);
        reportRefreshProgress(progressListener, LibraryRefreshPhase.RELOADING, tracks.size(), startedAtNanos);
        List<Track> refreshedTracks = database.loadTracks();
        reportRefreshCompleted(refreshedTracks.size(), startedAtNanos, false);
        return refreshedTracks;
    }

    private static void reportRefreshProgress(
            LibraryRefreshProgressListener listener,
            LibraryRefreshPhase phase,
            int trackCount,
            long startedAtNanos
    ) {
        long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        if (listener != null) {
            listener.onProgress(new LibraryRefreshProgress(phase, trackCount, elapsedMs));
        }
        Log.i(
                TAG,
                "Device library refresh phase=" + phase
                        + " tracks=" + trackCount
                        + " elapsedMs=" + elapsedMs
        );
    }

    private static void reportRefreshCompleted(int trackCount, long startedAtNanos, boolean reusedCachedRows) {
        long elapsedMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        Log.i(
                TAG,
                "Device library refresh completed tracks=" + trackCount
                        + " reusedCachedRows=" + reusedCachedRows
                        + " elapsedMs=" + elapsedMs
        );
    }

    private static void throwIfRefreshInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Device library refresh cancelled");
        }
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
        List<Track> tracks = database.loadTracksNeedingAudioSpecs(Integer.MAX_VALUE);
        ArrayList<Track> enriched = new ArrayList<>();
        int updated = 0;
        for (Track track : tracks) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Audio spec parsing cancelled");
            }
            if (track == null || !track.needsAudioSpecParsing()) {
                continue;
            }
            Track parsed = audioSpecParser.enrich(track);
            if (parsed != null && parsed.hasAudioSpec()) {
                enriched.add(parsed);
                if (enriched.size() >= AUDIO_SPEC_UPDATE_BATCH_SIZE) {
                    updated += database.updateAudioSpecs(enriched);
                    enriched.clear();
                }
            }
        }
        if (!enriched.isEmpty()) {
            updated += database.updateAudioSpecs(enriched);
        }
        return updated;
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
        if (favorite) {
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
        long created = database.createPlaylist("我的 Yukine 歌单");
        if (created != -1L) {
            return created;
        }
        playlists = database.loadPlaylists();
        return playlists.isEmpty() ? -1L : playlists.get(0).id;
    }

    public boolean addTrackToPlaylist(long playlistId, long trackId) {
        return database.addTrackToPlaylist(playlistId, trackId);
    }

    public boolean removeTrackFromPlaylist(long playlistId, long trackId) {
        return database.removeTrackFromPlaylist(playlistId, trackId);
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
