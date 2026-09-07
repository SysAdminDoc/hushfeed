/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */

package app.morphe.extension.tiktok.share;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps a small, persistent catalogue of share actions seen in the native model. */
public final class ShareActionCatalog {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_LABEL_LENGTH = 80;

    private ShareActionCatalog() {
    }

    public static final class Entry {
        public final String key;
        public final String label;

        Entry(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    static synchronized void observe(List<?> items) {
        if (items == null || items.isEmpty()) return;
        LinkedHashMap<String, String> catalog = read();
        boolean changed = false;
        for (Object item : items) {
            String rawKey = Reflect.firstNonBlank(
                    Reflect.string(item, "getKey", "key"),
                    Reflect.string(item, "key", "key")
            );
            if (rawKey == null) continue;
            String key = canonical(rawKey);
            if (key.isEmpty()) continue;
            String label = label(item, key);
            if (!catalog.containsKey(key)) {
                if (catalog.size() >= MAX_ENTRIES) continue;
                catalog.put(key, label);
                changed = true;
            } else if (!catalog.get(key).equals(label) && !label.equals(key)) {
                catalog.put(key, label);
                changed = true;
            }
        }
        if (changed) {
            Settings.SHARE_ACTION_CATALOG.save(write(catalog));
        }
    }

    public static synchronized List<Entry> entries() {
        List<Entry> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : read().entrySet()) {
            result.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public static String canonical(String value) {
        if (value == null) return "";
        String key = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        switch (key) {
            case "copy":
            case "copy link":
                return "copy";
            case "not interested":
            case "dislike":
                return "dislike";
            case "save video":
            case "download":
            case "save":
                return "save";
            case "create group":
            case "im create group":
                return "im create group";
            case "why this post":
            case "why this video":
                return "why this video";
            case "add to story":
            case "share to story":
                return "share to story";
            case "set as wallpaper":
            case "live photo":
                return "live photo";
            case "promote":
            case "promote for others fyp":
                return "promote for others fyp";
            default:
                return key;
        }
    }

    private static LinkedHashMap<String, String> read() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String stored = Settings.SHARE_ACTION_CATALOG.get();
        if (stored == null || stored.trim().isEmpty()) return result;
        for (String line : stored.split("\\n")) {
            int separator = line.indexOf('\t');
            if (separator <= 0) continue;
            String key = canonical(line.substring(0, separator));
            String label = cleanLabel(line.substring(separator + 1));
            if (!key.isEmpty() && !label.isEmpty() && result.size() < MAX_ENTRIES) {
                result.put(key, label);
            }
        }
        return result;
    }

    private static String write(LinkedHashMap<String, String> catalog) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : catalog.entrySet()) {
            if (result.length() > 0) result.append('\n');
            result.append(entry.getKey()).append('\t').append(cleanLabel(entry.getValue()));
        }
        return result.toString();
    }

    private static String label(Object item, String key) {
        String found = Reflect.firstNonBlank(
                Reflect.string(item, "getLabel", "label"),
                Reflect.string(item, "label", "label"),
                Reflect.string(item, "getTitle", "title"),
                Reflect.string(item, "title", "title"),
                Reflect.string(item, "getName", "name"),
                Reflect.string(item, "name", "name"),
                Reflect.string(item, "getText", "text"),
                Reflect.string(item, "text", "text")
        );
        return found == null ? humanize(key) : cleanLabel(found);
    }

    private static String humanize(String key) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (Character.isWhitespace(character)) {
                result.append(' ');
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
        }
        return cleanLabel(result.toString());
    }

    private static String cleanLabel(String value) {
        if (value == null) return "";
        String clean = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() > MAX_LABEL_LENGTH) {
            clean = clean.substring(0, MAX_LABEL_LENGTH).trim();
        }
        return clean;
    }
}
