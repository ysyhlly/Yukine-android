package app.yukine.playback;

import android.os.Process;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

final class PlaybackTaskScheduler implements Executor {
    private static final String TAG = "PlaybackTaskScheduler";

    enum Priority {
        CURRENT_PLAYBACK_RECOVERY,
        CURRENT_URL_RESOLVE,
        NEXT_URL_RESOLVE,
        NEXT_TRACK_PRECACHE,
        CURRENT_WAVEFORM
    }

    private final PriorityBlockingQueue<ScheduledTask> queue = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Thread worker;
    private final int threadPriority;
    private final Runnable beforeTaskRun;
    private final ErrorSink errorSink;
    private volatile boolean running = true;

    PlaybackTaskScheduler(String threadName) {
        this(threadName, Process.THREAD_PRIORITY_BACKGROUND);
    }

    PlaybackTaskScheduler(String threadName, int priority) {
        this(threadName, priority, () -> {
        });
    }

    PlaybackTaskScheduler(String threadName, int priority, Runnable beforeTaskRun) {
        this(threadName, priority, beforeTaskRun, PlaybackTaskScheduler::logTaskFailure);
    }

    PlaybackTaskScheduler(String threadName, int priority, Runnable beforeTaskRun, ErrorSink errorSink) {
        threadPriority = priority;
        this.beforeTaskRun = beforeTaskRun == null ? () -> {
        } : beforeTaskRun;
        this.errorSink = errorSink == null ? PlaybackTaskScheduler::logTaskFailure : errorSink;
        worker = new Thread(this::runLoop, threadName);
        worker.setDaemon(true);
        worker.setPriority(javaPriority(priority));
        worker.start();
    }

    @Override
    public void execute(Runnable command) {
        schedule(Priority.NEXT_TRACK_PRECACHE, command);
    }

    void schedule(Priority priority, Runnable command) {
        if (command == null || !running) {
            return;
        }
        queue.offer(new ScheduledTask(priority, sequence.getAndIncrement(), command));
    }

    void shutdownNow() {
        running = false;
        queue.clear();
        worker.interrupt();
    }

    private void runLoop() {
        try {
            Process.setThreadPriority(threadPriority);
        } catch (RuntimeException ignored) {
            // Keep the scheduler alive even if a device rejects this priority.
        }
        while (running) {
            try {
                ScheduledTask task = queue.take();
                beforeTaskRun.run();
                if (!running) {
                    return;
                }
                try {
                    task.runnable.run();
                } catch (RuntimeException exception) {
                    errorSink.onTaskFailure(task.priority, exception);
                }
            } catch (InterruptedException ignored) {
                if (!running) {
                    return;
                }
            }
        }
    }

    private static void logTaskFailure(Priority priority, RuntimeException exception) {
        Log.w(TAG, "Playback task failed: " + priority, exception);
    }

    interface ErrorSink {
        void onTaskFailure(Priority priority, RuntimeException exception);
    }

    private static int javaPriority(int androidPriority) {
        return androidPriority <= Process.THREAD_PRIORITY_DISPLAY
                ? Thread.NORM_PRIORITY + 2
                : Thread.NORM_PRIORITY - 1;
    }

    private static final class ScheduledTask implements Comparable<ScheduledTask> {
        private final Priority priority;
        private final long sequence;
        private final Runnable runnable;

        private ScheduledTask(Priority priority, long sequence, Runnable runnable) {
            this.priority = priority;
            this.sequence = sequence;
            this.runnable = runnable;
        }

        @Override
        public int compareTo(ScheduledTask other) {
            int priorityCompare = Integer.compare(priority.ordinal(), other.priority.ordinal());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
