package app.morphe.extension.shared.settings.preference;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.DiagnosticEvent;
import app.morphe.extension.shared.diagnostics.DiagnosticRedactor;
import app.morphe.extension.shared.settings.BaseSettings;

/** Bounded structured event storage and latest sanitized crash storage. */
public final class LogBufferManager {
    private static final int BUFFER_MAX_CHARS = 250_000;
    private static final int BUFFER_MAX_SIZE = 10_000;
    private static final int CLIPBOARD_MAX_CHARS = 60_000;
    private static final int CRASH_MAX_BYTES = 64_000;
    private static final String CRASH_FILE = "morphe_java_crash_report_v1.txt";
    private static final String NPTH_CRASH_FILE = "morphe_npth_crash_report_v1.txt";

    private static final Deque<DiagnosticEvent> logBuffer = new ConcurrentLinkedDeque<>();
    private static final AtomicInteger logBufferCharSize = new AtomicInteger();
    private static final Object CRASH_FILE_LOCK = new Object();

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
        Uri pendingUri = null;
        try {
            String exportText = buildExportText();
            if (exportText.isEmpty()) {
                Utils.showToastShort("No matching Morphe diagnostics found.");
                return;
            }

            String fileName = "morphe-diagnostics-" + fileTimestamp() + ".txt";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Morphe");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                pendingUri = context.getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (pendingUri == null) throw new IllegalStateException("Could not create report file");

                try (OutputStream output = context.getContentResolver().openOutputStream(pendingUri)) {
                    if (output == null) throw new IllegalStateException("Could not open report file");
                    output.write(exportText.getBytes(StandardCharsets.UTF_8));
                }

                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(pendingUri, values, null, null);
            } else {
                File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (directory == null) throw new IllegalStateException("Documents directory unavailable");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Could not create Documents directory");
                }
                try (FileOutputStream output = new FileOutputStream(new File(directory, fileName))) {
                    output.write(exportText.getBytes(StandardCharsets.UTF_8));
                }
            }
            Utils.showToastLong("Full report saved to Downloads/Morphe.");
        } catch (Exception ex) {
            if (pendingUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getContentResolver().delete(pendingUri, null, null);
            }
            String message = "Failed to save Morphe diagnostics: " + ex.getMessage();
            Utils.showToastLong(message);
            Logger.printDebug(() -> message, ex);
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

        if (crash.isEmpty() && npthCrash.isEmpty() && events.length() == 0) return "";

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
        if (events.length() > 0) {
            report.append("\n\n[SELECTED EVENTS]\n")
                    .append("category | timestamp | thread | source | level | message\n")
                    .append(events);
        }
        return report.toString();
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
        clearCrashReports(Utils.getContext());
        Utils.showToastShort("Morphe diagnostic data cleared.");
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

    private static String fileTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }
}
