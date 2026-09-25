/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.tiktok.captions.CaptionStyle;
import app.morphe.extension.tiktok.interaction.TapConfirmation;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.SwitchListPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

/**
 * Everything drawn on the feed itself, grouped by where it sits: the right column, the text
 * beside the video, what is around it, what pops over it, then captions, the screen, clear
 * display and gestures. The page used to be one 27-row "Feed controls" card after the
 * captions, in the order the patches were written.
 */
@SuppressWarnings("deprecation")
public final class InterfacePreferenceCategory extends ConditionalPreferenceCategory {
    public InterfacePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feed screen");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled
                || SettingsStatus.alwaysShowPublishDateEnabled
                || SettingsStatus.authorRegionEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.feedFilterEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.sensitiveWarningsEnabled
                || SettingsStatus.subtitleToolsEnabled
                || SettingsStatus.screenCaptureEnabled
                || SettingsStatus.automaticClearDisplayEnabled
                || SettingsStatus.doubleTapEnabled
                || SettingsStatus.swipeLeftEnabled
                || SettingsStatus.longPressEnabled
                || SettingsStatus.longPressSpeedLockEnabled
                || SettingsStatus.disableLongPressQuickShareEnabled
                || SettingsStatus.disableLongPressRepostEnabled
                || SettingsStatus.confirmInteractionsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        addRightColumn(context);
        addVideoInfo(context);
        addAroundTheVideo(context);
        addPopups(context);
        addCaptions(context);
        addScreen(context);
        addClearDisplay(context);
        addGestures(context);
    }

    private void addRightColumn(Context context) {
        boolean any = SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Right column"));
        if (SettingsStatus.videoOverlaysEnabled) {
            // One row over the six hides and the counts. Six rows that each said "Hide the ...
            // button" read the same choice seven times over; the row's second line says what
            // is hidden at a glance.
            List<SwitchListPreference.Item> hides = new ArrayList<>();
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Avatar and follow button"), Settings.HIDE_RAIL_FOLLOW));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Like"), Settings.HIDE_RAIL_LIKE));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Comments"), Settings.HIDE_RAIL_COMMENTS));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Save"), Settings.HIDE_RAIL_FAVOURITE));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Share"), Settings.HIDE_RAIL_SHARE));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Music disc"), Settings.HIDE_RAIL_MUSIC));
            hides.add(new SwitchListPreference.Item(L10n.t(context, "Counts under the buttons"), Settings.HIDE_RAIL_COUNTS));
            addPreference(new SwitchListPreference(context, "feed_right_column_hides",
                    "Hide buttons in the right column",
                    "Pick the buttons to take off the column. The rest stay where they are.",
                    hides));
            addPreference(new TogglePreference(
                    context,
                    "Hide the right column",
                    "Hide the whole column on the right, from the avatar down to the music disc.",
                    Settings.HIDE_FEED_ACTION_BAR
            ));
            // Two choices, not four. The right column has no room between its slots, so a
            // glyph grown by more than a quarter lands on the count under it or the button
            // above it (S22, 2026-09-17); 1.5x and 2x were offered before that was measured.
            addPreference(new ChoicePreference(context, "Feed button size", Settings.TOUCH_TARGET_SCALE,
                    new String[]{"Normal", "Larger (1.25x)"},
                    new String[]{"1", "1.25"}));
        }
        if (SettingsStatus.hideFeedFollowButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the follow button on the feed",
                    "Hide the plus button under the creator's picture in the right column. Ticking Avatar and follow button above hides the picture with it.",
                    Settings.HIDE_FEED_FOLLOW_BUTTON
            ));
        }
        if (SettingsStatus.hideFeedSaveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the save button on the feed",
                    "Hide the save button in the right column. Ticking Save in the list above hides it too.",
                    Settings.HIDE_FEED_SAVE_BUTTON
            ));
        }
    }

    private void addVideoInfo(Context context) {
        boolean any = SettingsStatus.alwaysShowPublishDateEnabled
                || SettingsStatus.authorRegionEnabled
                || SettingsStatus.videoOverlaysEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Video info"));
        if (SettingsStatus.alwaysShowPublishDateEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Always show publish date",
                    "Always show the publish date in video author information. Restart TikTok to apply this.",
                    Settings.ALWAYS_SHOW_PUBLISH_DATE
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
        }
    }

    private void addAroundTheVideo(Context context) {
        boolean any = SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.feedFilterEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Around the video"));
        if (SettingsStatus.videoOverlaysEnabled || SettingsStatus.hideFeedLiveButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the LIVE button",
                    "Hide the LIVE button in the top left corner of the feed.",
                    Settings.HIDE_LIVE_ENTRANCE
            ));
        }
        if (SettingsStatus.hideFeedSearchButtonEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the search button on the feed",
                    "Hide the magnifying glass at the top right of the feed. Search in Inbox and suggestions above comments stay unchanged.",
                    Settings.HIDE_FEED_SEARCH_BUTTON
            ));
        }
        if (SettingsStatus.videoOverlaysEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the Full screen button",
                    "Hide the Full screen button over videos. Playback and automatic rotation keep working as before.",
                    Settings.HIDE_FULLSCREEN_BUTTON
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the Report button on videos",
                    "Hide the flag button TikTok puts above the creator's picture in some regions. You can still report a video from the share menu.",
                    Settings.HIDE_FEED_REPORT_BUTTON
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide location labels",
                    "Hide place badges on videos, including cards with multiple places. Videos and your location permissions stay unchanged.",
                    Settings.HIDE_LOCATION_LABELS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide surveys",
                    "Hide the question cards TikTok slides over a video near its end.",
                    Settings.HIDE_FEED_SURVEYS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide Search this image prompts",
                    "Hide the \"Search this image\" prompt shown over videos. The feed search button and suggestions above comments are separate settings.",
                    Settings.HIDE_VISUAL_SEARCH
            ));
        }
        // Feed filter rows that hide things drawn around a video rather than videos.
        // They were the tail of the Feed filter page under "Feed elements", a page about
        // which videos arrive.
        if (SettingsStatus.feedFilterEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the search bar below videos",
                    "Hide the suggested-search strip above the bottom tabs and let video details and side controls use its space. The top search button and comments stay unchanged.",
                    Settings.HIDE_BOTTOM_SEARCH_BAR
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the playlist bar",
                    "Hide the playlist bar along the bottom of videos that belong to a series.",
                    Settings.HIDE_PLAYLIST_BAR
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide the event badge",
                    "Hide the floating promotional badge over the feed.",
                    Settings.HIDE_EVENT_BADGE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide inserted cards",
                    "Hide the friend recommendation card and the other cards TikTok slots between videos.",
                    Settings.HIDE_INSERTED_CARDS
            ));
        }
    }

    private void addPopups(Context context) {
        boolean any = SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.sensitiveWarningsEnabled
                || SettingsStatus.videoOverlaysEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Popups"));
        if (SettingsStatus.promotionalBannersEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide floating promotions",
                    "Hide floating promotion badges, coins, and timer banners on the feed.",
                    Settings.HIDE_HOMEPAGE_COIN
            ));
        }
        if (SettingsStatus.captchaPopupSuppressionEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide CAPTCHA popups",
                    "Hide the CAPTCHA dialogs raised while you browse or watch LIVE. Login, account verification and any CAPTCHA raised over a follow, like, comment or repost stay visible.",
                    Settings.HIDE_CAPTCHA_POPUPS
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
        if (SettingsStatus.videoOverlaysEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the share prompt after a like",
                    "Stop the bubble that asks you to share a video with friends after you like it.",
                    Settings.HIDE_SHARE_GUIDE
            ));
        }
    }

    private void addCaptions(Context context) {
        if (SettingsStatus.subtitleToolsEnabled) {
            addPreference(new SectionHeadingPreference(context, "Captions"));
            NumberInputPreference captionSize = new NumberInputPreference(context, "Caption text size",
                    L10n.f(context, "Use 0 for TikTok's size, or %1$d to %2$d. Applies to the next caption.",
                            CaptionStyle.MIN_TEXT_SIZE, CaptionStyle.MAX_TEXT_SIZE),
                    Settings.CAPTION_TEXT_SIZE, "%1$s point", "%1$s points") {
                @Override protected int clamp(int value) { return CaptionStyle.clampSize(value); }
            };
            captionSize.zeroMeans("TikTok's size");
            addPreference(captionSize);
            addPreference(new ChoicePreference(context, "Caption background", Settings.CAPTION_BACKGROUND,
                    new String[]{"TikTok default", "Transparent", "Dark", "Black"},
                    new String[]{"default", "transparent", "dark", "black"}));
            addPreference(new TogglePreference(context, "Keep captions in clear display",
                    "Show the current spoken caption while the other controls are hidden.", Settings.KEEP_CAPTIONS_CLEAR_DISPLAY));
        }
    }

    private void addScreen(Context context) {
        boolean any = SettingsStatus.screenCaptureEnabled || SettingsStatus.videoOverlaysEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Screen"));
        if (SettingsStatus.screenCaptureEnabled) {
            addPreference(new TogglePreference(context, "Allow screenshots and Circle to Search",
                    "Let screenshots, screen recording and Circle to Search work on TikTok again. Restart TikTok to apply this.", Settings.ALLOW_SCREEN_CAPTURE));
        }
        if (SettingsStatus.videoOverlaysEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the status bar",
                    "Keep the clock and status icons off the screen while TikTok is open. "
                            + "Swipe down from the top to peek at them.",
                    Settings.HIDE_STATUS_BAR
            ));
        }
    }

    private void addClearDisplay(Context context) {
        if (SettingsStatus.automaticClearDisplayEnabled) {
            addPreference(new SectionHeadingPreference(context, "Clear display"));
            addPreference(new TogglePreference(context, "Automatic clear display",
                    "Hide controls after each video starts. Tap to restore them.", Settings.AUTOMATIC_CLEAR_DISPLAY));
            NumberInputPreference delay = new NumberInputPreference(context, "Clear display delay",
                    "Wait before hiding the controls.", Settings.AUTOMATIC_CLEAR_DISPLAY_DELAY,
                    "%1$s millisecond", "%1$s milliseconds");
            delay.zeroMeans("No delay");
            addPreference(delay);
        }
    }

    private void addGestures(Context context) {
        boolean any = SettingsStatus.doubleTapEnabled || SettingsStatus.longPressEnabled
                || SettingsStatus.swipeLeftEnabled
                || SettingsStatus.longPressSpeedLockEnabled
                || SettingsStatus.disableLongPressQuickShareEnabled
                || SettingsStatus.disableLongPressRepostEnabled
                || SettingsStatus.confirmInteractionsEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Gestures"));
        if (SettingsStatus.doubleTapEnabled) {
            addPreference(new ChoicePreference(context, "Double tap", Settings.DOUBLE_TAP_ACTION,
                    new String[]{"TikTok default", "Do nothing", "Open comments"},
                    new String[]{"default", "nothing", "comments"}));
        }
        if (SettingsStatus.swipeLeftEnabled) {
            addPreference(new ChoicePreference(context, "Swipe left", Settings.SWIPE_LEFT_ACTION,
                    new String[]{"TikTok default (open the creator's profile)", "Do nothing", "Open comments"},
                    new String[]{"default", "nothing", "comments"}));
        }
        if (SettingsStatus.longPressEnabled) {
            addPreference(new ChoicePreference(context, "Long press", Settings.LONG_PRESS_ACTION,
                    new String[]{"TikTok default (hold to speed up, quick share)", "Do nothing",
                            "Open comments", "Save the original sound", "Copy the video link",
                            "Copy the sound link", "Find the sound on YouTube Music"},
                    new String[]{"default", "nothing", "comments", "original_sound", "copy_link",
                            "copy_sound_link", "youtube_music"}));
            addPreference(new TogglePreference(context, "Seek from the edges",
                    "Press and hold the left or right third of the screen to jump back or forward. "
                            + "The middle third keeps the Long press action.", Settings.EDGE_SEEK));
            addPreference(new NumberInputPreference(context, "Seek by",
                    "How far each edge press moves the video.", Settings.EDGE_SEEK_SECONDS, "%1$s s"));
            addPreference(new TogglePreference(context, "Hold speed on Comment",
                    "A long press on Comment plays the video at the hold speed instead of opening the emoji row. "
                            + "It needs Long press on TikTok default and Seek from the edges off.",
                    Settings.RAIL_HOLD_COMMENT));
            addPreference(new TogglePreference(context, "Hold speed on Share",
                    "A long press on Share plays the video at the hold speed instead of opening the quick share row. "
                            + "It needs Long press on TikTok default and Seek from the edges off.",
                    Settings.RAIL_HOLD_SHARE));
            addPreference(new TogglePreference(context, "Hold speed on Favorites",
                    "A long press on Favorites plays the video at the hold speed instead of offering a new collection. "
                            + "It needs Long press on TikTok default and Seek from the edges off.",
                    Settings.RAIL_HOLD_FAVORITES));
        }
        // Three more long presses, each its own patch. They sat under Player on the App page,
        // two pages away from the Long press row they belong beside.
        if (SettingsStatus.longPressSpeedLockEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Lock the hold speed with a pull down",
                    "Use TikTok's own hold, pull down and release gesture to keep playing at the hold speed, "
                            + "2x unless Playback sets another.",
                    Settings.ENABLE_LONG_PRESS_SPEED_LOCK
            ));
        }
        if (SettingsStatus.disableLongPressQuickShareEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Keep long press from quick sharing",
                    "Stop a long press on Share from opening TikTok's quick share action.",
                    Settings.DISABLE_LONG_PRESS_QUICK_SHARE
            ));
        }
        if (SettingsStatus.disableLongPressRepostEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Keep long press from reposting",
                    "Stop a long press on Like from opening TikTok's repost action.",
                    Settings.DISABLE_LONG_PRESS_REPOST
            ));
        }
        if (SettingsStatus.confirmInteractionsEnabled) {
            addPreference(new TogglePreference(context, "Confirm before following",
                    L10n.f(context, "Tap the feed Follow button twice within %1$d seconds.",
                            TapConfirmation.CONFIRM_WINDOW_SECONDS),
                    Settings.CONFIRM_FOLLOW));
            addPreference(new TogglePreference(context, "Confirm before liking",
                    L10n.f(context, "Tap the like heart twice within %1$d seconds. Removing a like stays immediate.",
                            TapConfirmation.CONFIRM_WINDOW_SECONDS),
                    Settings.CONFIRM_LIKE));
            addPreference(new TogglePreference(context, "Confirm before liking a comment",
                    L10n.f(context, "Tap a comment's heart twice within %1$d seconds. A double tap on the comment needs a second one too. Removing a like stays immediate.",
                            TapConfirmation.CONFIRM_WINDOW_SECONDS),
                    Settings.CONFIRM_COMMENT_LIKE));
            addPreference(new TogglePreference(context, "Confirm before liking a story",
                    L10n.f(context, "Tap a story's heart twice within %1$d seconds. Removing a like stays immediate.",
                            TapConfirmation.CONFIRM_WINDOW_SECONDS),
                    Settings.CONFIRM_STORY_LIKE));
            addPreference(new TogglePreference(context, "Confirm before a quick repost",
                    L10n.f(context, "Tap Repost on the bar under a video twice within %1$d seconds. A repost with a note is left alone.",
                            TapConfirmation.CONFIRM_WINDOW_SECONDS),
                    Settings.CONFIRM_QUICK_REPOST));
        }
    }
}
