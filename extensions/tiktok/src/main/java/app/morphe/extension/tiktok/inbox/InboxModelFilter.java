package app.morphe.extension.tiktok.inbox;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.List;

/** Reads the serialized model fields verified in TikTok 46.2.3. No localized titles. */
public final class InboxModelFilter {
    private InboxModelFilter() {}

    public static List<?> filter(List<?> rows) {
        if (rows == null || rows.isEmpty()) return rows;
        if (!Settings.HIDE_INBOX_NEW_FOLLOWERS.get() && !Settings.HIDE_INBOX_ACTIVITY.get()
                && !Settings.HIDE_INBOX_TAKO.get() && !Settings.HIDE_INBOX_SHOP.get()) return rows;
        try {
            ArrayList<Object> kept = new ArrayList<>(rows.size());
            for (Object row : rows) {
                if (!hidden(row)) kept.add(row);
            }
            return kept.size() == rows.size() ? rows : kept;
        } catch (Throwable e) {
            Logger.printException(() -> "Could not filter inbox models", e);
            return rows;
        }
    }

    private static boolean hidden(Object row) {
        // ActivityPod.dataType is FOLLOWER, ACTIVITY or SHOP, including cached pods.
        Object dataType = Reflect.readField(row, "dataType");
        if (dataType instanceof Enum<?>) {
            switch (((Enum<?>) dataType).name()) {
                case "FOLLOWER": return Settings.HIDE_INBOX_NEW_FOLLOWERS.get();
                case "ACTIVITY": return Settings.HIDE_INBOX_ACTIVITY.get();
                case "SHOP": return Settings.HIDE_INBOX_SHOP.get();
                default: break;
            }
        }
        // InboxEntrancePod wraps InboxEntranceCell. The cell's named predicates use
        // stable server ids: Activity=1, Follower=2, Tako=9. Conversations aren't cells.
        Object cell = Reflect.readField(row, "entranceCell");
        return (Settings.HIDE_INBOX_NEW_FOLLOWERS.get() && Boolean.TRUE.equals(Reflect.invoke(cell, "isFollower")))
                || (Settings.HIDE_INBOX_ACTIVITY.get() && Boolean.TRUE.equals(Reflect.invoke(cell, "isActivity")))
                || (Settings.HIDE_INBOX_TAKO.get() && Boolean.TRUE.equals(Reflect.invoke(cell, "isTako")));
    }
}
