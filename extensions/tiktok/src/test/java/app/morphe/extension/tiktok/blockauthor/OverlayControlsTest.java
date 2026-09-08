package app.morphe.extension.tiktok.blockauthor;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OverlayControlsTest {
    @Test public void renderAccessibleOverlayControls() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(activity);
        Bitmap bitmap = Bitmap.createBitmap(304, 88, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(24, 24, 27));
        String[] methods = {"createButton", "createLocalHideButton", "createSoundButton", "createNotInterestedButton"};
        for (int i = 0; i < methods.length; i++) {
            Method factory = BlockAuthorOverlay.class.getDeclaredMethod(methods[i], Activity.class);
            factory.setAccessible(true);
            View view = (View) factory.invoke(null, activity);
            assertNotNull(view.getContentDescription());
            assertTrue(view.hasOnClickListeners());
            int size = View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY);
            view.measure(size, size);
            view.layout(0, 0, 56, 56);
            canvas.save();
            canvas.translate(16 + i * 72, 16);
            view.draw(canvas);
            canvas.restore();
        }
        String directory = System.getProperty("morphe.screenshotDir");
        if (directory != null) {
            File output = new File(directory, "overlay-controls.png");
            assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
        }
        activity.finish();
    }

    @Test public void theFeedButtonsAreLaidOutFromTheLeftInEitherDirection() throws Exception {
        // Every position on these buttons is a pixel worked out from a raw touch and written to
        // leftMargin. A mirrored layout resolves START to RIGHT and then reads rightMargin, which
        // nothing sets, so the saved position was thrown away, a drag moved nothing sideways, and
        // the Not interested button, which differs from the block button only by leftMargin,
        // landed on top of it.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(activity);
        Utils.setActivity(activity);
        android.view.ViewGroup root = activity.findViewById(android.R.id.content);

        // The real attach path, so the params under test are the ones production writes.
        assertEquals("the overlay looks the activity up for itself", activity, Utils.getActivity());
        Method attach = BlockAuthorOverlay.class.getDeclaredMethod("attach", VideoAuthor.class);
        attach.setAccessible(true);
        int before = root.getChildCount();
        attach.invoke(null, new VideoAuthor("1", "sec", "someone", "7712345"));
        assertNotEquals("attach added nothing to the content root", before, root.getChildCount());

        String[] buttons = {"buttonReference", "localHideReference", "soundButtonReference",
                "notInterestedReference"};
        for (String name : buttons) {
            java.lang.reflect.Field held = BlockAuthorOverlay.class.getDeclaredField(name);
            held.setAccessible(true);
            View view = ((java.lang.ref.WeakReference<View>) held.get(null)).get();
            assertNotNull(name + " was never attached", view);

            // Asking the platform what a mirrored layout does with these exact params, rather
            // than laying the root out right-to-left, because a FrameLayout only resolves a
            // direction once it is attached to a real window and off a test that would pass
            // whichever gravity it was given.
            int gravity = ((FrameLayout.LayoutParams) view.getLayoutParams()).gravity;
            int mirrored = Gravity.getAbsoluteGravity(gravity, View.LAYOUT_DIRECTION_RTL)
                    & Gravity.HORIZONTAL_GRAVITY_MASK;
            assertEquals(name + " is mirrored away from the margin that positions it",
                    Gravity.LEFT, mirrored);
        }
    }

    @Test public void placingTheFeedButtonsTwiceOverDoesNotAskForAnotherLayout() throws Exception {
        // placeSoundButton runs from an OnGlobalLayoutListener, which the framework dispatches
        // after layout inside the same traversal. setLayoutParams calls requestLayout whatever
        // it is handed, so writing the same margins back scheduled another traversal, whose
        // layout called this again: the whole content root measured and laid out every frame for
        // as long as the overlay was attached, including on screens where every button is GONE.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(activity);
        Utils.setActivity(activity);
        android.view.ViewGroup root = activity.findViewById(android.R.id.content);

        Method attach = BlockAuthorOverlay.class.getDeclaredMethod("attach", VideoAuthor.class);
        attach.setAccessible(true);
        attach.invoke(null, new VideoAuthor("1", "sec", "someone", "7712345"));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

        Method place = BlockAuthorOverlay.class.getDeclaredMethod(
                "placeSoundButton", View.class, android.view.ViewGroup.class);
        place.setAccessible(true);
        java.lang.reflect.Field held = BlockAuthorOverlay.class.getDeclaredField("buttonReference");
        held.setAccessible(true);
        View button = ((java.lang.ref.WeakReference<View>) held.get(null)).get();
        assertNotNull("attach never created the block button", button);

        // Settle the tree the way a real traversal would, so the flag under test starts clear.
        int spec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        place.invoke(null, button, root);
        root.measure(spec, spec);
        root.layout(0, 0, 1080, 1080);
        assertFalse("the tree did not settle, so this proves nothing", root.isLayoutRequested());

        // Nothing has moved, so this pass must write no margins and ask for no layout.
        place.invoke(null, button, root);
        assertFalse("placing the buttons again asked for another layout, which the layout"
                + " callback would answer by placing them again", root.isLayoutRequested());
    }

    @Test public void theOverlaysFollowTheActivityTheHostRecreated() {
        Activity first = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(first);
        assertSame(first, Utils.getActivity());

        // The host recreates its main activity on a configuration change it does not swallow,
        // and the extension hook runs again for the new one. Keeping the first instance left
        // every overlay attaching to a window nobody was looking at, and isFinishing() reports
        // nothing for a recreated activity because it is destroyed rather than finishing.
        Activity second = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(second);

        assertSame("the overlays would still be drawing on the old window", second, Utils.getActivity());
    }

    @Test public void aSecondUndoBannerKeepsItsOwnSixSeconds() {
        // Block an author and hide one locally inside six seconds: the second banner replaced the
        // first, and the first banner's dismiss was still queued, so it took the second away
        // early and the Undo went with it.
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(activity);
        Utils.setActivity(activity);
        android.view.ViewGroup root = activity.findViewById(android.R.id.content);
        var looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());

        BlockAuthorOverlay.showUndoBanner("first", () -> { });
        looper.idle();
        int withOne = root.getChildCount();
        assertTrue("the first banner was never shown", withOne > 0);

        looper.idleFor(java.time.Duration.ofSeconds(3));
        BlockAuthorOverlay.showUndoBanner("second", () -> { });
        looper.idle();

        looper.idleFor(java.time.Duration.ofSeconds(3));
        assertEquals("the first banner's timer took the second one away", withOne,
                root.getChildCount());

        // And the second banner still goes away on its own time rather than staying forever.
        looper.idleFor(java.time.Duration.ofSeconds(4));
        assertTrue("the banner never went away", root.getChildCount() < withOne);
    }
}
