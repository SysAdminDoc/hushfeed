/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/ExtensionPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class ExtensionPreferenceCategory extends ConditionalPreferenceCategory {
    public ExtensionPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("App behavior");
    }

    @Override
    public boolean getSettingsStatus() {
        return true;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(
                context,
                "Sanitize sharing links",
                "Remove tracking parameters from shared links.",
                BaseSettings.SANITIZE_SHARING_LINKS
        ));
        if (SettingsStatus.externalBrowserEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Open external links directly",
                    "Open profile and story website links in your system browser instead of TikTok's in-app browser.",
                    Settings.OPEN_EXTERNAL_LINKS
            ));
        }

        addPreference(new TogglePreference(
                context,
                "Show seekbar",
                "Show the native seekbar on videos where TikTok would normally hide it.",
                Settings.SHOW_SEEKBAR
        ));
        if (SettingsStatus.seekbarThumbnailEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show seekbar thumbnail",
                    "Show a video preview thumbnail while dragging the seekbar.",
                    Settings.SHOW_SEEKBAR_THUMBNAIL
            ));
        }
        if (SettingsStatus.stopVideoLoopingEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Stop video looping",
                    "Stop videos at the end instead of replaying them.",
                    Settings.STOP_VIDEO_LOOPING
            ));
        }
        if (SettingsStatus.resumeVideoAfterScrollEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Resume videos after scrolling",
                    "Continue supported videos from where you stopped when you scroll back to them.",
                    Settings.RESUME_VIDEO_AFTER_SCROLL
            ));
        }
        if (SettingsStatus.longPressSpeedLockEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Enable hold-and-slide 2x lock",
                    "Use TikTok's native hold, slide down, and release gesture to lock 2x speed.",
                    Settings.ENABLE_LONG_PRESS_SPEED_LOCK
            ));
        }
        if (SettingsStatus.disableLongPressQuickShareEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Disable long-press quick share",
                    "Keep long-pressing Share from opening TikTok's quick-share interaction.",
                    Settings.DISABLE_LONG_PRESS_QUICK_SHARE
            ));
        }
        if (SettingsStatus.disableLongPressRepostEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Disable long-press repost",
                    "Keep holding Like from opening TikTok's repost action.",
                    Settings.DISABLE_LONG_PRESS_REPOST
            ));
        }
        if (SettingsStatus.ghostModeEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Ghost mode",
                    "Stop TikTok reporting that you viewed a story or a profile, that you are "
                            + "typing. Online status is unchanged. It cannot undo what the server has "
                            + "already recorded.",
                    Settings.GHOST_MODE
            ));
        }
        if (SettingsStatus.disableTelemetryEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Disable analytics and tracking",
                    "Stop ByteDance AppLog events, AppsFlyer attribution, explicit Firebase screen reports and "
                            + "crash reporting from being sent. TikTok's own diagnostics go quiet with them.",
                    Settings.DISABLE_ANALYTICS
            ));
        }
        if (SettingsStatus.blockAuthorEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show block button on videos",
                    "Add a block button to the video player that blocks the account that posted the "
                            + "current video in one tap. An undo action is shown after each block.",
                    Settings.BLOCK_AUTHOR_BUTTON
            ));
        }
        if (SettingsStatus.notInterestedEnabled) {
            addPreference(new TogglePreference(context, "Not interested button",
                    "Add a button beside the block control to send feedback about the current video.",
                    Settings.NOT_INTERESTED_BUTTON));
        }
        if (SettingsStatus.nonPersonalizedSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Use non-personalized search",
                    "Use TikTok's non-personalized search state instead of the saved account choice.",
                    Settings.ENABLE_NON_PERSONALIZED_SEARCH
            ));
        }
        if (SettingsStatus.liveSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show Live search",
                    "Show TikTok's search entry in the Live drawer where supported.",
                    Settings.ENABLE_LIVE_SEARCH
            ));
        }

    }
}
