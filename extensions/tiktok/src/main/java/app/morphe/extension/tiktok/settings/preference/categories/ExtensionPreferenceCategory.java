/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/ExtensionPreferenceCategory.java
 * Mirror, since GitHub blocks the original: https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/ExtensionPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

/**
 * The app around the feed: its layout, the player, search, the profile and the system it
 * runs on. The feed's own buttons and gestures moved to Feed filter and Feed screen, where
 * the rows they belong beside are.
 */
@SuppressWarnings("deprecation")
public class ExtensionPreferenceCategory extends ConditionalPreferenceCategory {
    public ExtensionPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("App");
    }

    /**
     * Whether this page has anything on it. The row into it asks the same question.
     *
     * <p>Every row below belongs to a patch, so with none of them in the bundle this page is its
     * heading and nothing else. The home screen used to ask a copy of this question kept in
     * {@code TikTokPreferenceFragment}, and the copy fell one flag behind: a bundle carrying
     * Settings and Hide the launcher shortcuts and nothing else built the switch here and no row
     * into the page, so the only way to that switch was the settings search. One list, here,
     * next to the rows it is a list of.
     */
    public static boolean isAvailable() {
        return SettingsStatus.foldableSplitViewEnabled
                || SettingsStatus.systemFontEnabled
                || SettingsStatus.showSeekbarEnabled
                || SettingsStatus.seekbarThumbnailEnabled
                || SettingsStatus.stopVideoLoopingEnabled
                || SettingsStatus.resumeVideoAfterScrollEnabled
                || SettingsStatus.duetStitchEnabled
                || SettingsStatus.nonPersonalizedSearchEnabled
                || SettingsStatus.liveSearchEnabled
                || SettingsStatus.hideSearchSuggestionsEnabled
                || SettingsStatus.keepFavoritesTabEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.refreshRateEnabled
                || SettingsStatus.launcherShortcutsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.foldableSplitViewEnabled) {
            addPreference(new SectionHeadingPreference(context, "Layout"));
            addPreference(new TogglePreference(context, "Comments beside the video",
                    "Use the split layout on wider screens. Restart TikTok to apply this. If the old layout is still there, unfold again.", Settings.FOLDABLE_SPLIT_VIEW));
            addPreference(new app.morphe.extension.tiktok.settings.preference.NumberInputPreference(context,
                    "Split comment minimum width", "Window width needed to enable the layout. Restart TikTok to apply this.",
                    Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP, "%1$s dp", "%1$s dp"));
        }
        if (SettingsStatus.systemFontEnabled) {
            addPreference(new SectionHeadingPreference(context, "Appearance"));
            addPreference(new TogglePreference(
                    context,
                    "Use system font",
                    "Draw TikTok's text in your device's font instead of TikTok Sans. Icons, gift "
                            + "animations and the @ and # glyphs keep their own fonts. Restart TikTok to apply this.",
                    Settings.SYSTEM_FONT
            ));
        }
        boolean hasPlayer = SettingsStatus.showSeekbarEnabled || SettingsStatus.seekbarThumbnailEnabled
                || SettingsStatus.stopVideoLoopingEnabled || SettingsStatus.resumeVideoAfterScrollEnabled
                || SettingsStatus.duetStitchEnabled;
        if (hasPlayer) {
            addPreference(new SectionHeadingPreference(context, "Player"));
        }
        if (SettingsStatus.showSeekbarEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show the progress bar",
                    "Show TikTok's own progress bar on videos where it's normally hidden.",
                    Settings.SHOW_SEEKBAR
            ));
        }
        if (SettingsStatus.seekbarThumbnailEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show the progress bar thumbnail",
                    "Show a video preview thumbnail while dragging the progress bar.",
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
        if (SettingsStatus.duetStitchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Allow Duet and Stitch anyway",
                    "Ignore the creator's choice so the Duet and Stitch entries appear. "
                            + "Everything else the app checks still applies, and whether the "
                            + "upload is accepted is the server's decision.",
                    Settings.ALLOW_DUET_AND_STITCH
            ));
        }
        boolean hasSearch = SettingsStatus.nonPersonalizedSearchEnabled || SettingsStatus.liveSearchEnabled
                || SettingsStatus.hideSearchSuggestionsEnabled;
        if (hasSearch) {
            addPreference(new SectionHeadingPreference(context, "Search"));
        }
        if (SettingsStatus.nonPersonalizedSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Use non-personalized search",
                    "Ask TikTok for search results that aren't personalized to your account. This changes results, not search buttons or suggestions.",
                    Settings.ENABLE_NON_PERSONALIZED_SEARCH
            ));
        }
        if (SettingsStatus.liveSearchEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show search in LIVE",
                    "Show the search entry inside TikTok's LIVE drawer, where available. This doesn't add a search box to video comments.",
                    Settings.ENABLE_LIVE_SEARCH
            ));
        }
        if (SettingsStatus.hideSearchSuggestionsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide suggestions on the search page",
                    "Hide recommended searches before you type on TikTok's search page. Your search history stays. This doesn't hide suggestions above comments.",
                    Settings.HIDE_SEARCH_SUGGESTIONS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide search rewards",
                    "Hide the points banner under the search box and the coin counter floating over search results, which TikTok shows in some regions. Searching works as before.",
                    Settings.HIDE_SEARCH_REWARDS
            ));
        }
        if (SettingsStatus.keepFavoritesTabEnabled || SettingsStatus.promotionalBannersEnabled) {
            addPreference(new SectionHeadingPreference(context, "Profile"));
        }
        if (SettingsStatus.keepFavoritesTabEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Keep the Favorites tab",
                    "TikTok's server can put an account into an experiment that empties the Favorites tab on your profile. Keep the tab and its saved videos.",
                    Settings.KEEP_FAVORITES_TAB
            ));
        }
        if (SettingsStatus.promotionalBannersEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the rewards shortcut",
                    "Hide the purple rewards button beside Add friends on your profile.",
                    Settings.HIDE_PROFILE_REWARDS_SHORTCUT
            ));
        }
        if (SettingsStatus.refreshRateEnabled || SettingsStatus.launcherShortcutsEnabled) {
            addPreference(new SectionHeadingPreference(context, "System"));
        }
        if (SettingsStatus.refreshRateEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Keep the screen's refresh rate",
                    "Stop TikTok asking the screen to run at the frame rate of the video it's "
                            + "playing. On a 90 or 120 Hz phone that ask slows the whole app down "
                            + "to the video's rate, scrolling included.",
                    Settings.UNCAP_REFRESH_RATE
            ));
        }
        if (SettingsStatus.launcherShortcutsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide the launcher shortcuts",
                    "Empty the menu that opens when you press and hold TikTok's icon on the home "
                            + "screen. Turning this off asks TikTok to build them again. Tapping "
                            + "the icon still opens the app, and a shortcut you pinned yourself "
                            + "stays where you put it.",
                    Settings.HIDE_LAUNCHER_SHORTCUTS
            ));
        }
    }
}
