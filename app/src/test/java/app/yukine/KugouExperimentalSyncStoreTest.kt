package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.streaming.KugouExperimentalSyncStore
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.StreamingCredentialState
import app.yukine.streaming.StreamingProviderName
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KugouExperimentalSyncStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("kugou_experimental_sync", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(LocalStreamingAuthStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("kugou_experimental_sync", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(LocalStreamingAuthStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun enablingExperimentDoesNotBypassUnverifiedContractGate() {
        val store = KugouExperimentalSyncStore(context)
        store.setUserEnabled(true)

        val status = store.status(accountConnected = true)

        assertTrue(status.userEnabled)
        assertFalse(status.contractVerified)
        assertFalse(status.writeEnabled(accountConnected = true))
        assertTrue(status.degradationReason.orEmpty().contains("契约验证"))
    }

    @Test
    fun repeatedContractFailuresSuspendWrites() {
        val store = KugouExperimentalSyncStore(context)
        store.setUserEnabled(true)
        repeat(3) { store.recordContractFailure("response schema drift") }

        val status = store.status(accountConnected = true)

        assertTrue(status.writeSuspended)
        assertFalse(status.writeEnabled(accountConnected = true))
    }

    @Test
    fun kugouCookieIsPendingAndNeverCountsAsVerifiedAccount() {
        val auth = LocalStreamingAuthStore(context).saveLogin(
            StreamingProviderName.KUGOU,
            "token=redacted-test-value; kg_mid=test-device"
        )

        assertFalse(auth.connected)
        assertTrue(auth.credentialState == StreamingCredentialState.PENDING_VERIFICATION)
    }
}
