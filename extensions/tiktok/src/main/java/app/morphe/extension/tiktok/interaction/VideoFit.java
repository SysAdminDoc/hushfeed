/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.interaction;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Puts the whole of a video on screen instead of cropping it to the window.
 *
 * <p>TikTok works out how big the video view should be and keeps the answer in a real named
 * {@code VideoAdaptionResult}, which the feed cell then writes into the view's layout
 * parameters. On a 9:16 phone the answer fills the window exactly. On anything squarer, a Fold
 * opened up, a Titan, a Clicks, split view, the answer is larger than the window in one
 * direction and the sides or the ends are cut off. Same shape, same arithmetic, but taking the
 * smaller scale rather than the larger one, so the video sits inside the window whole.
 */
public final class VideoFit {
    private VideoFit() {}

    /** What the last decision on this thread settled: which result, and the height for it. */
    private static final class Fitted {
        final Object result;
        final int height;

        Fitted(Object result, int height) {
            this.result = result;
            this.height = height;
        }
    }

    /**
     * The height that goes with the width last handed back, and the result it was for.
     *
     * <p>One slot per thread rather than a map keyed by the result. The result is a data class
     * whose {@code hashCode} is built from its width and height, and the width is written before
     * the height is asked for, so a map would look under the old hash and find nothing. The
     * result is told apart by identity, and the three reads happen back to back on the thread
     * that made the decision.
     */
    private static final ThreadLocal<Fitted> LAST = new ThreadLocal<>();

    /**
     * The result the feed cell should apply: a copy sized to sit inside the window when the
     * switch is on and the video overflows it, and otherwise the result TikTok chose.
     *
     * <p>The feed cell does not size its video through {@code saveResultInner}; it hands the
     * result and the view to a pair of static helpers, one that writes the size and offsets into
     * the view and one that says whether the view already matches. Both get the result through
     * this, so the size they apply and the size they compare against are the same one. A copy is
     * handed back rather than the result changed in place, because the result is not this code's
     * to write and the copy is what the class offers for exactly that.
     *
     * <p>Anything that cannot be read or does not add up hands TikTok's own result back.
     */
    public static Object fitted(View view, Object result) {
        if (view == null || result == null) return result;
        int videoWidth = size(result, "getWidth");
        int videoHeight = size(result, "getHeight");
        if (videoWidth <= 0 || videoHeight <= 0) return result;
        if (!Settings.FIT_VIDEO_TO_SCREEN.get()) return result;
        try {
            int containerWidth = containerWidth(view);
            int containerHeight = containerHeight(view);
            if (containerWidth <= 0 || containerHeight <= 0) return result;
            if (videoWidth <= containerWidth && videoHeight <= containerHeight) return result;
            Object copy = copyOf(
                    result,
                    fitWidth(videoWidth, videoHeight, containerWidth, containerHeight),
                    fitHeight(videoWidth, videoHeight, containerWidth, containerHeight));
            if (copy == null) return result;
            centre(view);
            return copy;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not fit the video to the window", exception);
            return result;
        }
    }

    /**
     * The result again at the given size, with no offsets: the offsets are what centre a video
     * that is larger than the window, and one that fits has nothing hanging over an edge. The
     * operator travels across unchanged. Null when the class has no {@code copy} of the shape a
     * data class generates, which is what says the result is not one this knows how to remake.
     */
    private static Object copyOf(Object result, int width, int height) {
        for (Method candidate : result.getClass().getMethods()) {
            if (!candidate.getName().equals("copy")) continue;
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != 5) continue;
            if (parameters[0] != int.class || parameters[1] != int.class) continue;
            if (parameters[2] != Float.class || parameters[3] != Float.class) continue;
            Object operator = Reflect.invoke(result, "getResultOperator");
            try {
                return candidate.invoke(result, width, height, Float.valueOf(0f), Float.valueOf(0f), operator);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not copy the adaption result", exception);
                return null;
            }
        }
        return null;
    }

    /**
     * A video large enough to fill was pinned wherever the container puts a child that does
     * not fit. A smaller one has to say where it goes, or the bars all end up on one side. The
     * host sets the size, not the gravity, so this stays here.
     */
    private static void centre(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
        }
    }

    /**
     * The width the video should be, for the one path that sizes a view from inside the result:
     * {@code saveResultInner}, which the story cell uses. That method reads its own fields, so
     * there is no result to swap for a copy and the size is changed in place instead. The fields
     * are final, so nothing out here can write them; the patch does, from inside the class that
     * declares them, and asks here for what to write.
     *
     * <p>The matching height is worked out here too and kept for {@link #fittedHeightFor}, because
     * asking twice would mean the second answer was worked out from a width already changed.
     */
    public static int fitWidthFor(Object result, View view) {
        int videoWidth = size(result, "getWidth");
        int videoHeight = size(result, "getHeight");
        LAST.remove();
        if (view == null || videoWidth <= 0 || videoHeight <= 0) return videoWidth;
        if (!Settings.FIT_VIDEO_TO_SCREEN.get()) return videoWidth;
        try {
            int containerWidth = containerWidth(view);
            int containerHeight = containerHeight(view);
            if (containerWidth <= 0 || containerHeight <= 0) return videoWidth;
            // Already inside the window, so there is nothing hanging over an edge to bring back.
            if (videoWidth <= containerWidth && videoHeight <= containerHeight) return videoWidth;

            LAST.set(new Fitted(
                    result, fitHeight(videoWidth, videoHeight, containerWidth, containerHeight)));
            centre(view);
            return fitWidth(videoWidth, videoHeight, containerWidth, containerHeight);
        } catch (Exception exception) {
            Logger.printException(() -> "Could not fit the video to the window", exception);
            LAST.remove();
            return videoWidth;
        }
    }

    /**
     * The height that goes with the width already handed back, or the one TikTok chose.
     *
     * <p>Left in place rather than taken, because the offsets are asked about after this and the
     * entry is what says the video was fitted. The next pass clears it before deciding again.
     */
    public static int fittedHeightFor(Object result) {
        Fitted fitted = fittedFor(result);
        return fitted != null ? fitted.height : size(result, "getHeight");
    }

    /**
     * Nothing, when the video was fitted, and whatever the host had otherwise.
     *
     * <p>The offsets are what centre a video that is larger than the window. One that fits has
     * nothing hanging over an edge, so the same offsets would push it off the other side.
     */
    public static Float fittedTranslation(Object result, Float translation) {
        return fittedFor(result) != null ? Float.valueOf(0f) : translation;
    }

    /** The decision made for exactly this result on this thread, if the last one was for it. */
    private static Fitted fittedFor(Object result) {
        Fitted fitted = LAST.get();
        return fitted != null && result != null && fitted.result == result ? fitted : null;
    }

    private static int size(Object result, String getter) {
        if (result == null) return 0;
        Object value = Reflect.invoke(result, getter);
        return value instanceof Integer ? (Integer) value : 0;
    }

    /** The width the video needs to sit inside the container without changing shape. */
    static int fitWidth(int width, int height, int containerWidth, int containerHeight) {
        if (wider(width, height, containerWidth, containerHeight)) return containerWidth;
        return atLeastOne(Math.round(containerHeight * (double) width / height));
    }

    /** The matching height. */
    static int fitHeight(int width, int height, int containerWidth, int containerHeight) {
        if (wider(width, height, containerWidth, containerHeight)) {
            return atLeastOne(Math.round(containerWidth * (double) height / width));
        }
        return containerHeight;
    }

    /**
     * Whether the video is the wider shape of the two, which is what decides the side that
     * touches. Cross multiplied so the comparison stays exact.
     */
    private static boolean wider(int width, int height, int containerWidth, int containerHeight) {
        return (long) width * containerHeight > (long) height * containerWidth;
    }

    private static int atLeastOne(long value) {
        return value < 1 ? 1 : (int) value;
    }

    /**
     * How many levels up to look for something that has been measured. The video sits a
     * couple of frames inside the cell, and past that the answer stops being the cell.
     */
    private static final int LEVELS = 4;

    /**
     * The window the video has to fit in. The nearest laid out ancestor is the cell, which
     * fills the window; on a first bind nothing above it has a size yet, and the fallback is
     * the view's own resources rather than the application's, because in split view only the
     * activity's configuration reports the half of the screen TikTok actually has.
     */
    private static int containerWidth(View view) {
        int width = measuredAncestor(view, true);
        if (width > 0) return width;
        return metrics(view, true);
    }

    private static int containerHeight(View view) {
        int height = measuredAncestor(view, false);
        if (height > 0) return height;
        return metrics(view, false);
    }

    private static int measuredAncestor(View view, boolean horizontal) {
        ViewParent parent = view.getParent();
        for (int level = 0; level < LEVELS && parent instanceof View; level++) {
            View candidate = (View) parent;
            int size = horizontal ? candidate.getWidth() : candidate.getHeight();
            if (size > 0) return size;
            parent = candidate.getParent();
        }
        return 0;
    }

    private static int metrics(View view, boolean horizontal) {
        Context context = view.getContext();
        if (context == null) context = Utils.getContext();
        if (context == null) return 0;
        DisplayMetrics display = context.getResources().getDisplayMetrics();
        return horizontal ? display.widthPixels : display.heightPixels;
    }
}
