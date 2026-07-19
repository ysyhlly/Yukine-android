package app.yukine.data;

import android.net.Uri;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.UUID;

import app.yukine.data.room.RecordingFavoriteEntity;
import app.yukine.data.room.HistoryDao;
import app.yukine.data.room.LibraryDao;
import app.yukine.data.room.LibraryExclusionEntity;
import app.yukine.data.room.PlayHistoryEntity;
import app.yukine.data.room.PlaybackPersistenceDao;
import app.yukine.data.room.PlaylistDao;
import app.yukine.data.room.PlaylistTrackEntity;
import app.yukine.data.room.SettingEntity;
import app.yukine.data.room.SettingsDao;
import app.yukine.data.room.StreamingTrackMatchDao;
import app.yukine.data.room.StreamingTrackMatchEntity;
import app.yukine.data.room.IdentityCandidateEntity;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.TrackSourceMappingEntity;
import app.yukine.data.room.TrackSourceIdentity;
import app.yukine.data.room.TrackStreamingMatchRow;
import app.yukine.data.room.TrackEntity;
import app.yukine.data.room.TrackEntityMapper;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.PlaybackTrackSourceOverlay;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;
import app.yukine.streaming.DefaultPlaybackSourcePolicy;
import app.yukine.streaming.PlaybackSourcePolicy;
import app.yukine.streaming.PlaybackSourceSelectionEvaluation;
import app.yukine.streaming.PlaybackSourceSelectionEvaluator;
import app.yukine.streaming.PlaybackSourceSelectionFeatures;
import app.yukine.streaming.ProviderRolePolicy;
import app.yukine.streaming.StreamingProviderName;
import org.json.JSONObject;

/**
 * Owns the local library and every cross-table invariant caused by replacing or deleting tracks.
 * Scan, WebDAV and streaming callers share this path so queue/index/position reconciliation cannot
 * drift between import sources.
 */
public final class LibraryRepository {
    private static final String TAG = "LibraryRepository";
    private static final String STRUCTURED_MATCH_PREFIX = "__echo_source_match_v";
    private static final String STORED_MATCH_ITEM_ID = "__stored_match__";
    private static final int SQLITE_IN_BATCH_SIZE = 500;
    private static final long LONG_UNPLAYED_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String QUEUE_INDEX = "playback_queue_index";
    private static final String POSITION_TRACK_ID = "playback_position_track_id";
    private static final Set<String> FAVORITE_SYNC_STATES = Set.of(
            "LOCAL_ONLY",
            "PENDING",
            "SYNCED",
            "RETRY",
            "NEEDS_CONFIRMATION"
    );
    private static final String POSITION_MS = "playback_position_ms";
    private static final int PLAYBACK_SOURCE_FAILURE_THRESHOLD = 3;

    private final YukineDatabase database;
    private final LibraryDao libraryDao;
    private final HistoryDao historyDao;
    private final PlaylistDao playlistDao;
    private final PlaybackPersistenceDao playbackDao;
    private final SettingsDao settingsDao;
    private final StreamingTrackMatchDao streamingMatchDao;
    private final MusicIdentityDao musicIdentityDao;
    private final ProviderSourceIdentityWriter providerSourceIdentityWriter;
    private final PlaybackPersistenceRepository playbackPersistence;
    private final OfflineMusicIdentityStore musicIdentityStore;
    private final PlaybackSourcePolicy playbackSourcePolicy;
    private final Function<Track, String> contentSignatureProvider;
    private final PolicyAwarePlaybackSourceSelector playbackSourceSelector;
    private final AtomicLong legacyMatchComparisons = new AtomicLong();
    private final AtomicLong legacyMatchDivergences = new AtomicLong();

    public LibraryRepository(YukineDatabase database) {
        this(database, DefaultPlaybackSourcePolicy.INSTANCE, ignored -> "");
    }

    public LibraryRepository(
            YukineDatabase database,
            PlaybackSourcePolicy playbackSourcePolicy
    ) {
        this(database, playbackSourcePolicy, ignored -> "");
    }

    LibraryRepository(
            YukineDatabase database,
            Function<Track, String> contentSignatureProvider
    ) {
        this(database, DefaultPlaybackSourcePolicy.INSTANCE, contentSignatureProvider);
    }

    LibraryRepository(
            YukineDatabase database,
            PlaybackSourcePolicy playbackSourcePolicy,
            Function<Track, String> contentSignatureProvider
    ) {
        this.database = database;
        libraryDao = database.libraryDao();
        historyDao = database.historyDao();
        playlistDao = database.playlistDao();
        playbackDao = database.playbackPersistenceDao();
        settingsDao = database.settingsDao();
        streamingMatchDao = database.streamingTrackMatchDao();
        musicIdentityDao = database.musicIdentityDao();
        providerSourceIdentityWriter = new ProviderSourceIdentityWriter(musicIdentityDao);
        this.playbackSourcePolicy = playbackSourcePolicy == null
                ? DefaultPlaybackSourcePolicy.INSTANCE
                : playbackSourcePolicy;
        this.contentSignatureProvider = contentSignatureProvider == null
                ? ignored -> ""
                : contentSignatureProvider;
        playbackPersistence = new PlaybackPersistenceRepository(database, this.playbackSourcePolicy);
        musicIdentityStore = new OfflineMusicIdentityStore(database);
        playbackSourceSelector = new PolicyAwarePlaybackSourceSelector(database, this.playbackSourcePolicy);
    }

    public List<Track> loadTracks() {
        return tracks(libraryDao.loadTracks());
    }

    public Track loadTrack(long trackId) {
        TrackEntity entity = libraryDao.loadTrack(trackId);
        return entity == null ? null : TrackEntityMapper.track(entity);
    }

    /** Bounded cold-path lookup used by background audio verification. */
    public List<Track> loadTracksByIds(List<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return Collections.emptyList();
        }
        return tracks(libraryDao.loadTracksByIds(trackIds));
    }

    /**
     * Resolves the persisted hot-path source for a logical recording without network work.
     *
     * The caller must run this off the main thread. A missing/stale source degrades to the
     * requested track, so playback is never blocked by canonical identity maintenance.
     */
    public Track loadActivePlaybackSource(Track requested) {
        if (requested == null) {
            return null;
        }
        long recordingId = musicIdentityStore.recordingIdForTrack(requested.id);
        if (recordingId <= 0L) {
            return requested;
        }
        app.yukine.data.room.MusicIdentityDao identityDao = database.musicIdentityDao();
        app.yukine.data.room.TrackSourceMappingEntity source = selectActivePlaybackSource(recordingId);
        if (source == null) {
            return requested;
        }
        if (source.getLocalTrackId() == null) {
            Track platformSource = platformPlaybackTrack(requested, source);
            return platformSource == null ? requested : platformSource;
        }
        TrackEntity entity = libraryDao.loadTrack(source.getLocalTrackId());
        if (entity != null) {
            return PlaybackTrackSourceOverlay.merge(requested, TrackEntityMapper.track(entity));
        }
        if (source.getSourceId() != null) {
            identityDao.markSourceUnavailable(source.getSourceId(), System.currentTimeMillis());
            app.yukine.data.room.TrackSourceMappingEntity fallback = selectActivePlaybackSource(recordingId);
            if (fallback != null) {
                if (fallback.getLocalTrackId() != null) {
                    TrackEntity fallbackEntity = libraryDao.loadTrack(fallback.getLocalTrackId());
                    if (fallbackEntity != null) {
                        return PlaybackTrackSourceOverlay.merge(
                                requested,
                                TrackEntityMapper.track(fallbackEntity)
                        );
                    }
                } else {
                    Track platformFallback = platformPlaybackTrack(requested, fallback);
                    if (platformFallback != null) {
                        return platformFallback;
                    }
                }
            }
        }
        return requested;
    }

    private TrackSourceMappingEntity selectActivePlaybackSource(long recordingId) {
        return playbackSourceSelector.select(recordingId);
    }

    public int refreshActivePlaybackSources() {
        java.util.LinkedHashSet<Long> recordingIds = new java.util.LinkedHashSet<>();
        for (TrackSourceMappingEntity source : musicIdentityDao.identityAnchorSources()) {
            recordingIds.add(source.getRecordingId());
        }
        int refreshed = 0;
        for (Long recordingId : recordingIds) {
            selectActivePlaybackSource(recordingId);
            refreshed++;
        }
        return refreshed;
    }

    private static Track platformPlaybackTrack(Track logical, TrackSourceMappingEntity source) {
        String provider = source.getProvider() == null
                ? ""
                : source.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
        String providerTrackId = source.getProviderTrackId() == null
                ? ""
                : source.getProviderTrackId().trim();
        if (!"netease".equals(provider)
                || providerTrackId.isEmpty()) {
            return null;
        }
        String dataPath = source.getDataPath() == null ? "" : source.getDataPath().trim();
        if (!dataPath.startsWith("streaming:" + provider + ":")) {
            dataPath = "streaming:" + provider + ":" + providerTrackId;
        }
        return new Track(
                logical.id,
                logical.title,
                logical.artist,
                logical.album,
                logical.durationMs > 0L ? logical.durationMs : source.getDurationMs(),
                Uri.EMPTY,
                dataPath,
                logical.albumId,
                logical.albumArtUri,
                firstNonBlank(source.getCodec(), logical.codec),
                source.getBitrateKbps() > 0 ? source.getBitrateKbps() : logical.bitrateKbps,
                logical.sampleRateHz,
                logical.bitsPerSample,
                logical.channelCount,
                logical.replayGainTrackDb,
                logical.replayGainAlbumDb,
                logical.identityTags,
                firstNonBlank(source.getAlbumArtist(), logical.albumArtist),
                firstNonBlank(source.getComposer(), logical.composer),
                firstNonBlank(source.getReleaseType(), logical.releaseType),
                source.getYear() > 0 ? source.getYear() : logical.year
        );
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.trim().isEmpty() ? fallback : preferred;
    }

    /**
     * Records a source that reached decoded audio output and promotes it only when its recording
     * match is already confirmed. The caller must run this off the main/audio thread.
     */
    public boolean recordSuccessfulPlayback(
            Track track,
            String provider,
            String providerTrackId
    ) {
        if (track == null) {
            return false;
        }
        AtomicBoolean recorded = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            TrackSourceMappingEntity source = null;
            String cleanProvider = provider == null ? "" : provider.trim();
            String cleanProviderTrackId = providerTrackId == null ? "" : providerTrackId.trim();
            if (!cleanProvider.isEmpty() && !cleanProviderTrackId.isEmpty()) {
                source = musicIdentityDao.source(cleanProvider, cleanProviderTrackId);
            }
            if (source == null && track.id > 0L) {
                source = musicIdentityDao.sourceForLocalTrack(track.id);
            }
            if (source == null && track.dataPath != null && !track.dataPath.trim().isEmpty()) {
                source = musicIdentityDao.sourceForDataPath(track.dataPath);
            }
            if (source == null || source.getSourceId() == null) {
                return;
            }
            if (!ProviderRolePolicy.canEverBecomeActive(source.getProvider())) {
                return;
            }
            long now = System.currentTimeMillis();
            int updated = musicIdentityDao.markSourceVerifiedSuccess(
                    source.getSourceId(),
                    now,
                    track.codec,
                    track.bitrateKbps
            );
            if (updated <= 0) {
                return;
            }
            if ("CONFIRMED".equals(source.getMatchStatus())) {
                selectActivePlaybackSource(source.getRecordingId());
            }
            recorded.set(true);
        });
        return recorded.get();
    }

    /**
     * Records a provider URL-resolution failure without treating cancellation, auth, rate limits
     * or timeouts as proof that the source itself is invalid.
     */
    public boolean recordPlaybackResolutionFailure(
            String provider,
            String providerTrackId,
            String errorCode,
            boolean timedOut
    ) {
        String cleanProvider = provider == null ? "" : provider.trim();
        String cleanProviderTrackId = providerTrackId == null ? "" : providerTrackId.trim();
        if (cleanProvider.isEmpty() || cleanProviderTrackId.isEmpty()) {
            return false;
        }
        AtomicBoolean recorded = new AtomicBoolean(false);
        database.runInTransaction(() -> {
            TrackSourceMappingEntity source = musicIdentityDao.source(
                    cleanProvider,
                    cleanProviderTrackId
            );
            if (source == null || source.getSourceId() == null) {
                return;
            }
            String errorReason = boundedFailureReason(errorCode);
            String reason = timedOut ? errorReason + "_TIMEOUT" : errorReason;
            boolean resetCount = !reason.equals(source.getFailureReason());
            int nextFailureCount = resetCount ? 1 : source.getFailureCount() + 1;
            boolean disableSource = shouldDisablePlaybackSource(
                    errorReason,
                    timedOut,
                    nextFailureCount
            );
            int updated = musicIdentityDao.recordSourcePlaybackFailure(
                    source.getSourceId(),
                    System.currentTimeMillis(),
                    reason,
                    resetCount,
                    disableSource
            );
            if (updated > 0 && disableSource) {
                selectActivePlaybackSource(source.getRecordingId());
            }
            recorded.set(updated > 0);
        });
        return recorded.get();
    }

    private static boolean shouldDisablePlaybackSource(
            String errorCode,
            boolean timedOut,
            int failureCount
    ) {
        if ("UNSUPPORTED_OPERATION".equals(errorCode)) {
            return true;
        }
        return !timedOut
                && "SOURCE_UNAVAILABLE".equals(errorCode)
                && failureCount >= PLAYBACK_SOURCE_FAILURE_THRESHOLD;
    }

    private static String boundedFailureReason(String value) {
        String clean = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (clean.isEmpty()) {
            return "UNKNOWN";
        }
        return clean.substring(0, Math.min(clean.length(), 48));
    }

    public List<Track> loadRecentlyAdded(int limit) {
        return limit <= 0 ? Collections.emptyList() : tracks(libraryDao.loadRecentlyAdded(limit));
    }

    public List<Track> loadLongUnplayed(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        long playedAfter = System.currentTimeMillis() - LONG_UNPLAYED_WINDOW_MS;
        return tracks(libraryDao.loadLongUnplayed(playedAfter, limit));
    }

    public List<Track> loadTracksNeedingAudioSpecs(int limit) {
        return limit <= 0
                ? Collections.emptyList()
                : tracks(libraryDao.loadTracksNeedingAudioSpecs(limit));
    }

    public List<Track> loadTracksByDataPathPattern(String pattern) {
        return tracks(libraryDao.loadTracksByDataPathPattern(pattern));
    }

    public void upsertTracks(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<Long, String> contentSignatures = contentSignatures(values);
        database.runInTransaction(() -> {
            Set<String> exclusions = new HashSet<>(libraryDao.loadExclusionKeys());
            long now = System.currentTimeMillis();
            upsertTrackRows(
                    trackEntities(values, exclusions, now),
                    identityTags(values),
                    contentSignatures,
                    now
            );
        });
        ingestConfirmedIdentitySources(localTrackIds(values));
    }

    public void replaceScanManagedTracks(List<Track> values) {
        throwIfInterrupted();
        List<Track> replacementTracks = values == null ? Collections.emptyList() : values;
        Map<Long, String> contentSignatures = contentSignatures(replacementTracks);
        database.runInTransaction(() -> {
            throwIfInterrupted();
            LinkedHashSet<Long> removedIds = new LinkedHashSet<>(libraryDao.loadScanManagedTrackIds());
            List<Track> queueBefore = playbackPersistence.loadQueueSnapshots();
            int queueIndexBefore = playbackPersistence.loadQueueIndex();
            Set<String> exclusions = new HashSet<>(libraryDao.loadExclusionKeys());
            long now = System.currentTimeMillis();
            ArrayList<TrackEntity> replacements = trackEntities(
                    replacementTracks,
                    exclusions,
                    now
            );
            deleteTrackRows(new ArrayList<>(removedIds));
            if (!replacements.isEmpty()) {
                upsertTrackRows(
                        replacements,
                        identityTags(replacementTracks),
                        contentSignatures,
                        now
                );
                for (TrackEntity replacement : replacements) {
                    if (replacement.getId() != null) {
                        removedIds.remove(replacement.getId());
                    }
                }
            }
            cleanupDeletedTrackReferences(
                    new ArrayList<>(removedIds),
                    queueBefore,
                    queueIndexBefore
            );
            musicIdentityStore.pruneMissingTracks();
        });
        ingestConfirmedIdentitySources(localTrackIds(replacementTracks));
    }

    public int replaceTracksByDataPathPattern(String pattern, List<Track> replacements) {
        AtomicInteger removed = new AtomicInteger();
        Map<Long, String> contentSignatures = contentSignatures(replacements);
        database.runInTransaction(() -> {
            List<Long> ids = libraryDao.loadTrackIdsByDataPathPattern(pattern);
            removed.set(deleteTrackIdsInCurrentTransaction(ids));
            if (replacements != null && !replacements.isEmpty()) {
                long now = System.currentTimeMillis();
                upsertTrackRows(trackEntities(
                        replacements,
                        Collections.emptySet(),
                        now
                ), identityTags(replacements), contentSignatures, now);
            }
            musicIdentityStore.pruneMissingTracks();
        });
        ingestConfirmedIdentitySources(localTrackIds(replacements));
        return removed.get();
    }

    public int applyTrackDelta(List<Long> removedIds, List<Track> upserts) {
        AtomicInteger removed = new AtomicInteger();
        Map<Long, String> contentSignatures = contentSignatures(upserts);
        database.runInTransaction(() -> {
            removed.set(deleteTrackIdsInCurrentTransaction(
                    removedIds == null ? Collections.emptyList() : removedIds
            ));
            if (upserts != null && !upserts.isEmpty()) {
                long now = System.currentTimeMillis();
                upsertTrackRows(trackEntities(
                        upserts,
                        Collections.emptySet(),
                        now
                ), identityTags(upserts), contentSignatures, now);
            }
            musicIdentityStore.pruneMissingTracks();
        });
        ingestConfirmedIdentitySources(localTrackIds(upserts));
        return removed.get();
    }

    public int deleteTracksByDataPathPattern(String pattern) {
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            removed.set(deleteTrackIdsInCurrentTransaction(
                    libraryDao.loadTrackIdsByDataPathPattern(pattern)
            ));
            musicIdentityStore.pruneMissingTracks();
        });
        return removed.get();
    }

    public int deleteTrack(long trackId) {
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            removed.set(deleteTrackIdsInCurrentTransaction(List.of(trackId)));
            musicIdentityStore.pruneMissingTracks();
        });
        return removed.get();
    }

    public int hideTracks(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            ArrayList<Long> ids = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Track track : values) {
                String sourceKey = librarySourceKey(track);
                if (sourceKey.isEmpty()) {
                    continue;
                }
                libraryDao.putExclusion(new LibraryExclusionEntity(
                        sourceKey,
                        track.contentUri == null ? "" : track.contentUri.toString(),
                        track.dataPath == null ? "" : track.dataPath,
                        now
                ));
                ids.add(track.id);
            }
            removed.set(deleteTrackIdsInCurrentTransaction(ids));
            musicIdentityStore.pruneMissingTracks();
        });
        return removed.get();
    }

    public List<LibraryExclusion> loadExclusions() {
        ArrayList<LibraryExclusion> values = new ArrayList<>();
        for (LibraryExclusionEntity row : libraryDao.loadExclusions()) {
            values.add(new LibraryExclusion(
                    row.getSourceKey(),
                    row.getContentUri(),
                    row.getDataPath(),
                    row.getCreatedAt()
            ));
        }
        return values;
    }

    public boolean restoreExclusion(String sourceKey) {
        return sourceKey != null
                && !sourceKey.trim().isEmpty()
                && libraryDao.deleteExclusion(sourceKey.trim()) > 0;
    }

    public int restoreAllExclusions() {
        return libraryDao.deleteAllExclusions();
    }

    public int updateAudioSpecs(List<Track> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        AtomicInteger updated = new AtomicInteger();
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            for (Track track : values) {
                boolean hasIdentityTags = track != null
                        && track.identityTags != null
                        && !track.identityTags.isEmpty();
                if (track == null || (!track.hasAudioSpec()
                        && Math.abs(track.replayGainTrackDb) <= 0.001f
                        && Math.abs(track.replayGainAlbumDb) <= 0.001f
                        && !hasIdentityTags)) {
                    continue;
                }
                if (track.hasAudioSpec()
                        || Math.abs(track.replayGainTrackDb) > 0.001f
                        || Math.abs(track.replayGainAlbumDb) > 0.001f) {
                    updated.addAndGet(libraryDao.updateAudioSpecs(
                            track.id,
                            track.codec,
                            track.bitrateKbps,
                            track.sampleRateHz,
                            track.bitsPerSample,
                            track.channelCount,
                            track.replayGainTrackDb,
                            track.replayGainAlbumDb,
                            now
                    ));
                    playbackDao.updateAudioSpecs(
                            track.id,
                            track.codec,
                            track.bitrateKbps,
                            track.sampleRateHz,
                            track.bitsPerSample,
                            track.channelCount,
                            track.replayGainTrackDb,
                            track.replayGainAlbumDb
                    );
                }
                if (hasIdentityTags) {
                    TrackEntity identityTrack = TrackEntityMapper.entity(track, now);
                    musicIdentityStore.ensureTracks(
                            List.of(identityTrack),
                            identityTags(List.of(track)),
                            now
                    );
                }
            }
        });
        return updated.get();
    }

    public boolean trackExistsByDataPath(String dataPath) {
        return dataPath != null && !dataPath.isEmpty() && libraryDao.trackCountByDataPath(dataPath) > 0;
    }

    public void setFavorite(long trackId, boolean favorite) {
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            long recordingId = musicIdentityStore.recordingIdForTrack(trackId);
            if (recordingId <= 0L) {
                TrackEntity track = libraryDao.loadTrack(trackId);
                if (track != null) {
                    musicIdentityStore.ensureTracks(List.of(track), Collections.emptyMap(), now);
                    recordingId = musicIdentityStore.recordingIdForTrack(trackId);
                }
            }
            if (recordingId <= 0L) {
                return;
            }
            if (favorite) {
                RecordingFavoriteEntity existing = libraryDao.recordingFavorite(recordingId);
                libraryDao.putRecordingFavorite(new RecordingFavoriteEntity(
                        recordingId,
                        existing == null ? now : existing.getCreatedAt(),
                        existing == null ? "LOCAL_ONLY" : existing.getSyncState()
                ));
            } else {
                libraryDao.deleteRecordingFavorite(recordingId);
            }
        });
    }

    public int deleteTracks(List<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) return 0;
        AtomicInteger removed = new AtomicInteger();
        database.runInTransaction(() -> {
            removed.set(deleteTracksInCurrentTransaction(trackIds));
            musicIdentityStore.pruneMissingTracks();
        });
        return removed.get();
    }

    /** Package-private transaction primitive for callers that already own the Room transaction. */
    int deleteTracksInCurrentTransaction(List<Long> trackIds) {
        return deleteTrackIdsInCurrentTransaction(trackIds);
    }

    public boolean isFavorite(long trackId) {
        return libraryDao.recordingFavoriteCountForTrack(trackId) > 0;
    }

    public Set<Long> loadFavoriteIds() {
        return new HashSet<>(libraryDao.loadRecordingFavoriteTrackIds());
    }

    public List<Track> loadFavoriteTracks() {
        return tracks(libraryDao.loadRecordingFavoriteTracks());
    }

    /** Returns the stable local UUID without triggering metadata lookup or any network request. */
    public String loadCanonicalId(long trackId) {
        return musicIdentityStore.canonicalIdForTrack(trackId);
    }

    public long loadRecordingId(long trackId) {
        return musicIdentityStore.recordingIdForTrack(trackId);
    }

    /** Returns only a verified canonical provider mapping; candidate matches still require ranking. */
    public String loadConfirmedProviderTrackId(long recordingId, String provider) {
        if (recordingId <= 0L || provider == null || provider.trim().isEmpty()) {
            return "";
        }
        TrackSourceMappingEntity source = musicIdentityDao.bestProviderSource(
                recordingId,
                provider.trim().toLowerCase(java.util.Locale.ROOT)
        );
        if (source == null || !source.getPlayable() || !"CONFIRMED".equals(source.getMatchStatus())) {
            return "";
        }
        return source.getProviderTrackId().trim();
    }

    /**
     * Confirms only the provider identity encoded by the persisted track itself. This is used for
     * a provider favorite pulled from that same provider; it must never be used for a search
     * candidate or a legacy title match.
     */
    public boolean confirmDirectProviderSource(
            long localTrackId,
            String provider,
            String providerTrackId
    ) {
        return confirmDirectProviderSource(localTrackId, provider, providerTrackId, true);
    }

    public boolean confirmDirectProviderSourceWithoutIdentityIngest(
            long localTrackId,
            String provider,
            String providerTrackId
    ) {
        return confirmDirectProviderSource(localTrackId, provider, providerTrackId, false);
    }

    private boolean confirmDirectProviderSource(
            long localTrackId,
            String provider,
            String providerTrackId,
            boolean ingestIdentity
    ) {
        String cleanProvider = provider == null
                ? ""
                : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String cleanProviderTrackId = providerTrackId == null ? "" : providerTrackId.trim();
        if (localTrackId <= 0L || cleanProvider.isEmpty() || cleanProviderTrackId.isEmpty()) {
            return false;
        }
        AtomicBoolean confirmed = new AtomicBoolean(false);
        AtomicLong confirmedRecordingId = new AtomicLong(0L);
        database.runInTransaction(() -> {
            TrackSourceMappingEntity source = musicIdentityDao.sourceForLocalTrack(localTrackId);
            if (source == null || source.getSourceId() == null
                    || !cleanProvider.equals(source.getProvider())
                    || !cleanProviderTrackId.equals(source.getProviderTrackId())) {
                return;
            }
            if ("CONFIRMED".equals(source.getMatchStatus())) {
                confirmedRecordingId.set(source.getRecordingId());
                confirmed.set(true);
                return;
            }
            int updated = musicIdentityDao.confirmDirectProviderSource(source.getSourceId());
            if (updated > 0) {
                musicIdentityDao.refreshActiveSource(source.getRecordingId());
                confirmedRecordingId.set(source.getRecordingId());
                confirmed.set(true);
            }
        });
        if (ingestIdentity && confirmed.get() && confirmedRecordingId.get() > 0L) {
            new SourceIdentityIngestor(database).ingestLocalTracks(List.of(localTrackId));
        }
        return confirmed.get();
    }

    /** Runs only from explicit/background synchronization, never while opening the library. */
    public int ingestConfirmedIdentitySources() {
        return new SourceIdentityIngestor(database).ingestAllConfirmedSources();
    }

    public int ingestConfirmedIdentitySources(List<Long> localTrackIds) {
        if (localTrackIds == null || localTrackIds.isEmpty()) return 0;
        return new SourceIdentityIngestor(database).ingestLocalTracks(localTrackIds);
    }

    public int ingestIdentityRecordings(List<Long> recordingIds) {
        if (recordingIds == null || recordingIds.isEmpty()) return 0;
        return new SourceIdentityIngestor(database).ingestRecordings(recordingIds);
    }

    /** Backfills only legacy or algorithm-stale confirmed sources; safe for background startup. */
    public int ingestPendingConfirmedIdentitySources() {
        return new SourceIdentityIngestor(database).ingestPendingConfirmedSources();
    }

    public void updateFavoriteSyncState(long recordingId, String syncState) {
        if (recordingId <= 0L) {
            return;
        }
        String normalized = syncState == null ? "LOCAL_ONLY" : syncState.trim();
        if (!FAVORITE_SYNC_STATES.contains(normalized)) {
            normalized = "LOCAL_ONLY";
        }
        libraryDao.updateRecordingFavoriteSyncState(
                recordingId,
                normalized
        );
    }

    public String loadStreamingTrackMatch(String localKey, String provider) {
        if (localKey == null || localKey.isEmpty() || provider == null || provider.isEmpty()) {
            return "";
        }
        StreamingTrackMatchEntity match = streamingMatchDao.match(localKey, provider);
        return match == null ? "" : match.getProviderTrackId();
    }

    /**
     * Loads the complete current/legacy streaming match snapshot with two fixed queries. This is
     * intentionally keyed by local track id so callers can build the library without T x P Room
     * lookups. Current canonical mappings remain authoritative over the v15 compatibility table.
     */
    public Map<Long, Map<String, String>> loadStreamingTrackMatches(
            List<Track> tracks,
            List<String> providers,
            Map<Long, List<String>> legacyKeysByTrack
    ) {
        if (tracks == null || tracks.isEmpty() || providers == null || providers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<String, String>> currentByTrack = new HashMap<>();
        for (TrackStreamingMatchRow row : musicIdentityDao.trackStreamingMatches()) {
            String provider = row.getProvider() == null ? "" : row.getProvider().trim();
            String value = row.getStoredCandidate() == 1
                    ? storedMatchFromEvidence(row.getEvidenceJson())
                    : (row.getProviderTrackId() == null ? "" : row.getProviderTrackId().trim());
            if (provider.isEmpty() || value.isEmpty()) {
                continue;
            }
            currentByTrack
                    .computeIfAbsent(row.getLocalTrackId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(provider, value);
        }

        Map<String, Map<String, String>> legacyByKey = new HashMap<>();
        for (StreamingTrackMatchEntity match : streamingMatchDao.allMatches()) {
            String key = match.getLocalKey() == null ? "" : match.getLocalKey();
            String provider = match.getProvider() == null ? "" : match.getProvider().trim();
            if (key.isEmpty() || provider.isEmpty()) {
                continue;
            }
            legacyByKey
                    .computeIfAbsent(key, ignored -> new HashMap<>())
                    .put(provider, match.getProviderTrackId() == null ? "" : match.getProviderTrackId());
        }

        Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (Track track : tracks) {
            if (track == null) {
                continue;
            }
            Map<String, String> current = currentByTrack.getOrDefault(track.id, Collections.emptyMap());
            List<String> legacyKeys = legacyKeysByTrack == null
                    ? Collections.emptyList()
                    : legacyKeysByTrack.getOrDefault(track.id, Collections.emptyList());
            for (String provider : providers) {
                String cleanProvider = provider == null ? "" : provider.trim();
                if (cleanProvider.isEmpty()) {
                    continue;
                }
                String currentValue = current.getOrDefault(cleanProvider, "").trim();
                String legacyValue = loadLegacyStreamingMatch(legacyKeys, cleanProvider, legacyByKey);
                recordStreamingMatchCompatibility(track.id, cleanProvider, currentValue, legacyValue);
                String selected = currentValue.isEmpty() ? legacyValue : currentValue;
                if (!selected.isEmpty()) {
                    result.computeIfAbsent(track.id, ignored -> new LinkedHashMap<>())
                            .put(cleanProvider, selected);
                }
            }
        }
        return result;
    }

    /** New identity tables first; the v15 table is a read-only compatibility fallback. */
    public String loadStreamingTrackMatch(Track track, String provider, List<String> legacyKeys) {
        if (track == null || provider == null || provider.isEmpty()) {
            return "";
        }
        long recordingId = musicIdentityStore.recordingIdForTrack(track.id);
        String current = recordingId <= 0L ? "" : loadCurrentStreamingMatch(recordingId, provider);
        String legacy = loadLegacyStreamingMatch(legacyKeys, provider);
        recordStreamingMatchCompatibility(track.id, provider, current, legacy);
        return current.isEmpty() ? legacy : current;
    }

    public void saveStreamingTrackMatch(
            String localKey,
            String provider,
            String providerTrackId,
            Track track
    ) {
        if (localKey == null || localKey.isEmpty()
                || provider == null || provider.isEmpty()
                || providerTrackId == null || providerTrackId.isEmpty()) {
            return;
        }
        if (track == null) {
            return;
        }
        if (!ProviderRolePolicy.canPersistCanonicalSource(provider)) {
            Log.d(TAG, "Ignoring resolver-only provider identity provider=" + provider);
            return;
        }
        long recordingId = musicIdentityStore.recordingIdForTrack(track.id);
        if (recordingId <= 0L) {
            Log.w(TAG, "Skipping streaming match without canonical recording trackId=" + track.id);
            return;
        }
        String cleanProvider = provider.trim().toLowerCase(java.util.Locale.ROOT);
        String cleanMatch = providerTrackId.trim();
        if (isStoredMatchPayload(cleanMatch)) {
            saveStoredMatchCandidate(recordingId, cleanProvider, cleanMatch, track);
        } else {
            ProviderSourceIdentityWriter.CandidateWriteResult result = saveProviderSource(
                    recordingId,
                    cleanProvider,
                    cleanMatch,
                    track
            );
            if (result.isStored()) {
                musicIdentityDao.deleteCandidate(storedMatchCandidateId(recordingId, cleanProvider));
            } else {
                musicIdentityDao.deleteCandidate(storedMatchCandidateId(recordingId, cleanProvider));
                Long ownerRecordingId = result.getOwnerRecordingId();
                if (ownerRecordingId != null && ownerRecordingId > 0L) {
                    ingestIdentityRecordings(java.util.Arrays.asList(recordingId, ownerRecordingId));
                }
            }
        }
    }

    public void replaceStreamingTrackMatches(
            List<String> localKeys,
            String provider,
            String catalogKey,
            String providerTrackId,
            Track track
    ) {
        if (localKeys == null || localKeys.isEmpty()
                || provider == null || provider.isEmpty()
                || catalogKey == null || catalogKey.isEmpty()
                || providerTrackId == null || providerTrackId.isEmpty()) {
            return;
        }
        saveStreamingTrackMatch(catalogKey, provider, providerTrackId, track);
    }

    public String streamingMatchCompatibilitySummary() {
        return "compared=" + legacyMatchComparisons.get()
                + ",diverged=" + legacyMatchDivergences.get();
    }

    private String loadCurrentStreamingMatch(long recordingId, String provider) {
        IdentityCandidateEntity stored = musicIdentityDao.candidate(
                "RECORDING",
                recordingId,
                provider,
                STORED_MATCH_ITEM_ID
        );
        if (stored != null && "PENDING".equals(stored.getStatus())
                && stored.getEvidenceJson() != null && !stored.getEvidenceJson().isEmpty()) {
            String value = storedMatchFromEvidence(stored.getEvidenceJson());
            if (!value.isEmpty()) {
                return value;
            }
        }
        TrackSourceMappingEntity source = musicIdentityDao.bestProviderMatch(recordingId, provider);
        return source == null ? "" : source.getProviderTrackId().trim();
    }

    private String loadLegacyStreamingMatch(List<String> keys, String provider) {
        if (keys == null) {
            return "";
        }
        String fallback = "";
        for (String key : keys) {
            String value = loadStreamingTrackMatch(key, provider).trim();
            if (value.startsWith(STRUCTURED_MATCH_PREFIX)) {
                return value;
            }
            if (fallback.isEmpty() && !value.isEmpty()) {
                fallback = value;
            }
        }
        return fallback;
    }

    private static String loadLegacyStreamingMatch(
            List<String> keys,
            String provider,
            Map<String, Map<String, String>> legacyByKey
    ) {
        if (keys == null || legacyByKey == null || legacyByKey.isEmpty()) {
            return "";
        }
        String fallback = "";
        for (String key : keys) {
            String value = legacyByKey.getOrDefault(key, Collections.emptyMap())
                    .getOrDefault(provider, "")
                    .trim();
            if (value.startsWith(STRUCTURED_MATCH_PREFIX)) {
                return value;
            }
            if (fallback.isEmpty() && !value.isEmpty()) {
                fallback = value;
            }
        }
        return fallback;
    }

    private void recordStreamingMatchCompatibility(
            long trackId,
            String provider,
            String current,
            String legacy
    ) {
        if (current.isEmpty() && legacy.isEmpty()) {
            return;
        }
        legacyMatchComparisons.incrementAndGet();
        if (!current.isEmpty() && !legacy.isEmpty() && !current.equals(legacy)) {
            legacyMatchDivergences.incrementAndGet();
            Log.w(TAG, "Streaming match divergence provider=" + provider
                    + " trackId=" + trackId
                    + " currentHash=" + Integer.toHexString(current.hashCode())
                    + " legacyHash=" + Integer.toHexString(legacy.hashCode()));
        }
    }

    private ProviderSourceIdentityWriter.CandidateWriteResult saveProviderSource(
            long recordingId,
            String provider,
            String providerTrackId,
            Track track
    ) {
        return providerSourceIdentityWriter.saveUnverifiedCandidate(
                recordingId,
                provider,
                providerTrackId,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                0.75
        );
    }

    private void saveStoredMatchCandidate(
            long recordingId,
            String provider,
            String storedMatch,
            Track track
    ) {
        long now = System.currentTimeMillis();
        String candidateId = storedMatchCandidateId(recordingId, provider);
        musicIdentityDao.upsert(new IdentityCandidateEntity(
                candidateId,
                "RECORDING",
                recordingId,
                provider,
                STORED_MATCH_ITEM_ID,
                track.title == null ? "" : track.title,
                track.artist == null ? "" : track.artist,
                track.album == null ? "" : track.album,
                Math.max(0L, track.durationMs),
                "",
                "UNKNOWN",
                0.75,
                "PENDING",
                storedMatchEvidence(storedMatch),
                now,
                now
        ));
    }

    private static boolean isStoredMatchPayload(String value) {
        return value.startsWith(STRUCTURED_MATCH_PREFIX)
                || value.startsWith("__echo_no_source__")
                || value.startsWith("__echo_no_source_lx_v2__");
    }

    private static String storedMatchCandidateId(long recordingId, String provider) {
        return UUID.nameUUIDFromBytes(
                ("STREAMING_MATCH:" + recordingId + ":" + provider).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private static String storedMatchFromEvidence(String evidenceJson) {
        try {
            return new JSONObject(evidenceJson).optString("storedMatch", "").trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String storedMatchEvidence(String storedMatch) {
        try {
            return new JSONObject()
                    .put("storedMatch", storedMatch)
                    .put("source", "RUNTIME_MATCH")
                    .toString();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to encode streaming match", error);
        }
    }

    public void replaceTrackAndMigrateReferences(long oldTrackId, Track replacement) {
        if (replacement == null) {
            return;
        }
        Map<Long, String> contentSignatures = contentSignatures(List.of(replacement));
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            long oldRecordingId = musicIdentityStore.recordingIdForTrack(oldTrackId);
            RecordingFavoriteEntity canonicalFavorite = oldRecordingId > 0L
                    ? libraryDao.recordingFavorite(oldRecordingId)
                    : null;
            upsertTrackRows(
                    List.of(TrackEntityMapper.entity(replacement, now)),
                    identityTags(List.of(replacement)),
                    contentSignatures,
                    now
            );
            long newTrackId = replacement.id;
            if (oldTrackId == newTrackId) {
                return;
            }

            long newRecordingId = musicIdentityStore.recordingIdForTrack(newTrackId);
            if (oldRecordingId > 0L && newRecordingId > 0L && oldRecordingId != newRecordingId) {
                new RoomRecordingIdentityRepository(database).mergeRecordingsInCurrentTransaction(
                        oldRecordingId,
                        newRecordingId
                );
            }
            if (canonicalFavorite != null && newRecordingId > 0L) {
                libraryDao.putRecordingFavorite(new RecordingFavoriteEntity(
                        newRecordingId,
                        canonicalFavorite.getCreatedAt(),
                        canonicalFavorite.getSyncState()
                ));
                if (oldRecordingId > 0L && oldRecordingId != newRecordingId) {
                    libraryDao.deleteRecordingFavorite(oldRecordingId);
                }
            }
            migrateHistory(oldTrackId, newTrackId);
            historyDao.migrateEvents(oldTrackId, newTrackId);
            migratePlaylistReferences(oldTrackId, newTrackId, now);
            migrateQueueReferences(oldTrackId, newTrackId, replacement);
            if (playbackPersistence.loadPositionTrackId() == oldTrackId) {
                playbackPersistence.savePosition(newTrackId, playbackPersistence.loadPositionMs());
            }
            libraryDao.deleteTracksByIds(List.of(oldTrackId));
            musicIdentityStore.pruneMissingTracks();
        });
        ingestConfirmedIdentitySources(List.of(replacement.id));
    }

    static String librarySourceKey(Track track) {
        if (track == null) {
            return "";
        }
        String dataPath = track.dataPath == null ? "" : track.dataPath.trim();
        if (dataPath.startsWith("stream:")
                || dataPath.startsWith("streaming:")
                || dataPath.startsWith("webdav:")) {
            return "";
        }
        String uri = track.contentUri == null ? "" : track.contentUri.toString().trim();
        if (!uri.isEmpty()) {
            return "uri:" + uri;
        }
        return dataPath.isEmpty() ? "" : "path:" + dataPath;
    }

    private int deleteTrackIdsInCurrentTransaction(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Track> queueBefore = playbackPersistence.loadQueueSnapshots();
        int queueIndexBefore = playbackPersistence.loadQueueIndex();
        int existing = 0;
        for (List<Long> batch : batches(ids)) {
            for (Long id : batch) {
                if (id != null && libraryDao.loadTrack(id) != null) {
                    existing++;
                }
            }
            libraryDao.deleteTracksByIds(batch);
        }
        cleanupDeletedTrackReferences(ids, queueBefore, queueIndexBefore);
        return existing;
    }

    private void cleanupDeletedTrackReferences(
            List<Long> ids,
            List<Track> queueBefore,
            int queueIndexBefore
    ) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        HashSet<Long> deleted = new HashSet<>(ids);
        for (List<Long> batch : batches(ids)) {
            historyDao.deleteHistory(batch);
            historyDao.deleteEvents(batch);
            playlistDao.deleteTrackReferences(batch);
            playbackDao.deleteTracks(batch);
        }

        ArrayList<Track> remainingQueue = new ArrayList<>();
        int removedBeforeCurrent = 0;
        for (int index = 0; index < queueBefore.size(); index++) {
            Track track = queueBefore.get(index);
            if (deleted.contains(track.id)) {
                if (queueIndexBefore >= 0 && index < queueIndexBefore) {
                    removedBeforeCurrent++;
                }
            } else {
                remainingQueue.add(track);
            }
        }
        int newIndex = -1;
        if (!remainingQueue.isEmpty() && queueIndexBefore >= 0) {
            newIndex = Math.min(
                    Math.max(queueIndexBefore - removedBeforeCurrent, 0),
                    remainingQueue.size() - 1
            );
        }
        playbackPersistence.saveQueue(remainingQueue, newIndex);
        if (deleted.contains(playbackPersistence.loadPositionTrackId())) {
            playbackPersistence.savePosition(-1L, 0L);
        }
    }

    private void migrateHistory(long oldTrackId, long newTrackId) {
        PlayHistoryEntity oldHistory = historyDao.history(oldTrackId);
        if (oldHistory == null) {
            return;
        }
        PlayHistoryEntity newHistory = historyDao.history(newTrackId);
        long playedAt = oldHistory.getPlayedAt();
        int playCount = oldHistory.getPlayCount();
        if (newHistory != null) {
            playedAt = Math.max(playedAt, newHistory.getPlayedAt());
            playCount += newHistory.getPlayCount();
        }
        historyDao.upsertHistory(new PlayHistoryEntity(newTrackId, playedAt, Math.max(playCount, 1)));
        historyDao.deleteHistory(oldTrackId);
    }

    private void migratePlaylistReferences(long oldTrackId, long newTrackId, long now) {
        for (PlaylistTrackEntity reference : playlistDao.playlistReferences(oldTrackId)) {
            playlistDao.insertPlaylistTrack(new PlaylistTrackEntity(
                    reference.getPlaylistId(),
                    newTrackId,
                    reference.getPosition(),
                    reference.getAddedAt()
            ));
            playlistDao.touchPlaylist(reference.getPlaylistId(), now);
        }
        playlistDao.deleteTrackReferences(oldTrackId);
    }

    private void migrateQueueReferences(long oldTrackId, long newTrackId, Track replacement) {
        List<Track> queue = playbackPersistence.loadQueueSnapshots();
        int currentIndex = playbackPersistence.loadQueueIndex();
        boolean hasOld = false;
        boolean hasNew = false;
        for (Track track : queue) {
            hasOld |= track.id == oldTrackId;
            hasNew |= track.id == newTrackId;
        }
        if (!hasOld) {
            return;
        }
        if (!hasNew) {
            ArrayList<Track> migrated = new ArrayList<>(queue.size());
            for (Track track : queue) {
                migrated.add(track.id == oldTrackId ? replacement : track);
            }
            playbackPersistence.saveQueue(migrated, currentIndex);
            return;
        }

        int preferredIndex = -1;
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            long currentId = queue.get(currentIndex).id;
            if (currentId == oldTrackId || currentId == newTrackId) {
                preferredIndex = currentIndex;
            }
        }
        if (preferredIndex < 0) {
            for (int index = 0; index < queue.size(); index++) {
                long id = queue.get(index).id;
                if (id == oldTrackId || id == newTrackId) {
                    preferredIndex = index;
                    break;
                }
            }
        }

        ArrayList<Track> collapsed = new ArrayList<>();
        int newCurrentIndex = -1;
        boolean currentIsMerged = currentIndex >= 0
                && currentIndex < queue.size()
                && (queue.get(currentIndex).id == oldTrackId || queue.get(currentIndex).id == newTrackId);
        for (int index = 0; index < queue.size(); index++) {
            Track track = queue.get(index);
            boolean merged = track.id == oldTrackId || track.id == newTrackId;
            if (merged && index != preferredIndex) {
                continue;
            }
            if (merged) {
                track = replacement;
            }
            if (index == currentIndex || index == preferredIndex && currentIsMerged) {
                newCurrentIndex = collapsed.size();
            }
            collapsed.add(track);
        }
        if (newCurrentIndex < 0 && currentIndex >= 0 && !collapsed.isEmpty()) {
            newCurrentIndex = Math.min(Math.max(currentIndex, 0), collapsed.size() - 1);
        }
        playbackPersistence.saveQueue(collapsed, newCurrentIndex);
    }

    private static ArrayList<TrackEntity> trackEntities(
            List<Track> values,
            Set<String> exclusions,
            long updatedAt
    ) {
        ArrayList<TrackEntity> rows = new ArrayList<>();
        int index = 0;
        for (Track track : values) {
            if ((index++ & 63) == 0) {
                throwIfInterrupted();
            }
            TrackSourceIdentity identity = TrackSourceIdentity.from(
                    track.id,
                    track.contentUri == null ? "" : track.contentUri.toString(),
                    track.dataPath
            );
            if (ProviderRolePolicy.isPlaybackResolver(identity.provider)
                    || exclusions.contains(librarySourceKey(track))) {
                continue;
            }
            rows.add(TrackEntityMapper.entity(track, updatedAt));
        }
        return rows;
    }

    private void upsertTrackRows(
            List<TrackEntity> rows,
            Map<Long, TrackIdentityTags> identityTags,
            Map<Long, String> contentSignatures,
            long now
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<TrackEntity> uniqueRows = uniqueSourceRows(rows);
        ArrayList<Long> ids = new ArrayList<>(uniqueRows.size());
        for (TrackEntity row : uniqueRows) {
            if (row != null && row.getId() != null) ids.add(row.getId());
        }
        HashMap<Long, TrackEntity> existingById = new HashMap<>();
        for (List<Long> batch : batches(ids)) {
            for (TrackEntity existing : libraryDao.loadTracksByIds(batch)) {
                if (existing != null && existing.getId() != null) {
                    existingById.put(existing.getId(), existing);
                }
            }
        }
        ArrayList<TrackEntity> persistedRows = new ArrayList<>(uniqueRows.size());
        for (TrackEntity row : uniqueRows) {
            TrackEntity existing = row == null || row.getId() == null
                    ? null
                    : existingById.get(row.getId());
            TrackEntity persisted = TrackEntityMapper.preserveAudioSpecs(row, existing);
            TrackSourceIdentity identity = TrackSourceIdentity.from(
                    persisted.getId(),
                    persisted.getContentUri(),
                    persisted.getDataPath()
            );
            TrackSourceMappingEntity owner = musicIdentityDao.source(
                    identity.provider,
                    identity.providerTrackId
            );
            if (owner != null
                    && owner.getLocalTrackId() != null
                    && !owner.getLocalTrackId().equals(persisted.getId())
                    && libraryDao.loadTrack(owner.getLocalTrackId()) != null) {
                continue;
            }
            persistedRows.add(persisted);
        }
        if (persistedRows.isEmpty()) {
            return;
        }
        libraryDao.upsertTracks(persistedRows);
        musicIdentityStore.ensureTracks(persistedRows, identityTags, contentSignatures, now);
    }

    private static List<TrackEntity> uniqueSourceRows(List<TrackEntity> rows) {
        LinkedHashMap<String, TrackEntity> bySource = new LinkedHashMap<>();
        for (TrackEntity row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            TrackSourceIdentity identity = TrackSourceIdentity.from(
                    row.getId(),
                    row.getContentUri(),
                    row.getDataPath()
            );
            String key = identity.provider + '\u0000' + identity.providerTrackId;
            TrackEntity selected = bySource.get(key);
            if (selected == null || selected.getId().equals(row.getId())) {
                bySource.put(key, row);
            }
        }
        return new ArrayList<>(bySource.values());
    }

    public void markAudioSpecAttempt(long trackId, long attemptedAt) {
        libraryDao.touchAudioSpecAttempt(trackId, attemptedAt);
    }

    private static Map<Long, TrackIdentityTags> identityTags(List<Track> values) {
        HashMap<Long, TrackIdentityTags> tags = new HashMap<>();
        if (values == null) {
            return tags;
        }
        for (Track track : values) {
            if (track != null && track.identityTags != null && !track.identityTags.isEmpty()) {
                tags.put(track.id, track.identityTags);
            }
        }
        return tags;
    }

    private Map<Long, String> contentSignatures(List<Track> values) {
        HashMap<Long, String> signatures = new HashMap<>();
        if (values == null) {
            return signatures;
        }
        for (Track track : values) {
            if (track == null) {
                continue;
            }
            String signature;
            try {
                String value = contentSignatureProvider.apply(track);
                signature = value == null ? "" : value.trim();
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to compute content signature for track " + track.id, error);
                signature = "";
            }
            if (!signature.isEmpty()) {
                signatures.put(track.id, signature);
            }
        }
        return signatures;
    }

    private void deleteTrackRows(List<Long> ids) {
        for (List<Long> batch : batches(ids)) {
            libraryDao.deleteTracksByIds(batch);
        }
    }

    private static List<List<Long>> batches(List<Long> ids) {
        ArrayList<List<Long>> batches = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += SQLITE_IN_BATCH_SIZE) {
            batches.add(ids.subList(start, Math.min(ids.size(), start + SQLITE_IN_BATCH_SIZE)));
        }
        return batches;
    }

    private static List<Long> localTrackIds(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<Long> ids = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            if (track != null && track.id != 0L) {
                ids.add(track.id);
            }
        }
        return ids;
    }

    private static List<Track> tracks(List<TrackEntity> rows) {
        ArrayList<Track> values = new ArrayList<>(rows.size());
        for (TrackEntity row : rows) {
            Track track = TrackEntityMapper.track(row);
            if (track != null) {
                values.add(track);
            }
        }
        return values;
    }

    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Library database replacement cancelled");
        }
    }
}
