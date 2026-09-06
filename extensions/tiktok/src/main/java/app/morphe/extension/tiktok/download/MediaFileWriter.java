package app.morphe.extension.tiktok.download;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import app.morphe.extension.shared.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class MediaFileWriter {
    private MediaFileWriter() {}

    static String publish(Context context, File source, String name, String mime, String path, boolean video) throws IOException {
        File destinationDirectory = path == null
                ? Environment.getExternalStorageDirectory()
                : new File(Environment.getExternalStorageDirectory(), path);
        MediaBudget.checkDiskSpace(destinationDirectory, source == null ? -1L : source.length());
        if (Build.VERSION.SDK_INT >= 29) {
            var resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, path);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection;
            if ("application/x-subrip".equals(mime)) {
                collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else if (mime.startsWith("audio/")) {
                // Sound goes in the audio collection whatever folder the video chose; the
                // video collection rejects it.
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            } else {
                collection = DownloadDestination.collectionUri(path, video);
            }
            Uri uri = resolver.insert(collection, values);
            if (uri == null) throw new IOException("Could not create gallery entry");
            try {
                MediaCache.markPending(context, uri);
            } catch (IOException error) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
            try {
                try (InputStream input = new FileInputStream(source); OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("Could not open gallery entry");
                    copy(input, output);
                }
                String savedName;
                try (var cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor == null || !cursor.moveToFirst()) throw new IOException("Could not read saved filename");
                    savedName = cursor.getString(0);
                    if (savedName == null || savedName.isEmpty()) throw new IOException("Saved filename is empty");
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (resolver.update(uri, values, null, null) != 1) throw new IOException("Could not publish gallery entry");
                try {
                    MediaCache.clearPending(context, uri);
                } catch (IOException journalError) {
                    // Reconciliation checks IS_PENDING before deleting a journaled URI, so a
                    // completed row remains safe if this final cleanup write is interrupted.
                    Logger.printException(() -> "Could not clear media publication journal", journalError);
                }
                return savedName;
            } catch (IOException | RuntimeException exception) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
                try { MediaCache.clearPending(context, uri); } catch (IOException journalError) { exception.addSuppressed(journalError); }
                throw exception;
            }
        } else {
            File directory = new File(Environment.getExternalStorageDirectory(), path);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create download folder");
            File target = claim(directory, name);
            try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
                copy(input, output);
            } catch (IOException exception) {
                if (!target.delete()) exception.addSuppressed(new IOException("Could not remove incomplete download"));
                throw exception;
            }
            MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{mime}, null);
            return target.getName();
        }
    }

    /**
     * An empty file in {@code directory} that nothing else is using, created here so two
     * saves cannot pick the same name. A second copy of the same sticker becomes name_2,
     * then name_3, rather than replacing the one already there.
     */
    static File claim(File directory, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        String extension = dot < 0 ? "" : name.substring(dot);
        File target = new File(directory, name);
        int suffix = 1;
        while (!target.createNewFile()) {
            if (++suffix > 1000) throw new IOException("Too many files with that name");
            target = new File(directory, stem + "_" + suffix + extension);
        }
        return target;
    }

    static long copy(InputStream input, OutputStream output) throws IOException {
        return copy(input, output, Long.MAX_VALUE, null);
    }

    /**
     * Copies until the input runs out, or until {@code limit} bytes have gone past. An empty
     * result is an error: a zero byte file in the gallery looks like a save that worked.
     */
    static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        return copy(input, output, limit, null);
    }

    static long copy(InputStream input, OutputStream output, long limit,
            MediaBudget.Deadline deadline) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while (true) {
            MediaBudget.check(deadline);
            count = input.read(buffer);
            if (count == -1) break;
            total += count;
            if (total > limit) {
                throw new IOException("That file is larger than " + (limit >> 20) + " MB");
            }
            output.write(buffer, 0, count);
        }
        if (total == 0) throw new IOException("Download is empty");
        output.flush();
        return total;
    }
}
