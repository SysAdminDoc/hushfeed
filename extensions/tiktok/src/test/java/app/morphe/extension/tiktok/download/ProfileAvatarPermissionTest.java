package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

/** The installed avatar action must explain a consumed press that cannot save anything. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, qualifiers = "en")
public class ProfileAvatarPermissionTest {
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<MediaJobScheduler.JobHandle> held = new ArrayList<>();
    private ActivityController<Activity> owner;
    private View avatar;

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        SettingsStatus.advancedDownloadsEnabled = true;
        Settings.SAVE_PROFILE_PICTURE.save(true);
        owner = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = owner.get();
        LinearLayout header = new LinearLayout(activity);
        TextView handle = new TextView(activity);
        handle.setText("@dancer");
        header.addView(handle);
        avatar = new View(activity);
        header.addView(avatar);
        activity.setContentView(header);
        ProfileAvatarSaver.recordProfileResponse(new ProfileResponse());
        ProfileAvatarSaver.attachAvatar(avatar);

        // Occupy the real workers so a wrongly submitted download stays observable in the
        // queue instead of failing quickly on the network and making the assertion pass.
        assertEquals(0, MediaJobScheduler.queuedJobs());
        assertEquals(0, MediaJobScheduler.runningJobs());
        CountDownLatch started = new CountDownLatch(MediaJobScheduler.MAX_RUNNING_JOBS);
        for (int index = 0; index < MediaJobScheduler.MAX_RUNNING_JOBS; index++) {
            MediaJobScheduler.JobHandle job = MediaJobScheduler.submit("permission test hold", () -> {
                started.countDown();
                try {
                    release.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, null);
            assertNotNull(job);
            held.add(job);
        }
        assertTrue("the media workers never started", started.await(5, TimeUnit.SECONDS));
        ShadowToast.reset();
    }

    @After public void tearDown() throws Exception {
        // Cancel queued avatar work before freeing any worker. The allowed control reaches
        // real scheduling, but never fetches or publishes media during this permission test.
        Map<?, MediaJobScheduler.JobHandle> jobs = ReflectionHelpers.getStaticField(
                MediaJobScheduler.class, "HANDLES");
        for (MediaJobScheduler.JobHandle job : new ArrayList<>(jobs.values())) {
            if (job.label.equals("profile picture")) job.cancel();
        }
        release.countDown();
        for (MediaJobScheduler.JobHandle job : held) job.cancel();
        for (int wait = 0; wait < 250 && MediaJobScheduler.runningJobs() != 0; wait++) {
            Thread.sleep(20);
        }
        Settings.SAVE_PROFILE_PICTURE.save(false);
        SettingsStatus.advancedDownloadsEnabled = false;
        if (owner != null) owner.close();
        assertEquals("a test media worker was left running", 0, MediaJobScheduler.runningJobs());
        assertEquals("a test download was left queued", 0, MediaJobScheduler.queuedJobs());
    }

    @Test public void deniedStorageExplainsThePressAndGrantQueuesTheSameAvatar() {
        assertDeniedThenGranted();
    }

    @Test @Config(sdk = 23)
    public void androidSixAlsoExplainsTheDeniedAvatarPress() {
        assertDeniedThenGranted();
    }

    @Test @Config(sdk = 29)
    public void scopedStorageDoesNotRequireTheLegacyPermission() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertTrue(avatar.performLongClick());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, ShadowToast.shownToastCount());
        assertEquals("the scoped-storage save was not submitted", 1, MediaJobScheduler.queuedJobs());
    }

    private void assertDeniedThenGranted() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertEquals(PackageManager.PERMISSION_DENIED,
                avatar.getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertTrue("the installed avatar listener did not consume the press", avatar.performLongClick());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Storage permission is needed to save a profile picture", ShadowToast.getTextOfLatestToast());
        assertEquals(1, ShadowToast.shownToastCount());
        assertEquals("denied storage still submitted media work", 0, MediaJobScheduler.queuedJobs());
        assertEquals(MediaJobScheduler.MAX_RUNNING_JOBS, MediaJobScheduler.runningJobs());

        ShadowToast.reset();
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertTrue(avatar.performLongClick());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("granting permission still reported an error", 0, ShadowToast.shownToastCount());
        assertEquals("the same avatar did not reach scheduling after permission was granted",
                1, MediaJobScheduler.queuedJobs());
    }

    public static final class ProfileResponse {
        public final Account user = new Account();
    }

    public static final class Account {
        public final String uniqueId = "dancer";
        public final Address avatar300 = new Address();
    }

    public static final class Address {
        public final List<String> urlList = List.of("https://example.com/avatar.jpg");
    }
}
