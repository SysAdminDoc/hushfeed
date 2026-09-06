/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

/**
 * Keeps or drops a video by the country it was posted from, the same value the author row
 * shows when that switch is on. A video TikTok sends with no region is always kept: the two
 * lists say what to do with a country, not what to do when there isn't one.
 */
public final class RegionFilter implements IFilter {
    private final Set<String> only;
    private final Set<String> never;

    RegionFilter() {
        only = countries(Settings.REGION_ONLY_FROM.get());
        never = countries(Settings.REGION_NEVER_FROM.get());
    }

    /** The country codes in a comma or newline separated list, upper case. */
    static Set<String> countries(String value) {
        Set<String> codes = new LinkedHashSet<>();
        if (value == null) return codes;
        for (String entry : value.trim().split("\\s*[,\\n]\\s*")) {
            String code = entry.trim().toUpperCase(Locale.ROOT);
            if (!code.isEmpty()) codes.add(code);
        }
        return codes;
    }

    @Override
    public boolean getEnabled() {
        return !only.isEmpty() || !never.isEmpty();
    }

    @Override
    public boolean getFiltered(Aweme item) {
        String region = Reflect.string(item, "getRegion", "region");
        if (region == null) return false;

        String code = region.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) return false;
        if (never.contains(code)) return true;
        return !only.isEmpty() && !only.contains(code);
    }
}
