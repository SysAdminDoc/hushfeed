package app.morphe.extension.tiktok.comment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import app.morphe.extension.shared.Utils;
import java.util.Map;
import java.util.Set;
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
        ReflectionHelpers.<Map<View, ?>>getStaticField(CommentTools.class, "CELL_COMMENTS").clear();
        // A block that never reports back leaves the busy guard set, and every later tap would
        // return on it instead of acting.
        ReflectionHelpers.setStaticField(CommentTools.class, "blockInFlight", false);
        ReflectionHelpers.<Set<String>>getStaticField(CommentTools.class, "BLOCKED_UIDS").clear();
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

    /**
     * The toast tests above show whether a tap was acted on, not which comment it reached. These
     * two register a real comment on each row and read the haptic tick that only the acted-on
     * cell receives, so an implementation that released onto the last pressed row instead of its
     * own would be caught.
     */
    @Test public void aTapReachesTheCommentItLandedOn() {
        View first = commentCell("uid-first");
        View second = commentCell("uid-second");

        touch.onTouch(first, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(first, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("The tap did not reach its own comment",
                HapticFeedbackConstants.LONG_PRESS, hapticOn(first));
        assertEquals("The tap reached the other comment", -1, hapticOn(second));
    }

    /**
     * Two rows held down at once, released in the order they were pressed. The release belongs to
     * the row it lands on, not to whichever was pressed most recently.
     */
    @Test public void aReleaseReachesItsOwnRowWhileAnotherIsStillHeld() {
        View first = commentCell("uid-first");
        View second = commentCell("uid-second");

        touch.onTouch(first, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(second, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(first, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("The release did not reach the row it landed on",
                HapticFeedbackConstants.LONG_PRESS, hapticOn(first));
        assertEquals("The release reached the row that was pressed later", -1, hapticOn(second));
    }

    @Test public void aReleaseWithNoPressReachesNeitherComment() {
        View pressed = commentCell("uid-pressed");
        View released = commentCell("uid-released");

        touch.onTouch(pressed, event(MotionEvent.ACTION_DOWN, 5, 5));
        touch.onTouch(released, event(MotionEvent.ACTION_UP, 5, 5));

        assertEquals("The release acted on the row that was pressed", -1, hapticOn(pressed));
        assertEquals("The release acted on the row it landed on", -1, hapticOn(released));
    }

    /** A row carrying a comment whose author can be read, which is what reaching a block needs. */
    @Test public void anAccessibilityServiceActivatesTheBlockRatherThanTikToksDislike() {
        // TalkBack and Switch Access activate a control with performClick(), which produces no
        // MotionEvents, so a touch listener on its own left them reaching TikTok's own dislike
        // while the label still said "dislike".
        android.widget.FrameLayout cell = new android.widget.FrameLayout(activity);
        Map<View, Object> cells = ReflectionHelpers.getStaticField(CommentTools.class, "CELL_COMMENTS");
        synchronized (cells) {
            cells.put(cell, new Comment("uid-spoken"));
        }
        View button = new View(activity);
        View icon = new View(activity);
        cell.addView(button);
        cell.addView(icon);
        CommentTools.wireBlockControl(button, icon);

        assertTrue("the control was never given a click listener", button.performClick());
        assertEquals("activating the control did not reach the comment it belongs to",
                HapticFeedbackConstants.LONG_PRESS, hapticOn(cell));
        assertEquals("a screen reader would read the same control twice",
                View.IMPORTANT_FOR_ACCESSIBILITY_NO, icon.getImportantForAccessibility());
    }

    // The state description arrived in API 30, and the class runs at 28 for the gesture cases.
    @Test @org.robolectric.annotation.Config(sdk = 30)
    public void theControlSaysWhatItDoesAndWhetherItHasBeenUsed() {
        View button = new View(activity);

        CommentTools.describeBlockControl(button, false);
        assertEquals("Block this commenter", String.valueOf(button.getContentDescription()));
        assertEquals("Not blocked", String.valueOf(button.getStateDescription()));

        // A blocked row was faded and tinted and said nothing, so a screen reader heard no
        // difference between a blocked account and any other.
        CommentTools.describeBlockControl(button, true);
        assertEquals("Unblock this commenter", String.valueOf(button.getContentDescription()));
        assertEquals("Blocked", String.valueOf(button.getStateDescription()));
    }

    @Test public void blockingOneCommentUpdatesEveryRowByThatAccount() throws Exception {
        // A thread usually holds several comments by the same account. Refreshing only the row
        // that was tapped left the others reading "Block this commenter, not blocked" for an
        // account that is already blocked, and the toggle reads the blocked set rather than the
        // label, so following that label unblocked instead of blocking.
        View tapped = commentCell("uid-same");
        View sibling = commentCell("uid-same");
        View unrelated = commentCell("uid-other");

        java.util.Set<String> blocked =
                ReflectionHelpers.getStaticField(CommentTools.class, "BLOCKED_UIDS");
        blocked.add("uid-same");
        try {
            java.lang.reflect.Method refresh =
                    CommentTools.class.getDeclaredMethod("applyBlockedEverywhere");
            refresh.setAccessible(true);
            refresh.invoke(null);

            assertEquals("the row that was tapped does not read as blocked", 0.55f,
                    tapped.getAlpha(), 0.001f);
            assertEquals("another row by the same account was left saying the opposite", 0.55f,
                    sibling.getAlpha(), 0.001f);
            assertEquals("a row by a different account was faded too", 1f,
                    unrelated.getAlpha(), 0.001f);
        } finally {
            blocked.remove("uid-same");
        }
    }

    private View commentCell(String uid) {
        View cell = new View(activity);
        Map<View, Object> cells = ReflectionHelpers.getStaticField(CommentTools.class, "CELL_COMMENTS");
        synchronized (cells) {
            cells.put(cell, new Comment(uid));
        }
        return cell;
    }

    private static int hapticOn(View view) {
        return Shadows.shadowOf(view).lastHapticFeedbackPerformed();
    }

    @SuppressWarnings("unused")
    public static final class Comment {
        private final User user;
        private final String cid;

        Comment(String uid) {
            this.user = new User(uid);
            this.cid = "cid-" + uid;
        }

        public User getUser() { return user; }
        public String getCid() { return cid; }
    }

    @SuppressWarnings("unused")
    public static final class User {
        private final String uid;

        User(String uid) { this.uid = uid; }

        public String getUid() { return uid; }
        public String getSecUid() { return "sec-" + uid; }
        public String getUniqueId() { return "name-" + uid; }
    }

    private static MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static int toastCount() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return ShadowToast.shownToastCount();
    }
}
