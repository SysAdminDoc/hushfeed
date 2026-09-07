package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import org.junit.Test;

public class LiveFilterTest {
    @Test public void directGettersAndLiveTypeAreEvidence() {
        GetterAweme item = new GetterAweme();
        item.liveId = 42;
        item.liveType = "room";

        LiveFilter filter = new LiveFilter();
        assertTrue(filter.getFiltered(item));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("liveId=42"));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("liveType=room"));
    }

    @Test public void roomFieldsRemainASecondBoundaryWhenDirectGettersAreEmpty() {
        FieldAweme item = new FieldAweme();
        item.newLiveRoomData = new Room(99, new Object());

        LiveFilter filter = new LiveFilter();
        assertTrue(filter.getFiltered(item));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("newLiveRoomData="));
        assertFalse(LiveFilter.getLiveEvidence(item).contains("liveId="));
    }

    @Test public void numericAndNestedRoomShapesRemainIndependentEvidenceSources() {
        FieldAweme item = new FieldAweme();
        LiveFilter filter = new LiveFilter();

        item.awemeType = 101;
        assertTrue(filter.getFiltered(item));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("awemeType=101"));

        item.awemeType = 0;
        item.room = new RoomWithId(7);
        assertTrue(filter.getFiltered(item));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("room="));

        item.room = new Room(7, null);
        item.roomFeedCell = new RoomCell(new Room(8, new Object()));
        assertTrue(filter.getFiltered(item));
        assertTrue(LiveFilter.getLiveEvidence(item).contains("roomFeedCellStruct="));

        item.room = new Room(9, null);
        item.roomFeedCell = null;
        item.newLiveRoomData = new Room(10, null);
        assertFalse(filter.getFiltered(item));
        assertEquals("none", LiveFilter.getLiveEvidence(item));
    }

    public static class GetterAweme extends Aweme {
        long liveId;
        boolean replay;
        String liveType;

        @Override public long getLiveId() { return liveId; }
        @Override public boolean isLiveReplay() { return replay; }
        @Override public String getLiveType() { return liveType; }
    }

    public static class FieldAweme extends Aweme {
        public Room newLiveRoomData;
        public int awemeType;
        public Object room;
        public RoomCell roomFeedCell;

        @Override public long getLiveId() { return 0; }
        @Override public boolean isLiveReplay() { return false; }
        @Override public String getLiveType() { return null; }
        public RoomCell getRoomFeedCellStruct() { return roomFeedCell; }
    }

    public static class Room {
        public final long id;
        public final Object owner;

        Room(long id, Object owner) {
            this.id = id;
            this.owner = owner;
        }
    }

    public static class RoomWithId {
        public final long roomId;

        RoomWithId(long roomId) {
            this.roomId = roomId;
        }
    }

    public static class RoomCell {
        private final Room room;

        RoomCell(Room room) {
            this.room = room;
        }

        public Room getNewLiveRoomData() { return room; }
    }
}
