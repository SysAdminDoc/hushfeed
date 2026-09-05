package app.morphe.extension.tiktok.settings.preference;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

final class GateReportExport {
    static final int MAX_CLIPBOARD_CHARS = 60000;

    static boolean copy(Context context, String report) {
        if (report.length() > MAX_CLIPBOARD_CHARS) {
            Utils.showToastShort("Use Save JSON for this large report");
            return false;
        }
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("Clipboard is unavailable");
            clipboard.setPrimaryClip(ClipData.newPlainText("Feature gate recording", report));
            Utils.showToastShort("Copied feature gate report");
            return true;
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not copy gate report", error);
            Utils.showToastShort("Could not copy report. Use Save JSON.");
            return false;
        }
    }

    static void save(Context context, String report) {
        Context app = context.getApplicationContext();
        Utils.runOnBackgroundThread(() -> {
            try {
                Utils.showToastLong("Report saved to " + write(app, report));
            } catch (IOException | RuntimeException error) {
                Logger.printException(() -> "Could not save gate report", error);
                Utils.showToastLong("Could not save report: " + error.getMessage());
            }
        });
    }

    static String write(Context context, String report) throws IOException {
        String name = "gate-recording-" + System.currentTimeMillis() + ".json";
        if (Build.VERSION.SDK_INT >= 29) {
            var resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Morphe");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create report");
            try {
                writeText(resolver.openOutputStream(uri, "w"), report);
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (resolver.update(uri, values, null, null) != 1) throw new IOException("Could not publish report");
            } catch (IOException | RuntimeException error) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
            return "Downloads/Morphe/" + name;
        }
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) throw new IOException("Documents unavailable");
        File file = File.createTempFile("gate-recording-", ".json", directory);
        try { writeText(new FileOutputStream(file), report); }
        catch (IOException | RuntimeException error) {
            if (!file.delete()) error.addSuppressed(new IOException("Could not remove incomplete report"));
            throw error;
        }
        return file.getAbsolutePath();
    }

    private static void writeText(OutputStream output, String report) throws IOException {
        if (output == null) throw new IOException("Could not open report");
        try (var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) { writer.write(report); }
    }
}
