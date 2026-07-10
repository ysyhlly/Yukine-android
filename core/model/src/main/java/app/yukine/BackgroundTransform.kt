package app.yukine

/**
 * Rendering semantics for a persisted [BackgroundTransform].
 *
 * `LEGACY_CROP` keeps the original three-part settings value on the old `ContentScale.Crop`
 * geometry. `CROP_EDITOR` is used by the source-aware crop editor introduced in the versioned
 * `v2` settings value.
 */
enum class BackgroundTransformLayout {
    LEGACY_CROP,
    CROP_EDITOR
}

/**
 * User-chosen zoom + pan for a page background image.
 *
 * - [scale] zooms the selected page-cover crop (1.0 = the base phone-aspect crop). New
 *   [BackgroundTransformLayout.CROP_EDITOR] values cannot drop below that base crop, so their
 *   final page background never exposes empty edges. Historical
 *   [BackgroundTransformLayout.LEGACY_CROP] values retain their old 0.5 lower bound.
 * - [offsetX] / [offsetY] are normalized pan fractions in [-1, 1], where ±1 means panned all the
 *   way to the edge of the overflow at the current scale. Storing fractions (not pixels) keeps the
 *   transform resolution-independent so the same value renders consistently on any screen size.
 */
data class BackgroundTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val layout: BackgroundTransformLayout = BackgroundTransformLayout.CROP_EDITOR
) {
    val isIdentity: Boolean
        get() = scale in (1f - EPSILON)..(1f + EPSILON) &&
            offsetX in -EPSILON..EPSILON &&
            offsetY in -EPSILON..EPSILON

    fun normalized(): BackgroundTransform {
        val minScale = when (layout) {
            BackgroundTransformLayout.LEGACY_CROP -> LEGACY_MIN_SCALE
            BackgroundTransformLayout.CROP_EDITOR -> MIN_SCALE
        }
        val safeScale = scale.coerceIn(minScale, MAX_SCALE).takeIf { it.isFinite() } ?: 1f
        val safeX = offsetX.coerceIn(-1f, 1f).takeIf { it.isFinite() } ?: 0f
        val safeY = offsetY.coerceIn(-1f, 1f).takeIf { it.isFinite() } ?: 0f
        return BackgroundTransform(safeScale, safeX, safeY, layout)
    }

    /**
     * Compact serialization for the settings store.
     *
     * Existing three-part values retain [BackgroundTransformLayout.LEGACY_CROP]. New crop-editor
     * values use `v2|scale|offsetX|offsetY`. Identity remains empty to preserve the compact
     * settings representation; decoding an empty value returns the new [IDENTITY] layout.
     */
    fun encode(): String {
        val normalized = normalized()
        if (normalized.isIdentity) {
            return ""
        }
        return when (normalized.layout) {
            BackgroundTransformLayout.LEGACY_CROP -> {
                "${normalized.scale}|${normalized.offsetX}|${normalized.offsetY}"
            }
            BackgroundTransformLayout.CROP_EDITOR -> {
                "$CROP_EDITOR_VERSION|${normalized.scale}|${normalized.offsetX}|${normalized.offsetY}"
            }
        }
    }

    companion object {
        const val LEGACY_MIN_SCALE = 0.5f
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
        private const val EPSILON = 0.001f
        private const val CROP_EDITOR_VERSION = "v2"

        @JvmField
        val IDENTITY = BackgroundTransform()

        @JvmStatic
        fun decode(encoded: String?): BackgroundTransform {
            val raw = encoded?.trim().orEmpty()
            if (raw.isEmpty()) {
                return IDENTITY
            }
            val parts = raw.split('|')
            return when {
                parts.size == 3 -> decodeParts(parts, 0, BackgroundTransformLayout.LEGACY_CROP)
                parts.size == 4 && parts[0] == CROP_EDITOR_VERSION -> {
                    decodeParts(parts, 1, BackgroundTransformLayout.CROP_EDITOR)
                }
                else -> IDENTITY
            }
        }

        private fun decodeParts(
            parts: List<String>,
            valueStart: Int,
            layout: BackgroundTransformLayout
        ): BackgroundTransform {
            val scale = parts[valueStart].toFloatOrNull() ?: return IDENTITY
            val offsetX = parts[valueStart + 1].toFloatOrNull() ?: return IDENTITY
            val offsetY = parts[valueStart + 2].toFloatOrNull() ?: return IDENTITY
            return BackgroundTransform(scale, offsetX, offsetY, layout).normalized()
        }
    }
}
