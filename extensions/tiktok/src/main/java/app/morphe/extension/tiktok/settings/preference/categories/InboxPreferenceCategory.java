/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public final class InboxPreferenceCategory extends ConditionalPreferenceCategory {
    public InboxPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Inbox");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.inboxFilterEnabled
                || SettingsStatus.hideSuggestedAccountsEnabled
                || SettingsStatus.hideInboxStoriesEnabled
                || SettingsStatus.expandActivityListEnabled
                || SettingsStatus.notificationControlsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.inboxFilterEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide new followers",
                    "Hide the New followers row.",
                    Settings.HIDE_INBOX_NEW_FOLLOWERS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide activity",
                    "Hide the Activity row of likes and comments on your posts.",
                    Settings.HIDE_INBOX_ACTIVITY
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide archive",
                    "Hide the Archive row.",
                    Settings.HIDE_INBOX_ARCHIVE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide TikTok Tako",
                    "Hide the TikTok Tako assistant conversation.",
                    Settings.HIDE_INBOX_TAKO
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide TikTok Shop",
                    "Hide TikTok Shop updates and coupon messages.",
                    Settings.HIDE_INBOX_SHOP
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide message requests",
                    "Hide the Message requests row.",
                    Settings.HIDE_INBOX_MESSAGE_REQUESTS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide conversations",
                    "Hide every direct message conversation. Leaves the Inbox empty apart from "
                            + "whatever else is still switched on.",
                    Settings.HIDE_INBOX_CONVERSATIONS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide add people",
                    "Hide the add people icon in the Inbox header.",
                    Settings.HIDE_INBOX_ADD_PEOPLE
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide search",
                    "Hide the search icon in the Inbox header.",
                    Settings.HIDE_INBOX_SEARCH
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide activity status",
                    "Hide the activity status control in the Inbox header.",
                    Settings.HIDE_INBOX_ACTIVITY_STATUS
            ));
            addPreference(new InputTextPreference(
                    context,
                    "Hide rows by title",
                    "Comma separated list of any other Inbox row titles to hide, matched exactly. "
                            + "Use this for anything not listed above.",
                    Settings.HIDE_INBOX_CUSTOM_TITLES
            ));
        }
        if (SettingsStatus.inboxFilterEnabled || SettingsStatus.hideInboxStoriesEnabled) {
        addPreference(new TogglePreference(
                context,
                "Hide stories tray",
                "Hide the row of story avatars across the top of the Inbox.",
                Settings.HIDE_INBOX_STORIES
        ));
        }
        if (SettingsStatus.inboxFilterEnabled || SettingsStatus.hideSuggestedAccountsEnabled) {
        addPreference(new TogglePreference(
                context,
                "Hide suggested accounts",
                "Hide suggested account rows.",
                Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS
        ));
        }
        if (SettingsStatus.notificationControlsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide new follower notifications",
                    "Drop the notification saying somebody new followed you before it reaches "
                            + "the drawer. Every other notification is left alone, and the "
                            + "follower still appears in the Inbox.",
                    Settings.HIDE_FOLLOWER_NOTIFICATIONS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide message streaks",
                    "Take away the streak button in a chat and the reminder message that goes "
                            + "with it.",
                    Settings.HIDE_MESSAGE_STREAKS
            ));
        }
        if (SettingsStatus.expandActivityListEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Expand activity list",
                    "Show the whole Activity and New followers lists instead of stopping at a "
                            + "View all button.",
                    Settings.EXPAND_ACTIVITY_LIST
            ));
        }
    }
}
