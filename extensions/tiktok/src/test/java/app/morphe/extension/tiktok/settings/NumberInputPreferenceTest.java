package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/** Singular and plural unit labels on numeric settings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NumberInputPreferenceTest {
    public static final class TestActivity extends PreferenceActivity {}

    /** Reaches the row's own dialog close, which is protected and one package over. */
    static final class Row extends NumberInputPreference {
        Row(Context context, IntegerSetting setting) {
            super(context, "Daily time budget", "Summary", setting, "minute", "minutes");
        }

        void save() {
            onDialogClosed(true);
        }
    }

    // Before as well as after. The settings registry needs a context the first time anything
    // touches it, and this class built one at line one of its first case; whether that worked
    // depended on an earlier class in the same Robolectric sandbox having set one. Adding test
    // classes changed the order and every case in the run failed on a poisoned static.
    @Before
    public void installContext() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @After
    public void restoreContext() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Test
    public void englishUsesTheRightFormForZeroOneAndMany() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Current", "to", "second", "seconds");
            assertUnits(context, "day", "days", "Current", "to", "day", "days");
            assertUnits(context, "view per like", "views per like", "Current", "to",
                    "view per like", "views per like");
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanTranslatesEachForm() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Aktuell", "bis", "Sekunde", "Sekunden");
            assertUnits(context, "day", "days", "Aktuell", "bis", "Tag", "Tage");
            assertUnits(context, "view per like", "views per like", "Aktuell", "bis",
                    "Aufruf pro Like", "Aufrufe pro Like");
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "in-rID")
    public void indonesianTranslatesEachForm() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Saat ini", "sampai", "detik", "detik");
            assertUnits(context, "day", "days", "Saat ini", "sampai", "hari", "hari");
            assertUnits(context, "view per like", "views per like", "Saat ini", "sampai",
                    "tayangan per like", "tayangan per like");
        }
    }

    private static void assertUnits(
            Context context,
            String singularKey,
            String pluralKey,
            String currentPrefix,
            String rangeJoiner,
            String singular,
            String plural
    ) {
        String range = "0 " + rangeJoiner + " 10";
        IntegerSetting setting = new IntegerSetting("unit_test_" + singularKey, 3).withRange(0, 10);
        NumberInputPreference preference = new NumberInputPreference(
                context, "Title", "Summary", setting, singularKey, pluralKey);

        // Three lines since 2026-09-08: the row's own wording, the range it accepts, and the
        // value. Twelve of these rows stated their range nowhere and pulled an out of range
        // number to the nearest end without a word.
        preference.setValue("0");
        assertEquals("Summary\n" + range + "\n" + currentPrefix + ": 0 " + plural,
                preference.getSummary().toString());
        preference.setValue("1");
        assertEquals("Summary\n" + range + "\n" + currentPrefix + ": 1 " + singular,
                preference.getSummary().toString());
        preference.setValue("2");
        assertEquals("Summary\n" + range + "\n" + currentPrefix + ": 2 " + plural,
                preference.getSummary().toString());
        assertEquals(Integer.valueOf(3), setting.get());
    }

    @Test
    public void aNumberOutsideTheRangeIsMovedAndSaidSo() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            IntegerSetting setting = new IntegerSetting("unit_test_clamp_toast", 30)
                    .withRange(0, 600);
            Row preference = new Row(context, setting);

            // Typing 5000 into a row that stops at 600 came back as "Current: 600" with no
            // explanation at all.
            ShadowToast.reset();
            preference.getEditText().setText("5000");
            preference.save();
            assertEquals("Kept to 600, the nearest value this row allows",
                    ShadowToast.getTextOfLatestToast());
            assertEquals("Summary\n0 to 600\nCurrent: 600 minutes",
                    preference.getSummary().toString());

            // Nothing is said for a number the row accepts.
            ShadowToast.reset();
            preference.getEditText().setText("45");
            preference.save();
            assertNull(ShadowToast.getTextOfLatestToast());
            assertEquals("Summary\n0 to 600\nCurrent: 45 minutes",
                    preference.getSummary().toString());

            // Nor for an empty box. That falls back to the stored setting, 30 here, rather than
            // to the smallest value: on several of these rows the smallest value means off, so
            // clearing the field would have quietly turned the feature off.
            ShadowToast.reset();
            preference.getEditText().setText("");
            preference.save();
            assertNull(ShadowToast.getTextOfLatestToast());
            assertEquals("Summary\n0 to 600\nCurrent: 30 minutes",
                    preference.getSummary().toString());
        }
    }

    @Test
    public void aRowCanCarryAFourthLineAndTheDailyBudgetsUseItForToday() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            IntegerSetting setting = new IntegerSetting("unit_test_today", 0).withRange(0, 2000);
            // Until this, the only place the day's progress showed was the one notice when the
            // budget ran out, so a row set to 200 said nothing about the 57 already watched.
            NumberInputPreference row = new NumberInputPreference(
                    context, "Daily video budget", "Summary", setting, "video", "videos") {
                @Override protected String extraSummaryLine() {
                    return setting.get() <= 0 ? null : "Today: 57 videos";
                }
            };

            // The stored setting is what says whether a budget is set, not the text in the box:
            // typing a number does not save it, and the page is built from what is saved.
            setting.save(200);
            row.setValue("200");
            assertEquals("Summary\n0 to 2000\nCurrent: 200 videos\nToday: 57 videos",
                    row.getSummary().toString());

            // Off, and there is no budget for the day to be measured against.
            setting.save(0);
            row.setValue("0");
            assertEquals("Summary\n0 to 2000\nCurrent: 0 videos", row.getSummary().toString());
        }
    }

    @Test
    public void aRowWithNothingToAddIsUnchanged() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            IntegerSetting setting = new IntegerSetting("unit_test_plain", 3).withRange(0, 10);
            NumberInputPreference row = new NumberInputPreference(
                    context, "Title", "Summary", setting, "video", "videos");

            row.setValue("4");
            assertEquals("Summary\n0 to 10\nCurrent: 4 videos", row.getSummary().toString());
        }
    }

    @Test
    public void aRowWhereZeroMeansOffSaysOffRatherThanNoneOfSomething() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            IntegerSetting setting = new IntegerSetting("unit_test_zero_off", 3).withRange(0, 10);
            NumberInputPreference preference = new NumberInputPreference(
                    context, "Title", "Summary", setting, "video", "videos");

            preference.setValue("0");
            assertEquals("Summary\n0 to 10\nCurrent: 0 videos", preference.getSummary().toString());

            // Seven rows say in their own wording that zero turns the setting off, and then read
            // "Current: 0 videos" underneath, which is a limit of none rather than no limit.
            preference.zeroMeansOff();
            assertEquals("Summary\n0 to 10\nCurrent: Off", preference.getSummary().toString());

            preference.setValue("4");
            assertEquals("Summary\n0 to 10\nCurrent: 4 videos", preference.getSummary().toString());
        }
    }
}
