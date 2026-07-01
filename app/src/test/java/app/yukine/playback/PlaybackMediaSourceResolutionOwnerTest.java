package app.yukine.playback;

import android.net.Uri;

import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.source.MediaSource;

import app.yukine.model.Track;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackMediaSourceResolutionOwnerTest {
    @Test
    public void delegatesSingleAndMirroredMediaSourceResolutionWithMetadata() {
        List<String> events = new ArrayList<>();
        Track first = track(1L);
        Track second = track(2L);
        PlaybackMediaSourceResolutionOwner owner = new PlaybackMediaSourceResolutionOwner(
                () -> new PlaybackMediaSourceResolutionOwner.MediaSourceResolver() {
                    @Override
                    public MediaSource mediaSourceForTrack(
                            Track track,
                            PlaybackMediaSourceResolutionOwner.MetadataProvider metadataProvider
                    ) {
                        events.add("single:" + track.id + ":" + metadataProvider.mediaMetadataForTrack(track).title);
                        return null;
                    }

                    @Override
                    public List<MediaSource> mediaSourcesForTracks(
                            List<Track> tracks,
                            PlaybackMediaSourceResolutionOwner.MetadataProvider metadataProvider
                    ) {
                        for (Track track : tracks) {
                            events.add("mirrored:" + track.id + ":" + metadataProvider.mediaMetadataForTrack(track).title);
                        }
                        return Collections.singletonList(null);
                    }
                },
                track -> new MediaMetadata.Builder().setTitle(track.title).build()
        );

        assertNull(owner.mediaSourceForTrack(first));
        assertEquals(1, owner.mediaSourcesForTracks(java.util.Arrays.asList(first, second)).size());
        assertEquals(
                java.util.Arrays.asList(
                        "single:1:Track 1",
                        "mirrored:1:Track 1",
                        "mirrored:2:Track 2"
                ),
                events
        );
    }

    @Test
    public void ignoresMissingResolver() {
        PlaybackMediaSourceResolutionOwner owner = new PlaybackMediaSourceResolutionOwner(
                () -> null,
                track -> MediaMetadata.EMPTY
        );

        assertNull(owner.mediaSourceForTrack(track(3L)));
        assertEquals(0, owner.mediaSourcesForTracks(Collections.singletonList(track(3L))).size());
    }

    private static Track track(long id) {
        return new Track(
                id,
                "Track " + id,
                "Artist",
                "Album",
                1000L,
                Uri.parse("file:///music/" + id + ".flac"),
                "/music/" + id + ".flac"
        );
    }
}
