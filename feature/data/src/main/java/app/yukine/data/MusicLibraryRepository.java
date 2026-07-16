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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

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
import app.yukine.streaming.StreamingTrack;
import app.yukine.TrackShareStyle;
import app.yukine.common.StreamingDataPathParser;
import app.yukine.data.room.YukineDatabase;
import app.yukine.data.room.AudioFeatureEntity;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.TrackSourceMappingEntity;
import app.yukine.data.room.TrackArtistIdentityRow;
import app.yukine.data.room.TrackMergeIdentityRow;
import app.yukine.data.room.TrackRecordingIdentityRow;
import app.yukine.identity.ArtistCreditRole;
import app.yukine.identity.ArtistAlias;
import app.yukine.identity.ArtistAliasType;
import app.yukine.identity.ArtistSourceMapping;
import app.yukine.identity.ArtistType;
import app.yukine.identity.CanonicalArtist;
import app.yukine.identity.IdentityMatchStatus;
import app.yukine.identity.TrackArtistIdentity;
import app.yukine.identity.LyricSourceBinding;
import app.yukine.fingerprint.AudioFingerprintCandidate;
import app.yukine.fingerprint.AudioFingerprintEvidence;
import app.yukine.data.room.LyricBindingEntity;
import app.yukine.data.room.ArtistAliasEntity;
import app.yukine.data.room.ArtistSourceMappingEntity;
import app.yukine.data.room.CanonicalArtistEntity;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class MusicLibraryRepository {
    private static final String TAG = "MusicLibraryRepository";
    private static final int AUDIO_SPEC_PARSE_LIMIT = 24;
    private static final int AUDIO_SPEC_CANDIDATE_SCAN_LIMIT = 192;
    private static final int AUDIO_SPEC_ALGORITHM_VERSION = 1;
    private static final int AUDIO_FEATURE_ALGORITHM_VERSION = 1;
    private static final int AUDIO_FINGERPRINT_ALGORITHM_VERSION = 1;
    private static final int AUDIO_FINGERPRINT_SCAN_MULTIPLIER = 8;
    private static final long AUDIO_FINGERPRINT_RETRY_DELAY_MS = 6L * 60L * 60L * 1000L;
    private static final String AUDIO_SPEC_READY = "READY";
    private static final String AUDIO_SPEC_FAILED = "FAILED";
    private static final String STRUCTURED_MATCH_PREFIX = "__echo_source_match_v";

    private final LibraryRepository libraryRepository;
    private final YukineDatabase database;
    private final Context appContext;
    private final PlaybackPersistenceRepository playbackPersistenceRepository;
    private final SettingsRepository settingsRepository;
    private final HistoryRepository historyRepository;
    private final PlaylistRepository playlistRepository;
    private final RemoteSourceRepository remoteSourceRepository;
    private final MediaStoreMusicScanner scanner;
    private final DocumentMusicImporter documentImporter;
    private final AudioSpecParser audioSpecParser;
    private final WebDavClient webDavClient;
    private final StreamingDataPathParser streamingDataPathParser;
    private final MusicIdentityDao musicIdentityDao;
    private final RoomArtistIdentityRepository artistIdentityRepository;
    private final StreamingCandidateCatalogStore streamingCandidateCatalogStore;

    @Inject
    public MusicLibraryRepository(@ApplicationContext Context context, StreamingDataPathParser streamingDataPathParser) {
        this(context, streamingDataPathParser, YukineDatabase.getInstance(context.getApplicationContext()));
    }

    MusicLibraryRepository(
            Context context,
            StreamingDataPathParser streamingDataPathParser,
            YukineDatabase database
    ) {
        Context appContext = context.getApplicationContext();
        this.appContext = appContext;
        this.database = database;
        libraryRepository = new LibraryRepository(database);
        playbackPersistenceRepository = new PlaybackPersistenceRepository(database);
        settingsRepository = new SettingsRepository(database.settingsDao());
        historyRepository = new HistoryRepository(database);
        playlistRepository = new PlaylistRepository(database);
        remoteSourceRepository = new RemoteSourceRepository(database, libraryRepository);
        scanner = new MediaStoreMusicScanner(appContext);
        documentImporter = new DocumentMusicImporter(appContext);
        audioSpecParser = new AudioSpecParser(appContext);
        webDavClient = new WebDavClient(appContext);
        this.streamingDataPathParser = streamingDataPathParser;
        streamingCandidateCatalogStore = new StreamingCandidateCatalogStore(database);
        musicIdentityDao = database.musicIdentityDao();
        artistIdentityRepository = new RoomArtistIdentityRepository(database);
    }

    public List<Track> loadCachedTracks() {
        return libraryRepository.loadTracks();
    }

    /** Off-main-thread, network-free lookup of the canonical recording's preferred source. */
    public Track loadActivePlaybackSource(Track requested) {
        return libraryRepository.loadActivePlaybackSource(requested);
    }

    /** Off-main-thread feedback from the playback service after the first decoded PCM buffer. */
    public boolean recordSuccessfulPlayback(Track track) {
        if (track == null) {
            return false;
        }
        String dataPath = track.dataPath == null ? "" : track.dataPath;
        boolean streaming = streamingDataPathParser.isStreamingTrack(dataPath);
        String provider = streaming
                ? streamingDataPathParser.providerName(dataPath)
                : "";
        String providerTrackId = streaming
                ? streamingDataPathParser.providerTrackId(dataPath)
                : "";
        return libraryRepository.recordSuccessfulPlayback(track, provider, providerTrackId);
    }

    /** Off-main-thread, non-blocking-to-playback persistence invoked by telemetry. */
    public boolean recordPlaybackResolutionFailure(
            String provider,
            String providerTrackId,
            String errorCode,
            boolean timedOut
    ) {
        return libraryRepository.recordPlaybackResolutionFailure(
                provider,
                providerTrackId,
                errorCode,
                timedOut
        );
    }

    /** Loads provider lyric IDs by canonical recording, newest successful binding first. */
    public List<LyricSourceBinding> loadLyricBindings(long trackId) {
        Long recordingId = musicIdentityDao.recordingIdForLocalTrack(trackId);
        if (recordingId == null) {
            return Collections.emptyList();
        }
        ArrayList<LyricSourceBinding> bindings = new ArrayList<>();
        for (LyricBindingEntity entity : musicIdentityDao.lyricBindings(recordingId)) {
            bindings.add(new LyricSourceBinding(
                    entity.getRecordingId(),
                    entity.getProvider(),
                    entity.getProviderLyricId(),
                    entity.getSynced(),
                    entity.getDurationMs(),
                    entity.getChecksum(),
                    entity.getUpdatedAt()
            ));
        }
        return bindings;
    }

    /** Persists a successful lyric lookup without changing the recording identity. */
    public void saveLyricBinding(long trackId, LyricSourceBinding binding) {
        if (binding == null || binding.getProvider().trim().isEmpty()
                || binding.getProviderLyricId().trim().isEmpty()) {
            return;
        }
        Long recordingId = musicIdentityDao.recordingIdForLocalTrack(trackId);
        if (recordingId == null) {
            return;
        }
        musicIdentityDao.upsert(new LyricBindingEntity(
                recordingId,
                binding.getProvider().trim(),
                binding.getProviderLyricId().trim(),
                binding.getSynced(),
                Math.max(0L, binding.getDurationMs()),
                binding.getChecksum().trim(),
                binding.getUpdatedAt()
        ));
    }

    /** Stable offline identity used to merge display rows; never performs network work. */
    public String loadCanonicalId(long trackId) {
        return libraryRepository.loadCanonicalId(trackId);
    }

    /**
     * Batch display-merge identities. A generated UUID remains stable storage identity, but it
     * becomes a hard display anchor only after strong evidence, confirmation, a manual split, or
     * an existing multi-source grouping. Empty values intentionally allow metadata/LX fallback.
     */
    public Map<Long, String> loadTrackMergeIdentities() {
        LinkedHashMap<Long, String> values = new LinkedHashMap<>();
        for (TrackMergeIdentityRow row : musicIdentityDao.trackMergeIdentities()) {
            values.put(row.getLocalTrackId(), row.getMergeIdentity());
        }
        return values;
    }

    /** One-query integer recording snapshot for canonical library deduplication. */
    public Map<Long, Long> loadTrackRecordingIdentities() {
        LinkedHashMap<Long, Long> values = new LinkedHashMap<>();
        for (TrackRecordingIdentityRow row : musicIdentityDao.trackRecordingIdentities()) {
            if (row.getRecordingId() > 0L) {
                values.put(row.getLocalTrackId(), row.getRecordingId());
            }
        }
        return values;
    }

    /** Batch snapshot for artist grouping; one query avoids per-row/N+1 identity lookups. */
    public Map<Long, List<TrackArtistIdentity>> loadTrackArtistIdentities() {
        LinkedHashMap<Long, List<TrackArtistIdentity>> values = new LinkedHashMap<>();
        for (TrackArtistIdentityRow row : musicIdentityDao.trackArtistIdentities()) {
            ArtistCreditRole role;
            try {
                role = ArtistCreditRole.valueOf(row.getRole());
            } catch (IllegalArgumentException ignored) {
                role = ArtistCreditRole.UNKNOWN;
            }
            values.computeIfAbsent(row.getLocalTrackId(), ignored -> new ArrayList<>()).add(
                    new TrackArtistIdentity(
                            row.getLocalTrackId(),
                            row.getArtistKey(),
                            row.getArtistId(),
                            row.getDisplayName(),
                            row.getCreditedName(),
                            role,
                            row.getPosition()
                    )
            );
        }
        return values;
    }

    /** Network-free canonical artist lookup for artist pages and offline navigation. */
    public CanonicalArtist loadCanonicalArtist(String artistId) {
        if (artistId == null || artistId.trim().isEmpty()) {
            return null;
        }
        CanonicalArtistEntity entity = musicIdentityDao.canonicalArtist(artistId.trim());
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return canonicalArtist(entity);
    }

    /** Network-free alias snapshot keyed by the stable external artist UUID. */
    public List<ArtistAlias> loadArtistAliases(String artistId) {
        CanonicalArtist artist = loadCanonicalArtist(artistId);
        if (artist == null) {
            return Collections.emptyList();
        }
        ArrayList<ArtistAlias> aliases = new ArrayList<>();
        for (ArtistAliasEntity entity : musicIdentityDao.aliases(artist.getArtistKey())) {
            aliases.add(new ArtistAlias(
                    artist.getArtistKey(),
                    artist.getArtistId(),
                    entity.getAlias(),
                    entity.getNormalizedAlias(),
                    entity.getLocale(),
                    entity.getScript(),
                    enumValueOr(entity.getAliasType(), ArtistAliasType.ALIAS, ArtistAliasType.class),
                    entity.getSource(),
                    entity.getConfidence(),
                    entity.getVerifiedAt()
            ));
        }
        return aliases;
    }

    /** Local merge choices for a user-confirmed artist identity correction. */
    public List<CanonicalArtist> loadArtistMergeTargets(String artistId, int limit) {
        CanonicalArtist source = loadCanonicalArtist(artistId);
        if (source == null) {
            return Collections.emptyList();
        }
        ArrayList<CanonicalArtist> artists = new ArrayList<>();
        for (CanonicalArtistEntity entity : musicIdentityDao.otherArtists(
                source.getArtistKey(),
                Math.max(1, Math.min(limit, 200))
        )) {
            if (entity.getId() != null) {
                artists.add(canonicalArtist(entity));
            }
        }
        return artists;
    }

    /** Provider mappings that can be detached into a new canonical artist. */
    public List<ArtistSourceMapping> loadArtistSourceMappings(String artistId) {
        CanonicalArtist artist = loadCanonicalArtist(artistId);
        if (artist == null) {
            return Collections.emptyList();
        }
        ArrayList<ArtistSourceMapping> mappings = new ArrayList<>();
        for (ArtistSourceMappingEntity entity : musicIdentityDao.artistMappings(artist.getArtistKey())) {
            if (entity.getMappingId() == null) {
                continue;
            }
            mappings.add(new ArtistSourceMapping(
                    entity.getMappingId(),
                    artist.getArtistKey(),
                    artist.getArtistId(),
                    entity.getProvider(),
                    entity.getProviderArtistId(),
                    entity.getDisplayName(),
                    enumValueOr(entity.getStatus(), IdentityMatchStatus.UNRESOLVED, IdentityMatchStatus.class),
                    entity.getConfidence(),
                    entity.getLastVerifiedAt()
            ));
        }
        return mappings;
    }

    public CanonicalArtist mergeArtistIdentities(String sourceArtistId, String targetArtistId) {
        CanonicalArtist source = loadCanonicalArtist(sourceArtistId);
        CanonicalArtist target = loadCanonicalArtist(targetArtistId);
        if (source == null || target == null) {
            throw new IllegalArgumentException("Unknown artist identity");
        }
        return artistIdentityRepository.mergeArtists(source.getArtistKey(), target.getArtistKey());
    }

    public CanonicalArtist splitArtistSourceMapping(long mappingId) {
        if (mappingId <= 0L) {
            throw new IllegalArgumentException("Invalid artist source mapping");
        }
        return artistIdentityRepository.splitArtistMapping(mappingId);
    }

    private static CanonicalArtist canonicalArtist(CanonicalArtistEntity entity) {
        return new CanonicalArtist(
                entity.getId(),
                entity.getArtistUuid(),
                entity.getDisplayName(),
                entity.getSortName(),
                enumValueOr(entity.getArtistType(), ArtistType.UNKNOWN, ArtistType.class),
                entity.getCountryCode(),
                entity.getMusicBrainzArtistId(),
                enumValueOr(entity.getMatchStatus(), IdentityMatchStatus.UNRESOLVED, IdentityMatchStatus.class),
                entity.getConfidence(),
                entity.getMetadataSource(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private static <T extends Enum<T>> T enumValueOr(String value, T fallback, Class<T> type) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public List<Track> loadRecentlyAdded(int limit) {
        return libraryRepository.loadRecentlyAdded(limit);
    }

    public List<Track> loadLongUnplayed(int limit) {
        return libraryRepository.loadLongUnplayed(limit);
    }

    public PlaybackQueueState loadPlaybackQueue() {
        return new PlaybackQueueState(
                playbackPersistenceRepository.loadQueue(),
                playbackPersistenceRepository.loadQueueIndex()
        );
    }

    public void savePlaybackQueue(List<Track> tracks, int currentIndex) {
        playbackPersistenceRepository.saveQueue(tracks, currentIndex);
    }

    public long loadPlaybackPositionTrackId() {
        return playbackPersistenceRepository.loadPositionTrackId();
    }

    public long loadPlaybackPositionMs() {
        return playbackPersistenceRepository.loadPositionMs();
    }

    public void savePlaybackPosition(long trackId, long positionMs) {
        playbackPersistenceRepository.savePosition(trackId, positionMs);
    }

    public boolean loadShuffleEnabled() {
        return settingsRepository.loadShuffleEnabled();
    }

    public void saveShuffleEnabled(boolean enabled) {
        settingsRepository.saveShuffleEnabled(enabled);
    }

    public int loadRepeatMode() {
        return normalizeRepeatMode(settingsRepository.loadRepeatMode());
    }

    public void saveRepeatMode(int repeatMode) {
        settingsRepository.saveRepeatMode(normalizeRepeatMode(repeatMode));
    }

    public boolean loadPlaybackResumeRequested() {
        return settingsRepository.loadPlaybackResumeRequested();
    }

    public void savePlaybackResumeRequested(boolean requested) {
        settingsRepository.savePlaybackResumeRequested(requested);
    }

    public AudioEffectSettings loadAudioEffectSettings() {
        return settingsRepository.loadAudioEffectSettings();
    }

    public void saveAudioEffectSettings(AudioEffectSettings settings) {
        settingsRepository.saveAudioEffectSettings(settings);
    }

    public boolean loadStatusBarLyricsEnabled() {
        return settingsRepository.loadStatusBarLyricsEnabled();
    }

    public void saveStatusBarLyricsEnabled(boolean enabled) {
        settingsRepository.saveStatusBarLyricsEnabled(enabled);
    }

    public boolean loadSystemMediaLyricsTitleEnabled() {
        return settingsRepository.loadSystemMediaLyricsTitleEnabled();
    }

    public void saveSystemMediaLyricsTitleEnabled(boolean enabled) {
        settingsRepository.saveSystemMediaLyricsTitleEnabled(enabled);
    }

    public boolean loadFloatingLyricsEnabled() {
        return settingsRepository.loadFloatingLyricsEnabled();
    }

    public void saveFloatingLyricsEnabled(boolean enabled) {
        settingsRepository.saveFloatingLyricsEnabled(enabled);
    }

    public boolean loadNowPlayingGesturesEnabled() {
        return settingsRepository.loadNowPlayingGesturesEnabled();
    }

    public void saveNowPlayingGesturesEnabled(boolean enabled) {
        settingsRepository.saveNowPlayingGesturesEnabled(enabled);
    }

    public boolean loadPlaybackRestoreEnabled() {
        return settingsRepository.loadPlaybackRestoreEnabled();
    }

    public void savePlaybackRestoreEnabled(boolean enabled) {
        settingsRepository.savePlaybackRestoreEnabled(enabled);
    }

    public boolean loadReplayGainEnabled() {
        return settingsRepository.loadReplayGainEnabled();
    }

    public void saveReplayGainEnabled(boolean enabled) {
        settingsRepository.saveReplayGainEnabled(enabled);
    }

    public boolean loadDebugPromptsEnabled() {
        return settingsRepository.loadDebugPromptsEnabled();
    }

    public void saveDebugPromptsEnabled(boolean enabled) {
        settingsRepository.saveDebugPromptsEnabled(enabled);
    }

    public boolean loadCustomBackgroundBlurEnabled() {
        return settingsRepository.loadCustomBackgroundBlurEnabled();
    }

    public float loadCustomBackgroundBlurRadiusDp() {
        return settingsRepository.loadCustomBackgroundBlurRadiusDp();
    }

    public void saveCustomBackgroundBlurEnabled(boolean enabled) {
        settingsRepository.saveCustomBackgroundBlurEnabled(enabled);
    }

    public void saveCustomBackgroundBlurRadiusDp(float radiusDp) {
        settingsRepository.saveCustomBackgroundBlurRadiusDp(radiusDp);
    }

    public boolean loadGlassBlurEnabled() {
        return settingsRepository.loadGlassBlurEnabled();
    }

    public float loadGlassBlurRadiusDp() {
        return settingsRepository.loadGlassBlurRadiusDp();
    }

    public void saveGlassBlurEnabled(boolean enabled) {
        settingsRepository.saveGlassBlurEnabled(enabled);
    }

    public void saveGlassBlurRadiusDp(float radiusDp) {
        settingsRepository.saveGlassBlurRadiusDp(radiusDp);
    }

    public float loadGlassSurfaceOpacity() {
        return settingsRepository.loadGlassSurfaceOpacity();
    }

    public void saveGlassSurfaceOpacity(float opacity) {
        settingsRepository.saveGlassSurfaceOpacity(opacity);
    }

    public String loadShareStyle() {
        return settingsRepository.loadShareStyle();
    }

    public void saveShareStyle(String style) {
        settingsRepository.saveShareStyle(style);
    }

    public PageBackgrounds loadPageBackgrounds() {
        return settingsRepository.loadPageBackgrounds();
    }

    public void savePageBackgrounds(PageBackgrounds backgrounds) {
        settingsRepository.savePageBackgrounds(backgrounds);
    }

    public boolean loadOnboardingCompleted() {
        return settingsRepository.loadOnboardingCompleted();
    }

    public void saveOnboardingCompleted(boolean completed) {
        settingsRepository.saveOnboardingCompleted(completed);
    }

    public boolean loadLibraryAutoSyncEnabled() {
        return settingsRepository.loadLibraryAutoSyncEnabled();
    }

    public void saveLibraryAutoSyncEnabled(boolean enabled) {
        settingsRepository.saveLibraryAutoSyncEnabled(enabled);
    }

    public List<RemoteSource> loadRemoteSources() {
        return remoteSourceRepository.loadSources();
    }

    public RemoteSource loadRemoteSource(long sourceId) {
        return remoteSourceRepository.loadSource(sourceId);
    }

    public RemoteSource cachedRemoteSource(long sourceId) {
        return remoteSourceRepository.cachedSource(sourceId);
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
        long savedId = remoteSourceRepository.save(source);
        if (sourceId > 0L && savedId > 0L) {
            settingsRepository.saveWebDavSyncManifest(sourceId, "");
        }
        return savedId;
    }

    public String testRemoteSource(long sourceId) {
        RemoteSource source = remoteSourceRepository.loadSource(sourceId);
        if (source == null) {
            return "远程源不存在";
        }
        String status = webDavClient.test(source);
        remoteSourceRepository.updateStatus(sourceId, status);
        return status;
    }

    public WebDavSyncResult syncRemoteSource(long sourceId) {
        RemoteSource source = remoteSourceRepository.loadSource(sourceId);
        if (source == null) {
            return new WebDavSyncResult(Collections.<Track>emptyList(), 0, 0, 0);
        }
        try {
            List<Track> oldTracks = remoteSourceRepository.loadTracks(sourceId);
            WebDavClient.IncrementalResult incremental = webDavClient.listAudioTracksIncremental(
                    source,
                    oldTracks,
                    settingsRepository.loadWebDavSyncManifest(sourceId)
            );
            List<Track> tracks = incremental.tracks;
            WebDavSyncResult result = syncResult(oldTracks, tracks);
            remoteSourceRepository.applyIncrementalTracks(oldTracks, tracks);
            settingsRepository.saveWebDavSyncManifest(sourceId, incremental.manifest);
            remoteSourceRepository.updateStatus(sourceId, "已同步 WebDAV：" + result.summary());
            return result;
        } catch (RuntimeException error) {
            remoteSourceRepository.updateStatus(sourceId, "同步失败：" + safeMessage(error));
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
        remoteSourceRepository.delete(sourceId);
        settingsRepository.saveWebDavSyncManifest(sourceId, "");
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
        libraryRepository.upsertTracks(tracks);
        return track;
    }

    public boolean streamUrlExists(String url) {
        String cleanUrl = normalizeBaseUrl(url);
        if (cleanUrl.isEmpty()) {
            return false;
        }
        String dataPath = "stream:" + cleanUrl;
        // 使用 SQL EXISTS 查询代替全表加载，O(1) 而非 O(n)。
        return libraryRepository.trackExistsByDataPath(dataPath);
    }

    public Track updateStreamUrl(long oldTrackId, String title, String url) {
        String cleanUrl = normalizeBaseUrl(url);
        if (cleanUrl.isEmpty()) {
            return null;
        }
        String cleanTitle = title == null || title.trim().isEmpty() ? "网络流媒体" : title.trim();
        Track track = M3uPlaylistParser.streamTrack(cleanTitle, cleanUrl);
        libraryRepository.replaceTrackAndMigrateReferences(oldTrackId, track);
        return track;
    }

    public String loadStreamingTrackMatch(Track track, String provider) {
        String cleanProvider = provider == null ? "" : provider;
        return libraryRepository.loadStreamingTrackMatch(
                track,
                cleanProvider,
                streamingTrackMatchKeys(track)
        );
    }

    /** Fixed-query snapshot used by startup and incremental sync; performs no network work. */
    public Map<Long, Map<String, String>> loadStreamingTrackMatches(
            List<Track> tracks,
            List<String> providers
    ) {
        if (tracks == null || tracks.isEmpty() || providers == null || providers.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<Long, List<String>> legacyKeysByTrack = new LinkedHashMap<>();
        for (Track track : tracks) {
            if (track != null) {
                legacyKeysByTrack.put(track.id, streamingTrackMatchKeys(track));
            }
        }
        return libraryRepository.loadStreamingTrackMatches(tracks, providers, legacyKeysByTrack);
    }

    public void saveStreamingTrackMatch(Track track, String provider, String providerTrackId) {
        String cleanProvider = provider == null ? "" : provider;
        String cleanProviderTrackId = providerTrackId == null ? "" : providerTrackId.trim();
        List<String> keys = streamingTrackMatchKeys(track);
        if (!keys.isEmpty()) {
            libraryRepository.saveStreamingTrackMatch(keys.get(keys.size() - 1), cleanProvider, cleanProviderTrackId, track);
        }
    }

    /** Stores one structured candidate catalog instead of duplicating it for every lookup key. */
    public void saveStructuredStreamingTrackMatch(Track track, String provider, String encodedMatch) {
        String cleanProvider = provider == null ? "" : provider.trim();
        String cleanEncodedMatch = encodedMatch == null ? "" : encodedMatch.trim();
        if (cleanProvider.isEmpty() || !cleanEncodedMatch.startsWith(STRUCTURED_MATCH_PREFIX)) {
            saveStreamingTrackMatch(track, cleanProvider, cleanEncodedMatch);
            return;
        }
        List<String> keys = streamingTrackMatchKeys(track);
        if (keys.isEmpty()) {
            return;
        }
        String catalogKey = keys.get(keys.size() - 1);
        libraryRepository.replaceStreamingTrackMatches(
                keys,
                cleanProvider,
                catalogKey,
                cleanEncodedMatch,
                track
        );
    }

    /** Stores the bounded, ranked search catalog as reviewable candidates without confirming Top1. */
    public void saveStreamingTrackCandidates(
            Track track,
            String provider,
            List<StreamingTrack> candidates
    ) {
        if (track == null || candidates == null) {
            return;
        }
        streamingCandidateCatalogStore.replace(track, provider == null ? "" : provider, candidates);
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

        List<Track> existingTracks = libraryRepository.loadTracks();
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
            libraryRepository.upsertTracks(newStreamTracks);
        }

        int playlistAddedCount = 0;
        for (Track track : playlistTracks) {
            if (playlistRepository.addTrack(playlistId, track.id)) {
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
        return libraryRepository.deleteTracksByDataPathPattern("stream:%");
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
            libraryRepository.upsertTracks(toUpsert);
        }
        cacheStreamingTrackMatches(streamingTracks);
        // Replace the complete membership in one database transaction. This keeps large remote
        // playlists fast and guarantees that the sync marker is only advanced after all rows exist.
        ArrayList<Long> trackIds = new ArrayList<>();
        for (Track track : streamingTracks) {
            if (track != null) {
                trackIds.add(track.id);
            }
        }
        return playlistRepository.replaceTracks(playlistId, trackIds);
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

        List<Track> existingTracks = libraryRepository.loadTracks();
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
            libraryRepository.upsertTracks(newTracks);
        }
        cacheStreamingTrackMatches(streamingTracks);

        int playlistAddedCount = 0;
        int duplicateCount = 0;
        for (Track track : streamingTracks) {
            if (track == null) {
                continue;
            }
            if (playlistRepository.addTrack(playlistId, track.id)) {
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
        return libraryRepository.deleteTrack(trackId);
    }

    public int hideTracks(List<Track> tracks) {
        return libraryRepository.hideTracks(tracks);
    }

    public List<LibraryExclusion> loadLibraryExclusions() {
        return libraryRepository.loadExclusions();
    }

    public boolean restoreLibraryExclusion(String sourceKey) {
        return libraryRepository.restoreExclusion(sourceKey);
    }

    public int restoreAllLibraryExclusions() {
        return libraryRepository.restoreAllExclusions();
    }

    public String loadThemeMode() {
        return settingsRepository.loadThemeMode();
    }

    public void saveThemeMode(String mode) {
        settingsRepository.saveThemeMode(mode);
    }

    public String loadAccentMode() {
        return settingsRepository.loadAccentMode();
    }

    public void saveAccentMode(String mode) {
        settingsRepository.saveAccentMode(mode);
    }

    public String loadLanguageMode() {
        return settingsRepository.loadLanguageMode();
    }

    public void saveLanguageMode(String mode) {
        settingsRepository.saveLanguageMode(mode);
    }

    public float loadPlaybackSpeed() {
        return normalizePlaybackSpeed(settingsRepository.loadPlaybackSpeed());
    }

    public void savePlaybackSpeed(float speed) {
        settingsRepository.savePlaybackSpeed(normalizePlaybackSpeed(speed));
    }

    public float loadAppVolume() {
        return normalizeAppVolume(settingsRepository.loadAppVolume());
    }

    public void saveAppVolume(float volume) {
        settingsRepository.saveAppVolume(normalizeAppVolume(volume));
    }

    public String loadStreamingAudioQuality() {
        return StreamingQualityPreference.normalize(settingsRepository.loadStreamingAudioQuality());
    }

    public void saveStreamingAudioQuality(String quality) {
        settingsRepository.saveStreamingAudioQuality(StreamingQualityPreference.normalize(quality));
    }

    public boolean loadRefuseAutomaticQualityDowngrade() {
        return settingsRepository.loadRefuseAutomaticQualityDowngrade();
    }

    public void saveRefuseAutomaticQualityDowngrade(boolean refuse) {
        settingsRepository.saveRefuseAutomaticQualityDowngrade(refuse);
    }

    public boolean loadOnlineLyricsEnabled() {
        return settingsRepository.loadOnlineLyricsEnabled();
    }

    public void saveOnlineLyricsEnabled(boolean enabled) {
        settingsRepository.saveOnlineLyricsEnabled(enabled);
    }

    public boolean loadConcurrentPlaybackEnabled() {
        return settingsRepository.loadConcurrentPlaybackEnabled();
    }

    public void saveConcurrentPlaybackEnabled(boolean enabled) {
        settingsRepository.saveConcurrentPlaybackEnabled(enabled);
    }

    public long loadLyricsOffsetMs() {
        return normalizeLyricsOffsetMs(settingsRepository.loadLyricsOffsetMs());
    }

    public void saveLyricsOffsetMs(long offsetMs) {
        settingsRepository.saveLyricsOffsetMs(normalizeLyricsOffsetMs(offsetMs));
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
        if (generation >= 0L && generation == settingsRepository.loadMediaStoreGeneration()) {
            // The full DB list also contains document, stream, streaming and WebDAV rows, so it
            // remains the single source of truth while avoiding a redundant MediaStore rewrite.
            reportRefreshProgress(progressListener, LibraryRefreshPhase.RELOADING, -1, startedAtNanos);
            List<Track> cachedTracks = libraryRepository.loadTracks();
            reportRefreshCompleted(cachedTracks.size(), startedAtNanos, true);
            return cachedTracks;
        }
        reportRefreshProgress(progressListener, LibraryRefreshPhase.SCANNING, -1, startedAtNanos);
        List<Track> tracks = scanner.scan();
        throwIfRefreshInterrupted();
        reportRefreshProgress(progressListener, LibraryRefreshPhase.REPLACING, tracks.size(), startedAtNanos);
        libraryRepository.replaceScanManagedTracks(tracks);
        throwIfRefreshInterrupted();
        // Persist only after the replacement transaction succeeds. If the scan or write fails,
        // the old token forces a safe retry next time.
        settingsRepository.saveMediaStoreGeneration(generation);
        reportRefreshProgress(progressListener, LibraryRefreshPhase.RELOADING, tracks.size(), startedAtNanos);
        List<Track> refreshedTracks = libraryRepository.loadTracks();
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
        libraryRepository.upsertTracks(tracks);
        return tracks;
    }

    public List<Track> importAudioTree(Uri treeUri) {
        List<Track> tracks = documentImporter.importAudioTree(treeUri);
        libraryRepository.upsertTracks(tracks);
        return tracks;
    }

    public int parseMissingAudioSpecs() {
        // Scan a bounded candidate window, but perform extractor work for at most 24 changed items.
        // Persisted failures with the same content signature are rotated without reopening media.
        List<Track> tracks = libraryRepository.loadTracksNeedingAudioSpecs(AUDIO_SPEC_CANDIDATE_SCAN_LIMIT);
        if (tracks.isEmpty()) return 0;
        ArrayList<Long> localTrackIds = new ArrayList<>(tracks.size());
        for (Track track : tracks) if (track != null && track.id > 0L) localTrackIds.add(track.id);
        HashMap<Long, TrackSourceMappingEntity> sourceByTrack = new HashMap<>();
        for (TrackSourceMappingEntity source : musicIdentityDao.sourcesForLocalTracks(localTrackIds)) {
            if (source.getLocalTrackId() != null) sourceByTrack.put(source.getLocalTrackId(), source);
        }
        ArrayList<Long> sourceIds = new ArrayList<>(sourceByTrack.size());
        for (TrackSourceMappingEntity source : sourceByTrack.values()) {
            if (source.getSourceId() != null) sourceIds.add(source.getSourceId());
        }
        HashMap<Long, AudioFeatureEntity> featureBySource = new HashMap<>();
        if (!sourceIds.isEmpty()) {
            for (AudioFeatureEntity feature : musicIdentityDao.audioFeatures(sourceIds)) {
                featureBySource.put(feature.getSourceId(), feature);
            }
        }
        ArrayList<Track> enriched = new ArrayList<>();
        ArrayList<AudioFeatureEntity> featureUpdates = new ArrayList<>();
        long attemptedAt = System.currentTimeMillis();
        int parsedCount = 0;
        for (Track track : tracks) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.util.concurrent.CancellationException("Audio spec parsing cancelled");
            }
            if (track == null || !track.needsAudioSpecParsing()) {
                continue;
            }
            TrackSourceMappingEntity source = sourceByTrack.get(track.id);
            if (source == null || source.getSourceId() == null) {
                libraryRepository.markAudioSpecAttempt(track.id, attemptedAt);
                continue;
            }
            long sourceId = source.getSourceId();
            String contentSignature = AudioContentSignature.create(appContext, track);
            AudioFeatureEntity previous = featureBySource.get(sourceId);
            if (shouldSkipAudioSpec(previous, contentSignature)) {
                libraryRepository.markAudioSpecAttempt(track.id, attemptedAt);
                continue;
            }
            if (parsedCount >= AUDIO_SPEC_PARSE_LIMIT) break;
            parsedCount++;
            Track parsed;
            try {
                parsed = audioSpecParser.enrich(track);
            } catch (RuntimeException ignored) {
                parsed = track;
            }
            boolean hasAudioSpec = parsed != null && parsed.hasAudioSpec();
            if (parsed != null && (parsed.hasAudioSpec()
                    || parsed.identityTags != null && !parsed.identityTags.isEmpty())) {
                enriched.add(parsed);
            }
            if (!hasAudioSpec) {
                libraryRepository.markAudioSpecAttempt(track.id, attemptedAt);
            }
            featureUpdates.add(audioFeature(
                    sourceId,
                    contentSignature,
                    previous,
                    hasAudioSpec,
                    attemptedAt
            ));
        }
        int updated = enriched.isEmpty() ? 0 : libraryRepository.updateAudioSpecs(enriched);
        if (!featureUpdates.isEmpty()) musicIdentityDao.upsertAudioFeatures(featureUpdates);
        return updated;
    }

    /**
     * Returns a bounded, network-free batch for the app's native decoder. This method never opens
     * media and is intentionally separate from library/display queries.
     */
    public List<AudioFingerprintCandidate> loadPendingAudioFingerprintCandidates(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 16));
        long now = System.currentTimeMillis();
        List<TrackSourceMappingEntity> sources = musicIdentityDao.sourcesNeedingAudioFingerprint(
                AUDIO_FINGERPRINT_ALGORITHM_VERSION,
                now - AUDIO_FINGERPRINT_RETRY_DELAY_MS,
                limit * AUDIO_FINGERPRINT_SCAN_MULTIPLIER
        );
        if (sources.isEmpty()) return Collections.emptyList();
        ArrayList<Long> trackIds = new ArrayList<>(sources.size());
        ArrayList<Long> sourceIds = new ArrayList<>(sources.size());
        for (TrackSourceMappingEntity source : sources) {
            if (source.getLocalTrackId() != null && source.getSourceId() != null) {
                trackIds.add(source.getLocalTrackId());
                sourceIds.add(source.getSourceId());
            }
        }
        HashMap<Long, Track> tracksById = new HashMap<>();
        for (Track track : libraryRepository.loadTracksByIds(trackIds)) {
            tracksById.put(track.id, track);
        }
        HashMap<Long, AudioFeatureEntity> featuresBySource = new HashMap<>();
        for (AudioFeatureEntity feature : musicIdentityDao.audioFeatures(sourceIds)) {
            featuresBySource.put(feature.getSourceId(), feature);
        }
        ArrayList<AudioFingerprintCandidate> candidates = new ArrayList<>(limit);
        ArrayList<AudioFeatureEntity> resets = new ArrayList<>();
        for (TrackSourceMappingEntity source : sources) {
            if (candidates.size() >= limit || source.getSourceId() == null
                    || source.getLocalTrackId() == null) break;
            Track track = tracksById.get(source.getLocalTrackId());
            if (!isLocallyReadableForAudioVerification(track)) continue;
            long sourceId = source.getSourceId();
            String signature = AudioContentSignature.create(appContext, track);
            AudioFeatureEntity previous = featuresBySource.get(sourceId);
            boolean contentChanged = previous == null
                    || !previous.getContentSignature().equals(signature);
            if (contentChanged) {
                AudioFeatureEntity reset = resetAudioEvidence(sourceId, signature, track, previous, now);
                resets.add(reset);
                previous = reset;
            } else if (!previous.getChromaprint().isEmpty()
                    && previous.getAlgorithmVersion() >= AUDIO_FINGERPRINT_ALGORITHM_VERSION) {
                musicIdentityDao.touchAudioFeatureIfCurrent(sourceId, signature, now);
                continue;
            }
            candidates.add(new AudioFingerprintCandidate(
                    sourceId,
                    track,
                    signature,
                    previous.getAlgorithmVersion()
            ));
        }
        if (!resets.isEmpty()) musicIdentityDao.upsertAudioFeatures(resets);
        return candidates;
    }

    /**
     * Returns one WebDAV fingerprint work item without opening media or performing network I/O.
     * The playback service calls this only after it has found a sufficiently large cached prefix.
     */
    public AudioFingerprintCandidate loadPendingWebDavAudioFingerprintCandidate(long localTrackId) {
        if (localTrackId <= 0L) return null;
        TrackSourceMappingEntity source = musicIdentityDao.sourceForLocalTrack(localTrackId);
        if (source == null || source.getSourceId() == null
                || !"webdav".equalsIgnoreCase(source.getProvider())) {
            return null;
        }
        Track track = libraryRepository.loadTrack(localTrackId);
        if (track == null || track.contentUri == null || Uri.EMPTY.equals(track.contentUri)
                || !track.dataPath.startsWith("webdav:")) {
            return null;
        }
        long sourceId = source.getSourceId();
        long now = System.currentTimeMillis();
        String signature = AudioContentSignature.create(appContext, track);
        AudioFeatureEntity previous = musicIdentityDao.audioFeature(sourceId);
        if (previous == null || !previous.getContentSignature().equals(signature)) {
            previous = resetAudioEvidence(sourceId, signature, track, previous, now);
            musicIdentityDao.upsertAudioFeatures(Collections.singletonList(previous));
        } else if (!previous.getChromaprint().isEmpty()
                && previous.getAlgorithmVersion() >= AUDIO_FINGERPRINT_ALGORITHM_VERSION) {
            musicIdentityDao.touchAudioFeatureIfCurrent(sourceId, signature, now);
            return null;
        }
        return new AudioFingerprintCandidate(
                sourceId,
                track,
                signature,
                previous.getAlgorithmVersion()
        );
    }

    /** Conditional write prevents an analysis result from attaching after a file changed. */
    public boolean saveAudioFingerprint(
            AudioFingerprintCandidate candidate,
            AudioFingerprintEvidence evidence
    ) {
        if (candidate == null || evidence == null || candidate.getSourceId() <= 0L
                || evidence.getChromaprint().trim().isEmpty()
                || evidence.getAlgorithmVersion() < AUDIO_FINGERPRINT_ALGORITHM_VERSION) {
            return false;
        }
        return musicIdentityDao.updateAudioFingerprintIfCurrent(
                candidate.getSourceId(),
                candidate.getContentSignature(),
                evidence.getPcmHash().trim(),
                evidence.getChromaprint().trim(),
                evidence.getAlgorithmVersion(),
                System.currentTimeMillis()
        ) == 1;
    }

    /** One cold incremental identity pass after an entire fingerprint batch has been persisted. */
    public int refreshAudioVerifiedMatches(List<Long> localTrackIds) {
        if (localTrackIds == null || localTrackIds.isEmpty()) return 0;
        ArrayList<Long> validIds = new ArrayList<>(localTrackIds.size());
        for (Long trackId : localTrackIds) {
            if (trackId != null && trackId > 0L && !validIds.contains(trackId)) {
                validIds.add(trackId);
            }
        }
        if (validIds.isEmpty()) return 0;
        return new SourceIdentityIngestor(database).ingestLocalTracks(validIds);
    }

    public boolean recordAudioFingerprintFailure(
            AudioFingerprintCandidate candidate,
            String errorCode
    ) {
        if (candidate == null || candidate.getSourceId() <= 0L) return false;
        String normalized = errorCode == null ? "FAILED" : errorCode.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9_]+", "_");
        if (normalized.isEmpty()) normalized = "FAILED";
        return musicIdentityDao.recordAudioFingerprintFailureIfCurrent(
                candidate.getSourceId(),
                candidate.getContentSignature(),
                AUDIO_FINGERPRINT_ALGORITHM_VERSION,
                "FINGERPRINT_" + normalized,
                System.currentTimeMillis()
        ) == 1;
    }

    private static boolean isLocallyReadableForAudioVerification(Track track) {
        if (track == null || track.contentUri == null || Uri.EMPTY.equals(track.contentUri)) return false;
        String scheme = track.contentUri.getScheme();
        if ("content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme)) return true;
        String path = track.dataPath == null ? "" : track.dataPath.trim();
        return path.startsWith("/") || path.matches("^[A-Za-z]:[\\\\/].*");
    }

    private static AudioFeatureEntity resetAudioEvidence(
            long sourceId,
            String signature,
            Track track,
            AudioFeatureEntity previous,
            long updatedAt
    ) {
        boolean specReady = track != null && track.hasAudioSpec();
        return new AudioFeatureEntity(
                sourceId,
                signature,
                "",
                "",
                null,
                null,
                "",
                0,
                specReady ? AUDIO_SPEC_READY : "PENDING",
                specReady ? AUDIO_SPEC_ALGORITHM_VERSION : 0,
                previous == null ? 0 : previous.getAudioSpecAttemptCount(),
                previous == null ? 0L : previous.getLastAttemptAt(),
                "",
                updatedAt
        );
    }

    private static AudioFeatureEntity audioFeature(
            long sourceId,
            String contentSignature,
            AudioFeatureEntity previous,
            boolean ready,
            long attemptedAt
    ) {
        boolean contentUnchanged = previous != null
                && previous.getContentSignature().equals(contentSignature == null ? "" : contentSignature);
        return new AudioFeatureEntity(
                sourceId,
                contentSignature,
                contentUnchanged ? previous.getPcmHash() : "",
                contentUnchanged ? previous.getChromaprint() : "",
                contentUnchanged ? previous.getRecordingEmbedding() : null,
                contentUnchanged ? previous.getWorkEmbedding() : null,
                contentUnchanged ? previous.getVersionScores() : "",
                Math.max(
                        AUDIO_FEATURE_ALGORITHM_VERSION,
                        contentUnchanged ? previous.getAlgorithmVersion() : 0
                ),
                ready ? AUDIO_SPEC_READY : AUDIO_SPEC_FAILED,
                AUDIO_SPEC_ALGORITHM_VERSION,
                previous == null ? 1 : previous.getAudioSpecAttemptCount() + 1,
                attemptedAt,
                ready ? "" : "AUDIO_SPEC_UNAVAILABLE",
                attemptedAt
        );
    }

    static boolean shouldSkipAudioSpec(AudioFeatureEntity previous, String contentSignature) {
        return previous != null
                && previous.getAudioSpecAlgorithmVersion() == AUDIO_SPEC_ALGORITHM_VERSION
                && AUDIO_SPEC_FAILED.equals(previous.getAudioSpecState())
                && previous.getContentSignature().equals(contentSignature == null ? "" : contentSignature);
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
        historyRepository.markPlayed(trackId);
    }

    public List<TrackPlayRecord> loadRecentlyPlayed(int limit) {
        return historyRepository.loadRecentlyPlayed(limit);
    }

    public List<TrackPlayRecord> loadPlayedSince(long startMs, int limit) {
        return historyRepository.loadPlayedSince(startMs, limit);
    }

    public List<TrackPlayRecord> loadMostPlayed(int limit) {
        return historyRepository.loadMostPlayed(limit);
    }

    public int clearPlayHistory() {
        return historyRepository.clear();
    }

    public void setFavorite(long trackId, boolean favorite) {
        libraryRepository.setFavorite(trackId, favorite);
    }

    public void setFavorite(Track track, boolean favorite) {
        if (track == null) {
            return;
        }
        if (favorite) {
            ArrayList<Track> tracks = new ArrayList<>();
            tracks.add(track);
            libraryRepository.upsertTracks(tracks);
            cacheStreamingTrackMatches(tracks);
        }
        libraryRepository.setFavorite(track.id, favorite);
    }

    public boolean isFavorite(long trackId) {
        return libraryRepository.isFavorite(trackId);
    }

    public Set<Long> loadFavoriteIds() {
        return libraryRepository.loadFavoriteIds();
    }

    public List<Track> loadFavoriteTracks() {
        return libraryRepository.loadFavoriteTracks();
    }

    public Track loadTrack(long trackId) {
        return libraryRepository.loadTrack(trackId);
    }

    public long loadRecordingId(long trackId) {
        return libraryRepository.loadRecordingId(trackId);
    }

    public String loadConfirmedProviderTrackId(long recordingId, String provider) {
        return libraryRepository.loadConfirmedProviderTrackId(recordingId, provider);
    }

    public boolean confirmDirectProviderSource(
            long localTrackId,
            String provider,
            String providerTrackId
    ) {
        return libraryRepository.confirmDirectProviderSource(
                localTrackId,
                provider,
                providerTrackId
        );
    }

    public boolean confirmDirectProviderSourceWithoutIdentityIngest(
            long localTrackId,
            String provider,
            String providerTrackId
    ) {
        return libraryRepository.confirmDirectProviderSourceWithoutIdentityIngest(
                localTrackId,
                provider,
                providerTrackId
        );
    }

    public int ingestConfirmedIdentitySources() {
        return libraryRepository.ingestConfirmedIdentitySources();
    }

    public int ingestConfirmedIdentitySources(List<Long> localTrackIds) {
        return libraryRepository.ingestConfirmedIdentitySources(localTrackIds);
    }

    public int ingestPendingConfirmedIdentitySources() {
        return libraryRepository.ingestPendingConfirmedIdentitySources();
    }

    public void updateFavoriteSyncState(long recordingId, String syncState) {
        libraryRepository.updateFavoriteSyncState(recordingId, syncState);
    }

    public List<Playlist> loadPlaylists() {
        return playlistRepository.loadPlaylists();
    }

    public long createPlaylist(String name) {
        return playlistRepository.create(name);
    }

    private long ensurePlaylistNamed(String name) {
        long created = playlistRepository.create(name);
        if (created >= 0L) {
            return created;
        }
        for (Playlist playlist : playlistRepository.loadPlaylists()) {
            if (playlist.name.equals(name)) {
                return playlist.id;
            }
        }
        return -1L;
    }

    public boolean renamePlaylist(long playlistId, String name) {
        return playlistRepository.rename(playlistId, name);
    }

    public boolean deletePlaylist(long playlistId) {
        return playlistRepository.delete(playlistId);
    }

    public long ensureDefaultPlaylist() {
        List<Playlist> playlists = playlistRepository.loadPlaylists();
        if (!playlists.isEmpty()) {
            return playlists.get(0).id;
        }
        long created = playlistRepository.create("我的 Yukine 歌单");
        if (created != -1L) {
            return created;
        }
        playlists = playlistRepository.loadPlaylists();
        return playlists.isEmpty() ? -1L : playlists.get(0).id;
    }

    public boolean addTrackToPlaylist(long playlistId, long trackId) {
        return playlistRepository.addTrack(playlistId, trackId);
    }

    public boolean removeTrackFromPlaylist(long playlistId, long trackId) {
        return playlistRepository.removeTrack(playlistId, trackId);
    }

    public boolean movePlaylistTrack(long playlistId, long trackId, int direction) {
        return playlistRepository.moveTrack(playlistId, trackId, direction);
    }

    public boolean movePlaylistTrackAt(long playlistId, int trackIndex, int direction) {
        return playlistRepository.moveAt(playlistId, trackIndex, direction);
    }

    public List<Track> loadPlaylistTracks(long playlistId) {
        return playlistRepository.loadTracks(playlistId);
    }

    public List<Track> immutableCopy(List<Track> tracks) {
        return Collections.unmodifiableList(new ArrayList<>(tracks));
    }

    private StreamImportResult importParsedStreamTracks(List<Track> parsedTracks) {
        if (parsedTracks == null || parsedTracks.isEmpty()) {
            return emptyStreamImportResult();
        }
        Set<String> existingDataPaths = streamDataPaths(libraryRepository.loadTracks());
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
        libraryRepository.upsertTracks(newTracks);
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
