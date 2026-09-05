package app.morphe.extension.tiktok.share;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs before the share panel builds its rows. Never mutates the builder's lists. */
public final class ShareModelFilter {
    private ShareModelFilter() {}

    public static List<?> channels(List<?> items) {
        return filter(items, Settings.HIDE_SHARE_CHANNELS.get());
    }

    public static List<?> actions(List<?> items) {
        return filter(items, Settings.HIDE_SHARE_ACTIONS.get());
    }

    public static boolean contacts(boolean original) {
        return original && !Settings.HIDE_SHARE_CONTACTS.get();
    }

    private static List<?> filter(List<?> items, boolean hideRow) {
        if (items == null) return null;
        if (hideRow) return new ArrayList<>();
        String hidden = Settings.SHARE_HIDDEN_ITEMS.get();
        if (hidden.trim().isEmpty()) return items;
        List<Object> kept = new ArrayList<>(items.size());
        for (Object item : items) {
            String key = Reflect.string(item, "key", "key");
            boolean hide = false;
            if (key != null) {
                for (String entry : hidden.split("[,\\n]")) {
                    if (!entry.trim().isEmpty() && canonical(entry).equals(canonical(key))) {
                        hide = true;
                        break;
                    }
                }
            }
            if (!hide) kept.add(item);
        }
        return kept.size() == items.size() ? items : kept;
    }

    private static String canonical(String value) {
        String key = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        switch (key) {
            case "copy": case "copy link": return "copy";
            case "not interested": case "dislike": return "dislike";
            case "save video": case "download": case "save": return "save";
            case "create group": case "im create group": return "im create group";
            case "why this post": case "why this video": return "why this video";
            case "add to story": case "share to story": return "share to story";
            case "set as wallpaper": case "live photo": return "live photo";
            case "promote": case "promote for others fyp": return "promote for others fyp";
            default: return key;
        }
    }
}
