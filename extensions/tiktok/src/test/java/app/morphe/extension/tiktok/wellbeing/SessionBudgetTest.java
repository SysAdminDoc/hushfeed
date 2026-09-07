package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A budget that miscounts is worse than none: it either stops someone who has barely started or
 * never stops anyone at all, and either way they turn it off and do not come back. These pin the
 * counting, the day boundary, and that the hold and the counts are still there after the process
 * has been killed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SessionBudgetTest {
    private final AtomicLong now = new AtomicLong();

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_RESET_HOUR.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        Settings.AUTO_ADVANCE_LIMIT.resetToDefault();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
    }

    @After public void tearDown() {
        SessionBudget.setClockForTests(null);
        SessionBudget.resetForTests();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
    }

    @Test public void everyDifferentVideoCountsOnce() {
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.noteVideo("c");

        // The player names the same video several times a second; only a change is a video.
        assertEquals(3, SessionBudget.videosSeen());
    }

    @Test public void aVideoComeBackToCountsAgain() {
        // Scrolling away and back is watching it again, and it is only the immediate repeat
        // from the player that must not count twice.
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.noteVideo("a");

        assertEquals(3, SessionBudget.videosSeen());
    }

    @Test public void nothingIsABudgetUntilOneIsSet() {
        for (int video = 0; video < 50; video++) SessionBudget.noteVideo("v" + video);

        assertFalse("a budget nobody set stopped the feed", SessionBudget.reachedLimit());
        assertFalse(SessionBudget.claimNotice());
    }

    @Test public void theVideoBudgetIsReachedOnTheVideoThatReachesIt() {
        Settings.SESSION_BUDGET_VIDEOS.save(3);

        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertFalse(SessionBudget.reachedLimit());

        SessionBudget.noteVideo("c");
        assertTrue(SessionBudget.reachedLimit());
    }

    @Test public void theNoticeIsClaimedOnceAndOnlyOnce() {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");

        assertTrue("nothing told the reader the budget had gone", SessionBudget.claimNotice());
        SessionBudget.noteVideo("c");
        assertFalse("the notice came back on the next video", SessionBudget.claimNotice());
    }

    @Test public void raisingTheBudgetArmsTheNoticeAgain() {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());

        Settings.SESSION_BUDGET_VIDEOS.save(4);
        assertFalse("raising the budget did not put the reader back under it",
                SessionBudget.reachedLimit());
        assertFalse(SessionBudget.claimNotice());

        SessionBudget.noteVideo("c");
        SessionBudget.noteVideo("d");
        assertTrue(SessionBudget.claimNotice());
    }

    @Test public void watchingIsCountedFromOnePlayerReportToTheNext() {
        Settings.SESSION_BUDGET_MINUTES.save(1);

        SessionBudget.noteWatching();            // the first report has nothing to measure from
        assertEquals(0, SessionBudget.watchedMs());

        for (int tick = 0; tick < 30; tick++) {
            now.addAndGet(2_000L);
            SessionBudget.noteWatching();
        }

        assertEquals(60_000L, SessionBudget.watchedMs());
        assertTrue(SessionBudget.reachedLimit());
    }

    @Test public void timeTheAppSpentAwayIsNotWatching() {
        SessionBudget.noteWatching();
        now.addAndGet(1_000L);
        SessionBudget.noteWatching();

        // The player stops reporting while the app is in the background, so a long gap is not
        // an hour of watching, it is an hour of the phone being in a pocket.
        now.addAndGet(60L * 60_000L);
        SessionBudget.noteWatching();

        assertEquals(1_000L, SessionBudget.watchedMs());
    }

    @Test public void theCountsResetAtTheChosenHourAndNotAtMidnight() {
        Settings.SESSION_BUDGET_RESET_HOUR.save(4);
        now.set(at(2026, Calendar.SEPTEMBER, 7, 23, 30));
        SessionBudget.resetForTests();
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertEquals(2, SessionBudget.videosSeen());

        // One in the morning is still the same evening.
        now.set(at(2026, Calendar.SEPTEMBER, 8, 1, 0));
        assertEquals("midnight ended the day", 2, SessionBudget.videosSeen());

        // Four o'clock is the new day.
        now.set(at(2026, Calendar.SEPTEMBER, 8, 4, 1));
        assertEquals("the chosen hour did not start a new day", 0, SessionBudget.videosSeen());
    }

    @Test public void aDifferentChosenHourMovesTheBoundary() {
        Settings.SESSION_BUDGET_RESET_HOUR.save(9);
        now.set(at(2026, Calendar.SEPTEMBER, 7, 23, 30));
        SessionBudget.resetForTests();
        SessionBudget.noteVideo("a");

        now.set(at(2026, Calendar.SEPTEMBER, 8, 8, 59));
        assertEquals("the day ended before the hour that was chosen", 1, SessionBudget.videosSeen());

        now.set(at(2026, Calendar.SEPTEMBER, 8, 9, 0));
        assertEquals(0, SessionBudget.videosSeen());
    }

    @Test public void theHoldRunsForAsLongAsItWasGivenAndThenStops() {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);

        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());
        assertTrue("no hold started", SessionBudget.isLocked());
        assertEquals(10L * 60_000L, SessionBudget.lockRemainingMs());

        now.addAndGet(9L * 60_000L);
        assertTrue(SessionBudget.isLocked());
        assertEquals(60_000L, SessionBudget.lockRemainingMs());

        now.addAndGet(60_000L);
        assertFalse("the hold outlasted the time it was given", SessionBudget.isLocked());
    }

    @Test public void noHoldIsSetWhenNoneWasAskedFor() {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(0);

        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());
        assertFalse("a hold appeared that nobody asked for", SessionBudget.isLocked());
    }

    @Test public void theHoldCanBeLiftedWithoutForgettingTheCount() {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        SessionBudget.noteVideo("a");
        SessionBudget.claimNotice();

        SessionBudget.releaseLock();

        assertFalse(SessionBudget.isLocked());
        assertEquals("lifting the hold forgot the day", 1, SessionBudget.videosSeen());
        assertTrue(SessionBudget.reachedLimit());
        assertFalse("the notice came back after the hold was lifted", SessionBudget.claimNotice());
    }

    @Test public void theCountAndTheHoldSurviveTheProcessBeingKilled() {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());

        // Everything in memory goes; only what reached the settings store comes back.
        SessionBudget.resetForTests();

        assertEquals(2, SessionBudget.videosSeen());
        assertTrue("the hold did not survive a restart", SessionBudget.isLocked());
        assertEquals(30L * 60_000L, SessionBudget.lockRemainingMs());
        assertFalse("the notice came back after a restart", SessionBudget.claimNotice());
    }

    @Test public void aRecordFromAnotherDayIsNotBelieved() {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.claimNotice();

        now.set(at(2026, Calendar.SEPTEMBER, 9, 12, 0));
        SessionBudget.resetForTests();

        assertEquals("yesterday's count came back", 0, SessionBudget.videosSeen());
        assertFalse("yesterday's hold came back", SessionBudget.isLocked());
    }

    @Test public void anUnreadableRecordIsDiscardedRatherThanCrashing() {
        Settings.SESSION_BUDGET_STATE.save("this is not a session budget");
        SessionBudget.resetForTests();

        assertEquals(0, SessionBudget.videosSeen());
        assertFalse(SessionBudget.isLocked());

        SessionBudget.noteVideo("a");
        assertEquals(1, SessionBudget.videosSeen());
    }

    @Test public void theBudgetIsNotTheAutoAdvanceLimit() {
        // AUTO_ADVANCE_LIMIT counts only videos Hushfeed itself advanced past, and lives on the
        // per-component controller. Wiring one to the other would stop the feed for someone who
        // never turned automatic advance on.
        Settings.AUTO_ADVANCE_LIMIT.save(1);
        Settings.SESSION_BUDGET_VIDEOS.save(0);

        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.noteVideo("c");

        assertFalse("the auto-advance limit stopped the feed", SessionBudget.reachedLimit());
        assertEquals(3, SessionBudget.videosSeen());

        Settings.SESSION_BUDGET_VIDEOS.save(3);
        assertTrue(SessionBudget.reachedLimit());
        assertEquals("the budget changed the auto-advance limit",
                1, (int) Settings.AUTO_ADVANCE_LIMIT.get());
    }

    @Test public void nothingInTheBudgetCanDropAFeedItem() throws Exception {
        // The feature is an overlay for a reason. If the budget ever reached into the feed
        // filter, a short batch would go back to TikTok and it would refetch the videos it had
        // already sent, which is the opposite of leaving someone alone. This is the gate that
        // keeps the two apart, and it fails the moment either one names the other.
        String filter = read("feedfilter/FeedItemsFilter.java");
        assertFalse("the feed filter now consults the session budget",
                filter.contains("SessionBudget"));

        for (String name : new String[]{"wellbeing/SessionBudget.java",
                "wellbeing/SessionBudgetNotice.java", "wellbeing/SessionLockOverlay.java"}) {
            String source = read(name);
            assertFalse(name + " reaches into the feed filter",
                    source.contains("feedfilter"));
        }
    }

    private static String read(String relative) throws Exception {
        java.io.File root = new java.io.File("src/main/java/app/morphe/extension/tiktok");
        if (!root.isDirectory()) root = new java.io.File(
                "extensions/tiktok/src/main/java/app/morphe/extension/tiktok");
        java.io.File file = new java.io.File(root, relative);
        assertTrue("could not find " + file.getAbsolutePath(), file.isFile());
        return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
