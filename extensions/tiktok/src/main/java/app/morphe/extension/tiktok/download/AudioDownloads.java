package app.morphe.extension.tiktok.download;

import android.content.Context;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves a video's sound as its own .m4a, copied out of a container rather than re-encoded.
 *
 * The bytes come from whatever has already been fetched wherever possible: when
 * {@link VideoDownloads} handles the download it hands its own temporary file over, which is
 * the separate audio stream for a video with DASH and the video file itself otherwise. Only
 * when TikTok's own downloader takes the video does this fetch anything of its own.
 */
final class AudioDownloads {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    private AudioDownloads() {}

    static boolean enabled() {
        return SettingsStatus.advancedDownloadsEnabled && Settings.DOWNLOAD_AUDIO_TRACK.get();
    }

    /** Fetches the sound itself, for the downloads this extension does not handle. */
    static void start(Object aweme, Context context) {
        if (context == null || !enabled()) return;
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Object video = Reflect.property(aweme, "getVideo", "video");
        if (video == null) return;

        // The separate audio stream when there is one, otherwise the video, which carries it.
        List<String> sound = VideoDownloads.audioUrls(video, null);
        List<String> source = sound.isEmpty() ? VideoDownloads.sourceUrls(video) : sound;
        if (source.isEmpty()) return;

        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null) return;
        Context app = context.getApplicationContext();
        if (!ACTIVE.add(id)) return;
        WORKER.execute(() -> {
            File fetched = null;
            try {
                fetched = File.createTempFile("sound-source-", ".mp4", app.getCacheDir());
                RemoteMedia.fetch(source, fetched, false);
                write(app, aweme, fetched);
            } catch (IOException | RuntimeException exception) {
                Logger.printException(() -> "Sound download failed", exception);
                Utils.showToastLong("The sound couldn't be saved.");
            } finally {
                if (fetched != null && !fetched.delete()) {
                    Logger.printInfo(() -> "Could not remove sound temporary file");
                }
                ACTIVE.remove(id);
            }
        });
    }

    /**
     * Copies the sound out of a file that is already on disk. Runs on the caller's thread, so
     * the file has to outlive the call, and reports its own failures rather than taking the
     * video download down with it.
     */
    static void write(Context app, Object aweme, File source) {
        if (!enabled()) return;
        File output = null;
        try {
            output = File.createTempFile("sound-", ".m4a", app.getCacheDir());
            TrackMuxer.audioOnly(source, output);
            String path = audioPath(DownloadsPatch.getVideoDownloadPath());
            MediaFileWriter.publish(app, output, DownloadFilenameFormatter.formatSelectedAudioName(aweme),
                    "audio/mp4", path, true);
            Utils.showToastShort("Sound saved to " + path);
        } catch (IOException | RuntimeException exception) {
            Logger.printException(() -> "Sound save failed", exception);
            Utils.showToastLong("The sound couldn't be saved.");
        } finally {
            if (output != null && !output.delete()) {
                Logger.printInfo(() -> "Could not remove sound temporary file");
            }
        }
    }

    /**
     * Where the .m4a goes. Android 10 and later file media by type, and an audio file does not
     * belong to the video collection, so the sound mirrors the video's own folder under Music.
     * Older versions write real files and keep the two together.
     */
    static String audioPath(String videoPath) {
        if (android.os.Build.VERSION.SDK_INT < 29) return videoPath;
        int slash = videoPath == null ? -1 : videoPath.indexOf('/');
        return "Music" + (slash < 0 ? "/TikTok" : videoPath.substring(slash));
    }
}
