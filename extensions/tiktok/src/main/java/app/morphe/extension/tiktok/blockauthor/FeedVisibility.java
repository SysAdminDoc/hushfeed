/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

import android.app.Activity;
import android.view.View;

import app.morphe.extension.shared.Logger;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

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
public final class FeedVisibility {
    /** Bottom navigation Home tab on TikTok 46.2.3. */
    private static final String HOME_TAB_RESOURCE_NAME = "o1k";

    private static WeakReference<View> homeTabReference = new WeakReference<>(null);
    private static volatile boolean warnedMissing;

    // Fragment instances are weak keys, and values never retain the fragment or its view.
    // Lifecycle hooks are injected into TikTok's kept DetailPageFragment methods.
    private static final Map<Object, PageState> DETAIL_PAGES = new WeakHashMap<>();

    private static final class PageState {
        WeakReference<View> view = new WeakReference<>(null);
        boolean resumed;
        boolean visible = true;
    }

    public static void onDetailView(Object page, View view) {
        DETAIL_PAGES.computeIfAbsent(page, ignored -> new PageState()).view = new WeakReference<>(view);
    }

    public static void onDetailResume(Object page) {
        DETAIL_PAGES.computeIfAbsent(page, ignored -> new PageState()).resumed = true;
    }

    public static void onDetailPause(Object page) {
        PageState state = DETAIL_PAGES.get(page);
        if (state != null) state.resumed = false;
    }

    public static void onDetailVisibility(Object page, boolean visible) {
        DETAIL_PAGES.computeIfAbsent(page, ignored -> new PageState()).visible = visible;
    }

    public static void onDetailDestroyed(Object page) {
        DETAIL_PAGES.remove(page);
    }

    static boolean isDetailVisible() {
        for (PageState state : DETAIL_PAGES.values()) {
            View view = state.view.get();
            if (state.resumed && state.visible && view != null && view.isShown()) return true;
        }
        return false;
    }

    private FeedVisibility() {
    }

    /**
     * @return true when the feed is showing. Unknown states report true so a TikTok build
     *         that renames the tab loses the hiding behaviour rather than the button.
     */
    public static boolean isOnFeed(Activity activity) {
        View homeTab = homeTab(activity);
        if (homeTab == null) {
            return true;
        }
        if (homeTab.isShown()) return homeTab.isSelected();
        return isDetailVisible();
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
