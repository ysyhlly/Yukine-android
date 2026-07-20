package app.yukine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs
import kotlin.math.roundToInt

internal class FloatingLyricsOverlayView(
    private val context: Context,
    initialSettings: FloatingLyricsOverlaySettings,
    private val onAction: (FloatingLyricsOverlayAction) -> Unit
) {
    private var settings = initialSettings.normalized()
    private var presentation: FloatingLyricsPresentation.Visible =
        FloatingLyricsPresentation.Visible(
            FloatingLyricsMode.Compact,
            FloatingLyricsInteraction.Interactive
        )
    private var syncingControls = false

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private val compactSurface = LinearLayout(context).apply {
        gravity = Gravity.CENTER
        minimumHeight = dp(48)
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        isClickable = true
        isFocusable = true
    }

    val lyricsView: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        contentDescription = context.getString(R.string.floating_lyrics_interaction_hint)
        typeface = ResourcesCompat.getFont(context, R.font.noto_sans_cjk_sc_regular)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val artworkView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageResource(R.drawable.ic_echo_monochrome)
        contentDescription = context.getString(R.string.floating_lyrics_artwork_description)
        background = roundedBackground(PANEL_SOFT, 10)
        clipToOutline = true
    }

    private val titleView = TextView(context).apply {
        setTextColor(TEXT_PRIMARY)
        textSize = 15f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.DEFAULT_BOLD
    }

    private val artistView = TextView(context).apply {
        setTextColor(TEXT_MUTED)
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private val playPauseButton = imageButton(
        R.drawable.ic_notif_play,
        R.string.play,
        accent = true
    ) {
        onAction(FloatingLyricsOverlayAction.PlayPause)
    }

    private val opacitySlider = SeekBar(context).apply {
        max = 100
        contentDescription = context.getString(R.string.floating_lyrics_background_opacity_description)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (!syncingControls && seekBar != null) {
                    val progress = seekBar.progress
                    seekBar.post {
                        onAction(FloatingLyricsOverlayAction.UpdateBackgroundOpacity(progress))
                    }
                }
            }
        })
    }

    private val lockConfirmation = buildLockConfirmation()
    private val controlsView: LinearLayout = buildControls()

    init {
        compactSurface.addView(
            lyricsView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            compactSurface,
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
            ).apply { topMargin = dp(6) }
        )
        installMoveGesture(compactSurface)
        applySettings(settings)
        renderPresentation(presentation, animate = false)
    }

    fun bindState(
        state: FloatingLyricsState,
        artwork: Bitmap?
    ) {
        if (lyricsView.text?.toString() != state.activeLine) {
            lyricsView.text = state.activeLine
        }
        titleView.text = state.trackTitle.ifBlank {
            context.getString(R.string.floating_lyrics_unknown_track)
        }
        artistView.text = state.artist
        if (artwork != null) {
            artworkView.setImageBitmap(artwork)
        } else {
            artworkView.setImageResource(R.drawable.ic_echo_monochrome)
        }
        playPauseButton.setImageResource(
            if (state.playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )
        playPauseButton.contentDescription = context.getString(
            if (state.playing) R.string.pause else R.string.play
        )
    }

    private var lastBackgroundAlpha = -1

    fun applySettings(next: FloatingLyricsOverlaySettings) {
        settings = next.normalized()
        if (lyricsView.textSize != settings.textSizeSp.toFloat()) {
            lyricsView.textSize = settings.textSizeSp.toFloat()
        }
        lyricsView.setTextColor(settings.textColorArgb)
        val alpha = FloatingLyricsOverlayWindowPolicy.backgroundAlpha(settings)
        if (alpha != lastBackgroundAlpha) {
            lastBackgroundAlpha = alpha
            compactSurface.background = surfaceBackground(alpha)
        }
        syncingControls = true
        opacitySlider.progress = settings.backgroundOpacityPercent
        opacitySlider.isEnabled = !settings.transparentBackground
        opacitySlider.alpha = if (settings.transparentBackground) 0.45f else 1f
        syncingControls = false
    }

    fun renderPresentation(
        next: FloatingLyricsPresentation.Visible,
        animate: Boolean = true
    ) {
        val previous = presentation
        presentation = next
        val expanded = next.mode == FloatingLyricsMode.Expanded &&
            next.interaction == FloatingLyricsInteraction.Interactive
        if (expanded) {
            val wasExpanded = previous.mode == FloatingLyricsMode.Expanded &&
                previous.interaction == FloatingLyricsInteraction.Interactive
            if (wasExpanded && controlsView.visibility == View.VISIBLE) return
            controlsView.visibility = View.VISIBLE
            if (animate && animationsEnabled()) {
                controlsView.alpha = 0f
                controlsView.animate().alpha(1f).setDuration(180L).start()
            } else {
                controlsView.alpha = 1f
            }
        } else {
            controlsView.animate().cancel()
            controlsView.visibility = View.GONE
            lockConfirmation.visibility = View.GONE
        }
    }

    private fun buildControls(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = surfaceBackground(235)
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
        }
        header.addView(artworkView, LinearLayout.LayoutParams(dp(40), dp(40)))
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        labels.addView(titleView)
        labels.addView(artistView)
        header.addView(
            labels,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        installMoveGesture(header)
        panel.addView(header)

        val playbackRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        }
        playbackRow.addView(
            imageButton(
                R.drawable.ic_notif_previous,
                R.string.previous
            ) { onAction(FloatingLyricsOverlayAction.Previous) },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        playbackRow.addView(
            playPauseButton,
            LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            }
        )
        playbackRow.addView(
            imageButton(
                R.drawable.ic_notif_next,
                R.string.next
            ) { onAction(FloatingLyricsOverlayAction.Next) },
            LinearLayout.LayoutParams(dp(48), dp(48))
        )
        panel.addView(playbackRow)

        panel.addView(TextView(context).apply {
            text = context.getString(R.string.floating_lyrics_opacity_quick)
            setTextColor(TEXT_MUTED)
            textSize = 12f
        })
        panel.addView(
            opacitySlider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        panel.addView(
            actionButton(R.string.floating_lyrics_enable_click_through) {
                lockConfirmation.visibility = View.VISIBLE
                onAction(FloatingLyricsOverlayAction.RequestClickThrough)
            }
        )
        panel.addView(lockConfirmation)

        val secondaryRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER
        }
        secondaryRow.addView(
            actionButton(R.string.floating_lyrics_hide_session) {
                onAction(FloatingLyricsOverlayAction.HideSession)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        secondaryRow.addView(
            actionButton(R.string.floating_lyrics_open_full_settings) {
                onAction(FloatingLyricsOverlayAction.OpenSettings)
            },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) }
        )
        panel.addView(secondaryRow)
        panel.addView(
            actionButton(R.string.floating_lyrics_collapse) {
                onAction(FloatingLyricsOverlayAction.ToggleExpanded)
            }
        )
        return panel
    }

    private fun buildLockConfirmation(): LinearLayout = LinearLayout(context).apply confirmation@ {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        setPadding(dp(10), dp(8), dp(10), dp(8))
        background = roundedBackground(0xFF222B36.toInt(), 10)
        addView(TextView(context).apply {
            text = context.getString(R.string.floating_lyrics_click_through_confirmation)
            setTextColor(TEXT_PRIMARY)
            textSize = 12f
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.END
            addView(
                actionButton(R.string.cancel) {
                    this@confirmation.visibility = View.GONE
                    onAction(FloatingLyricsOverlayAction.CancelClickThrough)
                },
                LinearLayout.LayoutParams(0, dp(48), 1f)
            )
            addView(
                actionButton(R.string.floating_lyrics_confirm_lock) {
                    this@confirmation.visibility = View.GONE
                    onAction(FloatingLyricsOverlayAction.ConfirmClickThrough)
                },
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) }
            )
        })
    }

    private fun installMoveGesture(target: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var lastRawX = 0f
        var lastRawY = 0f
        var totalX = 0f
        var totalY = 0f
        var dragging = false
        target.setOnTouchListener { view, event ->
            if (event.pointerCount != 1) {
                if (dragging) {
                    onAction(FloatingLyricsOverlayAction.DragFinished)
                }
                dragging = false
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    totalX = 0f
                    totalY = 0f
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - lastRawX
                    val deltaY = event.rawY - lastRawY
                    totalX += deltaX
                    totalY += deltaY
                    if (!dragging && (abs(totalX) > touchSlop || abs(totalY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        onAction(
                            FloatingLyricsOverlayAction.DragBy(
                                deltaX.roundToInt(),
                                deltaY.roundToInt()
                            )
                        )
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        onAction(FloatingLyricsOverlayAction.DragFinished)
                    } else {
                        view.performClick()
                        onAction(FloatingLyricsOverlayAction.ToggleExpanded)
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        onAction(FloatingLyricsOverlayAction.DragFinished)
                    }
                    dragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun imageButton(
        drawableRes: Int,
        descriptionRes: Int,
        accent: Boolean = false,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(drawableRes)
        setColorFilter(Color.WHITE)
        contentDescription = context.getString(descriptionRes)
        background = roundedBackground(if (accent) ACCENT else PANEL_SOFT, if (accent) 26 else 14)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

    private fun actionButton(
        labelRes: Int,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = context.getString(labelRes)
        setTextColor(TEXT_PRIMARY)
        textSize = 13f
        minHeight = dp(48)
        minimumHeight = dp(48)
        background = roundedBackground(PANEL_SOFT, 12)
        setOnClickListener { onClick() }
    }

    private fun surfaceBackground(alpha: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.argb(alpha, 20, 24, 31))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.argb(31, 255, 255, 255))
        }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun animationsEnabled(): Boolean =
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TEXT_PRIMARY = 0xFFF8FBFF.toInt()
        const val TEXT_MUTED = 0xFFA8B5C4.toInt()
        const val PANEL_SOFT = 0xFF1C222B.toInt()
        const val ACCENT = 0xFF2F6E91.toInt()
    }
}
