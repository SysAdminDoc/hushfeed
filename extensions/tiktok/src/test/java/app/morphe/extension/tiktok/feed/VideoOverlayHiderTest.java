package app.morphe.extension.tiktok.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Utils;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The caption and music line come back when their switch goes off, unlike the prompts,
 * which only need hiding until the next video.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoOverlayHiderTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
    }

    @Test
    public void turningTheSwitchOffPutsTheViewBack() {
        View caption = new View(context);

        VideoOverlayHider.setHidden(caption, true);
        assertEquals(View.GONE, caption.getVisibility());

        // Runs on every layout pass, so it has to stay put.
        VideoOverlayHider.setHidden(caption, true);
        assertEquals(View.GONE, caption.getVisibility());

        VideoOverlayHider.setHidden(caption, false);
        assertEquals(View.VISIBLE, caption.getVisibility());
    }

    @Test
    public void aViewTikTokHidItselfIsNotForcedBackOn() {
        View hiddenByTikTok = new View(context);
        hiddenByTikTok.setVisibility(View.GONE);

        // The switch was never on for this view, so nothing here hid it.
        VideoOverlayHider.setHidden(hiddenByTikTok, false);
        assertEquals(View.GONE, hiddenByTikTok.getVisibility());
    }

    @Test
    public void aMissingViewIsHarmless() {
        VideoOverlayHider.setHidden(null, true);
        VideoOverlayHider.setHidden(null, false);
    }

    @Test
    public void everyCellsColumnIsFoundNotJustTheFirst() {
        // The feed keeps the previous and next cells inflated beside the one on screen.
        int columnId = 0x7f0a0001;
        LinearLayout root = new LinearLayout(context);
        View[] columns = new View[3];
        for (int i = 0; i < columns.length; i++) {
            FrameLayout cell = new FrameLayout(context);
            columns[i] = new View(context);
            columns[i].setId(columnId);
            cell.addView(new View(context));
            cell.addView(columns[i]);
            root.addView(cell);
        }

        java.util.List<View> found = VideoOverlayHider.viewsWithId(root, columnId);

        assertEquals(3, found.size());
        for (View column : columns) {
            VideoOverlayHider.setHidden(column, true);
            assertEquals(View.GONE, column.getVisibility());
        }
        assertEquals(0, VideoOverlayHider.viewsWithId(root, 0).size());
        assertEquals(0, VideoOverlayHider.viewsWithId(null, columnId).size());
    }

    @Test
    public void theStatusBarComesBackOnlyIfThisClassHidIt() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            View decor = activity.getWindow().getDecorView();
            int fullscreen = View.SYSTEM_UI_FLAG_FULLSCREEN;

            VideoOverlayHider.setStatusBarHidden(activity, true);
            assertNotEquals(0, decor.getSystemUiVisibility() & fullscreen);
            // Every layout pass re-runs this; it must not toggle or stack.
            VideoOverlayHider.setStatusBarHidden(activity, true);
            assertNotEquals(0, decor.getSystemUiVisibility() & fullscreen);

            VideoOverlayHider.setStatusBarHidden(activity, false);
            assertEquals(0, decor.getSystemUiVisibility() & fullscreen);

            // TikTok hid the bar itself on some page. Turning the switch off leaves that alone.
            decor.setSystemUiVisibility(fullscreen);
            VideoOverlayHider.setStatusBarHidden(activity, true);
            VideoOverlayHider.setStatusBarHidden(activity, false);
            assertNotEquals(0, decor.getSystemUiVisibility() & fullscreen);
        }
    }
}
