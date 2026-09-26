package app.morphe.extension.tiktok.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.view.ViewGroup;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSensor;

import java.util.List;

/**
 * The answers TikTok gets back from the privacy switches. Every intercept blocks while its
 * switch is on and hands the call through while it is off; the dot follows the counts and the
 * switch; and the Privacy page is where the switches live.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class PrivacySwitchesTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
    }

    @After public void tearDown() {
        for (var setting : new app.morphe.extension.shared.settings.BooleanSetting[]{
                Settings.BLOCK_CONTACT_LIST, Settings.BLOCK_INSTALLED_APPS, Settings.BLOCK_LOCATION,
                Settings.BLOCK_CLIPBOARD_READS, Settings.BLOCK_MOTION_SENSORS,
                Settings.BLOCK_WEBVIEW_JS_INTERFACES, Settings.CAMERA_MIC_INDICATOR}) {
            setting.save(setting.defaultValue);
        }
        CameraMicIndicator.resetForTests();
        SettingsStatus.contactListBlockerEnabled = false;
        SettingsStatus.installedAppsBlockerEnabled = false;
        SettingsStatus.locationGovernorEnabled = false;
        SettingsStatus.devicePrivacyGuardEnabled = false;
        SettingsStatus.resourceGovernorEnabled = false;
        SettingsStatus.browserPrivacyGuardEnabled = false;
        SettingsStatus.cameraMicIndicatorEnabled = false;
        SettingsStatus.ghostModeEnabled = false;
        SettingsStatus.disableTelemetryEnabled = false;
        SettingsStatus.foldableSplitViewEnabled = false;
    }

    /** One row from whichever provider is asked, so a pass-through is visible. */
    public static class OneRowProvider extends ContentProvider {
        @Override public boolean onCreate() { return true; }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[]{"_id"});
            cursor.addRow(new Object[]{1L});
            return cursor;
        }

        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }

    @Test public void contactsComeBackEmptyWhileTheSwitchIsOnAndRealWhenItIsOff() {
        Robolectric.setupContentProvider(OneRowProvider.class, ContactsContract.AUTHORITY);
        Robolectric.setupContentProvider(OneRowProvider.class, MediaStore.AUTHORITY);
        ContentResolver resolver = context.getContentResolver();
        String[] projection = {"_id"};

        Settings.BLOCK_CONTACT_LIST.save(true);
        Cursor blocked = ContactListBlocker.interceptQuery(resolver,
                ContactsContract.Contacts.CONTENT_URI, projection, null, null, null);
        assertNotNull("a blocked read is an empty cursor, not a null the caller trips on", blocked);
        assertEquals(0, blocked.getCount());
        assertEquals("the projection is honoured so the caller's column lookups still work",
                1, blocked.getColumnCount());
        Cursor media = ContactListBlocker.interceptQuery(resolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);
        assertEquals("only the contacts provider is refused", 1, media.getCount());

        Settings.BLOCK_CONTACT_LIST.save(false);
        Cursor allowed = ContactListBlocker.interceptQuery(resolver,
                ContactsContract.Contacts.CONTENT_URI, projection, null, null, null);
        assertEquals("with the switch off the real provider answers", 1, allowed.getCount());
        Cursor cancellable = ContactListBlocker.interceptQuery(resolver,
                ContactsContract.Contacts.CONTENT_URI, projection, null, null, null, null);
        assertEquals(1, cancellable.getCount());
    }

    @Test public void theLauncherEnumerationIsTheOnlyPackageQueryRefused() {
        Intent inventory = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain");
        Intent oneApp = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage("com.example.other");
        assertTrue(InstalledAppsBlocker.isAppInventoryScan(inventory));
        assertFalse("a share target query is not an inventory scan", InstalledAppsBlocker.isAppInventoryScan(share));
        assertFalse("a check for one named app is left alone", InstalledAppsBlocker.isAppInventoryScan(oneApp));
        assertFalse(InstalledAppsBlocker.isAppInventoryScan(null));

        PackageManager pm = context.getPackageManager();
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.example.other";
        info.activityInfo.name = "com.example.other.Main";
        Shadows.shadowOf(pm).addResolveInfoForIntent(inventory, info);
        Shadows.shadowOf(pm).addResolveInfoForIntent(share, info);

        Settings.BLOCK_INSTALLED_APPS.save(true);
        assertEquals(0, InstalledAppsBlocker.interceptQueryIntentActivities(pm, inventory, 0).size());
        assertEquals("the share sheet still learns which apps can take a share",
                1, InstalledAppsBlocker.interceptQueryIntentActivities(pm, share, 0).size());
        assertEquals(0, InstalledAppsBlocker.interceptGetInstalledPackages(pm, 0).size());

        Settings.BLOCK_INSTALLED_APPS.save(false);
        assertEquals(1, InstalledAppsBlocker.interceptQueryIntentActivities(pm, inventory, 0).size());
    }

    @Test public void locationIsNothingWhileTheSwitchIsOn() {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location here = new Location(LocationManager.GPS_PROVIDER);
        here.setLatitude(51.5);
        here.setLongitude(-0.12);
        Shadows.shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, here);

        Settings.BLOCK_LOCATION.save(true);
        assertNull(LocationGovernor.interceptGetLastKnownLocation(manager, LocationManager.GPS_PROVIDER));

        Settings.BLOCK_LOCATION.save(false);
        Location answered = LocationGovernor.interceptGetLastKnownLocation(manager, LocationManager.GPS_PROVIDER);
        assertNotNull(answered);
        assertEquals(51.5, answered.getLatitude(), 0.0001);
    }

    @Test public void clipboardReadsAreEmptyWhileTheSwitchIsOn() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("label", "hello"));

        Settings.BLOCK_CLIPBOARD_READS.save(true);
        assertNull(DevicePrivacyGuard.interceptPrimaryClip(clipboard));
        assertEquals("", DevicePrivacyGuard.interceptClipboardText(clipboard).toString());
        assertFalse(DevicePrivacyGuard.interceptHasPrimaryClip(clipboard));

        Settings.BLOCK_CLIPBOARD_READS.save(false);
        assertNotNull(DevicePrivacyGuard.interceptPrimaryClip(clipboard));
        assertEquals("hello", DevicePrivacyGuard.interceptClipboardText(clipboard).toString());
        assertTrue(DevicePrivacyGuard.interceptHasPrimaryClip(clipboard));
    }

    @Test public void motionSensorsAreRefusedAndTheRestRegistered() {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER);
        Sensor light = ShadowSensor.newInstance(Sensor.TYPE_LIGHT);
        SensorEventListener listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {}
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        Settings.BLOCK_MOTION_SENSORS.save(true);
        assertFalse(ResourceBatteryGovernor.interceptSensorRegistration(manager, listener, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL));
        assertFalse(ResourceBatteryGovernor.interceptSensorRegistration(manager, listener, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL, (android.os.Handler) null));
        assertTrue("a light sensor is not a motion sensor and goes through",
                ResourceBatteryGovernor.interceptSensorRegistration(manager, listener, light,
                        SensorManager.SENSOR_DELAY_NORMAL));

        Settings.BLOCK_MOTION_SENSORS.save(false);
        assertTrue(ResourceBatteryGovernor.interceptSensorRegistration(manager, listener, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL));
    }

    @Test public void theDotFollowsTheCountsAndTheSwitch() {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            int childrenBefore = decor.getChildCount();

            Settings.CAMERA_MIC_INDICATOR.save(true);
            CameraMicIndicator.onMicStart();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            CameraMicIndicator.DotView dot = CameraMicIndicator.shownDot();
            assertNotNull("a live microphone shows the dot", dot);
            assertSame("the dot sits on the top activity's decor view", decor, dot.getParent());
            assertTrue(dot.microphone());
            assertFalse(dot.camera());
            assertEquals("Microphone in use", String.valueOf(dot.getContentDescription()));
            assertFalse("the dot must never take a touch", dot.isClickable());

            CameraMicIndicator.onCameraOpened(null);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse("a camera that failed to open is not an access", CameraMicIndicator.shownDot().camera());
            CameraMicIndicator.onCameraStart();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(CameraMicIndicator.shownDot().camera());
            assertEquals("Camera and microphone in use",
                    String.valueOf(CameraMicIndicator.shownDot().getContentDescription()));

            CameraMicIndicator.onMicStop();
            CameraMicIndicator.onMicStop();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("a stop reported twice does not go below zero", 0, CameraMicIndicator.microphones());
            assertTrue("the camera is still open, so the dot stays", CameraMicIndicator.shownDot().camera());
            assertFalse(CameraMicIndicator.shownDot().microphone());

            CameraMicIndicator.onCameraStop();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNull("nothing live, nothing drawn", CameraMicIndicator.shownDot());
            assertEquals(childrenBefore, decor.getChildCount());

            Settings.CAMERA_MIC_INDICATOR.save(false);
            CameraMicIndicator.onCameraStart();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertNull("with the switch off the access is counted and nothing is drawn", CameraMicIndicator.shownDot());
            assertEquals(1, CameraMicIndicator.cameras());
        }
    }

    /**
     * TikTok's camera is a scene inside an activity that has already resumed when the camera
     * opens. With the tracking installed from the main activity, the mark goes onto that screen,
     * not onto the main activity underneath, where the S25 showed nothing (2026-09-26).
     */
    @Test public void theDotGoesOnTheScreenInFrontWhenTheCameraOpensAfterItResumed() {
        try (var main = Robolectric.buildActivity(Activity.class).create()) {
            CameraMicIndicator.install(main.get());
            main.start().resume().visible();
            Utils.setActivity(main.get());
            try (var camera = Robolectric.buildActivity(Activity.class).setup().visible()) {
                Settings.CAMERA_MIC_INDICATOR.save(true);
                CameraMicIndicator.onCameraStart();
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                CameraMicIndicator.DotView dot = CameraMicIndicator.shownDot();
                assertNotNull("an open camera shows the mark", dot);
                assertSame("the mark is on the camera's screen, not the main one behind it",
                        camera.get().getWindow().getDecorView(), dot.getParent());
            }
        }
    }

    /**
     * A screen that draws under the status bar and reports no top inset still gets the mark below
     * the bar, not on its icons: the S25's camera screen put it over the battery (2026-09-26).
     */
    @Test public void theMarkSitsBelowTheStatusBarWhenTheWindowReportsNoInset() {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setActivity(activity);
            Settings.CAMERA_MIC_INDICATOR.save(true);
            CameraMicIndicator.onCameraStart();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            CameraMicIndicator.DotView dot = CameraMicIndicator.shownDot();
            assertNotNull(dot);
            int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int bar = activity.getResources().getDimensionPixelSize(id);
            assertTrue("the status bar has a height to clear", bar > 0);
            int top = ((android.widget.FrameLayout.LayoutParams) dot.getLayoutParams()).topMargin;
            assertTrue("the mark starts below the status bar (" + top + " px, bar " + bar + " px)", top > bar);
        }
    }

    @Test public void thePrivacyPageCarriesTheSwitchesAndAppBehaviorNoLongerDoes() {
        SettingsStatus.contactListBlockerEnabled = true;
        SettingsStatus.installedAppsBlockerEnabled = true;
        SettingsStatus.locationGovernorEnabled = true;
        SettingsStatus.devicePrivacyGuardEnabled = true;
        SettingsStatus.resourceGovernorEnabled = true;
        SettingsStatus.browserPrivacyGuardEnabled = true;
        SettingsStatus.cameraMicIndicatorEnabled = true;
        SettingsStatus.ghostModeEnabled = true;
        SettingsStatus.disableTelemetryEnabled = true;
        SettingsStatus.foldableSplitViewEnabled = true;
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment privacy = open(activity, "PRIVACY");
            List<String> keys = keysOn(privacy);
            List<String> expected = List.of(Settings.DISABLE_ANALYTICS.key, Settings.GHOST_MODE.key,
                    Settings.BLOCK_CONTACT_LIST.key, Settings.BLOCK_INSTALLED_APPS.key,
                    Settings.BLOCK_LOCATION.key, Settings.BLOCK_CLIPBOARD_READS.key,
                    Settings.BLOCK_MOTION_SENSORS.key, Settings.CAMERA_MIC_INDICATOR.key,
                    Settings.BLOCK_WEBVIEW_JS_INTERFACES.key);
            assertEquals("the Privacy page lists tracking, then device access, then links",
                    expected, keys.stream().filter(expected::contains).toList());
            assertNotNull("Tracking heading", privacy.findPreference(Settings.GHOST_MODE.key));

            TikTokPreferenceFragment behavior = open(activity, "BEHAVIOR");
            assertNull("Ghost mode has moved off App behavior", behavior.findPreference(Settings.GHOST_MODE.key));
            assertNull(behavior.findPreference(Settings.DISABLE_ANALYTICS.key));
            assertNotNull("the layout rows are still App behavior's",
                    behavior.findPreference(Settings.FOLDABLE_SPLIT_VIEW.key));
        }
    }

    private static TikTokPreferenceFragment open(Activity activity, String section) {
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString("morphe_settings_section", section);
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        return fragment;
    }

    private static List<String> keysOn(TikTokPreferenceFragment fragment) {
        var screen = fragment.getPreferenceScreen();
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        for (int index = 0; index < screen.getPreferenceCount(); index++) {
            String key = screen.getPreference(index).getKey();
            if (key != null) keys.add(key);
        }
        return keys;
    }
}
