package app.echo.next;

final class SwipeGesturePolicy {
    private SwipeGesturePolicy() {
    }

    static boolean horizontalDistanceDominates(float dx, float dy, int minDistance) {
        return Math.abs(dx) >= minDistance && Math.abs(dx) > Math.abs(dy) * 2.1f;
    }

    static boolean horizontalSwipeAccepted(
            float dx,
            int longDistance,
            float velocityX,
            float velocityY,
            int minVelocity
    ) {
        if (Math.abs(dx) >= longDistance) {
            return true;
        }
        return Math.abs(velocityX) >= minVelocity && Math.abs(velocityX) > Math.abs(velocityY) * 1.6f;
    }

    static boolean verticalDistanceDominates(float dx, float dy, int touchSlop) {
        return Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.15f;
    }
}
