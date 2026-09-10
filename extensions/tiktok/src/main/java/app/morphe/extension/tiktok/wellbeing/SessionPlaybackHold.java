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
import java.lang.reflect.Method;
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
    private static WeakReference<Object> heldManager = new WeakReference<>(null);
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
    }

    /** Receives a focus change for the same native listener that requested it. */
    public static void onNativeFocusChange(Object listener, int change) {
        if (listener == null) return;
        synchronized (nativeFocusListeners) {
            if (change == AudioManager.AUDIOFOCUS_GAIN) {
                for (WeakReference<Object> request : nativeFocusListeners) {
                    if (request.get() == listener) {
                        nativeFocusOwner = new WeakReference<>(listener);
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
            Object aweme = Reflect.invoke(controller, "LIZIZ");
            return awemeId.equals(Reflect.invoke(aweme, "getAid"));
        }
    }

    /** Called with p0 and p1 from PlayerController.onPlayProgressChange(String, long, long). */
    public static void onPlayerProgress(Object controller, String awemeId) {
        if (controller == null || awemeId == null || awemeId.isEmpty()) return;
        Target previous = current;
        if (previous == null || previous.controller.get() != controller
                || !awemeId.equals(previous.awemeId)) {
            current = new Target(controller, awemeId);
        }
        if (!SessionBudget.isLocked() || !syncQueued.compareAndSet(false, true)) return;
        // Do not pause reentrantly inside a native callback. A fresh report also catches native
        // auto-resume while the panel already exists, including a return from settings.
        MAIN.post(() -> {
            syncQueued.set(false);
            SessionLockOverlay.sync();
        });
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
        if (invoke(manager, "LIZ")) {
            held = target;
            heldManager = new WeakReference<>(manager);
        }
    }

    /** Hands back only the still-current video this hold paused, while its feed is foreground. */
    static void release(boolean mayResume) {
        Target owner = held;
        Object manager = heldManager.get();
        held = null;
        heldManager = new WeakReference<>(null);
        if (owner == null || (!mayResume && !hasNativeFocus())) return;
        Activity activity = Utils.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || !activity.hasWindowFocus() || !FeedVisibility.isOnFeed(activity)) return;
        Object controller = owner.controller.get();
        if (current != owner || !owner.isCurrentCell(controller)
                || manager == null || Reflect.invoke(controller, "getPlayerManager") != manager) return;
        // Pause and resume share the native dispatcher. Playing can still be the state before
        // our queued pause; its matching resume must follow it when the hold is released early.
        // Read playing first so the PLAYING -> PAUSED transition cannot fall between the checks.
        if (!Boolean.TRUE.equals(Reflect.invoke(manager, "isPlaying"))
                && !Boolean.TRUE.equals(Reflect.invoke(manager, "isPaused"))) return;
        invoke(manager, "LJIILL");
    }

    private static boolean invoke(Object manager, String name) {
        if (manager == null) return false;
        Method method = Reflect.method(manager.getClass(), name);
        if (method == null) {
            Logger.printDebug(() -> "The session hold could not find native playback control " + name);
            return false;
        }
        try {
            method.invoke(manager);
            return true;
        } catch (Exception failure) {
            Logger.printException(() -> "The session hold could not change native playback", failure);
            return false;
        }
    }
}
