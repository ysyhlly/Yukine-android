package app.yukine;

import android.util.Log;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

final class StreamingPlaybackTaskScheduler implements StreamingPlaybackTaskQueue {
    private static final String TAG = "StreamingPlaybackTask";

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

    interface ErrorSink {
        void onTaskFailure(Priority priority, RuntimeException exception);
    }

    private final ErrorSink errorSink;

    StreamingPlaybackTaskScheduler() {
        this(StreamingPlaybackTaskScheduler::logTaskFailure);
    }

    StreamingPlaybackTaskScheduler(ErrorSink errorSink) {
        this.errorSink = errorSink == null ? StreamingPlaybackTaskScheduler::logTaskFailure : errorSink;
    }

    @Override
    public void scheduleCurrentPlaybackRecovery(StreamingPlaybackTask task) {
        schedule(Priority.CURRENT_PLAYBACK_RECOVERY, taskFrom(task));
    }

    @Override
    public void scheduleCurrentUrlResolve(StreamingPlaybackTask task) {
        schedule(Priority.CURRENT_URL_RESOLVE, taskFrom(task));
    }

    @Override
    public void scheduleNextUrlResolve(StreamingPlaybackTask task) {
        schedule(Priority.NEXT_URL_RESOLVE, taskFrom(task));
    }

    private Task taskFrom(StreamingPlaybackTask task) {
        if (task == null) {
            return null;
        }
        return completion -> task.run(completion::complete);
    }

    private final PriorityQueue<ScheduledTask> criticalQueue = new PriorityQueue<>();
    private final Queue<ScheduledTask> nextResolveQueue = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong();
    private boolean criticalActive;
    private boolean nextResolveActive;
    private boolean shutdown;

    synchronized void schedule(Priority priority, Task task) {
        if (shutdown || task == null) {
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

    synchronized void shutdownNow() {
        shutdown = true;
        criticalQueue.clear();
        nextResolveQueue.clear();
        criticalActive = false;
        nextResolveActive = false;
    }

    private synchronized void drainCritical() {
        if (shutdown || criticalActive) {
            return;
        }
        ScheduledTask next = criticalQueue.poll();
        if (next == null) {
            return;
        }
        criticalActive = true;
        try {
            next.task.run(() -> {
                synchronized (StreamingPlaybackTaskScheduler.this) {
                    completeCritical();
                }
            });
        } catch (RuntimeException exception) {
            errorSink.onTaskFailure(next.priority, exception);
            synchronized (StreamingPlaybackTaskScheduler.this) {
                completeCritical();
            }
        }
    }

    private synchronized void drainNextResolve() {
        if (shutdown || nextResolveActive) {
            return;
        }
        ScheduledTask next = nextResolveQueue.poll();
        if (next == null) {
            return;
        }
        nextResolveActive = true;
        try {
            next.task.run(() -> {
                synchronized (StreamingPlaybackTaskScheduler.this) {
                    completeNextResolve();
                }
            });
        } catch (RuntimeException exception) {
            errorSink.onTaskFailure(next.priority, exception);
            synchronized (StreamingPlaybackTaskScheduler.this) {
                completeNextResolve();
            }
        }
    }

    private void completeCritical() {
        if (shutdown) {
            return;
        }
        criticalActive = false;
        drainCritical();
    }

    private void completeNextResolve() {
        if (shutdown) {
            return;
        }
        nextResolveActive = false;
        drainNextResolve();
    }

    private static void logTaskFailure(Priority priority, RuntimeException exception) {
        Log.w(TAG, "Streaming playback task failed: " + priority, exception);
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
