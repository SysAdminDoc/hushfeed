/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabRuntime;
import app.morphe.extension.tiktok.settings.preference.FeatureGateLabPreference;

@SuppressWarnings("deprecation")
public final class FeatureGateLabPreferenceCategory extends ConditionalPreferenceCategory {
    public FeatureGateLabPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feature Gate Lab");
    }

    @Override
    public boolean getSettingsStatus() {
        return FeatureGateLabRuntime.isInstalled() || app.morphe.extension.tiktok.settings.SettingsStatus.featureGateRecorderEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (app.morphe.extension.tiktok.settings.SettingsStatus.featureGateRecorderEnabled) {
            addPreference(new app.morphe.extension.tiktok.settings.preference.FeatureGateRecorderPreference(context));
        }
        if (context instanceof Activity) {
            addPreference(new FeatureGateLabPreference((Activity) context));
        }
    }
}
