/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.Setting;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What an integer setting holds when nothing has saved anything yet.
 *
 * <p>Every other test writes a value before reading one, so none of them would notice a
 * setting that comes out of its own constructor wrong. That is the state the app is in at
 * every launch, and it is the state the reader's saved value has to survive.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingRangeTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        // Touching one setting builds them all.
        Settings.REGION_SPOOF.get();
    }

    @Test public void everySettingStartsAtItsOwnDefault() {
        int checked = 0;
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            // A setting the app has already written has a value of its own to hold, and the
            // launch timestamp is written before any test can look at it.
            if (Setting.preferences.preferences.contains(setting.key)) continue;
            checked++;
            assertEquals(setting.key + " did not start at its default",
                    setting.defaultValue, setting.get());
        }
        assertTrue("nothing was checked", checked > 100);
    }

    @Test public void aSettingWithARangeStartsInsideIt() {
        int ranged = 0;
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (!(setting instanceof IntegerSetting)) continue;
            IntegerSetting number = (IntegerSetting) setting;
            if (!number.hasRange()) continue;
            ranged++;
            assertTrue(number.key + " starts at " + number.get() + ", below " + number.minimum(),
                    number.get() >= number.minimum());
            assertTrue(number.key + " starts at " + number.get() + ", above " + number.maximum(),
                    number.get() <= number.maximum());
        }
        assertTrue("no ranged settings were found to check", ranged >= 7);
    }

    @Test public void aStoredValueOutsideTheRangeIsBroughtBackInside() {
        // The value on disk was written before the range existed, or by a backup file.
        Setting.preferences.saveString("edge_seek_seconds", "100000");
        Setting.preferences.saveString("caption_text_size", "-40");

        // A fresh setting reads what is on disk and then has its range declared, which is
        // the order the real class initializer runs in.
        IntegerSetting planted = new IntegerSetting("edge_seek_seconds", 5).withRange(1, 60);
        assertEquals(60, (int) planted.get());
        IntegerSetting negative = new IntegerSetting("caption_text_size", 0).withRange(0, 48);
        assertEquals(0, (int) negative.get());
    }

    @Test public void aSettingWithNoRangeKeepsWhateverItIsGiven() {
        IntegerSetting free = new IntegerSetting("morphe_test_unranged", 7);
        assertEquals(7, (int) free.get());
        free.save(999_999);
        assertEquals(999_999, (int) free.get());
    }
}
