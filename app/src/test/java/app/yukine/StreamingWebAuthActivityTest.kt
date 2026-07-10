package app.yukine

import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import app.yukine.streaming.StreamingProviderName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingWebAuthActivityTest {
    @Test
    fun qqWebLoginRequiresFiveSecondRiskConfirmation() {
        val intent = Intent(RuntimeEnvironment.getApplication(), StreamingWebAuthActivity::class.java)
            .putExtra(StreamingWebAuthActivity.EXTRA_PROVIDER, StreamingProviderName.QQ_MUSIC.wireName)
            .putExtra(StreamingWebAuthActivity.EXTRA_URL, "https://y.qq.com/")

        val activity = Robolectric.buildActivity(StreamingWebAuthActivity::class.java, intent).setup().get()
        val dialog = ShadowAlertDialog.getLatestAlertDialog() as AlertDialog
        val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        assertFalse(confirm.isEnabled)
        repeat(5) {
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
        }
        assertTrue(confirm.isEnabled)

        activity.finish()
    }
}
