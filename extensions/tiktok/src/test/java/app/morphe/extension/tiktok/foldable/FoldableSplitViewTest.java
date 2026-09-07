package app.morphe.extension.tiktok.foldable;

import static org.junit.Assert.*;
import android.content.res.Configuration;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.ExtensionPreferenceCategory;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 30}, qualifiers = "w700dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FoldableSplitViewTest {
    public static class TestActivity extends android.preference.PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
        boolean multiWindow, pictureInPicture;
        @Override public boolean isInMultiWindowMode() { return multiWindow; }
        @Override public boolean isInPictureInPictureMode() { return pictureInPicture; }
    }
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Utils.setActivity(null);
        Settings.FOLDABLE_SPLIT_VIEW.save(false);
        Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.save(600);
    }
    @After public void tearDown() {
        SettingsStatus.foldableSplitViewEnabled = false;
    }
    @Test public void explicitWidthControlsBothSidesOfThresholdAndRespectsWindowModes() {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup()) {
            TestActivity activity = owner.get();
            Configuration configuration = new Configuration(activity.getResources().getConfiguration());
            configuration.screenWidthDp = 700;
            assertFalse(FoldableSplitView.shouldForce(activity, configuration));
            Settings.FOLDABLE_SPLIT_VIEW.save(true);
            assertTrue(FoldableSplitView.shouldForce(activity, configuration));
            configuration.screenWidthDp = 599;
            assertFalse(FoldableSplitView.shouldForce(activity, configuration));
            configuration.screenWidthDp = 600;
            assertTrue(FoldableSplitView.shouldForce(activity, configuration));
            activity.multiWindow = true;
            assertFalse(FoldableSplitView.shouldForce(activity, configuration));
            activity.multiWindow = false;
            activity.pictureInPicture = true;
            assertFalse(FoldableSplitView.shouldForce(activity, configuration));
            activity.pictureInPicture = false;
            activity.finish();
            assertFalse(FoldableSplitView.shouldForce(activity, configuration));
        }
    }
    @Test public void containerUsesCurrentActivityAndNeverRecreatesIt() {
        Settings.FOLDABLE_SPLIT_VIEW.save(true);
        assertFalse(FoldableSplitView.shouldForceContainer());
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = owner.get();
            Utils.setActivity(activity);
            assertTrue(FoldableSplitView.shouldForceContainer());
            Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.save(800);
            assertFalse(FoldableSplitView.shouldForceContainer());
            Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.save(600);
            activity.multiWindow = true;
            assertFalse(FoldableSplitView.shouldForceContainer());
            assertFalse(activity.isChangingConfigurations());
        }
        Utils.setActivity(null);
    }
    @Test public void controlsAreReachableAndWidthInputIsBounded() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.foldableSplitViewEnabled = true;
            var screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new ExtensionPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("foldable_split_view"));
            var width = (app.morphe.extension.tiktok.settings.preference.NumberInputPreference) screen.findPreference("foldable_split_view_min_width_dp");
            width.setValue("-1");
            assertEquals("320", width.getValue());
            width.setValue("600");
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "foldable-settings.png");
        }
    }
}
