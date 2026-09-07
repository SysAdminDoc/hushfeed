package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The Inbox reads one shape after another on rows of different types: itemUniqueId belongs to the
 * archive pod, dataType to the activity pod, entranceCell to the entrance pod. Every row misses
 * most of them by design, which is why these reads stay on the ordinary readers rather than the
 * one that reports a member this build does not have.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class InboxRowShapesTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        HookStatus.resetForTests();
    }

    @SuppressWarnings("unused")
    public static final class ArchivePod {
        public String itemUniqueId() { return "archive_entrance"; }
    }

    @SuppressWarnings("unused")
    public static final class ActivityPod {
        public final Kind dataType;
        ActivityPod(Kind dataType) { this.dataType = dataType; }
        public enum Kind { FOLLOWER, ACTIVITY, SHOP }
    }

    @SuppressWarnings("unused")
    public static final class EntrancePod {
        public final Cell entranceCell;
        EntrancePod(boolean follower, boolean activity, boolean tako) {
            this.entranceCell = new Cell(follower, activity, tako);
        }
        public static final class Cell {
            private final boolean follower, activity, tako;
            Cell(boolean follower, boolean activity, boolean tako) {
                this.follower = follower; this.activity = activity; this.tako = tako;
            }
            public Boolean isFollower() { return follower; }
            public Boolean isActivity() { return activity; }
            public Boolean isTako() { return tako; }
        }
    }

    /** A conversation row carries none of the shapes the others do. */
    public static final class Conversation {
    }

    @Test public void eachRowShapeChoosesItsOwnSetting() {
        assertSame(Settings.HIDE_INBOX_ARCHIVE, InboxModelFilter.settingFor(new ArchivePod()));
        assertSame(Settings.HIDE_INBOX_NEW_FOLLOWERS,
                InboxModelFilter.settingFor(new ActivityPod(ActivityPod.Kind.FOLLOWER)));
        assertSame(Settings.HIDE_INBOX_ACTIVITY,
                InboxModelFilter.settingFor(new ActivityPod(ActivityPod.Kind.ACTIVITY)));
        assertSame(Settings.HIDE_INBOX_SHOP,
                InboxModelFilter.settingFor(new ActivityPod(ActivityPod.Kind.SHOP)));
        assertSame(Settings.HIDE_INBOX_NEW_FOLLOWERS,
                InboxModelFilter.settingFor(new EntrancePod(true, false, false)));
        assertSame(Settings.HIDE_INBOX_TAKO,
                InboxModelFilter.settingFor(new EntrancePod(false, false, true)));
        assertNull("a conversation row belongs to no switch here",
                InboxModelFilter.settingFor(new Conversation()));
    }

    @Test public void probingOneShapeAfterAnotherReportsNothing() {
        InboxModelFilter.settingFor(new ArchivePod());
        InboxModelFilter.settingFor(new ActivityPod(ActivityPod.Kind.FOLLOWER));
        InboxModelFilter.settingFor(new EntrancePod(false, true, false));
        InboxModelFilter.settingFor(new Conversation());

        assertEquals("a shape a row was never going to have was reported: "
                + Reflect.missingMembers(), 0, Reflect.missingMembers().size());
    }
}
