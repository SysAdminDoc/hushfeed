package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w360dp-h800dp-night-mdpi")
public class SettingsActionBannerTest {
    private Activity activity;
    private FrameLayout content;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        Utils.setContext(activity);
        content = new FrameLayout(activity);
        content.setTag(SettingsActionBanner.CONTENT_ROOT_TAG);
        activity.setContentView(content);
    }

    @After public void tearDown() {
        SettingsActionBanner.dismissForTests();
        RestartPendingPreference.setRestarterForTests(null);
        activity.finish();
    }

    @Test public void undoIsReadableReachableAndRunsOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        SettingsActionBanner.showUndo(activity, "Today starts again", calls::incrementAndGet);

        View banner = content.findViewWithTag(SettingsActionBanner.BANNER_TAG);
        assertNotNull("the settings action was still sent to a toast", banner);
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE,
                banner.getAccessibilityLiveRegion());
        TextView message = banner.findViewWithTag(SettingsActionBanner.MESSAGE_TAG);
        TextView action = banner.findViewWithTag(SettingsActionBanner.ACTION_TAG);
        assertEquals("Today starts again", message.getText().toString());
        assertEquals("Undo", action.getText().toString());
        assertTrue(action.getMinimumWidth() >= SettingsUi.dp(activity, 48));
        assertTrue(action.getMinimumHeight() >= SettingsUi.dp(activity, 48));

        AccessibilityNodeInfo node = action.createAccessibilityNodeInfo();
        assertEquals(android.widget.Button.class.getName(), node.getClassName());
        assertTrue(node.isClickable());
        node.recycle();

        assertTrue(action.performClick());
        action.performClick();
        assertEquals("one press ran Undo more than once", 1, calls.get());
        assertNull(content.findViewWithTag(SettingsActionBanner.BANNER_TAG));
    }

    /** A failed action names the action: a Restart now that threw used to say a clear couldn't be undone. */
    @Test public void aFailedActionSaysWhichActionFailed() {
        SettingsActionBanner.showUndo(activity, "Today starts again", () -> {
            throw new IllegalStateException("undo failed on purpose");
        });
        TextView undo = content.findViewWithTag(SettingsActionBanner.ACTION_TAG);
        assertTrue(undo.performClick());
        assertEquals("Couldn't undo that. Try again.", ShadowToast.getTextOfLatestToast());

        RestartPendingPreference.setRestarterForTests(context -> {
            throw new IllegalStateException("restart failed on purpose");
        });
        SettingsActionBanner.showRestart(activity, "Restart TikTok to apply this.");
        TextView restart = content.findViewWithTag(SettingsActionBanner.ACTION_TAG);
        assertEquals("Restart now", restart.getText().toString());
        assertTrue(restart.performClick());
        assertEquals("Couldn't restart TikTok. Close it and open it again.",
                ShadowToast.getTextOfLatestToast());
    }

    @Test public void aNewBannerGetsItsOwnFullLifetime() {
        var looper = Shadows.shadowOf(android.os.Looper.getMainLooper());
        SettingsActionBanner.showNotice(activity, "first");
        looper.idleFor(Duration.ofSeconds(4));
        SettingsActionBanner.showNotice(activity, "second");

        looper.idleFor(Duration.ofSeconds(6));
        TextView message = content.findViewWithTag(SettingsActionBanner.MESSAGE_TAG);
        assertNotNull("the old timer removed the newer banner", message);
        assertEquals("second", message.getText().toString());

        looper.idleFor(Duration.ofSeconds(5));
        assertNull("the newer banner never left", content.findViewWithTag(
                SettingsActionBanner.BANNER_TAG));
    }

    @Test public void replacingFeedbackLeavesExactlyOneBanner() {
        SettingsActionBanner.showNotice(activity, "first");
        SettingsActionBanner.showUndo(activity, "second", () -> { });

        assertEquals(1, content.getChildCount());
        TextView message = content.findViewWithTag(SettingsActionBanner.MESSAGE_TAG);
        assertNotNull(message);
        assertEquals("second", message.getText().toString());
    }

    @Test public void restartIsAReachableOneShotAction() {
        AtomicInteger restarts = new AtomicInteger();
        RestartPendingPreference.setRestarterForTests(ignored -> restarts.incrementAndGet());

        SettingsActionBanner.showRestart(activity, "Restart TikTok to apply this change");

        TextView action = content.findViewWithTag(SettingsActionBanner.ACTION_TAG);
        assertNotNull("restart feedback has no action", action);
        assertEquals("Restart now", action.getText().toString());
        AccessibilityNodeInfo node = action.createAccessibilityNodeInfo();
        assertEquals(android.widget.Button.class.getName(), node.getClassName());
        assertTrue(node.isClickable());
        node.recycle();

        assertTrue(action.performClick());
        action.performClick();
        assertEquals("one press restarted more than once", 1, restarts.get());
        assertNull(content.findViewWithTag(SettingsActionBanner.BANNER_TAG));
    }
}
