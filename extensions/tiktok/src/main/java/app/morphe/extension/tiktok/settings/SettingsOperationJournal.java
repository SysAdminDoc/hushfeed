package app.morphe.extension.tiktok.settings;

import android.content.Context;
import android.util.AtomicFile;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.SettingsJson;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONException;
import org.json.JSONObject;

/** Serializes persistent settings changes and repairs an interrupted change at startup. */
public final class SettingsOperationJournal {
    static final String FILE_NAME = "hushfeed-settings-operation.json";
    // Each snapshot is bounded to 2 MB, and embedding it as a JSON string can escape every
    // quote and backslash. Leave room for that expansion and the journal envelope.
    private static final int MAX_JOURNAL_BYTES = SettingsBackup.MAX_BYTES * 4 + 16384;
    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static Recovery pendingNotice = Recovery.NONE;
    private static String noticeFingerprint;

    private SettingsOperationJournal() {
    }

    public enum Recovery {
        NONE,
        RECOVERED_PRIOR,
        ALREADY_COMMITTED,
        MALFORMED,
        FAILED
    }

    /** Reads and reconciles the journal. This is safe to call more than once during startup. */
    public static Recovery initialize(Context context) {
        if (context == null || !Utils.isMainProcess()) return Recovery.NONE;
        Context app = applicationContext(context);
        LOCK.lock();
        try {
            return reconcileLocked(app);
        } finally {
            LOCK.unlock();
        }
    }

    /** Shows a single user-facing notice for the most recent startup recovery result. */
    public static void showRecoveryNotice(Context context) {
        Recovery recovery = consumeRecoveryNotice();
        if (recovery == Recovery.NONE) return;
        String message;
        switch (recovery) {
            case RECOVERED_PRIOR:
                message = "Recovered an interrupted settings change. Restart TikTok to apply it.";
                break;
            case ALREADY_COMMITTED:
                message = "Completed an interrupted settings change. Restart TikTok to apply it.";
                break;
            case MALFORMED:
                message = "Settings recovery needs attention. Use Undo or restore your backup.";
                break;
            case FAILED:
                message = "Settings recovery could not finish. Use Undo or restore your backup.";
                break;
            default:
                return;
        }
        Utils.showToastLong(L10n.t(context, message));
    }

    public static Recovery consumeRecoveryNotice() {
        LOCK.lock();
        try {
            Recovery result = pendingNotice;
            pendingNotice = Recovery.NONE;
            return result;
        } finally {
            LOCK.unlock();
        }
    }

    /** Acquires the process-wide mutation lock before a caller captures its prior snapshot. */
    public static Operation acquire(Context context) throws IOException {
        if (context == null) throw new IOException("Settings storage unavailable");
        if (!Utils.isMainProcess()) {
            throw new IOException("Persistent settings are writable only from the main process");
        }
        Context app = applicationContext(context);
        LOCK.lock();
        try {
            Recovery recovery = reconcileLocked(app);
            if (recovery == Recovery.MALFORMED || recovery == Recovery.FAILED) {
                throw new IOException("Settings recovery needs attention");
            }
            return new Operation(app);
        } catch (IOException error) {
            LOCK.unlock();
            throw error;
        } catch (RuntimeException error) {
            LOCK.unlock();
            throw error;
        }
    }

    public static final class Operation {
        private final Context context;
        private boolean recorded;
        private boolean finished;

        private Operation(Context context) {
            this.context = context;
        }

        public void recordSettings(String before, String after) throws IOException {
            record("settings", before, after);
        }

        public void recordLab(String before, String after) throws IOException {
            record("lab", before, after);
        }

        private void record(String kind, String before, String after) throws IOException {
            ensureOpen();
            validateSnapshot(before);
            validateSnapshot(after);
            JSONObject root;
            try {
                root = new JSONObject().put("schema", 1).put("kind", kind)
                        .put("before", before).put("after", after);
            } catch (JSONException error) {
                throw new IOException("Could not create settings journal", error);
            }
            write(journalFile(context), root.toString());
            recorded = true;
        }

        /** Clears a committed journal and releases the process lock. */
        public void complete() {
            if (finished) return;
            try {
                if (recorded) {
                    try {
                        delete(journalFile(context));
                    } catch (IOException ignored) {
                        // A committed operation is still recoverable from its after snapshot.
                    }
                }
            } finally {
                finished = true;
                LOCK.unlock();
            }
        }

        /** Leaves the journal for the next process to reconcile, then releases the lock. */
        public void retainForRecovery() {
            if (finished) return;
            finished = true;
            LOCK.unlock();
        }

        /** Discards an intent that was never applied and releases the process lock. */
        public void abort() {
            if (finished) return;
            try {
                if (recorded) {
                    try {
                        delete(journalFile(context));
                    } catch (IOException ignored) {
                        // The next startup can clear an intent that was never applied.
                    }
                }
            } finally {
                finished = true;
                LOCK.unlock();
            }
        }

        private void ensureOpen() throws IOException {
            if (finished) throw new IOException("Settings operation is already closed");
            if (recorded) throw new IOException("Settings operation already has an intent");
        }
    }

    private static Recovery reconcileLocked(Context context) {
        AtomicFile file = journalFile(context);
        // AtomicFile may leave the last durable copy in its .bak file when the process
        // dies between startWrite and finishWrite. Let openRead restore that copy instead
        // of treating the interrupted operation as if no journal existed.
        if (!hasJournalFile(file)) return Recovery.NONE;

        String text;
        String fingerprint;
        try {
            text = read(file);
            fingerprint = fingerprint(text);
        } catch (Exception error) {
            publish(Recovery.MALFORMED, file.getBaseFile().getAbsolutePath());
            return Recovery.MALFORMED;
        }

        JournalEntry entry;
        try {
            entry = parseEntry(text);
            validateEntry(entry);
        } catch (Exception error) {
            publish(Recovery.MALFORMED, fingerprint);
            return Recovery.MALFORMED;
        }

        try {
            Recovery result = entry.settings
                    ? reconcileSettings(entry)
                    : reconcileLab(entry);
            delete(file);
            publish(result, fingerprint);
            return result;
        } catch (Exception error) {
            publish(Recovery.FAILED, fingerprint);
            return Recovery.FAILED;
        }
    }

    private static Recovery reconcileSettings(JournalEntry entry) throws Exception {
        SettingsBackup.Snapshot before = SettingsBackup.parseForJournal(entry.before);
        SettingsBackup.Snapshot after = SettingsBackup.parseForJournal(entry.after);
        boolean beforeMatches = SettingsBackup.matchesForJournal(before);
        boolean afterMatches = SettingsBackup.matchesForJournal(after);
        if (!beforeMatches && !afterMatches) {
            SettingsBackup.applyForJournal(before);
            if (!SettingsBackup.matchesForJournal(before)) {
                throw new IOException("Could not verify settings recovery");
            }
            return Recovery.RECOVERED_PRIOR;
        }
        return afterMatches ? Recovery.ALREADY_COMMITTED : Recovery.RECOVERED_PRIOR;
    }

    private static Recovery reconcileLab(JournalEntry entry) throws Exception {
        JSONObject before = SettingsJson.parseObject(entry.before);
        JSONObject after = SettingsJson.parseObject(entry.after);
        validateLab(before);
        validateLab(after);
        boolean beforeMatches = labMatches(before);
        boolean afterMatches = labMatches(after);
        if (!beforeMatches && !afterMatches) {
            applyLab(before);
            if (!labMatches(before)) throw new IOException("Could not verify Lab recovery");
            return Recovery.RECOVERED_PRIOR;
        }
        return afterMatches ? Recovery.ALREADY_COMMITTED : Recovery.RECOVERED_PRIOR;
    }

    private static boolean labMatches(JSONObject expected) {
        return FeatureGateLabStore.settingsMatch(expected);
    }

    private static void applyLab(JSONObject saved) throws Exception {
        List<FeatureGateLabStore.Rule> rules = FeatureGateLabStore.parseSettings(saved);
        FeatureGateLabStore.replaceSettings(rules, saved.getBoolean("master"),
                saved.getBoolean("acknowledged"));
    }

    private static void validateLab(JSONObject value) throws JSONException {
        FeatureGateLabStore.parseSettings(value);
    }

    private static JournalEntry parseEntry(String text) throws IOException, JSONException {
        JSONObject root = SettingsJson.parseObject(text);
        if (!Integer.valueOf(1).equals(root.get("schema"))) {
            throw new IOException("Unsupported settings journal schema");
        }
        String kind = root.getString("kind");
        if (!("settings".equals(kind) || "lab".equals(kind))) {
            throw new IOException("Unsupported settings journal kind");
        }
        Object before = root.get("before"), after = root.get("after");
        if (!(before instanceof String) || !(after instanceof String)) {
            throw new IOException("Invalid settings journal snapshots");
        }
        return new JournalEntry("settings".equals(kind), (String) before, (String) after);
    }

    private static void validateEntry(JournalEntry entry) throws Exception {
        validateSnapshot(entry.before);
        validateSnapshot(entry.after);
        if (entry.settings) {
            SettingsBackup.parseForJournal(entry.before);
            SettingsBackup.parseForJournal(entry.after);
        } else {
            validateLab(SettingsJson.parseObject(entry.before));
            validateLab(SettingsJson.parseObject(entry.after));
        }
    }

    private static void validateSnapshot(String text) throws IOException {
        if (text == null || text.isEmpty()
                || text.getBytes(StandardCharsets.UTF_8).length > SettingsBackup.MAX_BYTES) {
            throw new IOException("Invalid settings journal snapshot");
        }
    }

    private static Context applicationContext(Context context) {
        Context application = context.getApplicationContext();
        return application == null ? context : application;
    }

    private static AtomicFile journalFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
    }

    private static boolean hasJournalFile(AtomicFile file) {
        File base = file.getBaseFile();
        return base.isFile() || new File(base.getPath() + ".bak").isFile();
    }

    private static void write(AtomicFile file, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JOURNAL_BYTES) throw new IOException("Settings journal is too large");
        java.io.FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
        if (!text.equals(read(file))) throw new IOException("Could not verify settings journal");
    }

    private static String read(AtomicFile file) throws IOException {
        try (InputStream input = file.openRead(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_JOURNAL_BYTES) {
                    throw new IOException("Settings journal is too large");
                }
                bytes.write(buffer, 0, count);
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        }
    }

    private static void delete(AtomicFile file) throws IOException {
        file.delete();
        if (hasJournalFile(file)) throw new IOException("Could not clear settings journal");
    }

    private static void publish(Recovery result, String fingerprint) {
        if (result == Recovery.NONE || fingerprint == null) return;
        if (!fingerprint.equals(noticeFingerprint)) {
            noticeFingerprint = fingerprint;
            pendingNotice = result;
        }
    }

    private static String fingerprint(String text) {
        return text.length() + ":" + text.hashCode();
    }

    private static final class JournalEntry {
        final boolean settings;
        final String before;
        final String after;

        JournalEntry(boolean settings, String before, String after) {
            this.settings = settings;
            this.before = before;
            this.after = after;
        }
    }
}
