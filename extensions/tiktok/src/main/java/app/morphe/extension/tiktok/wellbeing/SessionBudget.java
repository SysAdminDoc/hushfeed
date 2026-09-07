/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * How much of the feed has gone by today, and whether it is time to stop.
 *
 * <p>Two numbers are kept: how many different videos have come up, and how long the player has
 * been running. Either can carry the budget, both can be off, and off is the default. Reaching
 * the budget says so once and, if a lock time was set, holds the feed behind an overlay until it
 * runs out. Nothing here touches the feed's own data, so TikTok never learns that anything
 * happened and never refetches a batch it already has.
 *
 * <p>The day ends at an hour of the reader's choosing rather than at midnight, because someone
 * still scrolling at one in the morning is having last night, not this morning. Both counts and
 * any running lock reset when that hour passes.
 *
 * <p>Everything survives the process being killed: the state is a single line in the settings
 * store, rewritten whenever it changes.
 */
public final class SessionBudget {
    /**
     * The most one player callback may add to the watched time. The callbacks arrive several
     * times a second while a video plays and stop when it does not, so a long gap means the app
     * was away rather than that someone watched for an hour.
     */
    static final long MAX_TICK_MS = 5_000L;

    /** Reading and writing the record is not atomic on its own, so every path holds this. */
    private static final Object LOCK = new Object();

    private static Clock clock = System::currentTimeMillis;

    private static boolean loaded;
    private static long day;
    private static int videos;
    private static long watchedMs;
    private static long lockUntilMs;
    private static long lastTickMs;
    private static String lastCountedId;
    private static boolean noticeShown;

    /** So a test can move time without waiting for it. */
    interface Clock {
        long now();
    }

    private SessionBudget() {
    }

    // ---------------------------------------------------------------- what the feed reports

    /**
     * A different video is on screen. Called from the point that decides which of the bound
     * items is the current one, so a prefetched neighbour does not count and a video revisited
     * without anything in between does not count twice.
     */
    public static void noteVideo(String awemeId) {
        if (awemeId == null || awemeId.isEmpty()) return;
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            if (awemeId.equals(lastCountedId)) return;
            lastCountedId = awemeId;
            videos++;
            save();
        }
    }

    /**
     * The player reported progress. Adds the time since the previous report, which is the only
     * measure of watching available without a clock of our own running in the background.
     */
    public static void noteWatching() {
        synchronized (LOCK) {
            load();
            long now = clock.now();
            rollOver(now);
            long since = lastTickMs == 0 ? 0 : now - lastTickMs;
            lastTickMs = now;
            if (since <= 0 || since > MAX_TICK_MS) return;
            watchedMs += since;
            save();
        }
    }

    // ---------------------------------------------------------------------- what it adds up to

    public static int videosSeen() {
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            return videos;
        }
    }

    public static long watchedMs() {
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            return watchedMs;
        }
    }

    /** True once either budget that is switched on has been used up. */
    public static boolean reachedLimit() {
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            return spent();
        }
    }

    private static boolean spent() {
        int videoBudget = Settings.SESSION_BUDGET_VIDEOS.get();
        if (videoBudget > 0 && videos >= videoBudget) return true;
        int minuteBudget = Settings.SESSION_BUDGET_MINUTES.get();
        return minuteBudget > 0 && watchedMs >= minuteBudget * 60_000L;
    }

    /**
     * True the first time the budget runs out, so the notice is shown once rather than on every
     * video after it. A new day, or a change of budget that puts the reader back under it, arms
     * it again.
     */
    public static boolean claimNotice() {
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            if (!spent()) {
                if (noticeShown) {
                    noticeShown = false;
                    save();
                }
                return false;
            }
            if (noticeShown) return false;
            noticeShown = true;
            startLock();
            save();
            return true;
        }
    }

    private static void startLock() {
        int lockMinutes = Settings.SESSION_BUDGET_LOCK_MINUTES.get();
        if (lockMinutes <= 0) return;
        long until = clock.now() + lockMinutes * 60_000L;
        if (until > lockUntilMs) lockUntilMs = until;
    }

    // ------------------------------------------------------------------------------- the lock

    public static boolean isLocked() {
        return lockRemainingMs() > 0;
    }

    /** How much of the lock is left, or zero when there is none. */
    public static long lockRemainingMs() {
        synchronized (LOCK) {
            load();
            long now = clock.now();
            rollOver(now);
            if (lockUntilMs <= now) {
                if (lockUntilMs != 0) {
                    lockUntilMs = 0;
                    save();
                }
                return 0;
            }
            return lockUntilMs - now;
        }
    }

    /**
     * Lifts a running hold without touching the counts. The budget stays reached, so the notice
     * does not come back until a new day or a raised budget puts the reader under it again.
     */
    public static void releaseLock() {
        synchronized (LOCK) {
            load();
            if (lockUntilMs == 0) return;
            lockUntilMs = 0;
            save();
        }
    }

    /** Ends the lock and clears both counts, which is what the settings screen's reset does. */
    public static void clear() {
        synchronized (LOCK) {
            load();
            day = dayOf(clock.now());
            videos = 0;
            watchedMs = 0;
            lockUntilMs = 0;
            lastCountedId = null;
            noticeShown = false;
            save();
        }
    }

    // -------------------------------------------------------------------------------- the day

    /**
     * Which day a moment belongs to, counting the day as starting at the chosen hour. The
     * device's own zone is what the reader lives in, so that is the one used.
     */
    static long dayOf(long now) {
        int resetHour = Settings.SESSION_BUDGET_RESET_HOUR.get();
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(now);
        if (calendar.get(Calendar.HOUR_OF_DAY) < resetHour) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private static void rollOver(long now) {
        long today = dayOf(now);
        if (today == day) return;
        day = today;
        videos = 0;
        watchedMs = 0;
        lockUntilMs = 0;
        lastCountedId = null;
        noticeShown = false;
        lastTickMs = 0;
        save();
    }

    // ------------------------------------------------------------------------- staying around

    private static void load() {
        if (loaded) return;
        loaded = true;
        String stored = Settings.SESSION_BUDGET_STATE.get();
        day = dayOf(clock.now());
        try {
            String[] parts = stored.split("\\|", -1);
            if (parts.length >= 5) {
                long storedDay = Long.parseLong(parts[0]);
                if (storedDay == day) {
                    videos = Integer.parseInt(parts[1]);
                    watchedMs = Long.parseLong(parts[2]);
                    lockUntilMs = Long.parseLong(parts[3]);
                    noticeShown = "1".equals(parts[4]);
                }
            }
        } catch (RuntimeException malformed) {
            Logger.printDebug(() -> "Discarded an unreadable session budget record");
        }
    }

    private static void save() {
        Settings.SESSION_BUDGET_STATE.save(day + "|" + videos + "|" + watchedMs + "|"
                + lockUntilMs + "|" + (noticeShown ? "1" : "0"));
    }

    // ------------------------------------------------------------------------------- for tests

    static void setClockForTests(Clock replacement) {
        synchronized (LOCK) {
            clock = replacement == null ? System::currentTimeMillis : replacement;
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            loaded = false;
            day = 0;
            videos = 0;
            watchedMs = 0;
            lockUntilMs = 0;
            lastTickMs = 0;
            lastCountedId = null;
            noticeShown = false;
        }
    }
}
