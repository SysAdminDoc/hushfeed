/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;

/**
 * Holds the author of the video that is currently on screen.
 *
 * Fed from {@code VideoAuthorInfoVM.paramSync2StateAccept}, which TikTok calls each time a
 * feed item becomes current.
 */
public final class CurrentVideoAuthor {
    /**
     * How long a captured author stays valid. If the feed view model stops reporting,
     * the button hides itself rather than acting on a stale account.
     */
    private static final long FRESHNESS_MS = 30_000L;

    private static volatile VideoAuthor author;
    private static volatile long capturedAtMs;

    private CurrentVideoAuthor() {
    }

    /**
     * @param videoItemParams a {@code com.ss.android.ugc.aweme.feed.model.VideoItemParams}
     */
    static void update(Object videoItemParams) {
        VideoAuthor parsed = parse(videoItemParams);
        if (parsed == null || !parsed.isUsable()) {
            return;
        }

        VideoAuthor previous = author;
        author = parsed;
        capturedAtMs = System.currentTimeMillis();

        if (!parsed.equals(previous)) {
            Logger.printDebug(() -> "Current video author: " + parsed.label()
                    + " aweme=" + parsed.awemeId);
            BlockAuthorOverlay.onAuthorChanged(parsed);
        }
    }

    /** @return the current author, or null when nothing fresh has been captured. */
    public static VideoAuthor get() {
        VideoAuthor current = author;
        if (current == null) {
            return null;
        }
        if (System.currentTimeMillis() - capturedAtMs > FRESHNESS_MS) {
            return null;
        }
        return current;
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
