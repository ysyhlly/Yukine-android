package app.yukine.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import app.yukine.data.room.ArtistSourceMappingEntity;
import app.yukine.data.room.ArtistAliasEntity;
import app.yukine.data.room.ArtistCreditParser;
import app.yukine.data.room.CanonicalArtistEntity;
import app.yukine.data.room.CanonicalRecordingEntity;
import app.yukine.data.room.CanonicalWorkEntity;
import app.yukine.data.room.IdentityResolutionJobEntity;
import app.yukine.data.room.IdentityCandidateEntity;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.RecordingArtistCreditEntity;
import app.yukine.data.room.RecordingIdentifierEntity;
import app.yukine.data.room.TrackEntity;
import app.yukine.data.room.TrackSourceIdentity;
import app.yukine.data.room.TrackSourceMappingEntity;
import app.yukine.data.room.YukineDatabase;
import app.yukine.identity.CanonicalRecording;
import app.yukine.identity.RecordingVariantRecognizer;
import app.yukine.identity.RecordingVariantType;
import app.yukine.identity.IdentityTextNormalizer;
import app.yukine.model.TrackIdentityTags;
import app.yukine.streaming.ProviderRolePolicy;

/**
 * Creates recording/source/artist identities in the caller's track persistence transaction.
 *
 * The hot Room graph uses integer IDs. UUIDs remain stable external identities for sync and export.
 * This owner is intentionally network-free; its pending jobs are consumed only by a later worker.
 */
final class OfflineMusicIdentityStore {
    private static final String STATUS_UNRESOLVED = "UNRESOLVED";
    private static final String METADATA_SOURCE = "LOCAL_CATALOG";

    private final MusicIdentityDao dao;
    private final YukineDatabase database;

    OfflineMusicIdentityStore(MusicIdentityDao dao) {
        this.dao = dao;
        this.database = null;
    }

    OfflineMusicIdentityStore(YukineDatabase database) {
        this.database = database;
        this.dao = database.musicIdentityDao();
    }

    void ensureTracks(List<TrackEntity> tracks, long now) {
        ensureTracks(tracks, Collections.emptyMap(), now);
    }

    void ensureTracks(
            List<TrackEntity> tracks,
            Map<Long, TrackIdentityTags> identityTags,
            long now
    ) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        ArrayList<Long> localTrackIds = new ArrayList<>(tracks.size());
        for (TrackEntity track : tracks) {
            long recordingId = ensureTrack(track, now);
            if (track != null && track.getId() != null) {
                localTrackIds.add(track.getId());
                if (identityTags != null) {
                    TrackIdentityTags tags = identityTags.get(track.getId());
                    if (tags != null && !tags.isEmpty()) {
                        applyIdentityTags(recordingId, tags, now);
                    }
                }
            }
        }
        if (database != null && !localTrackIds.isEmpty()) {
            new SourceIdentityIngestor(database).ingestLocalTracks(localTrackIds);
        }
    }

    String canonicalIdForTrack(long trackId) {
        String value = dao.canonicalIdForLocalTrack(trackId);
        return value == null ? "" : value;
    }

    long recordingIdForTrack(long trackId) {
        Long value = dao.recordingIdForLocalTrack(trackId);
        return value == null ? 0L : value;
    }

    void pruneMissingTracks() {
        dao.detachFavoriteSourcesForMissingTracks();
        dao.deleteSourcesForMissingTracks();
        dao.clearMissingActiveSources();
        dao.deleteOrphanCanonicalRecordings();
        dao.deleteOrphanWorks();
        dao.deleteOrphanArtists();
        dao.deleteDanglingCandidates();
        dao.deleteDanglingJobs();
    }

    private long ensureTrack(TrackEntity track, long now) {
        if (track == null || track.getId() == null) {
            return -1L;
        }
        long trackId = track.getId();
        TrackSourceIdentity identity = TrackSourceIdentity.from(
                trackId,
                track.getContentUri(),
                track.getDataPath()
        );
        if (!ProviderRolePolicy.canPersistCanonicalSource(identity.provider)) {
            return -1L;
        }
        TrackSourceMappingEntity localSource = dao.sourceForLocalTrack(trackId);
        TrackSourceMappingEntity keyedSource = dao.source(identity.provider, identity.providerTrackId);
        Long relocatedRecordingId = null;
        if (localSource != null && !sameProviderIdentity(localSource, identity)) {
            relocatedRecordingId = localSource.getRecordingId();
            if (localSource.getSourceId() != null) {
                dao.deleteSource(localSource.getSourceId());
            }
            localSource = null;
            dao.clearMissingActiveSources();
        }

        TrackSourceMappingEntity persistedSource = keyedSource != null ? keyedSource : localSource;
        TrackSourceMappingEntity dataPathAnchor = clean(track.getDataPath()).isEmpty()
                ? null
                : dao.sourceForDataPath(clean(track.getDataPath()));
        TrackSourceMappingEntity recordingAnchor = persistedSource != null
                ? persistedSource
                : dataPathAnchor;
        long recordingId;
        if (recordingAnchor != null) {
            recordingId = recordingAnchor.getRecordingId();
        } else if (relocatedRecordingId != null && dao.recording(relocatedRecordingId) != null) {
            recordingId = relocatedRecordingId;
        } else {
            recordingId = insertRecording(track, now);
        }

        dao.upsert(sourceEntity(persistedSource, recordingId, identity, track, trackId));
        TrackSourceMappingEntity saved = dao.source(identity.provider, identity.providerTrackId);
        if (saved != null) {
            dao.refreshActiveSource(saved.getRecordingId());
        }
        ensureArtistCredits(recordingId, track.getArtist(), now);
        dao.refreshWorkPrimaryCreator(recordingId, now);
        enqueueJob("RECORDING", recordingId, now);
        return recordingId;
    }

    private void applyIdentityTags(long initialRecordingId, TrackIdentityTags tags, long now) {
        long recordingId = initialRecordingId;
        recordingId = attachRecordingIdentifier(
                recordingId,
                "MUSICBRAINZ_RECORDING_ID",
                tags.recordingMusicBrainzId,
                now
        );
        recordingId = attachRecordingIdentifier(recordingId, "ISRC", tags.isrc, now);
        recordingId = attachRecordingIdentifier(recordingId, "ACOUSTID", tags.acoustId, now);
        recordingId = attachRecordingIdentifier(
                recordingId,
                "MUSICBRAINZ_WORK_ID",
                tags.workMusicBrainzId,
                now
        );
        attachArtistIdentifiers(recordingId, tags.artistMusicBrainzIds, now);
        dao.deleteOrphanArtists();
        dao.deleteDanglingCandidates();
        dao.deleteDanglingJobs();
    }

    private long attachRecordingIdentifier(
            long recordingId,
            String type,
            String value,
            long now
    ) {
        String cleanValue = clean(value);
        if (cleanValue.isEmpty()) {
            return recordingId;
        }
        RecordingIdentifierEntity existing = dao.identifier(type, "", cleanValue);
        if (existing != null && existing.getRecordingId() != recordingId) {
            if (database == null || !canAutoMerge(recordingId, existing.getRecordingId())) {
                saveTagConflict(
                        "RECORDING",
                        recordingId,
                        type,
                        cleanValue,
                        "IDENTITY_EVIDENCE_CONFLICT",
                        now
                );
                return recordingId;
            }
            CanonicalRecording merged = new RoomRecordingIdentityRepository(database)
                    .mergeRecordings(recordingId, existing.getRecordingId());
            recordingId = merged.getRecordingId();
        }
        CanonicalRecordingEntity recording = dao.recording(recordingId);
        if (recording == null) {
            return recordingId;
        }
        String currentValue = recordingIdentifierValue(recording, type);
        if (!currentValue.isEmpty() && !currentValue.equalsIgnoreCase(cleanValue)) {
            saveTagConflict(
                    "RECORDING",
                    recordingId,
                    type,
                    cleanValue,
                    "IDENTIFIER_VALUE_CONFLICT",
                    now
            );
            return recordingId;
        }
        dao.update(recordingWithIdentifier(recording, type, cleanValue, now));
        dao.upsert(new RecordingIdentifierEntity(
                recordingId,
                type,
                "",
                cleanValue,
                "EMBEDDED_TAG",
                1.0d,
                now
        ));
        return recordingId;
    }

    private static String recordingIdentifierValue(
            CanonicalRecordingEntity recording,
            String type
    ) {
        if ("MUSICBRAINZ_RECORDING_ID".equals(type)) {
            return recording.getMusicBrainzRecordingId();
        }
        if ("MUSICBRAINZ_WORK_ID".equals(type)) {
            return recording.getMusicBrainzWorkId();
        }
        if ("ISRC".equals(type)) {
            return recording.getIsrc();
        }
        if ("ACOUSTID".equals(type)) {
            return recording.getAcoustId();
        }
        return "";
    }

    private static CanonicalRecordingEntity recordingWithIdentifier(
            CanonicalRecordingEntity recording,
            String type,
            String value,
            long now
    ) {
        return new CanonicalRecordingEntity(
                recording.getId(),
                recording.getCanonicalUuid(),
                recording.getWorkId(),
                recording.getActiveSourceId(),
                "MUSICBRAINZ_RECORDING_ID".equals(type)
                        ? value
                        : recording.getMusicBrainzRecordingId(),
                "MUSICBRAINZ_WORK_ID".equals(type)
                        ? value
                        : recording.getMusicBrainzWorkId(),
                recording.getTitle(),
                recording.getPrimaryArtistDisplay(),
                recording.getDurationMs(),
                "ISRC".equals(type) ? value : recording.getIsrc(),
                "ACOUSTID".equals(type) ? value : recording.getAcoustId(),
                recording.getMatchStatus(),
                recording.getConfidence(),
                recording.getMetadataSource(),
                recording.getCreatedAt(),
                Math.max(now, recording.getUpdatedAt())
        );
    }

    private boolean canAutoMerge(long sourceRecordingId, long targetRecordingId) {
        CanonicalRecordingEntity source = dao.recording(sourceRecordingId);
        CanonicalRecordingEntity target = dao.recording(targetRecordingId);
        if (source == null || target == null) {
            return false;
        }
        if (!compatible(source.getMusicBrainzRecordingId(), target.getMusicBrainzRecordingId())
                || !compatible(source.getAcoustId(), target.getAcoustId())) {
            return false;
        }
        RecordingVariantType sourceVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                source.getTitle(),
                ""
        );
        RecordingVariantType targetVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                target.getTitle(),
                ""
        );
        if (isProtectedVariant(sourceVariant)
                && isProtectedVariant(targetVariant)
                && sourceVariant != targetVariant) {
            return false;
        }
        if (source.getDurationMs() > 0L && target.getDurationMs() > 0L
                && Math.abs(source.getDurationMs() - target.getDurationMs()) > 5_000L) {
            return false;
        }
        Set<Long> sourceArtists = primaryArtists(sourceRecordingId);
        Set<Long> targetArtists = primaryArtists(targetRecordingId);
        if (!sourceArtists.isEmpty() && !targetArtists.isEmpty()) {
            HashSet<Long> overlap = new HashSet<>(sourceArtists);
            overlap.retainAll(targetArtists);
            if (overlap.isEmpty()) {
                return false;
            }
            if (!source.getIsrc().isEmpty() && !target.getIsrc().isEmpty()
                    && !source.getIsrc().equalsIgnoreCase(target.getIsrc())
                    && !sourceArtists.equals(targetArtists)) {
                return false;
            }
        }
        return true;
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

    private Set<Long> primaryArtists(long recordingId) {
        HashSet<Long> artists = new HashSet<>();
        for (RecordingArtistCreditEntity credit : dao.credits(recordingId)) {
            if ("PRIMARY".equals(credit.getRole()) || "UNKNOWN".equals(credit.getRole())) {
                artists.add(credit.getArtistId());
            }
        }
        return artists;
    }

    private static boolean compatible(String first, String second) {
        return clean(first).isEmpty()
                || clean(second).isEmpty()
                || first.equalsIgnoreCase(second);
    }

    private void attachArtistIdentifiers(long recordingId, List<String> artistMbids, long now) {
        if (artistMbids == null || artistMbids.isEmpty()) {
            return;
        }
        List<RecordingArtistCreditEntity> credits = dao.credits(recordingId);
        if (credits.size() != artistMbids.size()) {
            return;
        }
        for (int index = 0; index < credits.size(); index++) {
            RecordingArtistCreditEntity credit = credits.get(index);
            String mbid = clean(artistMbids.get(index)).toLowerCase(Locale.ROOT);
            if (mbid.isEmpty()) {
                continue;
            }
            CanonicalArtistEntity mapped = dao.artistForProvider("musicbrainz", mbid);
            long artistId = credit.getArtistId();
            if (mapped != null && mapped.getId() != null && mapped.getId() != artistId) {
                dao.upsert(new RecordingArtistCreditEntity(
                        credit.getRecordingId(),
                        mapped.getId(),
                        credit.getRole(),
                        credit.getPosition(),
                        credit.getCreditedName(),
                        credit.getJoinPhrase(),
                        1.0d
                ));
                dao.deleteCredit(
                        credit.getRecordingId(),
                        credit.getArtistId(),
                        credit.getRole(),
                        credit.getPosition()
                );
                continue;
            }
            CanonicalArtistEntity artist = dao.artist(artistId);
            if (artist == null) {
                continue;
            }
            if (!artist.getMusicBrainzArtistId().isEmpty()
                    && !artist.getMusicBrainzArtistId().equalsIgnoreCase(mbid)) {
                saveTagConflict("ARTIST", artistId, "MUSICBRAINZ_ARTIST_ID", mbid, "MBID_CONFLICT", now);
                continue;
            }
            dao.update(new CanonicalArtistEntity(
                    artist.getId(),
                    artist.getArtistUuid(),
                    artist.getDisplayName(),
                    artist.getSortName(),
                    artist.getArtistType(),
                    artist.getCountryCode(),
                    mbid,
                    "CONFIRMED",
                    1.0d,
                    "EMBEDDED_TAG",
                    artist.getCreatedAt(),
                    Math.max(now, artist.getUpdatedAt())
            ));
            dao.upsert(new ArtistSourceMappingEntity(
                    null,
                    artistId,
                    "musicbrainz",
                    mbid,
                    artist.getDisplayName(),
                    "CONFIRMED",
                    1.0d,
                    now
            ));
        }
    }

    private void saveTagConflict(
            String targetType,
            long targetId,
            String identifierType,
            String identifierValue,
            String reason,
            long now
    ) {
        dao.upsert(new IdentityCandidateEntity(
                UUID.randomUUID().toString(),
                targetType,
                targetId,
                "embedded_tag",
                identifierType + ":" + identifierValue,
                "",
                "",
                "",
                0L,
                "ISRC".equals(identifierType) ? identifierValue : "",
                "UNKNOWN",
                1.0d,
                "REJECTED",
                "{\"reason\":\"" + jsonEscape(reason) + "\"}",
                now,
                now
        ));
    }

    private static String jsonEscape(String value) {
        return clean(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private long insertRecording(TrackEntity track, long now) {
        String canonicalUuid = UUID.randomUUID().toString();
        long workId = dao.insertWork(new CanonicalWorkEntity(
                null,
                canonicalUuid,
                IdentityTextNormalizer.INSTANCE.normalizeForSearch(clean(track.getTitle())),
                null,
                now,
                now
        ));
        if (workId <= 0L) {
            CanonicalWorkEntity existingWork = dao.work(canonicalUuid);
            if (existingWork == null || existingWork.getId() == null) {
                throw new IllegalStateException("Work UUID insert did not produce an internal ID");
            }
            workId = existingWork.getId();
        }
        long inserted = dao.insert(new CanonicalRecordingEntity(
                null,
                canonicalUuid,
                workId,
                null,
                "",
                "",
                clean(track.getTitle()),
                clean(track.getArtist()),
                Math.max(0L, track.getDurationMs()),
                "",
                "",
                STATUS_UNRESOLVED,
                0.0d,
                METADATA_SOURCE,
                now,
                now
        ));
        if (inserted > 0L) {
            return inserted;
        }
        CanonicalRecordingEntity existing = dao.canonicalRecording(canonicalUuid);
        if (existing == null || existing.getId() == null) {
            throw new IllegalStateException("Recording UUID insert did not produce an internal ID");
        }
        return existing.getId();
    }

    private void ensureArtistCredits(long recordingId, String rawArtist, long now) {
        if (dao.creditCount(recordingId) > 0) {
            return;
        }
        for (ArtistCreditParser.Credit parsed : ArtistCreditParser.parse(rawArtist)) {
            String normalized = ArtistCreditParser.normalizeAlias(parsed.name);
            List<CanonicalArtistEntity> matches = dao.artistsForNormalizedAlias(normalized);
            long artistId;
            if (matches.size() == 1 && matches.get(0).getId() != null) {
                artistId = matches.get(0).getId();
            } else {
                artistId = insertArtist(parsed.name, normalized, now);
            }
            dao.upsert(new RecordingArtistCreditEntity(
                    recordingId,
                    artistId,
                    parsed.role,
                    parsed.position,
                    parsed.name,
                    parsed.joinPhrase,
                    0.0d
            ));
            enqueueJob("ARTIST", artistId, now);
        }
    }

    private long insertArtist(String displayName, String normalizedAlias, long now) {
        String artistUuid = UUID.randomUUID().toString();
        long inserted = dao.insert(new CanonicalArtistEntity(
                null,
                artistUuid,
                displayName,
                normalizedAlias,
                "UNKNOWN",
                "",
                "",
                STATUS_UNRESOLVED,
                0.0d,
                METADATA_SOURCE,
                now,
                now
        ));
        if (inserted <= 0L) {
            CanonicalArtistEntity existing = dao.canonicalArtist(artistUuid);
            if (existing == null || existing.getId() == null) {
                throw new IllegalStateException("Artist UUID insert did not produce an internal ID");
            }
            inserted = existing.getId();
        }
        dao.upsert(new ArtistAliasEntity(
                inserted,
                displayName,
                normalizedAlias,
                "",
                "",
                "PRIMARY",
                METADATA_SOURCE,
                0.0d,
                0L
        ));
        return inserted;
    }

    private void enqueueJob(String targetType, long targetId, long now) {
        dao.insertJob(new IdentityResolutionJobEntity(
                UUID.randomUUID().toString(),
                targetType,
                targetId,
                0,
                "NEW_TRACK",
                0,
                0L,
                "",
                "PENDING",
                now,
                now
        ));
    }

    private static TrackSourceMappingEntity sourceEntity(
            TrackSourceMappingEntity existing,
            long recordingId,
            TrackSourceIdentity identity,
            TrackEntity track,
            long trackId
    ) {
        String quality = clean(track.getCodec()).isEmpty()
                ? existing == null ? "" : existing.getQuality()
                : clean(track.getCodec());
        boolean physicalSource = isPhysicalProvider(identity.provider);
        boolean directProviderIdentity = "netease".equals(identity.provider)
                || "qqmusic".equals(identity.provider);
        return new TrackSourceMappingEntity(
                existing == null ? null : existing.getSourceId(),
                recordingId,
                identity.provider,
                identity.providerTrackId,
                trackId,
                clean(track.getDataPath()),
                clean(track.getTitle()),
                clean(track.getArtist()),
                clean(track.getAlbum()),
                Math.max(0L, track.getDurationMs()),
                quality,
                qualityScore(quality),
                existing == null || existing.getPlayable(),
                physicalSource || directProviderIdentity ? "CONFIRMED" : existing == null
                        ? STATUS_UNRESOLVED
                        : existing.getMatchStatus(),
                physicalSource || directProviderIdentity
                        ? 1.0d
                        : existing == null ? 0.0d : existing.getConfidence(),
                existing == null ? 0L : existing.getLastSuccessfulAt(),
                existing == null ? 0L : existing.getLastVerifiedAt(),
                existing == null ? "" : existing.getLegacyLocalKey(),
                clean(track.getCodec()).isEmpty()
                        ? existing == null ? "" : existing.getCodec()
                        : clean(track.getCodec()),
                track.getBitrateKbps() > 0
                        ? track.getBitrateKbps()
                        : existing == null ? 0 : existing.getBitrateKbps(),
                existing == null ? 0L : existing.getLastFailureAt(),
                existing == null ? "" : existing.getFailureReason(),
                existing == null ? 0 : existing.getFailureCount()
        );
    }

    private static int qualityScore(String quality) {
        String value = clean(quality).toLowerCase(Locale.ROOT);
        if (containsAny(value, "flac", "alac", "wav", "ape", "lossless")) {
            return 100;
        }
        if (containsAny(value, "high", "320", "256")) {
            return 70;
        }
        if (containsAny(value, "standard", "192", "128", "aac", "mp3", "ogg")) {
            return 40;
        }
        return 0;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameProviderIdentity(
            TrackSourceMappingEntity source,
            TrackSourceIdentity identity
    ) {
        return source != null
                && source.getProvider().equals(identity.provider)
                && source.getProviderTrackId().equals(identity.providerTrackId);
    }

    private static boolean isPhysicalProvider(String provider) {
        return "local".equals(provider)
                || "webdav".equals(provider)
                || "document".equals(provider);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
