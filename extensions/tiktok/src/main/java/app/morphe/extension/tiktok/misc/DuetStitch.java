/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.misc;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Whether a video says it may be duetted or stitched.
 *
 * <p>TikTok reads one number off the video: 2 means the creator closed it, 1 means friends
 * only, and anything else carries on to the other checks. Those other checks are the ones
 * worth keeping. A photo post still cannot be duetted, a deleted video still cannot, and the
 * app's own reasons for refusing all still apply, because this only answers the question the
 * creator's setting answers.
 */
public final class DuetStitch {
    private DuetStitch() {}

    /**
     * True when the video's own setting should read as zero, which is the number the app
     * takes to mean anyone may.
     */
    public static boolean allow() {
        return Settings.ALLOW_DUET_AND_STITCH.get();
    }
}
