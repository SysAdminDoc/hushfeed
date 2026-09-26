/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.foldable;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.seen.SeenVideoHistory;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Map;
import java.util.WeakHashMap;

public final class FoldableSplitView {
    /**
     * The side of the width threshold each activity's comment containers were built on, and the
     * threshold that decided it, recorded the first time TikTok asked while that activity was
     * current with the switch on. TikTok builds the containers once, when the feed attaches, and
     * its feed activity takes size changes itself, so they stay whatever the window does next.
     *
     * <p>Both the record and the later comparison read {@link Configuration#screenWidthDp}: the
     * window metrics {@link #shouldForce} falls back to include the system bars below API 35 and
     * the configuration width does not, so one measure for both is what keeps a threshold inside
     * that gap from rebuilding on every configuration change.
     */
    private static final Map<Activity, Built> BUILT_FOR = new WeakHashMap<>();

    private static final class Built {
        final boolean wide;
        final int threshold;

        Built(boolean wide, int threshold) {
            this.wide = wide;
            this.threshold = threshold;
        }
    }

    /** Stands in for {@link Activity#recreate()}, so a test can count. */
    interface Recreator {
        void recreate(Activity activity);
    }

    static Recreator recreator = Activity::recreate;

    private FoldableSplitView() { }

    public static boolean shouldForce(Activity activity, Configuration configuration) {
        if (!Settings.FOLDABLE_SPLIT_VIEW.get()) return false;
        if (activity == null) activity = Utils.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        try {
            if (Build.VERSION.SDK_INT >= 24 && (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode())) return false;
            int width = configuration == null ? 0 : configuration.screenWidthDp;
            if (width <= 0 && Build.VERSION.SDK_INT >= 30) {
                float density = activity.getResources().getDisplayMetrics().density;
                if (density > 0) width = Math.round(activity.getWindowManager().getCurrentWindowMetrics().getBounds().width() / density);
            }
            if (width <= 0) width = activity.getResources().getConfiguration().screenWidthDp;
            return width >= threshold();
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not determine the comment panel width", error);
            return false;
        }
    }

    private static int threshold() {
        return Math.max(320, Math.min(1600, Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.get()));
    }

    private static boolean besideOtherApps(Activity activity) {
        return Build.VERSION.SDK_INT >= 24 && (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode());
    }

    /** The configuration's width, or the activity's own when the change carries none. */
    private static int widthDp(Activity activity, Configuration configuration) {
        int width = configuration == null ? 0 : configuration.screenWidthDp;
        if (width <= 0) width = activity.getResources().getConfiguration().screenWidthDp;
        return width;
    }

    public static boolean shouldForceContainer() {
        Activity activity = Utils.getActivity();
        boolean force = shouldForce(activity, null);
        // Only an answer given with the switch on is a split decision. Off, the row says the
        // switch applies at the next start, and a record taken now would rebuild the feed on
        // the first configuration change after the switch is turned on, with no crossing at all.
        if (activity != null && Settings.FOLDABLE_SPLIT_VIEW.get()) {
            try {
                int threshold = threshold();
                boolean wide = !besideOtherApps(activity) && widthDp(activity, null) >= threshold;
                synchronized (BUILT_FOR) {
                    if (!BUILT_FOR.containsKey(activity)) BUILT_FOR.put(activity, new Built(wide, threshold));
                }
            } catch (RuntimeException error) {
                Logger.printException(() -> "Could not record the comment panel width", error);
            }
        }
        return force;
    }

    /**
     * First thing in the feed activity's configuration change (issue #26). An unfold reaches the
     * feed as a size change rather than as a new activity, so the comment containers TikTok built
     * for the folded width stayed until TikTok was started again. When the width crosses the
     * threshold, the activity is built again, once per crossing. A window beside other apps is
     * left to TikTok, and with the switch off nothing is rebuilt.
     */
    public static void onConfigurationChanged(Activity activity, Configuration configuration) {
        if (activity == null || !Settings.FOLDABLE_SPLIT_VIEW.get()) return;
        if (besideOtherApps(activity)) return;
        Built built;
        synchronized (BUILT_FOR) {
            built = BUILT_FOR.get(activity);
        }
        // Never asked with the switch on, so nothing was built for the other width. The record's
        // own threshold decides, so a threshold edited mid-session applies at the next start,
        // as its row says, and not to the next rotation.
        if (built == null || (widthDp(activity, configuration) >= built.threshold) == built.wide) return;
        synchronized (BUILT_FOR) {
            BUILT_FOR.remove(activity);
        }
        Logger.printInfo(() -> "Split view: the window crossed the width threshold, so the feed is built again");
        try {
            // TikTok puts the video that was playing back after the rebuild, unless Hide seen
            // videos has taken it out of the lists it reads again by then (issue #26).
            SeenVideoHistory.keepThroughRebuild();
            recreator.recreate(activity);
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not build the feed again for the new window width", error);
        }
    }

    static void forgetForTests() {
        synchronized (BUILT_FOR) {
            BUILT_FOR.clear();
        }
        recreator = Activity::recreate;
    }
}
