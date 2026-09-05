/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import android.app.Activity;
import android.os.SystemClock;
import android.view.View;

import app.morphe.extension.shared.Logger;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Tells whether the video feed is the screen currently on show.
 *
 * The block button is an overlay on the activity content root, and TikTok keeps Inbox,
 * Profile, Friends and Shop inside that same activity. Without this the button follows
 * the user onto every screen.
 *
 * The bottom navigation tabs carry their selected state, so the Home tab being selected
 * is a reliable and cheap signal. Verified against TikTok 46.2.3, where the bottom
 * navigation ids are o1k Home, o1j Friends, o1g Create, o1l Inbox, o1m Profile.
 */
final class FeedVisibility {
    /** Bottom navigation Home tab on TikTok 46.2.3. */
    private static final String HOME_TAB_RESOURCE_NAME = "o1k";

    private static WeakReference<View> homeTabReference = new WeakReference<>(null);
    private static volatile boolean warnedMissing;

    /**
     * When the bottom navigation was last seen disappearing, on the monotonic clock, or
     * -1 while it is showing.
     */
    private static long navHiddenAtMs = -1L;

    /**
     * A video page that opens from a profile grid or search hides the navigation and
     * reports its author at about the same moment, in either order. A report this close
     * to the hide is taken as belonging to the new page rather than the feed behind it.
     */
    private static final long REPORT_GRACE_MS = 1_500L;

    /**
     * Fragment back stack depth when the feed last reported an author, or -1 if unknown.
     * Backing out of a video opened from a profile grid pops that page, which changes the
     * depth while the navigation stays hidden and the last report stays recent. Comparing
     * depths is what tells that state from the video page itself.
     */
    private static volatile int depthAtReport = -1;

    private static volatile Method fragmentManagerMethod;
    private static volatile Method backStackCountMethod;
    private static volatile boolean stackUnavailable;

    private FeedVisibility() {
    }

    /**
     * @return true when the feed is showing. Unknown states report true so a TikTok build
     *         that renames the tab loses the hiding behaviour rather than the button.
     */
    static boolean isOnFeed(Activity activity) {
        View homeTab = homeTab(activity);
        if (homeTab == null) {
            return true;
        }
        if (homeTab.isShown()) {
            navHiddenAtMs = -1L;
            return homeTab.isSelected();
        }

        // The bottom navigation is hidden. That is either a profile page pushed over the
        // feed, which should hide the button, or a video page opened from a profile grid
        // or search, which should keep it. Only the video page reports an author after
        // the navigation goes, so the order of those two events tells them apart.
        if (navHiddenAtMs < 0) {
            navHiddenAtMs = SystemClock.elapsedRealtime();
        }
        if (CurrentVideoAuthor.lastReportMs() < navHiddenAtMs - REPORT_GRACE_MS) {
            return false;
        }

        // The report is recent enough, but the page that made it may have been popped
        // since, with the navigation still hidden behind whatever is left.
        int depthNow = backStackDepth(activity);
        int depthThen = depthAtReport;
        return depthNow < 0 || depthThen < 0 || depthNow == depthThen;
    }

    /** Called by CurrentVideoAuthor whenever the feed reports an item. */
    static void noteReport(Activity activity) {
        depthAtReport = activity == null ? -1 : backStackDepth(activity);
    }

    /**
     * @return the AndroidX fragment back stack depth, or -1 when it cannot be read. The
     *         fragment classes are not on the extension's compile path, so this goes
     *         through reflection on real, unobfuscated AndroidX method names.
     */
    private static int backStackDepth(Activity activity) {
        if (stackUnavailable) {
            return -1;
        }
        try {
            Method managerMethod = fragmentManagerMethod;
            if (managerMethod == null) {
                managerMethod = activity.getClass().getMethod("getSupportFragmentManager");
                fragmentManagerMethod = managerMethod;
            }
            Object manager = managerMethod.invoke(activity);
            if (manager == null) {
                return -1;
            }

            Method countMethod = backStackCountMethod;
            if (countMethod == null) {
                countMethod = manager.getClass().getMethod("getBackStackEntryCount");
                backStackCountMethod = countMethod;
            }
            Object count = countMethod.invoke(manager);
            return count instanceof Integer ? (Integer) count : -1;
        } catch (Throwable ex) {
            stackUnavailable = true;
            Logger.printInfo(() -> "Fragment back stack not readable; page pops will not hide the block button");
            return -1;
        }
    }

    private static View homeTab(Activity activity) {
        View cached = homeTabReference.get();
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }

        try {
            int id = activity.getResources().getIdentifier(
                    HOME_TAB_RESOURCE_NAME, "id", activity.getPackageName());
            if (id == 0) {
                warnMissing();
                return null;
            }

            View homeTab = activity.findViewById(id);
            if (homeTab == null) {
                return null;
            }

            homeTabReference = new WeakReference<>(homeTab);
            return homeTab;
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not resolve the Home tab", ex);
            return null;
        }
    }

    private static void warnMissing() {
        if (warnedMissing) {
            return;
        }
        warnedMissing = true;
        Logger.printInfo(() -> "Bottom navigation Home tab '" + HOME_TAB_RESOURCE_NAME
                + "' not found. The block button cannot hide itself off the feed.");
    }
}
