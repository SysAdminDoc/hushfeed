package app.morphe.extension.tiktok.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * The feed keeps playing behind an open comment sheet, and it starts itself again every time
 * the app comes back. Neither is something the reader asked for, and there is no pause hook in
 * this extension, so both are answered by asking for the audio focus, which is how one app
 * tells another to stop.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class PausePlaybackTest {
    public static class HostActivity extends Activity {
    }

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.PAUSE_ON_COMMENTS.resetToDefault();
        Settings.NO_RESUME_ON_FOREGROUND.resetToDefault();
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        PausePlayback.resetForTests();
    }

    @After public void tearDown() {
        PausePlayback.resetForTests();
        Settings.PAUSE_ON_COMMENTS.resetToDefault();
        Settings.NO_RESUME_ON_FOREGROUND.resetToDefault();
    }

    /** Open the comments and the sound goes; close them and it comes back. */
    @Test public void aCommentSheetTakesTheSoundAndGivesItBackOnClose() {
        Settings.PAUSE_ON_COMMENTS.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            var shadow = Shadows.shadowOf(
                    (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE));
            assertNull("something already held the focus", shadow.getLastAudioFocusRequest());

            View cell = openSheet(activity, root);
            PausePlayback.onCommentCellBound(cell);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertTrue("the sheet did not quieten the feed", PausePlayback.quietenedForTests());
            assertNotNull("the focus was never asked for", shadow.getLastAudioFocusRequest());
            assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                    shadow.getLastAudioFocusRequest().durationHint);

            // A second cell in the same sheet is the list scrolling, not a second sheet.
            PausePlayback.onCommentCellBound(cell);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("scrolling the comments handed the focus back",
                    shadow.getLastAbandonedAudioFocusListener());

            closeSheet(root);
            assertFalse("the feed stayed quiet after the sheet closed",
                    PausePlayback.quietenedForTests());
            assertNotNull("the focus was never handed back",
                    shadow.getLastAbandonedAudioFocusListener());
        }
    }

    /** Off, which is the default, the sheet is none of this feature's business. */
    @Test public void withTheSwitchOffACommentSheetChangesNothing() {
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            var shadow = Shadows.shadowOf(
                    (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE));

            PausePlayback.onCommentCellBound(openSheet(activity, root));
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse(PausePlayback.quietenedForTests());
            assertNull("the switch is off and the focus was taken anyway",
                    shadow.getLastAudioFocusRequest());
        }
    }

    /**
     * Coming back to the app holds the feed until one tap. The tap is taken and nothing else
     * happens with it, which is what "a tap plays" means when there is no pause hook to press.
     */
    @Test public void comingBackHoldsTheFeedUntilATap() {
        Settings.NO_RESUME_ON_FOREGROUND.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            int before = root.getChildCount();

            PausePlayback.setWasAwayForTests(true);
            PausePlayback.onForeground(activity);

            assertTrue("returning did not hold the feed", PausePlayback.quietenedForTests());
            View catcher = PausePlayback.catcherForTests();
            assertNotNull("nothing was waiting for the tap", catcher);
            assertTrue("the catcher does not take a tap", catcher.isClickable());

            catcher.performClick();
            assertFalse("the tap did not start the feed", PausePlayback.quietenedForTests());
            assertNull("the catcher stayed on the feed", PausePlayback.catcherForTests());
            assertEquals("the catcher was left behind", before, root.getChildCount());
        }
    }

    /** A resume that follows no pause is a dialog closing, not the reader coming back. */
    @Test public void aResumeWithoutHavingLeftIsNotAReturn() {
        Settings.NO_RESUME_ON_FOREGROUND.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);

            PausePlayback.onForeground(activity);

            assertFalse("a resume that never left held the feed",
                    PausePlayback.quietenedForTests());
            assertNull(PausePlayback.catcherForTests());
        }
    }

    /** And with the switch off, returning is left exactly as it was. */
    @Test public void withTheSwitchOffReturningChangesNothing() {
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            PausePlayback.setWasAwayForTests(true);
            PausePlayback.onForeground(activity);
            assertFalse(PausePlayback.quietenedForTests());
            assertNull(PausePlayback.catcherForTests());
        }
    }

    /**
     * The tab bar keeps its own tap. A reader who comes back to open their messages should not
     * have to spend a tap on the feed first, which is the same courtesy the session hold owes.
     */
    @Test public void theCatcherLeavesTheTabBarAlone() {
        Settings.NO_RESUME_ON_FOREGROUND.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            PausePlayback.setWasAwayForTests(true);
            PausePlayback.onForeground(activity);

            View catcher = PausePlayback.catcherForTests();
            assertNotNull(catcher);
            var params = (FrameLayout.LayoutParams) catcher.getLayoutParams();
            assertEquals("the catcher does not ask the overlay where the tabs are",
                    app.morphe.extension.tiktok.wellbeing.SessionLockOverlay
                            .navigationHeight(activity, root),
                    params.bottomMargin);
        }
    }

    /** The sheet is what goes away when the comments close, not the cell inside it. */
    @Test public void theSheetIsTheThingThatDetaches() {
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            ViewGroup root = activity.findViewById(android.R.id.content);
            FrameLayout sheet = new FrameLayout(activity);
            FrameLayout list = new FrameLayout(activity);
            View cell = new View(activity);
            list.addView(cell);
            sheet.addView(list);
            root.addView(sheet);

            assertEquals("the walk up stopped somewhere in the middle of the sheet",
                    sheet, PausePlayback.sheetOf(cell));
            assertNull("a cell with nothing above it is not in a sheet",
                    PausePlayback.sheetOf(new View(activity)));
        }
    }

    // ------------------------------------------------------------------------------- fixture

    private static View openSheet(Activity activity, ViewGroup root) {
        FrameLayout sheet = new FrameLayout(activity);
        View cell = new View(activity);
        sheet.addView(cell);
        root.addView(sheet);
        return cell;
    }

    private static void closeSheet(ViewGroup root) {
        root.removeViewAt(root.getChildCount() - 1);
    }
}
