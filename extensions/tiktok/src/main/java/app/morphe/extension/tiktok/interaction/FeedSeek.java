/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.interaction;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Where the playing video is, and how to move it.
 *
 * <p>TikTok's {@code PlayerController} kept its name and so did the two methods that matter.
 * Its progress callback runs several times a second while a video plays and carries the
 * position and the length, and {@code getPlayerManager()} hands back the player the seekbar
 * drags. The manager's own {@code seek(float milliseconds)} kept its name too, but the
 * interface declaring it did not, so that one call goes through reflection on whichever
 * concrete class the manager turns out to be.
 */
public final class FeedSeek {
    private static WeakReference<Object> player = new WeakReference<>(null);
    private static long positionMs;
    private static long durationMs;
    private static Class<?> seekOwner;
    private static Method seekMethod;

    private FeedSeek() {}

    /**
     * {@code PlayerController.onPlayProgressChange(sourceId, position, duration)}. A tick with
     * no source id belongs to a player holding nothing, and a length of zero leaves a seek
     * with nothing to clamp against, so neither is worth keeping.
     */
    public static void recordProgress(Object controller, String sourceId, long position, long duration) {
        if (controller == null || sourceId == null || sourceId.isEmpty() || duration <= 0) return;
        player = new WeakReference<>(controller);
        positionMs = position;
        durationMs = duration;
    }

    /** Moves the playing video by {@code deltaMs}, kept inside its length. */
    static boolean seekBy(long deltaMs) {
        Object controller = player.get();
        if (controller == null || durationMs <= 0) return false;

        long target = positionMs + deltaMs;
        if (target < 0) target = 0;
        // A millisecond short of the end, because landing on the end finishes the video.
        if (target >= durationMs) target = durationMs - 1;

        Object manager = Reflect.invoke(controller, "getPlayerManager");
        if (manager == null || !seek(manager, target)) return false;

        // A second press counts from where the first one landed. The progress tick that would
        // say so has not arrived yet.
        positionMs = target;
        return true;
    }

    private static boolean seek(Object manager, long targetMs) {
        try {
            Method method = seekMethod(manager.getClass());
            if (method == null) return false;
            method.invoke(manager, (float) targetMs);
            return true;
        } catch (Exception exception) {
            Logger.printException(() -> "Could not seek the playing video", exception);
            return false;
        }
    }

    /** Remembered for the one class the manager is, which does not change while the app runs. */
    private static Method seekMethod(Class<?> type) {
        if (type == seekOwner) return seekMethod;
        seekOwner = type;
        seekMethod = null;
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("seek", float.class);
                method.setAccessible(true);
                seekMethod = method;
                break;
            } catch (NoSuchMethodException ignored) {
                // keep climbing
            } catch (Throwable ignored) {
                break;
            }
        }
        return seekMethod;
    }
}
