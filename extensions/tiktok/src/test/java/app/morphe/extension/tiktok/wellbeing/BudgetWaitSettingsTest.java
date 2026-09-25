package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Looper;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputCheckTest;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import app.morphe.extension.tiktok.settings.preference.categories.ScreenTimePreferenceCategory;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * What the Screen time page does with Wait a day to loosen the budget: a loosening typed into a
 * row is kept for the next day and the row says what it becomes and when, a tightening applies
 * at once, and Start today over is off.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class BudgetWaitSettingsTest {
    private final AtomicLong now = new AtomicLong();
    private final Map<Field, Boolean> statuses = new LinkedHashMap<>();

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionBudget.awaitWritesForTests();
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class
                    && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                statuses.put(field, field.getBoolean(null));
                field.setBoolean(null, true);
            }
        }
        for (Setting<?> setting : BudgetChanges.heldByALockedDay()) setting.resetToDefault();
        Settings.SESSION_BUDGET_PENDING.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 21, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
        BudgetChanges.resetForTests();
        ShadowToast.reset();
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(true);
        Settings.SESSION_BUDGET_MINUTES.save(30);
    }

    @After public void tearDown() throws Exception {
        for (Map.Entry<Field, Boolean> entry : statuses.entrySet()) {
            entry.getKey().setBoolean(null, entry.getValue());
        }
        statuses.clear();
        for (Setting<?> setting : BudgetChanges.heldByALockedDay()) setting.resetToDefault();
        Settings.SESSION_BUDGET_PENDING.resetToDefault();
        SessionBudget.setClockForTests(null);
        SessionBudget.awaitWritesForTests();
        SessionBudget.resetForTests();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        BudgetChanges.resetForTests();
    }

    @Test public void aLooseningTypedIntoARowWaitsAndTheRowSaysWhenItApplies() throws Exception {
        PreferenceScreen screen = savingPage();
        Preference row = screen.findPreference(Settings.SESSION_BUDGET_MINUTES.key);
        AlertDialog dialog = typeAndSave(row, "60");

        assertFalse("the dialog stayed open over a change that was taken", dialog.isShowing());
        assertEquals("the loosening applied at once", 30, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertEquals(60, BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        String applies = SessionLockOverlay.timeLabel(at(2026, Calendar.SEPTEMBER, 8, 4, 0));
        String summary = row.getSummary().toString();
        assertTrue("the row does not say what waits: " + summary,
                summary.contains("Changes to 60 minutes at " + applies));
        assertTrue("the row lost its current value: " + summary, summary.contains("Current: 30 minutes"));
        String toast = ShadowToast.getTextOfLatestToast();
        assertNotNull("nothing was said about the change waiting", toast);
        assertTrue("the toast does not say when: " + toast, toast.contains(applies));
    }

    @Test public void aTighteningAppliesAtOnceAndDropsWhatWaited() throws Exception {
        PreferenceScreen screen = savingPage();
        Preference row = screen.findPreference(Settings.SESSION_BUDGET_MINUTES.key);
        typeAndSave(row, "60");

        AlertDialog dialog = typeAndSave(row, "20");

        assertFalse(dialog.isShowing());
        assertEquals(20, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertNull("the looser number is still waiting", BudgetChanges.waiting(Settings.SESSION_BUDGET_MINUTES));
        assertFalse("the row still names a change that is gone: " + row.getSummary(),
                row.getSummary().toString().contains("Changes to"));
    }

    @Test public void withTheSwitchOffALooseningAppliesAtOnce() throws Exception {
        Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.save(false);
        PreferenceScreen screen = savingPage();
        typeAndSave(screen.findPreference(Settings.SESSION_BUDGET_MINUTES.key), "60");

        assertEquals(60, (int) Settings.SESSION_BUDGET_MINUTES.get());
        assertEquals(0, BudgetChanges.appliesAt());
    }

    @Test public void aLongerHoldAppliesAndAShorterOneWaits() throws Exception {
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(10);
        PreferenceScreen screen = savingPage();
        Preference row = screen.findPreference(Settings.SESSION_BUDGET_LOCK_MINUTES.key);

        typeAndSave(row, "15");
        assertEquals(15, (int) Settings.SESSION_BUDGET_LOCK_MINUTES.get());

        typeAndSave(row, "5");
        assertEquals(15, (int) Settings.SESSION_BUDGET_LOCK_MINUTES.get());
        assertTrue("the hold row does not say what waits: " + row.getSummary(),
                row.getSummary().toString().contains("Changes to 5 minutes at"));
    }

    @Test public void turningTheLockOffWaitsAndTheSwitchSaysWhen() {
        Settings.SESSION_BUDGET_LOCK.save(true);
        PreferenceScreen screen = screenTimeRows();
        Preference lock = screen.findPreference(Settings.SESSION_BUDGET_LOCK.key);

        assertFalse("the switch was let go off",
                lock.getOnPreferenceChangeListener().onPreferenceChange(lock, false));

        assertTrue("the lock went off at once", Settings.SESSION_BUDGET_LOCK.get());
        assertEquals(false, BudgetChanges.waiting(Settings.SESSION_BUDGET_LOCK));
        String applies = SessionLockOverlay.timeLabel(at(2026, Calendar.SEPTEMBER, 8, 4, 0));
        assertTrue("the switch does not say when it goes off: " + lock.getSummary(),
                lock.getSummary().toString().contains("Turns off at " + applies));
    }

    @Test public void turningTheSwitchItselfOffWaitsAndOnAgainTakesItBack() {
        PreferenceScreen screen = screenTimeRows();
        Preference wait = screen.findPreference(Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.key);
        assertNotNull("the switch is not on the page", wait);

        wait.getOnPreferenceChangeListener().onPreferenceChange(wait, false);
        assertTrue("the switch went off at once", Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.get());
        assertTrue(wait.getSummary().toString().contains("Turns off at"));

        assertTrue(wait.getOnPreferenceChangeListener().onPreferenceChange(wait, true));
        assertNull(BudgetChanges.waiting(Settings.SESSION_BUDGET_WAIT_TO_LOOSEN));
        assertFalse("the switch still says it turns off: " + wait.getSummary(),
                wait.getSummary().toString().contains("Turns off at"));
    }

    @Test public void aPageOpenedAfterTheResetShowsTheChangeApplied() {
        BudgetChanges.keep(Settings.SESSION_BUDGET_MINUTES, 60, now.get());
        PreferenceScreen tonight = screenTimeRows();
        assertTrue(tonight.findPreference(Settings.SESSION_BUDGET_MINUTES.key).getSummary().toString()
                .contains("Changes to 60 minutes"));

        now.set(at(2026, Calendar.SEPTEMBER, 8, 4, 1));
        PreferenceScreen morning = screenTimeRows();

        Preference row = morning.findPreference(Settings.SESSION_BUDGET_MINUTES.key);
        assertEquals(60, (int) Settings.SESSION_BUDGET_MINUTES.get());
        String summary = row.getSummary().toString();
        assertTrue(summary, summary.contains("Current: 60 minutes"));
        assertFalse(summary, summary.contains("Changes to"));
    }

    @Test public void startTodayOverIsOffWhileLooseningWaits() {
        Settings.SESSION_BUDGET_VIDEOS.save(2);
        SessionBudget.noteVideo("one");
        PreferenceScreen screen = screenTimeRows();
        Preference startOver = screen.findPreference("action_start_today_over");

        startOver.getOnPreferenceClickListener().onPreferenceClick(startOver);

        assertEquals("today started over anyway", 1, SessionBudget.videosSeen());
        String notice = ShadowToast.getTextOfLatestToast();
        assertNotNull("nothing was said about the refusal", notice);
        assertTrue(notice, notice.contains("can't start over"));
        assertTrue("the refusal does not say when the day starts over: " + notice,
                notice.contains(SessionLockOverlay.timeLabel(at(2026, Calendar.SEPTEMBER, 8, 4, 0))));
    }

    /** Types {@code text} into a number row's dialog and presses Save. */
    private static AlertDialog typeAndSave(Preference row, String text) throws Exception {
        assertNotNull("the row is not on the page", row);
        InputCheckTest.openDialog(row);
        AlertDialog dialog = (AlertDialog) ((DialogPreference) row).getDialog();
        assertNotNull("the dialog did not open", dialog);
        ((EditTextPreference) row).getEditText().setText(text);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return dialog;
    }

    /**
     * The real settings page on the Screen time section. A value the page takes is saved through
     * the settings store only there: a bare fragment keeps its own preferences file.
     */
    private static PreferenceScreen savingPage() {
        Activity activity = Robolectric.buildActivity(
                app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class)
                .setup().visible().get();
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString("morphe_settings_section", "SCREEN_TIME");
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return fragment.getPreferenceScreen();
    }

    /** A preference fragment only so the framework will hand out a PreferenceScreen. */
    public static class HostFragment extends android.preference.PreferenceFragment {
    }

    private static PreferenceScreen screenTimeRows() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        HostFragment fragment = new HostFragment();
        activity.getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        PreferenceScreen screen = fragment.getPreferenceManager().createPreferenceScreen(activity);
        fragment.setPreferenceScreen(screen);
        new ScreenTimePreferenceCategory(activity, screen);
        return screen;
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
