package app.yukine.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

import app.yukine.data.room.RemoteSourceDao;
import app.yukine.data.room.RemoteSourceEntity;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;
import app.yukine.identity.RecordingVariantRecognizer;
import app.yukine.identity.RecordingVariantType;
import app.yukine.security.SecureSecretStore;

/** WebDAV source metadata and its cached-track lifecycle. */
public final class RemoteSourceRepository {
    private final YukineDatabase database;
    private final RemoteSourceDao dao;
    private final LibraryRepository libraryRepository;
    private final ConcurrentHashMap<Long, RemoteSource> sourceCache = new ConcurrentHashMap<>();

    public RemoteSourceRepository(YukineDatabase database, LibraryRepository libraryRepository) {
        this.database = database;
        dao = database.remoteSourceDao();
        this.libraryRepository = libraryRepository;
    }

    public List<RemoteSource> loadSources() {
        ArrayList<RemoteSource> sources = new ArrayList<>();
        for (RemoteSourceEntity row : dao.loadSources()) {
            RemoteSource source = source(row);
            sources.add(source);
            sourceCache.put(source.id, source);
        }
        return sources;
    }

    public RemoteSource cachedSource(long sourceId) {
        return sourceCache.get(sourceId);
    }

    public RemoteSource loadSource(long sourceId) {
        RemoteSourceEntity row = dao.loadSource(sourceId);
        return row == null ? null : source(row);
    }

    public long save(RemoteSource source) {
        if (source == null) {
            return -1L;
        }
        AtomicLong savedId = new AtomicLong(-1L);
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            if (source.id > 0L) {
                RemoteSourceEntity updated = entity(source, source.id, now);
                if (dao.update(updated) > 0) {
                    savedId.set(source.id);
                    return;
                }
            }
            savedId.set(dao.upsert(entity(source, null, now)));
        });
        if (savedId.get() > 0L) {
            RemoteSource saved = new RemoteSource(
                    savedId.get(), source.type, source.name, source.baseUrl, source.username,
                    source.password, source.rootPath, source.lastStatus, System.currentTimeMillis()
            );
            sourceCache.put(saved.id, saved);
        }
        return savedId.get();
    }

    public void updateStatus(long sourceId, String status) {
        dao.updateStatus(
                sourceId,
                status == null ? "" : status,
                System.currentTimeMillis()
        );
    }

    public List<Track> loadTracks(long sourceId) {
        return libraryRepository.loadTracksByDataPathPattern(pattern(sourceId));
    }

    public int replaceTracks(long sourceId, List<Track> tracks) {
        return applyIncrementalTracks(loadTracks(sourceId), tracks);
    }

    public int applyIncrementalTracks(List<Track> previous, List<Track> current) {
        Map<String, Track> previousByPath = byDataPath(previous);
        Map<String, Track> currentByPath = byDataPath(
                reconcileMovedTracks(previousByPath, byDataPath(current))
        );
        Set<Long> currentTrackIds = new HashSet<>();
        for (Track track : currentByPath.values()) {
            currentTrackIds.add(track.id);
        }
        ArrayList<Long> removedIds = new ArrayList<>();
        for (Map.Entry<String, Track> entry : previousByPath.entrySet()) {
            if (!currentByPath.containsKey(entry.getKey())
                    && !currentTrackIds.contains(entry.getValue().id)) {
                removedIds.add(entry.getValue().id);
            }
        }
        ArrayList<Track> upserts = new ArrayList<>();
        for (Map.Entry<String, Track> entry : currentByPath.entrySet()) {
            if (previousByPath.get(entry.getKey()) != entry.getValue()) {
                upserts.add(entry.getValue());
            }
        }
        return libraryRepository.applyTrackDelta(removedIds, upserts);
    }

    private static List<Track> reconcileMovedTracks(
            Map<String, Track> previousByPath,
            Map<String, Track> currentByPath
    ) {
        ArrayList<Track> missingPrevious = new ArrayList<>();
        for (Map.Entry<String, Track> entry : previousByPath.entrySet()) {
            if (!currentByPath.containsKey(entry.getKey())) {
                missingPrevious.add(entry.getValue());
            }
        }
        LinkedHashMap<Track, List<Track>> candidates = new LinkedHashMap<>();
        LinkedHashMap<Long, Integer> oldUseCounts = new LinkedHashMap<>();
        for (Map.Entry<String, Track> entry : currentByPath.entrySet()) {
            if (previousByPath.containsKey(entry.getKey())) {
                continue;
            }
            Track current = entry.getValue();
            ArrayList<Track> matches = new ArrayList<>();
            for (Track previous : missingPrevious) {
                if (sameRecordingForRelocation(previous, current)) {
                    matches.add(previous);
                    oldUseCounts.put(previous.id, oldUseCounts.getOrDefault(previous.id, 0) + 1);
                }
            }
            candidates.put(current, matches);
        }
        ArrayList<Track> reconciled = new ArrayList<>(currentByPath.size());
        for (Track current : currentByPath.values()) {
            List<Track> matches = candidates.get(current);
            if (matches != null && matches.size() == 1
                    && oldUseCounts.getOrDefault(matches.get(0).id, 0) == 1) {
                reconciled.add(trackWithId(current, matches.get(0).id));
            } else {
                reconciled.add(current);
            }
        }
        return reconciled;
    }

    private static boolean sameRecordingForRelocation(Track previous, Track current) {
        if (previous == null || current == null || previous.id == current.id) {
            return previous != null && current != null && previous.id == current.id;
        }
        RecordingVariantType previousVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                previous.title,
                previous.album
        );
        RecordingVariantType currentVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                current.title,
                current.album
        );
        if (isProtectedVariant(previousVariant)
                && isProtectedVariant(currentVariant)
                && previousVariant != currentVariant) {
            return false;
        }
        if (previous.durationMs > 0L && current.durationMs > 0L
                && Math.abs(previous.durationMs - current.durationMs) > 2_000L) {
            return false;
        }
        if (hasStrongIdentifierConflict(previous.identityTags, current.identityTags)) {
            return false;
        }
        if (sharesStrongIdentifier(previous.identityTags, current.identityTags)) {
            return true;
        }
        return normalized(previous.title).equals(normalized(current.title))
                && normalized(previous.artist).equals(normalized(current.artist));
    }

    private static boolean sharesStrongIdentifier(TrackIdentityTags first, TrackIdentityTags second) {
        return sameNonEmpty(first.recordingMusicBrainzId, second.recordingMusicBrainzId)
                || sameNonEmpty(first.isrc, second.isrc)
                || sameNonEmpty(first.acoustId, second.acoustId);
    }

    private static boolean hasStrongIdentifierConflict(TrackIdentityTags first, TrackIdentityTags second) {
        return differentNonEmpty(first.recordingMusicBrainzId, second.recordingMusicBrainzId)
                || differentNonEmpty(first.isrc, second.isrc)
                || differentNonEmpty(first.acoustId, second.acoustId);
    }

    private static boolean sameNonEmpty(String first, String second) {
        return !clean(first).isEmpty() && clean(first).equalsIgnoreCase(clean(second));
    }

    private static boolean differentNonEmpty(String first, String second) {
        return !clean(first).isEmpty()
                && !clean(second).isEmpty()
                && !clean(first).equalsIgnoreCase(clean(second));
    }

    private static boolean isProtectedVariant(RecordingVariantType type) {
        return type == RecordingVariantType.ORIGINAL
                || type == RecordingVariantType.LIVE
                || type == RecordingVariantType.REMIX
                || type == RecordingVariantType.COVER
                || type == RecordingVariantType.ACOUSTIC
                || type == RecordingVariantType.INSTRUMENTAL
                || type == RecordingVariantType.KARAOKE;
    }

    private static String normalized(String value) {
        return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static Track trackWithId(Track track, long id) {
        return new Track(
                id,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                track.contentUri,
                track.dataPath,
                track.albumId,
                track.albumArtUri,
                track.codec,
                track.bitrateKbps,
                track.sampleRateHz,
                track.bitsPerSample,
                track.channelCount,
                track.replayGainTrackDb,
                track.replayGainAlbumDb,
                track.identityTags
        );
    }

    private static Map<String, Track> byDataPath(List<Track> tracks) {
        LinkedHashMap<String, Track> values = new LinkedHashMap<>();
        if (tracks == null) return values;
        for (Track track : tracks) {
            if (track != null && track.dataPath != null && !track.dataPath.isEmpty()) {
                values.put(track.dataPath, track);
            }
        }
        return values;
    }

    public void delete(long sourceId) {
        database.runInTransaction(() -> {
            libraryRepository.deleteTracksByDataPathPattern(pattern(sourceId));
            dao.delete(sourceId);
        });
        sourceCache.remove(sourceId);
    }

    private static String pattern(long sourceId) {
        return "webdav:" + sourceId + ":%";
    }

    private static RemoteSourceEntity entity(RemoteSource source, Long id, long updatedAt) {
        String encrypted = SecureSecretStore.INSTANCE.encryptOrPlain(source.password);
        return new RemoteSourceEntity(
                id,
                source.type,
                source.name,
                source.baseUrl,
                source.username,
                encrypted == null ? "" : encrypted,
                source.rootPath,
                source.lastStatus,
                updatedAt
        );
    }

    private static RemoteSource source(RemoteSourceEntity row) {
        String password = SecureSecretStore.INSTANCE.decryptOrPlain(row.getPassword());
        return new RemoteSource(
                row.getId() == null ? -1L : row.getId(),
                row.getType(),
                row.getName(),
                row.getBaseUrl(),
                row.getUsername(),
                password == null ? "" : password,
                row.getRootPath(),
                row.getLastStatus(),
                row.getUpdatedAt()
        );
    }
}
