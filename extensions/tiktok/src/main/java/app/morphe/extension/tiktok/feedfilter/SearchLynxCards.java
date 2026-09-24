/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.feedfilter;

import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.FeedFilterCounters;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * The server-drawn Lynx cards in the search results, TikTok's Short Drama block among them: a
 * header, an Explore button, category chips and a row of dramas, sitting in the results list as
 * a card of its own.
 *
 * <p>TikTok 47.0.3 streams the Top results in chunks, and a chunk that carries no card still
 * carries patches, from which the page's model builds a Lynx card and slots it into the list. So
 * the card never passes through the result list the other search filters read. Every one of them
 * is bound by the Lynx card cell, whose bind hands this the cell and its item. A card Hide mini
 * dramas takes has its row collapsed to nothing, and the row comes back on the bind that hands
 * the cell another card, or any bind once the switch is off.
 */
public final class SearchLynxCards {
    /** Route name for the diagnostic report. */
    static final String SOURCE = "SearchLynxCard";
    static final String DRAMA_REASON = "miniDramaCard";

    private static final Object LOCK = new Object();
    /** Rows this collapsed, with the width, height and visibility they had. */
    private static final WeakHashMap<View, int[]> COLLAPSED = new WeakHashMap<>();
    /** The card each row was last bound to, so a late re-collapse can tell a rebind happened. */
    private static final WeakHashMap<View, Object> BOUND = new WeakHashMap<>();
    private static volatile boolean warned;

    private SearchLynxCards() {
    }

    /** The Lynx card cell {@code cell} is binding {@code item}. Nothing may escape into TikTok's bind. */
    public static void onCardBound(Object cell, Object item) {
        try {
            judge(cell, patchOf(item));
        } catch (Throwable failure) {
            warnOnce(failure);
        }
    }

    /**
     * The results list's own Lynx holder is binding {@code patch} into its row: the one the Top
     * results bind a streamed card through, hooked as (holder, fragment, patch).
     */
    public static void onHolderBound(Object holder, Object fragment, Object patch) {
        try {
            judge(holder, patch);
        } catch (Throwable failure) {
            warnOnce(failure);
        }
    }

    private static void warnOnce(Throwable failure) {
        if (!warned) {
            warned = true;
            Logger.printException(() -> "Could not judge a search Lynx card", failure);
        }
    }

    /** Collapses the row a drama card is bound into, and gives any other row its size back. */
    private static void judge(Object holder, Object patch) {
        Object itemView = Reflect.readField(holder, "itemView");
        if (!(itemView instanceof View)) return;
        View row = (View) itemView;
        synchronized (LOCK) {
            BOUND.put(row, patch);
        }
        String kind = kind(patch);
        FeedFilterCounters.sawList(SOURCE, 1);
        FeedFilterCounters.sawKind(SOURCE, kind);
        if (BaseSettings.DEBUG.get()) {
            String shape = schemaShape(Reflect.readField(patch, "schema"));
            Logger.printDebug(() -> "[Morphe TikTok FeedFilter] search lynx card: " + kind + " | " + shape);
        }
        if (Settings.HIDE_MINI_DRAMAS.get() && isDrama(patch)) {
            collapse(row);
            FeedFilterCounters.removed(SOURCE, 1, DRAMA_REASON);
            // TikTok's own bind runs after this and may size the row again, so once it has
            // returned the row is collapsed again, if it still holds this card.
            row.post(() -> {
                Object current;
                synchronized (LOCK) {
                    current = BOUND.get(row);
                }
                if (current == patch && Settings.HIDE_MINI_DRAMAS.get()) collapse(row);
            });
        } else {
            restore(row);
        }
    }

    /** A drama module: the Lynx template or the server's source type for the card names dramas. */
    static boolean isDrama(Object patch) {
        if (patch == null) return false;
        return namesDrama(lynxTemplate(Reflect.readField(patch, "schema")))
                || namesDrama(FeedItemsFilter.searchCardSource(Reflect.readField(patch, "alaSrc")));
    }

    private static boolean namesDrama(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("drama");
    }

    /** The Lynx data an item carries: the one field whose type is TikTok's DynamicPatch. */
    static Object patchOf(Object item) {
        if (item == null) return null;
        for (Class<?> type = item.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!"DynamicPatch".equals(field.getType().getSimpleName())) continue;
                try {
                    field.setAccessible(true);
                    return field.get(item);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** What a Lynx card is, by shape alone: its source type and its template. Never what it says. */
    static String kind(Object patch) {
        if (patch == null) return "lynx card without data";
        StringBuilder kind = new StringBuilder("lynx card");
        String source = FeedItemsFilter.searchCardSource(Reflect.readField(patch, "alaSrc"));
        if (source != null) kind.append(' ').append(source);
        String template = lynxTemplate(Reflect.readField(patch, "schema"));
        if (template != null) kind.append(" template ").append(template);
        return kind.toString();
    }

    /**
     * The template a Lynx schema names: its channel, or else the folder its bundle sits in. Nothing
     * else from the schema, whose other parameters can carry the search itself.
     */
    static String lynxTemplate(Object schema) {
        if (!(schema instanceof String)) return null;
        String value = schemaParameter((String) schema, "channel");
        if (value == null) {
            String url = schemaParameter((String) schema, "surl");
            if (url == null) url = schemaParameter((String) schema, "url");
            if (url != null) {
                int cut = url.indexOf('?');
                String path = cut >= 0 ? url.substring(0, cut) : url;
                String[] parts = path.split("/");
                value = parts.length >= 2 ? parts[parts.length - 2] : null;
            }
        }
        if (value == null) return null;
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (out.length() == 60) break;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') out.append(c);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** For the debug log only: a schema's scheme, host and path, its parameter names, and its template parameters. */
    static String schemaShape(Object schema) {
        if (!(schema instanceof String)) return "no schema";
        String text = (String) schema;
        int query = text.indexOf('?');
        StringBuilder out = new StringBuilder(query >= 0 ? text.substring(0, query) : text);
        if (query >= 0) {
            out.append(" params");
            for (String pair : text.substring(query + 1).split("&")) {
                int eq = pair.indexOf('=');
                out.append(' ').append(eq >= 0 ? pair.substring(0, eq) : pair);
            }
            for (String name : new String[] {"channel", "bundle", "surl", "url"}) {
                String value = schemaParameter(text, name);
                if (value != null) {
                    int cut = value.indexOf('?');
                    out.append(" | ").append(name).append('=').append(cut >= 0 ? value.substring(0, cut) : value);
                }
            }
        }
        return out.toString();
    }

    /** A schema parameter's decoded value, or null. */
    static String schemaParameter(String schema, String name) {
        int query = schema.indexOf('?');
        if (query < 0) return null;
        for (String pair : schema.substring(query + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || !pair.substring(0, eq).equals(name)) continue;
            try {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            } catch (Exception badEncoding) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    /** Collapses the row to no height and hides it. The size it had is kept the first time only. */
    private static void collapse(View row) {
        ViewGroup.LayoutParams params = row.getLayoutParams();
        synchronized (LOCK) {
            if (!COLLAPSED.containsKey(row)) {
                COLLAPSED.put(row, new int[] {
                        params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height, row.getVisibility()});
            }
        }
        if (params != null) {
            params.height = 0;
            row.setLayoutParams(params);
        }
        row.setVisibility(View.GONE);
    }

    private static void restore(View row) {
        int[] saved;
        synchronized (LOCK) {
            saved = COLLAPSED.remove(row);
        }
        if (saved == null) return;
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params != null) {
            params.height = saved[0];
            row.setLayoutParams(params);
        }
        row.setVisibility(saved[1]);
    }

    static void resetForTests() {
        synchronized (LOCK) {
            COLLAPSED.clear();
            BOUND.clear();
        }
        warned = false;
    }
}
