package app.morphe.extension.shared.settings.preference;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.database.Cursor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.RejectedExecutionException;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.DiagnosticEvent;
import app.morphe.extension.shared.diagnostics.DiagnosticRedactor;
import app.morphe.extension.shared.settings.BaseSettings;

/** Bounded structured event storage and latest sanitized crash storage. */
public final class LogBufferManager {
    /**
     * What clearing says, so a bundle with a translation table can set its own. Null means the
     * English below, which is what a bundle carrying no table gets.
     */
    public static CharSequence clearedMessage;

    private static final int BUFFER_MAX_CHARS = 250_000;
    private static final int BUFFER_MAX_SIZE = 10_000;
    private static final int CLIPBOARD_MAX_CHARS = 60_000;
    private static final int CRASH_MAX_BYTES = 64_000;
    private static final String CRASH_FILE = "morphe_java_crash_report_v1.txt";
    private static final String NPTH_CRASH_FILE = "morphe_npth_crash_report_v1.txt";

    private static final Deque<DiagnosticEvent> logBuffer = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger logBufferCharSize = new AtomicInteger();
    private static final Object CRASH_FILE_LOCK = new Object();
    private static final AtomicBoolean FILE_EXPORT_RUNNING = new AtomicBoolean();

    private LogBufferManager() {
    }

    public static void appendToLogBuffer(String message) {
        appendEvent(app.morphe.extension.shared.diagnostics.DiagnosticCategory.OTHER,
                "Legacy", "INFO", message);
    }

    public static void appendEvent(
            app.morphe.extension.shared.diagnostics.DiagnosticCategory category,
            String source,
            String level,
            String message
    ) {
        DiagnosticEvent event = new DiagnosticEvent(
                category,
                System.currentTimeMillis(),
                Thread.currentThread().getName(),
                safe(source),
                safe(level),
                safe(message)
        );
        int eventSize = event.format().length();
        logBuffer.addLast(event);
        int newSize = logBufferCharSize.addAndGet(eventSize);

        while (newSize > BUFFER_MAX_CHARS || logBuffer.size() > BUFFER_MAX_SIZE) {
            DiagnosticEvent removed = logBuffer.pollFirst();
            if (removed == null) return;
            newSize = logBufferCharSize.addAndGet(-removed.format().length());
        }
    }

    public static void exportToClipboard() {
        try {
            String exportText = buildExportText();
            if (exportText.isEmpty()) {
                Utils.showToastShort("No matching Morphe diagnostics found.");
                return;
            }
            if (exportText.length() > CLIPBOARD_MAX_CHARS) {
                exportText = "MORPHE DIAGNOSTIC REPORT\n"
                        + "clipboard_note: older content omitted; use Save full report for everything\n\n"
                        + exportText.substring(exportText.length() - CLIPBOARD_MAX_CHARS);
            }
            Utils.setClipboard(exportText);
            Utils.showToastShort("Morphe diagnostic report copied to clipboard.");
        } catch (Exception ex) {
            String message = "Failed to export Morphe diagnostics: " + ex.getMessage();
            Utils.showToastLong(message);
            Logger.printDebug(() -> message, ex);
        }
    }

    public static void exportToFile() {
        Context context = Utils.getContext();
        if (context == null) {
            Utils.showToastLong("Failed to save Morphe diagnostics: application context unavailable.");
            return;
        }
        Context application = context.getApplicationContext();
        final Context app = application == null ? context : application;
        if (!FILE_EXPORT_RUNNING.compareAndSet(false, true)) {
            Utils.showToastShort("A diagnostic report is already being saved.");
            return;
        }
        try {
            Utils.submitOnBackgroundThread(() -> {
                try {
                    String exportText = buildExportText();
                    if (exportText.isEmpty()) {
                        Utils.showToastShort("No matching Morphe diagnostics found.");
                    } else {
                        Utils.showToastLong("Full report saved to " + writeToFile(app, exportText));
                    }
                } catch (Exception ex) {
                    String message = "Failed to save Morphe diagnostics: " + ex.getMessage();
                    Utils.showToastLong(message);
                    Logger.printDebug(() -> message, ex);
                } finally {
                    FILE_EXPORT_RUNNING.set(false);
                }
                return null;
            });
        } catch (RejectedExecutionException error) {
            FILE_EXPORT_RUNNING.set(false);
            Logger.printException(() -> "Could not start diagnostic export", error);
            Utils.showToastLong("Could not start report export. Try again shortly.");
        }
    }

    static String writeToFile(Context context, String exportText) throws Exception {
        if (context == null) throw new IOException("Application context unavailable");
        String fileName = "morphe-diagnostics-" + fileTimestamp() + "-"
                + Long.toHexString(System.nanoTime()) + ".txt";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Morphe");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri pendingUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (pendingUri == null) throw new IOException("Could not create report file");
            try {
                try (OutputStream output = resolver.openOutputStream(pendingUri, "w")) {
                    writeText(output, exportText);
                }
                String savedName = providerDisplayName(resolver, pendingUri);
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (resolver.update(pendingUri, values, null, null) != 1) {
                    throw new IOException("Could not publish report file");
                }
                return Environment.DIRECTORY_DOWNLOADS + "/Morphe/" + savedName;
            } catch (Exception error) {
                deleteIncomplete(resolver, pendingUri, error);
                throw error;
            }
        }

        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) throw new IOException("Documents directory unavailable");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create Documents directory");
        }
        File file = File.createTempFile("morphe-diagnostics-", ".txt", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                writeText(output, exportText);
            }
            return file.getAbsolutePath();
        } catch (Exception error) {
            if (file.exists() && !file.delete()) {
                error.addSuppressed(new IOException("Could not remove incomplete report"));
            }
            throw error;
        }
    }

    private static String providerDisplayName(ContentResolver resolver, Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri,
                new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Could not read saved report name");
            int column = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (column < 0) throw new IOException("Saved report name is unavailable");
            String name = cursor.getString(column);
            if (name == null || name.isEmpty()) throw new IOException("Saved report name is empty");
            return name;
        }
    }

    private static void deleteIncomplete(ContentResolver resolver, Uri uri, Exception failure) {
        try {
            if (resolver.delete(uri, null, null) != 1) {
                failure.addSuppressed(new IOException("Could not remove incomplete report"));
            }
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private static void writeText(OutputStream output, String report) throws IOException {
        if (output == null) throw new IOException("Could not open report file");
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(report);
        }
    }

    public static String buildExportText() {
        Set<String> selected = LogExportFilterPreference.parse(BaseSettings.DEBUG_LOG_FILTERS.get());
        boolean includeAll = selected.isEmpty() || selected.contains("all");
        String crash = DiagnosticRedactor.redact(readCrashReport(Utils.getContext()));
        String npthCrash = DiagnosticRedactor.redact(readNpthCrashReport(Utils.getContext()));

        List<DiagnosticEvent> snapshot = new ArrayList<>(logBuffer);
        StringBuilder events = new StringBuilder();
        for (DiagnosticEvent event : snapshot) {
            if (!includeAll && !selected.contains(event.category.value)) continue;
            if (events.length() > 0) events.append('\n');
            events.append(DiagnosticRedactor.redact(event.format()));
        }

        // Hook misses are buffered under PATCH_ERRORS, so the table follows the same choice
        // the reader made in "Included diagnostics" rather than printing regardless. It goes
        // through the redactor for the same reason every other section does: the next name put
        // in it may not be a literal.
        StringBuilder hooks = new StringBuilder();
        if (includeAll || selected.contains(
                app.morphe.extension.shared.diagnostics.DiagnosticCategory.PATCH_ERRORS.value)) {
            for (String line : app.morphe.extension.shared.diagnostics.HookStatus.report()) {
                if (hooks.length() > 0) hooks.append('\n');
                hooks.append(DiagnosticRedactor.redact(line));
            }
        }

        // A family exists from the first layout pass, so an all-bound table must not make a
        // report non-empty: "No matching Morphe diagnostics found" would never be said again.
        // A table with a miss in it is different. Those events are the oldest in the buffer and
        // are the first evicted, so on a badly broken build the table is exactly what would be
        // dropped, and it is the thing the report exists to carry.
        boolean worthReporting = !crash.isEmpty() || !npthCrash.isEmpty() || events.length() > 0
                || (hooks.length() > 0
                        && app.morphe.extension.shared.diagnostics.HookStatus.anyMissing());
        if (!worthReporting) return "";

        StringBuilder report = new StringBuilder();
        report.append("MORPHE DIAGNOSTIC REPORT\n")
                .append("schema: 1\n")
                .append("generated_utc: ").append(utcNow()).append('\n')
                .append("tiktok: ").append(Utils.getContext().getPackageName())
                .append(' ').append(Utils.getAppVersionName()).append('\n')
                .append("morphe: ").append(Utils.getPatchesReleaseVersion()).append('\n');

        if (!crash.isEmpty()) {
            report.append("\n[LATEST JAVA CRASH]\n").append(crash);
        }
        if (!npthCrash.isEmpty()) {
            report.append("\n[LATEST TIKTOK CRASH SIGNAL]\n").append(npthCrash);
        }
        if (hooks.length() > 0) {
            report.append("\n[HOOK STATUS]\n").append(hooks).append('\n');
        }
        // Deliberately not part of worthReporting above. Every process has a last exit, most of
        // them ordinary, so counting it would mean no report was ever empty and "No matching
        // Morphe diagnostics found" would never be said again.
        String lastExit = lastExitLine(includeAll, selected);
        if (!lastExit.isEmpty()) {
            report.append("\n[LAST EXIT]\n").append(lastExit).append('\n');
        }
        if (events.length() > 0) {
            report.append("\n\n[SELECTED EVENTS]\n")
                    .append("category | timestamp | thread | source | level | message\n")
                    .append(events);
        }
        return report.toString();
    }

    /**
     * Why the process went away last time. A Java crash handler sees none of the ways the system
     * ends an app: Android 17 kills one that goes over a RAM-proportional limit and records it as
     * a description like "MemoryLimiter:AnonSwap", and an ANR or a low-memory kill leaves nothing
     * behind either. One line turns an unexplained restart into something a maintainer can act on.
     *
     * <p>Read only from API 30, where the history exists at all. It follows the same filter as
     * the hook table, because it is the same kind of evidence.
     */
    private static String lastExitLine(boolean includeAll, Set<String> selected) {
        if (Build.VERSION.SDK_INT < 30) return "";
        if (!includeAll && !selected.contains(
                app.morphe.extension.shared.diagnostics.DiagnosticCategory.PATCH_ERRORS.value)) {
            return "";
        }
        try {
            Context context = Utils.getContext();
            if (context == null) return "";
            ActivityManager manager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return "";
            // Pid 0 means any process, and TikTok runs several. The most recent record is
            // routinely a background helper the system reaped, which says nothing about why the
            // app went away, so the main process is picked out by name.
            String packageName = context.getPackageName();
            List<ApplicationExitInfo> history =
                    manager.getHistoricalProcessExitReasons(packageName, 0, 16);
            if (history == null || history.isEmpty()) return "";
            ApplicationExitInfo exit = null;
            for (ApplicationExitInfo candidate : history) {
                if (packageName.equals(candidate.getProcessName())) {
                    exit = candidate;
                    break;
                }
            }
            if (exit == null) return "";
            StringBuilder line = new StringBuilder();
            line.append("reason: ").append(exitReasonName(exit.getReason()))
                    .append("\nstatus: ").append(exit.getStatus())
                    .append("\nimportance: ").append(exit.getImportance())
                    .append("\nat: ").append(utcOf(exit.getTimestamp()));
            String description = exit.getDescription();
            if (description != null && !description.isEmpty()) {
                line.append("\ndescription: ").append(description);
            }
            return DiagnosticRedactor.redact(line.toString());
        } catch (Throwable unavailable) {
            // A build that cannot answer this is not a build that should fail to export.
            return "";
        }
    }

    private static String exitReasonName(int reason) {
        switch (reason) {
            case 1: return "EXIT_SELF";
            case 2: return "SIGNALED";
            case 3: return "LOW_MEMORY";
            case 4: return "CRASH";
            case 5: return "CRASH_NATIVE";
            case 6: return "ANR";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            case 0: return "UNKNOWN";
            default: return "REASON " + reason;
        }
    }

    /** The same stamp the report header carries, so one report does not hold two formats. */
    private static String utcOf(long epochMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(epochMillis));
    }

    public static String snapshotForCrash(int maxChars) {
        List<DiagnosticEvent> snapshot = new ArrayList<>(logBuffer);
        StringBuilder result = new StringBuilder();
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            String line = snapshot.get(index).format();
            if (result.length() + line.length() + 1 > maxChars) break;
            if (result.length() > 0) result.insert(0, '\n');
            result.insert(0, line);
        }
        return result.toString();
    }

    public static void persistCrashReport(Context context, String report) throws Exception {
        persistCrashReport(context, CRASH_FILE, report);
    }

    public static void persistNpthCrashReport(Context context, String report) throws Exception {
        persistCrashReport(context, NPTH_CRASH_FILE, report);
    }

    private static void persistCrashReport(Context context, String fileName, String report) throws Exception {
        byte[] bytes = safe(report).getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, CRASH_MAX_BYTES);
        AtomicFile atomicFile = new AtomicFile(new File(context.getFilesDir(), fileName));

        synchronized (CRASH_FILE_LOCK) {
            FileOutputStream output = null;
            try {
                output = atomicFile.startWrite();
                output.write(bytes, 0, length);
                output.flush();
                output.getFD().sync();
                atomicFile.finishWrite(output);
            } catch (Exception exception) {
                if (output != null) atomicFile.failWrite(output);
                throw exception;
            }
        }
    }

    public static String readCrashReport(Context context) {
        return readCrashReport(context, CRASH_FILE);
    }

    public static String readNpthCrashReport(Context context) {
        return readCrashReport(context, NPTH_CRASH_FILE);
    }

    private static String readCrashReport(Context context, String fileName) {
        if (context == null) return "";
        File file = new File(context.getFilesDir(), fileName);
        if (!file.isFile() || file.length() <= 0 || file.length() > CRASH_MAX_BYTES) return "";

        synchronized (CRASH_FILE_LOCK) {
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int offset = 0;
                while (offset < data.length) {
                    int read = input.read(data, offset, data.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                return new String(data, 0, offset, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    public static void clearLogBuffer() {
        clearLogBufferData();
        app.morphe.extension.shared.diagnostics.HookStatus.clear();
        clearCrashReports(Utils.getContext());
        Utils.showToastShort(clearedMessage == null
                ? "Morphe diagnostic data cleared."
                : clearedMessage.toString());
    }

    private static void clearLogBufferData() {
        while (!logBuffer.isEmpty()) {
            DiagnosticEvent removed = logBuffer.pollFirst();
            if (removed != null) logBufferCharSize.addAndGet(-removed.format().length());
        }
    }

    private static void clearCrashReports(Context context) {
        if (context == null) return;
        synchronized (CRASH_FILE_LOCK) {
            new AtomicFile(new File(context.getFilesDir(), CRASH_FILE)).delete();
            new AtomicFile(new File(context.getFilesDir(), NPTH_CRASH_FILE)).delete();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    /** The stamp every Hushfeed export is named with. UTC, so two exports sort together. */
    public static String fileTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
