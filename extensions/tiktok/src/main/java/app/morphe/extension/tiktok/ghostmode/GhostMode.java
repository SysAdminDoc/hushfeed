/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows eduardo3677-ai/tiktok-patches-for-morphe.
 */
package app.morphe.extension.tiktok.ghostmode;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Answers TikTok's own reporting calls so they return before sending anything. This stops
 * the client telling other people what you looked at; it cannot take back what the server
 * already recorded, and a viewer list can still fill up from another device.
 */
public final class GhostMode {
    private GhostMode() {
    }

    public static boolean shouldBlockStoryView() {
        return Settings.GHOST_MODE.get();
    }

    public static boolean shouldBlockProfileView() {
        return Settings.GHOST_MODE.get();
    }

    public static boolean shouldBlockTypingStatus() {
        return Settings.GHOST_MODE.get();
    }

    public static boolean shouldBlockPresence() {
        return Settings.GHOST_MODE.get();
    }
}
