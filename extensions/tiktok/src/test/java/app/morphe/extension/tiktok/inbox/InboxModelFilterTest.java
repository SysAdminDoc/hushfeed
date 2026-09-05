package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InboxModelFilterTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.HIDE_INBOX_NEW_FOLLOWERS.save(false);
        Settings.HIDE_INBOX_ACTIVITY.save(false);
        Settings.HIDE_INBOX_TAKO.save(false);
        Settings.HIDE_INBOX_SHOP.save(false);
    }
    @Test public void defaultsKeepRecognizedCategoriesVisible() {
        List<?> rows = List.of(new Pod(Kind.FOLLOWER), new Entrance(9));
        for (Object row : rows) assertFalse(InboxModelFilter.settingFor(row).get());
        assertNull(InboxModelFilter.settingFor(null));
    }
    @Test public void categoryIdentityTracksSwitchesWithoutTouchingConversationsOrInputData() {
        Object conversation = new Object();
        List<?> rows = List.of(new Pod(Kind.FOLLOWER), new Entrance(2), new Entrance(1),
                new Entrance(9), conversation);
        Settings.HIDE_INBOX_NEW_FOLLOWERS.save(true);
        Settings.HIDE_INBOX_ACTIVITY.save(true);
        Settings.HIDE_INBOX_TAKO.save(true);
        assertEquals(List.of(true, true, true, true, false), hidden(rows));
        assertEquals(5, rows.size());
        Settings.HIDE_INBOX_NEW_FOLLOWERS.save(false);
        assertEquals(List.of(false, false, true, true, false), hidden(rows));
        assertNull(InboxModelFilter.settingFor(conversation));
    }
    private List<Boolean> hidden(List<?> rows) {
        return rows.stream().map(row -> {
            var setting = InboxModelFilter.settingFor(row);
            return setting != null && setting.get();
        }).collect(java.util.stream.Collectors.toList());
    }
    private enum Kind { FOLLOWER, ACTIVITY, SHOP }
    private static final class Pod {
        public final Kind dataType;
        Pod(Kind kind) { dataType = kind; }
    }
    private static final class Entrance {
        public final Cell entranceCell;
        Entrance(int id) { entranceCell = new Cell(id); }
    }
    private static final class Cell {
        private final int id;
        Cell(int id) { this.id = id; }
        public boolean isFollower() { return id == 2; }
        public boolean isActivity() { return id == 1; }
        public boolean isTako() { return id == 9; }
    }
}
