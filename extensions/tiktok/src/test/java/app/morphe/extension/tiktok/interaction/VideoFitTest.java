package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.FrameLayout;

import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.interaction.narrowed.NarrowedResult;
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

    /**
     * Stands in for VideoAdaptionResult, whose size getters kept their names. It is a data
     * class, so its equality and hash are built from the size, and the patch writes the fitted
     * width into it from inside the class before the height is asked for; both are copied here
     * because the second is what a map keyed by the result cannot survive.
     */
    public static final class Result {
        int width, height;
        final Float translateX, translateY;
        final Object operator;
        Result(int width, int height) { this(width, height, null, null, null); }
        Result(int width, int height, Float translateX, Float translateY, Object operator) {
            this.width = width;
            this.height = height;
            this.translateX = translateX;
            this.translateY = translateY;
            this.operator = operator;
        }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public Float getTranslateX() { return translateX; }
        public Float getTranslateY() { return translateY; }
        public Object getResultOperator() { return operator; }
        /** The copy a Kotlin data class generates, which is how a fitted result is remade. */
        public Result copy(int width, int height, Float translateX, Float translateY, Object operator) {
            return new Result(width, height, translateX, translateY, operator);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Result && ((Result) other).width == width
                    && ((Result) other).height == height;
        }
        @Override public int hashCode() { return width * 31 + height; }
    }

    /** A result of a shape we cannot read. */
    public static final class Shapeless {
    }

    /** A result whose size can be read, with no copy to remake it through. */
    public static final class Uncopyable {
        public int getWidth() { return 1440; }
        public int getHeight() { return 2560; }
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

            // Off: the result is to be left alone, and nothing is remembered.
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(cropped, video));
            assertEquals(2560, VideoFit.fittedHeightFor(cropped));
            assertEquals(Float.valueOf(12f), VideoFit.fittedTranslation(cropped, Float.valueOf(12f)));

            Settings.FIT_VIDEO_TO_SCREEN.save(true);
            assertEquals(506, VideoFit.fitWidthFor(cropped, video));
            // The patch writes that width into the result before asking for the height, and the
            // result's hash moves with it. The answer has to be for this result all the same.
            cropped.width = 506;
            // The height that goes with that width, worked out from the size before it changed.
            assertEquals(900, VideoFit.fittedHeightFor(cropped));
            // Asked twice, because the host reads it once and this must not consume it.
            assertEquals(900, VideoFit.fittedHeightFor(cropped));
            // The offsets that centred a crop would push a fitted video off the screen.
            assertEquals(Float.valueOf(0f), VideoFit.fittedTranslation(cropped, Float.valueOf(12f)));
            // A video that no longer fills has to say where it sits, or every bar ends up on
            // the same side.
            assertEquals(android.view.Gravity.CENTER,
                    ((FrameLayout.LayoutParams) video.getLayoutParams()).gravity);

            // A video that already fits is left exactly as TikTok laid it out, offsets included.
            Result fits = new Result(400, 800);
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(fits, video));
            assertEquals(800, VideoFit.fittedHeightFor(fits));
            assertEquals(Float.valueOf(12f), VideoFit.fittedTranslation(fits, Float.valueOf(12f)));

            // A second result equal to the last fitted one, by value, is still not it: the
            // decision belongs to the object it was made for.
            cropped.width = 1440;
            assertEquals(506, VideoFit.fitWidthFor(cropped, video));
            Result twin = new Result(1440, 2560);
            assertEquals(2560, VideoFit.fittedHeightFor(twin));
            assertEquals(Float.valueOf(12f), VideoFit.fittedTranslation(twin, Float.valueOf(12f)));
            assertEquals(900, VideoFit.fittedHeightFor(cropped));

            // Deciding again on the same result clears what the last pass remembered.
            container.layout(0, 0, 2000, 4000);
            View roomy = new View(activity);
            container.addView(roomy);
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(cropped, roomy));
            assertEquals(2560, VideoFit.fittedHeightFor(cropped));
            assertEquals(Float.valueOf(12f), VideoFit.fittedTranslation(cropped, Float.valueOf(12f)));

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
            Result earlyResult = new Result(1440, 2560);
            assertEquals(Math.round(window.heightPixels * 1440f / 2560f),
                    VideoFit.fitWidthFor(earlyResult, early));
            assertEquals(window.heightPixels, VideoFit.fittedHeightFor(earlyResult));

            // Nothing to work with: no view, no result, a result we cannot read, and sizes
            // that make no sense. Every one of them says leave it, and the patch then writes
            // nothing. The old answer was the width it had read, 0 for a result it could not
            // read, and the patch wrote that into the result, so a renamed getter would have
            // laid the story out at nothing by nothing.
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(cropped, null));
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(null, video));
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(new Shapeless(), video));
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(new Result(0, 2560), video));
            assertEquals(VideoFit.LEAVE, VideoFit.fitWidthFor(new Result(1440, -1), video));
        } finally {
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
        }
    }

    @Test public void theFeedCellIsHandedAFittedCopyAndOtherwiseItsOwnResult() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            FrameLayout container = new FrameLayout(activity);
            View video = new View(activity);
            container.addView(video);
            container.layout(0, 0, 1200, 900);
            video.setLayoutParams(new FrameLayout.LayoutParams(1440, 2560));
            Object operator = new Object();
            Result cropped = new Result(1440, 2560, 0f, -320f, operator);

            // Off: the very same result, not a copy of it.
            Settings.FIT_VIDEO_TO_SCREEN.save(false);
            assertSame(cropped, VideoFit.fitted(video, cropped));

            // On: a copy at the fitted size, with no offsets, carrying the operator across.
            Settings.FIT_VIDEO_TO_SCREEN.save(true);
            Object fitted = VideoFit.fitted(video, cropped);
            assertNotSame(cropped, fitted);
            Result copy = (Result) fitted;
            assertEquals(506, copy.getWidth());
            assertEquals(900, copy.getHeight());
            assertEquals(Float.valueOf(0f), copy.getTranslateX());
            assertEquals(Float.valueOf(0f), copy.getTranslateY());
            assertSame(operator, copy.getResultOperator());
            assertEquals(android.view.Gravity.CENTER,
                    ((FrameLayout.LayoutParams) video.getLayoutParams()).gravity);
            // The result handed in is left exactly as it was.
            assertEquals(1440, cropped.getWidth());
            assertEquals(2560, cropped.getHeight());
            assertEquals(Float.valueOf(-320f), cropped.getTranslateY());

            // A video that already fits is TikTok's own result again, offsets and all.
            Result fits = new Result(400, 800, 0f, 12f, operator);
            assertSame(fits, VideoFit.fitted(video, fits));

            // Nothing to work with hands the result straight back.
            assertSame(cropped, VideoFit.fitted(null, cropped));
            assertNull(VideoFit.fitted(video, null));
            Shapeless shapeless = new Shapeless();
            assertSame(shapeless, VideoFit.fitted(video, shapeless));
            // A result that can be read but not remade is also handed back as it is. Readable,
            // as the story path proves by working out a size from it; only the copy is missing.
            Uncopyable uncopyable = new Uncopyable();
            assertEquals(506, VideoFit.fitWidthFor(uncopyable, video));
            assertSame(uncopyable, VideoFit.fitted(video, uncopyable));

            // A copy that is not public, on a class in another package, is still the copy to
            // remake the result through. VideoFit sits outside TikTok's package the way it sits
            // outside this one, so the copy is found among what the class declares and has to be
            // made accessible before it can be called.
            NarrowedResult narrowed = new NarrowedResult(1440, 2560);
            Object remade = VideoFit.fitted(video, narrowed);
            assertNotSame(narrowed, remade);
            assertEquals(506, ((NarrowedResult) remade).getWidth());
            assertEquals(900, ((NarrowedResult) remade).getHeight());
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
