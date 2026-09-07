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
        return SettingsStatus.subtitleToolsEnabled || SettingsStatus.screenCaptureEnabled || SettingsStatus.automaticClearDisplayEnabled || SettingsStatus.doubleTapEnabled || SettingsStatus.longPressEnabled || SettingsStatus.confirmInteractionsEnabled || SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.alwaysShowPublishDateEnabled
                || SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.authorRegionEnabled
                || SettingsStatus.sensitiveWarningsEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled
                || SettingsStatus.hideSearchSuggestionsEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.subtitleToolsEnabled) {
            addPreference(new NumberInputPreference(context, "Caption text size",
                    "Use 0 for TikTok's size, or 12 to 48 sp. Applies to the next caption.", Settings.CAPTION_TEXT_SIZE, "sp") {
                @Override protected int clamp(int value) { return value <= 0 ? 0 : Math.max(12, Math.min(48, value)); }
            });
            addPreference(new ChoicePreference(context, "Caption background", Settings.CAPTION_BACKGROUND,
                    new String[]{"TikTok default", "Transparent", "Dark", "Black"},
                    new String[]{"default", "transparent", "dark", "black"}));
            addPreference(new TogglePreference(context, "Keep captions in clear display",
                    "Show the current spoken caption while the other controls are hidden.", Settings.KEEP_CAPTIONS_CLEAR_DISPLAY));
        }
        if (SettingsStatus.screenCaptureEnabled) {
            addPreference(new TogglePreference(context, "Allow screenshots and Circle to Search",
                    "Let screenshots, screen recording and Circle to Search work on TikTok again. Restart TikTok after changing this.", Settings.ALLOW_SCREEN_CAPTURE));
        }
        if (SettingsStatus.automaticClearDisplayEnabled) {
            addPreference(new TogglePreference(context, "Automatic clear display",
                    "Hide controls after each video starts. Tap to restore them.", Settings.AUTOMATIC_CLEAR_DISPLAY));
            addPreference(new NumberInputPreference(context, "Clear display delay",
                    "Wait before hiding the controls.", Settings.AUTOMATIC_CLEAR_DISPLAY_DELAY, "ms"));
        }
        if (SettingsStatus.doubleTapEnabled) {
            addPreference(new ChoicePreference(context, "Double tap", Settings.DOUBLE_TAP_ACTION,
                    new String[]{"TikTok default", "Do nothing", "Open comments"},
                    new String[]{"default", "nothing", "comments"}));
        }
        if (SettingsStatus.longPressEnabled) {
            addPreference(new ChoicePreference(context, "Long press", Settings.LONG_PRESS_ACTION,
                    new String[]{"TikTok default (2x hold, quick share)", "Do nothing",
                            "Open comments", "Save the original sound"},
                    new String[]{"default", "nothing", "comments", "original_sound"}));
            addPreference(new TogglePreference(context, "Seek from the edges",
                    "Press and hold the left or right third of the screen to jump back or forward. "
                            + "The middle third keeps the Long press action.", Settings.EDGE_SEEK));
            addPreference(new NumberInputPreference(context, "Seek by",
                    "How far each edge press moves the video.", Settings.EDGE_SEEK_SECONDS, "s"));
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
                    "Hide browsing and LIVE puzzle dialogs. Login, account verification and any puzzle raised over a follow, like, comment or repost stay visible.",
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
        if (SettingsStatus.sensitiveWarningsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Skip content warnings",
                    "Play videos TikTok has classified without the overlay asking to be tapped "
                            + "through first.",
                    Settings.HIDE_SENSITIVE_WARNINGS
            ));
        }
        if (SettingsStatus.authorRegionEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show where a video was posted",
                    "Show the country a video was posted from next to the creator's name.",
                    Settings.SHOW_AUTHOR_REGION
            ));
            addPreference(new TogglePreference(
                    context,
                    "Show the handle instead of the name",
                    "Show @username beside a video instead of the display name the creator chose.",
                    Settings.SHOW_AUTHOR_HANDLE
            ));
        }
        if (SettingsStatus.videoOverlaysEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the caption",
                    "Hide the description under the creator's name on the feed.",
                    Settings.HIDE_FEED_CAPTION
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the music line",
                    "Hide the spinning music cover and the track name beside the caption.",
                    Settings.HIDE_FEED_MUSIC
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the action bar",
                    "Hide the whole column on the right, from the avatar down to the music disc.",
                    Settings.HIDE_FEED_ACTION_BAR
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the avatar and follow button",
                    "Hide the creator's picture and the plus button at the top of the right column.",
                    Settings.HIDE_RAIL_FOLLOW
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the like button",
                    "Hide the heart and its count in the right column.",
                    Settings.HIDE_RAIL_LIKE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the comments button",
                    "Hide the speech bubble and its count in the right column.",
                    Settings.HIDE_RAIL_COMMENTS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the favourite button",
                    "Hide the favourite button and its count in the right column.",
                    Settings.HIDE_RAIL_FAVOURITE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the music disc",
                    "Hide the spinning record at the bottom of the right column.",
                    Settings.HIDE_RAIL_MUSIC
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the share button",
                    "Hide the arrow and its count in the right column.",
                    Settings.HIDE_RAIL_SHARE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the counts on the right column",
                    "Hide the numbers under the like, comment, favourite and share buttons. The buttons stay.",
                    Settings.HIDE_RAIL_COUNTS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide surveys",
                    "Hide the question cards TikTok slides over a video near its end.",
                    Settings.HIDE_FEED_SURVEYS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the status bar",
                    "Keep the clock and status icons off the screen while TikTok is open. "
                            + "Swipe down from the top to peek at them.",
                    Settings.HIDE_STATUS_BAR
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide visual search prompt",
                    "Hide the \"Search this image\" prompt TikTok shows over videos when it spots something to shop for.",
                    Settings.HIDE_VISUAL_SEARCH
            ));
        }
        if (SettingsStatus.hideSearchSuggestionsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide search suggestions",
                    "Hide the searches TikTok offers before you type. Your own search history stays.",
                    Settings.HIDE_SEARCH_SUGGESTIONS
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
