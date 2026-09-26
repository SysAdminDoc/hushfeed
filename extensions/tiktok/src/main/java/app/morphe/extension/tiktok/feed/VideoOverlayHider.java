/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch;
import app.morphe.extension.tiktok.navigation.NavigationTabsFilter;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides controls TikTok lays over the video player.
 *
 * Ids were read off the live view hierarchy of TikTok 47.0.3 with a video paused, apart from
 * the two search module ids, which were read off the module's code in the APK. Where 47.0.3
 * renamed a 46.x view, only the 47.0.3 name is looked up: TikTok hands the old short names
 * out again, and on 47.0.3 each one names some other view, which a fallback would have hidden
 * in any cell that lacked the current one.
 * <pre>
 *   df_search_biz:id/fo   the full screen layer the visual search prompt lives in, the root
 *                         of the layout SearchVisualSearchContainerComponentV2 inflates.
 *                         46.2.3 called it fb, which on 47.0.3 is a row of the visual search
 *                         camera page
 *   df_search_biz:id/d4   the clickable visual search pill, the root of both layouts the VTag
 *                         processors inflate. 46.2.3 called it cn, which on 47.0.3 is a row of
 *                         the floating card in search results
 *   id/k_5                the Live entrance, top left, 158 px square, no description
 *   id/liy                the interaction area over the video: the right-hand column's slots,
 *                         the caption block and the music row
 *   id/f7u                the root of every feed survey card; the cell's survey ViewStubs
 *                         carry no inflatedId, so the card keeps its own layout id. All
 *                         seven layouts those stubs inflate have it on 47.0.3, where 46.2.3
 *                         called it ezp (on 47.0.3 ezp is a label in the paid series panel).
 *                         It is a shared root id: the profile's Favorites page is a
 *                         LinearLayout with the same id.
 *   id/view_rootview      the root of every feed cell (VideoViewCellRootView). Every id below
 *                         it here is feed furniture and lives inside one. id/long_press_layout
 *                         is a sibling layer under it, not an ancestor of the rail: scoping
 *                         to that from 0.35.0 hid nothing in the right column (S22, 2026-09-17,
 *                         read off the live tree with the probe's views action).
 *   id/uvy                the strip across the top holding For You, Following and the rest
 *   id/i98 id/g6r id/ep7  the avatar, like and comment controls
 *   id/i7r id/pnp id/w_2  the favourite, music and share controls
 *   id/g6t id/ej_         the rows under like, comment, favourite and share
 *   id/i6r id/w6_         whose icon stays put when its count goes
 *   id/g6s id/ej9         the numeric TextViews inside those rows
 *   id/i6q id/w69
 * </pre>
 * The first two belong to TikTok's search dynamic feature module, so they resolve under
 * that module's package name rather than the app's. Views are re-hidden on every layout
 * pass, since TikTok shows the prompt again for each video that has something to search.
 */
public final class VideoOverlayHider {
    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    private static final String SEARCH_MODULE_PACKAGE = APP_PACKAGE + ".df_search_biz";
    private static final String[] VISUAL_SEARCH_LAYER_IDS = {"fo"};
    private static final String[] VISUAL_SEARCH_PILL_IDS = {"d4"};
    private static final String[] LIVE_ENTRANCE_IDS = {"k_5"};

    /** The caption under the creator's name, and the music cover block beside it. */
    private static final String[] CAPTION_IDS = {"desc"};
    private static final String[] MUSIC_IDS = {"videomusiccoverblock"};
    private static final String[] ACTION_BAR_IDS = {"liy"};
    private static final String[] SURVEY_IDS = {"f7u"};
    private static final String[] TAB_STRIP_IDS = {"uvy"};
    /**
     * The feed cell root. Furniture is only hidden underneath one: Hide feed surveys used to
     * take every survey card id in the window, and on the profile that is the Favorites tab's whole
     * page, which showed as an empty tab (a Galaxy S25, 2026-09-16, found by restoring the
     * settings one group at a time). The tab strip sits above the cells and is the one
     * target that stays window-wide. A build that renames the cell root falls back to the
     * whole window, and the hook status names the miss.
     */
    private static final String CELL_ROOT_ID = "view_rootview";
    /** Whether the last pass left the rail buttons scaled, so the next one can put them back. */
    private static boolean scaledLastPass;
    /** The index of the music row in {@link #RAIL_BUTTON_IDS}; it spans the width and is not scaled. */
    private static final int RAIL_MUSIC_INDEX = 4;
    /**
     * The one size the column has room for. The slots sit at a 169 px pitch on the S22 with a
     * 126 px icon area and the count in the rest, so an icon grown from its bottom edge by a
     * quarter still clears the count under it and the button above it; anything more lands on
     * a neighbour. A larger stored value, from the days the row offered 1.5x and 2x, is read as
     * this.
     */
    static final float MAX_TOUCH_SCALE = 1.25f;
    /**
     * The rail buttons the last walk scaled, put back to the chosen size before every frame
     * while a size other than Normal is chosen.
     *
     * <p>TikTok animates scaleX and scaleY on these very buttons (the like bounce, the avatar
     * pulse), and an animation ends by writing 1 back. The walk scales on a layout pass and an
     * animation is not a layout, which is why the S22 showed the first video's heart at 2x and
     * the next video's at 1x, with the comment, save and share buttons never enlarged at all.
     * A pre-draw listener runs before every frame, so a 1 that TikTok wrote back is corrected
     * before it is drawn. The values are compared first: setting a scale invalidates, and an
     * unconditional write from pre-draw would be a frame that never stops.
     */
    private static final List<WeakReference<View>> SCALED = new ArrayList<>();
    private static float scaleWanted = 1f;
    private static WeakReference<View> rescaleRoot = new WeakReference<>(null);
    private static final ViewTreeObserver.OnPreDrawListener RESCALE = () -> {
        reapplyScale();
        return true;
    };
    /** The row under each rail button holding its count, without the button itself. */
    private static final String[] LIKE_COUNT_ROW_IDS = {"g6t"};
    private static final String[] COMMENT_COUNT_ROW_IDS = {"ej_"};
    private static final String[] FAVORITE_COUNT_ROW_IDS = {"i6r"};
    private static final String[] SHARE_COUNT_ROW_IDS = {"w6_"};
    private static final String[][] RAIL_COUNT_ROW_IDS = {
            LIKE_COUNT_ROW_IDS, COMMENT_COUNT_ROW_IDS,
            FAVORITE_COUNT_ROW_IDS, SHARE_COUNT_ROW_IDS
    };
    /** The numeric text inside each row, retained by layouts that replace the row wrapper. */
    private static final String[] LIKE_COUNT_TEXT_IDS = {"g6s"};
    private static final String[] COMMENT_COUNT_TEXT_IDS = {"ej9"};
    private static final String[] FAVORITE_COUNT_TEXT_IDS = {"i6q"};
    private static final String[] SHARE_COUNT_TEXT_IDS = {"w69"};
    private static final String[][] RAIL_COUNT_TEXT_IDS = {
            LIKE_COUNT_TEXT_IDS, COMMENT_COUNT_TEXT_IDS,
            FAVORITE_COUNT_TEXT_IDS, SHARE_COUNT_TEXT_IDS
    };
    /**
     * Which button each count belongs to, as an index into {@link #RAIL_BUTTON_IDS}: like,
     * comment, favourite, share. A count goes with its button. "Hide like button" used to
     * leave the number standing under an empty space, half under the avatar, which read as
     * the switch doing nothing, and was reported as exactly that.
     */
    private static final int[] RAIL_COUNT_BUTTON_INDEX = {1, 2, 3, 5};
    /** The six buttons inside the action column, in the order they are stacked. */
    private static final String[] AVATAR_BUTTON_IDS = {"i98"};
    private static final String[] LIKE_BUTTON_IDS = {"g6r"};
    private static final String[] COMMENT_BUTTON_IDS = {"ep7"};
    private static final String[] FAVORITE_BUTTON_IDS = {"i7r"};
    private static final String[] MUSIC_BUTTON_IDS = {"pnp"};
    private static final String[] SHARE_BUTTON_IDS = {"w_2"};
    private static final String[][] RAIL_BUTTON_IDS = {
            AVATAR_BUTTON_IDS, LIKE_BUTTON_IDS, COMMENT_BUTTON_IDS,
            FAVORITE_BUTTON_IDS, MUSIC_BUTTON_IDS, SHARE_BUTTON_IDS
    };
    private static final int CAPTION_TARGET = 0;
    private static final int MUSIC_TARGET = 1;
    private static final int ACTION_BAR_TARGET = 2;
    private static final int SURVEY_TARGET = 3;
    private static final int TAB_STRIP_TARGET = 4;
    private static final int RAIL_TARGET_START = 5;
    private static final int COUNT_ROW_TARGET_START = RAIL_TARGET_START + RAIL_BUTTON_IDS.length;
    private static final int COUNT_TEXT_TARGET_START = COUNT_ROW_TARGET_START
            + RAIL_COUNT_ROW_IDS.length;
    private static final String[][] TRAVERSAL_TARGET_IDS = traversalTargetIds();
    private static final int LOGICAL_TARGET_COUNT = TRAVERSAL_TARGET_IDS.length;
    private static final int TRAVERSAL_TARGET_COUNT = candidateCount(TRAVERSAL_TARGET_IDS);

    private static final int LEGACY_STATUS_BAR_FLAGS = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private static final ResourceIdCache RESOURCE_IDS = new ResourceIdCache();
    private static final TraversalScratch TRAVERSAL = new TraversalScratch(TRAVERSAL_TARGET_COUNT);

    /**
     * Views this class hid, with the visibility each had before, so turning a switch back
     * off restores exactly that and a view TikTok hid for its own reasons is never forced
     * back on.
     */
    private static final Map<View, Integer> HIDDEN_HERE = new WeakHashMap<>();

    /**
     * How long a status bar may stay visible before it is hidden again on Android 11 and
     * up. A swipe from the top shows the bar transiently and the system takes it away by
     * itself, so re-hiding inside this window would only snap a peek shut. TikTok's own
     * show calls are caught by the layout pass after the window ends.
     */
    static final long STATUS_BAR_PEEK_MS = 4000L;

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    /** Whether this class, rather than TikTok, is the one holding the status bar away. */
    private static boolean statusBarHiddenHere;
    private static long statusBarHiddenAt;
    private static final GlobalLayoutHook LAYOUT_HOOK = new GlobalLayoutHook();

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
                LAYOUT_HOOK.detach();
                return;
            }
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                LAYOUT_HOOK.detach();
                Logger.printInfo(() -> "Video overlay hider found no content view to watch");
                return;
            }
            boolean installed = LAYOUT_HOOK.install(root, VideoOverlayHider::apply);
            activityReference = new WeakReference<>(activity);
            if (installed) {
                Logger.printDebug(() -> "Video overlay hider installed");
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not install the video overlay hider", ex);
        }
    }

    private static void apply() {
        Activity activity = activityReference.get();
        if (activity == null) {
            LAYOUT_HOOK.detach();
            return;
        }
        if (activity.isFinishing()) {
            LAYOUT_HOOK.detach();
            return;
        }
        applyTo(activity);
    }

    /** One pass over {@code activity}, the same one the layout listener runs. */
    static void applyTo(Activity activity) {
        try {
            if (activity.isFinishing()) {
                return;
            }
            // The names above the feed follow their own switch on the strip TikTok's tab filter
            // hides; this pass is the one thing that runs on every layout, so it carries the ask.
            NavigationTabsFilter.refreshTopTabStrips();

            if (Settings.HIDE_VISUAL_SEARCH.get()) {
                hide(activity, SEARCH_MODULE_PACKAGE, VISUAL_SEARCH_LAYER_IDS);
                hide(activity, SEARCH_MODULE_PACKAGE, VISUAL_SEARCH_PILL_IDS);
            }
            if (Settings.HIDE_LIVE_ENTRANCE.get()) {
                hide(activity, APP_PACKAGE, LIVE_ENTRANCE_IDS);
            }

            // These are ordinary feed furniture rather than a prompt, so they come back
            // when their switch goes off instead of staying gone until the next video. The
            // feed keeps the neighbouring cells inflated too, so the first match is not
            // always the cell on screen: every cell is covered in one walk of the tree,
            // and the walk is skipped while nothing is on and nothing is left to restore.
            // Caption above comments puts the whole caption at the top of the comments, so the
            // one over the video goes with it rather than showing twice.
            boolean caption = Settings.HIDE_FEED_CAPTION.get() || Settings.CAPTION_ABOVE_COMMENTS.get();
            boolean music = Settings.HIDE_FEED_MUSIC.get();
            boolean actionBar = Settings.HIDE_FEED_ACTION_BAR.get();
            boolean surveys = Settings.HIDE_FEED_SURVEYS.get();
            // Clear display hides the tab strip when it starts and TikTok brings it back on
            // the first swipe. Following the live state keeps it away until the tap that ends
            // the mode. The persisted setting cannot be used here: the automatic path never
            // writes it, so it would answer false for exactly the case this is meant to fix.
            boolean tabStrip = RememberClearDisplayPatch.isClearDisplayNow();
            boolean counts = Settings.HIDE_RAIL_COUNTS.get();
            boolean[] rail = TRAVERSAL.rail;
            updateRailButtonsWanted(rail);
            boolean anyRail = counts;
            for (boolean one : rail) {
                anyRail |= one;
            }
            // The rail scale needs the same walk the hiding uses, so it joins the gate. Without
            // it a build with every hide switch off never traversed and the chosen size did
            // nothing at all (seen on the S22, 2026-09-16). scaledLastPass keeps the walk alive
            // for the one pass that writes the buttons back to 1.
            float touchScale = 1f;
            try {
                touchScale = Float.parseFloat(Settings.TOUCH_TARGET_SCALE.get());
            } catch (NumberFormatException ignored) {
            }
            touchScale = Math.min(MAX_TOUCH_SCALE, Math.max(1f, touchScale));
            if (caption || music || actionBar || surveys || tabStrip || anyRail
                    || !HIDDEN_HERE.isEmpty() || touchScale != 1f || scaledLastPass) {
                ViewGroup root = activity.findViewById(android.R.id.content);
                int[] ids = TRAVERSAL.ids;
                boolean[] hidden = TRAVERSAL.hidden;
                boolean[] wanted = TRAVERSAL.wanted;
                wanted[CAPTION_TARGET] = caption;
                wanted[MUSIC_TARGET] = music;
                wanted[ACTION_BAR_TARGET] = actionBar;
                wanted[SURVEY_TARGET] = surveys;
                wanted[TAB_STRIP_TARGET] = tabStrip;
                for (int i = 0; i < RAIL_BUTTON_IDS.length; i++) {
                    wanted[RAIL_TARGET_START + i] = rail[i];
                }
                for (int i = 0; i < RAIL_COUNT_ROW_IDS.length; i++) {
                    boolean hideCount = counts || rail[RAIL_COUNT_BUTTON_INDEX[i]];
                    wanted[COUNT_ROW_TARGET_START + i] = hideCount;
                    wanted[COUNT_TEXT_TARGET_START + i] = hideCount;
                }

                int candidateAt = 0;
                boolean[] needsCell = TRAVERSAL.needsCell;
                for (int target = 0; target < TRAVERSAL_TARGET_IDS.length; target++) {
                    boolean hideTarget = wanted[target];
                    for (String name : TRAVERSAL_TARGET_IDS[target]) {
                        ids[candidateAt] = resolveIdentifier(
                                activity, APP_PACKAGE, name, false);
                        hidden[candidateAt] = hideTarget;
                        needsCell[candidateAt] = target != TAB_STRIP_TARGET;
                        candidateAt++;
                    }
                }

                List<List<View>> found = TRAVERSAL.found;
                for (List<View> views : found) {
                    views.clear();
                }
                int cellId = identifier(activity, APP_PACKAGE, CELL_ROOT_ID);
                try {
                    collect(root, ids, found, cellId, cellId == 0, needsCell);
                    selectCurrentTargets(found, TRAVERSAL.selected);
                    applySelectedTargets(ids, hidden, found, TRAVERSAL.selected,
                            touchScale != 1f);
                    // The size goes on the icon inside each button, not the button. The slots
                    // are packed with no room between them, so a grown button lands on its
                    // neighbours; an icon grown from its bottom edge stays inside its slot at
                    // the one size the column has room for. The parents between the icon and
                    // the column stop clipping, or the growth is cut off at the slot's edge and
                    // never seen. Written every pass, 1 included, so returning the row to
                    // Normal puts the icons back. The music row spans the width and is left alone.
                    List<View> scaled = TRAVERSAL.scaled;
                    scaled.clear();
                    for (int i = 0; i < RAIL_BUTTON_IDS.length; i++) {
                        if (i == RAIL_MUSIC_INDEX) continue;
                        int selected = TRAVERSAL.selected[RAIL_TARGET_START + i];
                        if (selected < 0) continue;
                        for (View button : found.get(selected)) {
                            collectIcons(button, scaled);
                        }
                    }
                    int actionBarId = selectedId(
                            ids, TRAVERSAL.selected[ACTION_BAR_TARGET]);
                    for (View icon : scaled) {
                        if (touchScale != 1f) unclipUpTo(icon, actionBarId);
                        scaleView(icon, touchScale);
                    }
                    if (touchScale != 1f) {
                        final int count = scaled.size();
                        final float scale = touchScale;
                        Logger.printDebug(() -> "Rail scale " + scale + " written to " + count + " icons");
                    }
                    rememberScaled(scaled, touchScale, root);
                    scaledLastPass = touchScale != 1f;
                } finally {
                    for (List<View> views : found) {
                        views.clear();
                    }
                    TRAVERSAL.scaled.clear();
                }
            }

            setStatusBarHidden(activity, Settings.HIDE_STATUS_BAR.get());
        } catch (Throwable ex) {
            Logger.printException(() -> "Video overlay hider failed", ex);
        }
    }

    /**
     * The icon-sized leaves under a rail button: the glyph itself, and for the avatar button
     * its picture, its ring and the follow plus. Read off the S22 (2026-09-17): the like heart
     * is a 127 px leaf inside a 180 by 126 area, the comment and share glyphs 90 px, the count
     * rows 180 wide, so a leaf between 22 and 52 dp on both sides is a glyph and nothing else.
     */
    static void collectIcons(View view, List<View> out) {
        if (view instanceof ViewGroup && ((ViewGroup) view).getChildCount() > 0) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                collectIcons(group.getChildAt(i), out);
            }
            return;
        }
        if (view.getVisibility() != View.VISIBLE) return;
        float density = view.getResources().getDisplayMetrics().density;
        int min = Math.round(22 * density);
        int max = Math.round(52 * density);
        int width = view.getWidth();
        int height = view.getHeight();
        if (width >= min && width <= max && height >= min && height <= max) out.add(view);
    }

    /**
     * Lets the growth be drawn. Every ViewGroup clips its children to its own bounds by
     * default, and the icon sits in a frame its own size, so a scaled glyph was cut off at the
     * frame's edge. Unclipped up to the interaction area, which is the whole column and clips
     * nothing that matters.
     */
    private static void unclipUpTo(View icon, int stopId) {
        android.view.ViewParent parent = icon.getParent();
        for (int depth = 0; depth < 8 && parent instanceof ViewGroup; depth++) {
            ViewGroup group = (ViewGroup) parent;
            if (stopId != 0 && group.getId() == stopId) break;
            if (group.getClipChildren()) group.setClipChildren(false);
            if (group.getClipToPadding()) group.setClipToPadding(false);
            parent = group.getParent();
        }
    }

    /**
     * The scale is written whatever the measured size, because a button skipped for having no
     * width yet was never revisited and stayed at 1 while its neighbours grew. The pivot is the
     * bottom centre, so a grown glyph rises into the free space above it and leaves the count
     * under it readable; it needs a real size, so it waits.
     */
    private static void scaleView(View view, float scale) {
        if (view.getWidth() > 0) {
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(view.getHeight());
        }
        if (view.getScaleX() != scale) view.setScaleX(scale);
        if (view.getScaleY() != scale) view.setScaleY(scale);
    }

    /** Keeps the scaled icons for the pre-draw pass, and the pass itself on the root. */
    private static void rememberScaled(List<View> scaled, float touchScale, View root) {
        SCALED.clear();
        scaleWanted = touchScale;
        if (touchScale != 1f) {
            for (View view : scaled) {
                SCALED.add(new WeakReference<>(view));
            }
        }
        View watched = rescaleRoot.get();
        if (touchScale != 1f) {
            if (watched != root && root != null) {
                if (watched != null && watched.getViewTreeObserver().isAlive()) {
                    watched.getViewTreeObserver().removeOnPreDrawListener(RESCALE);
                }
                root.getViewTreeObserver().addOnPreDrawListener(RESCALE);
                rescaleRoot = new WeakReference<>(root);
            }
        } else if (watched != null) {
            if (watched.getViewTreeObserver().isAlive()) {
                watched.getViewTreeObserver().removeOnPreDrawListener(RESCALE);
            }
            rescaleRoot = new WeakReference<>(null);
        }
    }

    /** When the frame pass last said what it put back, so the log is not written per frame. */
    private static long rescaleLoggedAt;

    /** The pre-draw pass: every remembered button back to the chosen size, if it moved. */
    static void reapplyScale() {
        float scale = scaleWanted;
        if (scale == 1f) return;
        int corrected = 0;
        int seen = 0;
        for (WeakReference<View> held : SCALED) {
            View view = held.get();
            if (view == null || !view.isAttachedToWindow()) continue;
            seen++;
            if (view.getScaleX() != scale || view.getScaleY() != scale) corrected++;
            scaleView(view, scale);
        }
        long now = SystemClock.uptimeMillis();
        if (corrected > 0 && now - rescaleLoggedAt > 2000L) {
            rescaleLoggedAt = now;
            final int put = corrected;
            final int held = seen;
            Logger.printDebug(() -> "Rail scale " + scale + " put back on " + put + " of " + held + " icons");
        }
    }

    private static void updateRailButtonsWanted(boolean[] rail) {
        rail[0] = Settings.HIDE_RAIL_FOLLOW.get();
        rail[1] = Settings.HIDE_RAIL_LIKE.get();
        rail[2] = Settings.HIDE_RAIL_COMMENTS.get();
        rail[3] = Settings.HIDE_RAIL_FAVOURITE.get();
        rail[4] = Settings.HIDE_RAIL_MUSIC.get();
        rail[5] = Settings.HIDE_RAIL_SHARE.get();
    }

    private static String[][] traversalTargetIds() {
        String[][] targets = new String[5 + RAIL_BUTTON_IDS.length
                + RAIL_COUNT_ROW_IDS.length + RAIL_COUNT_TEXT_IDS.length][];
        targets[CAPTION_TARGET] = CAPTION_IDS;
        targets[MUSIC_TARGET] = MUSIC_IDS;
        targets[ACTION_BAR_TARGET] = ACTION_BAR_IDS;
        targets[SURVEY_TARGET] = SURVEY_IDS;
        targets[TAB_STRIP_TARGET] = TAB_STRIP_IDS;
        for (int i = 0; i < RAIL_BUTTON_IDS.length; i++) {
            targets[RAIL_TARGET_START + i] = RAIL_BUTTON_IDS[i];
        }
        for (int i = 0; i < RAIL_COUNT_ROW_IDS.length; i++) {
            targets[COUNT_ROW_TARGET_START + i] = RAIL_COUNT_ROW_IDS[i];
            targets[COUNT_TEXT_TARGET_START + i] = RAIL_COUNT_TEXT_IDS[i];
        }
        return targets;
    }

    private static int candidateCount(String[][] targets) {
        int count = 0;
        for (String[] candidates : targets) count += candidates.length;
        return count;
    }

    /** Chooses the newest candidate that actually occurs in the current hierarchy. */
    private static void selectCurrentTargets(List<List<View>> found, int[] selected) {
        int candidateAt = 0;
        for (int target = 0; target < TRAVERSAL_TARGET_IDS.length; target++) {
            selected[target] = -1;
            for (int i = 0; i < TRAVERSAL_TARGET_IDS[target].length; i++, candidateAt++) {
                if (selected[target] < 0 && !found.get(candidateAt).isEmpty()) {
                    selected[target] = candidateAt;
                }
            }
        }
    }

    /** Applies each logical switch only to its selected version-specific candidate. */
    private static void applySelectedTargets(int[] ids, boolean[] hidden,
            List<List<View>> found, int[] selected, boolean scaling) {
        int candidateAt = 0;
        for (int target = 0; target < TRAVERSAL_TARGET_IDS.length; target++) {
            String[] names = TRAVERSAL_TARGET_IDS[target];
            int chosen = selected[target];
            boolean wanted = hidden[candidateAt];
            if (chosen >= 0) {
                String diagnostic = String.join("|", names);
                HookStatus.recoveredViewId("overlay", diagnostic);
                HookStatus.bound("overlay", names[chosen - candidateAt]);
                for (View view : found.get(chosen)) setHidden(view, wanted);
            // A survey is content TikTok inserts only on selected posts. Its absence from an
            // ordinary feed cell says nothing about whether this build still has the anchor.
            } else if (target != SURVEY_TARGET && (wanted || (scaling
                    && target >= RAIL_TARGET_START
                    && target < COUNT_ROW_TARGET_START))) {
                HookStatus.missingViewId("overlay", String.join("|", names));
            }
            candidateAt += names.length;
        }
    }

    private static int selectedId(int[] ids, int selected) {
        return selected < 0 ? 0 : ids[selected];
    }

    private static void hide(Activity activity, String packageName, String[] names) {
        View view = view(activity, packageName, names);
        if (view != null && view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }

    private static View view(Activity activity, String packageName, String[] names) {
        boolean retryMissing = SEARCH_MODULE_PACKAGE.equals(packageName);
        String family = overlayFamily(retryMissing);
        String diagnostic = String.join("|", names);
        for (String name : names) {
            int id = resolveIdentifier(activity, packageName, name, retryMissing);
            View candidate = id == 0 ? null : activity.findViewById(id);
            if (candidate != null) {
                HookStatus.recoveredViewId(family, diagnostic);
                HookStatus.bound(family, name);
                return candidate;
            }
        }
        if (!retryMissing) HookStatus.missingViewId(family, diagnostic);
        return null;
    }

    /** Every descendant of {@code root} carrying {@code id}, in tree order. */
    static List<View> viewsWithId(View root, int id) {
        return viewsWithIds(root, new int[]{id}).get(0);
    }

    /**
     * One walk of the tree under {@code root} collecting the views for several ids at
     * once. The result holds one list per id, in the order given; an id of zero, which is
     * what an unresolved name gives, matches nothing.
     */
    static List<List<View>> viewsWithIds(View root, int[] ids) {
        List<List<View>> found = new ArrayList<>(ids.length);
        boolean anyId = false;
        for (int id : ids) {
            found.add(new ArrayList<>());
            anyId |= id != 0;
        }
        if (anyId && root != null) {
            collect(root, ids, found);
        }
        return found;
    }

    private static void collect(View view, int[] ids, List<List<View>> found) {
        collect(view, ids, found, 0, true, null);
    }

    /**
     * The walk. A target flagged in {@code needsCell} is collected only under a view whose
     * id is {@code cellId}; with no cell id resolved ({@code inCell} starts true) every
     * match is taken, which is what the walk did before the cell root was known.
     */
    private static void collect(View view, int[] ids, List<List<View>> found,
                                int cellId, boolean inCell, boolean[] needsCell) {
        int viewId = view.getId();
        if (cellId != 0 && viewId == cellId) {
            inCell = true;
        }
        if (viewId != View.NO_ID) {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == viewId && (inCell || needsCell == null || !needsCell[i])) {
                    found.get(i).add(view);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                collect(group.getChildAt(i), ids, found, cellId, inCell, needsCell);
            }
        }
    }

    private static final class TraversalScratch {
        final int[] ids;
        final boolean[] hidden;
        final boolean[] needsCell;
        final boolean[] rail;
        final boolean[] wanted;
        final int[] selected;
        final List<List<View>> found;
        /** The icons one pass scaled, cleared with the rest once the pass is over. */
        final List<View> scaled = new ArrayList<>();

        TraversalScratch(int targetCount) {
            ids = new int[targetCount];
            hidden = new boolean[targetCount];
            needsCell = new boolean[targetCount];
            rail = new boolean[RAIL_BUTTON_IDS.length];
            wanted = new boolean[LOGICAL_TARGET_COUNT];
            selected = new int[LOGICAL_TARGET_COUNT];
            found = new ArrayList<>(targetCount);
            for (int i = 0; i < targetCount; i++) {
                found.add(new ArrayList<>());
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
            if (isStatusBarHidden(decor)) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (!rehideAllowed(now)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = decor.getWindowInsetsController();
                if (controller == null) {
                    return;
                }
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars());
            } else {
                decor.setSystemUiVisibility(decor.getSystemUiVisibility() | LEGACY_STATUS_BAR_FLAGS);
            }
            statusBarHiddenHere = true;
            statusBarHiddenAt = now;
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

    /**
     * False while a hide this class issued is younger than the peek window: the bar is
     * either a swipe peek the system will end by itself, or the request is still landing.
     */
    static boolean rehideAllowed(long now) {
        return !statusBarHiddenHere || now - statusBarHiddenAt >= STATUS_BAR_PEEK_MS;
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
            int visibility = view.getVisibility();
            if (visibility != View.GONE) {
                HIDDEN_HERE.put(view, visibility);
                view.setVisibility(View.GONE);
            }
            return;
        }

        Integer before = HIDDEN_HERE.remove(view);
        if (before != null && view.getVisibility() == View.GONE) {
            view.setVisibility(before);
        }
    }

    /** Lets a test stand in for a TikTok resource id, which only the real APK resolves. */
    static void resolveForTests(String name, int id) {
        RESOURCE_IDS.putForTests(APP_PACKAGE, name, id);
    }

    static void resolveSearchModuleForTests(String name, int id) {
        RESOURCE_IDS.putForTests(SEARCH_MODULE_PACKAGE, name, id);
    }

    /**
     * Resolves a resource id by name once and remembers it, including a miss. The search
     * module's ids only exist once that module has loaded, so a miss for those is retried
     * rather than cached.
     */
    /** The search module's ids live in their own package, so they are counted on their own. */
    private static String overlayFamily(boolean searchModule) {
        return searchModule ? "overlay (search)" : "overlay";
    }

    private static int identifier(Activity activity, String packageName, String name) {
        boolean retryMissing = SEARCH_MODULE_PACKAGE.equals(packageName);
        int id = resolveIdentifier(activity, packageName, name, retryMissing);
        if (id != 0) {
            // A family per package rather than a composed key: this runs on every layout pass,
            // and the two id spaces can hand out the same two-character obfuscated name.
            HookStatus.bound(overlayFamily(retryMissing), name);
            return id;
        }
        if (!retryMissing) {
            HookStatus.missingViewId(overlayFamily(false), name);
        }
        return id;
    }

    private static int resolveIdentifier(Activity activity, String packageName, String name,
            boolean retryMissing) {
        return RESOURCE_IDS.resolve(
                activity == null ? null : activity.getResources(), packageName, name, retryMissing);
    }
}
