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
import app.morphe.extension.shared.ResourceIdCache;

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

    /** Bottom navigation Inbox tab on the same build. */
    private static final String INBOX_TAB_RESOURCE_NAME = "o1l";

    private static WeakReference<View> homeTabReference = new WeakReference<>(null);
    private static WeakReference<View> inboxTabReference = new WeakReference<>(null);

    /**
     * Names resolved once each. The view lookup below has to run again whenever the cached view
     * is gone, and for a tab that is genuinely absent, which is what a reader who hid Inbox in
     * Feed navigation has, that is every call. Resolving the name each time is a string search
     * through TikTok's resource table, and the hold's panel asks once a second for as long as it
     * is up. InboxFilter made the same fix for the same id.
     */
    private static final ResourceIdCache IDS = new ResourceIdCache();
    private static volatile boolean warnedMissing;

    // Fragment instances are weak keys, and values never retain the fragment or its view.
    // Lifecycle hooks are injected into TikTok's kept DetailPageFragment methods.
    private static final Map<Object, PageState> DETAIL_PAGES = new WeakHashMap<>();

    private static final class PageState {
        WeakReference<View> view = new WeakReference<>(null);
        boolean resumed;
        boolean visible = true;
    }

    private static PageState detailPage(Object page) {
        PageState state = DETAIL_PAGES.get(page);
        if (state == null) {
            state = new PageState();
            DETAIL_PAGES.put(page, state);
        }
        return state;
    }

    public static void onDetailView(Object page, View view) {
        detailPage(page).view = new WeakReference<>(view);
    }

    public static void onDetailResume(Object page) {
        detailPage(page).resumed = true;
    }

    public static void onDetailPause(Object page) {
        PageState state = DETAIL_PAGES.get(page);
        if (state != null) state.resumed = false;
    }

    public static void onDetailVisibility(Object page, boolean visible) {
        detailPage(page).visible = visible;
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

    /**
     * The Home tab itself, for anything that has to draw around the navigation rather than
     * over it. Null when this build does not have the id, which is the same case
     * {@link #isOnFeed} treats as "assume the feed".
     */
    public static View homeTabView(Activity activity) {
        return homeTab(activity);
    }

    /**
     * The Inbox tab, for anything that wants to send the reader there.
     *
     * <p>Null when this build renames it and, just as usefully, when the reader has hidden Inbox
     * in Feed navigation: the filter drops the tab from the model, so no view is ever built. A
     * caller can treat null as "there is no Inbox to open" without reasoning about the setting.
     */
    public static View inboxTabView(Activity activity) {
        return tab(activity, INBOX_TAB_RESOURCE_NAME, inboxTabReference,
                reference -> inboxTabReference = reference);
    }

    private static View homeTab(Activity activity) {
        return tab(activity, HOME_TAB_RESOURCE_NAME, homeTabReference,
                reference -> homeTabReference = reference);
    }

    /** Holds the view weakly and re-resolves it once the old one leaves the window. */
    private static View tab(
            Activity activity,
            String resourceName,
            WeakReference<View> cache,
            Consumer<WeakReference<View>> store
    ) {
        View cached = cache.get();
        if (cached != null && cached.isAttachedToWindow()) {
            return cached;
        }

        try {
            int id = IDS.resolve(activity.getResources(), activity.getPackageName(),
                    resourceName, false);
            if (id == 0) {
                if (HOME_TAB_RESOURCE_NAME.equals(resourceName)) warnMissing();
                return null;
            }

            View tab = activity.findViewById(id);
            if (tab == null) {
                return null;
            }

            store.accept(new WeakReference<>(tab));
            return tab;
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not resolve the " + resourceName + " tab", ex);
            return null;
        }
    }

    /** API 24's own is above the payload's floor. */
    private interface Consumer<T> {
        void accept(T value);
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
