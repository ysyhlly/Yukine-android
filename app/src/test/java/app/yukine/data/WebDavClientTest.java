package app.yukine.data;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import app.yukine.model.RemoteSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WebDavClientTest {
    @Test
    public void directoryUrlJoinsBaseAndRootWithSingleSlash() throws Exception {
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
        WebDavClient client = new WebDavClient();
        Method directoryUrl = WebDavClient.class.getDeclaredMethod("directoryUrl", RemoteSource.class);
        directoryUrl.setAccessible(true);

        assertEquals(
                "https://192.168.3.52:5005/vlomes1/",
                directoryUrl.invoke(client, source)
        );
    }

    @Test
    public void safeMessageFormatsAndroidConnectExceptionTarget() throws Exception {
        WebDavClient client = new WebDavClient();
        Method safeMessage = WebDavClient.class.getDeclaredMethod("safeMessage", Exception.class);
        safeMessage.setAccessible(true);

        String message = (String) safeMessage.invoke(
                client,
                new ConnectException("Failed to connect to /198.44.178.36:5006")
        );

        assertTrue(message.contains("198.44.178.36:5006"));
        assertTrue(message.contains("端口拒绝连接"));
        assertFalse(message.contains("/198.44.178.36"));
    }

    @Test
    public void parseHtmlEntriesAcceptsIndexDirectoryListing() throws Exception {
        WebDavClient client = new WebDavClient();
        Method parseHtmlEntries = WebDavClient.class.getDeclaredMethod(
                "parseHtmlEntries",
                RemoteSource.class,
                String.class,
                String.class,
                byte[].class
        );
        parseHtmlEntries.setAccessible(true);
        RemoteSource source = new RemoteSource(
                7L,
                RemoteSource.TYPE_WEBDAV,
                "nas",
                "http://127.0.0.1:5005",
                "",
                "",
                "/music",
                "",
                0L
        );
        String html = "<html><body><h1>Index of /music</h1>"
                + "<a href=\"?C=N;O=D\">Name</a>"
                + "<a href=\"../\">Parent Directory</a>"
                + "<a href=\"9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac\">9を眺めた魚達.flac</a>"
                + "<a href=\"cover.jpg\">cover.jpg</a>"
                + "<a href=\"sub/\">sub/</a>"
                + "</body></html>";

        List<?> entries = (List<?>) parseHtmlEntries.invoke(
                client,
                source,
                "http://127.0.0.1:5005/music/",
                "text/html; charset=utf-8",
                html.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(2, entries.size());
        assertEquals("9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac", field(entries.get(0), "href"));
        assertEquals("http://127.0.0.1:5005/music/9%E3%82%92%E7%9C%BA%E3%82%81%E3%81%9F%E9%AD%9A%E9%81%94.flac", field(entries.get(0), "url"));
        assertEquals(false, field(entries.get(0), "directory"));
        assertEquals("sub/", field(entries.get(1), "href"));
        assertEquals("http://127.0.0.1:5005/music/sub/", field(entries.get(1), "url"));
        assertEquals(true, field(entries.get(1), "directory"));
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
