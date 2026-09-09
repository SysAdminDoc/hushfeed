package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
public class InboxLayoutLifecycleTest {
    private static final String[] RESOURCE_NAMES = {"o1l", "kmx", "pgu", "t4g", "fnc",
            "vpj", "vid", "user_name", "tyh", "bo5", "v15", "f8t", "k_f", "kmz"};
    private final List<Inbox> inboxes = new ArrayList<>();
    private BooleanSetting[] switches;

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        switches = new BooleanSetting[]{Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS,
                Settings.HIDE_INBOX_STORIES, Settings.HIDE_INBOX_CONVERSATIONS,
                Settings.HIDE_INBOX_MESSAGE_REQUESTS, Settings.HIDE_INBOX_NEW_FOLLOWERS,
                Settings.HIDE_INBOX_ACTIVITY, Settings.HIDE_INBOX_ARCHIVE, Settings.HIDE_INBOX_TAKO,
                Settings.HIDE_INBOX_SHOP, Settings.HIDE_INBOX_ADD_PEOPLE, Settings.HIDE_INBOX_SEARCH,
                Settings.HIDE_INBOX_ACTIVITY_STATUS};
        for (BooleanSetting setting : switches) setting.save(false);
        Settings.HIDE_INBOX_CUSTOM_TITLES.save("");
        String packageName = RuntimeEnvironment.getApplication().getPackageName();
        for (String name : RESOURCE_NAMES) InboxFilter.resolveForTests(packageName, name, id(name));
    }

    @After public void tearDown() {
        for (int i = inboxes.size() - 1; i >= 0; i--) {
            Inbox inbox = inboxes.get(i);
            inbox.activity.finish();
            inbox.layout();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
            inbox.owner.close();
        }
        for (BooleanSetting setting : switches) setting.save(false);
        Settings.HIDE_INBOX_CUSTOM_TITLES.save("");
    }

    @Test public void movedHeaderKeepsItsControlAndReplacementGetsItsOwnUsableControl() {
        Inbox inbox = openInbox();
        LinearLayout first = new LinearLayout(inbox.activity);
        first.setOrientation(LinearLayout.HORIZONTAL);
        TextView firstTitle = addHeading(inbox, first, Color.WHITE);
        inbox.rows.addView(first, new LinearLayout.LayoutParams(-1, 64));
        inbox.layout();
        TextView firstClear = clearControl(first);
        assertSame(first, firstClear.getParent());
        assertSame(first, firstTitle.getParent().getParent());

        FrameLayout wrapper = new FrameLayout(inbox.activity);
        inbox.rows.removeView(first);
        wrapper.addView(first, new FrameLayout.LayoutParams(-1, 64));
        inbox.rows.addView(wrapper, new LinearLayout.LayoutParams(-1, 64));
        inbox.layout();
        assertSame("moving a header duplicated or replaced its action", firstClear, clearControl(first));
        assertEquals(2, first.getChildCount());

        inbox.rows.removeView(wrapper);
        FrameLayout replacement = new FrameLayout(inbox.activity);
        TextView replacementTitle = addHeading(inbox, replacement, Color.BLACK);
        int[] hostClicks = {0};
        replacementTitle.setOnClickListener(view -> hostClicks[0]++);
        inbox.rows.addView(replacement, new LinearLayout.LayoutParams(-1, 64));
        int[] dismissed = {0};
        LinearLayout account = new LinearLayout(inbox.activity);
        View remove = new View(inbox.activity);
        remove.setId(id("fnc"));
        remove.setContentDescription("Remove current account from suggested accounts");
        remove.setOnClickListener(view -> { dismissed[0]++; inbox.rows.removeView(account); });
        account.addView(remove, new LinearLayout.LayoutParams(48, 48));
        inbox.rows.addView(account, new LinearLayout.LayoutParams(-1, 72));
        inbox.layout();

        TextView replacementClear = clearControl(replacement);
        assertNotSame(firstClear, replacementClear);
        assertSame("a replacement must not steal the detached header's action", first, firstClear.getParent());
        assertSame(replacement, replacementClear.getParent());
        assertEquals(Color.BLACK, replacementClear.getCurrentTextColor());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, replacementClear.getLayoutParams().width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, replacementClear.getLayoutParams().height);
        assertTrue(replacementClear.isShown());
        assertTrue(replacementTitle.performClick());
        assertEquals("the host title's own click remains installed", 1, hostClicks[0]);
        assertTrue(replacementClear.performClick());
        assertEquals("the replacement action did not reach the current row", 1, dismissed[0]);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
    }

    @Test public void liveLayoutsFollowRecycledRowShapesAndWaitUntilInboxIsSelected() {
        Inbox inbox = openInbox();
        LinearLayout row = new LinearLayout(inbox.activity);
        inbox.rows.addView(row, new LinearLayout.LayoutParams(-1, 72));
        Settings.HIDE_INBOX_ARCHIVE.save(true);
        reshape(inbox, row, "tyh", "bo5", "Archiv");
        InboxFilter.onRowBound(new Holder(row), 0, new Archive());
        inbox.layout();
        assertRow(row, true);

        inbox.tab.setSelected(false);
        Settings.HIDE_INBOX_ARCHIVE.save(false);
        Settings.HIDE_INBOX_ADD_PEOPLE.save(true);
        Settings.HIDE_INBOX_SEARCH.save(true);
        Settings.HIDE_INBOX_ACTIVITY_STATUS.save(true);
        inbox.layout();
        assertRow(row, true);
        assertEquals(View.VISIBLE, inbox.addPeople.getVisibility());
        assertEquals(View.VISIBLE, inbox.search.getVisibility());
        assertEquals(View.VISIBLE, inbox.status.getVisibility());

        inbox.tab.setSelected(true);
        inbox.layout();
        assertRow(row, false);
        assertEquals(View.GONE, inbox.addPeople.getVisibility());
        assertEquals(View.GONE, inbox.search.getVisibility());
        assertEquals(View.GONE, inbox.status.getVisibility());
        Settings.HIDE_INBOX_ADD_PEOPLE.save(false);
        Settings.HIDE_INBOX_SEARCH.save(false);
        Settings.HIDE_INBOX_ACTIVITY_STATUS.save(false);

        Settings.HIDE_INBOX_ARCHIVE.save(true);
        Settings.HIDE_INBOX_CONVERSATIONS.save(true);
        reshape(inbox, row, "v15", "user_name", "Archiv");
        InboxFilter.onRowBound(new Holder(row), 1, new Object());
        inbox.layout();
        assertRow(row, false);
        assertEquals(View.VISIBLE, inbox.search.getVisibility());
        LinearLayout conversationMarker = new LinearLayout(inbox.activity);
        conversationMarker.setId(id("vid"));
        View conversationTitle = row.getChildAt(0);
        row.removeView(conversationTitle);
        conversationMarker.addView(conversationTitle);
        row.addView(conversationMarker);
        inbox.layout();
        assertRow(row, true);
        conversationMarker.removeView(conversationTitle);
        row.removeView(conversationMarker);
        row.addView(conversationTitle);
        inbox.layout();
        assertRow(row, false);

        Settings.HIDE_INBOX_STORIES.save(true);
        reshape(inbox, row, null, "vpj", "Story account");
        InboxFilter.onRowBound(new Holder(row), 2, new Object());
        inbox.layout();
        assertRow(row, true);
        Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.save(false);
        reshape(inbox, row, null, "fnc", "Suggested account");
        InboxFilter.onRowBound(new Holder(row), 3, new Object());
        inbox.layout();
        assertRow(row, false);
        Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.save(true);
        inbox.layout();
        assertRow(row, true);
        reshape(inbox, row, null, null, "Unknown row");
        InboxFilter.onRowBound(new Holder(row), 4, new Object());
        inbox.layout();
        assertRow(row, false);
    }

    @Test public void installingNewActivityDetachesOldLayoutsAndFinishingStopsFiltering() {
        Inbox old = openInbox();
        ViewTreeObserver oldObserver = old.activity.findViewById(android.R.id.content).getViewTreeObserver();
        Inbox current = openInbox();
        Settings.HIDE_INBOX_SEARCH.save(true);
        oldObserver.dispatchOnGlobalLayout();
        assertEquals("the old root still invokes the new activity's filter", View.VISIBLE, current.search.getVisibility());
        assertEquals(View.VISIBLE, old.search.getVisibility());
        current.layout();
        assertEquals(View.GONE, current.search.getVisibility());

        current.activity.finish();
        current.layout();
        Settings.HIDE_INBOX_SEARCH.save(false);
        current.layout();
        assertEquals("a finishing activity kept filtering its hierarchy", View.GONE, current.search.getVisibility());
    }

    private Inbox openInbox() {
        Inbox inbox = new Inbox();
        inboxes.add(inbox);
        InboxFilter.install(inbox.activity);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        inbox.layout();
        return inbox;
    }

    private static TextView addHeading(Inbox inbox, ViewGroup header, int color) {
        header.setId(id("pgu"));
        LinearLayout titleWrapper = new LinearLayout(inbox.activity);
        TextView title = new TextView(inbox.activity);
        title.setId(id("t4g"));
        title.setText("Suggested accounts");
        title.setTextColor(color);
        titleWrapper.addView(title);
        header.addView(titleWrapper);
        return title;
    }

    private static TextView clearControl(ViewGroup header) {
        TextView control = null;
        for (int i = 0; i < header.getChildCount(); i++) {
            View child = header.getChildAt(i);
            if ("Clear all suggested accounts".contentEquals(String.valueOf(child.getContentDescription()))) {
                assertNull("one header received two clear controls", control);
                control = (TextView) child;
            }
        }
        assertNotNull("the installed layout callback did not populate the current header", control);
        return control;
    }

    private static void reshape(Inbox inbox, LinearLayout row, String rowId, String childId, String text) {
        row.setId(rowId == null ? View.NO_ID : id(rowId));
        row.removeAllViews();
        TextView title = new TextView(inbox.activity);
        title.setId(childId == null ? View.NO_ID : id(childId));
        title.setText(text);
        row.addView(title);
    }

    private static void assertRow(View row, boolean hidden) {
        assertEquals(hidden ? View.GONE : View.VISIBLE, row.getVisibility());
        assertEquals(hidden ? 0 : 72, row.getLayoutParams().height);
    }

    private static int id(String name) {
        for (int i = 0; i < RESOURCE_NAMES.length; i++) if (RESOURCE_NAMES[i].equals(name)) return 7000 + i;
        throw new AssertionError("unknown fixture resource " + name);
    }

    private static final class Holder {
        public final View itemView;
        Holder(View itemView) { this.itemView = itemView; }
    }

    private static final class Archive {
        public String itemUniqueId() { return "archive_entrance"; }
    }

    private static final class Inbox {
        final ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup().visible();
        final Activity activity = owner.get();
        final LinearLayout rows = new LinearLayout(activity);
        final View tab = new View(activity), addPeople = new View(activity), search = new View(activity), status = new View(activity);

        Inbox() {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            tab.setId(id("o1l"));
            tab.setSelected(true);
            addPeople.setId(id("f8t"));
            search.setId(id("k_f"));
            status.setId(id("kmz"));
            for (View view : new View[]{tab, addPeople, search, status}) root.addView(view, new LinearLayout.LayoutParams(-1, 48));
            rows.setId(id("kmx"));
            rows.setOrientation(LinearLayout.VERTICAL);
            root.addView(rows, new LinearLayout.LayoutParams(-1, -1));
            activity.setContentView(root);
        }

        void layout() { activity.findViewById(android.R.id.content).getViewTreeObserver().dispatchOnGlobalLayout(); }
    }
}
