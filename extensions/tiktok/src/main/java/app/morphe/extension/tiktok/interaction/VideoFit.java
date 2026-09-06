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

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Puts the whole of a video on screen instead of cropping it to the window.
 *
 * <p>TikTok works out how big the video view should be and keeps the answer in a real named
 * {@code VideoAdaptionResult}, which then writes itself into the view's layout parameters.
 * On a 9:16 phone the answer fills the window exactly. On anything squarer, a Fold opened
 * up, a Titan, a Clicks, split view, the answer is larger than the window in one direction
 * and the sides or the ends are cut off. Same shape, same arithmetic, but taking the
 * smaller scale rather than the larger one, so the video sits inside the window whole.
 */
public final class VideoFit {
    private VideoFit() {}

    /**
     * Lays the video out to fit and reports that TikTok's own sizing should be skipped.
     * Anything unexpected returns false and leaves the app to do what it always did.
     */
    public static boolean fitInstead(Object result, View view) {
        if (view == null || result == null || !Settings.FIT_VIDEO_TO_SCREEN.get()) return false;
        try {
            Object width = Reflect.invoke(result, "getWidth");
            Object height = Reflect.invoke(result, "getHeight");
            if (!(width instanceof Integer) || !(height instanceof Integer)) return false;
            int videoWidth = (Integer) width, videoHeight = (Integer) height;
            if (videoWidth <= 0 || videoHeight <= 0) return false;

            int containerWidth = containerWidth(view);
            int containerHeight = containerHeight(view);
            if (containerWidth <= 0 || containerHeight <= 0) return false;
            // Already inside the window, so there is nothing hanging over an edge to bring back.
            if (videoWidth <= containerWidth && videoHeight <= containerHeight) return false;

            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) return false;
            params.width = fitWidth(videoWidth, videoHeight, containerWidth, containerHeight);
            params.height = fitHeight(videoWidth, videoHeight, containerWidth, containerHeight);
            // A video large enough to fill was pinned wherever the container puts a child that
            // does not fit, and the offsets moved it from there. A smaller one has to say
            // where it goes, or the bars all end up on one side.
            if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
            }
            view.setLayoutParams(params);
            // The offsets that follow a crop shift the video off centre once it fits.
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            return true;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not fit the video to the window", exception);
            return false;
        }
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
