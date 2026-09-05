package app.morphe.extension.tiktok.spoof.region;

import android.os.Build;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.spoof.sim.SimPreset;
import app.morphe.extension.tiktok.spoof.sim.SimPresets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public final class RegionSpoof {
    private static final Set<String> COUNTRIES = new HashSet<>(Arrays.asList(Locale.getISOCountries()));
    private RegionSpoof() { }

    public static boolean validCountry(String value) {
        return value != null && COUNTRIES.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private static String selectedCountry() {
        if (Utils.getContext() == null || !Settings.SIM_SPOOF.get() || !Settings.REGION_SPOOF.get()) return "";
        String value = Settings.SIM_SPOOF_ISO.get();
        return validCountry(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    public static String country(String original) {
        String country = selectedCountry();
        return country.isEmpty() ? original : country;
    }

    public static String storeCountry(String original) {
        return Utils.getContext() != null && Settings.REGION_STORE_SPOOF.get() ? country(original) : original;
    }

    public static Locale locale(Locale original) {
        String country = selectedCountry();
        if (original == null || country.isEmpty() || country.equals(original.getCountry())) return original;
        try {
            return new Locale.Builder().setLocale(original).setRegion(country).build();
        } catch (java.util.IllformedLocaleException error) {
            // Legacy locales may have variants rejected by Builder. Keep their language and variant.
            return new Locale(original.getLanguage(), country, original.getVariant());
        }
    }

    public static TimeZone timeZone(TimeZone original) {
        String country = selectedCountry();
        if (original == null || country.isEmpty()) return original;
        for (SimPreset preset : SimPresets.PRESETS) {
            if (country.equalsIgnoreCase(preset.iso)) return TimeZone.getTimeZone(preset.timeZone);
        }
        if (Build.VERSION.SDK_INT >= 24) {
            String[] zones = android.icu.util.TimeZone.getAvailableIDs(country);
            if (zones.length > 0) return TimeZone.getTimeZone(zones[0]);
        }
        return original;
    }
}
