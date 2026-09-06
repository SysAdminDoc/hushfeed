/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.interaction;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

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
     * The window the video has to fit in. The view's own parent is the cell's container and
     * knows its size once it has been laid out; before that the feed cell fills the window,
     * which the display is measured against in split view too.
     */
    private static int containerWidth(View view) {
        int width = parentWidth(view);
        if (width > 0) return width;
        Context context = Utils.getContext();
        return context == null ? 0 : context.getResources().getDisplayMetrics().widthPixels;
    }

    private static int containerHeight(View view) {
        int height = parentHeight(view);
        if (height > 0) return height;
        Context context = Utils.getContext();
        return context == null ? 0 : context.getResources().getDisplayMetrics().heightPixels;
    }

    private static int parentWidth(View view) {
        return view.getParent() instanceof View ? ((View) view.getParent()).getWidth() : 0;
    }

    private static int parentHeight(View view) {
        return view.getParent() instanceof View ? ((View) view.getParent()).getHeight() : 0;
    }
}
