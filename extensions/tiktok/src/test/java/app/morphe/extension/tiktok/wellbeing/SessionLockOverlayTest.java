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
import org.robolectric.Shadows;
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
        Settings.SESSION_BUDGET_LOCK.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        now.set(at(2026, Calendar.SEPTEMBER, 7, 12, 0));
        SessionBudget.setClockForTests(now::get);
        SessionBudget.resetForTests();
        // With no hold this detaches and hands back the audio focus, which is what clears the
        // overlay's own static state between cases.
        SessionLockOverlay.sync();
    }

    @After public void tearDown() throws Exception {
        // The override is a static Boolean on the shared library and Robolectric reuses its
        // sandbox classloader across test classes, so leaving it set answers for every later
        // test that expects the system configuration.
        org.robolectric.util.ReflectionHelpers.setStaticField(
                Utils.class, "isDarkModeEnabled", null);
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

    @Test public void aLockedDayLeavesThePanelWithNoWayOut() throws Exception {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        Settings.SESSION_BUDGET_LOCK.save(true);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            SessionLockOverlay.sync();

            ViewGroup root = activity.findViewById(android.R.id.content);
            ViewGroup panel = (ViewGroup) root.getChildAt(root.getChildCount() - 1);
            View release = panel.getChildAt(3);
            assertEquals("Open the feed anyway is still on a locked panel",
                    View.GONE, release.getVisibility());

            // And tapping where it used to be does nothing, so a stale panel cannot be used
            // as a way out either.
            release.performClick();
            assertTrue("a tap lifted a locked hold", SessionBudget.isLocked());

            android.widget.TextView hint = (android.widget.TextView) panel.getChildAt(2);
            assertTrue("the panel does not say when the feed comes back: " + hint.getText(),
                    hint.getText().toString().contains(SessionLockOverlay.resetTimeLabel()));
        }
    }

    @Test public void anUnlockedDayKeepsItsWayOut() throws Exception {
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            SessionLockOverlay.sync();

            ViewGroup root = activity.findViewById(android.R.id.content);
            ViewGroup panel = (ViewGroup) root.getChildAt(root.getChildCount() - 1);
            assertEquals("the way out went missing on a day nobody locked",
                    View.VISIBLE, panel.getChildAt(3).getVisibility());
            panel.getChildAt(3).performClick();
            assertTrue("Open the feed anyway did not open the feed", !SessionBudget.isLocked());
        }
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

    @Test public void theHoldAsksTheFeedToStopPlaying() throws Exception {
        // The panel covers the feed and swallows touches, but the video underneath kept playing
        // with sound, which reads as the app having broken. Taking the audio focus is how one
        // app tells another to stop, and it is the only lever this extension has.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        android.media.AudioManager audio = (android.media.AudioManager)
                RuntimeEnvironment.getApplication().getSystemService(
                        android.content.Context.AUDIO_SERVICE);
        var shadow = org.robolectric.Shadows.shadowOf(audio);
        assertNull("something already held the focus", shadow.getLastAudioFocusRequest());

        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Utils.setActivity(owner.get());
            SessionLockOverlay.sync();

            var request = shadow.getLastAudioFocusRequest();
            assertNotNull("the feed kept playing behind the hold", request);
            assertEquals("a permanent grab would leave the feed silent afterwards",
                    android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.durationHint);

            // Handing it back is what lets the feed play again, so a hold that ends must not
            // leave the app quiet.
            SessionBudget.releaseLock();
            SessionLockOverlay.sync();
            assertNotNull("the focus was never handed back", shadow.getLastAbandonedAudioFocusListener());
        }
    }

    @Test public void theHoldDoesNotFightWhoeverTookTheSoundAway() throws Exception {
        // A call takes the focus for good. The flag has to follow, or nothing would ever ask
        // again for the rest of the hold. What must not happen is asking again a second later:
        // the tick runs every second, and that would be taking the sound off the call.
        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());

        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            SessionLockOverlay.sync();
            assertTrue("the hold never took the focus", quietened());

            android.media.AudioManager.OnAudioFocusChangeListener listener =
                    org.robolectric.util.ReflectionHelpers.getStaticField(
                            SessionLockOverlay.class, "QUIET");
            listener.onAudioFocusChange(android.media.AudioManager.AUDIOFOCUS_LOSS);
            assertTrue("losing the focus left the hold thinking it still had it", !quietened());

            SessionLockOverlay.sync();
            assertTrue("the hold asked a call to give the sound back a second later", !quietened());

            // The panel going up again is a thing the reader did, so that is when it is fair to
            // ask. This is the state the off-feed branch leaves the panel in.
            ViewGroup root = activity.findViewById(android.R.id.content);
            root.getChildAt(root.getChildCount() - 1).setVisibility(View.GONE);
            SessionLockOverlay.sync();
            assertTrue("coming back to the feed did not quiet it again", quietened());
        }
    }

    private static boolean quietened() {
        return org.robolectric.util.ReflectionHelpers.getStaticField(
                SessionLockOverlay.class, "quietened");
    }

    @Test public void aHoldRestoredFromDiskReattachesOnTheFirstPlayerReport() throws Exception {
        // What makes a hold survive the app being killed: the player reports progress, that asks
        // whether a hold is running, and the answer comes off the persisted record. Every other
        // case here starts from a hold already in memory, so a break anywhere along this path
        // passed the suite. The fast path in isLocked() is exactly the shape that would.
        long until = now.get() + 5L * 60_000L;
        Settings.SESSION_BUDGET_STATE.save(SessionBudget.dayOf(now.get())
                + "|3|0|" + until + "|1");
        SessionBudget.resetForTests();
        assertTrue("the record did not carry the hold back", SessionBudget.isLocked());

        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            int before = root.getChildCount();

            SessionLockOverlay.ensureRunning();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertTrue("a hold that survived the process being killed never came back",
                    root.getChildCount() > before);
        }
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
