package app.morphe.extension.tiktok.download;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;
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
import java.util.concurrent.RejectedExecutionException;

/** Owns temporary media files and the pending publications created by this extension. */
public final class MediaCache {
    static final String DIRECTORY_NAME = "hushfeed-media";
    static final long STALE_AFTER_MS = 24L * 60 * 60 * 1000;
    private static final String PENDING_FILE_NAME = "pending-uris.tsv";
    private static final String PENDING_INTENT_PREFIX = "intent:";
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

    /** Records the insert intent before asking MediaStore for a row, closing the crash window. */
    static String beginPending(Context context, Uri collection, String displayName) throws IOException {
        if (collection == null || displayName == null || displayName.isEmpty()) {
            throw new IOException("MediaStore publication details are missing");
        }
        String token = PENDING_INTENT_PREFIX + Long.toHexString(System.nanoTime()) + ":"
                + encode(collection.toString()) + ":" + encode(displayName);
        synchronized (LOCK) {
            File directory = directory(context);
            Map<String, Long> records = readPending(directory);
            records.put(token, System.currentTimeMillis());
            writePending(directory, records);
        }
        return token;
    }

    /** Inserts and journals a pending row as one recoverable operation. */
    static Uri insertPending(
            Context context,
            ContentResolver resolver,
            Uri collection,
            ContentValues values
    ) throws IOException {
        String displayName = values == null
                ? null : values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME);
        String token = beginPending(context, collection, displayName);
        Uri uri = null;
        try {
            uri = resolver.insert(collection, values);
            if (uri == null) throw new IOException("MediaStore returned no URI");
            markPending(context, token, uri);
            return uri;
        } catch (IOException | RuntimeException error) {
            boolean deleted = uri == null;
            if (uri != null) {
                try {
                    deleted = resolver.delete(uri, null, null) > 0;
                } catch (RuntimeException cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            if (deleted) {
                try {
                    clearPendingIntent(context, token);
                } catch (IOException journalError) {
                    error.addSuppressed(journalError);
                }
            }
            throw error;
        }
    }

    private static void markPending(Context context, String token, Uri uri) throws IOException {
        if (uri == null) throw new IOException("MediaStore returned no URI");
        synchronized (LOCK) {
            File directory = directory(context);
            Map<String, Long> records = readPending(directory);
            records.remove(token);
            records.put(uri.toString(), System.currentTimeMillis());
            writePending(directory, records);
        }
    }

    static void clearPending(Context context, Uri uri) throws IOException {
        if (uri == null) return;
        clearPendingKey(context, uri.toString());
    }

    private static void clearPendingIntent(Context context, String token) throws IOException {
        clearPendingKey(context, token);
    }

    private static void clearPendingKey(Context context, String key) throws IOException {
        if (key == null) return;
        synchronized (LOCK) {
            File directory = directory(context);
            Map<String, Long> records = readPending(directory);
            if (records.remove(key) != null) writePending(directory, records);
        }
    }

    public static void reconcileAsync(Context context) {
        if (context == null || !RECONCILIATION_STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        try {
            Utils.submitOnBackgroundThread(() -> {
                try {
                    reconcile(app);
                } finally {
                    RECONCILIATION_STARTED.set(false);
                }
                return null;
            });
        } catch (RejectedExecutionException error) {
            RECONCILIATION_STARTED.set(false);
            Logger.printException(() -> "Could not schedule media cache reconciliation", error);
        }
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
                    if (reconcilePendingEntry(app.getContentResolver(), entry.getKey())) {
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

    private static Map<String, Long> readPending(File directory) throws IOException {
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
            IOException failure = error instanceof IOException
                    ? (IOException) error : new IOException("Could not read media publication journal", error);
            throw failure;
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

    private static boolean reconcilePendingEntry(ContentResolver resolver, String value) {
        if (value != null && value.startsWith(PENDING_INTENT_PREFIX)) {
            return reconcilePendingIntent(resolver, value);
        }
        return reconcilePendingUri(resolver, value);
    }

    private static boolean reconcilePendingUri(ContentResolver resolver, String value) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        Uri uri;
        try {
            uri = Uri.parse(value);
            if (uri == null) return false;
            try (Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns.IS_PENDING}, null, null, null)) {
                if (cursor == null) return false;
                if (!cursor.moveToFirst()) return true;
                int column = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING);
                if (column < 0) return false;
                if (cursor.getInt(column) == 0) return true;
            }
            return resolver.delete(uri, null, null) > 0;
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not reconcile pending media URI", error);
            return false;
        }
    }

    private static boolean reconcilePendingIntent(ContentResolver resolver, String value) {
        PendingIntent intent = decodeIntent(value);
        if (intent == null) return false;
        try (Cursor cursor = resolver.query(intent.collection,
                new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.IS_PENDING},
                MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{intent.displayName}, null)) {
            if (cursor == null) return false;
            int idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
            int nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int pendingColumn = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING);
            if (idColumn < 0 || nameColumn < 0 || pendingColumn < 0) return false;
            boolean found = false;
            boolean allDeleted = true;
            if (!cursor.moveToFirst()) return true;
            do {
                if (intent.displayName.equals(cursor.getString(nameColumn))) {
                    found = true;
                    if (cursor.getInt(pendingColumn) == 0) return true;
                    Uri uri = ContentUris.withAppendedId(intent.collection, cursor.getLong(idColumn));
                    if (resolver.delete(uri, null, null) <= 0) allDeleted = false;
                }
            } while (cursor.moveToNext());
            return !found || allDeleted;
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not reconcile pending media insert", error);
            return false;
        }
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private static PendingIntent decodeIntent(String value) {
        try {
            String encoded = value.substring(PENDING_INTENT_PREFIX.length());
            int firstSeparator = encoded.indexOf(':');
            int secondSeparator = firstSeparator < 0 ? -1 : encoded.indexOf(':', firstSeparator + 1);
            if (firstSeparator <= 0 || secondSeparator == firstSeparator + 1
                    || secondSeparator == encoded.length() - 1) return null;
            String collection = new String(Base64.decode(
                    encoded.substring(firstSeparator + 1, secondSeparator), Base64.URL_SAFE),
                    StandardCharsets.UTF_8);
            String displayName = new String(Base64.decode(encoded.substring(secondSeparator + 1), Base64.URL_SAFE),
                    StandardCharsets.UTF_8);
            return new PendingIntent(Uri.parse(collection), displayName);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static final class PendingIntent {
        final Uri collection;
        final String displayName;

        PendingIntent(Uri collection, String displayName) {
            this.collection = collection;
            this.displayName = displayName;
        }
    }
}
