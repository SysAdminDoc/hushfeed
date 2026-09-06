package app.morphe.extension.tiktok.misc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.ExtensionPreferenceCategory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Whether a video's own Duet and Stitch setting is the one the app reads. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DuetStitchTest {
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    @Test public void theCreatorsChoiceStandsUntilTheSwitchIsOn() {
        try {
            Settings.ALLOW_DUET_AND_STITCH.save(false);
            assertFalse(DuetStitch.allow());
            Settings.ALLOW_DUET_AND_STITCH.save(true);
            assertTrue(DuetStitch.allow());
        } finally {
            Settings.ALLOW_DUET_AND_STITCH.save(false);
        }
    }

    @Test public void theSwitchIsReachable() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.duetStitchEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new ExtensionPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("allow_duet_and_stitch"));
        } finally {
            SettingsStatus.duetStitchEnabled = false;
        }
    }
}
