package app.yukine

import android.content.Context
import android.view.WindowManager

internal data class FloatingLyricsOverlaySettings(
    val textSizeSp: Int = DEFAULT_TEXT_SIZE_SP,
    val widthPercent: Int = DEFAULT_WIDTH_PERCENT,
    val verticalPositionPercent: Int = DEFAULT_VERTICAL_POSITION_PERCENT,
    val backgroundOpacityPercent: Int = DEFAULT_BACKGROUND_OPACITY_PERCENT,
    val transparentBackground: Boolean = false,
    val clickThrough: Boolean = false
) {
    fun normalized(): FloatingLyricsOverlaySettings = copy(
        textSizeSp = textSizeSp.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP),
        widthPercent = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT),
        verticalPositionPercent = verticalPositionPercent.coerceIn(
            MIN_VERTICAL_POSITION_PERCENT,
            MAX_VERTICAL_POSITION_PERCENT
        ),
        backgroundOpacityPercent = backgroundOpacityPercent.coerceIn(
            MIN_BACKGROUND_OPACITY_PERCENT,
            MAX_BACKGROUND_OPACITY_PERCENT
        )
    )

    companion object {
        const val MIN_TEXT_SIZE_SP = 12
        const val MAX_TEXT_SIZE_SP = 30
        const val DEFAULT_TEXT_SIZE_SP = 14

        const val MIN_WIDTH_PERCENT = 40
        const val MAX_WIDTH_PERCENT = 100
        const val DEFAULT_WIDTH_PERCENT = 100

        const val MIN_VERTICAL_POSITION_PERCENT = 0
        const val MAX_VERTICAL_POSITION_PERCENT = 85
        const val DEFAULT_VERTICAL_POSITION_PERCENT = 5

        const val MIN_BACKGROUND_OPACITY_PERCENT = 0
        const val MAX_BACKGROUND_OPACITY_PERCENT = 100
        const val DEFAULT_BACKGROUND_OPACITY_PERCENT = 87
    }
}

internal object FloatingLyricsOverlayWindowPolicy {
    fun windowFlags(clickThrough: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (clickThrough) {
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
}

internal class FloatingLyricsOverlaySettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): FloatingLyricsOverlaySettings =
        FloatingLyricsOverlaySettings(
            textSizeSp = preferences.getInt(KEY_TEXT_SIZE_SP, FloatingLyricsOverlaySettings.DEFAULT_TEXT_SIZE_SP),
            widthPercent = preferences.getInt(
                KEY_WIDTH_PERCENT,
                FloatingLyricsOverlaySettings.DEFAULT_WIDTH_PERCENT
            ),
            verticalPositionPercent = preferences.getInt(
                KEY_VERTICAL_POSITION_PERCENT,
                FloatingLyricsOverlaySettings.DEFAULT_VERTICAL_POSITION_PERCENT
            ),
            backgroundOpacityPercent = preferences.getInt(
                KEY_BACKGROUND_OPACITY_PERCENT,
                FloatingLyricsOverlaySettings.DEFAULT_BACKGROUND_OPACITY_PERCENT
            ),
            transparentBackground = preferences.getBoolean(KEY_TRANSPARENT_BACKGROUND, false),
            clickThrough = preferences.getBoolean(KEY_CLICK_THROUGH, false)
        ).normalized()

    fun save(settings: FloatingLyricsOverlaySettings) {
        val normalized = settings.normalized()
        preferences.edit()
            .putInt(KEY_TEXT_SIZE_SP, normalized.textSizeSp)
            .putInt(KEY_WIDTH_PERCENT, normalized.widthPercent)
            .putInt(KEY_VERTICAL_POSITION_PERCENT, normalized.verticalPositionPercent)
            .putInt(KEY_BACKGROUND_OPACITY_PERCENT, normalized.backgroundOpacityPercent)
            .putBoolean(KEY_TRANSPARENT_BACKGROUND, normalized.transparentBackground)
            .putBoolean(KEY_CLICK_THROUGH, normalized.clickThrough)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "floating_lyrics_overlay"
        private const val KEY_TEXT_SIZE_SP = "text_size_sp"
        private const val KEY_WIDTH_PERCENT = "width_percent"
        private const val KEY_VERTICAL_POSITION_PERCENT = "vertical_position_percent"
        private const val KEY_BACKGROUND_OPACITY_PERCENT = "background_opacity_percent"
        private const val KEY_TRANSPARENT_BACKGROUND = "transparent_background"
        private const val KEY_CLICK_THROUGH = "click_through"
    }
}
