/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import app.morphe.extension.shared.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings text in the phone's language.
 *
 * The English string in the code is the lookup key. The translations are generated into
 * {@link L10nTranslations} from extensions/tiktok/src/main/l10n by scripts/gen-l10n.py, so
 * they travel in the extension's own code rather than in TikTok's resources: merging a few
 * hundred strings into a resource table of 74,765 costs more than 250 MB of patching memory,
 * which is more than Morphe Manager allows by default.
 *
 * A language with no table, or a string nobody translated yet, falls back to the English
 * text, so nothing here can leave a label empty.
 */
public final class L10n {
    /** A set of language tags and the answer they came to, so neither can go stale alone. */
    private static final class Table {
        final String key;
        final Map<String, String> translations;

        Table(String key, Map<String, String> translations) {
            this.key = key;
            this.translations = translations;
        }
    }

    private static volatile Table cached;

    private L10n() {
    }

    public static String t(String english) {
        return t(Utils.getContext(), english);
    }

    /** Text that may be anything: only plain strings are looked up, styled text passes through. */
    public static CharSequence t(Context context, CharSequence text) {
        return text instanceof String ? t(context, (String) text) : text;
    }

    public static String t(Context context, String english) {
        if (english == null || english.isEmpty()) {
            return english;
        }
        Map<String, String> translations = tableFor(tags(context));
        if (translations == null) {
            return english;
        }
        String translated = translations.get(english);
        return translated == null || translated.isEmpty() ? english : translated;
    }

    /** {@link String#format} over the translated form of {@code english}. */
    public static String f(String english, Object... args) {
        return f(Utils.getContext(), english, args);
    }

    public static String f(Context context, String english, Object... args) {
        try {
            return String.format(Locale.getDefault(), t(context, english), args);
        } catch (Throwable ignored) {
            return String.format(Locale.ROOT, english, args);
        }
    }

    /**
     * Every tag worth trying, in the order the phone asks for them, each one most specific
     * first, so a table for "pt-rbr" wins over one for "pt". Android resolves a string
     * resource against the whole language list rather than the first entry alone, and reading
     * the table by hand has to do the same or a second choice language never shows.
     */
    static List<String> tags(Context context) {
        List<String> tags = new ArrayList<>(4);
        for (Locale locale : locales(context)) {
            String language = locale.getLanguage().toLowerCase(Locale.ROOT);
            if (language.isEmpty() || tags.contains(language)) {
                continue;
            }
            String country = locale.getCountry();
            if (country != null && !country.isEmpty()) {
                tags.add(language + "-r" + country.toLowerCase(Locale.ROOT));
            }
            tags.add(language);
        }
        if (tags.isEmpty()) {
            tags.add(Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT));
        }
        return tags;
    }

    /** The languages the phone is set to, or the default when there is no context to ask. */
    private static List<Locale> locales(Context context) {
        try {
            Resources resources = context == null ? null : context.getResources();
            if (resources != null) {
                Configuration configuration = resources.getConfiguration();
                if (Build.VERSION.SDK_INT >= 24) {
                    LocaleList list = configuration.getLocales();
                    List<Locale> found = new ArrayList<>(list.size());
                    for (int index = 0; index < list.size(); index++) {
                        found.add(list.get(index));
                    }
                    if (!found.isEmpty()) {
                        return found;
                    }
                } else if (configuration.locale != null) {
                    return Collections.singletonList(configuration.locale);
                }
            }
        } catch (Throwable ignored) {
            // No context yet, or none with resources: the default locale still answers.
        }
        return Collections.singletonList(Locale.getDefault());
    }

    /** The first tag with a table, remembered until the phone's languages change. */
    private static Map<String, String> tableFor(List<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            builder.append(tag).append(',');
        }
        String key = builder.toString();

        Table table = cached;
        if (table != null && key.equals(table.key)) {
            return table.translations;
        }

        Map<String, String> found = null;
        for (String tag : tags) {
            found = L10nTranslations.of(tag);
            if (found != null) {
                break;
            }
        }
        cached = new Table(key, found);
        return found;
    }
}
