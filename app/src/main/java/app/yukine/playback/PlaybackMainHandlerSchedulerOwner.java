package app.yukine.playback;

import android.os.Handler;

import app.yukine.playback.manager.PlaybackCrossfadeAdvanceManager;
import app.yukine.playback.manager.PlaybackErrorRecoveryManager;
import app.yukine.playback.manager.PlaybackProgressUpdateManager;
import app.yukine.playback.manager.PlaybackRecoveryScheduler;
import app.yukine.playback.manager.PlaybackSleepTimerManager;

final class PlaybackMainHandlerSchedulerOwner implements
        PlaybackSleepTimerManager.CallbackScheduler,
        PlaybackErrorRecoveryManager.RetryScheduler,
        PlaybackProgressUpdateManager.CallbackScheduler,
        PlaybackCrossfadeAdvanceManager.CallbackScheduler,
        PlaybackRecoveryScheduler.MainScheduler,
        PlaybackPrecacheManager.CallbackScheduler {
    interface MainHandlerCallbacks {
        void post(Runnable runnable);

        void postDelayed(Runnable runnable, long delayMs);

        void removeCallbacks(Runnable runnable);

        void removeCallbacksAndMessages(Object token);
    }

    private final MainHandlerCallbacks callbacks;

    PlaybackMainHandlerSchedulerOwner(Handler handler) {
        this(handler == null ? null : new AndroidMainHandlerCallbacks(handler));
    }

    PlaybackMainHandlerSchedulerOwner(MainHandlerCallbacks callbacks) {
        this.callbacks = callbacks == null ? new NoOpMainHandlerCallbacks() : callbacks;
    }

    @Override
    public void post(Runnable runnable) {
        callbacks.post(runnable);
    }

    @Override
    public void postDelayed(Runnable runnable, long delayMs) {
        callbacks.postDelayed(runnable, delayMs);
    }

    @Override
    public void removeCallbacks(Runnable runnable) {
        callbacks.removeCallbacks(runnable);
    }

    void clearCallbacks() {
        callbacks.removeCallbacksAndMessages(null);
    }

    private static final class AndroidMainHandlerCallbacks implements MainHandlerCallbacks {
        private final Handler handler;

        private AndroidMainHandlerCallbacks(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void post(Runnable runnable) {
            handler.post(runnable);
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            handler.postDelayed(runnable, delayMs);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            handler.removeCallbacks(runnable);
        }

        @Override
        public void removeCallbacksAndMessages(Object token) {
            handler.removeCallbacksAndMessages(token);
        }
    }

    private static final class NoOpMainHandlerCallbacks implements MainHandlerCallbacks {
        @Override
        public void post(Runnable runnable) {
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
        }

        @Override
        public void removeCallbacksAndMessages(Object token) {
        }
    }
}
