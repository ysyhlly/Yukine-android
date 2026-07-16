package app.yukine.data;

import app.yukine.data.room.IdentityCandidateEntity;
import app.yukine.data.room.MusicIdentityDao;
import app.yukine.data.room.TrackSourceMappingEntity;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Owns the identity semantics for provider-backed recording sources.
 *
 * <p>This class deliberately does not search, rank, verify playback URLs or select an active
 * source. It only prevents the legacy-match and candidate-confirmation paths from assigning the
 * same provider item to different recordings or from accidentally changing source health.</p>
 */
final class ProviderSourceIdentityWriter {
    static final class CandidateWriteResult {
        private final Long ownerRecordingId;

        private CandidateWriteResult(Long ownerRecordingId) {
            this.ownerRecordingId = ownerRecordingId;
        }

        static CandidateWriteResult stored() {
            return new CandidateWriteResult(null);
        }

        static CandidateWriteResult ownedByRecording(long ownerRecordingId) {
            return new CandidateWriteResult(ownerRecordingId);
        }

        boolean isStored() {
            return ownerRecordingId == null;
        }

        Long getOwnerRecordingId() {
            return ownerRecordingId;
        }
    }

    private final MusicIdentityDao dao;

    ProviderSourceIdentityWriter(MusicIdentityDao dao) {
        this.dao = dao;
    }

    CandidateWriteResult saveUnverifiedCandidate(
            long recordingId,
            String provider,
            String providerTrackId,
            String title,
            String artist,
            String album,
            long durationMs,
            double confidence
    ) {
        String cleanProvider = normalizeProvider(provider);
        String cleanProviderTrackId = normalizeProviderTrackId(providerTrackId);
        requireTarget(recordingId, cleanProvider, cleanProviderTrackId);
        TrackSourceMappingEntity existing = dao.source(cleanProvider, cleanProviderTrackId);
        if (existing != null && existing.getRecordingId() != recordingId) {
            saveVisibleCandidate(
                    recordingId,
                    cleanProvider,
                    cleanProviderTrackId,
                    title,
                    artist,
                    album,
                    durationMs,
                    confidence,
                    "PENDING",
                    false,
                    existing.getRecordingId()
            );
            return CandidateWriteResult.ownedByRecording(existing.getRecordingId());
        }
        String matchStatus = existing != null
                && ("CONFIRMED".equals(existing.getMatchStatus())
                || "REJECTED".equals(existing.getMatchStatus()))
                ? existing.getMatchStatus()
                : "CANDIDATE";
        write(
                existing,
                recordingId,
                cleanProvider,
                cleanProviderTrackId,
                title,
                artist,
                album,
                durationMs,
                matchStatus,
                existing != null && "CONFIRMED".equals(existing.getMatchStatus())
                        ? existing.getConfidence()
                        : Math.max(existing == null ? 0.0 : existing.getConfidence(),
                                clampConfidence(confidence))
        );
        saveVisibleCandidate(
                recordingId,
                cleanProvider,
                cleanProviderTrackId,
                title,
                artist,
                album,
                durationMs,
                confidence,
                "CONFIRMED".equals(matchStatus) ? "USER_CONFIRMED"
                        : "REJECTED".equals(matchStatus) ? "REJECTED" : "PENDING",
                false,
                0L
        );
        return CandidateWriteResult.stored();
    }

    void saveUserConfirmedSource(
            long recordingId,
            String provider,
            String providerTrackId,
            String title,
            String artist,
            String album,
            long durationMs
    ) {
        String cleanProvider = normalizeProvider(provider);
        String cleanProviderTrackId = normalizeProviderTrackId(providerTrackId);
        requireTarget(recordingId, cleanProvider, cleanProviderTrackId);
        TrackSourceMappingEntity existing = dao.source(cleanProvider, cleanProviderTrackId);
        if (existing != null && existing.getRecordingId() != recordingId) {
            throw new IllegalArgumentException("Provider source already belongs to another recording");
        }
        write(
                existing,
                recordingId,
                cleanProvider,
                cleanProviderTrackId,
                title,
                artist,
                album,
                durationMs,
                "CONFIRMED",
                1.0
        );
        IdentityCandidateEntity candidate = dao.candidate(
                "RECORDING",
                recordingId,
                cleanProvider,
                cleanProviderTrackId
        );
        if (candidate != null && "PENDING".equals(candidate.getStatus())) {
            dao.updateCandidateStatus(candidate.getCandidateId(), "USER_CONFIRMED", System.currentTimeMillis());
        }
    }

    void rejectUnconfirmedSource(long recordingId, String provider, String providerTrackId) {
        String cleanProvider = normalizeProvider(provider);
        String cleanProviderTrackId = normalizeProviderTrackId(providerTrackId);
        TrackSourceMappingEntity source = dao.source(cleanProvider, cleanProviderTrackId);
        if (source == null || source.getRecordingId() != recordingId) {
            return;
        }
        dao.rejectUnconfirmedProviderSource(source.getSourceId());
        dao.refreshActiveSource(recordingId);
    }

    private void saveVisibleCandidate(
            long recordingId,
            String provider,
            String providerTrackId,
            String title,
            String artist,
            String album,
            long durationMs,
            double confidence,
            String requestedStatus,
            boolean hardConflict,
            long ownerRecordingId
    ) {
        long now = System.currentTimeMillis();
        IdentityCandidateEntity existing = dao.candidate(
                "RECORDING",
                recordingId,
                provider,
                providerTrackId
        );
        String status = existing == null ? requestedStatus : preserveTerminalStatus(existing.getStatus(), requestedStatus);
        String candidateId = existing == null
                ? candidateId(recordingId, provider, providerTrackId)
                : existing.getCandidateId();
        dao.upsert(new IdentityCandidateEntity(
                candidateId,
                "RECORDING",
                recordingId,
                provider,
                providerTrackId,
                preferExisting(existing == null ? "" : existing.getTitle(), title),
                preferExisting(existing == null ? "" : existing.getArtist(), artist),
                preferExisting(existing == null ? "" : existing.getAlbum(), album),
                existing != null && existing.getDurationMs() > 0L
                        ? existing.getDurationMs()
                        : Math.max(0L, durationMs),
                existing == null ? "" : existing.getIsrc(),
                existing == null ? "UNKNOWN" : existing.getVariantType(),
                Math.max(existing == null ? 0.0 : existing.getScore(), clampConfidence(confidence)),
                status,
                candidateEvidence(
                        existing == null ? "" : existing.getEvidenceJson(),
                        hardConflict,
                        ownerRecordingId
                ),
                existing == null ? now : existing.getCreatedAt(),
                now
        ));
    }

    private static String candidateEvidence(
            String existingEvidence,
            boolean hardConflict,
            long ownerRecordingId
    ) {
        try {
            boolean ownedByAnotherRecording = ownerRecordingId > 0L;
            JSONObject evidence = existingCandidateEvidence(existingEvidence)
                    .put("source", "RUNTIME_MATCH")
                    .put(
                            "hardConflict",
                            hardConflict || (!ownedByAnotherRecording && existingHardConflict(existingEvidence))
                    );
            if (ownedByAnotherRecording) {
                evidence.put("conflict", "provider_source_owned_by_recording")
                        .put("ownerRecordingId", ownerRecordingId);
            }
            return evidence.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Unable to encode provider candidate evidence", error);
        }
    }

    private static JSONObject existingCandidateEvidence(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static boolean existingHardConflict(String value) {
        return existingCandidateEvidence(value).optBoolean("hardConflict", false);
    }

    static String candidateId(long recordingId, String provider, String providerTrackId) {
        return UUID.nameUUIDFromBytes(
                ("PROVIDER_CANDIDATE:" + recordingId + ":" + provider + ":" + providerTrackId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private static String preserveTerminalStatus(String existing, String requested) {
        if ("USER_CONFIRMED".equals(existing)
                || "AUTO_CONFIRMED".equals(existing)
                || "ALTERNATE_VERSION".equals(existing)
                || "REJECTED".equals(existing)
                || "EXPIRED".equals(existing)) {
            return existing;
        }
        return requested;
    }

    private void write(
            TrackSourceMappingEntity existing,
            long recordingId,
            String provider,
            String providerTrackId,
            String title,
            String artist,
            String album,
            long durationMs,
            String matchStatus,
            double confidence
    ) {
        dao.upsert(new TrackSourceMappingEntity(
                existing == null ? null : existing.getSourceId(),
                recordingId,
                provider,
                providerTrackId,
                existing == null ? null : existing.getLocalTrackId(),
                existing == null ? "" : existing.getDataPath(),
                preferExisting(existing == null ? "" : existing.getTitle(), title),
                preferExisting(existing == null ? "" : existing.getArtist(), artist),
                preferExisting(existing == null ? "" : existing.getAlbum(), album),
                existing != null && existing.getDurationMs() > 0L
                        ? existing.getDurationMs()
                        : Math.max(0L, durationMs),
                existing == null ? "" : existing.getQuality(),
                existing == null ? 0 : existing.getQualityScore(),
                existing != null && existing.getPlayable(),
                matchStatus,
                clampConfidence(confidence),
                existing == null ? 0L : existing.getLastSuccessfulAt(),
                existing == null ? 0L : existing.getLastVerifiedAt(),
                existing == null ? "" : existing.getLegacyLocalKey(),
                existing == null ? "" : existing.getCodec(),
                existing == null ? 0 : existing.getBitrateKbps(),
                existing == null ? 0L : existing.getLastFailureAt(),
                existing == null ? "" : existing.getFailureReason(),
                existing == null ? 0 : existing.getFailureCount()
        ));
    }

    private void requireTarget(long recordingId, String provider, String providerTrackId) {
        if (recordingId <= 0L || dao.recording(recordingId) == null) {
            throw new IllegalArgumentException("Unknown recording " + recordingId);
        }
        if (provider.isEmpty()) {
            throw new IllegalArgumentException("Provider is required");
        }
        if (providerTrackId.isEmpty()) {
            throw new IllegalArgumentException("Provider track ID is required");
        }
    }

    private static String preferExisting(String existing, String incoming) {
        String current = existing == null ? "" : existing.trim();
        return current.isEmpty() ? (incoming == null ? "" : incoming.trim()) : existing;
    }

    private static String normalizeProvider(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeProviderTrackId(String providerTrackId) {
        return providerTrackId == null ? "" : providerTrackId.trim();
    }

    private static double clampConfidence(double confidence) {
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}
