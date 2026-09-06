package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.AppLanguage;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SettingsBackupTest {
    @Before public void setup() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.REGION_SPOOF.get();
        for (Setting<?> setting : Setting.allLoadedSettings()) setting.resetToDefault();
        FeatureGateLabStore.resetAllLabData();
    }

    @Test public void everySwitchAndTypedValuesReturnAfterPreferencesAreCleared() throws Exception {
        Map<Setting<?>, Object> expected = new LinkedHashMap<>();
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (setting instanceof BooleanSetting) ((BooleanSetting) setting).save(!(Boolean) setting.defaultValue);
        }
        Settings.BLOCKED_SOUND_NAMES.save("音楽, Straße, original sound");
        Settings.MAX_VIDEO_SECONDS.save(90);
        Settings.REMEMBERED_SPEED.save(2.5f);
        BaseSettings.MORPHE_LANGUAGE.save(AppLanguage.DE);
        BaseSettings.DEBUG_LOG_FILTERS.save("network");
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (setting.includeWithImportExport || setting == BaseSettings.DEBUG_LOG_FILTERS) expected.put(setting, setting.get());
        }
        FeatureGateLabStore.saveRule("abmock", "test_gate", "BOOLEAN", "true", true);
        FeatureGateLabStore.setMasterEnabled(true);
        FeatureGateLabStore.acknowledgeWarning();
        String backup = SettingsBackup.create(false);
        Setting.preferences.preferences.edit().clear().commit();
        for (Setting<?> setting : expected.keySet()) setting.resetToDefault();
        FeatureGateLabStore.resetAllLabData();
        SettingsBackup.restore(Utils.getContext(), backup, true);
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getKey().key, entry.getValue(), entry.getKey().get());
            if (!entry.getKey().isSetToDefault()) assertTrue(Setting.preferences.preferences.contains(entry.getKey().key));
        }
        assertTrue(FeatureGateLabStore.masterEnabled());
        assertTrue(FeatureGateLabStore.warningAcknowledged());
        var rule = FeatureGateLabStore.rule("abmock", "test_gate", "BOOLEAN");
        assertNotNull(rule);
        assertTrue(rule.enabled);
        assertEquals("true", rule.value);
    }

    @Test public void malformedLateValuesNeverPartiallyApplyOrReplaceUndo() throws Exception {
        Settings.MAX_VIDEO_SECONDS.save(42);
        SettingsBackup.reset(Utils.getContext());
        String baseline = SettingsBackup.create(false);
        for (Object bad : new Object[]{"bad", true, 1.25, 2147483648L, JSONObject.NULL}) {
            JSONObject root = new JSONObject(baseline);
            root.getJSONObject("settings").put(Settings.REGION_SPOOF.key, true).put(Settings.MAX_VIDEO_SECONDS.key, bad);
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), root.toString(), true));
            assertEquals(baseline, SettingsBackup.create(false));
        }
        SettingsBackup.undo(Utils.getContext());
        assertEquals(42, (int) Settings.MAX_VIDEO_SECONDS.get());
    }

    @Test public void invalidLabVersionRuleAndTrailingInputLeaveSettingsUntouched() throws Exception {
        Settings.REGION_SPOOF.save(true);
        String baseline = SettingsBackup.create(false);
        JSONObject wrongVersion = new JSONObject(baseline).put("target", "other");
        JSONObject wrongLab = new JSONObject(baseline);
        wrongLab.getJSONObject("lab").put("master", "true");
        JSONObject badRule = new JSONObject(baseline);
        badRule.getJSONObject("lab").getJSONArray("rules").put(new JSONObject()
                .put("manager", "abmock").put("key", "bad").put("type", "FLOAT").put("value", "NaN").put("force", true));
        for (String invalid : new String[]{wrongVersion.toString(), wrongLab.toString(), badRule.toString(), baseline + "garbage", "[]"}) {
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), invalid, true));
            assertEquals(baseline, SettingsBackup.create(false));
        }
    }

    @Test public void resetAndUndoRestoreBothStoresAndSurviveAnUnrelatedSettingChange() throws Exception {
        Settings.BLOCKED_CREATORS.save("creator");
        Settings.AUTO_ADVANCE.save(true);
        FeatureGateLabStore.saveRule("abmock", "another_gate", "INT", "3", false);
        FeatureGateLabStore.setMasterEnabled(true);
        SettingsBackup.reset(Utils.getContext());
        assertEquals("", Settings.BLOCKED_CREATORS.get());
        assertFalse(Settings.AUTO_ADVANCE.get());
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertTrue(FeatureGateLabStore.rules().isEmpty());
        assertTrue(SettingsBackup.hasUndo(Utils.getContext()));
        Settings.BLOCKED_CREATORS.save("later change");
        SettingsBackup.undo(Utils.getContext());
        assertEquals("creator", Settings.BLOCKED_CREATORS.get());
        assertTrue(Settings.AUTO_ADVANCE.get());
        assertTrue(FeatureGateLabStore.masterEnabled());
        assertEquals("3", FeatureGateLabStore.rule("abmock", "another_gate", "INT").value);
    }

    @Test public void inputIsBoundedAndRejectsMalformedUtf8() throws Exception {
        byte[] bytes = SettingsBackup.create(false).getBytes(StandardCharsets.UTF_8);
        assertEquals(new String(bytes, StandardCharsets.UTF_8), SettingsBackup.read(new ByteArrayInputStream(bytes)));
        assertThrows(java.io.IOException.class, () -> SettingsBackup.read(new ByteArrayInputStream(new byte[SettingsBackup.MAX_BYTES + 1])));
        assertThrows(java.io.IOException.class, () -> SettingsBackup.read(new ByteArrayInputStream(new byte[]{(byte) 0xc3, 0x28})));
    }

    @Test public void exactNumericTokensAndTrailingNulAreValidatedBeforeAnyBackupChanges() throws Exception {
        Settings.MAX_VIDEO_SECONDS.save(42);
        SettingsBackup.reset(Utils.getContext());
        String baseline = SettingsBackup.create(false);
        JSONObject fractional = new JSONObject(baseline);
        fractional.getJSONObject("settings").put(Settings.MAX_VIDEO_SECONDS.key, "precise-number");
        for (String invalid : new String[]{fractional.toString().replace("\"precise-number\"", "1.00000000000000001"),
                baseline + '\0' + "garbage"}) {
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), invalid, true));
            assertEquals(baseline, SettingsBackup.create(false));
        }
        SettingsBackup.undo(Utils.getContext());
        assertEquals(42, (int) Settings.MAX_VIDEO_SECONDS.get());
    }

    @Test public void missingSettingsAreRejectedBeforeWritingUndoOrChangingValues() throws Exception {
        Settings.MAX_VIDEO_SECONDS.save(34);
        String original = SettingsBackup.create(false);
        JSONObject empty = new JSONObject(original).put("settings", new JSONObject());
        JSONObject missing = new JSONObject(original);
        missing.getJSONObject("settings").remove(Settings.REGION_SPOOF.key);
        for (String text : new String[]{empty.toString(), missing.toString()}) {
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), text, true));
            assertEquals(original, SettingsBackup.create(false));
        }
    }

    @Test public void rollbackStillAttemptsLabWhenOrdinaryPreferenceRecoveryFails() throws Exception {
        var app = Utils.getContext();
        FeatureGateLabStore.setMasterEnabled(true);
        JSONObject next = new JSONObject(SettingsBackup.create(false));
        next.getJSONObject("settings").put(Settings.REGION_SPOOF.key, true);
        next.getJSONObject("lab").put("master", false);
        var original = Setting.preferences.preferences;
        var normalFailure = new java.util.concurrent.atomic.AtomicBoolean();
        var labCommits = new java.util.concurrent.atomic.AtomicInteger();
        var normal = failingCommits(original, normalFailure::get, () -> {});
        var lab = failingCommits(app.getSharedPreferences("morphe_feature_gate_lab", 0),
                () -> labCommits.get() == 1, () -> {
                    if (labCommits.incrementAndGet() == 1) normalFailure.set(true);
                });
        var field = app.morphe.extension.shared.settings.preference.SharedPrefCategory.class.getDeclaredField("preferences");
        field.setAccessible(true);
        field.set(Setting.preferences, normal);
        Utils.setContext(new android.content.ContextWrapper(app) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return name.equals("morphe_feature_gate_lab") ? lab : super.getSharedPreferences(name, mode);
            }
        });
        try {
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), next.toString(), true));
            assertTrue("Lab recovery must run even after the other store fails", FeatureGateLabStore.masterEnabled());
            assertTrue(labCommits.get() >= 2);
        } finally {
            field.set(Setting.preferences, original);
            Utils.setContext(app);
        }
        SettingsBackup.undo(app);
        assertFalse(Settings.REGION_SPOOF.get());
        assertTrue(FeatureGateLabStore.masterEnabled());
    }

    @Test public void anOlderCompleteInventoryUsesDefaultsForNewerSettings() throws Exception {
        Settings.MAX_VIDEO_SECONDS.save(75);
        JSONObject root = new JSONObject(SettingsBackup.create(false));
        org.json.JSONArray original = root.getJSONArray("setting_keys"), older = new org.json.JSONArray();
        for (int i = 0; i < original.length(); i++) {
            if (!original.getString(i).equals(Settings.REGION_SPOOF.key)) older.put(original.get(i));
        }
        root.put("setting_keys", older);
        root.getJSONObject("settings").remove(Settings.REGION_SPOOF.key);
        Settings.REGION_SPOOF.save(true);
        Settings.MAX_VIDEO_SECONDS.save(0);
        SettingsBackup.restore(Utils.getContext(), root.toString(), true);
        assertFalse(Settings.REGION_SPOOF.get());
        assertEquals(75, (int) Settings.MAX_VIDEO_SECONDS.get());
    }

    private static android.content.SharedPreferences failingCommits(android.content.SharedPreferences target,
            java.util.function.BooleanSupplier fail, Runnable committed) {
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                target.getClass().getClassLoader(), new Class[]{android.content.SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(target, args);
                    var editor = target.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(editor.getClass().getClassLoader(),
                            new Class[]{android.content.SharedPreferences.Editor.class}, (editorProxy, call, values) -> {
                                Object result = call.invoke(editor, values);
                                if (call.getName().equals("commit")) {
                                    committed.run();
                                    return !fail.getAsBoolean() && (Boolean) result;
                                }
                                return result instanceof android.content.SharedPreferences.Editor ? editorProxy : result;
                            });
                });
    }

    @Test public void anUnwritableUndoLocationPreventsAnyChange() throws Exception {
        var file = java.io.File.createTempFile("unwritable-backup", ".tmp", Utils.getContext().getCacheDir());
        var context = new android.content.ContextWrapper(Utils.getContext()) {
            @Override public java.io.File getFilesDir() { return file; }
        };
        Settings.MAX_VIDEO_SECONDS.save(52);
        assertThrows(java.io.IOException.class, () -> SettingsBackup.restore(context, SettingsBackup.create(true), true));
        assertEquals(52, (int) Settings.MAX_VIDEO_SECONDS.get());
        assertEquals("52", Setting.preferences.preferences.getString(Settings.MAX_VIDEO_SECONDS.key, null));
        assertTrue(file.delete());
    }

    @Test public void settingsScreenExportsThroughTheFilePickerWithoutTheDiagnosticsPatch() throws Exception {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            SettingsStatus.diagnosticsEnabled = false;
            var fragment = new TikTokPreferenceFragment();
            Bundle arguments = new Bundle();
            arguments.putString("morphe_settings_section", "DIAGNOSTICS");
            fragment.setArguments(arguments);
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
            activity.getFragmentManager().executePendingTransactions();
            var export = fragment.findPreference("settings_backup_7311");
            assertNotNull(export);
            assertNotNull(fragment.findPreference("settings_backup_7312"));
            assertNotNull(fragment.findPreference("settings_backup_7313"));
            assertNull(fragment.findPreference(BaseSettings.DEBUG.key));
            export.getOnPreferenceClickListener().onPreferenceClick(export);
            var started = Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.getAction());
            assertEquals("application/json", started.intent.getType());
            Uri uri = Uri.parse("content://settings-test/backup.json");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(uri, output);
            fragment.onActivityResult(7311, android.app.Activity.RESULT_OK, new Intent().setData(uri));
            waitFor("Settings backup saved");
            assertEquals("hushfeed-settings", new JSONObject(output.toString(StandardCharsets.UTF_8)).getString("format"));
            Settings.MAX_VIDEO_SECONDS.save(73);
            Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(output.toByteArray()));
            fragment.onActivityResult(7312, android.app.Activity.RESULT_OK, new Intent().setData(uri));
            waitFor("Settings saved. Restart TikTok to apply all changes.");
            assertEquals(0, (int) Settings.MAX_VIDEO_SECONDS.get());
            var undo = fragment.findPreference("settings_backup_7314");
            undo.getOnPreferenceClickListener().onPreferenceClick(undo);
            waitFor("Settings saved. Restart TikTok to apply all changes.");
            assertEquals(73, (int) Settings.MAX_VIDEO_SECONDS.get());
            var reset = fragment.findPreference("settings_backup_7313");
            reset.getOnPreferenceClickListener().onPreferenceClick(reset);
            waitFor("Settings saved. Restart TikTok to apply all changes.");
            assertEquals(0, (int) Settings.MAX_VIDEO_SECONDS.get());
            fragment.onActivityResult(7312, android.app.Activity.RESULT_CANCELED, null);
            assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "settings-backup.png");
        }
    }

    private static void waitFor(String message) throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (message.equals(ShadowToast.getTextOfLatestToast())) return;
            Thread.sleep(10);
        }
        fail("Missing completion toast: " + ShadowToast.getTextOfLatestToast());
    }
}
