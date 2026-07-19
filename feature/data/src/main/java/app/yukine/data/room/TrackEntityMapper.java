package app.yukine.data.room;

import android.net.Uri;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;
import app.yukine.model.TrackIdentityTags;

public final class TrackEntityMapper {
    private TrackEntityMapper() {
    }

    public static TrackEntity entity(Track track, long updatedAt) {
        if (track == null) {
            throw new NullPointerException("track");
        }
        return new TrackEntity(
                track.id,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                track.contentUri.toString(),
                track.dataPath,
                track.albumId,
                track.albumArtUriString(),
                track.codec,
                track.bitrateKbps,
                track.sampleRateHz,
                track.bitsPerSample,
                track.channelCount,
                (double) track.replayGainTrackDb,
                (double) track.replayGainAlbumDb,
                updatedAt,
                track.albumArtist,
                track.composer,
                track.releaseType,
                track.year
        );
    }

    /** Keeps expensive parsed fields when a scan reports the same underlying media item. */
    public static TrackEntity preserveAudioSpecs(TrackEntity incoming, TrackEntity existing) {
        if (incoming == null || existing == null
                || incoming.getCodec() != null && !incoming.getCodec().isEmpty()
                || incoming.getBitrateKbps() > 0
                || incoming.getSampleRateHz() > 0
                || incoming.getBitsPerSample() > 0
                || incoming.getChannelCount() > 0
                || !incoming.getContentUri().equals(existing.getContentUri())
                || !incoming.getDataPath().equals(existing.getDataPath())
                || incoming.getDurationMs() != existing.getDurationMs()) {
            return incoming;
        }
        boolean existingHasSpecs = existing.getCodec() != null && !existing.getCodec().isEmpty()
                || existing.getBitrateKbps() > 0
                || existing.getSampleRateHz() > 0
                || existing.getBitsPerSample() > 0
                || existing.getChannelCount() > 0
                || existing.getReplayGainTrackDb() != 0.0
                || existing.getReplayGainAlbumDb() != 0.0;
        if (!existingHasSpecs) {
            return incoming;
        }
        return new TrackEntity(
                incoming.getId(),
                incoming.getTitle(),
                incoming.getArtist(),
                incoming.getAlbum(),
                incoming.getDurationMs(),
                incoming.getContentUri(),
                incoming.getDataPath(),
                incoming.getAlbumId(),
                incoming.getAlbumArtUri(),
                existing.getCodec(),
                existing.getBitrateKbps(),
                existing.getSampleRateHz(),
                existing.getBitsPerSample(),
                existing.getChannelCount(),
                existing.getReplayGainTrackDb(),
                existing.getReplayGainAlbumDb(),
                incoming.getUpdatedAt(),
                incoming.getAlbumArtist(),
                incoming.getComposer(),
                incoming.getReleaseType(),
                incoming.getYear()
        );
    }

    public static PlaybackQueueEntity queueEntity(Track track, int position) {
        if (track == null) {
            throw new NullPointerException("track");
        }
        return new PlaybackQueueEntity(
                position,
                track.id,
                track.title,
                track.artist,
                track.album,
                track.durationMs,
                track.contentUri.toString(),
                track.dataPath,
                track.albumId,
                track.albumArtUriString(),
                track.codec,
                track.bitrateKbps,
                track.sampleRateHz,
                track.bitsPerSample,
                track.channelCount,
                (double) track.replayGainTrackDb,
                (double) track.replayGainAlbumDb,
                track.albumArtist,
                track.composer,
                track.releaseType,
                track.year
        );
    }

    public static Track track(TrackEntity entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }
        return new Track(
                entity.getId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getAlbum(),
                entity.getDurationMs(),
                Uri.parse(entity.getContentUri()),
                entity.getDataPath(),
                entity.getAlbumId(),
                artwork(entity.getAlbumArtUri(), entity.getContentUri()),
                entity.getCodec(),
                entity.getBitrateKbps(),
                entity.getSampleRateHz(),
                entity.getBitsPerSample(),
                entity.getChannelCount(),
                (float) entity.getReplayGainTrackDb(),
                (float) entity.getReplayGainAlbumDb(),
                TrackIdentityTags.EMPTY,
                entity.getAlbumArtist(),
                entity.getComposer(),
                entity.getReleaseType(),
                entity.getYear()
        );
    }

    public static Track queueTrack(PlaybackQueueEntity entity, TrackEntity fallback) {
        if (entity == null || entity.getPosition() == null) {
            return null;
        }
        if ((entity.getTitle().isEmpty() || entity.getDataPath().isEmpty()) && fallback != null) {
            return track(fallback);
        }
        return new Track(
                entity.getTrackId(),
                entity.getTitle(),
                entity.getArtist(),
                entity.getAlbum(),
                entity.getDurationMs(),
                Uri.parse(entity.getContentUri()),
                entity.getDataPath(),
                entity.getAlbumId(),
                artwork(entity.getAlbumArtUri(), entity.getContentUri()),
                entity.getCodec(),
                entity.getBitrateKbps(),
                entity.getSampleRateHz(),
                entity.getBitsPerSample(),
                entity.getChannelCount(),
                (float) entity.getReplayGainTrackDb(),
                (float) entity.getReplayGainAlbumDb(),
                TrackIdentityTags.EMPTY,
                entity.getAlbumArtist(),
                entity.getComposer(),
                entity.getReleaseType(),
                entity.getYear()
        );
    }

    private static Uri artwork(String artwork, String content) {
        if (artwork != null && !artwork.isEmpty()) {
            return Uri.parse(artwork);
        }
        if (content != null && content.startsWith("content://")) {
            return EmbeddedArtwork.uriFor(Uri.parse(content));
        }
        return null;
    }
}
