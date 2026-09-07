package app.morphe.extension.tiktok.shared;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.widget.FrameLayout;

import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.GlobalLayoutHook;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GlobalLayoutHookTest {
    @Test public void replacingRootsDetachesTheOldListenerAndDetachStopsTheNewOne() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout first = new FrameLayout(activity);
        FrameLayout second = new FrameLayout(activity);
        AtomicInteger calls = new AtomicInteger();
        GlobalLayoutHook hook = new GlobalLayoutHook();

        assertTrue(hook.install(first, calls::incrementAndGet));
        first.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(1, calls.get());
        assertFalse(hook.install(first, calls::incrementAndGet));

        assertTrue(hook.install(second, calls::incrementAndGet));
        first.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(1, calls.get());
        second.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(2, calls.get());

        hook.detach();
        second.getViewTreeObserver().dispatchOnGlobalLayout();
        assertEquals(2, calls.get());
    }
}
