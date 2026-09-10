package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

/** Exercises the slot-three native gesture callbacks, which use an OnClickListener for a hold. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28, qualifiers = "en")
public class ProfileAvatarGestureTest {
    private ActivityController<Activity> activityOwner;
    private View avatar;
    private TextView handle;
    private Object previousProfile;
    private boolean previousSetting, previousStatus;

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        previousSetting = Settings.SAVE_PROFILE_PICTURE.get();
        previousStatus = SettingsStatus.advancedDownloadsEnabled;
        previousProfile = ProfileAvatarSaver.recordedProfileUser();
        Settings.SAVE_PROFILE_PICTURE.save(true);
        SettingsStatus.advancedDownloadsEnabled = true;
        activityOwner = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = activityOwner.get();
        LinearLayout header = new LinearLayout(activity);
        handle = new TextView(activity);
        header.addView(handle);
        avatar = new View(activity);
        header.addView(avatar);
        activity.setContentView(header);
        // A modern ProfileUser response need not invoke UserResponse.getUser at all.
        ProfileAvatarSaver.recordProfileResponse(new LegacyResponse(new User("previous-profile")));
        Shadows.shadowOf(RuntimeEnvironment.getApplication())
                .denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        ShadowToast.reset();
    }

    @After public void tearDown() {
        Settings.SAVE_PROFILE_PICTURE.save(previousSetting);
        SettingsStatus.advancedDownloadsEnabled = previousStatus;
        ReflectionHelpers.setStaticField(ProfileAvatarSaver.class, "profileUser", previousProfile);
        if (activityOwner != null) activityOwner.close();
        assertEquals("an avatar test queued a network download", 0, MediaJobScheduler.queuedJobs());
        assertEquals("an avatar test started a network download", 0, MediaJobScheduler.runningJobs());
    }

    @Test public void otherProfileHoldUsesCurrentOwnerInsteadOfLegacyResponse() {
        OtherProfileOwner owner = new OtherProfileOwner("nasa");
        NativeSlotThree gesture = install(owner);

        pressAndAssertSave(gesture, owner);

        assertEquals("the legacy response must not be rewritten to simulate a modern fetch",
                "previous-profile", ((User) ProfileAvatarSaver.recordedProfileUser()).uniqueId);
    }

    @Test public void ownProfileHoldUsesCurrentAccountOwner() {
        MyProfileOwner owner = new MyProfileOwner("my-account");
        pressAndAssertSave(install(owner), owner);
    }

    @Test public void reusedGestureReadsTheProfileNowOwnedByItsComponent() {
        OtherProfileOwner owner = new OtherProfileOwner("nasa");
        NativeSlotThree gesture = install(owner);
        pressAndAssertSave(gesture, owner);

        owner.user = new User("another-profile");
        handle.setText("@another-profile");
        ShadowToast.reset();
        pressAndAssertSave(gesture, owner);

        assertEquals("the second hold used a cached profile instead of the current component", 2, owner.reads);
    }

    @Test public void disabledSettingKeepsTheNativeLongPress() {
        Settings.SAVE_PROFILE_PICTURE.save(false);
        assertNativeGestureRetained();
    }

    @Test public void absentDownloadsPatchKeepsTheNativeLongPress() {
        SettingsStatus.advancedDownloadsEnabled = false;
        assertNativeGestureRetained();
    }

    private void assertNativeGestureRetained() {
        OtherProfileOwner owner = new OtherProfileOwner("nasa");
        NativeSlotThree gesture = install(owner);
        assertTrue(avatar.performLongClick());
        idle();
        assertEquals(1, gesture.nativeActions);
        assertEquals("an inactive save still resolved the native profile", 0, owner.reads);
        assertEquals(0, ShadowToast.shownToastCount());
    }

    private NativeSlotThree install(ProfileOwner owner) {
        handle.setText("@" + owner.user.uniqueId);
        NativeSlotThree gesture = new NativeSlotThree();
        // Native tag43 (own profile) and tag46 (other profile) both carry this owner in l0.
        gesture.add(view -> {
            if (!ProfileAvatarSaver.onAvatarLongPress(owner, view)) gesture.nativeActions++;
        });
        avatar.setOnLongClickListener(view -> {
            gesture.dispatch(view);
            return true;
        });
        return gesture;
    }

    private void pressAndAssertSave(NativeSlotThree gesture, ProfileOwner owner) {
        assertTrue(avatar.performLongClick());
        idle();
        assertEquals("the enabled avatar hold still ran the native action", 0, gesture.nativeActions);
        // This requires both the current identity and a usable avatar URL. Storage denial then
        // ends the real save path before media work, so the test never downloads anything.
        assertEquals("Storage permission is needed to save a profile picture", ShadowToast.getTextOfLatestToast());
        assertTrue("the gesture did not read its owner's current user", owner.reads > 0);
    }

    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

    private static final class NativeSlotThree {
        final Set<View.OnClickListener> listeners = new LinkedHashSet<>();
        int nativeActions;
        void add(View.OnClickListener listener) { listeners.add(listener); }
        void dispatch(View view) { for (View.OnClickListener listener : listeners) listener.onClick(view); }
    }

    public static class ProfileOwner {
        User user;
        int reads;
        ProfileOwner(String handle) { user = new User(handle); }
        public User bq() { reads++; return user; }
    }
    public static final class OtherProfileOwner extends ProfileOwner {
        OtherProfileOwner(String handle) { super(handle); }
    }
    public static final class MyProfileOwner extends ProfileOwner {
        MyProfileOwner(String handle) { super(handle); }
    }
    public static final class LegacyResponse {
        public final User user;
        LegacyResponse(User user) { this.user = user; }
    }
    public static final class User {
        public final String uniqueId;
        public final Address avatar300 = new Address();
        User(String handle) { uniqueId = handle; }
    }
    public static final class Address {
        public final List<String> urlList = List.of("https://example.com/avatar.jpg");
    }
}
