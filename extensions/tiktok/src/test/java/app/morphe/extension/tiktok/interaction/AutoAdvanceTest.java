package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.*;
import android.view.View;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
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
    }
    @Test public void restartsNativeStopButPreservesPauseAndStopsWhenDisabled() {
        var control = new AutoAdvance.Control(new FeedView());
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
    @Test public void disabledSettingLeavesPreexistingNativeAutoScrollAlone() {
        var control = new AutoAdvance.Control(new FeedView());
        var state = new AtomicReference<>(State.AUTO_SCROLL_STATE_START);
        control.update(state::get, () -> fail("Already running"), () -> fail("Native ownership"));
        Settings.AUTO_ADVANCE.save(false);
        control.update(state::get, () -> fail("Disabled"), () -> fail("Native ownership"));
        assertFalse(AutoAdvance.available(false));
        assertTrue(AutoAdvance.available(true));
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
        var control = new AutoAdvance.Control(new FeedView());
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
}
