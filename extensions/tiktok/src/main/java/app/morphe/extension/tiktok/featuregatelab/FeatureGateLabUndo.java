package app.morphe.extension.tiktok.featuregatelab;

import android.util.AtomicFile;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.SettingsJson;
import app.morphe.extension.tiktok.settings.SettingsBackup;
import app.morphe.extension.tiktok.settings.SettingsOperationJournal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/** Worker-thread recovery for resets and imports; unrelated patch settings are untouched. */
final class FeatureGateLabUndo {
    private static Runnable observationsUndo;

    private FeatureGateLabUndo() {}

    static synchronized void reset(boolean allData) throws Exception {
        replace(List.of(), !allData && FeatureGateLabStore.masterEnabled(),
                !allData && FeatureGateLabStore.warningAcknowledged(), allData);
    }

    static synchronized void importRules(FeatureGateLabStore.ImportReview review) throws Exception {
        if (review.accepted.isEmpty()) return;
        var merged = new LinkedHashMap<String, FeatureGateLabStore.Rule>();
        for (var rule : FeatureGateLabStore.rules()) merged.put(rule.id, rule);
        for (var rule : review.accepted) {
            merged.put(rule.id, new FeatureGateLabStore.Rule(rule.id, rule.manager, rule.key,
                    rule.type, rule.value, false, rule.updatedAtMs));
        }
        replace(new ArrayList<>(merged.values()), FeatureGateLabStore.masterEnabled(),
                FeatureGateLabStore.warningAcknowledged(), false);
    }

    private static void replace(List<FeatureGateLabStore.Rule> rules, boolean master,
            boolean acknowledged, boolean clearObservations) throws Exception {
        SettingsOperationJournal.Operation operation = SettingsOperationJournal.acquire(Utils.getContext());
        boolean closed = false;
        try {
            JSONObject before = FeatureGateLabStore.exportSettings();
            FeatureGateLabStore.parseSettings(before);
            Runnable observations = clearObservations ? SettingsManagerObservationRecorder.checkpoint() : null;
            AtomicFile file = file();
            String text = before.toString();
            writeUndo(file, text);
            operation.recordLab(text, replacement(rules, master, acknowledged).toString());
            observationsUndo = observations;
            try {
                FeatureGateLabStore.replaceSettings(rules, master, acknowledged);
                if (clearObservations) SettingsManagerObservationRecorder.clear();
                operation.complete();
                closed = true;
            } catch (Exception error) {
                try { apply(before); } catch (Exception recovery) { error.addSuppressed(recovery); }
                boolean rollbackComplete = matches(before);
                if (rollbackComplete) operation.complete();
                else operation.retainForRecovery();
                closed = true;
                throw error;
            }
        } finally {
            if (!closed) operation.abort();
        }
    }

    static synchronized void undo() throws Exception {
        SettingsOperationJournal.Operation operation = SettingsOperationJournal.acquire(Utils.getContext());
        boolean closed = false;
        try {
            JSONObject saved;
            try (var input = file().openRead()) {
                saved = SettingsJson.parseObject(SettingsBackup.read(input));
                FeatureGateLabStore.parseSettings(saved);
            }
            JSONObject before = FeatureGateLabStore.exportSettings();
            operation.recordLab(before.toString(), saved.toString());
            try {
                apply(saved);
                operation.complete();
                closed = true;
            } catch (Exception error) {
                try { apply(before); } catch (Exception recovery) { error.addSuppressed(recovery); }
                boolean rollbackComplete = matches(before);
                if (rollbackComplete) operation.complete();
                else operation.retainForRecovery();
                closed = true;
                throw error;
            }
            if (observationsUndo != null) observationsUndo.run();
        } finally {
            if (!closed) operation.abort();
        }
    }

    private static void writeUndo(AtomicFile file, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 2 * 1024 * 1024) throw new IOException("Lab undo copy exceeds 2 MB");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
        try (var input = file.openRead()) {
            if (!text.equals(SettingsBackup.read(input))) throw new IOException("Could not verify Lab undo copy");
        }
    }

    private static JSONObject replacement(List<FeatureGateLabStore.Rule> rules, boolean master,
            boolean acknowledged) throws Exception {
        JSONObject root = new JSONObject().put("schema", 1).put("target", "TikTok global")
                .put("tiktok_version", FeatureGateLabStore.TARGET_VERSION);
        JSONArray items = new JSONArray();
        for (FeatureGateLabStore.Rule rule : rules) {
            items.put(new JSONObject().put("manager", rule.manager).put("key", rule.key)
                    .put("type", rule.type).put("value", rule.value).put("force", rule.enabled));
        }
        return root.put("rules", items).put("master", master).put("acknowledged", acknowledged);
    }

    private static boolean matches(JSONObject expected) {
        try {
            return expected.toString().equals(FeatureGateLabStore.exportSettings().toString());
        } catch (Exception error) {
            return false;
        }
    }

    private static void apply(JSONObject saved) throws Exception {
        FeatureGateLabStore.replaceSettings(FeatureGateLabStore.parseSettings(saved),
                saved.getBoolean("master"), saved.getBoolean("acknowledged"));
    }

    private static AtomicFile file() throws IOException {
        if (Utils.getContext() == null) throw new IOException("Lab storage unavailable");
        return new AtomicFile(new File(Utils.getContext().getFilesDir(), "feature-gate-lab-undo.json"));
    }
}
