package app.yukine.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.StreamingProviderName
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSecretStoreInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearAuthPrefs()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        clearAuthPrefs()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun secureSecretStoreEncryptsAndDecryptsWithAndroidKeystore() {
        val plain = "MUSIC_U=secret; os=pc"

        val encrypted = SecureSecretStore.encrypt(plain)

        assertTrue(!encrypted.isNullOrBlank())
        assertNotEquals(plain, encrypted)
        assertEquals(plain, SecureSecretStore.decrypt(encrypted))
        assertEquals("legacy-plain", SecureSecretStore.decryptOrPlain("legacy-plain"))
    }

    @Test
    fun localStreamingAuthStoreEncryptsCookieAtRestAndReadsPlainCookie() {
        val cookie = "MUSIC_U=local-secret; os=pc"
        val store = LocalStreamingAuthStore(context)

        val state = store.saveLogin(StreamingProviderName.NETEASE, cookie, "tester")
        val stored = authPrefs().getString("cookie:netease", null)

        assertTrue(state.connected)
        assertTrue(!stored.isNullOrBlank())
        assertNotEquals(cookie, stored)
        assertEquals(cookie, store.cookieHeader(StreamingProviderName.NETEASE))
        assertTrue(store.connected(StreamingProviderName.NETEASE))
    }

    private fun authPrefs() = context.getSharedPreferences(LocalStreamingAuthStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun clearAuthPrefs() {
        authPrefs().edit().clear().commit()
    }

    companion object {
        private const val DATABASE_NAME = "secure_secret_store_instrumented_test.db"
    }
}
