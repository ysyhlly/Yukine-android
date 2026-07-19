package app.yukine

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import app.yukine.streaming.LocalStreamingAuthStore
import app.yukine.streaming.StreamingProviderName
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
    @Before
    fun setUp() {
        clearCookies()
    }

    @After
    fun tearDown() {
        clearCookies()
    }

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

    @Test
    fun kugouHomepageDoesNotAutoCompleteEvenWhenAccountTokenExists() {
        CookieManager.getInstance().setCookie("https://www.kugou.com/", "token=test-account-token")
        val controller = Robolectric.buildActivity(
            StreamingWebAuthActivity::class.java,
            authIntent(StreamingProviderName.KUGOU, "https://www.kugou.com/")
        ).setup()
        val activity = controller.get()
        val webView = requireNotNull(findWebView(activity.window.decorView))

        shadowOf(webView).webViewClient.onPageFinished(webView, "https://www.kugou.com/")

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).peekNextStartedActivityForResult())
        controller.destroy()
    }

    @Test
    fun repeatedPageFinishedCallbacksCompleteAnotherProviderOnlyOnce() {
        CookieManager.getInstance().setCookie("https://music.163.com/", "MUSIC_U=test-account-token")
        val controller = Robolectric.buildActivity(
            StreamingWebAuthActivity::class.java,
            authIntent(StreamingProviderName.NETEASE, "https://music.163.com/")
        ).setup()
        val activity = controller.get()
        val webView = requireNotNull(findWebView(activity.window.decorView))
        val client = shadowOf(webView).webViewClient

        client.onPageFinished(webView, "https://music.163.com/")
        client.onPageFinished(webView, "https://music.163.com/")

        val launch = shadowOf(activity).nextStartedActivityForResult
        assertNotNull(launch)
        assertTrue(launch.intent.component?.className == MainActivity::class.java.name)
        assertNull(shadowOf(activity).nextStartedActivityForResult)
        controller.destroy()
    }

    @Test
    fun destroyingActivityDetachesAndDestroysWebView() {
        val controller = Robolectric.buildActivity(
            StreamingWebAuthActivity::class.java,
            authIntent(StreamingProviderName.KUGOU, "https://www.kugou.com/")
        ).setup()
        val activity = controller.get()
        val webView = requireNotNull(findWebView(activity.window.decorView))

        controller.destroy()

        assertNull(webView.parent)
        assertTrue(shadowOf(webView).wasDestroyCalled())
    }

    private fun authIntent(provider: StreamingProviderName, url: String): Intent {
        return Intent(RuntimeEnvironment.getApplication(), StreamingWebAuthActivity::class.java)
            .putExtra(StreamingWebAuthActivity.EXTRA_PROVIDER, provider.wireName)
            .putExtra(StreamingWebAuthActivity.EXTRA_URL, url)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) {
            return view
        }
        if (view !is ViewGroup) {
            return null
        }
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun clearCookies() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(LocalStreamingAuthStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
