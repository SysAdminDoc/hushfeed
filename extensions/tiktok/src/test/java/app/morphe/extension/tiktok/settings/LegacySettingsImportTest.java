package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LegacySettingsImportTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.REGION_SPOOF.get();
        for (Setting<?> setting : Setting.allLoadedSettings()) setting.resetToDefault();
    }

    @Test public void fullJsonAndLegacyFragmentsBothImport() {
        Setting.importFromJSON(null, "{\"region_spoof\":true,\"max_video_seconds\":37}");
        assertTrue(Settings.REGION_SPOOF.get());
        assertEquals(37, (int) Settings.MAX_VIDEO_SECONDS.get());
        Setting.importFromJSON(null, "\"region_spoof\":false,\"max_video_seconds\":18");
        assertFalse(Settings.REGION_SPOOF.get());
        assertEquals(18, (int) Settings.MAX_VIDEO_SECONDS.get());
    }

    @Test public void anInvalidLaterValueCannotSaveEarlierValuesOrResetOmittedOnes() {
        Settings.BLOCKED_SOUND_NAMES.save("keep this");
        Setting.importFromJSON(null, "\"region_spoof\":true,\"max_video_seconds\":\"not a number\"");
        assertFalse(Settings.REGION_SPOOF.get());
        assertEquals("keep this", Settings.BLOCKED_SOUND_NAMES.get());
        assertEquals(0, (int) Settings.MAX_VIDEO_SECONDS.get());
    }

    @Test public void numericOverflowFractionsAndTrailingJunkDoNotChangeValues() {
        for (String value : new String[]{"1.25", "2147483648", "true", "null"}) {
            Settings.MAX_VIDEO_SECONDS.save(29);
            Setting.importFromJSON(null, "\"region_spoof\":true,\"max_video_seconds\":" + value);
            assertEquals(29, (int) Settings.MAX_VIDEO_SECONDS.get());
            assertFalse(Settings.REGION_SPOOF.get());
        }
        Setting.importFromJSON(null, "{\"region_spoof\":true} garbage");
        assertFalse(Settings.REGION_SPOOF.get());
    }

    @Test public void exportRoundTripRetainsValuesAndBlankImportResetsOnlyPortableSettings() {
        Settings.MAX_VIDEO_SECONDS.save(64);
        Settings.BLOCKED_CREATORS.save("動画");
        BaseSettings.FIRST_TIME_APP_LAUNCHED.save(123456L);
        String exported = Setting.exportToJson(null);
        Settings.MAX_VIDEO_SECONDS.save(0);
        Settings.BLOCKED_CREATORS.save("");
        Setting.importFromJSON(null, exported);
        assertEquals(64, (int) Settings.MAX_VIDEO_SECONDS.get());
        assertEquals("動画", Settings.BLOCKED_CREATORS.get());
        Setting.importFromJSON(null, "");
        assertEquals(0, (int) Settings.MAX_VIDEO_SECONDS.get());
        assertEquals("", Settings.BLOCKED_CREATORS.get());
        assertEquals(123456L, (long) BaseSettings.FIRST_TIME_APP_LAUNCHED.get());
    }
}
