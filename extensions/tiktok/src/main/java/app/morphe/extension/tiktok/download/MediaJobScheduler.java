/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.download;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private MediaJobScheduler() {}

    static boolean submit(String label, Runnable work) {
        if (work == null) throw new NullPointerException("work");
        try {
            EXECUTOR.execute(() -> MediaBudget.runWithJobDeadline(work));
            return true;
        } catch (RejectedExecutionException error) {
            Logger.printException(() -> "Media job queue is full for " + label, error);
            Utils.showToastLong(L10n.t("Too many media saves are already running. Try again in a moment."));
            return false;
        }
    }

    static int queuedJobs() {
        return EXECUTOR.getQueue().size();
    }

    static int runningJobs() {
        return EXECUTOR.getActiveCount();
    }

}
