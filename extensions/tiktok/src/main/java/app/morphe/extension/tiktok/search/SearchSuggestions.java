/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.search;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

/** Whether the search page's suggested words are asked for and shown. */
@SuppressWarnings("unused")
public final class SearchSuggestions {
    private SearchSuggestions() {
    }

    public static boolean shouldHide() {
        return SettingsStatus.hideSearchSuggestionsEnabled && Settings.HIDE_SEARCH_SUGGESTIONS.get();
    }

    public static boolean filterCachedSuggestions(boolean nativeValue) {
        return nativeValue && !shouldHide();
    }

    public static Object filterLynxParameter(String key, Object nativeValue) {
        if (shouldHide()) {
            if ("show_suggest_search_words".equals(key)) return Integer.valueOf(0);
            if ("is_lynx_request_suggest".equals(key)) return Boolean.FALSE;
        }
        return nativeValue;
    }

    public static int filterReentryVisibility(int nativeValue) {
        return shouldHide() ? 0 : nativeValue;
    }
}
