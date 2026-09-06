package app.morphe.extension.tiktok.misc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

/** What the window is told to ask the screen for. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RefreshRateTest {
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    @Test public void theAskIsPassedThroughUntilTheSwitchStopsIt() {
        try {
            // Off: whatever TikTok worked out from the video goes through untouched.
            Settings.UNCAP_REFRESH_RATE.save(false);
            for (float rate : new float[]{24f, 25f, 30f, 60f, 120f}) {
                assertEquals(rate, RefreshRate.preferredRefreshRate(rate), 0.001f);
            }

            // On: zero, which is what Android reads as no preference from the app.
            Settings.UNCAP_REFRESH_RATE.save(true);
            for (float rate : new float[]{24f, 30f, 60f, 120f}) {
                assertEquals(0f, RefreshRate.preferredRefreshRate(rate), 0.001f);
            }
        } finally {
            Settings.UNCAP_REFRESH_RATE.save(false);
        }
    }

    @Test public void theSwitchIsReachable() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.refreshRateEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new ExtensionPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("uncap_refresh_rate"));
        } finally {
            SettingsStatus.refreshRateEnabled = false;
        }
    }
}
