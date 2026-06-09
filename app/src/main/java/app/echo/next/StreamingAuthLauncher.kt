package app.echo.next

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.echo.next.streaming.StreamingAuthKind

internal object StreamingAuthLauncher {
    fun launch(context: Context?, launch: MainActivityStreamingAuthLaunch?): Boolean {
        if (context == null || launch == null || launch.launchUrl.isBlank()) {
            return false
        }
        val uri = Uri.parse(launch.launchUrl)
        if (launch.kind == StreamingAuthKind.ISOLATED_WEB_VIEW_COOKIE) {
            return launchIsolatedWebView(context, launch, uri)
        }
        return launchCustomTabs(context, uri) || launchExternalBrowser(context, uri)
    }

    private fun launchIsolatedWebView(
        context: Context,
        launch: MainActivityStreamingAuthLaunch,
        uri: Uri
    ): Boolean {
        val intent = Intent(context, StreamingWebAuthActivity::class.java)
            .putExtra(StreamingWebAuthActivity.EXTRA_PROVIDER, launch.provider.wireName)
            .putExtra(StreamingWebAuthActivity.EXTRA_URL, uri.toString())
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivity(context, intent)
    }

    private fun launchCustomTabs(context: Context, uri: Uri): Boolean {
        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun launchExternalBrowser(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startActivity(context, intent)
    }

    private fun startActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
