/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.seekbar;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("unused")
public final class SeekbarPatch {
    private static final String LOG_PREFIX = "[Morphe TikTok Seekbar]";

    private SeekbarPatch() {}

    public static boolean isEnabled() {
        return Settings.SHOW_SEEKBAR.get();
    }

    public static int overrideSeekbarShowType(int seekbarType) {
        if (!Settings.SHOW_SEEKBAR.get()) return seekbarType;
        if (seekbarType != 3 && seekbarType != 4) return seekbarType;

        if (Settings.DEBUG.get()) {
            Logger.printDebug(() -> LOG_PREFIX + " Overrode seekbar show type " + seekbarType + " -> 0");
        }
        return 0;
    }

    /**
     * TikTok 47.0.3 returns from its show-type setter when the stored type already equals the
     * requested type. After {@link #overrideSeekbarShowType(int)} changes a hidden request to
     * zero, that shortcut can leave the actual progress view invisible even though the stored
     * type says it is visible. Give only that equality check a value that cannot match so the
     * host runs its normal visible branch and writes the real requested type back to the field.
     */
    public static int forceSeekbarRefresh(int storedType, int requestedType) {
        if (!Settings.SHOW_SEEKBAR.get() || requestedType != 0 || storedType != requestedType) {
            return storedType;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * TikTok's "draft progress bar" flag on a video, read by its can-drag gate once the gate's
     * own experiment has said no. The server leaves it off on most videos, so with the
     * experiment off the bar stayed hidden on all of them. Any non-zero value passes the gate.
     */
    public static int overrideDraftProgressBar(int value) {
        if (!Settings.SHOW_SEEKBAR.get()) return value;
        return value != 0 ? value : 1;
    }

    public static int overrideThumbnailGate(String key, int value) {
        if (!"seekbar_show_thumbnail_when_drag".equals(key)) return value;
        return Settings.SHOW_SEEKBAR_THUMBNAIL.get() ? 1 : value;
    }
}
