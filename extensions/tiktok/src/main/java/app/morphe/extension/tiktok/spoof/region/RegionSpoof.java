/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
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

    /*
     * The patch sends every Locale.getDefault() and TimeZone.getDefault() in the app through
     * here, on every thread, and those are asked for all the time: each String.format, each
     * date and each lower-casing. Each answer used to compile a regex, build a Locale and scan
     * the presets (or ICU's whole zone table for a custom country). The last answer is kept
     * with what it was worked out from, so a changed setting still takes effect at once.
     */
    private static volatile String[] lastCountry = {"", ""};
    private static volatile LocaleAnswer lastLocale;
    private static volatile Object[] lastZone = {"", null};

    private static final class LocaleAnswer {
        final Locale original;
        final String country;
        final Locale answer;

        LocaleAnswer(Locale original, String country, Locale answer) {
            this.original = original;
            this.country = country;
            this.answer = answer;
        }
    }

    private RegionSpoof() { }

    public static boolean validCountry(String value) {
        if (value == null) return false;
        String code = value.trim();
        if (code.length() != 2) return false;
        for (int index = 0; index < 2; index++) {
            char letter = code.charAt(index);
            if ((letter < 'A' || letter > 'Z') && (letter < 'a' || letter > 'z')) return false;
        }
        return COUNTRIES.contains(code.toUpperCase(Locale.ROOT));
    }

    private static String selectedCountry() {
        if (Utils.getContext() == null || !Settings.SIM_SPOOF.get() || !Settings.REGION_SPOOF.get()) return "";
        String value = Settings.SIM_SPOOF_ISO.get();
        if (value == null) return "";
        String[] last = lastCountry;
        if (value.equals(last[0])) return last[1];
        String country = validCountry(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
        lastCountry = new String[]{value, country};
        return country;
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
        LocaleAnswer last = lastLocale;
        if (last != null && last.country.equals(country)
                && (last.original == original || last.original.equals(original))) {
            return last.answer;
        }
        Locale answer = withCountry(original, country);
        lastLocale = new LocaleAnswer(original, country, answer);
        return answer;
    }

    private static Locale withCountry(Locale original, String country) {
        try {
            return new Locale.Builder().setLocale(original).setRegion(country).build();
        } catch (java.util.IllformedLocaleException error) {
            // Legacy variants can be invalid BCP 47. Keep the other locale fields intact.
            try {
                Locale.Builder builder = new Locale.Builder().setLanguage(original.getLanguage())
                        .setScript(original.getScript()).setRegion(country);
                for (Character key : original.getExtensionKeys()) {
                    builder.setExtension(key, original.getExtension(key));
                }
                return builder.build();
            } catch (java.util.IllformedLocaleException invalidLanguage) {
                return original;
            }
        }
    }

    public static TimeZone timeZone(TimeZone original) {
        String country = selectedCountry();
        if (original == null || country.isEmpty()) return original;
        Object[] last = lastZone;
        TimeZone zone;
        if (country.equals(last[0])) {
            zone = (TimeZone) last[1];
        } else {
            zone = zoneOf(country);
            lastZone = new Object[]{country, zone};
        }
        // A copy each time, as TimeZone.getDefault() gives: a TimeZone can be changed by whoever
        // holds it, and the kept one is shared by every caller.
        return zone == null ? original : (TimeZone) zone.clone();
    }

    private static TimeZone zoneOf(String country) {
        for (SimPreset preset : SimPresets.PRESETS) {
            if (country.equalsIgnoreCase(preset.iso)) return TimeZone.getTimeZone(preset.timeZone);
        }
        if (Build.VERSION.SDK_INT >= 24) {
            String[] zones = android.icu.util.TimeZone.getAvailableIDs(country);
            if (zones.length > 0) return TimeZone.getTimeZone(zones[0]);
        }
        return null;
    }
}
