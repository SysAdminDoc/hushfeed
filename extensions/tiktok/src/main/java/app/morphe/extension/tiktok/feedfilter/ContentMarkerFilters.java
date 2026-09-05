/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import java.util.Collection;

/**
 * One-predicate feed filters on markers TikTok attaches to a video.
 *
 * Every accessor named here is an unobfuscated getter on {@code Aweme} in TikTok 46.2.3:
 * {@code getAigcInfo}, {@code getModerationAigcInfo}, {@code getBrandContentAccounts},
 * {@code getCommerceVideoAuthInfo}, {@code getCommercialVideoInfo}, {@code isPaidContent},
 * {@code getMPaidContentInfo}, {@code getMixInfo} and {@code getAuthor}. Names inside the
 * nested structs are read by reflection with fallbacks, since those were not all confirmed.
 */
public final class ContentMarkerFilters {
    private ContentMarkerFilters() {
    }

    /** Videos carrying TikTok's AI generated label. */
    public static class AiGeneratedFilter implements IFilter {
        @Override
        public boolean getEnabled() {
            return Settings.HIDE_AI_GENERATED.get();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            Object aigc = Reflect.property(item, "getAigcInfo", "aigcInfo");
            if (aigc != null) {
                Object labelType = Reflect.property(aigc, "getAIGCLabelType", "aigcLabelType");
                if (labelType instanceof Number && ((Number) labelType).intValue() != 0) {
                    return true;
                }
            }
            return Reflect.property(item, "getModerationAigcInfo", "moderationAigcInfo") != null;
        }
    }

    /** Videos marked as paid partnership or branded content. */
    public static class PaidPartnershipFilter implements IFilter {
        @Override
        public boolean getEnabled() {
            return Settings.HIDE_PAID_PARTNERSHIP.get();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            Object accounts = Reflect.property(item, "getBrandContentAccounts", "brandContentAccounts");
            if (accounts instanceof Collection && !((Collection<?>) accounts).isEmpty()) {
                return true;
            }

            // The commerce struct is present on many ordinary videos, so its mere presence
            // is not a signal; only its own commerce flag is. (isCommerce is in the app's
            // string table; getBrandedContentType is not, so it is not tried.)
            Object commerce = Reflect.property(item, "getCommerceVideoAuthInfo", "commerceVideoAuthInfo");
            if (commerce != null) {
                Object isCommerce = Reflect.property(commerce, "isCommerce", "isCommerce");
                if (Boolean.TRUE.equals(isCommerce)) {
                    return true;
                }
            }

            String info = Reflect.string(item, "getCommercialVideoInfo", "commercialVideoInfo");
            return info != null;
        }
    }

    /** Videos that belong to a paid Series. */
    public static class SeriesFilter implements IFilter {
        @Override
        public boolean getEnabled() {
            return Settings.HIDE_SERIES.get();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            Object paid = Reflect.property(item, "isPaidContent", "isPaidContent");
            if (Boolean.TRUE.equals(paid)) {
                return true;
            }
            return Reflect.property(item, "getMPaidContentInfo", "mPaidContentInfo") != null;
        }
    }

    /** Videos posted as part of a playlist ("Part 3 of ..."). */
    public static class PlaylistFilter implements IFilter {
        @Override
        public boolean getEnabled() {
            return Settings.HIDE_PLAYLIST_VIDEOS.get();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            return Reflect.property(item, "getMixInfo", "mixInfo") != null;
        }
    }

    /** Videos from verified accounts. */
    public static class VerifiedFilter implements IFilter {
        @Override
        public boolean getEnabled() {
            return Settings.HIDE_VERIFIED.get();
        }

        @Override
        public boolean getFiltered(Aweme item) {
            Object author = Reflect.property(item, "getAuthor", "author");
            if (author == null) {
                return false;
            }
            Object type = Reflect.property(author, "getVerificationType", "verificationType");
            if (type instanceof Number && ((Number) type).intValue() != 0) {
                return true;
            }
            return Reflect.string(author, "getCustomVerify", "customVerify") != null
                    || Reflect.string(author, "getEnterpriseVerifyReason", "enterpriseVerifyReason") != null;
        }
    }
}
