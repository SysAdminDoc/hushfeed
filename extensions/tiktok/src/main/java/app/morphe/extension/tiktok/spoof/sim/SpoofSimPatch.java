/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/spoof/sim/SpoofSimPatch.java
 */

package app.morphe.extension.tiktok.spoof.sim;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("unused")
public class SpoofSimPatch {
    private static boolean isContextNotSet(String fieldSpoofed) {
        if (Utils.getContext() != null) {
            return false;
        }

        Logger.printException(() -> "Context is not yet set, cannot spoof: " + fieldSpoofed, null);
        return true;
    }

    public static String getCountryIso(String value) {
        if (isContextNotSet("countryIso")) return value;

        if (Settings.SIM_SPOOF.get()) {
            String iso = Settings.SIM_SPOOF_ISO.get();
            // The dialog checks this; a restored backup does not go through the dialog, and
            // TikTok reads the value straight into code that expects a country code.
            if (!app.morphe.extension.tiktok.spoof.region.RegionSpoof.validCountry(iso)) {
                Logger.printInfo(() -> "Ignoring an unusable SIM country: " + iso);
                return value;
            }
            Logger.printDebug(() -> "Spoofing countryIso from: " + value + " to: " + iso);
            return iso;
        }

        return value;
    }

    public static String getOperator(String value) {
        if (isContextNotSet("MCC-MNC")) return value;

        if (Settings.SIM_SPOOF.get()) {
            String mccMnc = Settings.SIMSPOOF_MCCMNC.get();
            if (!validMccMnc(mccMnc)) {
                Logger.printInfo(() -> "Ignoring an unusable MCC/MNC: " + mccMnc);
                return value;
            }
            Logger.printDebug(() -> "Spoofing sim MCC-MNC from: " + value + " to: " + mccMnc);
            return mccMnc;
        }

        return value;
    }

    public static String getOperatorName(String value) {
        if (isContextNotSet("operatorName")) return value;

        if (Settings.SIM_SPOOF.get()) {
            String operator = Settings.SIMSPOOF_OP_NAME.get();
            Logger.printDebug(() -> "Spoofing sim operatorName from: " + value + " to: " + operator);
            return operator;
        }

        return value;
    }

    public static String getMcc(String value) {
        return getMccMncPart(value, true);
    }

    public static String getMnc(String value) {
        return getMccMncPart(value, false);
    }

    /**
     * Five or six digits, which is what a network code is. Callers inside TikTok read the
     * first three as the country and parse them as a number, so anything else is refused
     * rather than handed on: a value typed before this check existed, or restored from a
     * backup, would otherwise reach them as letters.
     */
    private static boolean validMccMnc(String value) {
        if (value == null || value.length() < 5 || value.length() > 6) return false;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') return false;
        }
        return true;
    }

    private static String getMccMncPart(String value, boolean mcc) {
        if (isContextNotSet(mcc ? "cellMcc" : "cellMnc")) return value;
        if (!Settings.SIM_SPOOF.get()) return value;

        String combined = Settings.SIMSPOOF_MCCMNC.get();
        if (!validMccMnc(combined)) return value;

        String replacement = mcc ? combined.substring(0, 3) : combined.substring(3);
        Logger.printDebug(() -> "Spoofing " + (mcc ? "cell MCC" : "cell MNC")
                + " from: " + value + " to: " + replacement);
        return replacement;
    }
}
