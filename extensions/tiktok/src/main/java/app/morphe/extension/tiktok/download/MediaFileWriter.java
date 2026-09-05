package app.morphe.extension.tiktok.download;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class MediaFileWriter {
    private MediaFileWriter() {}

    static String publish(Context context, File source, String name, String mime, String path, boolean video) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            var resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, path);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = "application/x-subrip".equals(mime)
                    ? MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : DownloadDestination.collectionUri(path, video);
            Uri uri = resolver.insert(collection, values);
            if (uri == null) throw new IOException("Could not create gallery entry");
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
                return savedName;
            } catch (IOException | RuntimeException exception) {
                try { resolver.delete(uri, null, null); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        } else {
            File directory = new File(Environment.getExternalStorageDirectory(), path);
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create download folder");
            File target = new File(directory, name);
            int dot = name.lastIndexOf('.'), suffix = 1;
            while (!target.createNewFile()) {
                target = new File(directory, name.substring(0, dot) + "_" + (++suffix) + name.substring(dot));
            }
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

    static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
            total += count;
        }
        if (total == 0) throw new IOException("Download is empty");
        return total;
    }
}
