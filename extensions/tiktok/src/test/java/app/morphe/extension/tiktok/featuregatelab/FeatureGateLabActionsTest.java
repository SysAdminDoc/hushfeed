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
        FeatureGateLabFragment.awaitFileIoForTests();
        FeatureGateCatalog.awaitForTests();
        Utils.awaitBackgroundTasksForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        FeatureGateCatalog.resetForTests();
        FeatureGateLabFragment.resetForTests();
        FeatureGateLabSession.resetForTests();
        FeatureGateLabUndo.resetForTests();
        SettingsManagerObservationRecorder.clear();
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

    @Test public void aFailedCommitOnASelectionPutsNoneOfItThrough() throws Exception {
        // The reason a selection goes through one replaceSettings rather than one per gate: a
        // selection half applied is worse than one not applied. Same failing commit the
        // single-gate path is held to below.
        var app = Utils.getContext();
        save("kept", "true", true);
        String before = FeatureGateLabStore.exportSettings().toString();

        // A fresh store each time: the proxy fails only its first commit, which is what leaves a
        // rollback to succeed on the second.
        try {
            var gates = List.of(entry("alpha"), entry("beta"), entry("gamma"));
            useFailingStore(app);
            assertThrows(Exception.class, () -> FeatureGateLabUndo.forceBoolean(gates, false));
            assertEquals("part of the selection was written by a commit that failed",
                    before, FeatureGateLabStore.exportSettings().toString());

            useFailingStore(app);
            assertThrows(Exception.class,
                    () -> FeatureGateLabUndo.resetAll(List.of(entry("kept"))));
            assertEquals("a reset that failed dropped a rule anyway",
                    before, FeatureGateLabStore.exportSettings().toString());
        } finally { Utils.setContext(app); }
    }

    /** Points the Lab's storage at a store whose next commit reports failure. */
    private static void useFailingStore(android.content.Context app) {
        var failing = failingOnceStore(app);
        Utils.setContext(new android.content.ContextWrapper(app) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return name.equals("morphe_feature_gate_lab")
                        ? failing : super.getSharedPreferences(name, mode);
            }
        });
    }

    /** A settings store whose first commit reports failure, as the single-gate case uses. */
    private static android.content.SharedPreferences failingOnceStore(android.content.Context app) {
        var delegate = app.getSharedPreferences("morphe_feature_gate_lab", 0);
        var commits = new java.util.concurrent.atomic.AtomicInteger();
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class[]{android.content.SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(delegate, args);
                    var editor = delegate.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(editor.getClass().getClassLoader(),
                            new Class[]{android.content.SharedPreferences.Editor.class},
                            (wrapped, call, values) -> {
                                Object result = call.invoke(editor, values);
                                if (call.getName().equals("commit")) {
                                    return commits.incrementAndGet() != 1 && (Boolean) result;
                                }
                                return result instanceof android.content.SharedPreferences.Editor
                                        ? wrapped : result;
                            });
                });
    }

    @Test public void failedPreferenceCommitRecoversThePreviousConfigAndLeavesObservations() throws Exception {
        var app = Utils.getContext();
        save("gate", "true", true);
        FeatureGateLabStore.setMasterEnabled(true);
        SettingsManagerObservationRecorder.observeWithDefault("object", String.class, "", "before");
        // A gate that actually fired. The detail screen reads this to say whether an override has
        // been reached, and a commit that did not land must not be able to erase it.
        FeatureGateLabRuntime.reloadRules();
        assertTrue(FeatureGateLabRuntime.overrideBoolean("gate", false));
        assertTrue("the rule never fired, so the test cannot see it being lost",
                FeatureGateLabRuntime.isTriggered("abmock", "gate", "BOOLEAN"));
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
            assertTrue("a failed commit cleared the record of which overrides fired",
                    FeatureGateLabRuntime.isTriggered("abmock", "gate", "BOOLEAN"));
            // Both halves of that are load bearing. The failed write must not clear, because
            // nothing was written; and the rollback must not clear either, because it puts the
            // very rules back that the record describes.
            assertEquals("the original the rule replaced went with it",
                    "false", FeatureGateLabRuntime.originalValue("abmock", "gate", "BOOLEAN"));
        } finally { Utils.setContext(app); }
    }

    @Test public void nulCannotHideTrailingDataInTheLabUndoFile() throws Exception {
        save("gate", "true", true);
        FeatureGateLabUndo.reset(false);
        try (var output = new java.io.FileOutputStream(new File(Utils.getContext().getFilesDir(), "feature-gate-lab-undo.json"), true)) {
            output.write(new byte[]{0, 'b', 'a', 'd'});
        }
        assertThrows(Exception.class, FeatureGateLabUndo::undo);
        assertTrue(FeatureGateLabStore.rules().isEmpty());
    }

    @Test public void controlsApplyMasterResetAndUndoWithoutDialogs() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            Switch master = findSwitch(fragment.getView());
            master.performClick();
            // The switch writes through the journal, which is storage, so it goes the same way
            // as every other Lab change: off this thread, then back to it.
            settle();
            assertTrue(master.isChecked());
            assertTrue(FeatureGateLabStore.masterEnabled());
            assertTrue(FeatureGateLabStore.warningAcknowledged());
            assertNull(ShadowDialog.getLatestDialog());
            master.performClick();
            settle();
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
            FeatureGateCatalog.awaitForTests();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotSame(cached, FeatureGateCatalog.cachedSnapshot());
            var tabs = FeatureGateLabFragment.class.getDeclaredField("viewTabs");
            tabs.setAccessible(true);
            ((ViewGroup) tabs.get(fragment)).getChildAt(1).performClick();
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

    @Test public void rawJsonImportMatchesThePickerAndAppliesTheSameReview() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            JSONObject root = new JSONObject().put("payload_kind", "loaded_values")
                    .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION)
                    .put("rules", new JSONArray().put(rule("gate", "true")));
            action(fragment, 3);
            var started = Shadows.shadowOf(activity).getNextStartedActivityForResult();
            var uri = android.net.Uri.parse("content://lab-test/values.json");
            Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,
                    new ByteArrayInputStream(root.toString().getBytes(StandardCharsets.UTF_8)));
            fragment.onActivityResult(started.requestCode, Activity.RESULT_OK, new Intent().setData(uri));
            waitFor("Imported 1 disabled values.");
            assertFalse(FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").enabled);
            assertEquals("true", FeatureGateLabStore.rule("abmock", "gate", "BOOLEAN").value);
        }
    }

    @Test public void boundedImportParserRejectsDuplicateDeepInvalidAndOverlargeInput() throws Exception {
        assertLoadedJsonRejected(("{\"payload_kind\":\"loaded_values\","
                + "\"payload_kind\":\"loaded_values\",\"tiktok_version\":\"46.2.3\",\"rules\":[]}")
                .getBytes(StandardCharsets.UTF_8));
        assertLoadedJsonRejected(new byte[]{'{', '"', 'x', '"', ':', '"', (byte) 0xc3, 0x28, '"', '}'});

        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 30; i++) deep.append('[');
        deep.append("{}");
        for (int i = 0; i < 30; i++) deep.append(']');
        assertLoadedJsonRejected(deep.toString().getBytes(StandardCharsets.UTF_8));
        assertLoadedJsonRejected(new byte[8 * 1024 * 1024 + 1]);

        JSONArray many = new JSONArray();
        for (int i = 0; i < 2000; i++) many.put(rule("gate" + i, "true"));
        JSONObject root = new JSONObject().put("payload_kind", "loaded_values")
                .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION).put("rules", many);
        assertLoadedJsonRejected(root.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test public void loadedValueReviewRejectsNonStringFieldsAndDuplicateRules() throws Exception {
        JSONObject nonString = rule("gate", "true").put("value", true);
        JSONObject duplicate = rule("gate", "false");
        JSONObject duplicateAgain = rule("gate", "true");
        JSONObject root = new JSONObject().put("schema", 1).put("target", "TikTok global")
                .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION)
                .put("rules", new JSONArray().put(nonString).put(duplicate).put(duplicateAgain));
        FeatureGateLabStore.ImportReview review = FeatureGateLabStore.reviewProfile(
                root.toString(), FeatureGateCatalog.cachedSnapshot().byIdentity);
        assertEquals(1, review.accepted.size());
        assertEquals(2, review.rejected.size());
    }

    @Test public void failedExportReportsProviderWriteFailure() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            action(fragment, 2);
            var started = Shadows.shadowOf(activity).getNextStartedActivityForResult();
            var uri = android.net.Uri.parse("file:///unregistered-export.json.gz");
            fragment.onActivityResult(started.requestCode, Activity.RESULT_OK, new Intent().setData(uri));
            waitFor("Loaded-value file export failed");
        }
    }

    private static void assertLoadedJsonRejected(byte[] bytes) throws Exception {
        var method = FeatureGateLabFragment.class.getDeclaredMethod("readLoadedJson", byte[].class);
        method.setAccessible(true);
        try {
            method.invoke(null, (Object) bytes);
            fail("import should be rejected");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertNotNull(expected.getCause());
        }
    }

    @Test public void leavingTheLabWhileAChangeRunsDoesNotWedgeIt() throws Exception {
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var fragment = attach(owner.get());
            var reset = FeatureGateLabFragment.class.getDeclaredMethod("reset", boolean.class);
            reset.setAccessible(true);

            // Pressing Back while a reset is still running. The change finishes on the main
            // thread afterwards, and by then the switch it wants to put back is gone.
            reset.invoke(fragment, false);
            fragment.onDestroyView();
            Utils.awaitBackgroundTasksForTests();
            // Robolectric runs a posted runnable on this thread, so the NPE this used to throw
            // would surface right here.
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            var changing = FeatureGateLabFragment.class.getDeclaredField("CHANGING");
            changing.setAccessible(true);
            assertFalse("the Lab stayed marked busy, so every later change would be refused",
                    ((java.util.concurrent.atomic.AtomicBoolean) changing.get(null)).get());

            // And the next change is not turned away.
            ShadowToast.reset();
            reset.invoke(fragment, false);
            Utils.awaitBackgroundTasksForTests();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotEquals("A Lab change is already running", ShadowToast.getTextOfLatestToast());
        }
    }

    @Test public void theMasterSwitchDoesItsStorageOffTheMainThread() throws Exception {
        // Turning overrides on takes the journal lock and does two write-and-verify cycles. On
        // the main thread that is a frozen screen for as long as a settings restore holds that
        // lock, which is exactly what the Lab's other changes were written to avoid.
        try (var owner = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = owner.get();
            var fragment = attach(activity);
            Switch master = findSwitch(fragment.getView());
            boolean before = FeatureGateLabStore.masterEnabled();
            // The hook is static and every Lab change writes it, so an earlier test in this
            // class would otherwise answer for this one.
            forgetLastChangeThread();

            master.performClick();
            settle();

            assertEquals("the switch did not take", !before, FeatureGateLabStore.masterEnabled());
            assertEquals("the switch does not show what was stored", !before, master.isChecked());
            String thread = FeatureGateLabFragment.lastChangeThreadForTests();
            assertNotNull("nothing recorded a Lab change at all", thread);
            assertNotEquals("the master switch wrote to storage on the drawing thread",
                    Looper.getMainLooper().getThread().getName(), thread);
        }
    }

    private static void forgetLastChangeThread() throws Exception {
        var field = FeatureGateLabFragment.class.getDeclaredField("lastChangeThreadForTests");
        field.setAccessible(true);
        field.set(null, null);
    }

    /** Lets a Lab change finish: the storage half, then the part that puts the screen back. */
    @Test public void aLongPressGathersASelectionAndTheActionsActOnAllOfIt() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup().visible()) {
            var activity = controller.get();
            var fragment = attach(activity);
            settle();

            android.widget.ListView list = listOf(fragment);
            assertEquals("the fixture catalogue is not on the screen", 2, list.getCount());
            android.view.View bar = selectionBar(fragment);
            assertEquals("the actions were offered before anything was chosen",
                    android.view.View.GONE, bar.getVisibility());

            // A long press gathers, a tap adds, and a tap on a chosen row takes it back out.
            assertTrue(list.performItemClick(null, 0, list.getItemIdAtPosition(0)));
            assertEquals("a plain tap started a selection",
                    android.view.View.GONE, bar.getVisibility());

            assertTrue(list.getOnItemLongClickListener()
                    .onItemLongClick(list, null, 0, list.getItemIdAtPosition(0)));
            assertEquals(android.view.View.VISIBLE, bar.getVisibility());
            assertEquals("1 gate selected", selectionCountText(fragment));

            assertTrue(list.performItemClick(null, 1, list.getItemIdAtPosition(1)));
            assertEquals("2 gates selected", selectionCountText(fragment));
            assertTrue(list.performItemClick(null, 1, list.getItemIdAtPosition(1)));
            assertEquals("1 gate selected", selectionCountText(fragment));
            assertTrue(list.performItemClick(null, 1, list.getItemIdAtPosition(1)));

            // Disable acts on both, in one operation, and leaves the selection behind it.
            selectionAction(fragment, "Disable").performClick();
            settle();
            var rules = FeatureGateLabStore.rules();
            assertEquals("both gates were not written", 2, rules.size());
            for (var rule : rules) assertEquals("false", rule.value);
            assertEquals("the selection outlived the action it ran",
                    android.view.View.GONE, bar.getVisibility());

            // And one Undo takes both back, which is the whole reason for one operation.
            FeatureGateLabUndo.undo();
            assertTrue(FeatureGateLabStore.rules().isEmpty());
        }
    }

    private static android.widget.ListView listOf(FeatureGateLabFragment fragment) throws Exception {
        var field = FeatureGateLabFragment.class.getDeclaredField("list");
        field.setAccessible(true);
        return (android.widget.ListView) field.get(fragment);
    }

    private static android.view.View selectionBar(FeatureGateLabFragment fragment) throws Exception {
        var field = FeatureGateLabFragment.class.getDeclaredField("selectionBar");
        field.setAccessible(true);
        return (android.view.View) field.get(fragment);
    }

    private static String selectionCountText(FeatureGateLabFragment fragment) throws Exception {
        var field = FeatureGateLabFragment.class.getDeclaredField("selectionCount");
        field.setAccessible(true);
        return ((android.widget.TextView) field.get(fragment)).getText().toString();
    }

    /** The action with this label, found by the description it gives a screen reader. */
    private static android.view.View selectionAction(FeatureGateLabFragment fragment, String label)
            throws Exception {
        android.view.ViewGroup bar = (android.view.ViewGroup) selectionBar(fragment);
        android.view.ViewGroup actions = (android.view.ViewGroup) bar.getChildAt(1);
        for (int index = 0; index < actions.getChildCount(); index++) {
            android.view.View child = actions.getChildAt(index);
            if (label.contentEquals(String.valueOf(child.getContentDescription()))) return child;
        }
        throw new AssertionError("no selection action called " + label);
    }

    private static void settle() throws Exception {
        FeatureGateLabFragment.awaitFileIoForTests();
        Utils.awaitBackgroundTasksForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
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
        FeatureGateLabFragment.awaitFileIoForTests();
        Utils.awaitBackgroundTasksForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        String toast = ShadowToast.getTextOfLatestToast();
        if (toast == null || !toast.startsWith(prefix)) {
            fail("Missing completion: " + toast);
        }
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
    @Test public void awholeSelectionIsForcedInOneOperationAndUndoneInOne() throws Exception {
        // Three gates chosen together. The point of doing it in one operation rather than three
        // is that a selection half applied is worse than one not applied, and that Undo puts the
        // whole thing back rather than the last gate of it.
        var gates = List.of(entry("alpha"), entry("beta"), entry("gamma"));

        assertEquals(3, FeatureGateLabUndo.forceBoolean(gates, false));
        var rules = FeatureGateLabStore.rules();
        assertEquals(3, rules.size());
        for (var rule : rules) {
            assertEquals("a gate in the selection was not forced", "false", rule.value);
            assertTrue("a forced gate was left switched off", rule.enabled);
        }

        FeatureGateLabUndo.undo();
        assertTrue("undo left part of the selection behind",
                FeatureGateLabStore.rules().isEmpty());

        // And the other direction, over rules that already exist, so this is a replacement
        // rather than three fresh writes.
        save("alpha", "false", true);
        assertEquals(3, FeatureGateLabUndo.forceBoolean(gates, true));
        assertEquals(3, FeatureGateLabStore.rules().size());
        for (var rule : FeatureGateLabStore.rules()) {
            assertEquals("true", rule.value);
        }

        FeatureGateLabUndo.undo();
        var afterUndo = FeatureGateLabStore.rules();
        assertEquals("undo did not go back to the one rule there was", 1, afterUndo.size());
        assertEquals("alpha", afterUndo.get(0).key);
        assertEquals("false", afterUndo.get(0).value);
    }

    @Test public void resettingASelectionDropsOnlyItsRulesAndUndoBringsThemBack() throws Exception {
        save("alpha", "true", true);
        save("beta", "true", true);
        save("kept", "true", true);

        assertEquals(2, FeatureGateLabUndo.resetAll(List.of(entry("alpha"), entry("beta"))));
        var rules = FeatureGateLabStore.rules();
        assertEquals(1, rules.size());
        assertEquals("a rule outside the selection was dropped", "kept", rules.get(0).key);

        FeatureGateLabUndo.undo();
        assertEquals(3, FeatureGateLabStore.rules().size());
    }

    @Test public void aSelectionWithNothingToDoIsNotAnOperationAtAll() throws Exception {
        // Nothing to force means no journal entry and no undo point, so an Undo after it still
        // means whatever it meant before. A gate whose type the Lab cannot force a boolean on is
        // skipped rather than written with a value its own catalogue disagrees with.
        var text = new FeatureGateCatalog.Entry("worded", "worded", "abmock", "STRING", true, true,
                List.of(""), List.of(), List.of(), "", "", true, "", "STRING");
        assertEquals(0, FeatureGateLabUndo.forceBoolean(List.of(text), false));
        assertTrue(FeatureGateLabStore.rules().isEmpty());

        assertEquals(0, FeatureGateLabUndo.resetAll(List.of(entry("never_saved"))));
        assertTrue(FeatureGateLabStore.rules().isEmpty());
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
