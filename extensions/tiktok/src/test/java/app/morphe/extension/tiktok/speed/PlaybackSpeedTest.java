package app.morphe.extension.tiktok.speed;

import static org.junit.Assert.*;
import android.os.Bundle;
import android.preference.SwitchPreference;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import java.util.List;
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
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlaybackSpeedTest {
    private static Aweme video(String id) { return new Aweme() { @Override public String getAid() { return id; } }; }
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.DEFAULT_SPEED_ENABLED.save(true);
        Settings.DEFAULT_SPEED.save("1.5");
        Settings.CUSTOM_SPEEDS.save("");
        Settings.REMEMBERED_SPEED.save(1f);
        PlaybackSpeedPatch.beginVideo(null);
    }
    @After public void tearDown() {
        SettingsStatus.playbackSpeedEnabled = false;
        SettingsStatus.playbackQualityEnabled = false;
    }
    @Test public void manualChoiceSurvivesRepeatedFramesUntilTheVideoChanges() {
        Aweme a = video("a"), b = video("b");
        assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(a), 0);
        PlaybackSpeedPatch.onSelection(2.5f, a, "feed", "long_press");
        assertEquals(2.5f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("a")), 0);
        assertEquals(2.5f, PlaybackSpeedPatch.preserveTransitionSpeed(1f), 0);
        assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(b), 0);
        assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(a), 0);
    }
    @Test public void temporarySpeedAndInvalidInputsDoNotBecomeManualChoices() {
        Aweme a = video("a");
        PlaybackSpeedPatch.getPlaybackSpeedForVideo(a);
        PlaybackSpeedPatch.onSelection(2f, a, "feed", "gesture_hold");
        assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeed(), 0);
        for (float speed : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0, -1}) {
            PlaybackSpeedPatch.onSelection(speed, a, "feed", "long_press");
            assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeed(), 0);
        }
        PlaybackSpeedPatch.onSelection(2f, a, "feed", "swipe_up_lock_persist");
        assertEquals(2f, PlaybackSpeedPatch.getPlaybackSpeed(), 0);
        PlaybackSpeedPatch.beginVideo(null);
        assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeed(), 0);
    }
    @Test public void disabledDefaultRetainsRememberedSpeedAndNativeTemporaryRequests() {
        Settings.DEFAULT_SPEED_ENABLED.save(false);
        PlaybackSpeedPatch.onSelection(2.5f, video("a"), "feed", "click_share_button");
        assertEquals(2.5f, PlaybackSpeedPatch.getPlaybackSpeedForVideo(video("b")), 0);
        assertEquals(2.5f, PlaybackSpeedPatch.preserveTransitionSpeed(1f), 0);
        assertEquals(2f, PlaybackSpeedPatch.preserveTransitionSpeed(2f), 0);
    }
    @Test public void malformedDefaultUsesSafeDefaultAndMenuFailuresKeepNativeList() {
        var nativeList = List.of(0.5f, 1f, 1.5f, 2f);
        assertSame(nativeList, PlaybackSpeedPatch.menuSpeeds(nativeList));
        for (String value : new String[]{"NaN", "Infinity", "0", "-1", "4", "invalid"}) {
            Settings.DEFAULT_SPEED.save(value);
            assertEquals(1.5f, PlaybackSpeedPatch.getPlaybackSpeed(), 0);
            Settings.CUSTOM_SPEEDS.save(value);
            assertSame(nativeList, PlaybackSpeedPatch.menuSpeeds(nativeList));
        }
        Settings.CUSTOM_SPEEDS.save("0.5, 1, 1.5, 2, 2.5, 3, 2.5");
        assertEquals(List.of(0.5f, 1f, 1.5f, 2f, 2.5f, 3f), PlaybackSpeedPatch.menuSpeeds(nativeList));
        assertEquals(4, nativeList.size());
        for (String value : new String[]{"1,", ",1", "1,2,3,1,2,3,1,2,3"}) {
            assertThrows(IllegalArgumentException.class, () -> PlaybackSpeedPatch.parseMenuSpeeds(value));
        }
    }
    @Test public void speedOnlyPatchExposesControlsAndSavesFromActualSettingsScreen() throws Exception {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            SettingsStatus.playbackSpeedEnabled = true;
            SettingsStatus.playbackQualityEnabled = false;
            var fragment = new TikTokPreferenceFragment();
            Bundle arguments = new Bundle();
            arguments.putString("morphe_settings_section", "PLAYBACK");
            fragment.setArguments(arguments);
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
            activity.getFragmentManager().executePendingTransactions();
            var toggle = (SwitchPreference) fragment.findPreference("default_speed_enabled");
            toggle.setChecked(false);
            toggle.setChecked(true);
            var choices = fragment.findPreference("custom_speeds");
            assertFalse(choices.getOnPreferenceChangeListener().onPreferenceChange(choices, "NaN"));
            assertTrue(choices.getOnPreferenceChangeListener().onPreferenceChange(choices, "1, 1.5, 2.5, 3"));
            ((android.preference.EditTextPreference) choices).setText("1, 1.5, 2.5, 3");
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(Settings.DEFAULT_SPEED_ENABLED.get());
            assertEquals("1, 1.5, 2.5, 3", Settings.CUSTOM_SPEEDS.get());
            assertNotNull(fragment.findPreference("default_speed"));
            assertNull(fragment.findPreference("playback_quality"));
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "playback-speed-settings.png");
        }
    }
}
