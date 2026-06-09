package app.echo.next;

import org.junit.Test;

import java.util.Collections;

import app.echo.next.model.RemoteSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NetworkLibraryTest {
    @Test
    public void remoteSourceSubtitleJoinsWebDavUrlWithSingleSlash() {
        RemoteSource source = new RemoteSource(
                1L,
                RemoteSource.TYPE_WEBDAV,
                "nas",
                "https://192.168.3.52:5005/",
                "",
                "",
                "/vlomes1/",
                "",
                0L
        );

        String subtitle = NetworkLibrary.remoteSourceSubtitle(source, Collections.emptyList());

        assertTrue(subtitle.contains("https://192.168.3.52:5005/vlomes1/"));
        assertFalse(subtitle.contains(":5005//vlomes1"));
    }
}
