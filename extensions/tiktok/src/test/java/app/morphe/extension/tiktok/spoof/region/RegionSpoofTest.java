package app.morphe.extension.tiktok.spoof.region;

import static org.junit.Assert.*;
import android.os.Build;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.SimSpoofPreferenceCategory;
import app.morphe.extension.tiktok.spoof.sim.SimPreset;
import app.morphe.extension.tiktok.spoof.sim.SimPresets;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 28})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RegionSpoofTest {
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.SIM_SPOOF.save(true);
        Settings.REGION_SPOOF.save(true);
        Settings.REGION_STORE_SPOOF.save(false);
        Settings.SIM_SPOOF_ISO.save("jp");
    }
    @Test public void allPresetsSupplyRealCountryTimezonesWithoutChangingGlobalDefaults() {
        Locale systemLocale = Locale.getDefault();
        TimeZone systemZone = TimeZone.getDefault();
        for (SimPreset preset : SimPresets.PRESETS) {
            Settings.SIM_SPOOF_ISO.save(preset.iso);
            assertTrue(preset.iso, RegionSpoof.validCountry(preset.iso));
            TimeZone result = RegionSpoof.timeZone(systemZone);
            assertEquals(preset.timeZone, result.getID());
            if (Build.VERSION.SDK_INT >= 24) assertEquals(preset.iso.toUpperCase(Locale.ROOT),
                    android.icu.util.TimeZone.getRegion(result.getID()));
        }
        assertEquals(systemLocale, Locale.getDefault());
        assertEquals(systemZone, TimeZone.getDefault());
    }
    @Test public void localeKeepsLanguageScriptAndExtensionsAndFlagsAreIndependent() {
        Locale original = Locale.forLanguageTag("zh-Hant-TW-u-nu-hanidec");
        Locale changed = RegionSpoof.locale(original);
        assertEquals("JP", changed.getCountry());
        assertEquals(original.getLanguage(), changed.getLanguage());
        assertEquals(original.getScript(), changed.getScript());
        assertEquals(original.getUnicodeLocaleType("nu"), changed.getUnicodeLocaleType("nu"));
        assertEquals("JP", RegionSpoof.country("US"));
        assertEquals("US", RegionSpoof.storeCountry("US"));
        Settings.REGION_STORE_SPOOF.save(true);
        assertEquals("JP", RegionSpoof.storeCountry("US"));
        Settings.REGION_SPOOF.save(false);
        assertEquals("US", RegionSpoof.storeCountry("US"));
        assertSame(original, RegionSpoof.locale(original));
        Settings.REGION_SPOOF.save(true);
        Settings.SIM_SPOOF.save(false);
        assertSame(original, RegionSpoof.locale(original));
        assertEquals("US", RegionSpoof.storeCountry("US"));
    }
    @Test public void invalidCountryAndMissingValuesPreserveNativeInputs() {
        Locale original = Locale.CANADA_FRENCH;
        TimeZone zone = TimeZone.getTimeZone("America/Toronto");
        for (String value : new String[]{"", "zz", "USA", "123", "ja-JP"}) {
            Settings.SIM_SPOOF_ISO.save(value);
            assertFalse(value, RegionSpoof.validCountry(value));
            assertSame(original, RegionSpoof.locale(original));
            assertSame(zone, RegionSpoof.timeZone(zone));
            assertNull(RegionSpoof.country(null));
        }
        Settings.SIM_SPOOF_ISO.save(" jp ");
        assertEquals("JP", RegionSpoof.country(null));
        assertNull(RegionSpoof.locale(null));
        assertNull(RegionSpoof.timeZone(null));
        Settings.SIM_SPOOF_ISO.save("is");
        if (Build.VERSION.SDK_INT >= 24) assertEquals("IS", android.icu.util.TimeZone.getRegion(RegionSpoof.timeZone(zone).getID()));
        else assertSame(zone, RegionSpoof.timeZone(zone));
    }
    @Test public void legacyVariantKeepsScriptAndExtensionsWhenCountryChanges() {
        Locale original = Locale.forLanguageTag("zh-Hant-TW-u-nu-hanidec-x-custom-lvariant-WIN");
        Locale changed = RegionSpoof.locale(original);
        assertEquals("JP", changed.getCountry());
        assertEquals(original.getLanguage(), changed.getLanguage());
        assertEquals(original.getScript(), changed.getScript());
        for (Character key : original.getExtensionKeys()) {
            assertEquals(original.getExtension(key), changed.getExtension(key));
        }
    }
    @Test public void countryCodesRejectUnicodeCaseExpansion() {
        Locale original = Locale.CANADA_FRENCH;
        TimeZone zone = TimeZone.getTimeZone("America/Toronto");
        for (String value : new String[]{"ß", "ſs", "ıS", "ｊｐ"}) {
            Settings.SIM_SPOOF_ISO.save(value);
            assertFalse(value, RegionSpoof.validCountry(value));
            assertSame(original, RegionSpoof.locale(original));
            assertSame(zone, RegionSpoof.timeZone(zone));
            assertEquals("CA", RegionSpoof.country("CA"));
        }
        assertTrue(RegionSpoof.validCountry(" ss "));
    }
    @Test public void regionControlsAreReachableAndRejectInvalidCountryInput() throws Exception {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.interaction.GestureActionsTest.TestActivity.class).setup()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.simSpoofEnabled = true;
            SettingsStatus.regionSpoofEnabled = true;
            var screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new SimSpoofPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("region_spoof"));
            assertNotNull(screen.findPreference("region_store_spoof"));
            var country = screen.findPreference("simspoof_iso");
            assertFalse(country.getOnPreferenceChangeListener().onPreferenceChange(country, "zz"));
            assertTrue(country.getOnPreferenceChangeListener().onPreferenceChange(country, "de"));
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "region-settings.png");
        }
    }
}
