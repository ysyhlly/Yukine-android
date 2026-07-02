package app.yukine.common;

import android.net.Uri;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class EmbeddedArtworkTest {
    @After
    public void tearDown() {
        EmbeddedArtwork.clearCacheForTesting();
    }

    @Test
    public void readReturnsDefensiveCopyFromCachedEmbeddedPicture() {
        Uri audioUri = Uri.parse("content://media/external/audio/media/42");
        Uri artworkUri = EmbeddedArtwork.uriFor(audioUri);
        byte[] picture = new byte[]{1, 2, 3, 4};

        EmbeddedArtwork.cacheEmbeddedPictureForTesting(audioUri, picture);
        picture[0] = 9;

        byte[] firstRead = EmbeddedArtwork.read(null, artworkUri);
        assertNotNull(firstRead);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, firstRead);
        assertEquals(4, EmbeddedArtwork.cachedArtworkBytesForTesting(audioUri));

        firstRead[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3, 4}, EmbeddedArtwork.read(null, artworkUri));
    }
}
