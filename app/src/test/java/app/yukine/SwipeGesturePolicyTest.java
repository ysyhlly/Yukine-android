package app.yukine;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SwipeGesturePolicyTest {
    @Test
    public void horizontalDistanceMustDominateVerticalDrift() {
        assertTrue(SwipeGesturePolicy.horizontalDistanceDominates(120f, 20f, 88));
        assertFalse(SwipeGesturePolicy.horizontalDistanceDominates(120f, 80f, 88));
        assertFalse(SwipeGesturePolicy.horizontalDistanceDominates(60f, 0f, 88));
    }

    @Test
    public void longHorizontalSwipeDoesNotRequireFlingVelocity() {
        assertTrue(SwipeGesturePolicy.horizontalSwipeAccepted(160f, 150, 0f, 0f, 900));
        assertTrue(SwipeGesturePolicy.horizontalSwipeAccepted(100f, 150, 1000f, 120f, 900));
        assertFalse(SwipeGesturePolicy.horizontalSwipeAccepted(100f, 150, 400f, 80f, 900));
        assertFalse(SwipeGesturePolicy.horizontalSwipeAccepted(100f, 150, 1000f, 800f, 900));
    }

    @Test
    public void verticalDistanceMustDominateBeforeItLocksScrollDirection() {
        assertTrue(SwipeGesturePolicy.verticalDistanceDominates(20f, 80f, 8));
        assertFalse(SwipeGesturePolicy.verticalDistanceDominates(80f, 50f, 8));
        assertFalse(SwipeGesturePolicy.verticalDistanceDominates(20f, 6f, 8));
    }
}
