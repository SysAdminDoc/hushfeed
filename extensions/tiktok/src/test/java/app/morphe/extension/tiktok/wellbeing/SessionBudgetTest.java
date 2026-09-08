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

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        // The record is written on its own thread, so the previous case's write has to land
        // before the store is cleared or it comes back as this case's starting state.
        SessionBudget.awaitWritesForTests();
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_RESET_HOUR.resetToDefault();
        Settings.SESSION_BUDGET_LOCK.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        Settings.AUTO_ADVANCE_LIMIT.resetToDefault();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
    }

    @After public void tearDown() throws Exception {
        Settings.SESSION_BUDGET_LOCK.resetToDefault();
        SessionBudget.setClockForTests(null);
        SessionBudget.awaitWritesForTests();
        SessionBudget.resetForTests();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
    }

    @Test public void everyDifferentVideoCountsOnce() {
        Settings.SESSION_BUDGET_VIDEOS.save(100);
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
        Settings.SESSION_BUDGET_VIDEOS.save(100);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.noteVideo("a");

        assertEquals(3, SessionBudget.videosSeen());
    }

    @Test public void nothingIsCountedUntilABudgetIsSet() {
        // Not just "no limit reached": nothing is counted and nothing is written. The player
        // reports several times a second and the settings store commits synchronously, so
        // counting for someone who never asked for this is thousands of blocking writes an hour.
        for (int video = 0; video < 50; video++) SessionBudget.noteVideo("v" + video);
        for (int tick = 0; tick < 50; tick++) {
            now.addAndGet(1_000L);
            SessionBudget.noteWatching();
        }

        assertEquals("a video was counted with no budget set", 0, SessionBudget.videosSeen());
        assertEquals("time was counted with no budget set", 0, SessionBudget.watchedMs());
        assertFalse("a budget nobody set stopped the feed", SessionBudget.reachedLimit());
        assertFalse(SessionBudget.claimNotice());
        assertEquals("a record was written with nothing to record", "",
                Settings.SESSION_BUDGET_STATE.get());
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
        Settings.SESSION_BUDGET_MINUTES.save(60);
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
        Settings.SESSION_BUDGET_VIDEOS.save(100);
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
        Settings.SESSION_BUDGET_VIDEOS.save(100);
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

    @Test public void theCountAndTheHoldSurviveTheProcessBeingKilled() throws Exception {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());

        // Everything in memory goes; only what reached the settings store comes back.
        SessionBudget.awaitWritesForTests();
        SessionBudget.resetForTests();

        assertEquals(2, SessionBudget.videosSeen());
        assertTrue("the hold did not survive a restart", SessionBudget.isLocked());
        assertEquals(30L * 60_000L, SessionBudget.lockRemainingMs());
        assertFalse("the notice came back after a restart", SessionBudget.claimNotice());
    }

    @Test public void aRecordFromAnotherDayIsNotBelieved() throws Exception {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.claimNotice();

        SessionBudget.awaitWritesForTests();
        now.set(at(2026, Calendar.SEPTEMBER, 9, 12, 0));
        SessionBudget.resetForTests();

        assertEquals("yesterday's count came back", 0, SessionBudget.videosSeen());
        assertFalse("yesterday's hold came back", SessionBudget.isLocked());
    }

    @Test public void anUnreadableRecordIsDiscardedRatherThanCrashing() {
        Settings.SESSION_BUDGET_VIDEOS.save(100);
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
        Settings.SESSION_BUDGET_VIDEOS.save(5);

        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        SessionBudget.noteVideo("c");

        assertFalse("the auto-advance limit of 1 stopped the feed", SessionBudget.reachedLimit());
        assertEquals(3, SessionBudget.videosSeen());

        Settings.SESSION_BUDGET_VIDEOS.save(3);
        assertTrue(SessionBudget.reachedLimit());
        assertEquals("the budget changed the auto-advance limit",
                1, (int) Settings.AUTO_ADVANCE_LIMIT.get());
    }

    @Test public void movingTheDayBoundaryDoesNotHandBackASpentBudget() {
        // Changing the reset hour, or crossing a timezone, can put the same moment on an earlier
        // day. Treating that as a new day is a one-tap way out of the whole feature: the counts
        // go to zero and any hold running is lifted.
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(60);
        Settings.SESSION_BUDGET_RESET_HOUR.save(4);
        now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
        SessionBudget.resetForTests();
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());
        assertTrue(SessionBudget.isLocked());

        // Noon is now before the start of the day, so the day computes one earlier.
        Settings.SESSION_BUDGET_RESET_HOUR.save(13);

        assertEquals("the day was handed back", 2, SessionBudget.videosSeen());
        assertTrue("the hold was lifted by moving the boundary", SessionBudget.isLocked());
        assertTrue(SessionBudget.reachedLimit());
    }

    @Test public void aRaisedBudgetLiftsTheHoldItStartedUnder() {
        // A hold for a budget nobody is over any more is a screen covered for no reason the
        // reader can see, and it would sit there for the rest of the hold.
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());
        assertTrue(SessionBudget.isLocked());

        Settings.SESSION_BUDGET_VIDEOS.save(10);

        assertFalse(SessionBudget.claimNotice());
        assertFalse("the hold outlived the budget that started it", SessionBudget.isLocked());
    }

    @Test public void watchingIsNotCountedWhileTheFeedIsHeld() {
        // The player keeps reporting behind the overlay. Charging someone for a video they
        // cannot see would empty tomorrow's budget on top of today's.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_MINUTES.save(60);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());
        assertTrue(SessionBudget.isLocked());

        SessionBudget.noteWatching();
        long before = SessionBudget.watchedMs();
        for (int tick = 0; tick < 20; tick++) {
            now.addAndGet(2_000L);
            SessionBudget.noteWatching();
        }

        assertEquals("time behind the hold was counted", before, SessionBudget.watchedMs());
    }

    @Test public void theRecordIsNotRewrittenOnEveryPlayerReport() throws Exception {
        // The settings store commits synchronously, and the player reports several times a
        // second. A write per report is thousands of blocking writes an hour.
        Settings.SESSION_BUDGET_MINUTES.save(60);
        SessionBudget.noteWatching();
        now.addAndGet(1_000L);
        SessionBudget.noteWatching();
        SessionBudget.awaitWritesForTests();

        assertEquals("a second of watching was committed on its own", "",
                Settings.SESSION_BUDGET_STATE.get());

        // It is committed once enough of it has built up to be worth losing.
        for (int tick = 0; tick < 40; tick++) {
            now.addAndGet(1_000L);
            SessionBudget.noteWatching();
        }
        SessionBudget.awaitWritesForTests();
        assertTrue("half a minute of watching was never committed",
                Settings.SESSION_BUDGET_STATE.get().contains("|"));
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

    @Test public void theHoldCheckCostsNothingWithNoBudgetSet() {
        // SessionLockOverlay.ensureRunning asks this from the player's progress callback, which
        // arrives several times a second while any video plays. With no budget set, which is the
        // shipped default, it used to take the budget monitor and build a Calendar and a TimeZone
        // clone every time, on the player thread.
        assertFalse("a hold nobody set", SessionBudget.isLocked());
        int days = SessionBudget.dayComputationsForTests();
        int monitor = SessionBudget.lockChecksUnderTheMonitorForTests();

        for (int callback = 0; callback < 500; callback++) {
            now.addAndGet(40L);
            assertFalse(SessionBudget.isLocked());
        }

        assertEquals("a Calendar was built per player callback",
                days, SessionBudget.dayComputationsForTests());
        assertEquals("the budget monitor was taken per player callback",
                monitor, SessionBudget.lockChecksUnderTheMonitorForTests());
    }

    @Test public void aRunningHoldIsStillSeenByTheFastPath() {
        // The fast path answers only "no hold". If it ever answered from a stale field, the hold
        // would be invisible to the overlay and the panel would never appear.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        assertTrue("the hold the notice just started was not seen", SessionBudget.isLocked());
        now.addAndGet(6L * 60_000L);
        assertFalse("the hold outlived its own minutes", SessionBudget.isLocked());
    }

    @Test public void theDayIsWorkedOutOncePerDayNotOncePerCallback() {
        Settings.SESSION_BUDGET_MINUTES.save(30);
        SessionBudget.noteWatching();
        int days = SessionBudget.dayComputationsForTests();

        for (int callback = 0; callback < 500; callback++) {
            now.addAndGet(1_000L);
            SessionBudget.noteWatching();
        }
        assertEquals("a Calendar per callback inside one day",
                days, SessionBudget.dayComputationsForTests());

        // Past the reset hour the answer has to be worked out again, or the day never turns over.
        now.set(at(2026, Calendar.SEPTEMBER, 9, 12, 0));
        SessionBudget.noteWatching();
        assertTrue("the day was answered from a window it had left",
                SessionBudget.dayComputationsForTests() > days);
        assertEquals("the new day did not start clean", 0, SessionBudget.watchedMs());
    }

    @Test public void changingTheResetHourIsNotAnsweredFromTheOldWindow() {
        // The reader can move the hour the day ends at, and the window cached under the old hour
        // still contains the moment being asked about.
        Settings.SESSION_BUDGET_RESET_HOUR.save(4);
        long early = SessionBudget.dayOf(now.get());
        Settings.SESSION_BUDGET_RESET_HOUR.save(20);
        long late = SessionBudget.dayOf(now.get());

        assertEquals("noon belongs to the day before when the day ends at eight in the evening",
                early - 1, late);
        Settings.SESSION_BUDGET_RESET_HOUR.resetToDefault();
    }

    @Test public void videosAreNotCountedWhileTheFeedIsHeld() {
        // The feed is behind the panel. Counting what goes by underneath it spends tomorrow's
        // budget as well as today's, and the notice keeps climbing during a hold nobody can see.
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());
        assertTrue("no hold started", SessionBudget.isLocked());
        int during = SessionBudget.videosSeen();

        for (int video = 0; video < 20; video++) SessionBudget.noteVideo("held" + video);
        assertEquals("videos behind the panel were counted", during, SessionBudget.videosSeen());

        // Once the hold ends the feed is visible again, so counting starts again.
        now.addAndGet(11L * 60_000L);
        assertFalse(SessionBudget.isLocked());
        SessionBudget.noteVideo("after");
        assertEquals("counting did not start again when the hold ended",
                during + 1, SessionBudget.videosSeen());
    }

    @Test public void aBudgetOfOneDoesNotSayOneVideos() throws Exception {
        // "That is 1 videos today" is the shape a format string gives you if nobody looks.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        java.lang.reflect.Method message = SessionBudgetNotice.class
                .getDeclaredMethod("spentMessage");
        message.setAccessible(true);
        assertEquals("That is one video today", message.invoke(null));

        SessionBudget.clear();
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        SessionBudget.noteVideo("a");
        SessionBudget.noteVideo("b");
        assertTrue(SessionBudget.claimNotice());
        assertEquals("That is 2 videos today", message.invoke(null));
    }

    // ----------------------------------------------------------------- locking today's budget

    /** Spends a one video budget, which starts the hold and, with the lock on, commits it. */
    private void spendTheBudget() {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        SessionBudget.noteVideo("only-one");
        assertTrue("the budget was not reached", SessionBudget.claimNotice());
    }

    @Test public void theLockIsOffUntilItIsAskedFor() {
        assertFalse("the lock is on out of the box", Settings.SESSION_BUDGET_LOCK.get());
        spendTheBudget();
        assertFalse("an unlocked day came back locked", SessionBudget.lockedToday());
        assertTrue("Start today over was refused with no lock set", SessionBudget.clear());
    }

    @Test public void aLockedDayHasNoWayOutOfTheHold() {
        Settings.SESSION_BUDGET_LOCK.save(true);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        spendTheBudget();

        assertTrue("the day did not lock", SessionBudget.lockedToday());
        assertTrue("no hold was placed at all", SessionBudget.isLocked());

        SessionBudget.releaseLock();
        assertTrue("Open the feed anyway lifted a locked hold", SessionBudget.isLocked());
        assertFalse("Start today over cleared a locked day", SessionBudget.clear());
        assertTrue("the counts were cleared anyway", SessionBudget.reachedLimit());
        assertEquals("the video count was cleared anyway", 1, SessionBudget.videosSeen());
    }

    @Test public void aLockedHoldRunsToTheResetHourRatherThanForTheHoldMinutes() {
        Settings.SESSION_BUDGET_LOCK.save(true);
        // Ten minutes is what an unlocked day would have given. The locked day owes the reader
        // the rest of the day, which from noon with a four in the morning reset is sixteen hours.
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        spendTheBudget();

        now.addAndGet(11 * 60_000L);
        assertTrue("the hold ran out after the hold minutes", SessionBudget.isLocked());

        long resetAt = at(2026, Calendar.SEPTEMBER, 8, 4, 0);
        assertEquals("the locked day does not end at the reset hour",
                resetAt, SessionBudget.lockedUntilMs());
    }

    @Test public void raisingTheBudgetDoesNotHandBackALockedDay() {
        Settings.SESSION_BUDGET_LOCK.save(true);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        spendTheBudget();

        // The settings screen refuses this edit while the lock holds. Refused here too, because
        // a budget that a saved value can undo is not a commitment.
        Settings.SESSION_BUDGET_VIDEOS.save(500);
        assertFalse("the notice was armed again by raising the budget", SessionBudget.claimNotice());
        assertTrue("raising the budget lifted a locked hold", SessionBudget.isLocked());
        assertTrue("raising the budget unlocked the day", SessionBudget.lockedToday());
    }

    @Test public void movingTheDeviceTimezoneForwardDoesNotEndALockedDay() {
        // The day only ever moving forward is not enough on its own. A forward zone change makes
        // the day counter jump, which used to clear the lock, the hold and the counts together:
        // two taps in the device settings and the rest of the day was handed back.
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            SessionBudget.resetForTests();
            now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
            Settings.SESSION_BUDGET_LOCK.save(true);
            spendTheBudget();
            assertTrue("the day did not lock", SessionBudget.lockedToday());
            long until = SessionBudget.lockedUntilMs();

            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertTrue("a timezone change ended the locked day", SessionBudget.lockedToday());
            assertTrue("a timezone change lifted the hold", SessionBudget.isLocked());
            assertEquals("a timezone change cleared the counts", 1, SessionBudget.videosSeen());
            assertEquals("the locked day moved when the zone did", until, SessionBudget.lockedUntilMs());
        } finally {
            java.util.TimeZone.setDefault(original);
            SessionBudget.resetForTests();
        }
    }

    @Test public void theSwitchTurnedOnAfterTheBudgetRanOutLocksTheRestOfTheDay() {
        // It read as on and did nothing at all until tomorrow, and the day it was turned on for
        // stayed open, which is not what a switch called "lock today's budget" says.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        SessionBudget.noteVideo("only-one");
        assertTrue(SessionBudget.claimNotice());
        assertFalse("the day was locked before the switch was touched", SessionBudget.lockedToday());

        Settings.SESSION_BUDGET_LOCK.save(true);
        assertTrue("turning the switch on did nothing for the day it was turned on for",
                SessionBudget.lockIfSpent());
        assertTrue(SessionBudget.lockedToday());
        assertFalse("Start today over still cleared the day", SessionBudget.clear());
        assertEquals(1, SessionBudget.videosSeen());
    }

    @Test public void loweringTheBudgetUnderTodaysCountDoesNotLockTheDay() {
        // Worked out from the switch and the counts together, this locked the day on the spot
        // for someone who never reached their budget, and everything stayed refused until
        // tomorrow. A locked day is written once, by the budget running out or by the switch.
        Settings.SESSION_BUDGET_LOCK.save(true);
        Settings.SESSION_BUDGET_VIDEOS.save(10);
        for (int video = 0; video < 5; video++) SessionBudget.noteVideo("video-" + video);
        assertFalse("the budget was reached", SessionBudget.claimNotice());
        assertFalse(SessionBudget.lockedToday());

        Settings.SESSION_BUDGET_VIDEOS.save(3);

        assertFalse("lowering the budget locked a day nobody spent", SessionBudget.lockedToday());
        assertTrue("Start today over was refused", SessionBudget.clear());
        assertEquals("the day was not cleared", 0, SessionBudget.videosSeen());
    }

    @Test public void aDayLockedByTheSwitchAlsoSurvivesATimezoneChange() {
        // The guard reads the field, so the day the switch locked has to set the field too.
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
            SessionBudget.resetForTests();
            now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
            Settings.SESSION_BUDGET_VIDEOS.save(1);
            SessionBudget.noteVideo("only-one");
            assertTrue(SessionBudget.claimNotice());

            Settings.SESSION_BUDGET_LOCK.save(true);
            assertTrue(SessionBudget.lockIfSpent());
            long until = SessionBudget.lockedUntilMs();
            assertTrue("the switch locked no day at all", until > now.get());

            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertTrue("a timezone change ended the day the switch locked",
                    SessionBudget.lockedToday());
            assertEquals("the locked day moved when the zone did", until,
                    SessionBudget.lockedUntilMs());
        } finally {
            java.util.TimeZone.setDefault(original);
            SessionBudget.resetForTests();
        }
    }

    @Test public void theLockLetsGoWhenTheDayTurnsOver() {
        Settings.SESSION_BUDGET_LOCK.save(true);
        spendTheBudget();
        assertTrue(SessionBudget.lockedToday());

        // One minute before the reset hour, and then one minute after it.
        now.set(at(2026, Calendar.SEPTEMBER, 8, 3, 59));
        assertTrue("the lock let go before the day was over", SessionBudget.lockedToday());
        now.set(at(2026, Calendar.SEPTEMBER, 8, 4, 1));

        assertFalse("the lock outlived the day it was for", SessionBudget.lockedToday());
        assertFalse("the hold outlived the day it was for", SessionBudget.isLocked());
        assertTrue("Start today over is still refused on a new day", SessionBudget.clear());
        assertEquals("the new day did not start empty", 0, SessionBudget.videosSeen());
    }

    @Test public void aLockedDaySurvivesTheProcessBeingKilled() throws Exception {
        Settings.SESSION_BUDGET_LOCK.save(true);
        spendTheBudget();
        SessionBudget.awaitWritesForTests();

        SessionBudget.resetForTests();
        SessionBudget.setClockForTests(now::get);

        assertTrue("the lock was forgotten when the process went away", SessionBudget.lockedToday());
        SessionBudget.releaseLock();
        assertTrue("the hold was lifted after a restart", SessionBudget.isLocked());
    }

    @Test public void aRecordWrittenBeforeTheLockExistedStillLoads() throws Exception {
        // Five fields, which is what every record written before this feature has. A day it
        // describes was never locked, so its absence has to read as not locked rather than
        // as an unreadable record that throws the whole day away.
        long today = SessionBudget.dayOf(now.get());
        Settings.SESSION_BUDGET_STATE.save(today + "|7|60000|0|1");
        SessionBudget.resetForTests();
        SessionBudget.setClockForTests(now::get);

        assertEquals("an older record was thrown away", 7, SessionBudget.videosSeen());
        assertFalse("an older record came back locked", SessionBudget.lockedToday());
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
