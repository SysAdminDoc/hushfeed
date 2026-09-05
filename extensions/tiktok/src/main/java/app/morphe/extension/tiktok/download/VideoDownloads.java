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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

final class VideoDownloads {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();
    private VideoDownloads() {}

    static boolean start(Object aweme, Context context) {
        if (context == null || "auto".equals(Settings.DOWNLOAD_VIDEO_QUALITY.get())) return false;
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        Object video = Reflect.property(aweme, "getVideo", "video");
        Object rates = Reflect.readField(video, "bitRate");
        Object selected = rates instanceof List<?> ? QualitySelector.choose((List<?>) rates, Settings.DOWNLOAD_VIDEO_QUALITY.get()) : null;
        if (selected == null) return false;
        List<String> videoUrls = urls(Reflect.property(selected, "getPlayAddr", "playAddr"));
        boolean dash = Boolean.TRUE.equals(Reflect.invoke(video, "hasDashBitrate"));
        List<String> audioUrls = dash ? audioUrls(video, selected) : Collections.emptyList();
        if (videoUrls.isEmpty() || (dash && audioUrls.isEmpty())) {
            Utils.showToastShort("This quality isn't available as a complete download; using TikTok's download");
            return false;
        }
        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null) return false;
        if (!ACTIVE.add(id)) return true;
        Context app = context.getApplicationContext();
        Utils.showToastShort("Saving the selected video quality");
        WORKER.execute(() -> {
            List<File> temporary = new ArrayList<>();
            try {
                File picture = temp(app, temporary);
                RemoteMedia.fetch(videoUrls, picture, false);
                File result = picture;
                if (dash) {
                    File sound = temp(app, temporary);
                    RemoteMedia.fetch(audioUrls, sound, false);
                    result = temp(app, temporary);
                    TrackMuxer.combine(picture, sound, result);
                }
                MediaFileWriter.publish(app, result, DownloadFilenameFormatter.formatSelectedVideoName(aweme),
                        "video/mp4", DownloadsPatch.getVideoDownloadPath(), true);
                Utils.showToastShort("Video saved");
            } catch (IOException | RuntimeException exception) {
                Logger.printException(() -> "Selected-quality download failed", exception);
                Utils.showToastLong("Video download failed. Try again or choose Automatic.");
            } finally {
                for (File file : temporary) if (!file.delete()) Logger.printInfo(() -> "Could not remove video temporary file");
                ACTIVE.remove(id);
            }
        });
        return true;
    }

    private static File temp(Context context, List<File> files) throws IOException {
        File file = File.createTempFile("selected-video-", ".mp4", context.getCacheDir());
        files.add(file);
        return file;
    }

    static List<String> audioUrls(Object video, Object gear) {
        Object raw = Reflect.readField(video, "bitRateAudio");
        if (!(raw instanceof List<?>)) return Collections.emptyList();
        String wanted = "";
        String extra = Reflect.string(gear, "getVideoExtra", "videoExtra");
        if (extra != null) {
            try { wanted = new JSONObject(extra).optString("audio_file_id"); }
            catch (org.json.JSONException exception) { return Collections.emptyList(); }
        }
        List<String> selected = Collections.emptyList();
        long bestRate = -1;
        for (Object track : (List<?>) raw) {
            Object meta = Reflect.property(track, "getAudioMeta", "audioMeta");
            if (!wanted.isEmpty() && !wanted.equals(Reflect.string(meta, "getFileId", "fileId"))) continue;
            Object addresses = Reflect.property(meta, "getUrlList", "urlList");
            List<String> candidates = new ArrayList<>();
            for (String[] field : new String[][]{{"getMainUrl", "mainUrl"}, {"getBackupUrl", "backupUrl"}, {"getFallbackUrl", "fallbackUrl"}}) {
                String url = Reflect.string(addresses, field[0], field[1]);
                if (url != null && url.startsWith("https://")) candidates.add(url);
            }
            Object rate = Reflect.property(meta, "getBitrate", "bitrate");
            long value = rate instanceof Number ? ((Number) rate).longValue() : 0;
            if (!candidates.isEmpty() && value > bestRate) { selected = candidates; bestRate = value; }
        }
        return selected;
    }

    private static List<String> urls(Object address) {
        Object raw = Reflect.property(address, "getUrlList", "urlList");
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?>) for (Object url : (List<?>) raw) {
            if (url instanceof String && ((String) url).startsWith("https://")) result.add((String) url);
        }
        return result;
    }
}
