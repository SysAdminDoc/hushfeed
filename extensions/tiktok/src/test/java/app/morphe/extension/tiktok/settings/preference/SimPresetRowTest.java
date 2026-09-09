package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What the SIM preset row says underneath itself.
 *
 * <p>Three answers: the preset it recognises, nothing chosen at all, and details that match no
 * preset. The row is 313 lines and none of them were reached by a test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class SimPresetRowTest {
    public static final class TestActivity extends PreferenceActivity {}

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.SIM_SPOOF_ISO.resetToDefault();
        Settings.SIMSPOOF_MCCMNC.resetToDefault();
        Settings.SIMSPOOF_OP_NAME.resetToDefault();
    }

    @Test
    public void theRowNamesThePresetItRecognises() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            SimPresetPreference row = build(controller.get());

            // The defaults are a real preset, so the row should name it rather than call it custom.
            row.refreshSummary(
                    Settings.SIM_SPOOF_ISO.defaultValue,
                    Settings.SIMSPOOF_MCCMNC.defaultValue,
                    Settings.SIMSPOOF_OP_NAME.defaultValue);
            String named = row.getSummary().toString();
            assertEquals("(" + Settings.SIMSPOOF_MCCMNC.defaultValue + ")",
                    named.substring(named.lastIndexOf('(')));
        }
    }

    @Test
    public void emptyDetailsReadAsNothingChosenAndAMixtureReadsAsCustom() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            SimPresetPreference row = build(controller.get());

            row.refreshSummary("", "", "");
            assertEquals("No preset selected", row.getSummary().toString());

            row.refreshSummary("us", "310260", "Not a real operator");
            assertEquals("Custom SIM details", row.getSummary().toString());
        }
    }

    /**
     * The dialog behind the row, on a German phone.
     *
     * <p>Its title, its helper sentence and the search box were built with plain English
     * strings, so they went out in English however many tables held them: two of them had a
     * German row already and simply never asked for it.
     */
    @Test
    @Config(sdk = 28, qualifiers = "de-rDE")
    public void thePresetDialogReadsGermanOnAGermanPhone() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            SimPresetPreference row = build(controller.get());

            java.lang.reflect.Method show =
                    SimPresetPreference.class.getDeclaredMethod("showPresetDialog");
            show.setAccessible(true);
            show.invoke(row);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull("the preset dialog did not open", dialog);
            List<String> shown = new ArrayList<>();
            collectText(dialog.getWindow().getDecorView(), shown);

            assertTrue("the dialog title is still English: " + shown,
                    shown.contains("SIM-Landesvorlage") || shown.contains(germanFor(
                            "SIM country preset")));
            assertTrue("the helper sentence is still English: " + shown,
                    shown.contains(germanFor("Choose a preset to fill the SIM details.")));
            assertTrue("the search box hint is still English: " + shown,
                    shown.contains(germanFor("Search countries or operators")));
        }
    }

    /** What the shipped table says, so the test does not repeat the translation. */
    private static String germanFor(String english) {
        String translated = app.morphe.extension.tiktok.settings.L10n.t(
                RuntimeEnvironment.getApplication().createConfigurationContext(
                        germanConfiguration()), english);
        assertNotEquals("no German row for: " + english, english, translated);
        return translated;
    }

    private static android.content.res.Configuration germanConfiguration() {
        android.content.res.Configuration german = new android.content.res.Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration());
        german.setLocale(java.util.Locale.GERMANY);
        return german;
    }

    private static void collectText(android.view.View view, List<String> found) {
        if (view instanceof android.widget.TextView) {
            android.widget.TextView label = (android.widget.TextView) view;
            if (label.getText() != null && label.getText().length() > 0) {
                found.add(label.getText().toString());
            }
            if (label.getHint() != null && label.getHint().length() > 0) {
                found.add(label.getHint().toString());
            }
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectText(group.getChildAt(index), found);
            }
        }
    }

    private static SimPresetPreference build(Context context) {
        return new SimPresetPreference(
                context,
                new InputTextPreference(context, "ISO", "", Settings.SIM_SPOOF_ISO),
                new InputTextPreference(context, "MCC/MNC", "", Settings.SIMSPOOF_MCCMNC),
                new InputTextPreference(context, "Operator", "", Settings.SIMSPOOF_OP_NAME));
    }
}
