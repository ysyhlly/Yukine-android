package app.echo.next;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * A content host that observes vertical drag direction without intercepting or
 * consuming touch events. Children (Compose LazyColumns, native ScrollViews,
 * etc.) keep handling scrolling normally; this layout only reports whether the
 * user is dragging up or down so the search bar can collapse/reveal.
 *
 * Touch events are inspected in {@link #dispatchTouchEvent(MotionEvent)} and
 * always forwarded to super, so scrolling behaviour is unchanged.
 */
final class ScrollDirectionFrameLayout extends FrameLayout {

    interface OnScrollDirectionListener {
        /** Called when a sustained drag direction is detected. */
        void onScrollDirection(boolean scrollingDown);
    }

    interface OnHorizontalSwipeListener {
        /** Called when a completed horizontal swipe is detected. */
        void onHorizontalSwipe(boolean swipingLeft);
    }

    interface OnTapListener {
        void onTap();
    }

    private final int touchSlop;
    private final int minHorizontalSwipeDistance;
    private final int longHorizontalSwipeDistance;
    private final int minHorizontalFlingVelocity;
    private float downX;
    private float downY;
    private float lastY;
    private Boolean lastDirectionDown;
    private boolean tracking;
    private boolean horizontalSwipeLocked;
    private boolean horizontalSwipeLeft;
    private boolean verticalScrollLocked;
    private boolean horizontalSwipeDispatched;
    private boolean movedBeyondTapSlop;
    private VelocityTracker velocityTracker;
    private OnScrollDirectionListener listener;
    private OnHorizontalSwipeListener horizontalSwipeListener;
    private OnTapListener tapListener;

    ScrollDirectionFrameLayout(Context context) {
        super(context);
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minHorizontalSwipeDistance = Math.round(88f * context.getResources().getDisplayMetrics().density);
        longHorizontalSwipeDistance = Math.round(150f * context.getResources().getDisplayMetrics().density);
        minHorizontalFlingVelocity = Math.max(configuration.getScaledMinimumFlingVelocity() * 3, 900);
    }

    void setOnScrollDirectionListener(OnScrollDirectionListener listener) {
        this.listener = listener;
    }

    void setOnHorizontalSwipeListener(OnHorizontalSwipeListener listener) {
        this.horizontalSwipeListener = listener;
    }

    void setOnTapListener(OnTapListener listener) {
        this.tapListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                downX = ev.getRawX();
                downY = ev.getRawY();
                lastY = downY;
                lastDirectionDown = null;
                tracking = true;
                horizontalSwipeLocked = false;
                horizontalSwipeLeft = false;
                verticalScrollLocked = false;
                horizontalSwipeDispatched = false;
                movedBeyondTapSlop = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                if (tracking) {
                    float dxFromDown = ev.getRawX() - downX;
                    float dyFromDown = ev.getRawY() - downY;
                    if (Math.abs(dxFromDown) > touchSlop || Math.abs(dyFromDown) > touchSlop) {
                        movedBeyondTapSlop = true;
                    }
                    lockHorizontalSwipeIfNeeded(dxFromDown, dyFromDown);
                }
                if (tracking && !horizontalSwipeLocked && !verticalScrollLocked && listener != null) {
                    float currentY = ev.getRawY();
                    float dy = currentY - lastY;
                    float dxFromDown = ev.getRawX() - downX;
                    float dyFromDown = currentY - downY;
                    if (Math.abs(dy) > touchSlop
                            && SwipeGesturePolicy.verticalDistanceDominates(dxFromDown, dyFromDown, touchSlop)) {
                        // Finger moving up (dy < 0) => content scrolls down => collapse.
                        // Finger moving down (dy > 0) => content scrolls up => reveal.
                        boolean directionDown = dy < 0f;
                        if (lastDirectionDown == null || lastDirectionDown != directionDown) {
                            listener.onScrollDirection(directionDown);
                            lastDirectionDown = directionDown;
                        }
                        lastY = currentY;
                        verticalScrollLocked = true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                lockHorizontalSwipeIfNeeded(ev.getRawX() - downX, ev.getRawY() - downY);
                if (horizontalSwipeLocked && !horizontalSwipeDispatched && horizontalSwipeListener != null) {
                    horizontalSwipeDispatched = true;
                    horizontalSwipeListener.onHorizontalSwipe(horizontalSwipeLeft);
                }
                if (!movedBeyondTapSlop && tapListener != null) {
                    tapListener.onTap();
                }
                // fall through
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                horizontalSwipeLocked = false;
                horizontalSwipeLeft = false;
                verticalScrollLocked = false;
                horizontalSwipeDispatched = false;
                lastDirectionDown = null;
                recycleVelocityTracker();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void lockHorizontalSwipeIfNeeded(float dxFromDown, float dyFromDown) {
        if (
                !verticalScrollLocked &&
                !horizontalSwipeLocked &&
                !horizontalSwipeDispatched &&
                SwipeGesturePolicy.horizontalDistanceDominates(dxFromDown, dyFromDown, minHorizontalSwipeDistance) &&
                horizontalSwipeAccepted(dxFromDown)
        ) {
            horizontalSwipeLocked = true;
            horizontalSwipeLeft = dxFromDown < 0f;
        }
    }

    private boolean horizontalSwipeAccepted(float dxFromDown) {
        if (Math.abs(dxFromDown) >= longHorizontalSwipeDistance) {
            return true;
        }
        if (velocityTracker == null) {
            return true;
        }
        velocityTracker.computeCurrentVelocity(1000);
        float vx = velocityTracker.getXVelocity();
        float vy = velocityTracker.getYVelocity();
        return SwipeGesturePolicy.horizontalSwipeAccepted(
                dxFromDown,
                longHorizontalSwipeDistance,
                vx,
                vy,
                minHorizontalFlingVelocity
        );
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
