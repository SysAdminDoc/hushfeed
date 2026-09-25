/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.navigation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Picks the tab TikTok opens on when it starts from its icon.
 *
 * <p>TikTok's main activity works that tab out itself as it is created: the tab a notification
 * names, else a tab it saved, else one its own landing rules ask for, else Home. All of those end
 * at one comparison with "HOME", where the patch hands the tag to {@link #coldStartTag}, and the
 * tag that comes back goes through TikTok's own steps for a start on that tab: the splash theme is
 * put back for anything but Home, and Friends, Inbox and Profile each go through the handler
 * TikTok uses when a notification opens them.
 *
 * <p>Only a plain start from the launcher changes. A notification, a link or a shortcut keeps the
 * tab it asked for, and an activity Android restores keeps the tab it was on.
 */
public final class StartPage {
    static final String FAMILY = "start page";
    static final String PUSH_TAB = "com.ss.android.ugc.aweme.intent.extra.EXTRA_AWEME_PUSH_TAB";

    public static final String TIKTOK = "tiktok";
    public static final String FOR_YOU = "for_you";
    public static final String FRIENDS = "friends";
    public static final String INBOX = "inbox";
    public static final String PROFILE = "profile";

    /** TikTok's own tags for its tabs, as its tab switch and its notification handlers read them. */
    static final String HOME_TAG = "HOME";
    static final String FRIENDS_TAB_TAG = "FRIENDS_TAB";
    static final String FRIENDS_FEED_TAG = "FRIENDS_FEED";
    static final String INBOX_TAG = "NOTIFICATION";
    static final String PROFILE_TAG = "USER";

    private StartPage() {
    }

    /**
     * The tab a starting TikTok opens on, given the {@code tag} it worked out, the activity whose
     * intent started it and the state Android handed back, if any.
     */
    public static String coldStartTag(Activity activity, String tag, Bundle savedState) {
        try {
            HookStatus.bound(FAMILY, "cold start");
            if (savedState != null) return tag;
            String choice = Settings.START_PAGE.get();
            if (TIKTOK.equals(choice)) return tag;
            if (!isLauncherStart(activity == null ? null : activity.getIntent())) return tag;
            String target = tagFor(choice);
            if (target == null) return tag;
            HookStatus.bound(FAMILY, "opened on " + choice);
            return target;
        } catch (Throwable failure) {
            Logger.printException(() -> "Start page failed; TikTok opens on its own tab", failure);
            return tag;
        }
    }

    /** A tap on the app's icon: the launcher's own intent, with no page, link or notification tab in it. */
    static boolean isLauncherStart(Intent intent) {
        return intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && intent.getData() == null
                && !intent.hasExtra(PUSH_TAB);
    }

    /**
     * TikTok's tag for a choice, or nothing when the choice leaves it to TikTok or asks for a tab
     * the Feed tabs page hides. Friends is a bottom tab on some accounts and a feed tab across the
     * top on others, with a tag for each, so the tabs this phone has shown decide which.
     */
    static String tagFor(String choice) {
        if (FOR_YOU.equals(choice)) return HOME_TAG;
        if (PROFILE.equals(choice)) return PROFILE_TAG;
        if (INBOX.equals(choice)) {
            return bottomTabShown(BottomNavigationTabOptions.INBOX) ? INBOX_TAG : null;
        }
        if (FRIENDS.equals(choice)) {
            boolean bottom = BottomNavigationTabOptions.parseObservedKeys(
                    Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.get()).contains(BottomNavigationTabOptions.FRIENDS);
            if (bottom) return bottomTabShown(BottomNavigationTabOptions.FRIENDS) ? FRIENDS_TAB_TAG : null;
            return topTabShown(NavigationTabOptions.FRIENDS) ? FRIENDS_FEED_TAG : null;
        }
        return null;
    }

    private static boolean bottomTabShown(String key) {
        return !Settings.BOTTOM_NAVIGATION.get()
                || BottomNavigationTabOptions.parseEnabledKeys(Settings.BOTTOM_NAVIGATION_TABS.get()).contains(key);
    }

    private static boolean topTabShown(String key) {
        return !Settings.FEED_NAVIGATION.get()
                || NavigationTabOptions.parseEnabledKeys(Settings.FEED_NAVIGATION_TABS.get()).contains(key);
    }
}
