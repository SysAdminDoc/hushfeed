/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.featurecontrols;

import android.view.View;

import app.morphe.extension.tiktok.settings.Settings;

public final class FeatureControls {
    private static final int DEFAULT_LONG_PRESS_LOCK_DISTANCE_DP = 140;

    private FeatureControls() {
    }

    public static Object filterNormalPendant(Object pendant) {
        return filterPromotionalTouchPoint(pendant);
    }

    public static Object filterPromotionalTouchPoint(Object touchPoint) {
        if (!Settings.HIDE_HOMEPAGE_COIN.get() || touchPoint == null) return touchPoint;

        String className = touchPoint.getClass().getName();
        switch (className) {
            case "com.bytedance.touchpoint.api.model.NormalPendant":
            case "com.bytedance.touchpoint.api.model.TimerPendant":
            case "com.bytedance.touchpoint.api.model.SunshinePendant":
            case "com.bytedance.touchpoint.api.model.CoinBottomTab":
            case "com.bytedance.touchpoint.api.model.BottomTabBubble":
                return null;
            default:
                return touchPoint;
        }
    }

    public static boolean overrideLongPressSpeedUpEnabled(boolean enabled) {
        return Settings.ENABLE_LONG_PRESS_SPEED_LOCK.get() || enabled;
    }

    public static int overrideLongPressSpeedUpLockDistance(int distanceDp) {
        if (!Settings.ENABLE_LONG_PRESS_SPEED_LOCK.get()) return distanceDp;
        return distanceDp > 0 ? distanceDp : DEFAULT_LONG_PRESS_LOCK_DISTANCE_DP;
    }

    public static boolean overrideHideQuickCommentEmoji(boolean original, int followStatus) {
        return Settings.HIDE_COMMENT_QUICK_REACTIONS.get() || original;
    }

    public static int hideFeedFollowButtonVisibility(int originalVisibility) {
        return Settings.HIDE_FEED_FOLLOW_BUTTON.get() ? View.GONE : originalVisibility;
    }

    public static int hideFeedSaveButtonVisibility(int originalVisibility) {
        return Settings.HIDE_FEED_SAVE_BUTTON.get() ? View.GONE : originalVisibility;
    }

    /**
     * Shares {@code HIDE_LIVE_ENTRANCE} with the video overlay hider, so the switch keeps
     * its stored value and both mechanisms answer to it.
     */
    public static boolean hideFeedLiveButtonEnabled(boolean originalEnabled) {
        return !Settings.HIDE_LIVE_ENTRANCE.get() && originalEnabled;
    }

    public static boolean hideFeedSearchButtonEnabled(boolean originalEnabled) {
        return !Settings.HIDE_FEED_SEARCH_BUTTON.get() && originalEnabled;
    }

    public static int overrideLongPressQuickShare(int originalMode) {
        return Settings.DISABLE_LONG_PRESS_QUICK_SHARE.get() ? 0 : originalMode;
    }

    public static boolean disableLongPressRepost() {
        return Settings.DISABLE_LONG_PRESS_REPOST.get();
    }

    public static boolean enableNonPersonalizedSearch(boolean original) {
        return Settings.ENABLE_NON_PERSONALIZED_SEARCH.get() || original;
    }

    public static int forceNonPersonalizedSearchGate(String key, int value) {
        if (!"enable_non_personalized_search".equals(key)) return value;
        return Settings.ENABLE_NON_PERSONALIZED_SEARCH.get() ? 1 : value;
    }

    public static boolean enableLiveSearch(boolean original) {
        return Settings.ENABLE_LIVE_SEARCH.get() || original;
    }
}
