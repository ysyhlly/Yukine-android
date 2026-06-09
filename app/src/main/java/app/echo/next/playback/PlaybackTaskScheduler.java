package app.echo.next.playback;

import java.util.concurrent.Executor;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

final class PlaybackTaskScheduler implements Executor {
    enum Priority {
        CURRENT_PLAYBACK_RECOVERY,
        CURRENT_WAVEFORM,
        CURRENT_URL_RESOLVE,
        NEXT_URL_RESOLVE,
        NEXT_TRACK_PRECACHE
    }

    private final PriorityBlockingQueue<ScheduledTask> queue = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Thread worker;
    private volatile boolean running = true;

    PlaybackTaskScheduler(String threadName) {
        worker = new Thread(this::runLoop, threadName);
        worker.setDaemon(true);
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
        while (running) {
            try {
                queue.take().runnable.run();
            } catch (InterruptedException ignored) {
                if (!running) {
                    return;
                }
            }
        }
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
