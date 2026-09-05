package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InboxRowLanguageTest {
    private final BooleanSetting[] settings = {Settings.HIDE_INBOX_NEW_FOLLOWERS,
            Settings.HIDE_INBOX_ACTIVITY, Settings.HIDE_INBOX_ARCHIVE,
            Settings.HIDE_INBOX_TAKO, Settings.HIDE_INBOX_SHOP};
    private final Object[] models = {new Pod(Kind.FOLLOWER), new Pod(Kind.ACTIVITY),
            new Archive(), new Entrance(), new Pod(Kind.SHOP)};
    private Method predicate;

    @Before public void setup() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        for (BooleanSetting setting : settings) setting.save(false);
        Settings.HIDE_INBOX_CONVERSATIONS.save(false);
        Settings.HIDE_INBOX_MESSAGE_REQUESTS.save(false);
        Settings.HIDE_INBOX_CUSTOM_TITLES.save("");
        var ids = InboxFilter.class.getDeclaredField("RESOLVED_IDS");
        ids.setAccessible(true);
        @SuppressWarnings("unchecked") var values = (Map<String, Integer>) ids.get(null);
        values.clear();
        values.put("vid", 101);
        values.put("user_name", 102);
        values.put("tyh", 103);
        values.put("bo5", 104);
        predicate = InboxFilter.class.getDeclaredMethod("shouldHideRow", Activity.class, View.class);
        predicate.setAccessible(true);
    }

    @Test public void everyCategoryUsesModelIdentityAcrossLanguagesAndIndependentSwitches() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = owner.get();
            String[][] labels = {{"Neue Follower", "Aktivität", "Archiv", "TikTok Tako", "TikTok Shop"},
                    {"新しいフォロワー", "アクティビティ", "アーカイブ", "Tako", "ショップ"}};
            for (String[] language : labels) {
                for (int enabled = 0; enabled < settings.length; enabled++) {
                    for (int i = 0; i < settings.length; i++) settings[i].save(i == enabled);
                    for (int rowIndex = 0; rowIndex < models.length; rowIndex++) {
                        View row = row(activity, language[rowIndex], false);
                        InboxFilter.onRowBound(new Holder(row), rowIndex, models[rowIndex]);
                        assertEquals(rowIndex == enabled ? View.GONE : View.VISIBLE, row.getVisibility());
                        assertEquals(rowIndex == enabled, predicate.invoke(null, activity, row));
                    }
                }
            }
        }
    }

    @Test public void recycledRowsRestoreHeightAndNeverHideSameNamedConversations() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = owner.get();
            Settings.HIDE_INBOX_ARCHIVE.save(true);
            View row = row(activity, "Archiv", false);
            Holder holder = new Holder(row);
            InboxFilter.onRowBound(holder, 0, new Archive());
            assertEquals(0, row.getLayoutParams().height);
            InboxFilter.onRowBound(holder, 1, new Object());
            assertEquals(View.VISIBLE, row.getVisibility());
            assertEquals(72, row.getLayoutParams().height);
            assertEquals(false, predicate.invoke(null, activity, row));
            for (BooleanSetting setting : settings) setting.save(true);
            for (String name : new String[]{"Archiv", "Activity", "TikTok Shop", "新しいフォロワー"}) {
                View conversation = row(activity, name, true);
                InboxFilter.onRowBound(new Holder(conversation), 0, new Object());
                assertEquals(View.VISIBLE, conversation.getVisibility());
                assertEquals(false, predicate.invoke(null, activity, conversation));
            }
        }
    }

    @Test public void nextLayoutReadsTheCurrentSwitchWithoutRebinding() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = owner.get();
            View row = row(activity, "アクティビティ", false);
            InboxFilter.onRowBound(new Holder(row), 0, new Pod(Kind.ACTIVITY));
            assertEquals(false, predicate.invoke(null, activity, row));
            Settings.HIDE_INBOX_ACTIVITY.save(true);
            assertEquals(true, predicate.invoke(null, activity, row));
            Settings.HIDE_INBOX_ACTIVITY.save(false);
            assertEquals(false, predicate.invoke(null, activity, row));
        }
    }

    @Test public void archiveFilterPreservesInputAndUnknownModels() {
        Object archive = new Archive(), conversation = new Object();
        List<?> rows = List.of(archive, conversation);
        assertSame(rows, InboxModelFilter.filter(rows));
        Settings.HIDE_INBOX_ARCHIVE.save(true);
        assertEquals(List.of(conversation), InboxModelFilter.filter(rows));
        assertEquals(2, rows.size());
        assertNull(InboxModelFilter.filter(null));
    }

    private View row(Activity activity, String title, boolean conversation) {
        LinearLayout row = new LinearLayout(activity);
        row.setLayoutParams(new ViewGroup.LayoutParams(-1, 72));
        row.setId(conversation ? 105 : 103);
        TextView label = new TextView(activity);
        label.setId(conversation ? 102 : 104);
        label.setText(title);
        row.addView(label);
        if (conversation) {
            View marker = new View(activity);
            marker.setId(101);
            row.addView(marker);
        }
        return row;
    }

    private enum Kind { FOLLOWER, ACTIVITY, SHOP }
    private static final class Pod {
        public final Kind dataType;
        Pod(Kind kind) { dataType = kind; }
    }
    private static final class Archive { public String itemUniqueId() { return "archive_entrance"; } }
    private static final class Entrance { public final Cell entranceCell = new Cell(); }
    private static final class Cell { public boolean isTako() { return true; } }
    private static final class Holder {
        public final View itemView;
        Holder(View row) { itemView = row; }
    }
}
