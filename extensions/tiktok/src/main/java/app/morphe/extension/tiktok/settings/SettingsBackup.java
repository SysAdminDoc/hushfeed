package app.morphe.extension.tiktok.settings;

import android.content.Context;
import android.util.AtomicFile;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.SettingsJson;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** File-backed settings snapshots. Call file and preference writes on a worker thread. */
public final class SettingsBackup {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** The format tag written today, and the one backups made before the rename carry. */
    public static final String FORMAT = "hushfeed-settings";
    public static final String LEGACY_FORMAT = "metra-settings";
    private SettingsBackup() {}

    public enum Failure {
        REJECTED_INPUT,
        ROLLED_BACK,
        RECOVERY_REQUIRED
    }

    /** A restore failure that records whether both stores are back at their prior state. */
    public static final class RestoreException extends Exception {
        private final Failure failure;
        private final boolean rollbackComplete;
        private final boolean recoveryAvailable;

        private RestoreException(Throwable cause, Failure failure, boolean rollbackComplete,
                boolean recoveryAvailable) {
            super(cause.getMessage(), cause);
            this.failure = failure;
            this.rollbackComplete = rollbackComplete;
            this.recoveryAvailable = recoveryAvailable;
        }

        public Failure getFailure() { return failure; }
        public boolean isRollbackComplete() { return rollbackComplete; }
        public boolean isRecoveryAvailable() { return recoveryAvailable; }

        private static RestoreException rejected(Throwable cause) {
            return new RestoreException(cause, Failure.REJECTED_INPUT, true, false);
        }
    }

    private static boolean included(Setting<?> setting) {
        return setting.includeWithImportExport || setting == BaseSettings.DEBUG_LOG_FILTERS;
    }

    public static String create(boolean defaults) throws JSONException, IOException {
        Settings.REGION_SPOOF.get(); // Initialize the complete settings registry.
        JSONObject values = new JSONObject();
        JSONArray keys = new JSONArray();
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (!included(setting)) continue;
            Object value = defaults ? setting.defaultValue : setting.get();
            values.put(setting.key, value instanceof Enum<?> ? ((Enum<?>) value).name() : value);
            keys.put(setting.key);
        }
        JSONObject lab = FeatureGateLabStore.exportSettings();
        if (defaults) lab.put("rules", new JSONArray()).put("master", false).put("acknowledged", false);
        String text = new JSONObject().put("format", FORMAT).put("schema", 1)
                .put("target", FeatureGateLabStore.TARGET_VERSION).put("settings", values)
                .put("setting_keys", keys)
                .put("lab", lab).toString(2);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Backup exceeds 2 MB");
        return text;
    }

    public static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Could not open backup");
        try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Backup exceeds 2 MB");
                bytes.write(buffer, 0, count);
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        }
    }

    public static void restore(Context context, String text, boolean saveUndo) throws Exception {
        Snapshot next;
        try {
            next = parse(text);
        } catch (Exception error) {
            throw RestoreException.rejected(error);
        }
        String previousText = create(false);
        Snapshot previous = parse(previousText);
        Map<String, ?> previousPreferences = new LinkedHashMap<>(
                Setting.preferences.preferences.getAll());
        if (saveUndo) writeUndo(context, previousText);
        try {
            apply(next);
        } catch (Exception error) {
            try { Setting.saveAll(previous.values); } catch (Exception rollback) { error.addSuppressed(rollback); }
            try { FeatureGateLabStore.replaceSettings(previous.rules, previous.master, previous.acknowledged); }
            catch (Exception rollback) { error.addSuppressed(rollback); }
            boolean rollbackComplete = ordinarySettingsMatch(previousPreferences)
                    && labSettingsMatch(previous);
            boolean recoveryAvailable = hasVerifiedUndo(context);
            throw new RestoreException(error,
                    rollbackComplete ? Failure.ROLLED_BACK : Failure.RECOVERY_REQUIRED,
                    rollbackComplete, recoveryAvailable);
        }
    }

    public static void reset(Context context) throws Exception { restore(context, create(true), true); }
    public static void undo(Context context) throws Exception { restore(context, read(undoFile(context).openRead()), false); }
    public static boolean hasUndo(Context context) { return undoFile(context).getBaseFile().isFile(); }

    private static boolean ordinarySettingsMatch(Map<String, ?> expected) {
        try {
            return expected.equals(Setting.preferences.preferences.getAll());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean labSettingsMatch(Snapshot expected) {
        try {
            if (expected.master != FeatureGateLabStore.masterEnabled()
                    || expected.acknowledged != FeatureGateLabStore.warningAcknowledged()) {
                return false;
            }
            List<FeatureGateLabStore.Rule> actual = FeatureGateLabStore.rules();
            if (actual.size() != expected.rules.size()) return false;
            for (FeatureGateLabStore.Rule wanted : expected.rules) {
                FeatureGateLabStore.Rule found = null;
                for (FeatureGateLabStore.Rule candidate : actual) {
                    if (Objects.equals(wanted.id, candidate.id)) {
                        found = candidate;
                        break;
                    }
                }
                if (found == null || !sameRule(wanted, found)) return false;
            }
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean sameRule(FeatureGateLabStore.Rule left, FeatureGateLabStore.Rule right) {
        return Objects.equals(left.manager, right.manager)
                && Objects.equals(left.key, right.key)
                && Objects.equals(left.type, right.type)
                && Objects.equals(left.value, right.value)
                && left.enabled == right.enabled;
    }

    private static boolean hasVerifiedUndo(Context context) {
        if (!hasUndo(context)) return false;
        try (InputStream input = undoFile(context).openRead()) {
            parse(read(input));
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private static void apply(Snapshot snapshot) throws IOException {
        Setting.saveAll(snapshot.values);
        FeatureGateLabStore.replaceSettings(snapshot.rules, snapshot.master, snapshot.acknowledged);
    }

    private static AtomicFile undoFile(Context context) {
        File current = new File(context.getFilesDir(), "hushfeed-settings-undo.json");
        File legacy = new File(context.getFilesDir(), "metra-settings-undo.json");
        // An undo copy saved before the rename stays usable until the next restore writes the new file.
        return new AtomicFile(!current.isFile() && legacy.isFile() ? legacy : current);
    }

    private static void writeUndo(Context context, String text) throws IOException {
        AtomicFile file = undoFile(context);
        var output = file.startWrite();
        try {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
        if (!text.equals(read(file.openRead()))) throw new IOException("Could not verify the undo copy");
    }

    private static Snapshot parse(String text) throws JSONException, IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Invalid backup size");
        Settings.REGION_SPOOF.get();
        JSONObject root = SettingsJson.parseObject(text);
        String format = root.optString("format");
        if (!(FORMAT.equals(format) || LEGACY_FORMAT.equals(format)) || !Integer.valueOf(1).equals(root.get("schema"))
                || !FeatureGateLabStore.TARGET_VERSION.equals(root.optString("target"))) {
            throw new JSONException("Unsupported settings backup or TikTok version");
        }
        JSONObject values = root.getJSONObject("settings"), lab = root.getJSONObject("lab");
        JSONArray required = root.getJSONArray("setting_keys");
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (required.length() == 0 || required.length() != values.length()) throw new JSONException("Incomplete settings backup");
        for (int i = 0; i < required.length(); i++) {
            Object key = required.get(i);
            if (!(key instanceof String) || !keys.add((String) key) || !values.has((String) key)) {
                throw new JSONException("Incomplete settings backup");
            }
        }
        Map<Setting<?>, Object> updates = new LinkedHashMap<>();
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (!included(setting)) continue;
            updates.put(setting, values.has(setting.key)
                    ? convert(setting.defaultValue, values.get(setting.key), setting.key) : setting.defaultValue);
        }
        return new Snapshot(updates, FeatureGateLabStore.parseSettings(lab),
                lab.getBoolean("master"), lab.getBoolean("acknowledged"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object convert(Object fallback, Object value, String key) throws JSONException {
        try {
            if (fallback instanceof Boolean && value instanceof Boolean) return value;
            if (fallback instanceof String && value instanceof String) return value;
            if (fallback instanceof Enum<?> && value instanceof String) {
                return Enum.valueOf(((Enum) fallback).getDeclaringClass(), (String) value);
            }
            if (value instanceof Number) {
                BigDecimal number = new BigDecimal(value.toString());
                if (fallback instanceof Integer) return number.intValueExact();
                if (fallback instanceof Long) return number.longValueExact();
                if (fallback instanceof Float && Float.isFinite(number.floatValue())) return number.floatValue();
            }
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new JSONException("Invalid value for " + key);
        }
        throw new JSONException("Invalid value for " + key);
    }

    private static final class Snapshot {
        final Map<Setting<?>, Object> values;
        final List<FeatureGateLabStore.Rule> rules;
        final boolean master, acknowledged;
        Snapshot(Map<Setting<?>, Object> values, List<FeatureGateLabStore.Rule> rules, boolean master, boolean acknowledged) {
            this.values = values; this.rules = rules; this.master = master; this.acknowledged = acknowledged;
        }
    }
}
