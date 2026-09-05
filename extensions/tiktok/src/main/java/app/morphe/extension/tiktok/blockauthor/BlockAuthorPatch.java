/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Entry points called from patched TikTok code.
 *
 * Everything here must stay cheap and must never throw: it runs on the feed's hot path.
 */
public final class BlockAuthorPatch {
    private BlockAuthorPatch() {
    }

    /**
     * Called from {@code VideoAuthorInfoVM.paramSync2StateAccept} each time a feed item
     * becomes the current video.
     *
     * @param videoItemParams a {@code com.ss.android.ugc.aweme.feed.model.VideoItemParams}
     */
    public static void setCurrentVideoParams(Object videoItemParams) {
        try {
            if (!Settings.BLOCK_AUTHOR_BUTTON.get()) {
                return;
            }
            CurrentVideoAuthor.update(videoItemParams);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not track the current video author", ex);
        }
    }
}
