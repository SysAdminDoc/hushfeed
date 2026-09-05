package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TapConfirmationTest {
    public static final class TestActivity extends PreferenceActivity {}
    public static final class Params {
        public final Clip aweme;
        Params(String id) { aweme = new Clip(id); }
    }
    public static final class Clip {
        public final String aid;
        public final Author author = new Author();
        Clip(String id) { aid = id; }
    }
    public static final class Author { public final String uid = "same_creator"; }

    @Test public void leavingAndReturningToSameVideoRequiresFreshConfirmation() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            View view = new View(controller.get());
            var update = app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor.class.getDeclaredMethod("update", Object.class);
            update.setAccessible(true);
            update.invoke(null, new Params("one"));
            assertFalse(TapConfirmation.allow(view, "follow", "one", true));
            update.invoke(null, new Params("two"));
            assertNull(view.getForeground());
            update.invoke(null, new Params("one"));
            assertFalse(TapConfirmation.allow(view, "follow", "one", true));
        }
    }
    @Test public void firstTapArmsSecondTapRunsButNewVideoAndExpiredTapsRearm() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            PreferenceActivity activity = controller.get();
            Utils.setContext(activity);
            TextView button = new TextView(activity);
            button.setText("+");
            button.setTextSize(28);
            button.setTextColor(Color.WHITE);
            button.setGravity(Gravity.CENTER);
            button.setBackgroundColor(0xFF252525);
            assertFalse(TapConfirmation.allow(button, "follow", "video1", true));
            assertNotNull(button.getForeground());
            int size = View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY);
            button.measure(size, size);
            button.layout(0, 0, 56, 56);
            Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            canvas.translate(12, 12);
            button.draw(canvas);
            String directory = System.getProperty("morphe.screenshotDir");
            if (directory != null) {
                File output = new File(directory, "confirmation-control.png");
                assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
                }
            }
            assertTrue(TapConfirmation.allow(button, "follow", "video1", true));
            assertNull(button.getForeground());
            assertFalse(TapConfirmation.allow(button, "follow", "video1", true));
            assertFalse(TapConfirmation.allow(button, "follow", "video2", true));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
            assertNull(button.getForeground());
            assertFalse(TapConfirmation.allow(button, "follow", "video2", true));
            assertTrue(TapConfirmation.allow(button, "follow", "video2", false));
            assertNull(button.getForeground());
        }
    }

    @Test public void standalonePatchExposesBothSettings() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            PreferenceActivity activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.confirmInteractionsEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("confirm_follow"));
            assertNotNull(screen.findPreference("confirm_like"));
        }
    }
}
