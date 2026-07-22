package app.yukine

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import app.yukine.diagnostics.DiagnosticLog
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import org.json.JSONObject

/**
 * Checks GitHub Releases for app updates and presents the result to the user.
 * Repository: https://github.com/ysyhlly/Yukine-android
 */
internal class GitHubUpdateChecker(
    private val activity: Activity,
    private val executor: ExecutorService,
    private val mainHandler: Handler,
    private val languageModeProvider: () -> String
) {
    companion object {
        private const val TAG = "GitHubUpdateChecker"
        private const val RELEASES_API =
            "https://api.github.com/repos/ysyhlly/Yukine-android/releases/latest"
        private const val RELEASES_PAGE =
            "https://github.com/ysyhlly/Yukine-android/releases/latest"
    }

    fun check() {
        val languageMode = languageModeProvider()
        mainHandler.post {
            showToast(AppLanguage.text(languageMode, "check.update.checking"))
        }
        executor.execute {
            try {
                val result = fetchLatestRelease()
                if (result == null) {
                    showFailed(languageMode)
                    return@execute
                }
                val currentVersion = currentVersionName()
                val latestVersion = result.tagName.removePrefix("v")
                if (isNewerVersion(latestVersion, currentVersion)) {
                    showUpdateAvailable(languageMode, result)
                } else {
                    showLatest(languageMode, currentVersion)
                }
            } catch (e: Exception) {
                DiagnosticLog.w(TAG, "Update check failed", e)
                showFailed(languageMode)
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Yukine-Android-UpdateChecker")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val htmlUrl = json.optString("html_url", RELEASES_PAGE)
            val bodyText = json.optString("body", "")
            if (tagName.isBlank()) return null
            return ReleaseInfo(tagName, name, htmlUrl, bodyText)
        } finally {
            connection.disconnect()
        }
    }

    private fun currentVersionName(): String {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (current.isBlank()) return true
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showUpdateAvailable(languageMode: String, release: ReleaseInfo) {
        val title = AppLanguage.text(languageMode, "check.update.available")
        val message = buildString {
            append(release.name)
            if (release.body.isNotBlank()) {
                append("\n\n")
                append(release.body.take(500))
            }
        }
        val downloadLabel = AppLanguage.text(languageMode, "check.update.download")
        val dismissLabel = AppLanguage.text(languageMode, "close")
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(downloadLabel) { dialog, _ ->
                    dialog.dismiss()
                    openReleasePage(release.htmlUrl)
                }
                .setNegativeButton(dismissLabel, null)
                .show()
        }
    }

    private fun showLatest(languageMode: String, version: String) {
        val message = AppLanguage.text(languageMode, "check.update.latest") +
            if (version.isNotBlank()) " (v$version)" else ""
        mainHandler.post { showToast(message) }
    }

    private fun showFailed(languageMode: String) {
        mainHandler.post {
            showToast(AppLanguage.text(languageMode, "check.update.failed"))
        }
    }

    private fun showToast(message: String) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openReleasePage(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // Browser not available
        }
    }

    private data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val htmlUrl: String,
        val body: String
    )
}
