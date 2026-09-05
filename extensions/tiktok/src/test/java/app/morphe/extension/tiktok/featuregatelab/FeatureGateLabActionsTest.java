package app.morphe.extension.tiktok.featuregatelab;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.json.JSONArray;
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
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeatureGateLabActionsTest {
    @Before public void setup() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Utils.setIsDarkModeEnabled(true);
        FeatureGateLabStore.resetAllLabData();
        FeatureGateLabSession.begin();
        var entry = entry("gate");
        var same = entry("same_gate");
        var cached = FeatureGateCatalog.class.getDeclaredField("cachedSnapshot");
        cached.setAccessible(true);
        cached.set(null, new FeatureGateCatalog.Snapshot(List.of(entry, same),
                Map.of(entry.identity(), entry, same.identity(), same), 2, 0, true));
    }

    @Test public void disabledMasterCanResetRulesAndUndoWithoutChangingOtherSettings() throws Exception {
        save("gate", "true", true);
        FeatureGateLabStore.acknowledgeWarning();
        SettingsManagerObservationRecorder.observeWithDefault("object", String.class, "", "before");
        String observations = SettingsManagerObservationRecorder.exportJson().toString();
        FeatureGateLabUndo.reset(false);
        assertTrue(FeatureGateLabStore.rules().isEmpty());
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertTrue(FeatureGateLabStore.warningAcknowledged());
        Settings.REGION_SPOOF.save(true);
        // The config undo is on disk and does not depend on an in-memory rule snapshot.
        FeatureGateLabRuntime.reloadRules();
        FeatureGateLabUndo.undo();
        assertTrue(FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").enabled);
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertTrue(Settings.REGION_SPOOF.get());
        assertEquals(observations, SettingsManagerObservationRecorder.exportJson().toString());
    }

    @Test public void fullResetUndoRestoresSwitchesObservationsAndWrapperOwnership() throws Exception {
        save("gate", "false", true);
        FeatureGateLabStore.setMasterEnabled(true);
        FeatureGateLabStore.acknowledgeWarning();
        SettingsManagerObservationRecorder.observeWithDefault("structured", Sample.class, new Sample(), new Sample());
        String before = SettingsManagerObservationRecorder.exportJson().toString();
        FeatureGateLabUndo.reset(true);
        assertEquals(0, SettingsManagerObservationRecorder.size());
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertFalse(FeatureGateLabStore.warningAcknowledged());
        assertTrue(FeatureGateLabStore.rules().isEmpty());
        FeatureGateLabUndo.undo();
        assertTrue(FeatureGateLabStore.masterEnabled());
        assertTrue(FeatureGateLabStore.warningAcknowledged());
        assertEquals(before, SettingsManagerObservationRecorder.exportJson().toString());
        SettingsManagerObservationRecorder.observeWithoutDefault("structured", Sample.class, new Sample());
        assertEquals(1, SettingsManagerObservationRecorder.size());
    }

    @Test public void importsReplaceAcceptedRulesDisabledAndUndoRetainsUnrelatedRules() throws Exception {
        save("gate", "false", true);
        save("other", "true", true);
        FeatureGateLabStore.setMasterEnabled(true);
        var incoming = new FeatureGateLabStore.Rule(FeatureGateLabStore.idFor("abmock", "gate", "BOOLEAN"),
                "abmock", "gate", "BOOLEAN", "true", true, 1);
        FeatureGateLabUndo.importRules(new FeatureGateLabStore.ImportReview(List.of(incoming), List.of()));
        assertFalse(FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").enabled);
        assertEquals("true", FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").value);
        assertTrue(FeatureGateLabStore.rule("abmock", "other", "BOOLEAN").enabled);
        assertTrue(FeatureGateLabStore.masterEnabled());
        FeatureGateLabUndo.undo();
        assertEquals("false", FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").value);
        assertTrue(FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").enabled);
    }

    @Test public void failedCheckpointAndInvalidUndoLeaveSettingsUntouched() throws Exception {
        var app = Utils.getContext();
        save("gate", "true", true);
        String before = FeatureGateLabStore.exportSettings().toString();
        File invalidDirectory = File.createTempFile("lab-undo", ".tmp", app.getCacheDir());
        Utils.setContext(new android.content.ContextWrapper(app) {
            @Override public File getFilesDir() { return invalidDirectory; }
        });
        try {
            assertThrows(Exception.class, () -> FeatureGateLabUndo.reset(true));
            assertEquals(before, FeatureGateLabStore.exportSettings().toString());
        } finally {
            Utils.setContext(app);
            assertTrue(invalidDirectory.delete());
        }
        FeatureGateLabUndo.reset(false);
        String empty = FeatureGateLabStore.exportSettings().toString();
        try (var output = new java.io.FileOutputStream(new File(app.getFilesDir(), "feature-gate-lab-undo.json"))) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        assertThrows(Exception.class, FeatureGateLabUndo::undo);
        assertEquals(empty, FeatureGateLabStore.exportSettings().toString());
    }

    @Test public void failedPreferenceCommitRecoversThePreviousConfigAndLeavesObservations() throws Exception {
        var app = Utils.getContext();
        save("gate", "true", true);
        FeatureGateLabStore.setMasterEnabled(true);
        SettingsManagerObservationRecorder.observeWithDefault("object", String.class, "", "before");
        String before = FeatureGateLabStore.exportSettings().toString();
        var delegate = app.getSharedPreferences("morphe_feature_gate_lab", 0);
        var commits = new java.util.concurrent.atomic.AtomicInteger();
        var failing = (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(), new Class[]{android.content.SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(delegate, args);
                    var editor = delegate.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(editor.getClass().getClassLoader(),
                            new Class[]{android.content.SharedPreferences.Editor.class}, (wrapped, call, values) -> {
                                Object result = call.invoke(editor, values);
                                if (call.getName().equals("commit")) return commits.incrementAndGet() != 1 && (Boolean) result;
                                return result instanceof android.content.SharedPreferences.Editor ? wrapped : result;
                            });
                });
        Utils.setContext(new android.content.ContextWrapper(app) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return name.equals("morphe_feature_gate_lab") ? failing : super.getSharedPreferences(name, mode);
            }
        });
        try {
            assertThrows(Exception.class, () -> FeatureGateLabUndo.reset(true));
            assertEquals(before, FeatureGateLabStore.exportSettings().toString());
            assertEquals(1, SettingsManagerObservationRecorder.size());
            assertTrue(commits.get() >= 2);
        } finally { Utils.setContext(app); }
    }

    @Test public void controlsApplyMasterResetAndUndoWithoutDialogs() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            Switch master = findSwitch(fragment.getView());
            master.performClick();
            assertTrue(master.isChecked());
            assertTrue(FeatureGateLabStore.masterEnabled());
            assertTrue(FeatureGateLabStore.warningAcknowledged());
            assertNull(ShadowDialog.getLatestDialog());
            master.performClick();
            assertFalse(FeatureGateLabStore.masterEnabled());
            save("gate", "true", true);
            action(fragment, 4);
            waitFor("Lab overrides reset.");
            assertTrue(FeatureGateLabStore.rules().isEmpty());
            action(fragment, 6);
            waitFor("Restored the previous Lab settings.");
            assertEquals(1, FeatureGateLabStore.rules().size());
            action(fragment, 5);
            waitFor("Lab data reset.");
            assertFalse(master.isChecked());
            assertFalse(FeatureGateLabStore.warningAcknowledged());
            assertNull(ShadowDialog.getLatestDialog());
            var cached = FeatureGateCatalog.cachedSnapshot();
            action(fragment, 1);
            for (int i = 0; i < 500 && FeatureGateCatalog.cachedSnapshot() == cached; i++) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotSame(cached, FeatureGateCatalog.cachedSnapshot());
            var tabs = FeatureGateLabFragment.class.getDeclaredField("viewTabs");
            tabs.setAccessible(true);
            ((ViewGroup) tabs.get(fragment)).getChildAt(1).performClick();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "feature-gate-lab.png");
            ShadowToast.reset();
            FeatureGateLabSession.showRestartDialog(activity);
            FeatureGateLabSession.showAfterHostExit(activity);
            assertEquals(1, ShadowToast.shownToastCount());
            assertNull(ShadowDialog.getLatestDialog());
        }
    }

    @Test public void selectedFileImportsDisabledValuesAndReportsSkippedCountsWithoutConfirmation() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            JSONArray rules = new JSONArray().put(rule("gate", "true")).put(rule("same_gate", "false"))
                    .put(rule("missing", "true")).put(rule("gate", "invalid")).put(42);
            JSONObject root = new JSONObject().put("payload_kind", "loaded_values")
                    .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION).put("rules", rules);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(root.toString().getBytes(StandardCharsets.UTF_8)); }
            action(fragment, 3);
            var started = Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.getAction());
            var uri = android.net.Uri.parse("content://lab-test/values.json.gz");
            Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(bytes.toByteArray()));
            fragment.onActivityResult(started.requestCode, Activity.RESULT_OK, new Intent().setData(uri));
            waitFor("Imported 1 disabled values. 1 already matched, 1 unavailable, 2 rejected.");
            assertFalse(FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").enabled);
            assertEquals("true", FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").value);
            assertEquals(1, FeatureGateLabStore.rules().size());
            assertNull(ShadowDialog.getLatestDialog());
            action(fragment, 6);
            waitFor("Restored the previous Lab settings.");
            assertTrue(FeatureGateLabStore.rules().isEmpty());
        }
    }

    private static FeatureGateLabFragment attach(Activity activity) {
        var fragment = new FeatureGateLabFragment();
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return fragment;
    }
    private static void action(FeatureGateLabFragment fragment, int id) {
        assertTrue(fragment.getView().findViewWithTag("feature_gate_menu").performClick());
        var menu = ShadowPopupMenu.getLatestPopupMenu();
        assertTrue(menu.getMenu().performIdentifierAction(id, 0));
        menu.dismiss();
    }
    private static void waitFor(String prefix) throws Exception {
        for (int i = 0; i < 500; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            String toast = ShadowToast.getTextOfLatestToast();
            if (toast != null && toast.startsWith(prefix)) return;
            Thread.sleep(10);
        }
        fail("Missing completion: " + ShadowToast.getTextOfLatestToast());
    }
    private static Switch findSwitch(View view) {
        if (view instanceof Switch) return (Switch) view;
        if (view instanceof ViewGroup) {
            var group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Switch found = findSwitch(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private static FeatureGateCatalog.Entry entry(String key) {
        return new FeatureGateCatalog.Entry(key, key, "abmock", "BOOLEAN", true, true,
                List.of("false"), List.of(), List.of(), "", "", true, "false", "BOOLEAN");
    }
    private static JSONObject rule(String key, String value) throws Exception {
        return new JSONObject().put("manager", "abmock").put("key", key).put("type", "BOOLEAN").put("value", value);
    }
    private static void save(String key, String value, boolean enabled) {
        FeatureGateLabStore.saveRule("abmock", key, "BOOLEAN", value, enabled);
    }
    public static final class Sample { public String name = "日本語"; public List<Integer> values = List.of(2, 7); }
    public static class TestActivity extends Activity {
        @Override protected void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }
}
