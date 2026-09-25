package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.AtomicFile;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsBackup;
import app.morphe.extension.tiktok.settings.SettingsOperationJournal;

import java.io.File;

import org.json.JSONObject;
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
 * A restore, an undo and a reset are held to the budget the way the Screen time page is. A
 * backup that carried a looser budget was a way round both the lock and the wait: restore it
 * and the evening goes on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BudgetRestoreTest {
    private final AtomicLong now = new AtomicLong();

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionBudget.awaitWritesForTests();
        for (Setting<?> setting : Setting.allLoadedSettings()) setting.resetToDefault();
        FeatureGateLabStore.resetAllLabData();
        new AtomicFile(new File(Utils.getContext().getFilesDir(), "hushfeed-settings-operation.json")).delete();
        SettingsOperationJournal.consumeRecoveryNotice();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 21, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
        BudgetChanges.resetForTests();
    }

    @After public void tearDown() throws Exception {
        SessionBudget.setClockForTests(null);
        SessionBudget.awaitWritesForTests();
        for (Setting<?> setting : Setting.allLoadedSettings()) setting.resetToDefault();
        SessionBudget.resetForTests();
        BudgetChanges.resetForTests();
    }

    @Test public void aRestoredLooserBudgetWaitsAndUndoTakesTheWaitBack() throws Exception {
        Settings.SESSION_BUDGET_MINUTES.save(60);
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        String looser = SettingsBackup.create(false);
        Settings.SESSION_BUDGET_MINUTES.save(30);

        SettingsBackup.restore(Utils.getContext(), looser, true);

        assertEquals("a restore loosened the budget at once", 30, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertEquals(60, BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        assertEquals(at(2026, Calendar.SEPTEMBER, 8, 4, 0), BudgetChanges.appliesAt());

        SettingsBackup.undo(Utils.getContext());

        assertEquals(30, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertNull("undo left the restore's change waiting", BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        assertEquals(0, BudgetChanges.appliesAt());
    }

    @Test public void aTighterBudgetInABackupAppliesAtOnce() throws Exception {
        // The switch is on in the backup too: one that had it off would be turning it off,
        // which is a loosening of its own and waits.
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        Settings.SESSION_BUDGET_MINUTES.save(20);
        String tighter = SettingsBackup.create(false);
        Settings.SESSION_BUDGET_MINUTES.save(30);

        SettingsBackup.restore(Utils.getContext(), tighter, true);

        assertEquals(20, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertEquals(0, BudgetChanges.appliesAt());
    }

    /** Reset puts every default back, and the defaults are no budget and no wait at all. */
    @Test public void resettingWaitsForTheNextDayToDropTheBudget() throws Exception {
        Settings.SESSION_BUDGET_MINUTES.save(30);
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);

        SettingsBackup.reset(Utils.getContext());

        assertEquals(30, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertTrue(Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.get());
        assertEquals(0, BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        assertEquals(false, BudgetChanges.waiting(Settings.SESSION_BUDGET_WAIT_TO_LOOSEN));
    }

    @Test public void aLockedDayKeepsItsBudgetThroughARestore() throws Exception {
        Settings.SESSION_BUDGET_VIDEOS.save(50);
        String looser = SettingsBackup.create(false);
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK.save(true);
        SessionBudget.noteVideo("only-one");
        assertTrue(SessionBudget.claimNotice());
        assertTrue("the day did not lock", SessionBudget.lockedToday());

        SettingsBackup.restore(Utils.getContext(), looser, true);

        assertEquals("a restore reopened a locked day", 1, (int) Settings.SESSION_BUDGET_VIDEOS.get());
        assertTrue(Settings.SESSION_BUDGET_LOCK.get());
        assertEquals("nothing should wait on a locked day", 0, BudgetChanges.appliesAt());
    }

    /**
     * A number outside a setting's range is brought inside it when it is saved, so it is judged
     * that way too. A backup edited to say -1 videos restores as 0, which is no budget at all:
     * judged as -1 it looked tighter than 50 and turned the budget off at once.
     */
    @Test public void aNumberOutsideItsRangeIsJudgedAsItWillBeSaved() throws Exception {
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        Settings.SESSION_BUDGET_VIDEOS.save(50);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(30);
        JSONObject edited = new JSONObject(SettingsBackup.create(false));
        edited.getJSONObject("settings").put(Settings.SESSION_BUDGET_VIDEOS.key, -1)
                .put(Settings.SESSION_BUDGET_LOCK_MINUTES.key, 100000);

        SettingsBackup.restore(Utils.getContext(), edited.toString(), true);

        assertEquals("a restored -1 turned the budget off at once", 50, (int) Settings.SESSION_BUDGET_VIDEOS.get());
        assertEquals("what waits is the value the setting will hold", 0, BudgetChanges.waiting(Settings.SESSION_BUDGET_VIDEOS));
        // Brought down to the top of its range, a longer hold is still a tighter one.
        assertEquals(720, (int) Settings.SESSION_BUDGET_LOCK_MINUTES.get());
    }

    /**
     * A change that has fallen due is part of what a restore finds. The restore applies it before
     * it reads anything, so the undo copy carries it, and a restore that fails puts it back rather
     * than the value it replaced, whose record would already be gone.
     */
    @Test public void aChangeAlreadyDueIsPartOfWhatARestoreFinds() throws Exception {
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        Settings.SESSION_BUDGET_MINUTES.save(30);
        Settings.REGION_SPOOF.save(true);
        String backup = SettingsBackup.create(false);
        Settings.REGION_SPOOF.save(false);
        BudgetChanges.keep(Settings.SESSION_BUDGET_MINUTES, 60, now.get());
        now.set(at(2026, Calendar.SEPTEMBER, 8, 5, 0));

        SettingsBackup.restore(Utils.getContext(), backup, true);

        JSONObject found = new JSONObject(SettingsBackup.undo(Utils.getContext())).getJSONObject("settings");
        assertEquals("the undo copy missed a change that had fallen due",
                60, found.getInt(Settings.SESSION_BUDGET_MINUTES.key));
    }

    /**
     * A restore the budget held back writes less than its file carries. If its journal outlives
     * it, because the delete failed say, the next start has to read it as committed: read as an
     * interrupted restore, it put the old settings back and left the held back change to land
     * the next day anyway.
     */
    @Test public void aJournalLeftByARestoreTheBudgetHeldBackReadsAsCommitted() throws Exception {
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        Settings.SESSION_BUDGET_MINUTES.save(60);
        Settings.REGION_SPOOF.save(true);
        String looser = SettingsBackup.create(false);
        Settings.SESSION_BUDGET_MINUTES.save(30);
        Settings.REGION_SPOOF.save(false);

        SettingsOperationJournal.failCommittedDeletesForTests(true);
        try {
            SettingsBackup.restore(Utils.getContext(), looser, true);
        } finally {
            SettingsOperationJournal.failCommittedDeletesForTests(false);
        }
        assertTrue("no journal was left behind, so this checks nothing",
                new File(Utils.getContext().getFilesDir(), "hushfeed-settings-operation.json").isFile());

        // What the next change, or the next start, does first.
        SettingsOperationJournal.acquire(Utils.getContext()).complete();

        assertTrue("the next start put a committed restore back", Settings.REGION_SPOOF.get());
        assertEquals(30, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertEquals(60, BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        assertEquals(SettingsOperationJournal.Recovery.ALREADY_COMMITTED,
                SettingsOperationJournal.consumeRecoveryNotice());
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
