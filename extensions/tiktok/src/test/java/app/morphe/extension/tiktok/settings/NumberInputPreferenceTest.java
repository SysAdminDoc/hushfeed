package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Singular and plural unit labels on numeric settings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NumberInputPreferenceTest {
    public static final class TestActivity extends PreferenceActivity {}

    @After
    public void restoreContext() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Test
    public void englishUsesTheRightFormForZeroOneAndMany() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Current", "second", "seconds");
            assertUnits(context, "day", "days", "Current", "day", "days");
            assertUnits(context, "view per like", "views per like", "Current",
                    "view per like", "views per like");
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanTranslatesEachForm() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Aktuell", "Sekunde", "Sekunden");
            assertUnits(context, "day", "days", "Aktuell", "Tag", "Tage");
            assertUnits(context, "view per like", "views per like", "Aktuell",
                    "Aufruf pro Like", "Aufrufe pro Like");
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "in-rID")
    public void indonesianTranslatesEachForm() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Context context = controller.get();
            assertUnits(context, "second", "seconds", "Saat ini", "detik", "detik");
            assertUnits(context, "day", "days", "Saat ini", "hari", "hari");
            assertUnits(context, "view per like", "views per like", "Saat ini",
                    "tayangan per like", "tayangan per like");
        }
    }

    private static void assertUnits(
            Context context,
            String singularKey,
            String pluralKey,
            String currentPrefix,
            String singular,
            String plural
    ) {
        IntegerSetting setting = new IntegerSetting("unit_test_" + singularKey, 3).withRange(0, 10);
        NumberInputPreference preference = new NumberInputPreference(
                context, "Title", "Summary", setting, singularKey, pluralKey);

        preference.setValue("0");
        assertEquals("Summary\n" + currentPrefix + ": 0 " + plural,
                preference.getSummary().toString());
        preference.setValue("1");
        assertEquals("Summary\n" + currentPrefix + ": 1 " + singular,
                preference.getSummary().toString());
        preference.setValue("2");
        assertEquals("Summary\n" + currentPrefix + ": 2 " + plural,
                preference.getSummary().toString());
        assertEquals(Integer.valueOf(3), setting.get());
    }
}
