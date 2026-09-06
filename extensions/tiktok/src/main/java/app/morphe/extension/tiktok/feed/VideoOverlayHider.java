/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.Map;

/**
 * Hides controls TikTok lays over the video player.
 *
 * Ids were read off the live view hierarchy of TikTok 46.2.3 with a video paused:
 * <pre>
 *   df_search_biz:id/fb   full screen layer the visual search prompt lives in
 *   df_search_biz:id/cn   the clickable "Search this image" pill inside it
 *   id/jup                the Live entrance, top left, 158 px square, no description
 * </pre>
 * The first two belong to TikTok's search dynamic feature module, so they resolve under
 * that module's package name rather than the app's. Views are re-hidden on every layout
 * pass, since TikTok shows the prompt again for each video that has something to search.
 */
public final class VideoOverlayHider {
    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    private static final String SEARCH_MODULE_PACKAGE = APP_PACKAGE + ".df_search_biz";
    private static final String[] VISUAL_SEARCH_IDS = {"fb", "cn"};
    private static final String LIVE_ENTRANCE_ID = "jup";

    /** The caption under the creator's name, and the music cover block beside it. */
    private static final String CAPTION_ID = "desc";
    private static final String MUSIC_ID = "videomusiccoverblock";

    private static final Map<String, Integer> RESOLVED_IDS = new HashMap<>();

    /**
     * Views this class hid, so turning a switch back off restores them and a view
     * TikTok hid for its own reasons is never forced back on.
     */
    private static final Map<View, Boolean> HIDDEN_HERE = new WeakHashMap<>();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener listener;

    private VideoOverlayHider() {
    }

    /** Called from the patched {@code MainActivity.onCreate}; the work is posted. */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        Utils.runOnMainThread(() -> installNow(activity));
    }

    private static void installNow(Activity activity) {
        try {
            if (activity.isFinishing()) {
                return;
            }
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Logger.printInfo(() -> "Video overlay hider found no content view to watch");
                return;
            }
            if (listener != null && activityReference.get() == activity) {
                return;
            }

            listener = VideoOverlayHider::apply;
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            activityReference = new WeakReference<>(activity);
            Logger.printDebug(() -> "Video overlay hider installed");
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not install the video overlay hider", ex);
        }
    }

    private static void apply() {
        try {
            Activity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            if (Settings.HIDE_VISUAL_SEARCH.get()) {
                for (String name : VISUAL_SEARCH_IDS) {
                    hide(activity, SEARCH_MODULE_PACKAGE, name);
                }
            }
            if (Settings.HIDE_LIVE_ENTRANCE.get()) {
                hide(activity, APP_PACKAGE, LIVE_ENTRANCE_ID);
            }

            // These two are ordinary feed furniture rather than a prompt, so they come back
            // when the switch goes off instead of staying gone until the next video.
            setHidden(view(activity, APP_PACKAGE, CAPTION_ID), Settings.HIDE_FEED_CAPTION.get());
            setHidden(view(activity, APP_PACKAGE, MUSIC_ID), Settings.HIDE_FEED_MUSIC.get());
        } catch (Throwable ex) {
            Logger.printException(() -> "Video overlay hider failed", ex);
        }
    }

    private static void hide(Activity activity, String packageName, String name) {
        View view = view(activity, packageName, name);
        if (view != null && view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }

    private static View view(Activity activity, String packageName, String name) {
        int id = identifier(activity, packageName, name);
        return id == 0 ? null : activity.findViewById(id);
    }

    /**
     * Hides a view and remembers it, or puts back one this class hid. A view that was
     * already gone when the switch went on is left alone on the way back, because TikTok
     * had its own reason for that.
     */
    static void setHidden(View view, boolean hidden) {
        if (view == null) {
            return;
        }

        if (hidden) {
            if (view.getVisibility() != View.GONE) {
                HIDDEN_HERE.put(view, Boolean.TRUE);
                view.setVisibility(View.GONE);
            }
            return;
        }

        if (HIDDEN_HERE.remove(view) != null && view.getVisibility() == View.GONE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Resolves a resource id by name once and remembers it, including a miss. The search
     * module's ids only exist once that module has loaded, so a miss for those is retried
     * rather than cached.
     */
    private static int identifier(Activity activity, String packageName, String name) {
        String key = packageName + ":" + name;
        Integer cached = RESOLVED_IDS.get(key);
        if (cached != null) {
            return cached;
        }

        int id;
        try {
            id = activity.getResources().getIdentifier(name, "id", packageName);
        } catch (Throwable ignored) {
            id = 0;
        }
        if (id != 0) {
            RESOLVED_IDS.put(key, id);
        } else if (!SEARCH_MODULE_PACKAGE.equals(packageName)) {
            RESOLVED_IDS.put(key, 0);
            Logger.printInfo(() -> "Overlay view id '" + name + "' not found in this TikTok build");
        }
        return id;
    }
}
