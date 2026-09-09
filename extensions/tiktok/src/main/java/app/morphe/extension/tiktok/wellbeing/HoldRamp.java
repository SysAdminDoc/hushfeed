/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.wellbeing;

import android.app.Activity;
import android.content.ContentResolver;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;

/**
 * A cover that comes in over the last three quarters of a minute of the budget, so the hold is
 * something the feed arrives at rather than something that lands on it.
 *
 * <p>A field study of short-form feeds (IMWUT 2026, N=104, seven days, within-subject) measured
 * people leaving 56 seconds after a gradual visual occlusion against 7 seconds after a pop-up,
 * and the gradual version did not wear off across the week the way the pop-up did. It also
 * found the abrupt version worked better for the most impulsive readers, which is why this is a
 * switch beside the hold rather than a replacement for it.
 *
 * <p>Off by default. It only has anything to follow when a minutes budget is set: a budget
 * counted in videos has no "how long is left" to draw.
 */
public final class HoldRamp {
    /** How much of the budget the cover takes to arrive. */
    static final long RAMP_MS = 45_000L;

    /**
     * Where the ramp finishes. The hold's own panel is {@code argb(238, 0, 0, 0)}, so the two
     * meet without a step: the last frame of the ramp and the first frame of the hold are the
     * same shade.
     */
    static final int FULL_ALPHA = 238;

    private static WeakReference<View> coverReference = new WeakReference<>(null);

    private HoldRamp() {
    }

    /**
     * How dark the cover is with this much of the budget left, from 0 to {@link #FULL_ALPHA}.
     *
     * <p>Squared rather than straight so nine tenths of the cover arrives in the last thirty
     * seconds, which is the shape the study measured. A straight line spends the first fifteen
     * seconds visibly greying a feed nobody has been warned about yet.
     *
     * @param remainingMs how much budget is left, or a negative number when nothing is being
     *                    counted in time and there is therefore nothing to ramp
     */
    static int alphaFor(long remainingMs) {
        if (remainingMs < 0) return 0;
        if (remainingMs >= RAMP_MS) return 0;
        float travelled = 1f - (float) remainingMs / (float) RAMP_MS;
        return Math.round(FULL_ALPHA * travelled * travelled);
    }

    /**
     * Whether the reader has asked the system for no animation.
     *
     * <p>A zero animator duration scale snaps every duration-based animation to its end frame,
     * so a ramp built on an Animator would appear as an instant black screen. This one is drawn
     * from the budget rather than from an Animator, which means the scale cannot break it, but
     * a reader who has turned animation off has asked not to be shown a slow fade at all. They
     * get the plain hold, unchanged.
     */
    static boolean animationIsOff(Activity activity) {
        if (activity == null) return false;
        try {
            ContentResolver resolver = activity.getContentResolver();
            if (resolver == null) return false;
            return android.provider.Settings.Global.getFloat(resolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable unreadable) {
            // A setting that cannot be read is not a request for anything.
            return false;
        }
    }

    /**
     * Puts the cover where it belongs for the moment.
     *
     * <p>Called from the player's progress callback, which fires several times a second, so the
     * switched-off case has to answer before anything else happens.
     */
    public static void sync() {
        if (!Settings.SESSION_BUDGET_RAMP.get()) {
            if (coverReference.get() != null) detach();
            return;
        }
        try {
            // The hold covers the feed itself and swallows the touches; a ramp under it would
            // be two covers doing one job.
            if (SessionBudget.isLocked()) {
                detach();
                return;
            }
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                detach();
                return;
            }
            if (!FeedVisibility.isOnFeed(activity) || animationIsOff(activity)) {
                detach();
                return;
            }
            int alpha = alphaFor(SessionBudget.budgetRemainingMs());
            if (alpha <= 0) {
                detach();
                return;
            }
            View cover = attach(activity);
            if (cover != null) cover.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        } catch (Throwable error) {
            Logger.printException(() -> "Could not update the budget ramp", error);
        }
    }

    private static View attach(Activity activity) {
        View existing = coverReference.get();
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return null;
        if (existing != null && existing.getParent() == root) return existing;

        View cover = new View(activity);
        // Nothing about this is interactive. A plain View with no click listener refuses the
        // touch, so the feed underneath scrolls, likes and comments exactly as before.
        cover.setClickable(false);
        cover.setFocusable(false);
        // And a screen reader is told nothing until the hold itself, which has its own
        // announcement. A cover that says something once a second would be unusable.
        cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(cover, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        coverReference = new WeakReference<>(cover);
        return cover;
    }

    private static void detach() {
        View cover = coverReference.get();
        coverReference = new WeakReference<>(null);
        if (cover == null) return;
        ViewGroup parent = cover.getParent() instanceof ViewGroup
                ? (ViewGroup) cover.getParent()
                : null;
        if (parent != null) parent.removeView(cover);
    }

    /** Drops the cover and forgets it, between tests. */
    static void resetForTests() {
        detach();
    }

    /** The cover on screen, or null when there is none. Test seam. */
    static View coverForTests() {
        return coverReference.get();
    }
}
