/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.blockauthor.Reflect;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns only pauses made for the daily-budget panel. Native player objects remain weakly held. */
public final class SessionPlaybackHold {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean syncQueued = new AtomicBoolean();
    private static volatile Target current;
    // Accessed only on the main thread, where the panel changes and native playback is controlled.
    private static Target held;
    // Published by main-thread release; progress can prove an independent resume before focus returns.
    private static volatile Target waitingForFocus;
    // A queued LIZ can still report PLAYING until its native dispatcher applies the pause.
    private static volatile boolean heldPauseObserved;
    private static WeakReference<Object> heldManager = new WeakReference<>(null);
    // The pause switches' own pause (comments open, or the feed waiting for a tap after a
    // return), kept apart from the panel's so neither hands back a video the other paused.
    private static volatile boolean switchWanted;
    private static final AtomicBoolean switchQueued = new AtomicBoolean();
    // Main thread only, like held.
    private static Target switchHeld;
    private static WeakReference<Object> switchManager = new WeakReference<>(null);
    // Native requests may run on an executor while focus callbacks arrive on another thread.
    // Compare listener identity, and never retain an activity's listener through static state.
    private static final List<WeakReference<Object>> nativeFocusListeners = new ArrayList<>();
    private static WeakReference<Object> nativeFocusOwner = new WeakReference<>(null);

    private SessionPlaybackHold() { }

    /** Receives the result of the native activity audio-focus request. */
    public static void onNativeFocusRequestResult(Object listener, int result) {
        if (listener == null) return;
        synchronized (nativeFocusListeners) {
            forgetNativeFocus(listener);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                nativeFocusListeners.add(new WeakReference<>(listener));
                nativeFocusOwner = new WeakReference<>(listener);
            }
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) resumeAfterNativeFocus();
    }

    /** Receives a focus change for the same native listener that requested it. */
    public static void onNativeFocusChange(Object listener, int change) {
        if (listener == null) return;
        boolean gained = false;
        synchronized (nativeFocusListeners) {
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                for (WeakReference<Object> request : nativeFocusListeners) {
                    if (request.get() == listener) {
                        nativeFocusOwner = new WeakReference<>(listener);
                        gained = true;
                        break;
                    }
                }
            } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
                forgetNativeFocus(listener);
            } else if ((change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
                    && nativeFocusOwner.get() == listener) {
                nativeFocusOwner = new WeakReference<>(null);
            }
        }
        if (gained) resumeAfterNativeFocus();
    }

    /** A grant can arrive after the panel and its timer have already gone away. */
    private static void resumeAfterNativeFocus() {
        MAIN.post(() -> {
            if (waitingForFocus == null || SessionBudget.isLocked() || !hasNativeFocus()) return;
            release(false);
        });
    }

    /** Receives the native listener immediately before its focus request is abandoned. */
    public static void onNativeFocusAbandon(Object listener) {
        if (listener == null) return;
        synchronized (nativeFocusListeners) {
            forgetNativeFocus(listener);
        }
    }

    /** Caller holds nativeFocusListeners. A transient loss keeps admission for a later GAIN. */
    private static void forgetNativeFocus(Object listener) {
        for (int index = nativeFocusListeners.size() - 1; index >= 0; index--) {
            Object requested = nativeFocusListeners.get(index).get();
            if (requested == null || requested == listener) nativeFocusListeners.remove(index);
        }
        if (nativeFocusOwner.get() == listener) nativeFocusOwner = new WeakReference<>(null);
    }

    private static boolean hasNativeFocus() {
        synchronized (nativeFocusListeners) {
            return nativeFocusOwner.get() != null;
        }
    }

    private static final class Target {
        final WeakReference<Object> controller;
        final String awemeId;

        Target(Object controller, String awemeId) {
            this.controller = new WeakReference<>(controller);
            this.awemeId = awemeId;
        }

        boolean isCurrentCell(Object controller) {
            Object aweme;
            try {
                aweme = currentAweme(controller);
            } catch (RuntimeException notAController) {
                return false;
            }
            return awemeId.equals(Reflect.invoke(aweme, "getAid"));
        }
    }

    /**
     * What the hold calls on TikTok's player: the video a PlayerController has on screen, and its
     * player manager's pause and resume. TikTok renames all three with every build (the pause was
     * LIZ on 46.2.3 and is LJJLIIIJJI on 47.0.3, where LIZ reads a number instead), and naming
     * them here left the hold covering a video that played on. So nothing here names them: the
     * Block author patch reads them off PlayerController.pauseVideo and the For You feed's
     * space-key toggle and writes these three bodies. Unpatched, as in the tests, they go to
     * {@link #nativeForTests}.
     */
    interface NativeControls {
        Object currentAweme(Object controller);
        void pause(Object manager);
        void resume(Object manager);
    }

    static volatile NativeControls nativeForTests;

    static Object currentAweme(Object controller) {
        NativeControls controls = nativeForTests;
        return controls == null ? null : controls.currentAweme(controller);
    }

    static boolean pauseNative(Object manager) {
        NativeControls controls = nativeForTests;
        if (controls == null) return false;
        controls.pause(manager);
        return true;
    }

    static boolean resumeNative(Object manager) {
        NativeControls controls = nativeForTests;
        if (controls == null) return false;
        controls.resume(manager);
        return true;
    }

    /** Called with p0 and p1 from PlayerController.onPlayProgressChange(String, long, long). */
    public static void onPlayerProgress(Object controller, String awemeId) {
        if (controller == null || awemeId == null || awemeId.isEmpty()) return;
        Target previous = current;
        if (previous == null || previous.controller.get() != controller
                || !awemeId.equals(previous.awemeId)) {
            current = new Target(controller, awemeId);
        }
        if (!SessionBudget.isLocked()) {
            Target waiting = waitingForFocus;
            if (waiting != null && waiting == current) {
                Object manager = Reflect.invoke(controller, "getPlayerManager");
                boolean pauseWasObserved = heldPauseObserved;
                boolean playing = Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"));
                if ((playing && pauseWasObserved)
                        || (!playing && Boolean.TRUE.equals(Reflect.invoke(manager, "isPaused")))) {
                    // Only playback after an observed pause proves an independent resume.
                    // The old PLAYING state before our queued pause must retain its handback.
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        observeNativePlayback(waiting, manager, playing);
                    } else {
                        WeakReference<Object> observedManager = new WeakReference<>(manager);
                        MAIN.post(() -> observeNativePlayback(waiting, observedManager.get(), playing));
                    }
                }
            }
            // TikTok starts the video again by itself, on a return above all, so while a pause
            // switch wants it stopped each report of it playing pauses it again.
            if (switchWanted && switchQueued.compareAndSet(false, true)) {
                MAIN.post(() -> {
                    switchQueued.set(false);
                    pauseForSwitchIfPlaying();
                });
            }
            return;
        }
        if (!syncQueued.compareAndSet(false, true)) return;
        // Do not pause reentrantly inside a native callback. A fresh report also catches native
        // auto-resume while the panel already exists, including a return from settings.
        MAIN.post(() -> {
            syncQueued.set(false);
            SessionLockOverlay.sync();
        });
    }

    /** The video the player last reported, or null before any report. */
    public static String currentAwemeId() {
        Target target = current;
        return target == null ? null : target.awemeId;
    }

    /**
     * Whether the player of the last reported video is playing it now, or null when there is no
     * such player or it has moved to another video. Reports stop while a video is paused, so the
     * last one names the video the reader paused.
     */
    public static Boolean currentPlaying() {
        Target target = current;
        if (target == null) return null;
        Object controller = target.controller.get();
        if (controller == null || !target.isCurrentCell(controller)) return null;
        Object playing = Reflect.invoke(Reflect.invoke(controller, "getPlayerManager"), "isPlaying");
        return playing instanceof Boolean ? (Boolean) playing : null;
    }

    /** Runs on each visible hold sync, not only when the panel is first attached. */
    static void pauseIfPlaying() {
        Target target = current;
        if (target == null) return;
        Object controller = target.controller.get();
        if (!target.isCurrentCell(controller)) return;
        Object manager = Reflect.invoke(controller, "getPlayerManager");
        if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"))) return;
        // TikTok's feed play/pause control uses this same manager pair. Pause is queued by the
        // native engine, so successful intent is owned without requiring an immediate state flip.
        if (control(manager, true)) {
            held = target;
            heldManager = new WeakReference<>(manager);
            waitingForFocus = null;
            heldPauseObserved = false;
        }
    }

    /**
     * The pause switches want the video on screen stopped: the comments are open, or the feed is
     * waiting for a tap after a return. They ask for the audio focus, and TikTok 47.0.3's player
     * plays on through that, so they stop it the way the panel does, with TikTok's own pause: now
     * if it is playing, and again on any report of it playing, until {@link #releaseForSwitch}.
     * Main thread.
     */
    public static void pauseForSwitch() {
        switchWanted = true;
        pauseForSwitchIfPlaying();
    }

    /**
     * The switch is done. With {@code resume} the video it paused plays on from where it stopped,
     * but only while it is still the one on screen in the same player and no panel is up; a video
     * the reader had paused was never taken, so it is never started. A panel that came up in
     * between keeps the video stopped after it, for the reader to start. Going away passes false,
     * since TikTok stops its player itself then. Main thread.
     */
    public static void releaseForSwitch(boolean resume) {
        switchWanted = false;
        Target owner = switchHeld;
        Object manager = switchManager.get();
        switchHeld = null;
        switchManager.clear();
        if (!resume || owner == null || manager == null || SessionBudget.isLocked()) return;
        Object controller = owner.controller.get();
        if (current != owner || !owner.isCurrentCell(controller)
                || Reflect.invoke(controller, "getPlayerManager") != manager) return;
        // As in release: playing can still be the state before the queued pause, and the resume
        // queues behind it.
        if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"))
                && !Boolean.TRUE.equals(Reflect.invoke(manager, "isPaused"))) return;
        control(manager, false);
    }

    private static void pauseForSwitchIfPlaying() {
        if (!switchWanted || SessionBudget.isLocked()) return;
        Target target = current;
        if (target == null) return;
        Object controller = target.controller.get();
        if (!target.isCurrentCell(controller)) return;
        Object manager = Reflect.invoke(controller, "getPlayerManager");
        if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"))) return;
        if (control(manager, true)) {
            switchHeld = target;
            switchManager = new WeakReference<>(manager);
        }
    }

    private static void observeNativePlayback(Target owner, Object manager, boolean playing) {
        if (playing) relinquishAfterPlayback(owner, manager);
        else if (waitingForFocus == owner && heldManager.get() == manager) heldPauseObserved = true;
    }

    private static void relinquishAfterPlayback(Target owner, Object manager) {
        if (waitingForFocus == owner && heldManager.get() == manager) forgetHeld();
    }

    private static void forgetHeld() {
        waitingForFocus = null;
        heldPauseObserved = false;
        held = null;
        heldManager.clear();
    }

    /** Hands back only the still-current video this hold paused, while its feed is foreground. */
    static void release(boolean mayResume) {
        Target owner = held;
        Object manager = heldManager.get();
        if (owner == null) return;
        Activity activity = Utils.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus() || !FeedVisibility.isOnFeed(activity)) {
            forgetHeld();
            return;
        }
        Object controller = owner.controller.get();
        if (current != owner || !owner.isCurrentCell(controller)
                || manager == null || Reflect.invoke(controller, "getPlayerManager") != manager) {
            forgetHeld();
            return;
        }
        // Pause and resume share the native dispatcher. Playing can still be the state before
        // our queued pause; its matching resume must follow it when the hold is released early.
        // Read playing first so the PLAYING -> PAUSED transition cannot fall between the checks.
        if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"))) {
            if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPaused"))) {
                forgetHeld();
                return;
            }
            heldPauseObserved = true;
        }
        if (!mayResume && !hasNativeFocus()) {
            // Keep this exact weak owner through repeated detach calls until focus can return it.
            waitingForFocus = owner;
            return;
        }
        forgetHeld();
        control(manager, false);
    }

    private static boolean control(Object manager, boolean pause) {
        if (manager == null) return false;
        try {
            boolean done = pause ? pauseNative(manager) : resumeNative(manager);
            if (!done) Logger.printDebug(() -> "The session hold has no native playback control");
            return done;
        } catch (RuntimeException failure) {
            Logger.printException(() -> "The session hold could not change native playback", failure);
            return false;
        }
    }
}
