package app.morphe.extension.tiktok.inbox;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.List;

/** Reads category identity from the verified model fields, independently of visible titles. */
public final class InboxModelFilter {
    private InboxModelFilter() {}

    public static List<?> filter(List<?> rows) {
        if (rows == null || rows.isEmpty()) return rows;
        if (!Settings.HIDE_INBOX_NEW_FOLLOWERS.get() && !Settings.HIDE_INBOX_ACTIVITY.get()
                && !Settings.HIDE_INBOX_TAKO.get() && !Settings.HIDE_INBOX_SHOP.get()
                && !Settings.HIDE_INBOX_ARCHIVE.get()) return rows;
        try {
            ArrayList<Object> kept = new ArrayList<>(rows.size());
            for (Object row : rows) {
                BooleanSetting setting = settingFor(row);
                if (setting == null || !setting.get()) kept.add(row);
            }
            return kept.size() == rows.size() ? rows : kept;
        } catch (Throwable e) {
            Logger.printException(() -> "Could not filter inbox models", e);
            return rows;
        }
    }

    static BooleanSetting settingFor(Object row) {
        if ("archive_entrance".equals(Reflect.invoke(row, "itemUniqueId"))) return Settings.HIDE_INBOX_ARCHIVE;
        // ActivityPod.dataType is FOLLOWER, ACTIVITY or SHOP, including cached pods.
        Object dataType = Reflect.readField(row, "dataType");
        if (dataType instanceof Enum<?>) {
            switch (((Enum<?>) dataType).name()) {
                case "FOLLOWER": return Settings.HIDE_INBOX_NEW_FOLLOWERS;
                case "ACTIVITY": return Settings.HIDE_INBOX_ACTIVITY;
                case "SHOP": return Settings.HIDE_INBOX_SHOP;
                default: break;
            }
        }
        // InboxEntrancePod wraps InboxEntranceCell. The cell's named predicates use
        // stable server ids: Activity=1, Follower=2, Tako=9. Conversations aren't cells.
        Object cell = Reflect.readField(row, "entranceCell");
        if (Boolean.TRUE.equals(Reflect.invoke(cell, "isFollower"))) return Settings.HIDE_INBOX_NEW_FOLLOWERS;
        if (Boolean.TRUE.equals(Reflect.invoke(cell, "isActivity"))) return Settings.HIDE_INBOX_ACTIVITY;
        if (Boolean.TRUE.equals(Reflect.invoke(cell, "isTako"))) return Settings.HIDE_INBOX_TAKO;
        return null;
    }
}
