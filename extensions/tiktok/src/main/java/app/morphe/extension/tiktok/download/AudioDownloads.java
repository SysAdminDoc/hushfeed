package app.morphe.extension.tiktok.download;

import android.content.Context;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves a video's sound as its own .m4a. TikTok serves the audio separately for videos with a
 * DASH stream, and that track is copied straight into an MP4 container; everything else has the
 * sound inside the video file, so the video is fetched once and its audio track copied out. No
 * track is ever re-encoded.
 */
final class AudioDownloads {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    private AudioDownloads() {}

    /** Runs beside whichever download handles the video, and never takes it over. */
    static void start(Object aweme, Context context) {
        if (context == null || !SettingsStatus.advancedDownloadsEnabled) return;
        if (!Settings.DOWNLOAD_AUDIO_TRACK.get()) return;
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Object video = Reflect.property(aweme, "getVideo", "video");
        if (video == null) return;

        List<String> sound = VideoDownloads.audioUrls(video, null);
        List<String> fallback = sound.isEmpty() ? VideoDownloads.sourceUrls(video) : Collections.emptyList();
        if (sound.isEmpty() && fallback.isEmpty()) return;

        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null || !ACTIVE.add(id)) return;
        Context app = context.getApplicationContext();
        String name = DownloadFilenameFormatter.formatSelectedAudioName(aweme);
        String path = audioPath(DownloadsPatch.getVideoDownloadPath());
        WORKER.execute(() -> {
            List<File> temporary = new ArrayList<>();
            try {
                File source = temp(app, temporary);
                RemoteMedia.fetch(sound.isEmpty() ? fallback : sound, source, false);
                File result = temp(app, temporary);
                TrackMuxer.audioOnly(source, result);
                MediaFileWriter.publish(app, result, name, "audio/mp4", path, true);
                Utils.showToastShort("Sound saved to " + path);
            } catch (IOException | RuntimeException exception) {
                Logger.printException(() -> "Sound download failed", exception);
                Utils.showToastLong("The sound couldn't be saved.");
            } finally {
                for (File file : temporary) {
                    if (!file.delete()) Logger.printInfo(() -> "Could not remove sound temporary file");
                }
                ACTIVE.remove(id);
            }
        });
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

    private static File temp(Context context, List<File> files) throws IOException {
        File file = File.createTempFile("selected-audio-", ".m4a", context.getCacheDir());
        files.add(file);
        return file;
    }
}
