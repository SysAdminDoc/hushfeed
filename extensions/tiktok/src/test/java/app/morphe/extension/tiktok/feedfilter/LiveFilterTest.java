package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertFalse;
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

        @Override public long getLiveId() { return 0; }
        @Override public boolean isLiveReplay() { return false; }
        @Override public String getLiveType() { return null; }
    }

    public static class Room {
        public final long id;
        public final Object owner;

        Room(long id, Object owner) {
            this.id = id;
            this.owner = owner;
        }
    }
}
