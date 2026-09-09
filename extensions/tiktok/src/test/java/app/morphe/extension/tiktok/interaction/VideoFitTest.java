package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.FrameLayout;

import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.PlaybackPreferenceCategory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What size a video is laid out at once the whole of it has to be on screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VideoFitTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    /** Stands in for VideoAdaptionResult, whose size getters kept their names. */
    public static final class Result {
        private final int width, height;
        Result(int width, int height) { this.width = width; this.height = height; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    /** A result of a shape we cannot read. */
    public static final class Shapeless {
    }

    @Test public void theWholeVideoLandsInsideTheWindowWhicheverWayItOverflows() {
        // A 9:16 video cropped to fill a 4:3 window: 1440x2560 over 1200x900. Fitted, the
        // height is what touches and the width comes down with it.
        assertEquals(506, VideoFit.fitWidth(1440, 2560, 1200, 900));
        assertEquals(900, VideoFit.fitHeight(1440, 2560, 1200, 900));

        // The other way round, a wide video on a tall window: the width touches.
        assertEquals(1080, VideoFit.fitWidth(1920, 1080, 1080, 2400));
        assertEquals(608, VideoFit.fitHeight(1920, 1080, 1080, 2400));

        // Exactly the window's shape: both sides touch and nothing is added.
        assertEquals(1080, VideoFit.fitWidth(2160, 4800, 1080, 2400));
        assertEquals(2400, VideoFit.fitHeight(2160, 4800, 1080, 2400));

        // A sliver still gets a pixel rather than nothing at all.
        assertEquals(1, VideoFit.fitWidth(1, 100000, 1080, 2400));
        assertEquals(2400, VideoFit.fitHeight(1, 100000, 1080, 2400));
        assertEquals(1080, VideoFit.fitWidth(100000, 1, 1080, 2400));
        assertEquals(1, VideoFit.fitHeight(100000, 1, 1080, 2400));
    }

    @Test public void theSwitchDecidesAndAnythingUnreadableIsLeftToTikTok() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);

            // A container of a shape a phone folded open has, with the video cropped to fill it.
            FrameLayout container = new FrameLayout(activity);
            View video = new View(activity);
            container.addView(video);
            container.layout(0, 0, 1200, 900);
            // Laid out the way TikTok would: taller than the window, so the ends are cut off.
            video.setLayoutParams(new FrameLayout.LayoutParams(1440, 2560));
            Result cropped = new Result(1440, 2560);

            // Off: TikTok sizes the view itself and the layout is not touched.
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            assertFalse(VideoFit.fitInstead(cropped, video));

            Settings.FIT_VIDEO_TO_SCREEN.save(true);
            video.setTranslationX(120f);
            video.setTranslationY(40f);
            assertTrue(VideoFit.fitInstead(cropped, video));
            assertEquals(506, video.getLayoutParams().width);
            assertEquals(900, video.getLayoutParams().height);
            // A video that no longer fills has to say where it sits, or every bar ends up on
            // the same side.
            assertEquals(android.view.Gravity.CENTER,
                    ((FrameLayout.LayoutParams) video.getLayoutParams()).gravity);
            // The offsets that centred a crop would push a fitted video off the screen.
            assertEquals(0f, video.getTranslationX(), 0.001f);
            assertEquals(0f, video.getTranslationY(), 0.001f);

            // A video that already fits is left exactly as TikTok laid it out.
            assertFalse(VideoFit.fitInstead(new Result(400, 800), video));

            // Before the first layout pass nothing above the video has a size, and the window
            // is what is left to measure against. Taken from the view's own resources, which
            // in a split view is the half TikTok has rather than the whole display.
            android.util.DisplayMetrics window = activity.getResources().getDisplayMetrics();
            assertTrue("the window is a squarer shape than 9:16",
                    window.widthPixels * 16 > window.heightPixels * 9);
            FrameLayout unmeasured = new FrameLayout(activity);
            View early = new View(activity);
            unmeasured.addView(early);
            early.setLayoutParams(new FrameLayout.LayoutParams(1440, 2560));
            assertTrue(VideoFit.fitInstead(new Result(1440, 2560), early));
            assertEquals(window.heightPixels, early.getLayoutParams().height);
            assertEquals(Math.round(window.heightPixels * 1440f / 2560f),
                    early.getLayoutParams().width);

            // Nothing to work with: no view, no result, a result we cannot read, and sizes
            // that make no sense. Every one of them hands the job back.
            assertFalse(VideoFit.fitInstead(cropped, null));
            assertFalse(VideoFit.fitInstead(null, video));
            assertFalse(VideoFit.fitInstead(new Shapeless(), video));
            assertFalse(VideoFit.fitInstead(new Result(0, 2560), video));
            assertFalse(VideoFit.fitInstead(new Result(1440, -1), video));
        } finally {
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
        }
    }

    @Test public void theSwitchIsReachableOnItsOwn() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.videoFitEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new PlaybackPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("fit_video_to_screen"));
        } finally {
            SettingsStatus.videoFitEnabled = false;
        }
    }
}
