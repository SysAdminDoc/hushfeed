package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.AtomicFile;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.AppLanguage;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
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
    @After public void tearDownStatus() {
        SettingsStatus.diagnosticsEnabled = false;
    }
    @Before public void setup() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.REGION_SPOOF.get();
        for (Setting<?> setting : Setting.allLoadedSettings()) setting.resetToDefault();
        FeatureGateLabStore.resetAllLabData();
        AtomicFile journal = new AtomicFile(new File(Utils.getContext().getFilesDir(), SettingsOperationJournal.FILE_NAME));
        journal.delete();
        new AtomicFile(new File(Utils.getContext().getFilesDir(), "feature-gate-lab-undo.json")).delete();
        SettingsOperationJournal.consumeRecoveryNotice();
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

    @Test public void invalidLabRuleAndTrailingInputLeaveSettingsUntouched() throws Exception {
        // A backup stamped with another TikTok version used to belong in this list. It does not
        // any more: the settings half of a backup does not depend on the TikTok build, so it is
        // restored and only the Lab rules are dropped. That case is its own test below.
        Settings.REGION_SPOOF.save(true);
        String baseline = SettingsBackup.create(false);
        JSONObject wrongLab = new JSONObject(baseline);
        wrongLab.getJSONObject("lab").put("master", "true");
        JSONObject badRule = new JSONObject(baseline);
        badRule.getJSONObject("lab").getJSONArray("rules").put(new JSONObject()
                .put("manager", "abmock").put("key", "bad").put("type", "FLOAT").put("value", "NaN").put("force", true));
        for (String invalid : new String[]{wrongLab.toString(), badRule.toString(), baseline + "garbage", "[]"}) {
            assertThrows(Exception.class, () -> SettingsBackup.restore(Utils.getContext(), invalid, true));
            assertEquals(baseline, SettingsBackup.create(false));
        }
    }

    @Test public void aBackupFromAnotherTikTokVersionRestoresSettingsAndLeavesTheLabAlone() throws Exception {
        // The target stamp is there for the Lab rules, which name gates in one TikTok build. It
        // used to refuse the whole file, so the day this project retargets, every backup anyone
        // holds stops restoring, settings included.
        Settings.BLOCKED_CREATORS.save("from the backup");
        Settings.AUTO_ADVANCE.save(true);
        String backup = SettingsBackup.create(false);
        String otherVersion = new JSONObject(backup).put("target", "40.0.0").toString();

        // State that must survive: a Lab rule this backup knows nothing about.
        Settings.BLOCKED_CREATORS.save("changed since");
        Settings.AUTO_ADVANCE.save(false);
        FeatureGateLabStore.saveRule("abmock", "rule_for_this_build", "INT", "7", true);
        FeatureGateLabStore.setMasterEnabled(true);

        SettingsBackup.restore(Utils.getContext(), otherVersion, true);

        assertEquals("the settings half did not restore", "from the backup", Settings.BLOCKED_CREATORS.get());
        assertTrue("the settings half did not restore", Settings.AUTO_ADVANCE.get());
        assertTrue("the caller was not told the Lab rules were left out",
                SettingsBackup.labRulesWereSkipped(otherVersion));
        assertNotNull("the Lab was emptied by a backup that said nothing about this build",
                FeatureGateLabStore.rule("abmock", "rule_for_this_build", "INT"));
        assertTrue("the Lab master switch was changed by a backup for another build",
                FeatureGateLabStore.masterEnabled());
    }

    @Test public void everyRefusalSaysWhichOneItWas() throws Exception {
        String baseline = SettingsBackup.create(false);
        JSONObject wrongFormat = new JSONObject(baseline).put("format", "something-else");
        JSONObject wrongSchema = new JSONObject(baseline).put("schema", 99);
        JSONObject shortKeys = new JSONObject(baseline).put("setting_keys", new org.json.JSONArray());
        JSONObject badValue = new JSONObject(baseline);
        badValue.getJSONObject("settings").put(Settings.AUTO_ADVANCE.key, "not a boolean");

        assertEquals(SettingsBackup.Reason.FORMAT, reasonFor(wrongFormat.toString()));
        assertEquals(SettingsBackup.Reason.SCHEMA, reasonFor(wrongSchema.toString()));
        assertEquals(SettingsBackup.Reason.INCOMPLETE, reasonFor(shortKeys.toString()));
        assertEquals(SettingsBackup.Reason.VALUE, reasonFor(badValue.toString()));

        // And each of them reaches the user as its own sentence rather than one shared rejection,
        // which is what made a truncated download and a good backup read the same.
        java.util.Set<String> sentences = new java.util.HashSet<>();
        for (String input : new String[]{wrongFormat.toString(), wrongSchema.toString(),
                shortKeys.toString(), badValue.toString()}) {
            sentences.add(sentenceFor(input));
        }
        assertEquals("two refusals share a sentence, so the user cannot tell them apart",
                4, sentences.size());
    }

    private static SettingsBackup.Reason reasonFor(String text) {
        try {
            SettingsBackup.restore(Utils.getContext(), text, true);
        } catch (SettingsBackup.RestoreException rejected) {
            return rejected.getReason();
        } catch (Exception other) {
            throw new AssertionError("expected a RestoreException, got " + other, other);
        }
        throw new AssertionError("that input was accepted");
    }

    /** The sentence the user actually sees. failureMessage is package private one package over. */
    private static String sentenceFor(String text) throws Exception {
        Exception refusal;
        try {
            SettingsBackup.restore(Utils.getContext(), text, true);
            throw new AssertionError("that input was accepted");
        } catch (SettingsBackup.RestoreException rejected) {
            refusal = rejected;
        }
        Class<?> preference = Class.forName(
                "app.morphe.extension.tiktok.settings.preference.SettingsBackupPreference");
        java.lang.reflect.Method message =
                preference.getDeclaredMethod("failureMessage", int.class, Exception.class);
        message.setAccessible(true);
        return (String) message.invoke(null, 7312 /* IMPORT */, refusal);
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

    @Test public void partialRollbackReportsRecoveryAndKeepsPersistedValuesVisible() throws Exception {
        var app = Utils.getContext();
        FeatureGateLabStore.setMasterEnabled(true);
        JSONObject next = new JSONObject(SettingsBackup.create(false));
        next.getJSONObject("settings").put(Settings.REGION_SPOOF.key, true);
        next.getJSONObject("lab").put("master", false);
        var original = Setting.preferences.preferences;
        var normalFailure = new java.util.concurrent.atomic.AtomicBoolean();
        var labCommits = new java.util.concurrent.atomic.AtomicInteger();
        var normal = failingCommitsWithoutApply(original, normalFailure::get, () -> {});
        var lab = failingCommitsWithoutApply(app.getSharedPreferences("morphe_feature_gate_lab", 0),
                () -> labCommits.get() == 1, () -> {
                    if (labCommits.incrementAndGet() == 1) normalFailure.set(true);
                });
        var field = app.morphe.extension.shared.settings.preference.SharedPrefCategory.class
                .getDeclaredField("preferences");
        field.setAccessible(true);
        field.set(Setting.preferences, normal);
        Utils.setContext(new android.content.ContextWrapper(app) {
            @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                return name.equals("morphe_feature_gate_lab") ? lab : super.getSharedPreferences(name, mode);
            }
        });
        try {
            try {
                SettingsBackup.restore(Utils.getContext(), next.toString(), true);
                fail("restore should report the failed rollback");
            } catch (SettingsBackup.RestoreException error) {
                assertEquals(SettingsBackup.Failure.RECOVERY_REQUIRED, error.getFailure());
                assertFalse(error.isRollbackComplete());
                assertTrue(error.isRecoveryAvailable());
            }
            assertEquals(Boolean.TRUE, Setting.preferences.preferences.getAll().get(Settings.REGION_SPOOF.key));
            assertTrue(FeatureGateLabStore.masterEnabled());
            assertTrue(SettingsBackup.hasUndo(app));
        } finally {
            field.set(Setting.preferences, original);
            Utils.setContext(app);
        }
        SettingsBackup.undo(app);
        assertFalse(Settings.REGION_SPOOF.get());
        assertTrue(FeatureGateLabStore.masterEnabled());
    }

    @Test public void secondaryProcessCanReadButCannotOverwritePersistentSettings() {
        var app = Utils.getContext();
        Settings.REGION_SPOOF.save(true);
        FeatureGateLabStore.setMasterEnabled(true);
        FeatureGateLabStore.saveRule("abmock", "secondary_test", "BOOLEAN", "true", true);
        var secondary = new android.content.ContextWrapper(app) {
            @Override public android.content.pm.ApplicationInfo getApplicationInfo() {
                android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo(
                        super.getApplicationInfo());
                info.processName = app.getPackageName() + ":secondary";
                return info;
            }
        };
        Utils.setContext(secondary);
        try {
            assertFalse(Utils.isMainProcess());
            assertTrue(Settings.REGION_SPOOF.get());
            assertTrue(FeatureGateLabStore.masterEnabled());
            assertNotNull(FeatureGateLabStore.rule("abmock", "secondary_test", "BOOLEAN"));
            Settings.REGION_SPOOF.save(false);
            FeatureGateLabStore.setMasterEnabled(false);
            FeatureGateLabStore.deleteRule("abmock", "secondary_test", "BOOLEAN");
            assertTrue(Settings.REGION_SPOOF.get());
            assertTrue(Setting.preferences.preferences.getBoolean(Settings.REGION_SPOOF.key, false));
            assertTrue(FeatureGateLabStore.masterEnabled());
            assertNotNull(FeatureGateLabStore.rule("abmock", "secondary_test", "BOOLEAN"));
        } finally {
            Utils.setContext(app);
        }
    }

    @Test public void interruptedSettingsJournalRestoresThePriorStateAfterMixedWrites() throws Exception {
        var app = Utils.getContext();
        String before = SettingsBackup.create(false);
        JSONObject after = new JSONObject(before);
        after.getJSONObject("settings").put(Settings.REGION_SPOOF.key, true);
        after.getJSONObject("lab").put("master", true);

        Settings.REGION_SPOOF.save(true);
        writeJournal("settings", before, after.toString());
        assertEquals(SettingsOperationJournal.Recovery.RECOVERED_PRIOR,
                SettingsOperationJournal.initialize(app));
        assertFalse(Settings.REGION_SPOOF.get());
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertFalse(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME).isFile());
    }

    @Test public void committedSettingsJournalIsClearedWithoutRevertingTheCommit() throws Exception {
        var app = Utils.getContext();
        String before = SettingsBackup.create(false);
        Settings.REGION_SPOOF.save(true);
        FeatureGateLabStore.setMasterEnabled(true);
        String after = SettingsBackup.create(false);
        writeJournal("settings", before, after);

        assertEquals(SettingsOperationJournal.Recovery.ALREADY_COMMITTED,
                SettingsOperationJournal.initialize(app));
        assertTrue(Settings.REGION_SPOOF.get());
        assertTrue(FeatureGateLabStore.masterEnabled());
        assertFalse(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME).isFile());
    }

    @Test public void interruptedLabJournalRestoresPriorRulesAndKeepsTheUndoCopy() throws Exception {
        var app = Utils.getContext();
        String before = FeatureGateLabStore.exportSettings().toString();
        FeatureGateLabStore.saveRule("abmock", "journal_gate", "BOOLEAN", "true", true);
        String after = FeatureGateLabStore.exportSettings().toString();
        try (var undo = new FileOutputStream(new File(app.getFilesDir(), "feature-gate-lab-undo.json"))) {
            undo.write(after.getBytes(StandardCharsets.UTF_8));
        }
        FeatureGateLabStore.saveRule("abmock", "journal_gate", "BOOLEAN", "false", true);
        writeJournal("lab", before, after);

        assertEquals(SettingsOperationJournal.Recovery.RECOVERED_PRIOR,
                SettingsOperationJournal.initialize(app));
        assertTrue(FeatureGateLabStore.rules().isEmpty());
        assertTrue(new File(app.getFilesDir(), "feature-gate-lab-undo.json").isFile());
        assertFalse(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME).isFile());
    }

    @Test public void malformedSettingsJournalStaysVisibleWithoutChangingValues() throws Exception {
        var app = Utils.getContext();
        Settings.REGION_SPOOF.save(true);
        try (var output = new FileOutputStream(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME))) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(SettingsOperationJournal.Recovery.MALFORMED,
                SettingsOperationJournal.initialize(app));
        assertEquals(SettingsOperationJournal.Recovery.MALFORMED,
                SettingsOperationJournal.consumeRecoveryNotice());
        assertEquals(SettingsOperationJournal.Recovery.NONE,
                SettingsOperationJournal.consumeRecoveryNotice());
        assertTrue(Settings.REGION_SPOOF.get());
        new AtomicFile(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME)).delete();
    }

    @Test public void journalIntentIsDurableBeforeASettingsMutationRuns() throws Exception {
        var app = Utils.getContext();
        String before = SettingsBackup.create(false);
        SettingsOperationJournal.Operation operation = SettingsOperationJournal.acquire(app);
        try {
            operation.recordSettings(before, before);
            JSONObject journal = new JSONObject(SettingsBackup.read(new AtomicFile(
                    new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME)).openRead()));
            assertEquals("settings", journal.getString("kind"));
            assertEquals(before, journal.getString("before"));
            assertEquals(before, journal.getString("after"));
        } finally {
            operation.abort();
        }
    }

    @Test public void anAtomicFileBackupIsReconciledAfterAWriteCrash() throws Exception {
        var app = Utils.getContext();
        String before = SettingsBackup.create(false);
        JSONObject after = new JSONObject(before);
        after.getJSONObject("settings").put(Settings.REGION_SPOOF.key, true);
        Settings.REGION_SPOOF.save(true);
        writeJournal("settings", before, after.toString());

        File base = new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME);
        File backup = new File(base.getPath() + ".bak");
        assertTrue(base.renameTo(backup));
        assertFalse(base.isFile());
        assertTrue(backup.isFile());

        assertEquals(SettingsOperationJournal.Recovery.ALREADY_COMMITTED,
                SettingsOperationJournal.initialize(app));
        assertTrue(Settings.REGION_SPOOF.get());
        assertFalse(base.exists());
        assertFalse(backup.exists());
    }

    @Test public void labJournalMatchingIgnoresRuleOrder() throws Exception {
        var app = Utils.getContext();
        JSONArray ordered = new JSONArray()
                .put(new JSONObject().put("manager", "abmock").put("key", "order_a")
                        .put("type", "BOOLEAN").put("value", "true").put("force", true))
                .put(new JSONObject().put("manager", "abmock").put("key", "order_b")
                        .put("type", "INT").put("value", "3").put("force", false));
        JSONObject before = FeatureGateLabStore.exportSettings();
        JSONObject after = new JSONObject().put("schema", 1).put("target", "TikTok global")
                .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION).put("rules", ordered)
                .put("master", false).put("acknowledged", false);
        JSONArray reversed = new JSONArray().put(ordered.get(1)).put(ordered.get(0));
        JSONObject current = new JSONObject(after.toString()).put("rules", reversed);
        FeatureGateLabStore.replaceSettings(FeatureGateLabStore.parseSettings(current), false, false);
        writeJournal("lab", before.toString(), after.toString());

        assertEquals(SettingsOperationJournal.Recovery.ALREADY_COMMITTED,
                SettingsOperationJournal.initialize(app));
        assertEquals(2, FeatureGateLabStore.rules().size());
        assertFalse(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME).isFile());
    }

    private static void writeJournal(String kind, String before, String after) throws Exception {
        var app = Utils.getContext();
        JSONObject root = new JSONObject().put("schema", 1).put("kind", kind)
                .put("before", before).put("after", after);
        AtomicFile file = new AtomicFile(new File(app.getFilesDir(), SettingsOperationJournal.FILE_NAME));
        try (var output = file.startWrite()) {
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        }
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

    @Test public void anOlderBackupWithRemovedSettingKeyStillImports() throws Exception {
        Settings.MAX_VIDEO_SECONDS.save(81);
        JSONObject root = new JSONObject(SettingsBackup.create(false));
        root.getJSONObject("settings").put("comment_translation_excluded_languages", "es");
        root.getJSONArray("setting_keys").put("comment_translation_excluded_languages");
        Settings.MAX_VIDEO_SECONDS.save(0);

        SettingsBackup.restore(Utils.getContext(), root.toString(), true);

        assertEquals(81, (int) Settings.MAX_VIDEO_SECONDS.get());
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

    private static android.content.SharedPreferences failingCommitsWithoutApply(
            android.content.SharedPreferences target, java.util.function.BooleanSupplier fail,
            Runnable attempted) {
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                target.getClass().getClassLoader(), new Class[]{android.content.SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(target, args);
                    var editor = target.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(editor.getClass().getClassLoader(),
                            new Class[]{android.content.SharedPreferences.Editor.class}, (editorProxy, call, values) -> {
                                if (call.getName().equals("commit")) {
                                    attempted.run();
                                    if (fail.getAsBoolean()) return false;
                                }
                                Object result = call.invoke(editor, values);
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
        }
    }

    private static void waitFor(String message) throws Exception {
        Utils.awaitBackgroundTasksForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        String toast = ShadowToast.getTextOfLatestToast();
        if (!message.equals(toast)) {
            fail("Missing completion toast: " + toast);
        }
    }

    @Test public void aBackupCannotPlantANumberTheDialogWouldRefuse() throws Exception {
        // Every numeric setting the dialog bounds, bounded again where the value is stored.
        // A backup file is written by hand or shared by somebody else, and until now the
        // only thing enforcing a range was a dialog it never went through.
        String backup = SettingsBackup.create(false);
        JSONObject root = new JSONObject(backup);
        JSONObject values = root.getJSONObject("settings");
        values.put("edge_seek_seconds", 100000);
        values.put("seen_video_retention_days", -12);
        values.put("max_video_seconds", 999999999);
        values.put("caption_text_size", 400);
        SettingsBackup.restore(Utils.getContext(), root.toString(), true);

        assertEquals(60, (int) Settings.EDGE_SEEK_SECONDS.get());
        assertEquals(0, (int) Settings.SEEN_VIDEO_RETENTION_DAYS.get());
        assertEquals(86400, (int) Settings.MAX_VIDEO_SECONDS.get());
        assertEquals(48, (int) Settings.CAPTION_TEXT_SIZE.get());

        // A number inside the range is left exactly alone.
        values.put("edge_seek_seconds", 12);
        SettingsBackup.restore(Utils.getContext(), root.toString(), true);
        assertEquals(12, (int) Settings.EDGE_SEEK_SECONDS.get());
    }

    @Test public void theSuggestedBackupNameIsOneAPersonCanRead() {
        // It was epoch milliseconds, so two backups a minute apart were indistinguishable in the
        // picker and neither said when it was made. The other two exports already used this.
        String name = app.morphe.extension.tiktok.settings.preference.SettingsBackupPreference
                .suggestedExportName();
        assertTrue("the picker would show " + name,
                name.matches("hushfeed-settings-\\d{8}-\\d{6}\\.json"));
    }
}
