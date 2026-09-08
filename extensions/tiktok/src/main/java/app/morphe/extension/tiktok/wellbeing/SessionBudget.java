/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * How much of the feed has gone by today, and whether it is time to stop.
 *
 * <p>Two numbers are kept: how many different videos have come up, and how long the player has
 * been running. Either can carry the budget, both can be off, and off is the default. Reaching
 * the budget says so once and, if a hold was set, covers the feed until it runs out. Nothing
 * here touches the feed's own data, so TikTok never learns that anything happened and never
 * refetches a batch it already has.
 *
 * <p>The day ends at an hour of the reader's choosing rather than at midnight, because someone
 * still scrolling at one in the morning is having last night. The day only ever moves forward:
 * changing that hour, or crossing a timezone, must not hand back a budget that has been spent.
 *
 * <p>Everything survives the process being killed. What it must not do is cost anything to keep:
 * the player reports progress several times a second, the settings store commits synchronously,
 * and a record written on every report would be thousands of blocking writes an hour. So nothing
 * is counted at all until a budget is set, the record is written on a background thread, and the
 * watched time is only committed when it has moved by a meaningful amount.
 */
public final class SessionBudget {
    /**
     * The most one player callback may add to the watched time. The callbacks arrive several
     * times a second while a video plays and stop when it does not, so a long gap means the app
     * was away rather than that someone watched for an hour.
     */
    static final long MAX_TICK_MS = 5_000L;

    /** Watched time is committed at this granularity; between writes it is only in memory. */
    static final long WRITE_EVERY_MS = 30_000L;

    /** Reading and writing the record is not atomic on its own, so every path holds this. */
    private static final Object LOCK = new Object();

    /**
     * The settings store commits synchronously, and the caller here is the player's own progress
     * callback. One thread, so writes stay ordered, and never the caller's.
     */
    private static final ExecutorService WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "hushfeed-session-budget");
                thread.setDaemon(true);
                return thread;
            });

    private static Clock clock = System::currentTimeMillis;

    /**
     * Read without the monitor by {@link #isLocked()}, which runs from the player's progress
     * callback several times a second. Volatile rather than guarded because the fast path only
     * ever answers "no hold", and it answers that from the same field every writer publishes.
     */
    private static volatile boolean loaded;
    private static long day;
    private static int videos;
    private static long watchedMs;
    private static long writtenWatchedMs;
    private static volatile long lockUntilMs;
    private static long lastTickMs;
    private static String lastCountedId;
    private static boolean noticeShown;

    /**
     * The day {@link #dayOf(long)} last worked out and the span of time that answer holds for.
     * Building a Calendar and a TimeZone clone on every player callback was the whole cost of
     * the budget once one was set. Guarded by {@link #LOCK} like everything else here.
     */
    private static long cachedDay;
    private static long cachedWindowStart;
    private static long cachedWindowEnd;
    private static int cachedResetHour = -1;
    private static String cachedZoneId = "";
    private static int dayComputations;
    private static int lockChecksUnderTheMonitor;

    /** So a test can move time without waiting for it. */
    interface Clock {
        long now();
    }

    private SessionBudget() {
    }

    /**
     * Whether anything is being counted at all. With no budget set and no hold running there is
     * nothing to count and nothing to write, and this is checked before any lock is taken so the
     * off case costs one settings read.
     */
    private static boolean counting() {
        return Settings.SESSION_BUDGET_VIDEOS.get() > 0
                || Settings.SESSION_BUDGET_MINUTES.get() > 0;
    }

    // ---------------------------------------------------------------- what the feed reports

    /**
     * A different video is on screen. Called from the point that decides which of the bound
     * items is the current one, so a prefetched neighbour does not count and a video revisited
     * without anything in between does not count twice.
     */
    public static void noteVideo(String awemeId) {
        if (awemeId == null || awemeId.isEmpty() || !counting()) return;
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
     *
     * <p>Nothing is added while a hold is running: the feed is behind the overlay, and charging
     * someone for a video they cannot see would empty tomorrow's budget as well as today's.
     */
    public static void noteWatching() {
        if (!counting()) return;
        synchronized (LOCK) {
            load();
            long now = clock.now();
            rollOver(now);
            long since = lastTickMs == 0 ? 0 : now - lastTickMs;
            lastTickMs = now;
            if (since <= 0 || since > MAX_TICK_MS) return;
            if (lockUntilMs > now) return;
            watchedMs += since;
            // Committed in steps: a record on every callback is a blocking write several times
            // a second, and losing at most half a minute to a kill is a fair trade for that.
            if (watchedMs - writtenWatchedMs >= WRITE_EVERY_MS) save();
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
     * video after it. A new day, or a raised budget that puts the reader back under it, arms it
     * again and lifts any hold that was running: a hold for a budget nobody is over any more is
     * a screen covered for no reason anyone can see.
     */
    public static boolean claimNotice() {
        synchronized (LOCK) {
            load();
            rollOver(clock.now());
            if (!spent()) {
                if (noticeShown || lockUntilMs != 0) {
                    noticeShown = false;
                    lockUntilMs = 0;
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

    // ------------------------------------------------------------------------------- the hold

    public static boolean isLocked() {
        // SessionLockOverlay.ensureRunning reaches this from the player's progress callback, so
        // the answer almost everyone gets, that no hold has ever been set, must not take the
        // monitor or build a Calendar. Only "no hold" is answered here, and it is answered from
        // the field every writer publishes, so a hold is never missed.
        if (loaded && lockUntilMs == 0) return false;
        return lockRemainingMs() > 0;
    }

    /** How much of the hold is left, or zero when there is none. */
    public static long lockRemainingMs() {
        synchronized (LOCK) {
            lockChecksUnderTheMonitor++;
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

    /** Ends the hold and clears both counts. */
    public static void clear() {
        synchronized (LOCK) {
            load();
            day = dayOf(clock.now());
            videos = 0;
            watchedMs = 0;
            writtenWatchedMs = 0;
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
        TimeZone zone = TimeZone.getDefault();
        synchronized (LOCK) {
            if (resetHour == cachedResetHour && zone.getID().equals(cachedZoneId)
                    && now >= cachedWindowStart && now < cachedWindowEnd) {
                return cachedDay;
            }
            dayComputations++;
            Calendar calendar = Calendar.getInstance(zone);
            calendar.setTimeInMillis(now);
            if (calendar.get(Calendar.HOUR_OF_DAY) < resetHour) {
                calendar.add(Calendar.DAY_OF_YEAR, -1);
            }
            long value = calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);

            // How long that answer holds, so the next few thousand callbacks in the same day are
            // two comparisons. The end is a day added rather than 24 hours, because the day the
            // clocks change is 23 or 25 hours long.
            calendar.set(Calendar.HOUR_OF_DAY, resetHour);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long start = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            long end = calendar.getTimeInMillis();
            if (now >= start && now < end) {
                cachedDay = value;
                cachedWindowStart = start;
                cachedWindowEnd = end;
                cachedResetHour = resetHour;
                cachedZoneId = zone.getID();
            } else {
                // An hour that does not exist on the day the clocks go forward lands outside its
                // own window. Better to work it out again than to answer from a window that does
                // not contain the moment it was built for.
                cachedResetHour = -1;
            }
            return value;
        }
    }

    /**
     * Starts a new day when one has arrived, and only then.
     *
     * <p>The day is computed from the reset hour, which the reader can change, and from the
     * device's timezone, which travel changes. Either can make the same moment fall on an
     * earlier day than the one already recorded. Treating that as a new day would hand back a
     * budget that had been spent and lift a hold that was running, which is a one-tap way out of
     * the whole feature. So the day only ever moves forward.
     */
    private static void rollOver(long now) {
        long today = dayOf(now);
        if (today <= day) return;
        day = today;
        videos = 0;
        watchedMs = 0;
        writtenWatchedMs = 0;
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
                // A record from a day that has not arrived yet belongs to a clock that has since
                // gone backwards, and it is still the reader's own day.
                if (storedDay >= day) {
                    day = storedDay;
                    videos = Integer.parseInt(parts[1]);
                    watchedMs = Long.parseLong(parts[2]);
                    writtenWatchedMs = watchedMs;
                    lockUntilMs = Long.parseLong(parts[3]);
                    noticeShown = "1".equals(parts[4]);
                }
            }
        } catch (RuntimeException malformed) {
            Logger.printDebug(() -> "Discarded an unreadable session budget record");
        }
    }

    /** Hands the record to the writer thread, because the settings store commits synchronously. */
    private static void save() {
        writtenWatchedMs = watchedMs;
        final String record = day + "|" + videos + "|" + watchedMs + "|"
                + lockUntilMs + "|" + (noticeShown ? "1" : "0");
        try {
            WRITER.execute(() -> Settings.SESSION_BUDGET_STATE.save(record));
        } catch (RejectedExecutionException stopped) {
            Settings.SESSION_BUDGET_STATE.save(record);
        }
    }

    // ------------------------------------------------------------------------------- for tests

    static void setClockForTests(Clock replacement) {
        synchronized (LOCK) {
            clock = replacement == null ? System::currentTimeMillis : replacement;
        }
    }

    /** Waits for the writer to catch up, so a test can read the record back. */
    static void awaitWritesForTests() throws Exception {
        WRITER.submit(() -> null).get();
    }

    /** How many times a Calendar was actually built, so a test can prove the memo holds. */
    static int dayComputationsForTests() {
        synchronized (LOCK) {
            return dayComputations;
        }
    }

    /** How many hold checks got past the fast path and took the monitor. */
    static int lockChecksUnderTheMonitorForTests() {
        synchronized (LOCK) {
            return lockChecksUnderTheMonitor;
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            cachedResetHour = -1;
            cachedZoneId = "";
            cachedWindowStart = 0;
            cachedWindowEnd = 0;
            cachedDay = 0;
            dayComputations = 0;
            lockChecksUnderTheMonitor = 0;
            loaded = false;
            day = 0;
            videos = 0;
            watchedMs = 0;
            writtenWatchedMs = 0;
            lockUntilMs = 0;
            lastTickMs = 0;
            lastCountedId = null;
            noticeShown = false;
        }
    }
}
