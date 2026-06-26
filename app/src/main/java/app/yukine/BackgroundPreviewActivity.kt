package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.yukine.ui.BackgroundPreviewScreen
import app.yukine.ui.EchoTheme

/**
 * Full-screen editor that lets the user pinch-zoom and drag a freshly picked background image,
 * previews the frosted card look on top, and returns the chosen [BackgroundTransform] to the caller
 * via an activity result launcher. Pure presentation — persistence stays in the settings pipeline.
 */
class BackgroundPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent?.getStringExtra(EXTRA_URI).orEmpty()
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (uriString.isBlank() || uri == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val languageMode = intent?.getStringExtra(EXTRA_LANGUAGE).orEmpty()
        val initial = BackgroundTransform(
            scale = intent?.getFloatExtra(EXTRA_SCALE, 1f) ?: 1f,
            offsetX = intent?.getFloatExtra(EXTRA_OFFSET_X, 0f) ?: 0f,
            offsetY = intent?.getFloatExtra(EXTRA_OFFSET_Y, 0f) ?: 0f
        ).normalized()
        setContent {
            EchoTheme.EchoTheme {
                BackgroundPreviewScreen(
                    uri = uri,
                    languageMode = languageMode,
                    initialTransform = initial,
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onApply = { transform ->
                        setResult(Activity.RESULT_OK, resultIntent(transform))
                        finish()
                    }
                )
            }
        }
    }

    private fun resultIntent(transform: BackgroundTransform): Intent {
        val safe = transform.normalized()
        return Intent()
            .putExtra(EXTRA_SCALE, safe.scale)
            .putExtra(EXTRA_OFFSET_X, safe.offsetX)
            .putExtra(EXTRA_OFFSET_Y, safe.offsetY)
    }

    companion object {
        private const val EXTRA_URI = "extra_background_uri"
        private const val EXTRA_LANGUAGE = "extra_language_mode"
        private const val EXTRA_SCALE = "extra_scale"
        private const val EXTRA_OFFSET_X = "extra_offset_x"
        private const val EXTRA_OFFSET_Y = "extra_offset_y"

        @JvmStatic
        fun intent(
            context: Context,
            uri: Uri,
            languageMode: String,
            initial: BackgroundTransform
        ): Intent {
            val safe = initial.normalized()
            return Intent(context, BackgroundPreviewActivity::class.java)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_LANGUAGE, languageMode)
                .putExtra(EXTRA_SCALE, safe.scale)
                .putExtra(EXTRA_OFFSET_X, safe.offsetX)
                .putExtra(EXTRA_OFFSET_Y, safe.offsetY)
        }

        @JvmStatic
        fun transformFromResult(data: Intent?): BackgroundTransform {
            if (data == null) {
                return BackgroundTransform.IDENTITY
            }
            return BackgroundTransform(
                scale = data.getFloatExtra(EXTRA_SCALE, 1f),
                offsetX = data.getFloatExtra(EXTRA_OFFSET_X, 0f),
                offsetY = data.getFloatExtra(EXTRA_OFFSET_Y, 0f)
            ).normalized()
        }
    }
}
