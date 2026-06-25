package app.yukine

/**
 * User-chosen zoom + pan for a page background image.
 *
 * - [scale] multiplies the base `Crop` fit (1.0 = exactly cover the viewport). Range is clamped to
 *   [[MIN_SCALE], [MAX_SCALE]].
 * - [offsetX] / [offsetY] are normalized pan fractions in [-1, 1], where ±1 means panned all the
 *   way to the edge of the overflow at the current scale. Storing fractions (not pixels) keeps the
 *   transform resolution-independent so the same value renders consistently on any screen size.
 */
data class BackgroundTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    val isIdentity: Boolean
        get() = scale in (1f - EPSILON)..(1f + EPSILON) &&
            offsetX in -EPSILON..EPSILON &&
            offsetY in -EPSILON..EPSILON

    fun normalized(): BackgroundTransform {
        val safeScale = scale.coerceIn(MIN_SCALE, MAX_SCALE).takeIf { it.isFinite() } ?: 1f
        val safeX = offsetX.coerceIn(-1f, 1f).takeIf { it.isFinite() } ?: 0f
        val safeY = offsetY.coerceIn(-1f, 1f).takeIf { it.isFinite() } ?: 0f
        return BackgroundTransform(safeScale, safeX, safeY)
    }

    /** Compact "scale,offsetX,offsetY" serialization for the settings store. */
    fun encode(): String {
        if (isIdentity) {
            return ""
        }
        val n = normalized()
        return "${n.scale}|${n.offsetX}|${n.offsetY}"
    }

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 4f
        private const val EPSILON = 0.001f

        @JvmField
        val IDENTITY = BackgroundTransform()

        @JvmStatic
        fun decode(encoded: String?): BackgroundTransform {
            val raw = encoded?.trim().orEmpty()
            if (raw.isEmpty()) {
                return IDENTITY
            }
            val parts = raw.split('|')
            if (parts.size != 3) {
                return IDENTITY
            }
            val scale = parts[0].toFloatOrNull() ?: return IDENTITY
            val offsetX = parts[1].toFloatOrNull() ?: return IDENTITY
            val offsetY = parts[2].toFloatOrNull() ?: return IDENTITY
            return BackgroundTransform(scale, offsetX, offsetY).normalized()
        }
    }
}
