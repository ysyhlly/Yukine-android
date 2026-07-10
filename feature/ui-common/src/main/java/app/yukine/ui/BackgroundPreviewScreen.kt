package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yukine.BackgroundTransform
import app.yukine.BackgroundTransformLayout

// PREVIEW_UI_PLACEHOLDER

data class BackgroundPreviewLabels(
    val title: String,
    val hint: String,
    val reset: String,
    val cancel: String,
    val apply: String,
    val sample: String
) {
    companion object {
        fun defaults(): BackgroundPreviewLabels = BackgroundPreviewLabels(
            title = "调整背景",
            hint = "完整显示原图，双指缩放并拖动选择最终显示区域",
            reset = "重置",
            cancel = "取消",
            apply = "应用",
            sample = "示例卡片"
        )
    }
}

@Composable
fun BackgroundPreviewScreen(
    uri: Uri,
    labels: BackgroundPreviewLabels = BackgroundPreviewLabels.defaults(),
    initialTransform: BackgroundTransform,
    onCancel: () -> Unit,
    onApply: (BackgroundTransform) -> Unit
) {
    val p = EchoTheme.colors()
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    // Preview always uses the original image so zoom/pan fidelity matches the picked source.
    var scale by remember(initialTransform) { mutableFloatStateOf(initialTransform.scale) }
    var offsetXPx by remember { mutableFloatStateOf(0f) }
    var offsetYPx by remember { mutableFloatStateOf(0f) }
    var seededOffset by remember(uri, initialTransform) { mutableStateOf(false) }
    var previousCropFrame by remember(uri, initialTransform) {
        mutableStateOf(BackgroundTransformGeometry.CropFrame.EMPTY)
    }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }

    fun currentTransform(): BackgroundTransform {
        val cropFrame = bitmap?.let { current ->
            BackgroundTransformGeometry.previewCropFrame(
                sourceWidthPx = current.width.toFloat(),
                sourceHeightPx = current.height.toFloat(),
                viewportWidthPx = viewportW,
                viewportHeightPx = viewportH
            )
        } ?: BackgroundTransformGeometry.CropFrame.EMPTY
        return BackgroundTransform(
            scale = scale,
            offsetX = cropFrame.offsetFractionX(offsetXPx, scale),
            offsetY = cropFrame.offsetFractionY(offsetYPx, scale),
            layout = BackgroundTransformLayout.CROP_EDITOR
        ).normalized()
    }

    Box(modifier = Modifier.fillMaxSize().background(p.background)) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            LaunchedEffect(uri) {
                bitmap = ArtworkLoader.loadOriginal(context.applicationContext, uri)
            }
            LaunchedEffect(widthPx, heightPx) {
                viewportW = widthPx
                viewportH = heightPx
            }
            val current = bitmap
            val cropFrame = current?.let {
                BackgroundTransformGeometry.previewCropFrame(
                    sourceWidthPx = it.width.toFloat(),
                    sourceHeightPx = it.height.toFloat(),
                    viewportWidthPx = widthPx,
                    viewportHeightPx = heightPx
                )
            } ?: BackgroundTransformGeometry.CropFrame.EMPTY
            // Pixel offsets are convenient for gesture deltas, but do not survive an aspect-ratio
            // change. Reproject through the normalized crop fraction whenever the frame changes so
            // freeform windows and rotation keep showing the same selected source region.
            LaunchedEffect(cropFrame, initialTransform) {
                if (!seededOffset && cropFrame.isUsable) {
                    offsetXPx = cropFrame.translationX(scale, initialTransform.offsetX)
                    offsetYPx = cropFrame.translationY(scale, initialTransform.offsetY)
                    seededOffset = true
                } else if (cropFrame.isUsable && previousCropFrame.isUsable) {
                    offsetXPx = cropFrame.translationX(
                        scale,
                        previousCropFrame.offsetFractionX(offsetXPx, scale)
                    )
                    offsetYPx = cropFrame.translationY(
                        scale,
                        previousCropFrame.offsetFractionY(offsetYPx, scale)
                    )
                }
                if (cropFrame.isUsable) {
                    previousCropFrame = cropFrame
                }
            }
            val editorGestureModifier = if (cropFrame.isUsable) {
                Modifier.pointerInput(cropFrame) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom)
                            .coerceIn(BackgroundTransform.MIN_SCALE, BackgroundTransform.MAX_SCALE)
                        scale = newScale
                        val maxX = cropFrame.maxTranslationX(newScale)
                        val maxY = cropFrame.maxTranslationY(newScale)
                        offsetXPx = (offsetXPx + pan.x).coerceIn(-maxX, maxX)
                        offsetYPx = (offsetYPx + pan.y).coerceIn(-maxY, maxY)
                    }
                }
            } else {
                Modifier
            }
            Box(Modifier.fillMaxSize().then(editorGestureModifier)) {
                if (current != null && cropFrame.isUsable) {
                    val imageWidth = with(density) { cropFrame.imageWidthPx.toDp() }
                    val imageHeight = with(density) { cropFrame.imageHeightPx.toDp() }
                    val frameWidth = with(density) { cropFrame.frameWidthPx.toDp() }
                    val frameHeight = with(density) { cropFrame.frameHeightPx.toDp() }
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(imageWidth, imageHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetXPx
                                translationY = offsetYPx
                            },
                        contentScale = ContentScale.FillBounds
                    )
                    // A light treatment retains the page-preview feel without hiding the original.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(p.background.copy(alpha = 0.18f))
                    )
                    PreviewSampleCard(
                        label = labels.sample,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .padding(horizontal = 28.dp)
                    )
                    // The fixed frame is the exact phone-aspect area rendered after Apply.
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(frameWidth, frameHeight)
                            .border(2.dp, p.accent, RectangleShape)
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(p.background.copy(alpha = 0.18f))
                    )
                }
            }
        }
        PreviewTopHint(
            title = labels.title,
            hint = labels.hint,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp)
        )
        PreviewBottomBar(
            resetLabel = labels.reset,
            cancelLabel = labels.cancel,
            applyLabel = labels.apply,
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
