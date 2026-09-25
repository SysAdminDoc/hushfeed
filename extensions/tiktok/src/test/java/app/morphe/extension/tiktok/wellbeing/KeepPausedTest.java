package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.playback.KeepPaused;
import app.morphe.extension.tiktok.playback.PausePlayback;
import app.morphe.extension.tiktok.settings.Settings;

import java.time.Duration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * Keep a paused video paused: the state read as the app goes away, and TikTok's plays answered as
 * it comes back. Here beside the session hold's tests for its fake player.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class KeepPausedTest {
    private Activity activity;
    private SessionPlaybackHoldTest.NativeController player;

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionPlaybackHoldTest.installNativeControls();
        ReflectionHelpers.callStaticMethod(CurrentVideoAuthor.class, "resetForTests");
        ReflectionHelpers.callStaticMethod(KeepPaused.class, "resetForTests");
        ReflectionHelpers.setStaticField(SessionPlaybackHold.class, "current", null);
        Settings.KEEP_PAUSED_ON_RETURN.resetToDefault();
        HookStatus.clear();
        activity = Robolectric.buildActivity(SessionPlaybackHoldTest.HostActivity.class).setup().get();
        player = new SessionPlaybackHoldTest.NativeController();
        player.bind("first");
        player.reportProgress();
    }

    @After public void tearDown() {
        Settings.KEEP_PAUSED_ON_RETURN.resetToDefault();
        ReflectionHelpers.callStaticMethod(KeepPaused.class, "resetForTests");
        ReflectionHelpers.setStaticField(SessionPlaybackHold.class, "current", null);
        ReflectionHelpers.callStaticMethod(CurrentVideoAuthor.class, "resetForTests");
        SessionPlaybackHold.nativeForTests = null;
        HookStatus.clear();
    }

    private void leave() {
        ReflectionHelpers.callStaticMethod(KeepPaused.class, "onLeaving", ClassParameter.from(Activity.class, activity));
    }

    private static void comeBack() {
        ReflectionHelpers.callStaticMethod(KeepPaused.class, "onReturning");
    }

    private static boolean play(String aid) {
        return KeepPaused.refusePlay(new SessionPlaybackHoldTest.Clip(aid));
    }

    @Test public void aVideoTheReaderPausedIsNotPlayedAgainByTheReturn() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        player.manager.playing = false;
        leave();
        comeBack();
        assertTrue("the feed panel's resume", play("first"));
        assertTrue("and the new surface's", play("first"));
        assertFalse("another video still plays", play("second"));
    }

    @Test public void aVideoLeftPlayingStartsAgain() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        leave();
        comeBack();
        assertFalse(play("first"));
    }

    @Test public void theSwitchOffLeavesEveryPlayToTikTok() {
        player.manager.playing = false;
        leave();
        comeBack();
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        assertFalse("nothing was noted on the way out", play("first"));
        leave();
        comeBack();
        Settings.KEEP_PAUSED_ON_RETURN.save(false);
        assertFalse("turned off after the leaving", play("first"));
    }

    /** Robolectric runs sdk 28 here, so this goes through the callbacks older Androids use. */
    @Test public void theAppGoingAwayAndComingBackIsFollowedThroughTheActivityCallbacks() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        ReflectionHelpers.callStaticMethod(PausePlayback.class, "resetForTests");
        try (var controller = Robolectric.buildActivity(SessionPlaybackHoldTest.HostActivity.class).setup()) {
            PausePlayback.install(controller.get());
            player.manager.playing = false;
            controller.pause();
            controller.resume();
            assertTrue(play("first"));
        } finally {
            ReflectionHelpers.callStaticMethod(PausePlayback.class, "resetForTests");
        }
    }

    @Test public void onceThePausedVideoPlaysAgainItsPlaysGoThrough() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        player.manager.playing = false;
        leave();
        comeBack();
        assertTrue(play("first"));
        player.manager.playing = true;
        assertFalse("the reader started it", play("first"));
        player.manager.playing = false;
        assertFalse("and the record is spent", play("first"));
    }

    @Test public void onlyThePlaysThatComeWithTheReturnAreTurnedDown() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        player.manager.playing = false;
        leave();
        assertFalse("still away", play("first"));
        leave();
        comeBack();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3100));
        assertFalse("long after the return", play("first"));
        assertFalse("and the record is spent", play("first"));
    }

    /** The player has moved to another video since it last reported, so nobody knows the state. */
    @Test public void aPlayerThatCannotSayIsNotTakenForPaused() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        player.manager.playing = false;
        player.current = new SessionPlaybackHoldTest.Clip("other");
        leave();
        comeBack();
        assertFalse(play("first"));
    }

    @Test public void aVideoWithNoPlayerToAskIsLeftToTikTok() {
        Settings.KEEP_PAUSED_ON_RETURN.save(true);
        ReflectionHelpers.setStaticField(SessionPlaybackHold.class, "current", null);
        leave();
        comeBack();
        assertFalse(play("first"));
        assertFalse("nothing named", KeepPaused.refusePlay(null));
    }

    @Test public void theExportCountsThePlayAndTheVideoKeptPaused() {
        int[] found = new int[1];
        HookStatus.setLineWriter((family, count, missing, truncated, firstMiss) -> {
            if ("keep paused".equals(family)) found[0] = count;
            return family;
        });
        try {
            Settings.KEEP_PAUSED_ON_RETURN.save(true);
            play("first");
            HookStatus.report();
            assertEquals("the play alone", 1, found[0]);
            player.manager.playing = false;
            leave();
            comeBack();
            play("first");
            HookStatus.report();
            assertEquals("and the video kept paused", 2, found[0]);
        } finally {
            HookStatus.setLineWriter(null);
        }
    }
}
