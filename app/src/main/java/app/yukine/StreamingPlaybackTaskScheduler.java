package app.yukine;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.ArrayDeque;
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

    private final PriorityQueue<ScheduledTask> criticalQueue = new PriorityQueue<>();
    private final Queue<ScheduledTask> nextResolveQueue = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private boolean criticalActive;
    private boolean nextResolveActive;

    synchronized void schedule(Priority priority, Task task) {
        if (task == null) {
            return;
        }
        ScheduledTask scheduledTask = new ScheduledTask(priority, sequence.getAndIncrement(), task);
        if (priority == Priority.NEXT_URL_RESOLVE) {
            nextResolveQueue.offer(scheduledTask);
            drainNextResolve();
        } else {
            criticalQueue.offer(scheduledTask);
            drainCritical();
        }
    }

    private synchronized void drainCritical() {
        if (criticalActive) {
            return;
        }
        ScheduledTask next = criticalQueue.poll();
        if (next == null) {
            return;
        }
        criticalActive = true;
        next.task.run(() -> {
            criticalActive = false;
            drainCritical();
        });
    }

    private synchronized void drainNextResolve() {
        if (nextResolveActive) {
            return;
        }
        ScheduledTask next = nextResolveQueue.poll();
        if (next == null) {
            return;
        }
        nextResolveActive = true;
        next.task.run(() -> {
            nextResolveActive = false;
            drainNextResolve();
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
