/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TabSelectionPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class FeedNavigationPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedNavigationPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feed tabs");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.feedNavigationEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(
                context,
                "Filter feed tabs",
                "Choose which loaded TikTok feed tabs should stay visible. If only For You remains, its redundant header is hidden.",
                Settings.FEED_NAVIGATION
        ));
        addPreference(new TabSelectionPreference(
                context,
                Settings.FEED_NAVIGATION_TABS
        ));
        addPreference(new TogglePreference(
                context,
                "Block new feed tabs",
                "Hide tabs TikTok adds later unless you allow them.",
                Settings.FEED_NAVIGATION_BLOCK_NEW_TABS
        ));
        addPreference(new TogglePreference(
                context,
                "Filter bottom tabs",
                "Choose which loaded TikTok bottom navigation tabs should stay visible.",
                Settings.BOTTOM_NAVIGATION
        ));
        addPreference(new TabSelectionPreference(
                context,
                Settings.BOTTOM_NAVIGATION_TABS,
                true
        ));
        addPreference(new TogglePreference(
                context,
                "Block new bottom tabs",
                "Hide bottom tabs TikTok adds later unless you allow them.",
                Settings.BOTTOM_NAVIGATION_BLOCK_NEW_TABS
        ));
        addPreference(new TogglePreference(
                context,
                "Keep For You on a Home tap",
                "A tap on Home or on the For You tab while For You is showing no longer reloads it. "
                        + "The video you're on stays.",
                Settings.KEEP_FOR_YOU_ON_TAB_TAP
        ));
        addPreference(new TogglePreference(
                context,
                "Keep For You on a pull down",
                "Pulling down at the top of For You no longer reloads it.",
                Settings.KEEP_FOR_YOU_ON_PULL_DOWN
        ));
        addPreference(new TogglePreference(
                context,
                "Hide the unread badges on the bottom tabs",
                "Hide the red counts and dots on every bottom tab, Inbox and Profile included. "
                        + "The inbox itself still shows what came in.",
                Settings.HIDE_TAB_BADGES
        ));
        addPreference(new TogglePreference(
                context,
                "Hide the Tako bubble",
                "Hide the Tako AI bubble above the profile button, the Ask bar under videos, "
                        + "the Ask Tako button on the search page, the Ask Tako tab on search results "
                        + "and the Tako bar above comments.",
                Settings.HIDE_TAKO_AI
        ));
    }
}
