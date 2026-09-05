/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Resolves the API host the app is already talking to.
 *
 * TikTok picks a regional host at startup, so the value is read from the app's own
 * constants rather than assumed. The fallback is only used when none of them can be read.
 */
final class ApiHost {
    private static final String FALLBACK = "https://api16-normal-c-useast1a.tiktokv.com";

    /** Classes that hold the API prefix as a static String field. */
    private static final String[] CANDIDATE_HOLDERS = {
            "com.ss.android.ugc.aweme.app.api.Api",
            "com.ss.android.ugc.aweme.net.model.ApiConstants",
            "com.ss.android.ugc.aweme.app.constants.Constants",
    };

    private static volatile String resolved;

    private ApiHost() {
    }

    static String baseUrl() {
        String cached = resolved;
        if (cached != null) {
            return cached;
        }

        String discovered = discover();
        if (discovered == null) {
            discovered = FALLBACK;
            Logger.printInfo(() -> "Falling back to the default API host");
        } else {
            final String host = discovered;
            Logger.printDebug(() -> "Resolved API host: " + host);
        }

        resolved = discovered;
        return discovered;
    }

    private static String discover() {
        for (String holderName : CANDIDATE_HOLDERS) {
            try {
                Class<?> holder = Class.forName(holderName);
                for (Field field : holder.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (isUsableHost(value)) {
                        return trimTrailingSlash((String) value);
                    }
                }
            } catch (Throwable ignored) {
                // Try the next holder.
            }
        }
        return null;
    }

    private static boolean isUsableHost(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String text = (String) value;
        // A bare origin, not an endpoint path.
        return text.startsWith("https://")
                && text.indexOf('/', "https://".length()) <= 0
                && text.contains("tiktok");
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
