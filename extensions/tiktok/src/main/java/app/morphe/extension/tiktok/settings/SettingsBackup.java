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

    /**
     * Why a backup was refused.
     *
     * <p>Every one of these used to reach the user as "The settings backup was rejected", so a
     * truncated download, a file from a newer Hushfeed and a perfectly good backup all read the
     * same and there was nothing to act on.
     */
    public enum Reason {
        SIZE,
        ENCODING,
        /** Readable text that is not JSON at all, which is what a part-finished download is. */
        DAMAGED,
        FORMAT,
        SCHEMA,
        INCOMPLETE,
        VALUE,
        UNKNOWN
    }

    /** Carries {@link Reason} out of the parse, which throws JSONException and IOException. */
    static final class RejectedBackup extends JSONException {
        final Reason reason;

        RejectedBackup(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** A restore failure that records whether both stores are back at their prior state. */
    public static final class RestoreException extends Exception {
        private final Failure failure;
        private final Reason reason;
        private final boolean rollbackComplete;
        private final boolean recoveryAvailable;

        private RestoreException(Throwable cause, Failure failure, Reason reason,
                boolean rollbackComplete, boolean recoveryAvailable) {
            super(cause.getMessage(), cause);
            this.failure = failure;
            this.reason = reason;
            this.rollbackComplete = rollbackComplete;
            this.recoveryAvailable = recoveryAvailable;
        }

        private RestoreException(Throwable cause, Failure failure, boolean rollbackComplete,
                boolean recoveryAvailable) {
            this(cause, failure, Reason.UNKNOWN, rollbackComplete, recoveryAvailable);
        }

        public Failure getFailure() { return failure; }
        /** Only meaningful for {@link Failure#REJECTED_INPUT}. */
        public Reason getReason() { return reason; }
        public boolean isRollbackComplete() { return rollbackComplete; }
        public boolean isRecoveryAvailable() { return recoveryAvailable; }

        private static RestoreException rejected(Throwable cause) {
            return new RestoreException(cause, Failure.REJECTED_INPUT, reasonOf(cause), true, false);
        }

        private static Reason reasonOf(Throwable cause) {
            if (cause instanceof RejectedBackup) return ((RejectedBackup) cause).reason;
            if (cause instanceof java.nio.charset.CharacterCodingException) return Reason.ENCODING;
            if (cause instanceof IOException) {
                String message = cause.getMessage();
                if (message != null && message.contains("2 MB")) return Reason.SIZE;
            }
            return Reason.UNKNOWN;
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
        SettingsOperationJournal.Operation operation = SettingsOperationJournal.acquire(context);
        restoreWithOperation(context, text, saveUndo, next, operation);
    }

    public static void reset(Context context) throws Exception {
        SettingsOperationJournal.Operation operation = SettingsOperationJournal.acquire(context);
        try {
            String text = create(true);
            Snapshot next;
            try {
                next = parse(text);
            } catch (Exception error) {
                throw RestoreException.rejected(error);
            }
            restoreWithOperation(context, text, true, next, operation);
        } catch (Exception error) {
            operation.abort();
            throw error;
        }
    }

    private static void restoreWithOperation(Context context, String text, boolean saveUndo,
            Snapshot next, SettingsOperationJournal.Operation operation) throws Exception {
        boolean closed = false;
        try {
            String previousText = create(false);
            Snapshot previous = parse(previousText);
            Map<String, ?> previousPreferences = new LinkedHashMap<>(
                    Setting.preferences.preferences.getAll());
            if (saveUndo) writeUndo(context, previousText);
            operation.recordSettings(previousText, text);
            // Whether the apply below got as far as the Lab. It writes the ordinary settings
            // first, so a failure in those never reaches the Lab at all, and reading
            // labIncluded instead put the Lab back on every same-version failure: the common
            // one, and the only case this guard exists for.
            boolean[] touchedLab = new boolean[1];
            try {
                applyForJournal(next, touchedLab);
                operation.complete();
                closed = true;
            } catch (Exception error) {
                try { Setting.saveAll(previous.values); } catch (Exception rollback) { error.addSuppressed(rollback); }
                // Only put the Lab back when the apply above reached it. Writing the same
                // rules again is not free: it raises a restart notice for a store the failed
                // restore never touched.
                if (touchedLab[0]) {
                    try {
                        FeatureGateLabStore.replaceSettings(
                                previous.rules, previous.master, previous.acknowledged, true);
                    }
                    catch (Exception rollback) { error.addSuppressed(rollback); }
                }
                boolean rollbackComplete = ordinarySettingsMatch(previousPreferences)
                        && labSettingsMatch(previous);
                boolean recoveryAvailable = hasVerifiedUndo(context);
                if (rollbackComplete) operation.complete();
                else operation.retainForRecovery();
                closed = true;
                throw new RestoreException(error,
                        rollbackComplete ? Failure.ROLLED_BACK : Failure.RECOVERY_REQUIRED,
                        rollbackComplete, recoveryAvailable);
            }
        } finally {
            if (!closed) operation.abort();
        }
    }

    /** Restores the undo copy and returns its text, so the caller can report what it held. */
    public static String undo(Context context) throws Exception {
        return restoreFrom(context, undoFile(context).openRead(), false);
    }

    /**
     * Reads and restores in one call, so a file that cannot be read is refused with a reason
     * rather than as a bare {@link IOException} the caller cannot tell apart. Returns the text.
     */
    public static String restoreFrom(Context context, InputStream input, boolean saveUndo)
            throws Exception {
        String text;
        try {
            text = read(input);
        } catch (Exception error) {
            throw RestoreException.rejected(error);
        }
        restore(context, text, saveUndo);
        return text;
    }
    /**
     * Whether an undo copy can be read. AtomicFile keeps the last durable copy in .bak while a
     * write is in flight and openRead() restores it, so a process death in that window leaves
     * an undo that works behind a base file that is missing. The row was greyed out over it.
     */
    public static boolean hasUndo(Context context) {
        File base = undoFile(context).getBaseFile();
        return base.isFile() || new File(base.getPath() + ".bak").isFile();
    }

    private static boolean ordinarySettingsMatch(Map<String, ?> expected) {
        try {
            return expected.equals(Setting.preferences.preferences.getAll());
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean labSettingsMatch(Snapshot expected) {
        try {
            // A snapshot that leaves the Lab alone carries no rules to compare against, and its
            // master and acknowledged flags are placeholders rather than anything that was
            // applied. Reading them anyway threw on the null list, the catch below turned that
            // into "does not match", and the journal then reverted a restore that had worked.
            if (!expected.labIncluded) return true;
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

    /**
     * Puts back the settings a change was interrupted before it could commit. Its only caller
     * recovers the prior state, unlike the two argument form the restore itself uses to go
     * forward, so the Lab's record of which overrides fired is kept: the recovery reaches the
     * Lab whenever the file carries rules, including when only an ordinary setting had moved.
     */
    static void applyForJournal(Snapshot snapshot) throws IOException {
        applyForJournal(snapshot, new boolean[1], true);
    }

    /**
     * @param touchedLab set to true the moment the Lab write is about to start, so a caller
     *                   rolling back knows whether there is anything there to put back. Set
     *                   before the write rather than after, because a write that throws part
     *                   way through is exactly the one that needs undoing.
     */
    static void applyForJournal(Snapshot snapshot, boolean[] touchedLab) throws IOException {
        applyForJournal(snapshot, touchedLab, false);
    }

    static void applyForJournal(Snapshot snapshot, boolean[] touchedLab, boolean puttingBack)
            throws IOException {
        Setting.saveAll(snapshot.values);
        // A backup from another TikTok build carries no rules that mean anything here, so the
        // Lab is left as it was rather than emptied.
        if (snapshot.labIncluded) {
            touchedLab[0] = true;
            FeatureGateLabStore.replaceSettings(
                    snapshot.rules, snapshot.master, snapshot.acknowledged, puttingBack);
        }
    }

    /** How many included settings that file did not carry, which were left as the device had them. */
    public static int settingsNotInFile(String text) {
        try {
            return parseForJournal(text).absent;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** True when the file restored its settings but its Lab rules were for another TikTok build. */
    public static boolean labRulesWereSkipped(String text) {
        try {
            return !parseForJournal(text).labIncluded;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Snapshot parse(String text) throws JSONException, IOException {
        return parseForJournal(text);
    }

    static boolean matchesForJournal(Snapshot expected) {
        try {
            for (Map.Entry<Setting<?>, Object> entry : expected.values.entrySet()) {
                if (!Objects.equals(entry.getKey().get(), entry.getValue())) return false;
            }
            return labSettingsMatch(expected);
        } catch (RuntimeException error) {
            return false;
        }
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

    static Snapshot parseForJournal(String text) throws JSONException, IOException {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new RejectedBackup(Reason.SIZE, "Invalid backup size");
        }
        Settings.REGION_SPOOF.get();
        JSONObject root;
        try {
            root = SettingsJson.parseObject(text);
        } catch (JSONException | IOException error) {
            // A download that stopped halfway is valid UTF-8 and valid nothing else. Letting it
            // out as a bare parse failure reported it as the same unexplained rejection as a
            // photograph, which tells the person holding the file nothing about retrying. The
            // reader reports a document that simply ends as an IOException, not a JSONException,
            // so both are caught here. Oversized input was already refused as SIZE above.
            throw new RejectedBackup(Reason.DAMAGED, "Settings backup is not readable as JSON");
        }
        String format = root.optString("format");
        if (!(FORMAT.equals(format) || LEGACY_FORMAT.equals(format))) {
            throw new RejectedBackup(Reason.FORMAT, "Not a Hushfeed settings backup");
        }
        if (!Integer.valueOf(1).equals(root.get("schema"))) {
            throw new RejectedBackup(Reason.SCHEMA, "Unsupported settings backup schema");
        }
        // The target belongs to the Lab rules, which name gates in one TikTok build. It used to
        // refuse the whole file, so the day this project retargets, every backup anyone holds
        // becomes unrestorable, settings included, for a reason that only concerns the Lab. The
        // settings half is version independent and is restored either way; the Lab half is
        // dropped and the caller says so.
        boolean labApplies = FeatureGateLabStore.TARGET_VERSION.equals(root.optString("target"));
        JSONObject values = root.getJSONObject("settings"), lab = root.getJSONObject("lab");
        JSONArray required = root.getJSONArray("setting_keys");
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (required.length() == 0 || required.length() != values.length()) {
            throw new RejectedBackup(Reason.INCOMPLETE, "Incomplete settings backup");
        }
        for (int i = 0; i < required.length(); i++) {
            Object key = required.get(i);
            if (!(key instanceof String) || !keys.add((String) key) || !values.has((String) key)) {
                throw new RejectedBackup(Reason.INCOMPLETE, "Incomplete settings backup");
            }
        }
        Map<Setting<?>, Object> updates = new LinkedHashMap<>();
        int absent = 0;
        for (Setting<?> setting : Setting.allLoadedSettings()) {
            if (!included(setting)) continue;
            if (values.has(setting.key)) {
                updates.put(setting, convert(setting.defaultValue, values.get(setting.key), setting.key));
                continue;
            }
            // A backup is a set of values to apply, not a picture of the whole app. A file
            // written before a setting existed says nothing about that setting, and taking the
            // silence as "put it back to its default" quietly undid whatever the device held.
            updates.put(setting, setting.get());
            absent++;
        }
        absent -= migrateDownloadPath(values, updates);
        if (!labApplies) {
            // Leaving the Lab exactly as it is, rather than clearing it: the backup says nothing
            // about this TikTok build, so it is not evidence that the user wanted no rules.
            return new Snapshot(updates, null, false, false, false, absent);
        }
        return new Snapshot(updates, FeatureGateLabStore.parseSettings(lab),
                lab.getBoolean("master"), lab.getBoolean("acknowledged"), true, absent);
    }

    /**
     * Applies the one download folder a pre-split backup carries to the three that replaced it.
     *
     * <p>A file from before the split holds {@code down_path} and none of the three keys. The
     * one-shot migration in {@link Settings} cannot help: its flag is already true on the device
     * and was never in a backup, so the three would take their default and a custom folder would
     * be lost with nothing said.
     *
     * @return how many of the three this filled in, so they are not also counted as missing.
     */
    private static int migrateDownloadPath(JSONObject values, Map<Setting<?>, Object> updates) {
        if (!values.has(Settings.DOWNLOAD_PATH.key)) return 0;
        Object legacy = updates.get(Settings.DOWNLOAD_PATH);
        if (!(legacy instanceof String)) return 0;
        int filled = 0;
        for (Setting<?> destination : new Setting<?>[] {
                Settings.DOWNLOAD_VIDEO_PATH, Settings.DOWNLOAD_PHOTO_PATH,
                Settings.DOWNLOAD_STICKER_PATH }) {
            if (values.has(destination.key) || !updates.containsKey(destination)) continue;
            updates.put(destination, legacy);
            filled++;
        }
        return filled;
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
            throw new RejectedBackup(Reason.VALUE, "Invalid value for " + key);
        }
        throw new RejectedBackup(Reason.VALUE, "Invalid value for " + key);
    }

    static final class Snapshot {
        final Map<Setting<?>, Object> values;
        final List<FeatureGateLabStore.Rule> rules;
        final boolean master, acknowledged;
        /** False when the backup was written against another TikTok build, so the Lab is left alone. */
        final boolean labIncluded;
        /** Included settings the file did not carry, kept at whatever the device already held. */
        final int absent;

        Snapshot(Map<Setting<?>, Object> values, List<FeatureGateLabStore.Rule> rules,
                boolean master, boolean acknowledged, boolean labIncluded, int absent) {
            this.values = values; this.rules = rules; this.master = master;
            this.acknowledged = acknowledged; this.labIncluded = labIncluded; this.absent = absent;
        }

        Snapshot(Map<Setting<?>, Object> values, List<FeatureGateLabStore.Rule> rules,
                boolean master, boolean acknowledged, boolean labIncluded) {
            this(values, rules, master, acknowledged, labIncluded, 0);
        }

        Snapshot(Map<Setting<?>, Object> values, List<FeatureGateLabStore.Rule> rules, boolean master, boolean acknowledged) {
            this(values, rules, master, acknowledged, true, 0);
        }
    }
}
