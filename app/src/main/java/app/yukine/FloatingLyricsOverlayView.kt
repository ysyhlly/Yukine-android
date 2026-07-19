package app.yukine

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

internal class FloatingLyricsOverlayView(
    private val context: Context,
    initialSettings: FloatingLyricsOverlaySettings,
    private val onSettingsChanged: (FloatingLyricsOverlaySettings) -> Unit
) {
    private var settings = initialSettings.normalized()
    private var syncingControls = false
    private lateinit var clickThroughSwitch: Switch
    private lateinit var transparentBackgroundSwitch: Switch
    private lateinit var backgroundOpacitySlider: SeekBar

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    val lyricsView: TextView = TextView(context).apply {
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setTextColor(0xFFE0E0E0.toInt())
        gravity = android.view.Gravity.CENTER
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        contentDescription = context.getString(R.string.floating_lyrics_interaction_hint)
        typeface = ResourcesCompat.getFont(context, R.font.noto_sans_cjk_sc_regular)
            ?: android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    private val controlsView: LinearLayout = buildControls()

    init {
        root.addView(
            lyricsView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            controlsView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        applySettings(settings)
        setControlsVisible(false)
    }

    fun renderLyrics(text: String, visible: Boolean) {
        lyricsView.text = text
        lyricsView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun applySettings(next: FloatingLyricsOverlaySettings) {
        settings = next.normalized()
        lyricsView.textSize = settings.textSizeSp.toFloat()
        lyricsView.background = roundedBackground(
            color = Color.argb(
                FloatingLyricsOverlayWindowPolicy.backgroundAlpha(settings),
                26,
                26,
                26
            ),
            radiusDp = 14
        )
        syncingControls = true
        clickThroughSwitch.isChecked = settings.clickThrough
        transparentBackgroundSwitch.isChecked = settings.transparentBackground
        backgroundOpacitySlider.isEnabled = !settings.transparentBackground
        backgroundOpacitySlider.alpha = if (settings.transparentBackground) 0.45f else 1f
        syncingControls = false
        if (settings.clickThrough) {
            setControlsVisible(false)
        }
    }

    fun toggleControls() {
        setControlsVisible(controlsView.visibility != View.VISIBLE)
    }

    fun setControlsVisible(visible: Boolean) {
        controlsView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @Suppress("DEPRECATION")
    private fun buildControls(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.argb(248, 20, 24, 31), 14)
            elevation = dp(8).toFloat()
        }
        panel.addView(
            TextView(context).apply {
                text = context.getString(R.string.floating_lyrics_controls_title)
                textSize = 15f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(4))
            }
        )
        addSliderControl(
            panel = panel,
            minimum = FloatingLyricsOverlaySettings.MIN_TEXT_SIZE_SP,
            maximum = FloatingLyricsOverlaySettings.MAX_TEXT_SIZE_SP,
            current = settings.textSizeSp,
            label = { context.getString(R.string.floating_lyrics_text_size, it) },
            onValueChanged = { emit(settings.copy(textSizeSp = it)) }
        )
        addSliderControl(
            panel = panel,
            minimum = FloatingLyricsOverlaySettings.MIN_WIDTH_PERCENT,
            maximum = FloatingLyricsOverlaySettings.MAX_WIDTH_PERCENT,
            current = settings.widthPercent,
            label = { context.getString(R.string.floating_lyrics_width, it) },
            onValueChanged = { emit(settings.copy(widthPercent = it)) }
        )
        addSliderControl(
            panel = panel,
            minimum = FloatingLyricsOverlaySettings.MIN_VERTICAL_POSITION_PERCENT,
            maximum = FloatingLyricsOverlaySettings.MAX_VERTICAL_POSITION_PERCENT,
            current = settings.verticalPositionPercent,
            label = { context.getString(R.string.floating_lyrics_vertical_position, it) },
            onValueChanged = { emit(settings.copy(verticalPositionPercent = it)) }
        )
        backgroundOpacitySlider = addSliderControl(
            panel = panel,
            minimum = FloatingLyricsOverlaySettings.MIN_BACKGROUND_OPACITY_PERCENT,
            maximum = FloatingLyricsOverlaySettings.MAX_BACKGROUND_OPACITY_PERCENT,
            current = settings.backgroundOpacityPercent,
            label = { context.getString(R.string.floating_lyrics_background_opacity, it) },
            onValueChanged = { emit(settings.copy(backgroundOpacityPercent = it)) }
        )

        transparentBackgroundSwitch = Switch(context).apply {
            text = context.getString(R.string.floating_lyrics_transparent_background)
            setTextColor(0xFFE8EDF2.toInt())
            isChecked = settings.transparentBackground
            setPadding(0, dp(2), 0, dp(2))
            setOnCheckedChangeListener { _, checked ->
                if (!syncingControls) {
                    emit(settings.copy(transparentBackground = checked))
                }
            }
        }
        panel.addView(
            transparentBackgroundSwitch,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        clickThroughSwitch = Switch(context).apply {
            text = context.getString(R.string.floating_lyrics_click_through)
            setTextColor(0xFFE8EDF2.toInt())
            isChecked = settings.clickThrough
            setPadding(0, dp(2), 0, dp(6))
            setOnCheckedChangeListener { _, checked ->
                if (!syncingControls) {
                    if (checked) {
                        setControlsVisible(false)
                    }
                    emit(settings.copy(clickThrough = checked))
                }
            }
        }
        panel.addView(
            clickThroughSwitch,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        panel.addView(
            Button(context).apply {
                text = context.getString(R.string.floating_lyrics_hide_controls)
                setTextColor(Color.WHITE)
                background = roundedBackground(0xFF2F6E91.toInt(), 12)
                setOnClickListener { setControlsVisible(false) }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )
        return panel
    }

    private fun addSliderControl(
        panel: LinearLayout,
        minimum: Int,
        maximum: Int,
        current: Int,
        label: (Int) -> String,
        onValueChanged: (Int) -> Unit
    ): SeekBar {
        val valueLabel = TextView(context).apply {
            text = label(current)
            textSize = 12f
            setTextColor(0xFFBEC8D2.toInt())
        }
        panel.addView(valueLabel)
        val slider = SeekBar(context).apply {
            max = maximum - minimum
            progress = current - minimum
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = minimum + progress
                    valueLabel.text = label(value)
                    if (fromUser) {
                        onValueChanged(value)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        panel.addView(
            slider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )
        return slider
    }

    private fun emit(next: FloatingLyricsOverlaySettings) {
        settings = next.normalized()
        onSettingsChanged(settings)
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
