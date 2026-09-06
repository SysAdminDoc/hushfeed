/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings;

import android.content.Context;
import android.content.res.Resources;

import app.morphe.extension.shared.Utils;

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
    /** A language and its table together, so a reader can never pair one with the other's. */
    private static final class Table {
        final String language;
        final Map<String, String> translations;

        Table(String language, Map<String, String> translations) {
            this.language = language;
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
        Map<String, String> translations = tableFor(language(context));
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
     * The phone's language as a tag the tables are named by, most specific first: a table
     * for "pt-rbr" wins over one for "pt".
     */
    static String language(Context context) {
        Locale locale = null;
        try {
            Resources resources = context == null ? null : context.getResources();
            if (resources != null) {
                locale = resources.getConfiguration().locale;
            }
        } catch (Throwable ignored) {
            // No context yet, or none with resources: the default locale still answers.
        }
        if (locale == null) {
            locale = Locale.getDefault();
        }

        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry();
        return country == null || country.isEmpty()
                ? language
                : language + "-r" + country.toLowerCase(Locale.ROOT);
    }

    /** The table for a language tag, remembered until the language changes. */
    private static Map<String, String> tableFor(String language) {
        Table table = cached;
        if (table != null && language.equals(table.language)) {
            return table.translations;
        }

        Map<String, String> found = L10nTranslations.of(language);
        if (found == null) {
            int dash = language.indexOf('-');
            if (dash > 0) {
                found = L10nTranslations.of(language.substring(0, dash));
            }
        }
        cached = new Table(language, found);
        return found;
    }
}
