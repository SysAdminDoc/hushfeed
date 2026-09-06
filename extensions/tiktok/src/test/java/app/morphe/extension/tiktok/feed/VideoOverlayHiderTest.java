package app.morphe.extension.tiktok.feed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

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
    /** Stands in for the clear display event TikTok posts. */
    public static final class ClearEvent {
        public boolean LIZ;
        public int LIZIZ;

        ClearEvent(boolean clear, int type) {
            LIZ = clear;
            LIZIZ = type;
        }
    }

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
    public void anInvisibleViewComesBackInvisible() {
        // TikTok keeps the music disc invisible on a photo post to hold its space.
        View disc = new View(context);
        disc.setVisibility(View.INVISIBLE);

        VideoOverlayHider.setHidden(disc, true);
        assertEquals(View.GONE, disc.getVisibility());
        VideoOverlayHider.setHidden(disc, false);
        assertEquals(View.INVISIBLE, disc.getVisibility());
    }

    @Test
    public void aPassHidesEveryCellsCaptionAndColumnAndPutsThemBack() {
        // The feed keeps the previous and next cells inflated beside the one on screen,
        // each with its own caption and action column under the same ids.
        int captionId = 0x7f0a0001;
        int columnId = 0x7f0a0002;
        VideoOverlayHider.resolveForTests("desc", captionId);
        VideoOverlayHider.resolveForTests("kzj", columnId);
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            LinearLayout root = new LinearLayout(activity);
            View[] captions = new View[3];
            View[] columns = new View[3];
            for (int i = 0; i < 3; i++) {
                FrameLayout cell = new FrameLayout(activity);
                captions[i] = new View(activity);
                captions[i].setId(captionId);
                columns[i] = new View(activity);
                columns[i].setId(columnId);
                cell.addView(new View(activity));
                cell.addView(captions[i]);
                cell.addView(columns[i]);
                root.addView(cell);
            }
            activity.setContentView(root);

            Settings.HIDE_FEED_CAPTION.save(true);
            Settings.HIDE_FEED_ACTION_BAR.save(false);
            VideoOverlayHider.applyTo(activity);
            for (int i = 0; i < 3; i++) {
                assertEquals("caption " + i, View.GONE, captions[i].getVisibility());
                assertEquals("column " + i, View.VISIBLE, columns[i].getVisibility());
            }

            Settings.HIDE_FEED_CAPTION.save(false);
            Settings.HIDE_FEED_ACTION_BAR.save(true);
            VideoOverlayHider.applyTo(activity);
            for (int i = 0; i < 3; i++) {
                assertEquals("caption " + i, View.VISIBLE, captions[i].getVisibility());
                assertEquals("column " + i, View.GONE, columns[i].getVisibility());
            }

            Settings.HIDE_FEED_ACTION_BAR.save(false);
            VideoOverlayHider.applyTo(activity);
            for (int i = 0; i < 3; i++) {
                assertEquals("column " + i, View.VISIBLE, columns[i].getVisibility());
            }
        } finally {
            Settings.HIDE_FEED_CAPTION.save(false);
            Settings.HIDE_FEED_ACTION_BAR.save(false);
        }
        assertEquals(0, VideoOverlayHider.viewsWithId(new View(context), 0).size());
        assertEquals(0, VideoOverlayHider.viewsWithId(null, captionId).size());
    }

    @Test
    public void aPeekInsideTheGraceWindowIsNotHiddenAgain() {
        // On Android 11 and up a swipe from the top shows the bar transiently and the
        // insets report it visible, so the layout pass must not answer with another hide.
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            assertTrue(VideoOverlayHider.rehideAllowed(android.os.SystemClock.uptimeMillis()));

            VideoOverlayHider.setStatusBarHidden(activity, true);
            // Robolectric's clock moves a few milliseconds during the hide, so the two
            // probes sit well inside and just past the window rather than on its edge.
            long hiddenAt = android.os.SystemClock.uptimeMillis();
            assertFalse(VideoOverlayHider.rehideAllowed(hiddenAt + VideoOverlayHider.STATUS_BAR_PEEK_MS / 2));
            assertTrue(VideoOverlayHider.rehideAllowed(hiddenAt + VideoOverlayHider.STATUS_BAR_PEEK_MS));

            VideoOverlayHider.setStatusBarHidden(activity, false);
            assertTrue(VideoOverlayHider.rehideAllowed(hiddenAt + 1));
        }
    }

    @Test
    public void eachRailButtonHasItsOwnSwitchInEveryCell() {
        // The column keeps its six buttons under fixed ids, and the feed keeps the cells on
        // either side inflated with the same ones.
        String[] names = {"hvo", "fws", "ehl", "hu9", "p2l", "v9o"};
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = 0x7f0a0100 + i;
            VideoOverlayHider.resolveForTests(names[i], ids[i]);
        }
        var switches = new app.morphe.extension.shared.settings.BooleanSetting[]{
                Settings.HIDE_RAIL_FOLLOW, Settings.HIDE_RAIL_LIKE, Settings.HIDE_RAIL_COMMENTS,
                Settings.HIDE_RAIL_FAVOURITE, Settings.HIDE_RAIL_MUSIC, Settings.HIDE_RAIL_SHARE,
        };

        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            LinearLayout root = new LinearLayout(activity);
            View[][] buttons = new View[2][names.length];
            for (int cell = 0; cell < 2; cell++) {
                LinearLayout column = new LinearLayout(activity);
                for (int i = 0; i < names.length; i++) {
                    buttons[cell][i] = new View(activity);
                    buttons[cell][i].setId(ids[i]);
                    column.addView(buttons[cell][i]);
                }
                root.addView(column);
            }
            activity.setContentView(root);

            for (var setting : switches) setting.save(false);

            for (int target = 0; target < names.length; target++) {
                switches[target].save(true);
                VideoOverlayHider.applyTo(activity);
                for (int cell = 0; cell < 2; cell++) {
                    for (int i = 0; i < names.length; i++) {
                        assertEquals(names[i] + " in cell " + cell,
                                i == target ? View.GONE : View.VISIBLE,
                                buttons[cell][i].getVisibility());
                    }
                }
                switches[target].save(false);
            }

            // Everything off puts every button back.
            VideoOverlayHider.applyTo(activity);
            for (int cell = 0; cell < 2; cell++) {
                for (int i = 0; i < names.length; i++) {
                    assertEquals(names[i], View.VISIBLE, buttons[cell][i].getVisibility());
                }
            }
        }
    }

    @Test
    public void theCountsGoWithoutTheButtons() {
        String[] counts = {"fwu", "ecq", "ht9", "v5x"};
        int[] countIds = new int[counts.length];
        for (int i = 0; i < counts.length; i++) {
            countIds[i] = 0x7f0a0200 + i;
            VideoOverlayHider.resolveForTests(counts[i], countIds[i]);
        }
        int likeButtonId = 0x7f0a0210;
        VideoOverlayHider.resolveForTests("fws", likeButtonId);

        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            LinearLayout root = new LinearLayout(activity);
            View likeButton = new View(activity);
            likeButton.setId(likeButtonId);
            root.addView(likeButton);
            View[] rows = new View[counts.length];
            for (int i = 0; i < counts.length; i++) {
                rows[i] = new View(activity);
                rows[i].setId(countIds[i]);
                root.addView(rows[i]);
            }
            activity.setContentView(root);

            Settings.HIDE_RAIL_LIKE.save(false);
            Settings.HIDE_RAIL_COUNTS.save(true);
            VideoOverlayHider.applyTo(activity);
            for (int i = 0; i < counts.length; i++) {
                assertEquals(counts[i], View.GONE, rows[i].getVisibility());
            }
            assertEquals("the button itself stays", View.VISIBLE, likeButton.getVisibility());

            Settings.HIDE_RAIL_COUNTS.save(false);
            VideoOverlayHider.applyTo(activity);
            for (int i = 0; i < counts.length; i++) {
                assertEquals(counts[i], View.VISIBLE, rows[i].getVisibility());
            }
        }
    }

    @Test
    public void clearDisplayKeepsTheTabStripAwayUntilItEnds() {
        int tabStripId = 0x7f0a0011;
        VideoOverlayHider.resolveForTests("twc", tabStripId);
        try (var controller = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = controller.get();
            Utils.setContext(activity);
            LinearLayout root = new LinearLayout(activity);
            View tabStrip = new View(activity);
            tabStrip.setId(tabStripId);
            root.addView(tabStrip);
            activity.setContentView(root);

            // The hider follows the live clear display state, not the stored setting: the
            // automatic path never writes that one. AutomaticClearDisplayTest covers the
            // automatic transition itself.
            Settings.CLEAR_DISPLAY.save(false);
            app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch
                    .rememberClearDisplayEvent(new ClearEvent(true, 1));

            // The two disagree on purpose: the live state says the controls are hidden, the
            // stored setting says nothing. Reading the setting here would leave the strip up.
            Settings.CLEAR_DISPLAY.save(false);

            VideoOverlayHider.applyTo(activity);
            assertEquals(View.GONE, tabStrip.getVisibility());

            // TikTok puts the strip back on the first swipe; the next layout pass takes it
            // away again, which is the whole point of the mode.
            tabStrip.setVisibility(View.VISIBLE);
            VideoOverlayHider.applyTo(activity);
            assertEquals(View.GONE, tabStrip.getVisibility());

            // The tap that leaves clear display brings it back.
            app.morphe.extension.tiktok.cleardisplay.RememberClearDisplayPatch
                    .rememberClearDisplayEvent(new ClearEvent(false, 1));
            VideoOverlayHider.applyTo(activity);
            assertEquals(View.VISIBLE, tabStrip.getVisibility());
        }
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
