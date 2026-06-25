package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yukine.AppLanguage
import app.yukine.BackgroundTransform

// PREVIEW_UI_PLACEHOLDER

@Composable
fun BackgroundPreviewScreen(
    uri: Uri,
    languageMode: String,
    initialTransform: BackgroundTransform,
    onCancel: () -> Unit,
    onApply: (BackgroundTransform) -> Unit
) {
    val p = EchoTheme.colors()
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Preview always uses the original image so zoom/pan fidelity matches the picked source.
    var scale by remember { mutableFloatStateOf(initialTransform.scale) }
    var offsetXPx by remember { mutableFloatStateOf(0f) }
    var offsetYPx by remember { mutableFloatStateOf(0f) }
    var seededOffset by remember { mutableStateOf(false) }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }

    fun text(key: String): String = AppLanguage.text(languageMode, key)

    fun currentTransform(): BackgroundTransform {
        val maxX = BackgroundTransformGeometry.maxTranslation(viewportW, scale)
        val maxY = BackgroundTransformGeometry.maxTranslation(viewportH, scale)
        val fx = if (maxX > 0f) offsetXPx / maxX else 0f
        val fy = if (maxY > 0f) offsetYPx / maxY else 0f
        return BackgroundTransform(scale, fx, fy).normalized()
    }

    Box(modifier = Modifier.fillMaxSize().background(p.background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            viewportW = widthPx
            viewportH = heightPx
            LaunchedEffect(uri) {
                bitmap = ArtworkLoader.loadOriginal(context.applicationContext, uri)
            }
            // Seed the live pixel offset from the stored normalized fractions once dimensions exist.
            LaunchedEffect(widthPx, heightPx) {
                if (!seededOffset && widthPx > 0f && heightPx > 0f) {
                    offsetXPx = BackgroundTransformGeometry.translationPx(widthPx, scale, initialTransform.offsetX)
                    offsetYPx = BackgroundTransformGeometry.translationPx(heightPx, scale, initialTransform.offsetY)
                    seededOffset = true
                }
            }
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom)
                                    .coerceIn(BackgroundTransform.MIN_SCALE, BackgroundTransform.MAX_SCALE)
                                scale = newScale
                                val maxX = BackgroundTransformGeometry.maxTranslation(widthPx, newScale)
                                val maxY = BackgroundTransformGeometry.maxTranslation(heightPx, newScale)
                                offsetXPx = (offsetXPx + pan.x).coerceIn(-maxX, maxX)
                                offsetYPx = (offsetYPx + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetXPx
                            translationY = offsetYPx
                        },
                    contentScale = ContentScale.Crop
                )
            }
            // Dim + frosted sample card so the user sees the real in-app look.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.background.copy(alpha = 0.34f))
            )
            PreviewSampleCard(
                label = text("page.background.preview.sample"),
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 28.dp)
            )
        }
        PreviewTopHint(
            title = text("page.background.preview.title"),
            hint = text("page.background.preview.hint"),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        )
        PreviewBottomBar(
            resetLabel = text("page.background.preview.reset"),
            cancelLabel = text("page.background.preview.cancel"),
            applyLabel = text("page.background.preview.apply"),
            onReset = {
                scale = 1f
                offsetXPx = 0f
                offsetYPx = 0f
            },
            onCancel = onCancel,
            onApply = { onApply(currentTransform()) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
        )
    }
}

@Composable
private fun PreviewTopHint(title: String, hint: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        color = p.surface
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                title,
                style = EchoTypography.title,
                color = p.heading
            )
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                hint,
                style = EchoTypography.caption,
                color = p.muted
            )
        }
    }
}

@Composable
private fun PreviewSampleCard(label: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        color = p.surface
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                label,
                style = EchoTypography.title.copy(fontWeight = FontWeight.SemiBold),
                color = p.heading
            )
            Spacer(Modifier.padding(top = 6.dp))
            Text(
                "Preview",
                style = EchoTypography.body,
                color = p.muted
            )
        }
    }
}

@Composable
private fun PreviewBottomBar(
    resetLabel: String,
    cancelLabel: String,
    applyLabel: String,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviewActionButton(resetLabel, primary = false, modifier = Modifier.width(96.dp), onClick = onReset)
        Spacer(Modifier.weight(1f))
        PreviewActionButton(cancelLabel, primary = false, onClick = onCancel)
        PreviewActionButton(applyLabel, primary = true, onClick = onApply)
    }
}

@Composable
private fun PreviewActionButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = EchoTheme.colors()
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = EchoShapes.full,
        color = if (primary) p.accent else p.surface
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = EchoTypography.label,
                color = if (primary) p.onAccent else p.text,
                textAlign = TextAlign.Center
            )
        }
    }
}
