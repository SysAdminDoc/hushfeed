package app.morphe.extension.tiktok.featuregatelab;

import static org.junit.Assert.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.FeatureGateRecorderPreference;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeatureGateRecorderTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.featureGateRecorderEnabled = true;
        FeatureGateLearnMode.cancel();
    }
    @Test public void recordsRepeatedReadsNewKeysAndChangedValuesWithoutOverriding() throws Exception {
        assertTrue(FeatureGateLabRuntime.overrideBoolean("known", true));
        FeatureGateLearnMode.begin();
        assertTrue(FeatureGateLabRuntime.overrideBoolean("known", true));
        assertFalse(FeatureGateLabRuntime.overrideBoolean("known", false));
        assertEquals(7, FeatureGateLabRuntime.overrideInt("new_key", 7));
        assertEquals(2.5f, (Float) FeatureGateLabRuntime.overridePlayerValue("speed", Float.class, 2.5f), 0f);
        JSONObject report = new JSONObject(FeatureGateLearnMode.stopAndBuildReport());
        assertEquals(3, report.getInt("gate_count"));
        assertEquals(2, report.getInt("new_count"));
        assertEquals(1, report.getInt("changed_count"));
        JSONObject known = report.getJSONArray("gates").getJSONObject(0);
        assertEquals("changed", known.getString("state"));
        assertEquals(2, known.getInt("calls"));
        assertEquals("true", known.getString("before"));
        assertEquals("false", known.getString("after"));
        String stopped = FeatureGateLearnMode.lastReport();
        FeatureGateLabRuntime.overrideInt("after_stop", 9);
        assertEquals(stopped, FeatureGateLearnMode.stopAndBuildReport());
        FeatureGateLearnMode.begin();
        FeatureGateLabRuntime.overrideBoolean("known", false);
        assertEquals("read", new JSONObject(FeatureGateLearnMode.stopAndBuildReport())
                .getJSONArray("gates").getJSONObject(0).getString("state"));
    }
    @Test public void deduplicatedSettingsManagerReadsStillAppearDuringRecording() throws Exception {
        SettingsManagerObservationRecorder.observeWithDefault("structured", String.class, "", "before");
        FeatureGateLearnMode.begin();
        SettingsManagerObservationRecorder.observeWithDefault("structured", String.class, "", "after");
        SettingsManagerObservationRecorder.observeWithoutDefault("structured", String.class, "after");
        JSONObject report = new JSONObject(FeatureGateLearnMode.stopAndBuildReport());
        assertEquals(2, report.getInt("gate_count"));
        assertEquals(1, report.getInt("changed_count"));
    }
    @Test public void recordingRemainsBoundedAndReportsDroppedReads() throws Exception {
        FeatureGateLearnMode.begin();
        for (int i = 0; i < 2050; i++) FeatureGateLearnMode.observe("test", "gate_" + i, "INT", i);
        JSONObject report = new JSONObject(FeatureGateLearnMode.stopAndBuildReport());
        assertEquals(2048, report.getInt("gate_count"));
        assertEquals(2, report.getInt("dropped_reads"));
        FeatureGateLearnMode.begin();
        FeatureGateLearnMode.observe("test", "new_after_full_baseline", "INT", 1);
        assertEquals(1, new JSONObject(FeatureGateLearnMode.stopAndBuildReport()).getInt("gate_count"));
    }
    @Test public void controlStartsImmediatelyAndStopShowsTheReport() throws Exception {
        try (var owner = Robolectric.buildActivity(
                app.morphe.extension.tiktok.interaction.GestureActionsTest.TestActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            FeatureGateRecorderPreference control = new FeatureGateRecorderPreference(activity);
            control.getOnPreferenceClickListener().onPreferenceClick(control);
            assertTrue(FeatureGateLearnMode.isRecording());
            assertNull(ShadowAlertDialog.getLatestAlertDialog());
            FeatureGateLabRuntime.overrideBoolean("circle_search_block", false);
            control.getOnPreferenceClickListener().onPreferenceClick(control);
            assertFalse(FeatureGateLearnMode.isRecording());
            var dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            assertTrue(FeatureGateLearnMode.lastReport().contains("circle_search_block"));
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(dialog.getWindow().getDecorView(), "gate-recording.png");
            dialog.dismiss();
        }
    }
}
