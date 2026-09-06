package app.morphe.extension.tiktok.download;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded queue for every extension-owned media operation. */
final class MediaJobScheduler {
    static final int MAX_RUNNING_JOBS = 3;
    static final int MAX_QUEUED_JOBS = 8;

    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "Hushfeed-MediaJob");
        thread.setDaemon(true);
        return thread;
    };
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            MAX_RUNNING_JOBS,
            MAX_RUNNING_JOBS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_JOBS),
            THREAD_FACTORY,
            new ThreadPoolExecutor.AbortPolicy());
    private static final Map<TrackedTask, JobHandle> HANDLES = new ConcurrentHashMap<>();

    private MediaJobScheduler() {}

    static JobHandle submit(String label, Runnable work, Runnable onQueuedCancel) {
        if (work == null) throw new NullPointerException("work");
        TrackedTask task = new TrackedTask(label, work, onQueuedCancel);
        JobHandle handle = new JobHandle(task, label);
        HANDLES.put(task, handle);
        try {
            EXECUTOR.execute(task);
            return handle;
        } catch (RejectedExecutionException error) {
            HANDLES.remove(task);
            Logger.printException(() -> "Media job queue is full for " + label, error);
            Utils.showToastLong(L10n.t("Too many media saves are already running. Try again shortly."));
            return null;
        }
    }

    static int queuedJobs() {
        return EXECUTOR.getQueue().size();
    }

    static int runningJobs() {
        return EXECUTOR.getActiveCount();
    }

    static void cancelAll() {
        for (JobHandle handle : HANDLES.values()) handle.cancel();
    }

    static final class JobHandle {
        private final TrackedTask task;
        final String label;

        private JobHandle(TrackedTask task, String label) {
            this.task = task;
            this.label = label;
        }

        boolean cancel() {
            return task.cancel(true);
        }

        boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private static final class TrackedTask extends FutureTask<Void> {
        private final String label;
        private final Runnable onQueuedCancel;
        private final AtomicBoolean started;

        private TrackedTask(String label, Runnable work, Runnable onQueuedCancel) {
            this(label, work, onQueuedCancel, new AtomicBoolean());
        }

        private TrackedTask(String label, Runnable work, Runnable onQueuedCancel, AtomicBoolean started) {
            super(() -> {
                started.set(true);
                work.run();
                return null;
            });
            this.label = label;
            this.onQueuedCancel = onQueuedCancel;
            this.started = started;
        }

        @Override protected void done() {
            HANDLES.remove(this);
            if (isCancelled() && !started.get() && onQueuedCancel != null) {
                try {
                    onQueuedCancel.run();
                } catch (RuntimeException error) {
                    Logger.printException(() -> "Media job cancellation cleanup failed for " + label, error);
                }
            }
        }
    }
}
