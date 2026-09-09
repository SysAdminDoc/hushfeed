package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
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
import org.robolectric.shadows.ShadowToast;

/** Enters through the control installed on the same header shape as TikTok 46.2.3. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
public class InboxClearControlTest {
    private static final String[] RESOURCE_NAMES = {"o1l", "kmx", "pgu", "t4g", "fnc",
            "vpj", "vid", "user_name", "tyh", "bo5", "v15", "f8t", "k_f", "kmz"};
    private ActivityController<Activity> owner;
    private Activity activity;
    private LinearLayout rows;
    private LinearLayout header;
    private final List<String> dismissed = new ArrayList<>();

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.save(false);
        Settings.HIDE_INBOX_STORIES.save(false);
        Settings.HIDE_INBOX_CUSTOM_TITLES.save("");
        String packageName = RuntimeEnvironment.getApplication().getPackageName();
        for (String name : RESOURCE_NAMES) {
            InboxFilter.resolveForTests(packageName, name, id(name));
        }
        openInbox();
        ShadowToast.reset();
    }

    @After public void tearDown() {
        if (owner != null) {
            activity.finish();
            layout();
            advance(300);
            owner.close();
        }
    }

    @Test public void repeatedTapsKeepOnePacedRunAndReportOnce() {
        addAccount("A", true);
        addAccount("B", true);
        addAccount("C", true);
        TextView clear = clearControl();
        assertTrue(clear.performClick());
        assertTrue(clear.performClick());
        assertEquals("a second tap started another dismissal chain", List.of("A"), dismissed);
        assertEquals(0, ShadowToast.shownToastCount());

        advance(299);
        assertEquals(List.of("A"), dismissed);
        advance(1);
        assertEquals(List.of("A", "B"), dismissed);
        advance(300);
        assertEquals(List.of("A", "B", "C"), dismissed);
        advance(300);
        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("Dismissed 3 suggested accounts", ShadowToast.getTextOfLatestToast());

        addAccount("D", true);
        clear.performClick();
        assertEquals("completion did not release the next run", List.of("A", "B", "C", "D"), dismissed);
        advance(300);
        assertEquals(2, ShadowToast.shownToastCount());
        assertEquals("Dismissed one suggested account", ShadowToast.getTextOfLatestToast());
    }

    @Test public void oneButtonCanBeRecycledButAnUnchangedAccountIsNotClickedTwice() {
        View button = addAccount("A", false);
        button.setOnClickListener(view -> {
            String label = view.getContentDescription().toString();
            dismissed.add(label);
            if (label.equals(description("A"))) view.setContentDescription(description("B"));
        });
        clearControl().performClick();
        assertEquals(List.of(description("A")), dismissed);
        advance(300);
        assertEquals(List.of(description("A"), description("B")), dismissed);
        advance(300);
        assertEquals("the unchanged B row was dismissed again", 2, dismissed.size());
        assertEquals("Dismissed 2 suggested accounts", ShadowToast.getTextOfLatestToast());
    }

    @Test public void theRunStopsAtSixtyEvenWhenTheHostKeepsRefillingTheSameRow() {
        View button = addAccount("0", false);
        button.setOnClickListener(view -> {
            dismissed.add(view.getContentDescription().toString());
            view.setContentDescription(description(Integer.toString(dismissed.size())));
        });
        clearControl().performClick();
        advance(60 * 300);
        assertEquals(60, dismissed.size());
        assertEquals("Dismissed 60 suggested accounts", ShadowToast.getTextOfLatestToast());
        advance(3000);
        assertEquals(60, dismissed.size());

        clearControl().performClick();
        assertEquals("the cap left the next run locked", 61, dismissed.size());
    }

    @Test public void finishingStopsTheQueuedStepAndReleasesTheNextActivity() {
        addAccount("old A", true);
        addAccount("old B", true);
        clearControl().performClick();
        activity.finish();
        layout();
        advance(300);
        assertEquals(List.of("old A"), dismissed);
        owner.close();

        openInbox();
        addAccount("new A", true);
        clearControl().performClick();
        assertEquals("the old activity kept the next inbox locked", List.of("old A", "new A"), dismissed);
        advance(300);
    }

    @Test public void aThrowingNativeClickReleasesTheRunForRetry() {
        View button = addAccount("A", false);
        int[] attempts = {0};
        button.setOnClickListener(view -> {
            attempts[0]++;
            throw new IllegalStateException("fixture dismiss failure");
        });
        TextView clear = clearControl();
        clear.performClick();
        assertEquals("the fixture never reached the native click", 1, attempts[0]);
        button.setOnClickListener(view -> dismissed.add("A"));
        clear.performClick();
        assertEquals("the failed native click left the run locked", List.of("A"), dismissed);
        advance(300);
    }

    @Test public void repeatedLayoutsKeepOneUsableControlAndAnEmptyRunCanBeRetried() {
        TextView clear = clearControl();
        InboxFilter.install(activity);
        advance(0);
        layout();
        layout();
        assertEquals(2, header.getChildCount());
        assertSame(clear, clearControl());
        assertEquals(Color.WHITE, clear.getCurrentTextColor());
        assertTrue(clear.isClickable());
        assertTrue(clear.isShown());
        assertTrue(clear.getMinimumHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) clear.getLayoutParams();
        assertEquals(0, params.width);
        assertEquals(1f, params.weight, 0f);

        clear.performClick();
        advance(0);
        assertEquals("No suggested accounts to clear", ShadowToast.getTextOfLatestToast());
        addAccount("A", true);
        clear.performClick();
        assertEquals("an empty run kept the control locked", List.of("A"), dismissed);
        advance(300);
    }

    private void openInbox() {
        owner = Robolectric.buildActivity(Activity.class).setup().visible();
        activity = owner.get();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        View tab = new View(activity);
        tab.setId(id("o1l"));
        tab.setSelected(true);
        root.addView(tab, new LinearLayout.LayoutParams(-1, 48));
        rows = new LinearLayout(activity);
        rows.setId(id("kmx"));
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows, new LinearLayout.LayoutParams(-1, -1));
        header = new LinearLayout(activity);
        header.setId(id("pgu"));
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(activity);
        title.setId(id("t4g"));
        title.setText("Suggested accounts");
        title.setTextColor(Color.WHITE);
        header.addView(title);
        rows.addView(header, new LinearLayout.LayoutParams(-1, 48));
        activity.setContentView(root);
        InboxFilter.install(activity);
        advance(0);
        layout();
    }

    private View addAccount(String account, boolean removeAfterClick) {
        LinearLayout row = new LinearLayout(activity);
        View button = new View(activity);
        button.setId(id("fnc"));
        button.setContentDescription(description(account));
        button.setOnClickListener(view -> {
            dismissed.add(account);
            if (removeAfterClick) rows.removeView(row);
        });
        row.addView(button, new LinearLayout.LayoutParams(48, 48));
        rows.addView(row, new LinearLayout.LayoutParams(-1, 48));
        return button;
    }

    private TextView clearControl() {
        for (int index = 0; index < header.getChildCount(); index++) {
            View child = header.getChildAt(index);
            if ("Clear all suggested accounts".contentEquals(String.valueOf(child.getContentDescription()))) {
                return (TextView) child;
            }
        }
        throw new AssertionError("the layout hook did not install Clear all on the suggested header");
    }

    private void layout() {
        activity.findViewById(android.R.id.content).getViewTreeObserver().dispatchOnGlobalLayout();
    }

    private static void advance(long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private static String description(String account) {
        return "Remove " + account + " from suggested accounts";
    }

    private static int id(String name) {
        for (int index = 0; index < RESOURCE_NAMES.length; index++) {
            if (RESOURCE_NAMES[index].equals(name)) return 4000 + index;
        }
        throw new AssertionError("unknown fixture resource " + name);
    }
}
