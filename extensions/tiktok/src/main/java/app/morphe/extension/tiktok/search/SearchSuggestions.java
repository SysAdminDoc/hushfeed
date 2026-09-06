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
}
