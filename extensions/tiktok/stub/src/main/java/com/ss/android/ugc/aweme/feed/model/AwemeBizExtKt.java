package com.ss.android.ugc.aweme.feed.model;

public final class AwemeBizExtKt {
    public static int getCacheSourceType(Aweme aweme) {
        return aweme == null ? 0 : aweme.cacheSourceType;
    }
}
