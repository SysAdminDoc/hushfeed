package app.morphe.extension.tiktok.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
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

    /**
     * A comment sheet in a window of its own, which is what a bottom sheet dialog is. Its root
     * view detaches when it is dismissed, and that is the signal this feature is built on.
     */
    @Test public void aCommentSheetInItsOwnWindowTakesTheSoundAndGivesItBackOnClose() {
        Settings.PAUSE_ON_COMMENTS.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            var shadow = Shadows.shadowOf(
                    (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE));
            assertNull("something already held the focus", shadow.getLastAudioFocusRequest());

            Dialog sheet = openSheet(activity);
            View cell = cellIn(sheet);
            PausePlayback.onCommentCellBound(cell);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertTrue("the sheet did not quieten the feed", PausePlayback.quietenedForTests());
            assertNotNull("the focus was never asked for", shadow.getLastAudioFocusRequest());
            assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                    shadow.getLastAudioFocusRequest().durationHint);

            // Another cell in the same sheet is the list scrolling, not a second sheet.
            PausePlayback.onCommentCellBound(cell);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("scrolling the comments handed the focus back",
                    shadow.getLastAbandonedAudioFocusListener());

            sheet.dismiss();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("the feed stayed quiet after the sheet closed",
                    PausePlayback.quietenedForTests());
            assertNotNull("the focus was never handed back",
                    shadow.getLastAbandonedAudioFocusListener());
        }
    }

    /**
     * And a sheet drawn into the activity itself, which shares the activity's root view. There
     * is no signal here that says when it closes, so this does nothing rather than taking the
     * sound and never giving it back. The failure worth having is the one that leaves the app
     * as it was.
     */
    @Test public void aSheetDrawnIntoTheActivityIsLeftAlone() {
        Settings.PAUSE_ON_COMMENTS.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            ViewGroup root = activity.findViewById(android.R.id.content);
            var shadow = Shadows.shadowOf(
                    (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE));

            FrameLayout inActivitySheet = new FrameLayout(activity);
            View cell = new View(activity);
            inActivitySheet.addView(cell);
            root.addView(inActivitySheet);

            PausePlayback.onCommentCellBound(cell);
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertNull("the walk up found a sheet where there is no window of its own",
                    PausePlayback.sheetWindowOf(cell));
            assertFalse(PausePlayback.quietenedForTests());
            assertNull("the sound was taken with nothing to give it back",
                    shadow.getLastAudioFocusRequest());
        }
    }

    /** A sheet closing underneath a second one does not hand the sound back. */
    @Test public void aClosingSheetLeavesTheSoundWithTheOneStillOpen() {
        Settings.PAUSE_ON_COMMENTS.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);

            Dialog first = openSheet(activity);
            PausePlayback.onCommentCellBound(cellIn(first));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Dialog second = openSheet(activity);
            PausePlayback.onCommentCellBound(cellIn(second));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(PausePlayback.quietenedForTests());

            first.dismiss();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("the sheet underneath handed back the sound the top one is holding",
                    PausePlayback.quietenedForTests());

            second.dismiss();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(PausePlayback.quietenedForTests());
        }
    }

    /**
     * The walk up waits a post, because a cell has no parent during its bind. A sheet that
     * closes in that gap is a sheet nothing will ever see detach again.
     */
    @Test public void aSheetThatClosesBeforeThePostRunsTakesNothing() {
        Settings.PAUSE_ON_COMMENTS.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);

            Dialog sheet = openSheet(activity);
            View cell = cellIn(sheet);
            PausePlayback.onCommentCellBound(cell);
            sheet.dismiss();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse("the sound was taken for a sheet that had already gone",
                    PausePlayback.quietenedForTests());
        }
    }

    /** Off, which is the default, the sheet is none of this feature's business. */
    @Test public void withTheSwitchOffACommentSheetChangesNothing() {
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            var shadow = Shadows.shadowOf(
                    (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE));

            PausePlayback.onCommentCellBound(cellIn(openSheet(activity)));
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

    /** A resume that follows no stop is a dialog closing, not the reader coming back. */
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

    /**
     * And the app going away hands everything back, whatever state it was in. A sheet dismissed
     * while nobody was looking, or a catcher on an activity that is being torn down, would
     * otherwise leave the feed quiet for the life of the process.
     */
    @Test public void theAppGoingAwayHandsBackTheSoundAndTheCatcher() {
        Settings.NO_RESUME_ON_FOREGROUND.save(true);
        try (var owner = Robolectric.buildActivity(HostActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            PausePlayback.setWasAwayForTests(true);
            PausePlayback.onForeground(activity);
            assertTrue(PausePlayback.quietenedForTests());
            assertNotNull(PausePlayback.catcherForTests());

            PausePlayback.onBackground();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse("the feed stayed quiet after the app went away",
                    PausePlayback.quietenedForTests());
            assertNull("the catcher outlived the screen it was on",
                    PausePlayback.catcherForTests());
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

    // ------------------------------------------------------------------------------- fixture

    /** A comment sheet the way TikTok's is: a window of its own over the feed. */
    private static Dialog openSheet(Activity activity) {
        Dialog sheet = new Dialog(activity);
        FrameLayout content = new FrameLayout(activity);
        content.addView(new View(activity));
        sheet.setContentView(content);
        sheet.show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return sheet;
    }

    private static View cellIn(Dialog sheet) {
        ViewGroup content = sheet.findViewById(android.R.id.content);
        assertNotNull("the sheet has no content", content);
        View cell = firstLeaf(content);
        assertNotNull("the sheet has no cell in it", cell);
        return cell;
    }

    private static View firstLeaf(View view) {
        if (!(view instanceof ViewGroup)) return view;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = firstLeaf(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }
}
