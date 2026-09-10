package app.morphe.extension.tiktok.wellbeing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.wellbeing.SessionPlaybackHoldTest.Clip;
import app.morphe.extension.tiktok.wellbeing.SessionPlaybackHoldTest.HostActivity;
import app.morphe.extension.tiktok.wellbeing.SessionPlaybackHoldTest.NativeController;
import app.morphe.extension.tiktok.wellbeing.SessionPlaybackHoldTest.NativeManager;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/** Exercises the native activity focus calls around the real daily-hold and expiry callers. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class SessionNativeFocusTest {
    private final AtomicLong now = new AtomicLong(1_788_800_400_000L);

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionBudget.setClockForTests(now::get);
        clearHold();
    }

    @After public void tearDown() throws Exception {
        clearHold();
        SessionBudget.setClockForTests(null);
    }

    @Test public void nativeGrantAfterActivityReplacementLetsTheHeldVideoResume() throws Exception {
        for (int result : new int[] {AudioManager.AUDIOFOCUS_REQUEST_FAILED,
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED}) {
            try (Hold hold = new Hold()) {
                hold.replaceActivity();
                // A denied native request must not undo an external owner's existing loss.
                hold.quiet.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
                hold.client().request(result);
                hold.expire();
                assertEquals("release ignored the actual native request result", result ==
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? 1 : 0, hold.player.manager.resumes);
            }
        }
    }

    @Test public void newerNativeGrantSurvivesAnOlderListenerLoss() throws Exception {
        try (Hold hold = new Hold()) {
            NativeFocusClient older = hold.client();
            older.request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            hold.client().request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            // Android can deliver the previous client's loss after the new request returns.
            older.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
            hold.expire();
            assertEquals("an older listener's loss cleared the current native grant", 1,
                    hold.player.manager.resumes);
        }
    }

    @Test public void nativeGainRestoresAnOlderListenerAfterNewerAbandon() throws Exception {
        try (Hold hold = new Hold()) {
            NativeFocusClient older = hold.client();
            older.request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            NativeFocusClient newer = hold.client();
            newer.request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            newer.abandon();
            older.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
            hold.expire();
            assertEquals("the restored native client could not hand its paused video back", 1,
                    hold.player.manager.resumes);
        }
    }

    @Test public void externalLossRevokesTheObservedNativeGrant() throws Exception {
        for (int loss : new int[] {AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}) {
            try (Hold hold = new Hold()) {
                NativeFocusClient nativeOwner = hold.client();
                nativeOwner.request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                nativeOwner.listener.onAudioFocusChange(loss);
                hold.expire();
                assertEquals("release stole playback after the native client's loss " + loss,
                        0, hold.player.manager.resumes);
            }
        }
    }

    @Test public void abandonRevokesTheObservedNativeGrantAndRejectsALateGain() throws Exception {
        try (Hold hold = new Hold()) {
            NativeFocusClient nativeOwner = hold.client();
            nativeOwner.request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            nativeOwner.abandon();
            nativeOwner.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
            hold.expire();
            assertEquals("an abandoned native client revived playback permission", 0,
                    hold.player.manager.resumes);
        }
    }

    @Test public void nativeGrantKeepsTheCurrentPlayerAndForegroundGuards() throws Exception {
        for (int changed = 0; changed < 5; changed++) {
            try (Hold hold = new Hold()) {
                hold.client().request(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                NativeManager paused = hold.player.manager;
                if (changed == 0) hold.player.current = new Clip("different-cell");
                else if (changed == 1) hold.player.manager = new NativeManager();
                else if (changed == 2) {
                    NativeController replacement = new NativeController();
                    replacement.current = new Clip("held");
                    replacement.manager.playing = false;
                    replacement.manager.paused = true;
                    replacement.reportProgress();
                } else if (changed == 3) hold.home.setSelected(false);
                else hold.activity.windowFocusChanged(false);
                hold.expire();
                assertEquals("native focus bypassed the owned player/feed check " + changed,
                        0, paused.resumes);
            }
        }
    }

    /** Mirrors 0q3r's actual request/result and abandon calls, including its listener identity. */
    private static final class NativeFocusClient {
        final AudioManager audio;
        final NativeFocusListener listener = new NativeFocusListener();

        NativeFocusClient(AudioManager audio) { this.audio = audio; }

        @SuppressWarnings("deprecation")
        void request(int response) {
            var shadow = Shadows.shadowOf(audio);
            var previous = shadow.getLastAudioFocusRequest();
            shadow.setNextFocusRequestResponse(response);
            int result = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            SessionPlaybackHold.onNativeFocusRequestResult(listener, result);
            shadow.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && previous != null
                    && previous.listener != listener) {
                previous.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
            }
        }

        @SuppressWarnings("deprecation")
        void abandon() {
            SessionPlaybackHold.onNativeFocusAbandon(listener);
            audio.abandonAudioFocus(listener);
        }
    }

    private static final class NativeFocusListener implements AudioManager.OnAudioFocusChangeListener {
        @Override public void onAudioFocusChange(int change) {
            SessionPlaybackHold.onNativeFocusChange(this, change);
        }
    }

    private final class Hold implements AutoCloseable {
        final List<ActivityController<HostActivity>> activities = new ArrayList<>();
        final List<NativeFocusClient> clients = new ArrayList<>();
        final NativeController player = new NativeController();
        final AudioManager audio;
        final AudioManager.OnAudioFocusChangeListener quiet;
        ActivityController<HostActivity> activity;
        View home;

        Hold() throws Exception {
            clearHold();
            Settings.SESSION_BUDGET_VIDEOS.save(2);
            Settings.SESSION_BUDGET_LOCK_MINUTES.save(2);
            audio = (AudioManager) RuntimeEnvironment.getApplication()
                    .getSystemService(Activity.AUDIO_SERVICE);
            Shadows.shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            openActivity();
            player.bind("first");
            player.reportProgress();
            player.bind("held");
            player.reportProgress();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("the real item/progress path did not start the hold", SessionBudget.isLocked());
            assertEquals("the hold did not own a native pause", 1, player.manager.pauses);
            assertTrue(player.manager.isPaused());
            var request = Shadows.shadowOf(audio).getLastAudioFocusRequest();
            assertNotNull("the hold never requested audio focus", request);
            quiet = request.listener;
        }

        private void openActivity() {
            activity = Robolectric.buildActivity(HostActivity.class).setup().visible();
            activities.add(activity);
            activity.windowFocusChanged(true);
            Utils.setActivity(activity.get());
            home = new View(activity.get());
            ((ViewGroup) activity.get().findViewById(android.R.id.content)).addView(home);
            home.setSelected(true);
            ReflectionHelpers.setStaticField(FeedVisibility.class, "homeTabReference",
                    new WeakReference<>(home));
        }

        void replaceActivity() {
            activity.windowFocusChanged(false);
            activity.pause().stop();
            openActivity();
            Utils.setContext(activity.get());
            SessionLockOverlay.sync();
        }

        NativeFocusClient client() {
            NativeFocusClient client = new NativeFocusClient(audio);
            clients.add(client);
            return client;
        }

        void expire() {
            assertFalse("fixture failed to suspend the hold's own focus request",
                    ReflectionHelpers.<Boolean>getStaticField(SessionLockOverlay.class, "focusGranted"));
            now.addAndGet(121_000);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            assertFalse("the hold did not expire through its timer", SessionBudget.isLocked());
        }

        @Override public void close() throws Exception {
            for (NativeFocusClient client : clients) client.abandon();
            clearHold();
            for (int i = activities.size() - 1; i >= 0; i--) activities.get(i).close();
        }
    }

    private static void clearHold() throws Exception {
        Utils.setActivity(null);
        Utils.setContext(RuntimeEnvironment.getApplication());
        SessionBudget.awaitWritesForTests();
        Settings.SESSION_BUDGET_VIDEOS.resetToDefault();
        Settings.SESSION_BUDGET_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_LOCK_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_LOCK.resetToDefault();
        Settings.SESSION_BUDGET_PASSES_PER_DAY.resetToDefault();
        Settings.SESSION_BUDGET_NOTICE_MINUTES.resetToDefault();
        Settings.SESSION_BUDGET_STATE.resetToDefault();
        SessionBudget.resetForTests();
        // Robolectric can clear queued messages while preserving the static timer flag.
        // Let the real callback observe the cleared budget and finish its own lifecycle.
        Runnable tick = ReflectionHelpers.getStaticField(SessionLockOverlay.class, "TICK");
        android.os.Handler handler = ReflectionHelpers.getStaticField(SessionLockOverlay.class, "MAIN");
        handler.removeCallbacks(tick);
        tick.run();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        ReflectionHelpers.callStaticMethod(CurrentVideoAuthor.class, "resetForTests");
        ReflectionHelpers.setStaticField(FeedVisibility.class, "homeTabReference",
                new WeakReference<View>(null));
    }
}
