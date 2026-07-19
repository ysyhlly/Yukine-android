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
import app.yukine.data.room.AlbumAliasEntity;
import app.yukine.data.room.AlbumSourceMappingEntity;
import app.yukine.data.room.ArtistCreditParser;
import app.yukine.data.room.CanonicalAlbumEntity;
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
import app.yukine.streaming.RecordingVersionClassifier;
import app.yukine.streaming.StreamingTrackMatchPolicy;

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
        ensureTracks(tracks, Collections.emptyMap(), Collections.emptyMap(), now);
    }

    void ensureTracks(
            List<TrackEntity> tracks,
            Map<Long, TrackIdentityTags> identityTags,
            long now
    ) {
        ensureTracks(tracks, identityTags, Collections.emptyMap(), now);
    }

    void ensureTracks(
            List<TrackEntity> tracks,
            Map<Long, TrackIdentityTags> identityTags,
            Map<Long, String> contentSignatures,
            long now
    ) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        for (TrackEntity track : tracks) {
            TrackIdentityTags tags = track == null || track.getId() == null || identityTags == null
                    ? null
                    : identityTags.get(track.getId());
            String contentSignature =
                    track == null || track.getId() == null || contentSignatures == null
                            ? ""
                            : contentSignatures.get(track.getId());
            long recordingId = ensureTrack(track, tags, contentSignature, now);
            if (track != null && track.getId() != null) {
                if (recordingId > 0L && tags != null && !tags.isEmpty()) {
                    applyIdentityTags(recordingId, tags, now);
                }
            }
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

    private long ensureTrack(
            TrackEntity track,
            TrackIdentityTags tags,
            String contentSignature,
            long now
    ) {
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
        if (ownedByAnotherExistingTrack(keyedSource, trackId)) {
            return -1L;
        }
        if (shouldDetachForIncomingTrack(
                keyedSource,
                track,
                tags,
                contentSignature,
                trackId
        )) {
            deleteSource(keyedSource);
            if (localSource != null && sameSource(localSource, keyedSource)) {
                localSource = null;
            }
            keyedSource = null;
        }
        Long relocatedRecordingId = null;
        if (localSource != null && !sameProviderIdentity(localSource, identity)) {
            if (!hasHardContentConflict(localSource, track, tags, contentSignature)) {
                relocatedRecordingId = localSource.getRecordingId();
            }
            deleteSource(localSource);
            localSource = null;
        }

        TrackSourceMappingEntity persistedSource = keyedSource != null ? keyedSource : localSource;
        TrackSourceMappingEntity dataPathAnchor = clean(track.getDataPath()).isEmpty()
                ? null
                : dao.sourceForDataPath(clean(track.getDataPath()));
        if (persistedSource == null && dataPathAnchor != null
                && dataPathAnchor.getLocalTrackId() != null
                && dataPathAnchor.getLocalTrackId() != trackId) {
            if (dao.localTrackExists(dataPathAnchor.getLocalTrackId())) {
                dataPathAnchor = null;
            } else if (!canRelocateSource(dataPathAnchor, track, tags, contentSignature)) {
                deleteSource(dataPathAnchor);
                dataPathAnchor = null;
            }
        }
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

        ensureArtistCredits(recordingId, track.getArtist(), now);
        dao.refreshWorkPrimaryCreator(recordingId, now);
        canonicalizeWork(recordingId, track.getTitle(), now);
        Long albumId = ensureAlbum(recordingId, identity, track, now);
        dao.upsert(sourceEntity(
                persistedSource,
                recordingId,
                identity,
                track,
                trackId,
                contentSignature,
                albumId
        ));
        TrackSourceMappingEntity saved = dao.source(identity.provider, identity.providerTrackId);
        if (saved != null) {
            dao.refreshActiveSource(saved.getRecordingId());
        }
        enqueueJob("RECORDING", recordingId, now);
        return recordingId;
    }

    private boolean ownedByAnotherExistingTrack(
            TrackSourceMappingEntity source,
            long incomingTrackId
    ) {
        return source != null
                && source.getLocalTrackId() != null
                && source.getLocalTrackId() != incomingTrackId
                && dao.localTrackExists(source.getLocalTrackId());
    }

    private boolean shouldDetachForIncomingTrack(
            TrackSourceMappingEntity source,
            TrackEntity track,
            TrackIdentityTags tags,
            String contentSignature,
            long incomingTrackId
    ) {
        if (source == null || source.getLocalTrackId() == null) {
            return false;
        }
        if (source.getLocalTrackId() == incomingTrackId) {
            return hasHardContentConflict(source, track, tags, contentSignature);
        }
        return !canRelocateSource(source, track, tags, contentSignature);
    }

    private boolean canRelocateSource(
            TrackSourceMappingEntity source,
            TrackEntity track,
            TrackIdentityTags tags,
            String contentSignature
    ) {
        if (hasHardContentConflict(source, track, tags, contentSignature)) {
            return false;
        }
        if (sameContentSignature(source, contentSignature)) {
            return true;
        }
        CanonicalRecordingEntity recording = dao.recording(source.getRecordingId());
        if (recording != null && tags != null && (
                sameNonEmpty(recording.getMusicBrainzRecordingId(), tags.recordingMusicBrainzId)
                        || sameNonEmpty(recording.getIsrc(), tags.isrc)
                        || sameNonEmpty(recording.getAcoustId(), tags.acoustId)
        )) {
            return true;
        }
        if (source.getDurationMs() > 0L && track.getDurationMs() > 0L
                && Math.abs(source.getDurationMs() - track.getDurationMs()) > 2_000L) {
            return false;
        }
        return normalized(source.getTitle()).equals(normalized(track.getTitle()))
                && normalized(source.getArtist()).equals(normalized(track.getArtist()));
    }

    private boolean hasHardContentConflict(
            TrackSourceMappingEntity source,
            TrackEntity track,
            TrackIdentityTags tags,
            String contentSignature
    ) {
        if (source == null || track == null) {
            return false;
        }
        String persistedSignature = clean(source.getContentSignature());
        String incomingSignature = clean(contentSignature);
        if (!persistedSignature.isEmpty() && !incomingSignature.isEmpty()) {
            return !persistedSignature.equals(incomingSignature);
        }
        CanonicalRecordingEntity recording = dao.recording(source.getRecordingId());
        if (recording != null && tags != null && (
                differentNonEmpty(recording.getMusicBrainzRecordingId(), tags.recordingMusicBrainzId)
                        || differentNonEmpty(recording.getIsrc(), tags.isrc)
                        || differentNonEmpty(recording.getAcoustId(), tags.acoustId)
        )) {
            return true;
        }
        RecordingVariantType previousVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                source.getTitle(),
                source.getAlbum()
        );
        RecordingVariantType incomingVariant = RecordingVariantRecognizer.INSTANCE.recognize(
                track.getTitle(),
                track.getAlbum()
        );
        if (isProtectedVariant(previousVariant)
                && isProtectedVariant(incomingVariant)
                && previousVariant != incomingVariant) {
            return true;
        }
        if (source.getDurationMs() > 0L && track.getDurationMs() > 0L
                && Math.abs(source.getDurationMs() - track.getDurationMs()) > 5_000L) {
            return true;
        }
        return !normalized(source.getTitle()).equals(normalized(track.getTitle()))
                && !normalized(source.getArtist()).equals(normalized(track.getArtist()));
    }

    private static boolean sameContentSignature(
            TrackSourceMappingEntity source,
            String contentSignature
    ) {
        if (source == null) {
            return false;
        }
        String persistedSignature = clean(source.getContentSignature());
        String incomingSignature = clean(contentSignature);
        return !persistedSignature.isEmpty()
                && !incomingSignature.isEmpty()
                && persistedSignature.equals(incomingSignature);
    }

    private void deleteSource(TrackSourceMappingEntity source) {
        if (source != null && source.getSourceId() != null) {
            dao.deleteSource(source.getSourceId());
            dao.clearMissingActiveSources();
        }
    }

    private static boolean sameSource(
            TrackSourceMappingEntity first,
            TrackSourceMappingEntity second
    ) {
        return first != null
                && second != null
                && first.getSourceId() != null
                && first.getSourceId().equals(second.getSourceId());
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
                    .mergeRecordingsInCurrentTransaction(recordingId, existing.getRecordingId());
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
                    artist.getAvatarUrl(),
                    "CONFIRMED",
                    1.0d,
                    "EMBEDDED_TAG",
                    artist.getCreatedAt(),
                    Math.max(now, artist.getUpdatedAt()),
                    artist.getDescription()
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
                canonicalWorkTitle(track.getTitle()),
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

    private void canonicalizeWork(long recordingId, String rawTitle, long now) {
        CanonicalRecordingEntity recording = dao.recording(recordingId);
        if (recording == null || recording.getWorkId() == null) {
            return;
        }
        Long primaryArtistId = dao.primaryArtistId(recordingId);
        if (primaryArtistId == null) {
            return;
        }
        String normalizedTitle = canonicalWorkTitle(rawTitle);
        if (normalizedTitle.isEmpty()) {
            return;
        }
        dao.updateWorkTitle(recording.getWorkId(), normalizedTitle, now);
        CanonicalWorkEntity canonical = dao.workForIdentity(normalizedTitle, primaryArtistId);
        if (canonical == null || canonical.getId() == null
                || canonical.getId().equals(recording.getWorkId())) {
            return;
        }
        dao.updateRecordingWork(recordingId, canonical.getId(), now);
        dao.deleteOrphanWorks();
    }

    private Long ensureAlbum(
            long recordingId,
            TrackSourceIdentity identity,
            TrackEntity track,
            long now
    ) {
        String displayName = clean(track.getAlbum());
        String normalizedAlbum = StreamingTrackMatchPolicy.INSTANCE.canonicalAlbum(displayName);
        if (normalizedAlbum.isEmpty()) {
            return null;
        }
        String providerAlbumId = track.getAlbumId() > 0L
                ? Long.toString(track.getAlbumId())
                : "";
        CanonicalAlbumEntity album = providerAlbumId.isEmpty()
                ? null
                : dao.albumForProvider(identity.provider, providerAlbumId);
        Long primaryArtistId = dao.primaryArtistId(recordingId);
        String albumArtist = clean(track.getAlbumArtist()).isEmpty()
                ? clean(track.getArtist())
                : clean(track.getAlbumArtist());
        String artistIdentity = primaryArtistId == null
                ? ArtistCreditParser.normalizeAlias(albumArtist)
                : "artist:" + primaryArtistId;
        String identityKey = normalizedAlbum + "\u001f" + artistIdentity + "\u001f"
                + Math.max(0, track.getYear()) + "\u001f"
                + IdentityTextNormalizer.INSTANCE.normalizeForSearch(clean(track.getReleaseType()));
        if (album == null) {
            album = dao.albumForIdentity(identityKey);
        }
        long albumId;
        if (album != null && album.getId() != null) {
            albumId = album.getId();
        } else {
            String albumUuid = UUID.randomUUID().toString();
            albumId = dao.insert(new CanonicalAlbumEntity(
                    null,
                    albumUuid,
                    identityKey,
                    displayName,
                    normalizedAlbum,
                    primaryArtistId,
                    "",
                    "",
                    clean(track.getReleaseType()),
                    Math.max(0, track.getYear()),
                    STATUS_UNRESOLVED,
                    0.0d,
                    METADATA_SOURCE,
                    now,
                    now
            ));
            if (albumId <= 0L) {
                CanonicalAlbumEntity existing = dao.albumForIdentity(identityKey);
                if (existing == null || existing.getId() == null) {
                    throw new IllegalStateException("Album insert did not produce an internal ID");
                }
                albumId = existing.getId();
            }
        }
        dao.upsert(new AlbumAliasEntity(
                albumId,
                displayName,
                normalizedAlbum,
                "",
                "PRIMARY",
                METADATA_SOURCE,
                0.0d,
                0L
        ));
        if (!providerAlbumId.isEmpty()) {
            dao.upsert(new AlbumSourceMappingEntity(
                    null,
                    albumId,
                    identity.provider,
                    providerAlbumId,
                    displayName,
                    "",
                    "",
                    STATUS_UNRESOLVED,
                    0.0d,
                    0L
            ));
        }
        enqueueJob("ALBUM", albumId, now);
        return albumId;
    }

    private void ensureArtistCredits(long recordingId, String rawArtist, long now) {
        if (dao.creditCount(recordingId) > 0) {
            return;
        }
        for (ArtistCreditParser.Credit parsed : ArtistCreditParser.parse(rawArtist)) {
            List<String> normalizedAliases = ArtistCreditParser.normalizedAliases(parsed.name);
            Set<CanonicalArtistEntity> uniqueMatches = new HashSet<>();
            for (String alias : normalizedAliases) {
                uniqueMatches.addAll(dao.artistsForNormalizedAlias(alias));
            }
            long artistId;
            if (uniqueMatches.size() == 1 && uniqueMatches.iterator().next().getId() != null) {
                artistId = uniqueMatches.iterator().next().getId();
            } else {
                String primaryAlias = normalizedAliases.isEmpty()
                        ? ArtistCreditParser.normalizeAlias(parsed.name)
                        : normalizedAliases.get(0);
                artistId = insertArtist(parsed.name, primaryAlias, now);
            }
            for (String normalizedAlias : normalizedAliases) {
                dao.upsert(new ArtistAliasEntity(
                        artistId,
                        parsed.name,
                        normalizedAlias,
                        "",
                        "",
                        "ALIAS",
                        METADATA_SOURCE,
                        0.0d,
                        0L
                ));
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
                "",
                STATUS_UNRESOLVED,
                0.0d,
                METADATA_SOURCE,
                now,
                now,
                ""
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

    private static String canonicalWorkTitle(String rawTitle) {
        return StreamingTrackMatchPolicy.INSTANCE.canonicalTitle(
                RecordingVersionClassifier.INSTANCE.coreTitle(clean(rawTitle))
        );
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
            long trackId,
            String contentSignature,
            Long albumId
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
                existing == null ? 0 : existing.getFailureCount(),
                clean(contentSignature).isEmpty()
                        ? existing == null ? "" : existing.getContentSignature()
                        : clean(contentSignature),
                clean(track.getAlbumArtist()),
                clean(track.getComposer()),
                clean(track.getReleaseType()),
                track.getYear(),
                albumId == null
                        ? existing == null ? null : existing.getAlbumId()
                        : albumId
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

    private static boolean sameNonEmpty(String first, String second) {
        return !clean(first).isEmpty() && clean(first).equalsIgnoreCase(clean(second));
    }

    private static boolean differentNonEmpty(String first, String second) {
        return !clean(first).isEmpty()
                && !clean(second).isEmpty()
                && !clean(first).equalsIgnoreCase(clean(second));
    }

    private static String normalized(String value) {
        return IdentityTextNormalizer.INSTANCE.normalizeForSearch(clean(value));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
