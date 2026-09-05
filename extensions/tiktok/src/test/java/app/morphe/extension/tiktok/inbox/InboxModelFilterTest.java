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
    @Test public void defaultsPreserveTheOriginalList() {
        List<?> rows = List.of(new Pod(Kind.FOLLOWER), new Entrance(9));
        assertSame(rows, InboxModelFilter.filter(rows));
    }
    @Test public void hidesModelsWithoutTouchingConversationsOrInputData() {
        Object conversation = new Object();
        List<?> rows = List.of(new Pod(Kind.FOLLOWER), new Entrance(2), new Entrance(1),
                new Entrance(9), conversation);
        Settings.HIDE_INBOX_NEW_FOLLOWERS.save(true);
        Settings.HIDE_INBOX_ACTIVITY.save(true);
        Settings.HIDE_INBOX_TAKO.save(true);
        assertEquals(List.of(conversation), InboxModelFilter.filter(rows));
        assertEquals(5, rows.size());
        Settings.HIDE_INBOX_NEW_FOLLOWERS.save(false);
        assertEquals(3, InboxModelFilter.filter(rows).size());
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
