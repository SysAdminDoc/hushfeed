/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.extension.tiktok.inbox;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Answers TikTok's own widget injectors so an Inbox section is never built, rather than
 * hidden once it is on screen the way {@link InboxFilter} does. Both read the same
 * settings, so a switch works with either patch applied, or both.
 */
public final class InboxControls {
    private InboxControls() {
    }

    public static boolean shouldShowSuggestedAccounts() {
        return !Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.get();
    }

    public static boolean shouldShowInboxStories() {
        return !Settings.HIDE_INBOX_STORIES.get();
    }

    public static boolean shouldCollapseActivityList() {
        return !Settings.EXPAND_ACTIVITY_LIST.get();
    }
}
