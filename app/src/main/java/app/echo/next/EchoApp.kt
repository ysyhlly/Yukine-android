package app.echo.next

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.echo.next.ui.EchoTheme

fun interface EchoLegacyRootFactory {
    fun create(): View
}

object EchoAppHost {
    @JvmStatic
    fun install(activity: ComponentActivity, legacyRootFactory: EchoLegacyRootFactory) {
        activity.setContent {
            EchoApp(legacyRootFactory)
        }
    }
}

@Composable
fun EchoApp(legacyRootFactory: EchoLegacyRootFactory) {
    EchoTheme.EchoTheme {
        AndroidView(
            factory = { legacyRootFactory.create() },
            modifier = Modifier.fillMaxSize()
        )
    }
}
