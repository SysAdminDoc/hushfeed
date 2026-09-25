/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

/**
 * Everything that decides what TikTok learns: tracking, what it may read off the phone, and
 * what a link carries. Before this page the two tracking switches sat at the bottom of App
 * behavior and the seven device-access patches had no switch at all.
 */
@SuppressWarnings("deprecation")
public final class PrivacyPreferenceCategory extends ConditionalPreferenceCategory {
    public PrivacyPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Privacy");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return hasTracking() || hasDeviceAccess() || hasLinks();
    }

    private static boolean hasTracking() {
        return SettingsStatus.disableTelemetryEnabled || SettingsStatus.ghostModeEnabled;
    }

    private static boolean hasDeviceAccess() {
        return SettingsStatus.contactListBlockerEnabled
                || SettingsStatus.installedAppsBlockerEnabled
                || SettingsStatus.locationGovernorEnabled
                || SettingsStatus.devicePrivacyGuardEnabled
                || SettingsStatus.resourceGovernorEnabled
                || SettingsStatus.cameraMicIndicatorEnabled;
    }

    private static boolean hasLinks() {
        return SettingsStatus.sanitizeShareUrlsEnabled
                || SettingsStatus.externalBrowserEnabled
                || SettingsStatus.browserPrivacyGuardEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        if (hasTracking()) {
            addPreference(new SectionHeadingPreference(context, "Tracking"));
        }
        if (SettingsStatus.disableTelemetryEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Stop analytics and tracking",
                    "Stop ByteDance AppLog events, AppsFlyer attribution, explicit Firebase screen reports and "
                            + "crash reporting from being sent. TikTok's own diagnostics go quiet with them.",
                    Settings.DISABLE_ANALYTICS
            ));
        }
        if (SettingsStatus.ghostModeEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Ghost mode",
                    "Stop TikTok reporting that you viewed a story or a profile, or that you are "
                            + "typing. Online status is unchanged. It cannot undo what the server has "
                            + "already recorded.",
                    Settings.GHOST_MODE
            ));
        }

        if (hasDeviceAccess()) {
            addPreference(new SectionHeadingPreference(context, "Device access"));
        }
        if (SettingsStatus.contactListBlockerEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Block contact list access",
                    "Answer TikTok's reads of your contacts with an empty list. Find Friends and "
                            + "People you may know lose access to your contact list.",
                    Settings.BLOCK_CONTACT_LIST
            ));
        }
        if (SettingsStatus.installedAppsBlockerEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Block installed app scanning",
                    "Answer TikTok's scan of the apps on this phone with an empty list. A check "
                            + "for one named app, which TikTok also uses to open an app you tap, is "
                            + "left alone.",
                    Settings.BLOCK_INSTALLED_APPS
            ));
        }
        if (SettingsStatus.locationGovernorEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Block location",
                    "Answer TikTok's location requests with nothing. The region settings change "
                            + "the locale and timezone. This switch stops the coordinates.",
                    Settings.BLOCK_LOCATION
            ));
        }
        if (SettingsStatus.devicePrivacyGuardEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Block clipboard reads",
                    "Stop TikTok reading what you copied. Copying a link from TikTok still works.",
                    Settings.BLOCK_CLIPBOARD_READS
            ));
        }
        if (SettingsStatus.resourceGovernorEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Block motion sensors",
                    "Stop TikTok listening to the accelerometer, gyroscope, magnetometer and the "
                            + "other motion sensors it uses to fingerprint the phone. Saves the "
                            + "battery they wake.",
                    Settings.BLOCK_MOTION_SENSORS
            ));
        }
        if (SettingsStatus.cameraMicIndicatorEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show when the camera or microphone is in use",
                    "A green dot in the top corner while TikTok has the camera open and an "
                            + "orange one while it records sound. They go when the access ends.",
                    Settings.CAMERA_MIC_INDICATOR
            ));
        }

        if (hasLinks()) {
            addPreference(new SectionHeadingPreference(context, "Links"));
        }
        if (SettingsStatus.sanitizeShareUrlsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Sanitize sharing links",
                    "Remove tracking parameters from shared links.",
                    BaseSettings.SANITIZE_SHARING_LINKS
            ));
            addPreference(new InputTextPreference(
                    context,
                    "Share links through another host",
                    "A host to put in place of tiktok.com when you share or copy a link, like "
                            + "vxtiktok.com. Leave it empty to share TikTok's own links. Only TikTok "
                            + "links are changed, and only the host: nothing is sent anywhere new.",
                    Settings.CUSTOM_SHARE_DOMAIN
            ));
        }
        if (SettingsStatus.externalBrowserEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Open external links directly",
                    "Open profile and story website links in your system browser instead of TikTok's in-app browser.",
                    Settings.OPEN_EXTERNAL_LINKS
            ));
        }
        if (SettingsStatus.browserPrivacyGuardEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Protect external pages in TikTok's browser",
                    "Keep websites outside TikTok from using its connection back into the app. "
                            + "TikTok pages such as Activity center, Watch history, shop checkout "
                            + "and CAPTCHA keep working.",
                    Settings.BLOCK_WEBVIEW_JS_INTERFACES
            ));
        }
    }
}
