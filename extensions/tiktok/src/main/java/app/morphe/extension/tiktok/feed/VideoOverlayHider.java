/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 *   id/kzj                the right-hand column: avatar, like, comments, favourite, share
 *                         and the music disc, six id/eoh buttons in one LinearLayout
 *   id/ezp                the root of every feed survey card; the cell's survey ViewStubs
 *                         carry no inflatedId, so the card keeps its own layout id
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
    private static final String ACTION_BAR_ID = "kzj";
    private static final String SURVEY_ID = "ezp";

    private static final int LEGACY_STATUS_BAR_FLAGS = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final Map<String, Integer> RESOLVED_IDS = new HashMap<>();

    /**
     * Views this class hid, so turning a switch back off restores them and a view
     * TikTok hid for its own reasons is never forced back on.
     */
    private static final Map<View, Boolean> HIDDEN_HERE = new WeakHashMap<>();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    /** Whether this class, rather than TikTok, is the one holding the status bar away. */
    private static boolean statusBarHiddenHere;
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

            // The feed keeps the neighbouring cells inflated too, so the first match is
            // not always the cell on screen. Every cell's column and survey card is covered.
            ViewGroup root = activity.findViewById(android.R.id.content);
            int actionBarId = identifier(activity, APP_PACKAGE, ACTION_BAR_ID);
            int surveyId = identifier(activity, APP_PACKAGE, SURVEY_ID);
            for (View view : viewsWithId(root, actionBarId)) {
                setHidden(view, Settings.HIDE_FEED_ACTION_BAR.get());
            }
            for (View view : viewsWithId(root, surveyId)) {
                setHidden(view, Settings.HIDE_FEED_SURVEYS.get());
            }

            setStatusBarHidden(activity, Settings.HIDE_STATUS_BAR.get());
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

    /** Every descendant of {@code root} carrying {@code id}, in tree order. */
    static List<View> viewsWithId(View root, int id) {
        List<View> found = new ArrayList<>();
        if (id != 0 && root != null) {
            collect(root, id, found);
        }
        return found;
    }

    private static void collect(View view, int id, List<View> found) {
        if (view.getId() == id) {
            found.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                collect(group.getChildAt(i), id, found);
            }
        }
    }

    /**
     * Takes the status bar away for the whole activity, or gives it back if this class was
     * the one that hid it. TikTok sets its own system UI flags when it moves between
     * pages, so this runs on every layout pass and only touches the window when the bar
     * is not already in the wanted state. A bar TikTok hid itself is left to TikTok.
     */
    static void setStatusBarHidden(Activity activity, boolean hidden) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        if (hidden) {
            if (!isStatusBarHidden(decor)) {
                statusBarHiddenHere = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = decor.getWindowInsetsController();
                    if (controller != null) {
                        controller.setSystemBarsBehavior(
                                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                        controller.hide(WindowInsets.Type.statusBars());
                    }
                } else {
                    decor.setSystemUiVisibility(decor.getSystemUiVisibility() | LEGACY_STATUS_BAR_FLAGS);
                }
            }
            return;
        }
        if (statusBarHiddenHere) {
            statusBarHiddenHere = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                }
            } else {
                decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~LEGACY_STATUS_BAR_FLAGS);
            }
        }
    }

    private static boolean isStatusBarHidden(View decor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets insets = decor.getRootWindowInsets();
            // Before the first attach there is nothing to read; treat it as showing so the
            // request goes out and the layout pass after attach sees the real state.
            return insets != null && !insets.isVisible(WindowInsets.Type.statusBars());
        }
        return (decor.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
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
