/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.misc;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * What the window should ask the screen to run at.
 *
 * <p>When a video starts, TikTok reads its frame rate and asks the window for that, so a
 * 30 fps video puts a 120 Hz phone at 30 Hz for as long as the app is in front. Android
 * reads zero as no preference, which is the phone's own choice, so that is what the switch
 * sends instead. Nothing here raises anything: it only stops the asking.
 */
public final class RefreshRate {
    /** {@code WindowManager.LayoutParams.preferredRefreshRate}: no preference. */
    private static final float NO_PREFERENCE = 0f;

    private RefreshRate() {}

    public static float preferredRefreshRate(float requested) {
        return Settings.UNCAP_REFRESH_RATE.get() ? NO_PREFERENCE : requested;
    }
}
