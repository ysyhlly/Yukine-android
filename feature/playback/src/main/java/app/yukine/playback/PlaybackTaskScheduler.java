package app.yukine.playback;

import android.os.Process;

import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

final class PlaybackTaskScheduler implements Executor {
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
    private volatile boolean running = true;

    PlaybackTaskScheduler(String threadName) {
        this(threadName, Process.THREAD_PRIORITY_BACKGROUND);
    }

    PlaybackTaskScheduler(String threadName, int priority) {
        this(threadName, priority, () -> {
        });
    }

    PlaybackTaskScheduler(String threadName, int priority, Runnable beforeTaskRun) {
        threadPriority = priority;
        this.beforeTaskRun = beforeTaskRun == null ? () -> {
        } : beforeTaskRun;
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
                Runnable runnable = queue.take().runnable;
                beforeTaskRun.run();
                if (!running) {
                    return;
                }
                try {
                    runnable.run();
                } catch (RuntimeException ignored) {
                    // Keep later playback tasks alive when one cache/resolve task fails.
                }
            } catch (InterruptedException ignored) {
                if (!running) {
                    return;
                }
            }
        }
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
