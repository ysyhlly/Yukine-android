package app.echo.next;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

final class StreamingPlaybackTaskScheduler {
    enum Priority {
        CURRENT_PLAYBACK_RECOVERY,
        CURRENT_URL_RESOLVE,
        NEXT_URL_RESOLVE
    }

    interface Task {
        void run(Completion completion);
    }

    interface Completion {
        void complete();
    }

    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private boolean active;

    void schedule(Priority priority, Task task) {
        if (task == null) {
            return;
        }
        queue.offer(new ScheduledTask(priority, sequence.getAndIncrement(), task));
        drain();
    }

    private void drain() {
        if (active) {
            return;
        }
        ScheduledTask next = queue.poll();
        if (next == null) {
            return;
        }
        active = true;
        next.task.run(() -> {
            active = false;
            drain();
        });
    }

    private static final class ScheduledTask implements Comparable<ScheduledTask> {
        private final Priority priority;
        private final long sequence;
        private final Task task;

        private ScheduledTask(Priority priority, long sequence, Task task) {
            this.priority = priority;
            this.sequence = sequence;
            this.task = task;
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
