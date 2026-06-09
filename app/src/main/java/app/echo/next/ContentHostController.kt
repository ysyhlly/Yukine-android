package app.echo.next

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.echo.next.ui.EchoViewBackground

internal class ContentHostController(
    private val context: Context,
    private val host: FrameLayout,
    private val routeHost: View?
) {
    private var container: LinearLayout? = null
    private var scrollView: ScrollView? = null
    private var fixedMode = false
    private var pendingHorizontalTransition = 0

    fun useScrollingContainer(): LinearLayout {
        val currentScrollView = scrollView
        val currentContainer = container
        if (!fixedMode && currentScrollView != null && currentContainer != null) {
            clearContainer(currentContainer)
            return currentContainer
        }
        resetHost()
        fixedMode = false
        val nextScrollView = ScrollView(context).apply {
            isFillViewport = false
        }
        val nextContainer = createContainer()
        nextScrollView.addView(
            nextContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        host.addView(
            nextScrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        scrollView = nextScrollView
        container = nextContainer
        return nextContainer
    }

    fun useFixedContainer(existingChildren: List<View>): LinearLayout {
        val currentContainer = container
        val nextContainer = if (fixedMode && scrollView == null && currentContainer != null) {
            clearContainer(currentContainer)
            currentContainer
        } else {
            resetHost()
            fixedMode = true
            scrollView = null
            createContainer().also {
                host.addView(
                    it,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                container = it
            }
        }
        existingChildren.forEach { child ->
            nextContainer.addView(
                child,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return nextContainer
    }

    fun getScrollView(): ScrollView? = scrollView

    fun prepareHorizontalTransition(next: Boolean) {
        pendingHorizontalTransition = if (next) 1 else -1
    }

    fun animateContentIfPending(view: View) {
        val direction = pendingHorizontalTransition
        if (direction == 0) {
            resetAnimationState(view)
            return
        }
        pendingHorizontalTransition = 0
        val width = if (host.width > 0) host.width else context.resources.displayMetrics.widthPixels
        val startOffset = width * 0.14f * direction
        resetAnimationState(view)
        view.alpha = 0f
        view.translationX = startOffset
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        view.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(CONTENT_TRANSITION_MS)
            .setInterpolator(DecelerateInterpolator(1.55f))
            .withEndAction {
                view.setLayerType(View.LAYER_TYPE_NONE, null)
                resetAnimationState(view)
            }
            .start()
    }

    fun applyBackground() {
        EchoViewBackground.applyPageBackground(host)
        container?.let { EchoViewBackground.applyPageBackground(it) }
    }

    private fun clearContainer(target: LinearLayout) {
        for (index in 0 until target.childCount) {
            resetAnimationState(target.getChildAt(index))
        }
        target.removeAllViews()
        EchoViewBackground.applyPageBackground(target)
    }

    private fun resetHost() {
        for (index in 0 until host.childCount) {
            resetAnimationState(host.getChildAt(index))
        }
        host.removeAllViews()
        routeHost?.let {
            host.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                )
            )
        }
    }

    private fun createContainer(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            EchoViewBackground.applyPageBackground(this)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun resetAnimationState(view: View) {
        view.animate().cancel()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        view.alpha = 1f
        view.translationX = 0f
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    private companion object {
        const val CONTENT_TRANSITION_MS = 170L
    }
}
