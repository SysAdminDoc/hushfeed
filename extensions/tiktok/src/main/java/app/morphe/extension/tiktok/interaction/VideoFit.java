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
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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

    /** What the two switches ask for: the whole video inside the window, the window covered, or nothing. */
    enum Mode { LEAVE_ALONE, FIT, FILL }

    /**
     * Fit wins when both switches are on. The page keeps them apart, and a restored backup that
     * carries both should show the whole video rather than crop it.
     */
    static Mode mode() {
        if (Settings.FIT_VIDEO_TO_SCREEN.get()) return Mode.FIT;
        if (Settings.FILL_VIDEO_TO_SCREEN.get()) return Mode.FILL;
        return Mode.LEAVE_ALONE;
    }

    /** What the last decision on this thread settled: which result, the height for it and where it sits. */
    private static final class Fitted {
        final Object result;
        final int height;
        /** The offsets that centre it, in the order the host asks: across, then down. */
        final float[] offsets;
        int offsetsRemaining = 2;

        Fitted(Object result, int height, float[] offsets) {
            this.result = result;
            this.height = height;
            this.offsets = offsets;
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
        try {
            // The switches before the reads: this runs twice for every video the feed binds, off
            // is the default, and the reflection below is only worth paying for with one on.
            Mode mode = mode();
            if (mode == Mode.LEAVE_ALONE) return untouched(view, result);
            int videoWidth = size(result, "getWidth");
            int videoHeight = size(result, "getHeight");
            if (videoWidth <= 0 || videoHeight <= 0) return untouched(view, result);
            int containerWidth = containerWidth(view);
            int containerHeight = containerHeight(view);
            if (containerWidth <= 0 || containerHeight <= 0) return untouched(view, result);
            if (!wants(mode, videoWidth, videoHeight, containerWidth, containerHeight)) return untouched(view, result);
            int width = widthFor(mode, videoWidth, videoHeight, containerWidth, containerHeight);
            int height = heightFor(mode, videoWidth, videoHeight, containerWidth, containerHeight);
            // Centred by the layout when the container can do it, and by the offsets otherwise.
            float[] offsets = centre(view)
                    ? new float[]{0f, 0f} : offsets(width, height, containerWidth, containerHeight);
            Object copy = copyOf(result, width, height, offsets);
            if (copy == null) return untouched(view, result);
            return copy;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not fit the video to the window", exception);
            return untouched(view, result);
        }
    }

    /**
     * The result again at the given size, with no offsets: the offsets are what centre a video
     * that is larger than the window, and one that fits has nothing hanging over an edge. The
     * operator travels across unchanged. Null when the class has no {@code copy} of the shape a
     * data class generates, which is what says the result is not one this knows how to remake.
     */
    private static Object copyOf(Object result, int width, int height, float[] offsets) {
        // What the class declares, made accessible, rather than what it makes public: the copy is
        // the one a data class generates whatever access R8 leaves it or its class with.
        for (Method candidate : result.getClass().getDeclaredMethods()) {
            if (!candidate.getName().equals("copy")) continue;
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != 5) continue;
            if (parameters[0] != int.class || parameters[1] != int.class) continue;
            if (parameters[2] != Float.class || parameters[3] != Float.class) continue;
            Object operator = Reflect.invoke(result, "getResultOperator");
            try {
                candidate.setAccessible(true);
                return candidate.invoke(result, width, height,
                        Float.valueOf(offsets[0]), Float.valueOf(offsets[1]), operator);
            } catch (Exception exception) {
                Logger.printException(() -> "Could not copy the adaption result", exception);
                return null;
            }
        }
        return null;
    }

    /**
     * A video large enough to fill was pinned wherever the container puts a child that does
     * not fit. A smaller one has to say where it goes, or the bars all end up on one side, and
     * a larger one made here has to say it too, or the crop shows a corner. The host sets the
     * size, not the gravity, so this stays here.
     *
     * @return whether the container centres its children, which a frame does once told to; a
     *         container of another kind is centred through the offsets instead.
     */
    private static boolean centre(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
        if (frame.gravity == Gravity.CENTER) return true;
        GRAVITY_BEFORE.put(view, frame.gravity);
        frame.gravity = Gravity.CENTER;
        view.setLayoutParams(frame);
        return true;
    }

    /**
     * The gravity each view had before {@link #centre} replaced it. TikTok reuses a cell's video
     * view, and its layout parameters, for the next video, and lays its own result out with
     * offsets that assume its own gravity: a result left alone on a view centred here for an
     * earlier video was centred twice, and a video TikTok had cropped showed an edge instead of
     * the middle. Weak, because a view that is gone has nothing to give back.
     */
    private static final Map<View, Integer> GRAVITY_BEFORE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Gives a view centred here its own gravity back, for a result left as TikTok made it. */
    private static void uncentre(View view) {
        if (GRAVITY_BEFORE.isEmpty()) return;
        Integer before = GRAVITY_BEFORE.remove(view);
        if (before == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams frame = (FrameLayout.LayoutParams) params;
        frame.gravity = before;
        view.setLayoutParams(frame);
    }

    /** TikTok's own result for the feed cell, on a view with its own gravity. */
    private static Object untouched(View view, Object result) {
        uncentre(view);
        return result;
    }

    /** {@link #LEAVE} for the story cell, on a view with its own gravity. */
    private static int leave(View view) {
        uncentre(view);
        return LEAVE;
    }

    /** The offsets that centre a video of this size in the container: half the difference, each way. */
    static float[] offsets(int width, int height, int containerWidth, int containerHeight) {
        return new float[]{(containerWidth - width) / 2f, (containerHeight - height) / 2f};
    }

    /**
     * The width the video should be, for the one path that sizes a view from inside the result:
     * {@code saveResultInner}, which the story cell uses. That method reads its own fields, so
     * there is no result to swap for a copy and the size is changed in place instead. The fields
     * are final, so nothing out here can write them; the patch does, from inside the class that
     * declares them, and asks here for what to write, or {@link #LEAVE} to write nothing.
     *
     * <p>The matching height is worked out here too and kept for {@link #fittedHeightFor}, because
     * asking twice would mean the second answer was worked out from a width already changed.
     */
    public static int fitWidthFor(Object result, View view) {
        LAST.remove();
        if (view == null || result == null) return LEAVE;
        try {
            Mode mode = mode();
            if (mode == Mode.LEAVE_ALONE) return leave(view);
            int videoWidth = size(result, "getWidth");
            int videoHeight = size(result, "getHeight");
            if (videoWidth <= 0 || videoHeight <= 0) return leave(view);
            int containerWidth = containerWidth(view);
            int containerHeight = containerHeight(view);
            if (containerWidth <= 0 || containerHeight <= 0) return leave(view);
            // Already inside the window, or already covering it: nothing to bring back or add.
            if (!wants(mode, videoWidth, videoHeight, containerWidth, containerHeight)) return leave(view);

            int width = widthFor(mode, videoWidth, videoHeight, containerWidth, containerHeight);
            int height = heightFor(mode, videoWidth, videoHeight, containerWidth, containerHeight);
            float[] offsets = centre(view)
                    ? new float[]{0f, 0f} : offsets(width, height, containerWidth, containerHeight);
            LAST.set(new Fitted(result, height, offsets));
            return width;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not fit the video to the window", exception);
            LAST.remove();
            return leave(view);
        }
    }

    /**
     * What {@link #fitWidthFor} answers when the result is to be left exactly as TikTok wrote
     * it: the switch off, a video that already fits, or anything it could not read. The patch
     * branches past every write on it, so none of the host's fields is touched on that path.
     * It used to answer the width it had read, which was 0 for a result it could not read, and
     * the patch wrote that back: a build that renamed the getters would have laid every story
     * out at nothing by nothing.
     */
    public static final int LEAVE = -1;

    /**
     * The height that goes with the width already handed back, or the one TikTok chose.
     *
     * <p>Left in place rather than taken, because the two offsets are asked about after this and
     * the entry is what says the video was fitted. The second offset read clears it.
     */
    public static int fittedHeightFor(Object result) {
        Fitted fitted = fittedFor(result);
        return fitted != null ? fitted.height : size(result, "getHeight");
    }

    /**
     * The offset that centres the video this code sized, and whatever the host had otherwise.
     *
     * <p>TikTok's offsets are what centre a video it cropped. One that fits has nothing hanging
     * over an edge, so those would push it off the other side, and one cropped here needs its
     * own. The host asks twice, across and then down, and the second answer ends the decision.
     */
    public static Float fittedTranslation(Object result, Float translation) {
        Fitted fitted = fittedFor(result);
        if (fitted == null) return translation;
        float offset = fitted.offsets[2 - fitted.offsetsRemaining];
        if (--fitted.offsetsRemaining == 0) LAST.remove();
        return Float.valueOf(offset);
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

    /** Whether the video at this size needs changing: it overflows for fit, or leaves a gap for fill. */
    static boolean wants(Mode mode, int width, int height, int containerWidth, int containerHeight) {
        if (mode == Mode.FIT) return width > containerWidth || height > containerHeight;
        if (mode == Mode.FILL) return width < containerWidth || height < containerHeight;
        return false;
    }

    static int widthFor(Mode mode, int width, int height, int containerWidth, int containerHeight) {
        return mode == Mode.FILL
                ? fillWidth(width, height, containerWidth, containerHeight)
                : fitWidth(width, height, containerWidth, containerHeight);
    }

    static int heightFor(Mode mode, int width, int height, int containerWidth, int containerHeight) {
        return mode == Mode.FILL
                ? fillHeight(width, height, containerWidth, containerHeight)
                : fitHeight(width, height, containerWidth, containerHeight);
    }

    /** The width the video needs to cover the container without changing shape: the larger scale. */
    static int fillWidth(int width, int height, int containerWidth, int containerHeight) {
        if (wider(width, height, containerWidth, containerHeight)) {
            return atLeastOne(Math.round(containerHeight * (double) width / height));
        }
        return containerWidth;
    }

    /** The matching height. */
    static int fillHeight(int width, int height, int containerWidth, int containerHeight) {
        if (wider(width, height, containerWidth, containerHeight)) return containerHeight;
        return atLeastOne(Math.round(containerWidth * (double) height / width));
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
