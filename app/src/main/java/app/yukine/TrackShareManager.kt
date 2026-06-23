package app.yukine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import app.yukine.model.Track
import app.yukine.streaming.StreamingPlaybackAdapter
import app.yukine.streaming.StreamingProviderName
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class TrackShareCard(
    val title: String,
    val text: String
)

data class TrackMusicSharePayload(
    val provider: StreamingProviderName,
    val providerTrackId: String,
    val url: String,
    val oneBotJson: String
)

@Singleton
class TrackShareManager @Inject constructor() {
    fun share(context: Context, track: Track, style: String): Intent {
        return when (TrackShareStyle.normalize(style)) {
            TrackShareStyle.CARD -> shareCardImage(context, track)
            TrackShareStyle.PLATFORM_CARD -> sharePlatformCard(track)
            else -> shareText(track)
        }
    }

    fun share(track: Track): Intent = shareText(track)

    fun card(track: Track): TrackShareCard {
        val source = streamingSource(track)
        val title = "分享歌曲：${track.title}"
        val lines = buildList {
            add("Yukine 音源卡片")
            add("")
            add("歌曲：${track.title}")
            add("歌手：${track.artist}")
            if (track.album.isNotBlank()) {
                add("专辑：${track.album}")
            }
            if (source != null) {
                add("音源：${providerDisplayName(source.provider)}")
                add("音源 ID：${source.providerTrackId}")
                shareUrl(source.provider, source.providerTrackId)?.let { add("链接：$it") }
            } else if (track.contentUri?.toString()?.startsWith("http", ignoreCase = true) == true) {
                add("链接：${track.contentUri}")
            } else {
                add("来源：本地曲库")
            }
            add("")
            add("来自 Yukine")
        }
        return TrackShareCard(title, lines.joinToString("\n"))
    }

    private fun shareText(track: Track): Intent {
        val card = card(track)
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, card.title)
            .putExtra(Intent.EXTRA_TITLE, card.title)
            .putExtra(Intent.EXTRA_TEXT, card.text)
    }

    private fun sharePlatformCard(track: Track): Intent {
        val source = streamingSource(track)
        val url = source?.let { shareUrl(it.provider, it.providerTrackId) }
            ?: track.contentUri?.toString()?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (url.isNullOrBlank()) {
            return shareText(track)
        }
        val title = "${track.title} - ${track.artist}"
        val musicPayload = source?.let { musicSharePayload(it.provider, it.providerTrackId, track) }
        val text = musicPayload?.let {
            buildString {
                appendLine(it.url)
                appendLine()
                appendLine("Yukine 音乐卡片")
                appendLine("歌曲：${track.title}")
                appendLine("歌手：${track.artist}")
                appendLine("协议：${it.oneBotJson}")
            }
        } ?: url
        return Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TITLE, title)
            .putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun shareCardImage(context: Context, track: Track): Intent {
        val image = renderShareCard(context, track)
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < System.currentTimeMillis() - 24L * 60L * 60L * 1000L) {
                file.delete()
            }
        }
        val file = File(dir, "yukine-share-${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = shareText(track)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = android.content.ClipData.newUri(context.contentResolver, "Yukine share card", uri)
        return intent
    }

    private fun renderShareCard(context: Context, track: Track): Bitmap {
        val width = 940
        val height = 380
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.TRANSPARENT)

        val card = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = Color.WHITE
        canvas.drawRoundRect(card, 26f, 26f, paint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 32, 40)
            textSize = 46f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 126, 142)
            textSize = 34f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(126, 133, 148)
            textSize = 32f
        }

        val artRect = RectF(705f, 44f, 895f, 234f)
        drawArtwork(context, canvas, paint, track.albumArtUri, artRect)

        canvas.drawText(ellipsize(track.title, titlePaint, 620f), 42f, 94f, titlePaint)
        canvas.drawText(ellipsize(track.artist, artistPaint, 620f), 42f, 154f, artistPaint)

        val source = streamingSource(track)
        val sourceLabel = source?.provider?.let(::providerDisplayName) ?: "Yukine 本地曲库"
        drawProviderBadge(canvas, paint, source?.provider, 42f, 284f)
        canvas.drawText(ellipsize(sourceLabel, sourcePaint, 560f), 100f, 316f, sourcePaint)

        return bitmap
    }

    private fun drawArtwork(context: Context, canvas: Canvas, paint: Paint, uri: Uri?, rect: RectF) {
        val source = uri?.takeIf { it != Uri.EMPTY }?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
        if (source == null) {
            paint.color = Color.rgb(230, 237, 250)
            canvas.drawRoundRect(rect, 14f, 14f, paint)
            paint.color = Color.rgb(74, 132, 235)
            canvas.drawCircle(rect.centerX(), rect.centerY(), 46f, paint)
            return
        }
        val shaderBitmap = Bitmap.createScaledBitmap(source, rect.width().toInt(), rect.height().toInt(), true)
        val save = canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, 14f, 14f, Path.Direction.CW) })
        canvas.drawBitmap(shaderBitmap, rect.left, rect.top, null)
        canvas.restoreToCount(save)
    }

    private fun drawProviderBadge(canvas: Canvas, paint: Paint, provider: StreamingProviderName?, left: Float, top: Float) {
        val size = 38f
        val rect = RectF(left, top - size + 4f, left + size, top + 4f)
        paint.color = when (provider) {
            StreamingProviderName.NETEASE -> Color.rgb(225, 47, 39)
            StreamingProviderName.QQ_MUSIC -> Color.rgb(32, 180, 94)
            StreamingProviderName.KUGOU -> Color.rgb(43, 132, 238)
            StreamingProviderName.BILIBILI -> Color.rgb(251, 114, 153)
            StreamingProviderName.LUOXUE -> Color.rgb(99, 102, 241)
            else -> Color.rgb(78, 132, 235)
        }
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.color = Color.WHITE
        paint.textSize = 23f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        val glyph = when (provider) {
            StreamingProviderName.NETEASE -> "网"
            StreamingProviderName.QQ_MUSIC -> "Q"
            StreamingProviderName.KUGOU -> "酷"
            StreamingProviderName.BILIBILI -> "B"
            StreamingProviderName.LUOXUE -> "LX"
            else -> "Y"
        }
        val bounds = Rect()
        paint.getTextBounds(glyph, 0, glyph.length, bounds)
        canvas.drawText(glyph, rect.centerX() - bounds.width() / 2f, rect.centerY() + bounds.height() / 2f, paint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) {
            return text
        }
        val suffix = "..."
        var end = max(0, text.length - 1)
        while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) {
            end--
        }
        return text.substring(0, end) + suffix
    }

    private fun streamingSource(track: Track): StreamingShareSource? {
        val provider = StreamingPlaybackAdapter.providerName(track.dataPath) ?: return null
        val providerTrackId = providerTrackIdForShare(track.dataPath).takeIf { it.isNotBlank() }
            ?: StreamingPlaybackAdapter.providerTrackId(track.dataPath).takeIf { it.isNotBlank() }
            ?: return null
        return StreamingShareSource(provider, providerTrackId)
    }

    private fun providerTrackIdForShare(dataPath: String): String {
        val marker = "streaming:"
        val start = dataPath.indexOf(marker)
        if (start < 0) {
            return ""
        }
        val remainder = dataPath.substring(start + marker.length)
        val providerEnd = remainder.indexOf(':')
        if (providerEnd <= 0 || providerEnd >= remainder.length - 1) {
            return ""
        }
        return remainder.substring(providerEnd + 1)
            .substringBefore('|')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
    }

    private fun providerDisplayName(provider: StreamingProviderName): String =
        when (provider) {
            StreamingProviderName.NETEASE -> "网易云音乐"
            StreamingProviderName.QQ_MUSIC -> "QQ 音乐"
            StreamingProviderName.KUGOU -> "酷狗音乐"
            StreamingProviderName.BILIBILI -> "哔哩哔哩"
            StreamingProviderName.YOUTUBE -> "YouTube"
            StreamingProviderName.SOUNDCLOUD -> "SoundCloud"
            StreamingProviderName.SPOTIFY -> "Spotify"
            StreamingProviderName.TIDAL -> "TIDAL"
            StreamingProviderName.M3U8 -> "M3U8"
            StreamingProviderName.LUOXUE -> "LX/洛雪音源"
            StreamingProviderName.PLUGIN -> "自定义插件"
            StreamingProviderName.MOCK -> "Yukine 测试音源"
        }

    private fun shareUrl(provider: StreamingProviderName, providerTrackId: String): String? =
        when (provider) {
            StreamingProviderName.NETEASE -> "https://music.163.com/song?id=$providerTrackId"
            StreamingProviderName.QQ_MUSIC -> "https://y.qq.com/n/ryqq/songDetail/$providerTrackId"
            StreamingProviderName.KUGOU -> "https://www.kugou.com/song/#hash=$providerTrackId"
            StreamingProviderName.BILIBILI -> "https://www.bilibili.com/audio/au$providerTrackId"
            StreamingProviderName.YOUTUBE -> "https://music.youtube.com/watch?v=$providerTrackId"
            StreamingProviderName.SOUNDCLOUD -> "https://soundcloud.com/search/sounds?q=$providerTrackId"
            StreamingProviderName.SPOTIFY -> "https://open.spotify.com/track/$providerTrackId"
            StreamingProviderName.TIDAL -> "https://tidal.com/browse/track/$providerTrackId"
            StreamingProviderName.LUOXUE -> "lxmusic://music/song/$providerTrackId"
            else -> null
        }

    fun musicSharePayload(track: Track): TrackMusicSharePayload? {
        val source = streamingSource(track) ?: return null
        return musicSharePayload(source.provider, source.providerTrackId, track)
    }

    private fun musicSharePayload(
        provider: StreamingProviderName,
        providerTrackId: String,
        track: Track
    ): TrackMusicSharePayload? {
        val url = shareUrl(provider, providerTrackId) ?: return null
        val json = when (provider) {
            StreamingProviderName.NETEASE -> """{"type":"music","data":{"type":"163","id":"${escapeJson(providerTrackId)}"}}"""
            StreamingProviderName.QQ_MUSIC -> """{"type":"music","data":{"type":"custom","url":"${escapeJson(url)}","audio":"${escapeJson(track.contentUri?.toString().orEmpty())}","title":"${escapeJson(track.title)}","image":"${escapeJson(track.albumArtUri?.toString().orEmpty())}","singer":"${escapeJson(track.artist)}"}}"""
            else -> return null
        }
        return TrackMusicSharePayload(provider, providerTrackId, url, json)
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private data class StreamingShareSource(
        val provider: StreamingProviderName,
        val providerTrackId: String
    )
}
