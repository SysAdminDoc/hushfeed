/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feed;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Takes down the interstitial TikTok lays over a video it has classified, so the video
 * plays instead of asking to be tapped through on every pass.
 *
 * A video carries its masks on the Aweme itself, in fields that keep their real names on
 * 46.2.3: {@code videoMaskInfo} and the {@code videoMaskInfos} list, plus the four typed
 * ones. Each is a {@code VideoMaskInfo} whose {@code showMask} flag decides whether the
 * overlay is drawn, so clearing that flag is enough and leaves the rest of the model alone.
 * The four typed fields are additionally dropped, which is what TikTok's own reset does to
 * them, so a null there is a state the app already handles.
 *
 * This runs as the item is bound, before anything has read the model to build the overlay.
 */
public final class SensitiveWarnings {
    /** Cleared outright; TikTok's own reset nulls exactly these four. */
    private static final String[] DROPPED_MASKS = {
            "generalMaskInfo",
            "photosensitiveMaskInfo",
            "reportMaskInfo",
            "contentClassificationMaskInfo",
    };

    /** Kept, with the overlay flag turned off. */
    private static final String[] FLAGGED_MASKS = {
            "videoMaskInfo",
    };

    private static final String MASK_LIST = "videoMaskInfos";
    private static final String SHOW_MASK = "showMask";

    private SensitiveWarnings() {
    }

    /**
     * Called as TikTok binds a feed item.
     *
     * @param videoItemParams a {@code com.ss.android.ugc.aweme.feed.model.VideoItemParams}
     */
    public static void clear(Object videoItemParams) {
        try {
            if (!Settings.HIDE_SENSITIVE_WARNINGS.get()) {
                return;
            }

            Object aweme = Reflect.property(videoItemParams, "getAweme", "aweme");
            if (aweme == null) {
                return;
            }

            for (String name : FLAGGED_MASKS) {
                hideMask(Reflect.readField(aweme, name));
            }

            Object list = Reflect.readField(aweme, MASK_LIST);
            if (list instanceof List) {
                for (Object mask : (List<?>) list) {
                    hideMask(mask);
                }
            }

            for (String name : DROPPED_MASKS) {
                hideMask(Reflect.readField(aweme, name));
                clearField(aweme, name);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not clear the content warning", ex);
        }
    }

    /** Turns off a single mask's overlay flag. */
    private static void hideMask(Object mask) {
        if (mask == null) {
            return;
        }
        try {
            Field field = findField(mask.getClass(), SHOW_MASK);
            if (field != null) {
                field.set(mask, Boolean.FALSE);
            }
        } catch (Throwable ignored) {
            // A build that reshaped the model just keeps its warning.
        }
    }

    private static void clearField(Object target, String name) {
        try {
            Field field = findField(target.getClass(), name);
            if (field != null) {
                field.set(target, null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> type, String name) {
        return Reflect.field(type, name);
    }
}
