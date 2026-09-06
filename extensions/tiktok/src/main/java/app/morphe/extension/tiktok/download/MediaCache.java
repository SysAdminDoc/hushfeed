package app.morphe.extension.tiktok.download;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.AtomicFile;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns temporary media files and the pending publications created by this extension. */
public final class MediaCache {
    static final String DIRECTORY_NAME = "hushfeed-media";
    static final long STALE_AFTER_MS = 24L * 60 * 60 * 1000;
    private static final String PENDING_FILE_NAME = "pending-uris.tsv";
    private static final Object LOCK = new Object();
    private static final AtomicBoolean RECONCILIATION_STARTED = new AtomicBoolean();
    private static final Set<String> ACTIVE_FILES = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    private MediaCache() {}

    static File createTempFile(Context context, String prefix, String suffix) throws IOException {
        File file = File.createTempFile(prefix, suffix, directory(context));
        ACTIVE_FILES.add(file.getAbsolutePath());
        return file;
    }

    static boolean delete(File file) {
        if (file == null) return true;
        boolean deleted = !file.exists() || file.delete();
        if (deleted) ACTIVE_FILES.remove(file.getAbsolutePath());
        return deleted;
    }

    static void markPending(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("MediaStore returned no URI");
        synchronized (LOCK) {
            File directory = directory(context);
            Map<String, Long> records = readPending(directory);
            records.put(uri.toString(), System.currentTimeMillis());
            writePending(directory, records);
        }
    }

    static void clearPending(Context context, Uri uri) throws IOException {
        if (uri == null) return;
        synchronized (LOCK) {
            File directory = directory(context);
            Map<String, Long> records = readPending(directory);
            if (records.remove(uri.toString()) != null) writePending(directory, records);
        }
    }

    public static void reconcileAsync(Context context) {
        if (context == null || !RECONCILIATION_STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        Utils.runOnBackgroundThread(() -> reconcile(app));
    }

    static void reconcile(Context context) {
        if (context == null) return;
        try {
            Context app = context.getApplicationContext();
            synchronized (LOCK) {
                File directory = directory(app);
                long cutoff = System.currentTimeMillis() - STALE_AFTER_MS;
                File pendingBase = new File(directory, PENDING_FILE_NAME);
                File pendingBackup = new File(directory, PENDING_FILE_NAME + ".bak");
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.isFile() || file.equals(pendingBase) || file.equals(pendingBackup)
                                || ACTIVE_FILES.contains(file.getAbsolutePath())) continue;
                        if (file.lastModified() < cutoff && !file.delete()) {
                            Logger.printInfo(() -> "Could not remove stale media file " + file.getName());
                        }
                    }
                }

                Map<String, Long> records = readPending(directory);
                boolean changed = false;
                Iterator<Map.Entry<String, Long>> iterator = records.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, Long> entry = iterator.next();
                    if (entry.getValue() >= cutoff) continue;
                    if (reconcilePendingUri(app.getContentResolver(), entry.getKey())) {
                        iterator.remove();
                        changed = true;
                    }
                }
                if (changed) writePending(directory, records);
            }
        } catch (IOException | RuntimeException error) {
            Logger.printException(() -> "Media cache reconciliation failed", error);
        }
    }

    private static File directory(Context context) throws IOException {
        if (context == null || context.getCacheDir() == null) {
            throw new IOException("Application cache is unavailable");
        }
        File directory = new File(context.getCacheDir(), DIRECTORY_NAME);
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create media cache");
        }
        return directory;
    }

    private static Map<String, Long> readPending(File directory) {
        Map<String, Long> records = new LinkedHashMap<>();
        File base = new File(directory, PENDING_FILE_NAME);
        if (!base.exists() && !new File(directory, PENDING_FILE_NAME + ".bak").exists()) return records;
        try {
            AtomicFile atomic = new AtomicFile(base);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    atomic.openRead(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0 || tab == line.length() - 1) continue;
                    try {
                        long timestamp = Long.parseLong(line.substring(0, tab));
                        String uri = line.substring(tab + 1);
                        if (!uri.isEmpty() && timestamp > 0) records.put(uri, timestamp);
                    } catch (NumberFormatException ignored) {
                        // A torn line is discarded. Other records remain recoverable.
                    }
                }
            }
        } catch (IOException | RuntimeException error) {
            Logger.printException(() -> "Could not read media publication journal", error);
        }
        return records;
    }

    private static void writePending(File directory, Map<String, Long> records) throws IOException {
        File base = new File(directory, PENDING_FILE_NAME);
        if (records.isEmpty()) {
            if (base.exists() && !base.delete()) throw new IOException("Could not clear media publication journal");
            File backup = new File(directory, PENDING_FILE_NAME + ".bak");
            if (backup.exists() && !backup.delete()) throw new IOException("Could not clear media journal backup");
            return;
        }
        AtomicFile atomic = new AtomicFile(base);
        FileOutputStream output = atomic.startWrite();
        try {
            Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
            for (Map.Entry<String, Long> entry : records.entrySet()) {
                writer.write(Long.toString(entry.getValue()));
                writer.write('\t');
                writer.write(entry.getKey());
                writer.write('\n');
            }
            writer.flush();
            atomic.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            atomic.failWrite(output);
            throw error;
        }
    }

    private static boolean reconcilePendingUri(ContentResolver resolver, String value) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        Uri uri;
        try {
            uri = Uri.parse(value);
            if (uri == null) return true;
            try (Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns.IS_PENDING}, null, null, null)) {
                if (cursor == null || !cursor.moveToFirst()) return true;
                int column = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING);
                if (column < 0 || cursor.getInt(column) == 0) return true;
            }
            return resolver.delete(uri, null, null) > 0;
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not reconcile pending media URI", error);
            return false;
        }
    }
}
