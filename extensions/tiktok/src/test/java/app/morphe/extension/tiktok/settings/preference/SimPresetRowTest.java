package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

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

    private static SimPresetPreference build(Context context) {
        return new SimPresetPreference(
                context,
                new InputTextPreference(context, "ISO", "", Settings.SIM_SPOOF_ISO),
                new InputTextPreference(context, "MCC/MNC", "", Settings.SIMSPOOF_MCCMNC),
                new InputTextPreference(context, "Operator", "", Settings.SIMSPOOF_OP_NAME));
    }
}
