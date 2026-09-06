package app.morphe.extension.tiktok.download;

import android.content.Context;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class OriginalPhotos {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private OriginalPhotos() {}

    public static boolean start(Object aweme, Context context) {
        if (VideoDownloads.start(aweme, context)) return true;
        // Nothing here is handling the video, so the sound has to fetch its own bytes. When
        // the quality download above took it, it saved the sound from what it already had.
        AudioDownloads.start(aweme, context);
        if (!Settings.DOWNLOAD_ORIGINAL_PHOTOS.get() || context == null) return false;
        if (Reflect.property(aweme, "getPhotoModeImageInfo", "photoModeImageInfo") == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        List<List<String>> photos = sources(aweme);
        if (photos.isEmpty()) {
            Utils.showToastShort("Original photo URLs aren't available; using TikTok's download");
            return false;
        }
        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null) return false;
        if (!ACTIVE.add(id)) return true;
        Context app = context.getApplicationContext();
        Utils.showToastShort("Saving " + photos.size() + " original photos");
        WORKER.execute(() -> {
            int saved = 0;
            try {
                for (int i = 0; i < photos.size(); i++) {
                    File temp = File.createTempFile("original-photo-", ".tmp", app.getCacheDir());
                    try {
                        String extension = RemoteMedia.fetch(photos.get(i), temp, true);
                        String mime = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
                        String name = DownloadFilenameFormatter.formatOriginalPhotoName(aweme, i + 1, extension);
                        MediaFileWriter.publish(app, temp, name, mime, DownloadsPatch.getPhotoDownloadPath(), false);
                        saved++;
                    } finally {
                        if (!temp.delete()) Logger.printInfo(() -> "Could not remove original photo temporary file");
                    }
                }
                Utils.showToastShort("Saved " + saved + " original photos");
            } catch (IOException | RuntimeException exception) {
                int completed = saved;
                Logger.printException(() -> "Original photo download failed after " + completed + " photos", exception);
                Utils.showToastLong("Saved " + saved + " photos. Download failed; try again.");
            } finally {
                ACTIVE.remove(id);
            }
        });
        return true;
    }

    static List<List<String>> sources(Object aweme) {
        Object info = Reflect.property(aweme, "getPhotoModeImageInfo", "photoModeImageInfo");
        Object raw = Reflect.property(info, "getImageList", "imageList");
        if (!(raw instanceof List<?>)) return Collections.emptyList();
        List<List<String>> result = new ArrayList<>();
        for (Object photo : (List<?>) raw) {
            Object model = Reflect.property(photo, "getDisplayImageNoWatermark", "displayImageNoWatermark");
            Object urls = Reflect.property(model, "getUrlList", "urlList");
            List<String> candidates = new ArrayList<>();
            if (urls instanceof List<?>) {
                for (Object url : (List<?>) urls) {
                    if (url instanceof String && ((String) url).startsWith("https://")) candidates.add((String) url);
                }
            }
            // Never silently save just part of a post whose original sources are missing.
            if (candidates.isEmpty()) return Collections.emptyList();
            result.add(candidates);
        }
        return result;
    }

}
