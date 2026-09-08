package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.*;
import android.view.View;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AutoAdvanceTest {
    @After public void tearDown() {
        // clear() is the public way in from another package: it ends any hold and empties both
        // counts, in memory and in the record.
        SessionBudget.clear();
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        SettingsStatus.autoAdvanceEnabled = false;
        SettingsStatus.playbackSpeedEnabled = false;
        SettingsStatus.playbackQualityEnabled = false;
        Settings.AUTO_ADVANCE_LIMIT.save(0);
    }
    enum State { AUTO_SCROLL_STATE_START, AUTO_SCROLL_STATE_STOP, AUTO_SCROLL_STATE_PAUSE }
    static final class FeedView extends View {
        boolean attached = true, shown = true, focused = true;
        FeedView() { super(RuntimeEnvironment.getApplication()); }
        @Override public boolean isAttachedToWindow() { return attached; }
        @Override public boolean isShown() { return shown; }
        @Override public boolean hasWindowFocus() { return focused; }
    }
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.AUTO_ADVANCE.save(true);
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        SessionBudget.clear();
    }
    @Test public void restartsNativeStopButPreservesPauseAndStopsWhenDisabled() {
        // The control holds the view weakly, so the test has to hold it strongly. Without
        // this the view can be collected part way through and every later update returns
        // early, which showed up as one failing run in ten.
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_STOP);
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        Runnable start = () -> { starts.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_START); };
        Runnable stop = () -> { stops.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_STOP); };
        control.update(state::get, start, stop);
        assertEquals(1, starts.get());
        assertTrue(control.owned);
        control.update(state::get, start, stop);
        assertEquals(1, starts.get());
        state.set(State.AUTO_SCROLL_STATE_PAUSE);
        control.update(state::get, start, stop);
        assertEquals(State.AUTO_SCROLL_STATE_PAUSE, state.get());
        state.set(State.AUTO_SCROLL_STATE_STOP);
        control.update(state::get, start, stop);
        assertEquals(2, starts.get());
        Settings.AUTO_ADVANCE.save(false);
        control.update(state::get, start, stop);
        control.update(state::get, start, stop);
        assertEquals(1, stops.get());
        assertFalse(control.owned);
    }
    @Test public void aRunningHoldStopsAutoAdvanceAndLetsItStartAgainAfterwards() {
        // Advancing behind the hold walks through videos nobody can see, and each one used to
        // spend a place in this session's own limit as well.
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_STOP);
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        Runnable start = () -> { starts.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_START); };
        Runnable stop = () -> { stops.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_STOP); };

        control.update(state::get, start, stop);
        assertTrue(control.owned);

        Settings.SESSION_BUDGET_VIDEOS.save(1);
        Settings.SESSION_BUDGET_LOCK_MINUTES.save(5);
        SessionBudget.noteVideo("a");
        assertTrue(SessionBudget.claimNotice());
        assertTrue("no hold started", SessionBudget.isLocked());

        control.update(state::get, start, stop);
        assertEquals("the feed kept advancing behind the hold", 1, stops.get());
        assertFalse("ownership was kept through the hold", control.owned);
        control.update(state::get, start, stop);
        assertEquals("it started again while the hold was still running", 1, starts.get());

        SessionBudget.releaseLock();
        assertFalse(SessionBudget.isLocked());
        control.update(state::get, start, stop);
        assertEquals("it never started again once the hold ended", 2, starts.get());
        assertTrue(control.owned);
    }

    @Test public void disabledSettingLeavesPreexistingNativeAutoScrollAlone() {
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_START);
        control.update(state::get, () -> fail("Already running"), () -> fail("Native ownership"));
        Settings.AUTO_ADVANCE.save(false);
        control.update(state::get, () -> fail("Disabled"), () -> fail("Native ownership"));
        assertFalse(AutoAdvance.available(false));
        assertTrue(AutoAdvance.available(true));
    }
    @Test public void aCollectedFeedViewEndsTheControlsWork() {
        FeedView view = new FeedView();
        var control = new AutoAdvance.Control(view);
        control.update(() -> State.AUTO_SCROLL_STATE_STOP, () -> { }, () -> fail("Not started"));

        // Ownership of the view is deliberately weak. Once it is gone the control must do
        // nothing at all, which is also why every case here keeps its own reference.
        control.view.clear();
        control.update(() -> State.AUTO_SCROLL_STATE_STOP, () -> fail("View is gone"), () -> fail("View is gone"));
    }
    @Test public void hiddenDetachedAndUnfocusedViewsCannotStartScrolling() {
        FeedView view = new FeedView();
        var control = new AutoAdvance.Control(view);
        for (int i = 0; i < 3; i++) {
            view.attached = i != 0;
            view.shown = i != 1;
            view.focused = i != 2;
            control.update(() -> State.AUTO_SCROLL_STATE_STOP, () -> fail("Inactive view"), () -> fail("Inactive view"));
        }
        view.attached = view.shown = view.focused = true;
        control.update(() -> null, () -> fail("Unknown state"), () -> fail("Unknown state"));
    }
    @Test public void nativeRefusalDoesNotClaimOwnershipAndSettingIsReachable() throws Exception {
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        control.update(() -> State.AUTO_SCROLL_STATE_STOP, () -> { }, () -> fail("Not started"));
        assertFalse(control.owned);
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.autoAdvanceEnabled = true;
            SettingsStatus.playbackSpeedEnabled = true;
            SettingsStatus.playbackQualityEnabled = true;
            var fragment = new TikTokPreferenceFragment();
            android.os.Bundle arguments = new android.os.Bundle();
            arguments.putString("morphe_settings_section", "PLAYBACK");
            fragment.setArguments(arguments);
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
            activity.getFragmentManager().executePendingTransactions();
            assertNotNull(fragment.findPreference("auto_advance"));
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "auto-advance-settings.png");
        }
    }

    @Test public void ownedSessionStopsAtItsVisibleCompletionLimitAndCanBeReset() {
        Settings.AUTO_ADVANCE_LIMIT.save(2);
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_STOP);
        var starts = new AtomicInteger();
        var stops = new AtomicInteger();
        Runnable start = () -> { starts.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_START); };
        Runnable stop = () -> { stops.incrementAndGet(); state.set(State.AUTO_SCROLL_STATE_STOP); };

        control.update(state::get, start, stop);
        assertTrue(control.owned);
        assertTrue(control.recordCompletion("first"));
        state.set(State.AUTO_SCROLL_STATE_STOP);
        control.update(state::get, start, stop);
        assertEquals(2, starts.get());
        assertTrue(control.recordCompletion("second"));
        state.set(State.AUTO_SCROLL_STATE_STOP);
        control.update(state::get, start, stop);
        assertEquals(1, stops.get());
        assertFalse(control.owned);
        assertFalse(control.recordCompletion("second"));

        // Removing the limit lets this feed session continue. A new component starts at zero.
        Settings.AUTO_ADVANCE_LIMIT.save(0);
        control.update(state::get, start, stop);
        assertEquals(3, starts.get());
        var fresh = new AutoAdvance.Control(new FeedView());
        assertEquals(0, fresh.completedCount);
    }

    @Test public void nativeOnlyCompletionsDoNotConsumeTheHushfeedLimit() {
        Settings.AUTO_ADVANCE_LIMIT.save(1);
        FeedView feed = new FeedView();
        var control = new AutoAdvance.Control(feed);
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_START);
        control.update(state::get, () -> fail("native scrolling is already running"),
                () -> fail("native scrolling is not owned"));
        assertFalse(control.owned);
        assertFalse(control.recordCompletion("native"));
        assertEquals(0, control.completedCount);
    }
}
