/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

/**
 * The things TikTok slots into a feed that are not videos: the playlist bar along the
 * bottom of a video, the floating event badge, the friend recommendation card and its
 * bulletin board relatives, and the countdown lock on a short drama advert.
 *
 * These are stopped where they are built rather than filtered out of a list, because none
 * of them arrives as an ordinary feed item. The one exception is the inserted card, which
 * also shows up in the list as aweme type 105, so it gets a list filter too.
 */
public final class CardFilters {
    private CardFilters() {
    }

    /** The playlist bar TikTok shows along the bottom of a video in a series. */
    public static boolean shouldHidePlaylistBar() {
        return Settings.HIDE_PLAYLIST_BAR.get();
    }

    /** The floating promotional event badge. */
    public static boolean shouldHideEventBadge() {
        return Settings.HIDE_EVENT_BADGE.get();
    }

    /** The friend recommendation card and the other cards inserted between videos. */
    public static boolean shouldHideInsertedCards() {
        return Settings.HIDE_INSERTED_CARDS.get();
    }

    /**
     * TikTok locks scrolling until a short drama advert has counted down. It asks whether
     * the item is a blocking advert; answering no releases the scroll.
     */
    public static boolean shouldBlockForDramaAd(boolean isBlockingAd) {
        if (isBlockingAd && Settings.REMOVE_ADS.get()) {
            Logger.printInfo(() -> "Released the countdown lock on a short drama advert");
            return false;
        }
        return isBlockingAd;
    }

    /** The list side of {@link #shouldHideInsertedCards()}. */
    public static final class InsertedCardFilter implements IFilter {
        private static final int AWEME_TYPE_INSERT_CARD = 105;

        @Override
        public boolean getEnabled() {
            return shouldHideInsertedCards();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            return item.getAwemeType() == AWEME_TYPE_INSERT_CARD;
        }
    }
}
