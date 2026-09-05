/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;

@SuppressWarnings("deprecation")
public final class InterfacePreferenceCategory extends ConditionalPreferenceCategory {
    public InterfacePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Interface");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.screenCaptureEnabled || SettingsStatus.automaticClearDisplayEnabled || SettingsStatus.doubleTapEnabled || SettingsStatus.confirmInteractionsEnabled || SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.alwaysShowPublishDateEnabled
                || SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.screenCaptureEnabled) {
            addPreference(new TogglePreference(context, "Allow screenshots and Circle to Search",
                    "Remove secure window flags. Restart TikTok after changing.", Settings.ALLOW_SCREEN_CAPTURE));
        }
        if (SettingsStatus.automaticClearDisplayEnabled) {
            addPreference(new TogglePreference(context, "Automatic clear display",
                    "Hide controls after each video starts. Tap to restore them.", Settings.AUTOMATIC_CLEAR_DISPLAY));
            addPreference(new NumberInputPreference(context, "Clear display delay",
                    "Wait before hiding the controls.", Settings.AUTOMATIC_CLEAR_DISPLAY_DELAY, 0, 30000, "ms"));
        }
        if (SettingsStatus.doubleTapEnabled) {
            addPreference(new ChoicePreference(context, "Double tap", Settings.DOUBLE_TAP_ACTION,
                    new String[]{"TikTok default", "Do nothing", "Open comments"},
                    new String[]{"default", "nothing", "comments"}));
        }
        if (SettingsStatus.confirmInteractionsEnabled) {
            addPreference(new TogglePreference(context, "Confirm before following", "Tap the feed Follow button twice within four seconds.", Settings.CONFIRM_FOLLOW));
            addPreference(new TogglePreference(context, "Confirm before liking", "Tap the like heart twice within four seconds. Removing a like stays immediate.", Settings.CONFIRM_LIKE));
        }
        if (SettingsStatus.promotionalBannersEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide floating promotions",
                    "Hide floating promotion badges, coins, and timer banners on the homepage.",
                    Settings.HIDE_HOMEPAGE_COIN
            ));
        }
        if (SettingsStatus.captchaPopupSuppressionEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide CAPTCHA popups",
                    "Hide browsing and LIVE puzzle dialogs. Login and account verification remain available.",
                    Settings.HIDE_CAPTCHA_POPUPS
            ));
        }
        if (SettingsStatus.alwaysShowPublishDateEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Always show publish date",
                    "Always show the publish date in video author information. Requires restart.",
                    Settings.ALWAYS_SHOW_PUBLISH_DATE
            ));
        }
        if (SettingsStatus.videoOverlaysEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide visual search prompt",
                    "Hide the \"Search this image\" prompt TikTok shows over videos when it spots something to shop for.",
                    Settings.HIDE_VISUAL_SEARCH
            ));
        }
        if (SettingsStatus.videoOverlaysEnabled || SettingsStatus.hideFeedLiveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide Live entrance",
                    "Hide the Live button in the top left corner of the feed.",
                    Settings.HIDE_LIVE_ENTRANCE
            ));
        }
        if (SettingsStatus.hideFeedSearchButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide feed search button",
                    "Hide the search button in the top right corner of the feed.",
                    Settings.HIDE_FEED_SEARCH_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedFollowButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide feed follow button",
                    "Hide the plus button under the creator's avatar on the action rail.",
                    Settings.HIDE_FEED_FOLLOW_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedSaveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide feed save button",
                    "Hide the save button on the action rail.",
                    Settings.HIDE_FEED_SAVE_BUTTON
            ));
        }
    }
}
