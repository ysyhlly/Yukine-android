package app.yukine

import android.content.Context
import android.os.Build
import android.view.InputManager
import android.view.WindowManager

internal data class FloatingLyricsOverlaySettings(
    val textSizeSp: Int = DEFAULT_TEXT_SIZE_SP,
    val widthPercent: Int = DEFAULT_WIDTH_PERCENT,
    val positionXFraction: Float = DEFAULT_POSITION_X_FRACTION,
    val positionYFraction: Float = DEFAULT_POSITION_Y_FRACTION,
    val backgroundOpacityPercent: Int = DEFAULT_BACKGROUND_OPACITY_PERCENT,
    val transparentBackground: Boolean = false
) {
    fun normalized(): FloatingLyricsOverlaySettings = copy(
        textSizeSp = textSizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP),
        widthPercent = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT),
        positionXFraction = positionXFraction.finiteOr(DEFAULT_POSITION_X_FRACTION).coerceIn(0f, 1f),
        positionYFraction = positionYFraction.finiteOr(DEFAULT_POSITION_Y_FRACTION).coerceIn(0f, 1f),
        backgroundOpacityPercent = backgroundOpacityPercent.coerceIn(
            MIN_BACKGROUND_OPACITY_PERCENT,
            MAX_BACKGROUND_OPACITY_PERCENT
        )
    )

    companion object {
        const val MIN_TEXT_SIZE_SP = 12
        const val MAX_TEXT_SIZE_SP = 30
        const val DEFAULT_TEXT_SIZE_SP = 16

        const val MIN_WIDTH_PERCENT = 40
        const val MAX_WIDTH_PERCENT = 100
        const val DEFAULT_WIDTH_PERCENT = 88

        const val DEFAULT_POSITION_X_FRACTION = 0.5f
        const val DEFAULT_POSITION_Y_FRACTION = 0.08f

        const val MIN_BACKGROUND_OPACITY_PERCENT = 0
        const val MAX_BACKGROUND_OPACITY_PERCENT = 100
        const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 92

        private fun Float.finiteOr(fallback: Float): Float =
            if (isFinite()) this else fallback
    }
}

internal object FloatingLyricsOverlayWindowPolicy {
    private const val LEGACY_MAX_OBSCURING_OPACITY = 0.8f
    private const val TOUCH_OPACITY_SAFETY_MARGIN = 0.01f

    fun windowFlags(interaction: FloatingLyricsInteraction): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (interaction == FloatingLyricsInteraction.ClickThrough) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    fun backgroundAlpha(settings: FloatingLyricsOverlaySettings): Int =
        if (settings.transparentBackground) {
            0
        } else {
            settings.backgroundOpacityPercent.coerceIn(0, 100) * 255 / 100
        }

    fun windowAlpha(
        interaction: FloatingLyricsInteraction,
        maximumObscuringOpacity: Float
    ): Float {
        if (interaction != FloatingLyricsInteraction.ClickThrough) return 1f
        return (maximumObscuringOpacity - TOUCH_OPACITY_SAFETY_MARGIN)
            .coerceIn(0f, LEGACY_MAX_OBSCURING_OPACITY - TOUCH_OPACITY_SAFETY_MARGIN)
    }

    fun maximumObscuringOpacity(context: Context): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return LEGACY_MAX_OBSCURING_OPACITY
        }
        val inputManager = context.getSystemService(InputManager::class.java)
        return try {
            inputManager?.maximumObscuringOpacityForTouch ?: LEGACY_MAX_OBSCURING_OPACITY
        } catch (_: RuntimeException) {
            LEGACY_MAX_OBSCURING_OPACITY
        }
    }
}

internal class FloatingLyricsOverlaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): FloatingLyricsOverlaySettings {
        val legacyY = preferences.getInt(
            KEY_LEGACY_VERTICAL_POSITION_PERCENT,
            (FloatingLyricsOverlaySettings.DEFAULT_POSITION_Y_FRACTION * 100).toInt()
        )
        val settings = FloatingLyricsOverlaySettings(
            textSizeSp = preferences.getInt(
                KEY_TEXT_SIZE_SP,
                FloatingLyricsOverlaySettings.DEFAULT_TEXT_SIZE_SP
            ),
            widthPercent = preferences.getInt(
                KEY_WIDTH_PERCENT,
                FloatingLyricsOverlaySettings.DEFAULT_WIDTH_PERCENT
            ),
            positionXFraction = preferences.getFloat(
                KEY_POSITION_X_FRACTION,
                FloatingLyricsOverlaySettings.DEFAULT_POSITION_X_FRACTION
            ),
            positionYFraction = preferences.getFloat(
                KEY_POSITION_Y_FRACTION,
                legacyY / 100f
            ),
            backgroundOpacityPercent = preferences.getInt(
                KEY_BACKGROUND_OPACITY_PERCENT,
                FloatingLyricsOverlaySettings.DEFAULT_BACKGROUND_OPACITY_PERCENT
            ),
            transparentBackground = preferences.getBoolean(KEY_TRANSPARENT_BACKGROUND, false)
        ).normalized()
        migrateLegacyKeys(settings)
        return settings
    }

    fun save(settings: FloatingLyricsOverlaySettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putInt(KEY_TEXT_SIZE_SP, normalized.textSizeSp)
            .putInt(KEY_WIDTH_PERCENT, normalized.widthPercent)
            .putFloat(KEY_POSITION_X_FRACTION, normalized.positionXFraction)
            .putFloat(KEY_POSITION_Y_FRACTION, normalized.positionYFraction)
            .putInt(KEY_BACKGROUND_OPACITY_PERCENT, normalized.backgroundOpacityPercent)
            .putBoolean(KEY_TRANSPARENT_BACKGROUND, normalized.transparentBackground)
            .remove(KEY_LEGACY_VERTICAL_POSITION_PERCENT)
            .remove(KEY_LEGACY_CLICK_THROUGH)
            .apply()
    }

    fun reset(): FloatingLyricsOverlaySettings =
        FloatingLyricsOverlaySettings().also(::save)

    private fun migrateLegacyKeys(settings: FloatingLyricsOverlaySettings) {
        if (
            preferences.contains(KEY_LEGACY_VERTICAL_POSITION_PERCENT) ||
            preferences.contains(KEY_LEGACY_CLICK_THROUGH)
        ) {
            save(settings)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "floating_lyrics_overlay"
        private const val KEY_TEXT_SIZE_SP = "text_size_sp"
        private const val KEY_WIDTH_PERCENT = "width_percent"
        private const val KEY_POSITION_X_FRACTION = "position_x_fraction"
        private const val KEY_POSITION_Y_FRACTION = "position_y_fraction"
        private const val KEY_BACKGROUND_OPACITY_PERCENT = "background_opacity_percent"
        private const val KEY_TRANSPARENT_BACKGROUND = "transparent_background"
        private const val KEY_LEGACY_VERTICAL_POSITION_PERCENT = "vertical_position_percent"
        private const val KEY_LEGACY_CLICK_THROUGH = "click_through"
    }
}
