package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.*;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GestureActionsTest {
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }
    public static final class Params {
        public final Clip aweme;
        Params(String id) { aweme = new Clip(id); }
    }
    public static final class Clip {
        public final String aid;
        Clip(String id) { aid = id; }
    }

    @Test public void commentsUseMatchingAttachedNativeControlAndFollowRebinding() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            FrameLayout root = new FrameLayout(activity);
            View first = new View(activity), second = new View(activity);
            root.addView(first); root.addView(second);
            activity.setContentView(root);
            int[] clicks = {0, 0};
            first.setOnClickListener(view -> clicks[0]++);
            second.setOnClickListener(view -> clicks[1]++);
            Object firstOwner = new Object(), secondOwner = new Object();
            GestureActions.registerCommentView(firstOwner, first);
            GestureActions.bindCommentView(firstOwner, new Params("one"));
            GestureActions.bindCommentView(secondOwner, new Params("two"));
            GestureActions.registerCommentView(secondOwner, second);
            assertTrue(GestureActions.openComments("two"));
            assertArrayEquals(new int[]{0, 1}, clicks);
            GestureActions.bindCommentView(secondOwner, new Params("three"));
            assertFalse(GestureActions.openComments("two"));
            assertTrue(GestureActions.openComments("three"));
            root.removeView(second);
            assertFalse(GestureActions.openComments("three"));
            Object newOwner = new Object();
            GestureActions.registerCommentView(newOwner, first);
            GestureActions.bindCommentView(newOwner, new Params("four"));
            assertFalse(GestureActions.openComments("one"));
            assertTrue(GestureActions.openComments("four"));
            assertFalse(GestureActions.openComments(null));
            Settings.DOUBLE_TAP_ACTION.save("nothing");
            assertTrue(GestureActions.onDoubleTap());
            Settings.DOUBLE_TAP_ACTION.save("default");
            assertFalse(GestureActions.onDoubleTap());
        }
    }

    @Test public void standalonePatchHasThreeReachableChoices() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.doubleTapEnabled = true;
            Utils.setIsDarkModeEnabled(true);
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            ChoicePreference choice = (ChoicePreference) screen.findPreference("double_tap_action");
            assertNotNull(choice);
            assertArrayEquals(new String[]{"default", "nothing", "comments"}, choice.getEntryValues());
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "interface-settings.png");
        }
    }
}
