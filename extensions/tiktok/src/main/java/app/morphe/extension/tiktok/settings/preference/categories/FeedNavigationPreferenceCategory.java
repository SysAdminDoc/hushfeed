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
        setTitle("Feed navigation");
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
                "Choose which loaded TikTok feed tabs should stay visible.",
                Settings.FEED_NAVIGATION
        ));
        addPreference(new TabSelectionPreference(
                context,
                Settings.FEED_NAVIGATION_TABS
        ));
        addPreference(new TogglePreference(
                context,
                "Block new TikTok tabs",
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
                "Hide Tako AI",
                "Hide the Tako AI feed bubble above the profile button.",
                Settings.HIDE_TAKO_AI
        ));
    }
}
