package app.yukine

import androidx.test.core.app.ApplicationProvider
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PersistentPlaybackSourcePolicyTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clear() {
        context.getSharedPreferences("playback_source_policy", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun newInstallOnlyEnablesLuoxueAndPersistsUserChoice() {
        val first = PersistentPlaybackSourcePolicy(context)
        assertTrue(first.snapshot().isEnabled(StreamingProviderName.LUOXUE))
        assertFalse(first.snapshot().isEnabled(StreamingProviderName.NETEASE))
        assertFalse(first.snapshot().isEnabled(StreamingProviderName.QQ_MUSIC))

        first.setEnabled(StreamingProviderName.NETEASE, true)
        val restarted = PersistentPlaybackSourcePolicy(context)
        assertTrue(restarted.snapshot().isEnabled(StreamingProviderName.NETEASE))
    }

    @Test
    fun qqCanNeverBeEnabled() {
        val policy = PersistentPlaybackSourcePolicy(context)
        policy.setEnabled(StreamingProviderName.QQ_MUSIC, true)
        assertFalse(policy.snapshot().isEnabled(StreamingProviderName.QQ_MUSIC))
    }
}
