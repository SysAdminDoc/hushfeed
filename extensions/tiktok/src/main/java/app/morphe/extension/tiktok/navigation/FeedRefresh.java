/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.navigation;

import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Keeps the For You feed where it is when TikTok would reload it: a tap on Home or on the For You
 * tab while For You is showing, or a pull down at the top of the feed. The Following, Friends and
 * Nearby feeds are left alone.
 *
 * <p>A tap reaches the For You fragment's refresh with TikTok's trigger (CLICK_BOTTOM or
 * CLICK_TOP). A pull is answered earlier, at the refresh panel's pull listener: past the fragment's
 * refresh, that listener tells the feed and the fragment's other refresh listeners, and those load
 * the feed on their own. Either way a kept refresh ends the way TikTok ends one that arrives while
 * the feed is still loading, which the patch writes: the spinner stops and TikTok's refresh-end
 * event goes out, which turns the Home icon back from its refresh arrow.
 */
public final class FeedRefresh {
    static final String FAMILY = "for you refresh";
    private static final String FOR_YOU_FRAGMENT = "com.ss.android.ugc.aweme.feed.ui.FeedRecommendFragment";

    private FeedRefresh() {
    }

    /** Whether the For You fragment may go on with the refresh {@code trigger} asks for. */
    public static boolean allowRefresh(Object trigger) {
        HookStatus.bound(FAMILY, "refresh");
        String name = trigger instanceof Enum ? ((Enum<?>) trigger).name() : null;
        if (("CLICK_BOTTOM".equals(name) || "CLICK_TOP".equals(name)) && Settings.KEEP_FOR_YOU_ON_TAB_TAP.get()) {
            HookStatus.bound(FAMILY, "tab tap kept");
            return false;
        }
        return true;
    }

    /** Whether a pull down on {@code fragment}'s feed ends before it reloads anything. */
    public static boolean keepPull(Object fragment) {
        HookStatus.bound(FAMILY, "pull");
        if (!Settings.KEEP_FOR_YOU_ON_PULL_DOWN.get() || !isForYou(fragment)) return false;
        HookStatus.bound(FAMILY, "pull down kept");
        return true;
    }

    private static boolean isForYou(Object fragment) {
        for (Class<?> type = fragment == null ? null : fragment.getClass(); type != null; type = type.getSuperclass()) {
            if (FOR_YOU_FRAGMENT.equals(type.getName())) return true;
        }
        return false;
    }
}
