package app.yukine.data.room;

import android.net.Uri;

import app.yukine.common.EmbeddedArtwork;
import app.yukine.model.Track;

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
                updatedAt
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
                (double) track.replayGainAlbumDb
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
                (float) entity.getReplayGainAlbumDb()
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
                (float) entity.getReplayGainAlbumDb()
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
