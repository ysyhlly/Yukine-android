package app.yukine.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yukine.BackgroundTransform

object EchoPageDefaults {
    val horizontalPadding: Dp = 18.dp
    val topPadding: Dp = 16.dp
    val bottomPadding: Dp = 100.dp
    val itemSpacing: Dp = 16.dp
    val sectionSpacing: Dp = 22.dp
}

@Composable
fun Modifier.echoPageBackground(): Modifier {
    val p = EchoTheme.colors()
    return this
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                listOf(
                    p.background,
                    p.backgroundAlt
                )
            )
        )
}

@Composable
fun EchoPageBackground(
    backgroundUri: String,
    modifier: Modifier = Modifier,
    transform: BackgroundTransform = BackgroundTransform.IDENTITY,
    content: @Composable () -> Unit
) {
    val p = EchoTheme.colors()
    val hasCustomBackground = backgroundUri.isNotBlank()
    Box(modifier = modifier.echoPageBackground()) {
        AsyncPageBackgroundImage(backgroundUri, transform)
        if (hasCustomBackground) {
            // 轻一点的整页蒙版：磨砂卡片本身半透，过重的蒙版会让背景看不见。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.background.copy(alpha = 0.34f))
            )
        }
        CompositionLocalProvider(LocalEchoCustomBackground provides hasCustomBackground) {
            content()
        }
    }
}

@Composable
private fun AsyncPageBackgroundImage(
    backgroundUri: String,
    transform: BackgroundTransform = BackgroundTransform.IDENTITY
) {
    if (backgroundUri.isBlank()) {
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val uri = remember(backgroundUri) { runCatching { Uri.parse(backgroundUri) }.getOrNull() }
    var bitmap by remember(backgroundUri) {
        mutableStateOf(uri?.let { ArtworkLoader.peekAnySize(it) })
    }
    val safeTransform = remember(transform) { transform.normalized() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val targetPx = with(density) {
            maxOf(maxWidth.toPx(), maxHeight.toPx()).toInt()
        }.coerceIn(1, ArtworkLoader.MAX_TARGET_PX)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        LaunchedEffect(uri, targetPx) {
            bitmap = uri?.let { ArtworkLoader.load(context.applicationContext, it, targetPx) }
        }
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = safeTransform.scale
                        scaleY = safeTransform.scale
                        translationX = BackgroundTransformGeometry.translationPx(
                            widthPx,
                            safeTransform.scale,
                            safeTransform.offsetX
                        )
                        translationY = BackgroundTransformGeometry.translationPx(
                            heightPx,
                            safeTransform.scale,
                            safeTransform.offsetY
                        )
                    },
                contentScale = ContentScale.Crop
            )
        }
    }
}

fun echoPagePadding(
    top: Dp = EchoPageDefaults.topPadding,
    bottom: Dp = EchoPageDefaults.bottomPadding
): PaddingValues = PaddingValues(
    start = EchoPageDefaults.horizontalPadding,
    top = top,
    end = EchoPageDefaults.horizontalPadding,
    bottom = bottom
)

@Composable
fun EchoPageTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String? = null,
    onBack: Runnable? = null
) {
    val p = EchoTheme.colors()
    if (title.isBlank() && subtitle.isNullOrBlank()) return
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Surface(
                    onClick = { onBack.run() },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = backLabel ?: "返回" },
                    shape = EchoShapes.small,
                    color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EchoIcon(EchoIconKind.Back, Modifier.size(20.dp), p.muted)
                    }
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = EchoTypography.display.copy(fontSize = 22.sp, lineHeight = 28.sp),
                        color = p.heading,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = EchoTypography.caption,
                        color = p.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EchoSectionTitle(title: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    if (title.isBlank()) return
    Text(
        title,
        style = EchoTypography.title,
        color = p.text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 4.dp, end = 2.dp, bottom = 2.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun EchoEmptyCard(message: String, modifier: Modifier = Modifier) {
    val p = EchoTheme.colors()
    EchoGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = EchoShapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 26.dp)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                message,
                style = EchoTypography.body.copy(fontWeight = FontWeight.Medium),
                color = p.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
