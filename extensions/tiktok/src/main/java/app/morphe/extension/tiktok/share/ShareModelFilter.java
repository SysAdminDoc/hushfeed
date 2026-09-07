package app.morphe.extension.tiktok.share;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.List;

/** Runs before the share panel builds its rows. Never mutates the builder's lists. */
public final class ShareModelFilter {
    private ShareModelFilter() {}

    public static List<?> channels(List<?> items) {
        return filter(items, Settings.HIDE_SHARE_CHANNELS.get());
    }

    public static List<?> actions(List<?> items) {
        ShareActionCatalog.observe(items);
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
                    if (!entry.trim().isEmpty()
                            && ShareActionCatalog.canonical(entry).equals(ShareActionCatalog.canonical(key))) {
                        hide = true;
                        break;
                    }
                }
            }
            if (!hide) kept.add(item);
        }
        return kept.size() == items.size() ? items : kept;
    }

}
