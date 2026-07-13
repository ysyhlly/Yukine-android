package app.yukine

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal object CustomBackgroundAccentExtractor {
    private const val TARGET_SIZE = 128
    private const val HUE_BUCKETS = 24
    private const val SATURATION_BUCKETS = 3
    private const val VALUE_BUCKETS = 3
    private const val BUCKET_COUNT = HUE_BUCKETS * SATURATION_BUCKETS * VALUE_BUCKETS

    fun extract(contentResolver: ContentResolver, backgrounds: PageBackgrounds): Int? {
        val rawUri = backgrounds.accentSourceUri()
        if (rawUri.isBlank()) return null
        val bitmap = decodeSampled(contentResolver, Uri.parse(rawUri)) ?: return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            dominantAccent(pixels)
        } finally {
            bitmap.recycle()
        }
    }

    internal fun dominantAccent(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val weights = DoubleArray(BUCKET_COUNT)
        val reds = DoubleArray(BUCKET_COUNT)
        val greens = DoubleArray(BUCKET_COUNT)
        val blues = DoubleArray(BUCKET_COUNT)
        val hsv = FloatArray(3)

        pixels.forEach { pixel ->
            if (Color.alpha(pixel) < 160) return@forEach
            Color.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (saturation < 0.16f || value < 0.12f || value > 0.96f) return@forEach

            val hueBucket = ((hsv[0] / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val saturationBucket = (saturation * SATURATION_BUCKETS).toInt().coerceIn(0, SATURATION_BUCKETS - 1)
            val valueBucket = (value * VALUE_BUCKETS).toInt().coerceIn(0, VALUE_BUCKETS - 1)
            val bucket = (hueBucket * SATURATION_BUCKETS + saturationBucket) * VALUE_BUCKETS + valueBucket
            val weight = 1.0 + saturation * 2.5 + (1.0 - abs(value - 0.62f)) * 1.5
            weights[bucket] += weight
            reds[bucket] += Color.red(pixel) * weight
            greens[bucket] += Color.green(pixel) * weight
            blues[bucket] += Color.blue(pixel) * weight
        }

        val winner = weights.indices.maxByOrNull { weights[it] } ?: return null
        val total = weights[winner]
        if (total <= 0.0) return null
        val averaged = Color.rgb(
            (reds[winner] / total).roundToInt().coerceIn(0, 255),
            (greens[winner] / total).roundToInt().coerceIn(0, 255),
            (blues[winner] / total).roundToInt().coerceIn(0, 255)
        )
        Color.colorToHSV(averaged, hsv)
        hsv[1] = max(hsv[1], 0.45f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.82f)
        return Color.HSVToColor(hsv)
    }

    private fun decodeSampled(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > TARGET_SIZE * 2 || bounds.outHeight / sampleSize > TARGET_SIZE * 2) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }
}
