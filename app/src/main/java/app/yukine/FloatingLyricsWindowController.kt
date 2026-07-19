package app.yukine

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

internal class FloatingLyricsWindowController(
    private val context: Context,
    private val onFailure: (Throwable) -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var settings = FloatingLyricsOverlaySettings()
    private var interaction = FloatingLyricsInteraction.Interactive
    private val safeMarginPx = dp(8)

    val attached: Boolean
        get() = root != null && layoutParams != null

    fun show(
        view: View,
        nextSettings: FloatingLyricsOverlaySettings,
        nextInteraction: FloatingLyricsInteraction
    ): Boolean {
        if (!canDrawOverlays()) return false
        settings = nextSettings.normalized()
        interaction = nextInteraction
        if (attached) {
            update(settings, interaction)
            return true
        }
        val params = createLayoutParams(settings, interaction)
        return try {
            windowManager?.addView(view, params)
                ?: return false
            root = view
            layoutParams = params
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                clampAndUpdate()
            }
            view.post(::clampAndUpdate)
            true
        } catch (error: RuntimeException) {
            reportFailure("Unable to attach floating lyrics window", error)
            clearReferences()
            false
        }
    }

    fun update(
        nextSettings: FloatingLyricsOverlaySettings = settings,
        nextInteraction: FloatingLyricsInteraction = interaction
    ): Boolean {
        settings = nextSettings.normalized()
        interaction = nextInteraction
        val params = layoutParams ?: return false
        params.width = overlayWidth(settings.widthPercent)
        params.flags = FloatingLyricsOverlayWindowPolicy.windowFlags(interaction)
        params.alpha = FloatingLyricsOverlayWindowPolicy.windowAlpha(
            interaction,
            FloatingLyricsOverlayWindowPolicy.maximumObscuringOpacity(context)
        )
        applyPositionFractions(params, settings)
        return updateLayout()
    }

    fun moveBy(deltaX: Int, deltaY: Int): Boolean {
        val params = layoutParams ?: return false
        params.x += deltaX
        params.y += deltaY
        clamp(params)
        return updateLayout()
    }

    fun currentPositionFractions(): Pair<Float, Float> {
        val params = layoutParams ?: return settings.positionXFraction to settings.positionYFraction
        val bounds = displayBounds()
        val view = root
        val viewWidth = params.width.coerceAtLeast(view?.width ?: dp(220))
        val viewHeight = (view?.height ?: dp(48)).coerceAtLeast(dp(48))
        val maxX = (bounds.width() - viewWidth - safeMarginPx * 2).coerceAtLeast(0)
        val maxY = (bounds.height() - viewHeight - safeMarginPx * 2).coerceAtLeast(0)
        val minX = bounds.left + safeMarginPx
        val minY = bounds.top + safeMarginPx
        val x = if (maxX == 0) 0.5f else ((params.x - minX).toFloat() / maxX)
        val y = if (maxY == 0) 0.08f else ((params.y - minY).toFloat() / maxY)
        return x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
    }

    fun refreshBounds() {
        val params = layoutParams ?: return
        params.width = overlayWidth(settings.widthPercent)
        applyPositionFractions(params, settings)
        clampAndUpdate()
    }

    fun remove() {
        val view = root
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Unable to remove floating lyrics window", error)
            }
        }
        clearReferences()
    }

    private fun createLayoutParams(
        settings: FloatingLyricsOverlaySettings,
        interaction: FloatingLyricsInteraction
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            overlayWidth(settings.widthPercent),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            FloatingLyricsOverlayWindowPolicy.windowFlags(interaction),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = FloatingLyricsOverlayWindowPolicy.windowAlpha(
                interaction,
                FloatingLyricsOverlayWindowPolicy.maximumObscuringOpacity(context)
            )
            applyPositionFractions(this, settings)
        }
    }

    private fun overlayWidth(widthPercent: Int): Int {
        val safeWidth = (displayBounds().width() - safeMarginPx * 2).coerceAtLeast(1)
        val desired = safeWidth * widthPercent.coerceIn(40, 100) / 100
        return desired.coerceIn(dp(220).coerceAtMost(safeWidth), safeWidth)
    }

    private fun applyPositionFractions(
        params: WindowManager.LayoutParams,
        settings: FloatingLyricsOverlaySettings
    ) {
        val bounds = displayBounds()
        val viewHeight = (root?.height ?: dp(48)).coerceAtLeast(dp(48))
        val maxX = (bounds.width() - params.width - safeMarginPx * 2).coerceAtLeast(0)
        val maxY = (bounds.height() - viewHeight - safeMarginPx * 2).coerceAtLeast(0)
        params.x = bounds.left + safeMarginPx +
            (settings.positionXFraction * maxX).roundToInt()
        params.y = bounds.top + safeMarginPx +
            (settings.positionYFraction * maxY).roundToInt()
    }

    private fun clampAndUpdate() {
        val params = layoutParams ?: return
        clamp(params)
        updateLayout()
    }

    private fun clamp(params: WindowManager.LayoutParams) {
        val bounds = displayBounds()
        val viewWidth = params.width.coerceAtLeast(root?.width ?: dp(220))
        val viewHeight = (root?.height ?: dp(48)).coerceAtLeast(dp(48))
        val minX = bounds.left + safeMarginPx
        val minY = bounds.top + safeMarginPx
        val maxX = (bounds.right - viewWidth - safeMarginPx).coerceAtLeast(minX)
        val maxY = (bounds.bottom - viewHeight - safeMarginPx).coerceAtLeast(minY)
        params.x = params.x.coerceIn(minX, maxX)
        params.y = params.y.coerceIn(minY, maxY)
    }

    private fun updateLayout(): Boolean {
        if (!canDrawOverlays()) {
            val error = SecurityException("Overlay permission is not granted")
            reportFailure("Floating lyrics permission was revoked", error)
            remove()
            return false
        }
        val view = root ?: return false
        val params = layoutParams ?: return false
        return try {
            windowManager?.updateViewLayout(view, params)
            true
        } catch (error: RuntimeException) {
            reportFailure("Unable to update floating lyrics window", error)
            remove()
            false
        }
    }

    private fun displayBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager?.currentWindowMetrics ?: return legacyBounds()
            val bounds = Rect(metrics.bounds)
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            bounds.left += insets.left
            bounds.top += insets.top
            bounds.right -= insets.right
            bounds.bottom -= insets.bottom
            return bounds.takeIf { it.width() > 0 && it.height() > 0 } ?: legacyBounds()
        }
        return legacyBounds()
    }

    private fun legacyBounds(): Rect {
        val metrics = context.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun reportFailure(message: String, error: Throwable) {
        Log.w(TAG, message, error)
        onFailure(error)
    }

    private fun clearReferences() {
        root = null
        layoutParams = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val TAG = "FloatingLyricsWindow"
    }
}
