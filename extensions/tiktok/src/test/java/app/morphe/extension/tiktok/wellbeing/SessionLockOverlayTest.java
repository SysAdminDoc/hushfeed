package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The hold covers the feed. What it must not cover is the row of tabs along the bottom, because
 * the whole promise of holding the feed rather than emptying it is that messages, a profile and
 * search are still there. A panel over the tab bar is a panel that shuts the app.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SessionLockOverlayTest {
    private final AtomicLong now = new AtomicLong();

    public static class HostActivity extends Activity {
    }

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionBudget.awaitWritesForTests();
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
    }

    @After public void tearDown() throws Exception {
        SessionBudget.setClockForTests(null);
        SessionBudget.awaitWritesForTests();
        SessionBudget.resetForTests();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
    }

    @Test public void theHoldStopsAboveTheTabBar() throws Exception {
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            ViewGroup root = activity.findViewById(android.R.id.content);
            assertNotNull(root);

            // A tab bar the height of a real one, laid out across the bottom.
            FrameLayout bar = new FrameLayout(activity);
            View homeTab = new View(activity);
            bar.addView(homeTab);
            root.addView(bar);
            layout(root, 480, 960);
            bar.layout(0, 860, 480, 960);
            homeTab.layout(0, 0, 96, 100);
            // The id the Home tab is known by does not exist outside TikTok, so the view the
            // lookup would have found is put straight into its cache.
            seedHomeTab(homeTab);

            int height = (int) navigationHeight().invoke(null, activity, root);

            assertTrue("the panel would have covered the tab bar, height was " + height,
                    height > 0);
            assertEquals(100, height);
        }
    }

    @Test public void aBuildWithNoTabBarIsCoveredCompletely() throws Exception {
        // Better a hold that covers everything than one with a gap along the bottom that turns
        // out to be the feed.
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            ViewGroup root = activity.findViewById(android.R.id.content);
            layout(root, 480, 960);

            assertEquals(0, (int) navigationHeight().invoke(null, activity, root));
        }
    }

    @Test public void aRowTallEnoughToBeTheFeedIsNotMistakenForNavigation() throws Exception {
        // A lookup that walked too far up would find the whole page and leave the panel with a
        // margin the height of the screen, which is a hold covering nothing at all.
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            ViewGroup root = activity.findViewById(android.R.id.content);

            FrameLayout tall = new FrameLayout(activity);
            View homeTab = new View(activity);
            tall.addView(homeTab);
            root.addView(tall);
            layout(root, 480, 960);
            tall.layout(0, 0, 480, 900);
            homeTab.layout(0, 800, 96, 900);
            seedHomeTab(homeTab);

            assertEquals(0, (int) navigationHeight().invoke(null, activity, root));
        }
    }

    @Test public void nothingIsDrawnWithoutAHold() {
        assertNull("a hold nobody set", holdOrNull());
    }

    private static Object holdOrNull() {
        return SessionBudget.isLocked() ? Boolean.TRUE : null;
    }

    private static void seedHomeTab(View homeTab) {
        org.robolectric.util.ReflectionHelpers.setStaticField(
                app.morphe.extension.tiktok.blockauthor.FeedVisibility.class,
                "homeTabReference", new java.lang.ref.WeakReference<>(homeTab));
    }

    private static Method navigationHeight() throws Exception {
        Method method = SessionLockOverlay.class.getDeclaredMethod(
                "navigationHeight", Activity.class, ViewGroup.class);
        method.setAccessible(true);
        return method;
    }

    private static void layout(View view, int width, int height) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @Test public void theCountdownIsReadableOnTheScrimInEitherTheme() throws Exception {
        // The panel is always the same near-black scrim, so its colours cannot follow the
        // settings theme. The settings accent is a dark crimson in the light theme, which is
        // about 3:1 on black, and the flag it reads is a cached one the settings screen sets,
        // so away from that screen it answers for the system theme instead of for this panel.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        for (boolean darkSettings : new boolean[]{true, false}) {
            Utils.setIsDarkModeEnabled(darkSettings);
            try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
                Activity activity = owner.get();
                Utils.setActivity(activity);
                SessionLockOverlay.sync();

                ViewGroup root = activity.findViewById(android.R.id.content);
                View panel = root.getChildAt(root.getChildCount() - 1);
                android.widget.TextView countdown =
                        (android.widget.TextView) ((ViewGroup) panel).getChildAt(1);
                assertEquals("the countdown followed the settings theme onto a black panel",
                        app.morphe.extension.tiktok.settings.preference.SettingsUi.OVERLAY_ACCENT,
                        countdown.getCurrentTextColor());
            }
        }
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
