/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/SimSpoofPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.SimPresetPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.spoof.sim.SpoofSimPatch;
import app.morphe.extension.tiktok.spoof.region.RegionSpoof;
import app.morphe.extension.tiktok.settings.L10n;

@SuppressWarnings("deprecation")
public class SimSpoofPreferenceCategory extends ConditionalPreferenceCategory {
    public SimSpoofPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Region settings");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.simSpoofEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(
                context,
                "Override SIM details",
                "Use the selected country and operator values. Restart TikTok after changing region settings.",
                Settings.SIM_SPOOF
        ));
        if (SettingsStatus.regionSpoofEnabled) {
            addPreference(new TogglePreference(context, "Match locale and timezone to country",
                    "Also override TikTok's region getters. Keeps your interface language. Requires Override SIM details and a restart; IP address and account rules still apply.", Settings.REGION_SPOOF));
            addPreference(new TogglePreference(context, "Override store region (experimental)",
                    "Use the preset for TikTok's account and store region getters too. May affect search. Requires the locale option and a restart.", Settings.REGION_STORE_SPOOF));
        }
        InputTextPreference countryIsoPreference = new InputTextPreference(
                context,
                "Country ISO", "Two letters, like us, gb or jp.",
                Settings.SIM_SPOOF_ISO
        ).withCheck(value -> RegionSpoof.validCountry(value)
                ? null : L10n.t("Enter a valid two-letter country code"));
        InputTextPreference mccMncPreference = new InputTextPreference(
                context,
                "Operator MCC/MNC", "Your operator's numeric code, like 310260.",
                Settings.SIMSPOOF_MCCMNC
        ).withCheck(value -> SpoofSimPatch.validMccMnc(value.trim()) ? null
                : L10n.t("An operator code is five or six digits, like 310260"));
        InputTextPreference operatorNamePreference = new InputTextPreference(
                context,
                "Operator name", "Your operator's name, like T-Mobile.",
                Settings.SIMSPOOF_OP_NAME
        );
        SimPresetPreference simPresetPreference = new SimPresetPreference(
                context,
                countryIsoPreference,
                mccMncPreference,
                operatorNamePreference
        );

        countryIsoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    newValue.toString(),
                    mccMncPreference.getText(),
                    operatorNamePreference.getText()
            );
            return true;
        });
        mccMncPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    countryIsoPreference.getText(),
                    newValue.toString(),
                    operatorNamePreference.getText()
            );
            return true;
        });
        operatorNamePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            simPresetPreference.refreshSummary(
                    countryIsoPreference.getText(),
                    mccMncPreference.getText(),
                    newValue.toString()
            );
            return true;
        });

        addPreference(simPresetPreference);
        addPreference(countryIsoPreference);
        addPreference(mccMncPreference);
        addPreference(operatorNamePreference);
    }
}
