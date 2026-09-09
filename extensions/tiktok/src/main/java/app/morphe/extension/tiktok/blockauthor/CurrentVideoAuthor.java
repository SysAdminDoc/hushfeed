/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;

import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionBudgetNotice;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds the author of the video that is on screen.
 *
 * Two signals feed this, and the difference between them matters.
 *
 * {@code VideoAuthorInfoVM.paramSync2StateAccept} runs when TikTok <em>binds</em> an item,
 * which a paging feed does for the neighbours you have not reached yet. Taking the most
 * recent bind as the current video is therefore wrong: by the time you tap, it is usually
 * the video below the one you are watching. That is only a cache here.
 *
 * {@code PlayerController.onPlayProgressChange} reports the id of the video that is
 * actually <em>playing</em>, several times a second. That is what selects the current item
 * out of the cache. When the player names a video that has not been bound yet, nothing is
 * current and the button hides rather than arming the wrong creator.
 */
public final class CurrentVideoAuthor {
    /**
     * How many bound items to remember. The feed binds a couple either side of the current
     * one, so this is generous; it exists to bound the map, not to tune behaviour.
     */
    private static final int RECENT_LIMIT = 16;

    private static final Map<String, Item> RECENT = Collections.synchronizedMap(
            new LinkedHashMap<String, Item>(32, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Item> eldest) {
                    return size() > RECENT_LIMIT;
                }
            });

    private static volatile Item current;
    private static volatile String playingAwemeId;

    private CurrentVideoAuthor() {
    }

    /** One bound feed item. {@code author} is null for a card with nobody to block. */
    private static final class Item {
        final String awemeId;
        final VideoAuthor author;
        final Object aweme;

        Item(String awemeId, VideoAuthor author, Object aweme) {
            this.awemeId = awemeId;
            this.author = author;
            this.aweme = aweme;
        }
    }

    /**
     * Called as TikTok binds a feed item, which happens ahead of the item being reached.
     *
     * @param videoItemParams a {@code com.ss.android.ugc.aweme.feed.model.VideoItemParams}
     */
    static void update(Object videoItemParams) {
        Item item = parse(videoItemParams);
        if (item != null && item.awemeId != null) {
            RECENT.put(item.awemeId, item);
        }

        // A bind may fill an empty selection but never replace one. That covers the first
        // video of a fresh process, where nothing has played yet, and the moment after the
        // player names a video the feed had not bound. Replacing a live selection is the
        // player's job alone, which is what keeps a prefetched neighbour from arming the
        // button.
        if (current == null || (item != null && item.awemeId != null
                && item.awemeId.equals(playingAwemeId))) {
            select(item);
        }
    }

    /**
     * Called from {@code PlayerController.onPlayProgressChange} for the video that is
     * playing. Fires several times a second, so it returns immediately once the id is
     * already the current one.
     *
     * @param awemeId the playing video's id
     */
    static void onPlaying(String awemeId) {
        if (awemeId == null || awemeId.isEmpty()) {
            return;
        }

        // Before the early return: this is the only signal that arrives while a video plays,
        // so it is the only thing that can measure how long the feed has been running.
        SessionBudget.noteWatching();
        SessionLockOverlay.ensureRunning();

        if (awemeId.equals(playingAwemeId)) {
            return;
        }
        playingAwemeId = awemeId;

        // A miss means the player is ahead of the bind. Selecting null hides the button
        // for that moment, which is the right answer: better no button than one wired to
        // the previous creator.
        select(RECENT.get(awemeId));
    }

    /** Applies a new current item and tells everything that tracks the current video. */
    private static void select(Item item) {
        Item previous = current;
        current = item;

        String previousId = previous == null ? null : previous.awemeId;
        String newId = item == null ? null : item.awemeId;

        if (!Objects.equals(previousId, newId)) {
            SessionBudget.noteVideo(newId);
            if (SessionBudget.claimNotice()) SessionBudgetNotice.show();
            // Checked where the day's own notice is checked, on the video change rather than on
            // the progress callback: the reminder is measured in watched minutes but it should
            // arrive between videos rather than over one.
            SessionBudgetNotice.showIntervalNoticeIfDue();
            app.morphe.extension.tiktok.interaction.TapConfirmation.onVideoChanged();
            app.morphe.extension.tiktok.captions.CaptionTools.onVideoChanged(newId);

            if (item != null && item.aweme != null) {
                CurrentVideoSound.update(item.aweme);
            } else {
                CurrentVideoSound.clear();
            }
        }

        VideoAuthor author = item == null ? null : item.author;
        VideoAuthor previousAuthor = previous == null ? null : previous.author;
        if (Objects.equals(author, previousAuthor)) {
            return;
        }

        if (author == null) {
            Logger.printDebug(() -> "Current item has no blockable author");
        } else {
            Logger.printDebug(() -> "Current video author: " + author.label()
                    + " aweme=" + author.awemeId);
        }
        BlockAuthorOverlay.onAuthorChanged(author);
    }

    /**
     * @return the author of the video that is playing, or null when there is nobody to
     *         block on it. There is deliberately no expiry: a video can run for minutes,
     *         and the player keeps naming the same id throughout. Keeping the button off
     *         other screens is {@link FeedVisibility}'s job, not this one's.
     */
    public static VideoAuthor get() {
        Item item = current;
        return item == null ? null : item.author;
    }

    public static Object getAweme() {
        Item item = current;
        return item == null ? null : item.aweme;
    }

    static void resetForTests() {
        RECENT.clear();
        current = null;
        playingAwemeId = null;
        CurrentVideoSound.clear();
    }

    /** Reads the item without touching anything that tracks the current video. */
    private static Item parse(Object videoItemParams) {
        try {
            Object aweme = Reflect.property(videoItemParams, "getAweme", "aweme");
            if (aweme == null) {
                return null;
            }

            String awemeId = Reflect.string(aweme, "getAid", "aid");
            Object user = Reflect.property(aweme, "getAuthor", "author");
            if (user == null) {
                // A LIVE preview, a promo or an end of feed card: real item, nobody to block.
                return new Item(awemeId, null, aweme);
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

            VideoAuthor author = new VideoAuthor(uid, secUid, displayName, awemeId);
            return new Item(awemeId, author.isUsable() ? author : null, aweme);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not read the current video author", ex);
            return null;
        }
    }
}
