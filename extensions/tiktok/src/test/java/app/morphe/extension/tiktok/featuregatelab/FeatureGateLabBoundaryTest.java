package app.morphe.extension.tiktok.featuregatelab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** State, import, coercion and concurrency boundaries for the Feature Gate Lab. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FeatureGateLabBoundaryTest {
    private boolean recorderEnabled;

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        recorderEnabled = SettingsStatus.featureGateRecorderEnabled;
        SettingsStatus.featureGateRecorderEnabled = true;
        FeatureGateLearnMode.cancel();
        SettingsManagerObservationRecorder.clear();
        FeatureGateLabStore.resetAllLabData();
        FeatureGateLabRuntime.clearTriggered();
        FeatureGateLabRuntime.reloadRules();
    }

    @After
    public void tearDown() {
        FeatureGateLearnMode.cancel();
        SettingsManagerObservationRecorder.clear();
        FeatureGateLabStore.resetAllLabData();
        SettingsStatus.featureGateRecorderEnabled = recorderEnabled;
    }

    @Test
    public void malformedScalarRulesKeepNativeValuesAndDoNotTrigger() {
        FeatureGateLabStore.saveRule("abmock", "bad_boolean", "BOOLEAN", "maybe", true);
        FeatureGateLabStore.saveRule("abmock", "bad_float", "FLOAT", "NaN", true);
        FeatureGateLabStore.saveRule("abmock", "bad_double", "DOUBLE", "Infinity", true);
        FeatureGateLabStore.setMasterEnabled(true);

        assertTrue(FeatureGateLabRuntime.overrideBoolean("bad_boolean", true));
        assertFalse(FeatureGateLabRuntime.overrideBoolean("bad_boolean", false));
        assertEquals(1.25f, FeatureGateLabRuntime.overrideFloat("bad_float", 1.25f), 0f);
        assertEquals(2.5d, FeatureGateLabRuntime.overrideDouble("bad_double", 2.5d), 0d);
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "bad_boolean", "BOOLEAN"));
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "bad_float", "FLOAT"));
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "bad_double", "DOUBLE"));

        FeatureGateLabStore.saveRule(
                FeatureGateLabStore.MANAGER_PIA_ACTIVITY_CENTER,
                "bad_center_boolean", "BOOLEAN", "maybe", true);
        FeatureGateLabStore.saveRule(
                FeatureGateLabStore.MANAGER_PIA_ACTIVITY_CENTER,
                "bad_center_double", "DOUBLE", "Infinity", true);
        FeatureGateLabStore.setMasterEnabled(true);
        String inner = new Uri.Builder().scheme("https").authority("api.example")
                .appendPath("activity_center").appendQueryParameter("dev_fg_json", "{}").build().toString();
        String schema = new Uri.Builder().scheme("https").authority("wrapper.example")
                .appendQueryParameter("url", inner).build().toString();
        assertEquals(schema, FeatureGateLabRuntime.transformActivityCenterSchema(schema));
    }

    @Test
    public void masterAndResetCyclesClearRuntimeStateBeforeReenablingRules() {
        FeatureGateLabStore.saveRule("abmock", "cycle_gate", "BOOLEAN", "true", true);
        FeatureGateLabStore.setMasterEnabled(true);
        assertTrue(FeatureGateLabRuntime.overrideBoolean("cycle_gate", false));
        assertTrue(FeatureGateLabRuntime.isTriggered("abmock", "cycle_gate", "BOOLEAN"));

        FeatureGateLabRuntime.overridePlayerValue("cycle_player", Float.class, 1.0f);
        assertTrue(FeatureGateLabRuntime.wasPlayerObserved("cycle_player"));

        FeatureGateLabStore.setMasterEnabled(false);
        assertFalse(FeatureGateLabRuntime.overrideBoolean("cycle_gate", false));
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "cycle_gate", "BOOLEAN"));
        assertFalse(FeatureGateLabRuntime.wasPlayerObserved("cycle_player"));

        FeatureGateLabStore.setMasterEnabled(true);
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "cycle_gate", "BOOLEAN"));
        assertTrue(FeatureGateLabRuntime.overrideBoolean("cycle_gate", false));

        FeatureGateLabStore.resetAllLabData();
        assertFalse(FeatureGateLabStore.masterEnabled());
        assertFalse(FeatureGateLabRuntime.overrideBoolean("cycle_gate", false));
        assertFalse(FeatureGateLabRuntime.isTriggered("abmock", "cycle_gate", "BOOLEAN"));
        assertFalse(FeatureGateLabRuntime.wasPlayerObserved("cycle_player"));
    }

    @Test
    public void profileImportKeepsAcceptedRulesDisabledUntilExplicitlyEnabled() throws Exception {
        FeatureGateCatalog.Entry entry = new FeatureGateCatalog.Entry(
                "import_gate", "Import gate", FeatureGateLabStore.MANAGER_ABMOCK, "BOOLEAN",
                true, true, List.of(), List.of(), List.of(), "", "", false, null, null);
        JSONObject root = new JSONObject()
                .put("schema", 1)
                .put("target", "TikTok global")
                .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION)
                .put("rules", new org.json.JSONArray().put(new JSONObject()
                        .put("manager", FeatureGateLabStore.MANAGER_ABMOCK)
                        .put("key", "import_gate")
                        .put("type", "BOOLEAN")
                        .put("value", "true")));

        FeatureGateLabStore.ImportReview review = FeatureGateLabStore.reviewProfile(
                root.toString(), Map.of(entry.identity(), entry));
        assertEquals(1, review.accepted.size());
        assertFalse(review.accepted.get(0).enabled);

        FeatureGateLabUndo.importRules(review);
        assertFalse(FeatureGateLabStore.rule(
                FeatureGateLabStore.MANAGER_ABMOCK, "import_gate", "BOOLEAN").enabled);
        FeatureGateLabStore.setMasterEnabled(true);
        assertFalse(FeatureGateLabRuntime.overrideBoolean("import_gate", false));
    }

    @Test
    public void recorderStopsWhenDisabledAndStartsWithAFreshReadSet() throws Exception {
        String key = "toggle_" + System.nanoTime();
        FeatureGateLearnMode.cancel();
        SettingsStatus.featureGateRecorderEnabled = false;
        FeatureGateLearnMode.begin();
        assertFalse(FeatureGateLearnMode.isRecording());
        FeatureGateLearnMode.observe("test", key, "INT", 1);
        assertFalse(FeatureGateLearnMode.isRecording());

        SettingsStatus.featureGateRecorderEnabled = true;
        FeatureGateLearnMode.begin();
        FeatureGateLearnMode.observe("test", key, "INT", 1);
        assertTrue(FeatureGateLearnMode.isRecording());

        SettingsStatus.featureGateRecorderEnabled = false;
        FeatureGateLearnMode.observe("test", key, "INT", 2);
        assertFalse(FeatureGateLearnMode.isRecording());

        SettingsStatus.featureGateRecorderEnabled = true;
        FeatureGateLearnMode.begin();
        FeatureGateLearnMode.observe("test", key, "INT", 2);
        JSONObject report = new JSONObject(FeatureGateLearnMode.stopAndBuildReport());
        assertEquals(1, report.getInt("gate_count"));
        assertEquals("new", report.getJSONArray("gates").getJSONObject(0).getString("state"));
    }

    @Test
    public void observationLimitRemainsBoundedWhenCallsArriveTogether() throws Exception {
        SettingsManagerObservationRecorder.clear();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                final int offset = worker * 512;
                tasks.add(executor.submit(() -> {
                    for (int index = 0; index < 512; index++) {
                        SettingsManagerObservationRecorder.observeWithoutDefault(
                                "parallel_" + (offset + index), String.class, "value");
                    }
                }));
            }
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(2048, SettingsManagerObservationRecorder.size());
    }

    @Test public void aDeeplyNestedStructuredValueIsRefusedRatherThanKillingTheWorker() {
        // A rule's value is a string inside the backup, so the depth check on the document
        // around it says nothing about what the string holds. The platform parser recurses once
        // per level and raises StackOverflowError, which is an Error: it walked past the catch
        // in the restore and the catch in the preference, and took the process with it.
        int depth = 30_000;
        StringBuilder nested = new StringBuilder(2 * depth + 8).append("{\"a\":");
        for (int level = 0; level < depth; level++) nested.append('[');
        for (int level = 0; level < depth; level++) nested.append(']');
        String value = nested.append('}').toString();
        assertTrue("the probe must stay under the length check it is not testing",
                value.length() < 64 * 1024);

        assertEquals("invalid structured value",
                FeatureGateLabStore.validateValue("OBJECT", value));
        // The positive control: an ordinary structured value still goes through.
        assertNull(FeatureGateLabStore.validateValue("OBJECT", "{\"enable\":true}"));
        assertEquals("select at least one field",
                FeatureGateLabStore.validateValue("OBJECT", "{}"));
    }

    @Test
    public void aRuleSavedWhileTheSnapshotIsBuildingIsNotLost() {
        // A gate thread reads the rules to build its snapshot; the UI thread saves a rule and
        // clears the snapshot; the gate thread then publishes the rules it read before the save,
        // and the new rule stays invisible until something else happens to reload. The hook
        // creates that overlap from inside the build.
        FeatureGateLabStore.setMasterEnabled(true);
        FeatureGateLabRuntime.reloadRules();
        FeatureGateLabRuntime.rulesReadHook = () -> {
            FeatureGateLabRuntime.rulesReadHook = null;
            FeatureGateLabStore.saveRule("abmock", "saved_mid_build", "BOOLEAN", "true", true);
        };
        try {
            FeatureGateLabRuntime.overrideBoolean("saved_mid_build", false);
        } finally {
            FeatureGateLabRuntime.rulesReadHook = null;
        }

        assertTrue("the rule saved while the snapshot was building never took effect",
                FeatureGateLabRuntime.overrideBoolean("saved_mid_build", false));
    }

    @Test
    public void gateReadsBeforeTikTokHandsOverAContextDoNotKeepTakingTheMonitor() throws Exception {
        // Before Utils has a context the snapshot could not be built, and a null was rebuilt on
        // every read, so every gate read on every thread went through the class monitor at the
        // point in start-up where the host reads gates hardest.
        java.lang.reflect.Field held = Utils.class.getDeclaredField("context");
        held.setAccessible(true);
        Object previous = held.get(null);
        try {
            held.set(null, null);
            FeatureGateLabRuntime.reloadRules();
            FeatureGateLabRuntime.snapshotMonitorEntries = 0;
            for (int read = 0; read < 1000; read++) {
                FeatureGateLabRuntime.overrideBoolean("read_before_context", true);
            }
            assertTrue("1,000 gate reads with no context took the monitor more than once",
                    FeatureGateLabRuntime.snapshotMonitorEntries <= 1);
        } finally {
            held.set(null, previous);
        }

        // And the snapshot built without a context does not outlive it, which would leave every
        // rule dead for the rest of the process.
        FeatureGateLabRuntime.snapshotMonitorEntries = 0;
        FeatureGateLabRuntime.overrideBoolean("read_before_context", true);
        assertTrue("the snapshot from before the context arrived was never rebuilt",
                FeatureGateLabRuntime.snapshotMonitorEntries > 0);
    }

    @Test
    public void aSwitchedOffRecorderTakesNoLockOnTheGateReadPath() throws Exception {
        // The Lab's boundaries run for every AB, live settings and player config read TikTok
        // makes, on whatever thread makes it, and the Recorder patch that gives them something
        // to do ships off. Taking the class monitor before checking that put all of it behind
        // one lock.
        SettingsStatus.featureGateRecorderEnabled = false;
        FeatureGateLearnMode.cancel();
        FeatureGateLearnMode.monitorEntries = 0;

        int threads = 8;
        int callsPerThread = 1250;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<java.util.concurrent.Future<?>> running = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                running.add(pool.submit(() -> {
                    for (int call = 0; call < callsPerThread; call++) {
                        FeatureGateLabRuntime.overrideBoolean("gate_read_under_load", true);
                    }
                }));
            }
            for (java.util.concurrent.Future<?> task : running) {
                task.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals("10,000 gate reads with the Recorder off still serialised on the monitor",
                0, FeatureGateLearnMode.monitorEntries);

        // The counter is live, so the zero above is a measurement rather than a dead field.
        SettingsStatus.featureGateRecorderEnabled = true;
        FeatureGateLabRuntime.overrideBoolean("gate_read_under_load", true);
        assertTrue("the counter never moves, so it cannot show the monitor being taken",
                FeatureGateLearnMode.monitorEntries > 0);
    }
}
