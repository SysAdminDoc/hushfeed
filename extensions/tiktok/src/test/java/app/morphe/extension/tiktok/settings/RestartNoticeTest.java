package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import android.os.Bundle;
import android.preference.SwitchPreference;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RestartNoticeTest {
    @After public void tearDownStatus() {
        SettingsStatus.foldableSplitViewEnabled = false;
    }
    @Test public void preferenceChangesSaveImmediatelyWithoutOpeningRestartDialog() throws Exception {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Settings.FOLDABLE_SPLIT_VIEW.save(false);
            Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.save(600);
            SettingsStatus.foldableSplitViewEnabled = true;
            var fragment = new TikTokPreferenceFragment();
            Bundle arguments = new Bundle();
            arguments.putString("morphe_settings_section", "BEHAVIOR");
            fragment.setArguments(arguments);
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
            activity.getFragmentManager().executePendingTransactions();
            ((SwitchPreference) fragment.findPreference("foldable_split_view")).setChecked(true);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(Settings.FOLDABLE_SPLIT_VIEW.get());
            assertNull(ShadowDialog.getLatestDialog());
            assertTrue(ShadowToast.getTextOfLatestToast().contains("Restart"));
            ((NumberInputPreference) fragment.findPreference("foldable_split_view_min_width_dp")).setValue("720");
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(720, (int) Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.get());
            assertNull(ShadowDialog.getLatestDialog());
            assertFalse(activity.isFinishing());
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "restart-notice-settings.png");
        }
    }

    @Test public void arenamedEntryClassHidesTheRowInsteadOfTakingTheScreenDown() {
        // The only uncaught reflection in the tree. TikTok renames these classes every build, so
        // a miss threw out of the host's own settings screen and took the whole page with it.
        // The row is the only thing that should go missing.
        Utils.setContext(org.robolectric.RuntimeEnvironment.getApplication());
        app.morphe.extension.shared.diagnostics.HookStatus.clear();
        ShadowToast.reset();
        org.robolectric.util.ReflectionHelpers.setStaticField(
                TikTokActivityHook.class, "saidTheRowIsMissing", false);

        assertNull("a renamed entry class produced something to add",
                TikTokActivityHook.createSettingsEntry(
                        "does.not.Exist", "also.does.not.Exist"));

        assertTrue("the miss was not recorded for the diagnostic report",
                app.morphe.extension.shared.diagnostics.HookStatus.anyMissing());
        assertEquals("the reader was not told once", 1, ShadowToast.shownToastCount());

        // Every later open of the screen is silent: this runs each time the page is built.
        assertNull(TikTokActivityHook.createSettingsEntry("does.not.Exist", "also.does.not.Exist"));
        assertEquals("it said the same thing again", 1, ShadowToast.shownToastCount());
    }
}
