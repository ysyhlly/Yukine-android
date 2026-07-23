package app.yukine.common;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Explicit opt-in TLS compatibility for user-configured private servers with self-signed or
 * hostname-mismatched certificates. Never apply this process-wide or to unrelated requests.
 */
public final class InsecureTlsSupport {
    private static final X509TrustManager TRUST_ALL_MANAGER = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Explicitly trusted by the user for this one remote source.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Explicitly trusted by the user for this one remote source.
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY = createSocketFactory();

    private InsecureTlsSupport() {
    }

    public static void configure(HttpsURLConnection connection) {
        connection.setSSLSocketFactory(TRUST_ALL_SOCKET_FACTORY);
        connection.setHostnameVerifier((hostname, session) -> true);
    }

    public static OkHttpClient.Builder configure(OkHttpClient.Builder builder) {
        return builder
                .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY, TRUST_ALL_MANAGER)
                .hostnameVerifier((hostname, session) -> true);
    }

    private static SSLSocketFactory createSocketFactory() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{TRUST_ALL_MANAGER}, new SecureRandom());
            return context.getSocketFactory();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("TLS compatibility is unavailable", error);
        }
    }
}
