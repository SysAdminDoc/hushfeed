package app.morphe.extension.tiktok.comment;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import app.morphe.extension.shared.Utils;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

/**
 * The thumbs down listener is one object shared by every comment on screen. These tests pin that
 * a press belongs to the control it landed on: a release acts only where its own press was taken,
 * so a second finger or a rebound cell cannot turn one comment's release into another's block.
 *
 * <p>Reaching a block needs the native block service, so these drive the boundary just before it.
 * A tap on a control whose cell carries no comment reports that it could not read the author, and
 * that toast is the signal that the tap was acted on at all.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentDislikeGestureTest {
    private Activity activity;
    private View.OnTouchListener touch;

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        touch = ReflectionHelpers.getStaticField(CommentTools.class, "DISLIKE_TOUCH");
        // The listener outlives any one comment sheet, so a press left over from an earlier test
        // would decide what this one observes.
        ReflectionHelpers.<Map<View, ?>>getField(touch, "gestures").clear();
        ShadowToast.reset();
    }

    @Test public void aReleaseActsOnTheControlItsOwnPressLandedOn() {
        View comment = new View(activity);

        touch.onTouch(comment, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(comment, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("A clean tap did not act on its own comment", 1, toastCount());
    }

    @Test public void aReleaseWithNoPressOfItsOwnActsOnNothing() {
        View pressed = new View(activity);
        View neverPressed = new View(activity);

        // The press is taken on one comment and the release arrives on another, which is what a
        // second pointer or a recycled cell produces.
        touch.onTouch(pressed, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(neverPressed, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("A release blocked a comment that was never pressed", 0, toastCount());
    }

    @Test public void aDragOnOneCommentLeavesAnothersTapAlone() {
        View tapped = new View(activity);
        View dragged = new View(activity);

        touch.onTouch(tapped, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(dragged, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(dragged, event(MotionEvent.ACTION_MOVE, 500, 500));
        touch.onTouch(tapped, event(MotionEvent.ACTION_UP, 5, 5));

        // Exactly one: the tap it belongs to, and not the drag on the other comment.
        assertEquals("A drag elsewhere changed what this tap did", 1, toastCount());
    }

    @Test public void aDragStaysADragOnItsOwnControl() {
        View dragged = new View(activity);

        touch.onTouch(dragged, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(dragged, event(MotionEvent.ACTION_MOVE, 500, 500));
        touch.onTouch(dragged, event(MotionEvent.ACTION_UP, 500, 500));

        assertEquals("A drag was treated as a tap", 0, toastCount());
    }

    @Test public void acancelledPressIsNotReleasedLater() {
        View comment = new View(activity);

        touch.onTouch(comment, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(comment, event(MotionEvent.ACTION_CANCEL, 5, 5));
        touch.onTouch(comment, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("A cancelled press still acted on release", 0, toastCount());
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static int toastCount() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return ShadowToast.shownToastCount();
    }
}
