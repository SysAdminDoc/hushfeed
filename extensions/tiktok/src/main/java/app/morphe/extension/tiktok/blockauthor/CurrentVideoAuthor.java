/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import android.os.SystemClock;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Holds the author of the video that is currently on screen.
 *
 * Fed from {@code VideoAuthorInfoVM.paramSync2StateAccept}, which TikTok calls each time a
 * feed item becomes current.
 */
public final class CurrentVideoAuthor {
    private static volatile VideoAuthor author;

    /** When the feed last reported an item, on the monotonic clock. Read by FeedVisibility. */
    private static volatile long lastReportMs;

    private CurrentVideoAuthor() {
    }

    /**
     * @param videoItemParams a {@code com.ss.android.ugc.aweme.feed.model.VideoItemParams}
     */
    static void update(Object videoItemParams) {
        lastReportMs = SystemClock.elapsedRealtime();
        FeedVisibility.noteReport(Utils.getActivity());

        VideoAuthor parsed = parse(videoItemParams);
        if (parsed == null || !parsed.isUsable()) {
            // A card with nothing to block (a LIVE preview, a promo, an end of feed card)
            // must not leave the previous creator armed behind the button.
            if (author != null) {
                author = null;
                Logger.printDebug(() -> "Current item has no blockable author");
                BlockAuthorOverlay.onAuthorChanged(null);
            }
            return;
        }

        VideoAuthor previous = author;
        author = parsed;

        if (!parsed.equals(previous)) {
            Logger.printDebug(() -> "Current video author: " + parsed.label()
                    + " aweme=" + parsed.awemeId);
            BlockAuthorOverlay.onAuthorChanged(parsed);
        }
    }

    /**
     * @return the author of the video most recently made current, or null before the
     *         first one. There is deliberately no expiry: a video can run for minutes,
     *         and the feed only reports again when the item changes. Keeping the button
     *         off other screens is {@link FeedVisibility}'s job, not this one's.
     */
    public static VideoAuthor get() {
        return author;
    }

    /** @return {@link SystemClock#elapsedRealtime()} of the most recent feed report, or 0. */
    static long lastReportMs() {
        return lastReportMs;
    }

    private static VideoAuthor parse(Object videoItemParams) {
        try {
            Object aweme = Reflect.property(videoItemParams, "getAweme", "aweme");
            if (aweme == null) {
                return null;
            }

            Object user = Reflect.property(aweme, "getAuthor", "author");
            if (user == null) {
                return null;
            }

            String uid = Reflect.firstNonBlank(
                    Reflect.string(user, "getUid", "uid"),
                    Reflect.string(aweme, "getAuthorUid", "authorUid")
            );
            String secUid = Reflect.string(user, "getSecUid", "secUid");
            String displayName = Reflect.firstNonBlank(
                    Reflect.string(user, "getUniqueId", "uniqueId"),
                    Reflect.string(user, "getNickname", "nickname")
            );
            String awemeId = Reflect.string(aweme, "getAid", "aid");

            return new VideoAuthor(uid, secUid, displayName, awemeId);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not read the current video author", ex);
            return null;
        }
    }
}
