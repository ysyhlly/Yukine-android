package app.echo.next.data;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;

import app.echo.next.model.RemoteSource;

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
}
