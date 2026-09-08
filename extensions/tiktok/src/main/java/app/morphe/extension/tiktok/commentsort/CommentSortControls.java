/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e1fb74c7
 */
package app.morphe.extension.tiktok.commentsort;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * TikTok has a full comment sort sheet with hot, time, media and creator modes, and decides who
 * gets it with a rollout gate and a per-post eligibility check. Both answers are replaced when
 * the switch is on, so the sheet is the one TikTok already builds rather than one of ours.
 */
@SuppressWarnings("unused")
public final class CommentSortControls {
    /** The style value that asks for the full sheet rather than the cut-down row. */
    private static final int FULL_SORT_SHEET_STYLE = 2;

    private CommentSortControls() {
    }

    public static int forceOptionStyle(int originalStyle) {
        if (!Settings.COMMENT_SORT_CONTROLS.get()) return originalStyle;
        Logger.printDebug(() -> "Comment sort style " + originalStyle + " to " + FULL_SORT_SHEET_STYLE);
        return FULL_SORT_SHEET_STYLE;
    }

    public static boolean shouldForceSortEligibility() {
        return Settings.COMMENT_SORT_CONTROLS.get();
    }
}
