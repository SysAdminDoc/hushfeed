/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.privacy;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

/**
 * A dot in the top corner while TikTok holds the camera or records sound.
 *
 * <p>A green square for the camera, an orange diamond for the microphone, both when both. The counts come from the
 * patched call sites: a camera counts from the moment it opens until it is released or closed,
 * a recorder from start until stop or release. The dot sits on the decor view of whichever
 * activity is on top, follows the top activity while an access is live, and takes no touches.
 * With the switch off nothing is drawn; the counts are still kept so turning it on mid-access
 * shows the truth.
 */
@SuppressWarnings({"unused", "deprecation"})
public final class CameraMicIndicator {
    private static final AtomicInteger CAMERAS = new AtomicInteger();
    private static final AtomicInteger MICROPHONES = new AtomicInteger();
    private static WeakReference<Activity> top = new WeakReference<>(null);
    private static WeakReference<Activity> shownOn = new WeakReference<>(null);
    private static DotView dot;
    private static boolean following;

    /** The camera opened and came back non-null; a failed open shows nothing. */
    public static void onCameraOpened(Camera camera) {
        if (camera != null) onCameraStart();
    }

    public static void onCameraStart() {
        change(CAMERAS, 1, "camera opened");
    }

    public static void onCameraStop() {
        change(CAMERAS, -1, "camera released");
    }

    public static void onMicStart() {
        change(MICROPHONES, 1, "microphone recording");
    }

    public static void onMicStop() {
        change(MICROPHONES, -1, "microphone released");
    }

    private static void change(AtomicInteger counter, int delta, String what) {
        // Clamped at zero: a recorder that is stopped and then released reports its end twice.
        int now;
        do {
            int current = counter.get();
            now = Math.max(0, current + delta);
            if (counter.compareAndSet(current, now)) break;
        } while (true);
        Logger.printInfo(() -> "Camera/mic indicator: " + what
                + " (camera " + CAMERAS.get() + ", microphone " + MICROPHONES.get() + ")");
        Utils.runOnMainThreadNowOrLater(CameraMicIndicator::refresh);
    }

    private static boolean switchOn() {
        return Utils.getContext() != null && Settings.CAMERA_MIC_INDICATOR.get();
    }

    private static void refresh() {
        try {
            boolean camera = CAMERAS.get() > 0;
            boolean microphone = MICROPHONES.get() > 0;
            boolean wanted = (camera || microphone) && switchOn();
            Activity activity = wanted ? currentActivity() : null;
            Activity previous = shownOn.get();
            if (previous != null && previous != activity) remove(previous);
            if (activity == null) {
                shownOn = new WeakReference<>(null);
                return;
            }
            follow(activity);
            DotView view = attach(activity);
            view.show(camera, microphone);
            shownOn = new WeakReference<>(activity);
        } catch (Throwable error) {
            Logger.printException(() -> "Camera/mic indicator could not draw", error);
        }
    }

    private static Activity currentActivity() {
        Activity current = top.get();
        if (current != null && !current.isFinishing()) return current;
        return Utils.getActivity();
    }

    /** Follows the top activity for as long as the process lives, registered once. */
    private static void follow(Activity activity) {
        if (following) return;
        Application application = activity.getApplication();
        if (application == null) return;
        following = true;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity resumed) {
                top = new WeakReference<>(resumed);
                refresh();
            }

            @Override public void onActivityDestroyed(Activity destroyed) {
                if (shownOn.get() == destroyed) {
                    dot = null;
                    shownOn = new WeakReference<>(null);
                }
                if (top.get() == destroyed) top = new WeakReference<>(null);
            }

            @Override public void onActivityCreated(Activity created, Bundle state) {}
            @Override public void onActivityStarted(Activity started) {}
            @Override public void onActivityPaused(Activity paused) {}
            @Override public void onActivityStopped(Activity stopped) {}
            @Override public void onActivitySaveInstanceState(Activity saved, Bundle state) {}
        });
    }

    private static DotView attach(Activity activity) {
        if (dot != null && dot.getParent() != null && shownOn.get() == activity) return dot;
        remove(shownOn.get());
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        DotView view = new DotView(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        int edge = dp(activity, 10);
        params.topMargin = statusBarHeight(decor) + edge;
        params.setMarginEnd(edge);
        decor.addView(view, params);
        dot = view;
        return view;
    }

    private static void remove(Activity activity) {
        if (dot == null) return;
        if (dot.getParent() instanceof ViewGroup) ((ViewGroup) dot.getParent()).removeView(dot);
        dot = null;
    }

    private static int statusBarHeight(View decor) {
        WindowInsets insets = decor.getRootWindowInsets();
        return insets == null ? 0 : insets.getSystemWindowInsetTop();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * One or two filled marks with a hairline dark ring, so they read on any video. The camera
     * is a rounded square and the microphone a diamond: they were two dots told apart by colour
     * alone, which a reader who can't separate green from orange couldn't do.
     */
    static final class DotView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path diamond = new android.graphics.Path();
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean camera;
        private boolean microphone;

        DotView(Context context) {
            super(context);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Math.max(1f, dp(context, 1)));
            ring.setColor(SettingsUi.INDICATOR_RING);
            setClickable(false);
            setFocusable(false);
            setElevation(dp(context, 24));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        void show(boolean cameraOn, boolean microphoneOn) {
            boolean changed = camera != cameraOn || microphone != microphoneOn;
            camera = cameraOn;
            microphone = microphoneOn;
            Context context = getContext();
            String description = camera && microphone
                    ? L10n.t(context, "Camera and microphone in use")
                    : camera ? L10n.t(context, "Camera in use") : L10n.t(context, "Microphone in use");
            setContentDescription(description);
            if (changed) {
                requestLayout();
                invalidate();
                announceForAccessibility(description);
            }
        }

        boolean camera() { return camera; }

        boolean microphone() { return microphone; }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int size = dp(getContext(), 12);
            int gap = dp(getContext(), 6);
            int count = (camera ? 1 : 0) + (microphone ? 1 : 0);
            int width = count == 0 ? 0 : count * size + (count - 1) * gap;
            setMeasuredDimension(width, count == 0 ? 0 : size);
        }

        @Override protected void onDraw(Canvas canvas) {
            int size = dp(getContext(), 12);
            int gap = dp(getContext(), 6);
            float half = size / 2f;
            float x = half;
            if (camera) {
                fill.setColor(SettingsUi.INDICATOR_CAMERA);
                // Square cornered: the scale's 0, and the plainest contrast with the diamond.
                android.graphics.RectF box = new android.graphics.RectF(
                        x - half + 1, 1, x + half - 1, size - 1);
                canvas.drawRect(box, fill);
                canvas.drawRect(box, ring);
                x += size + gap;
            }
            if (microphone) {
                fill.setColor(SettingsUi.INDICATOR_MICROPHONE);
                diamond.reset();
                diamond.moveTo(x, 0.5f);
                diamond.lineTo(x + half - 0.5f, half);
                diamond.lineTo(x, size - 0.5f);
                diamond.lineTo(x - half + 0.5f, half);
                diamond.close();
                canvas.drawPath(diamond, fill);
                canvas.drawPath(diamond, ring);
            }
        }
    }

    /** What is live right now, for tests and the diagnostic report. */
    static int cameras() { return CAMERAS.get(); }

    static int microphones() { return MICROPHONES.get(); }

    static DotView shownDot() { return dot; }

    static void resetForTests() {
        CAMERAS.set(0);
        MICROPHONES.set(0);
        remove(shownOn.get());
        shownOn = new WeakReference<>(null);
        top = new WeakReference<>(null);
    }

    private CameraMicIndicator() {}
}
