package app.morphe.extension.tiktok.download;

import android.content.Context;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

final class VideoDownloads {
    private static final Set<String> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private VideoDownloads() {}

    static boolean start(Object aweme, Context context) {
        if (context == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        Object video = Reflect.property(aweme, "getVideo", "video");
        List<SubtitleDownloads.Track> captions = SettingsStatus.subtitleToolsEnabled && Settings.DOWNLOAD_SUBTITLES.get()
                ? SubtitleDownloads.tracks(video, Settings.SUBTITLE_LANGUAGE.get(), Locale.getDefault()) : Collections.emptyList();
        String quality = Settings.DOWNLOAD_VIDEO_QUALITY.get();
        boolean muted = Settings.DOWNLOAD_WITHOUT_SOUND.get();
        boolean automatic = "auto".equals(quality);
        // Automatic with nothing else asked for is TikTok's own download, which already does
        // the right thing. Taking the sound off is a reason to take it over, but not a reason
        // to fetch a different file: on Automatic the source stays the one TikTok would have
        // used and only the sound is left out of it.
        if (captions.isEmpty() && automatic && !muted) return false;
        Object rates = Reflect.readField(video, "bitRate");
        Object selected = !automatic && rates instanceof List<?>
                ? QualitySelector.choose((List<?>) rates, quality)
                : (captions.isEmpty() || !(rates instanceof List<?>) ? null
                        : QualitySelector.choose((List<?>) rates, "highest"));
        if (selected == null && captions.isEmpty() && !muted) return false;
        List<String> selectedUrls = urls(Reflect.property(selected, "getPlayAddr", "playAddr"));
        if (selected == null) {
            selectedUrls = urls(Reflect.property(video, "getDownloadNoWatermarkAddr", "downloadNoWatermarkAddr"));
            if (selectedUrls.isEmpty()) selectedUrls = urls(Reflect.property(video, "getDownloadAddr", "downloadAddr"));
        }
        List<String> videoUrls = List.copyOf(selectedUrls);
        boolean dash = selected != null && Boolean.TRUE.equals(Reflect.invoke(video, "hasDashBitrate"));
        List<String> audioUrls = dash ? List.copyOf(audioUrls(video, selected)) : Collections.emptyList();
        if (videoUrls.isEmpty() || (dash && !muted && audioUrls.isEmpty())) {
            Utils.showToastShort(L10n.t(
                    "This quality isn't available as a complete file, so TikTok's own save runs instead"));
            return false;
        }
        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null) return false;
        Context app = context.getApplicationContext();
        String name, path;
        try {
            name = DownloadFilenameFormatter.formatSelectedVideoName(aweme);
            path = captions.isEmpty() ? DownloadsPatch.getVideoDownloadPath()
                    : SubtitleDownloads.pairedPath(DownloadsPatch.getVideoDownloadPath());
        } catch (RuntimeException exception) {
            // Working out the name is reflection over TikTok's model, so it can throw. Leaving
            // the id in ACTIVE here would refuse every later attempt on this video in silence.
            Logger.printException(() -> "Could not work out the download name", exception);
            return false;
        }
        String audioName = null;
        if (AudioDownloads.enabled()) {
            try {
                audioName = DownloadFilenameFormatter.formatSelectedAudioName(aweme);
            } catch (RuntimeException exception) {
                Logger.printException(() -> "Could not work out the sound download name", exception);
            }
        }
        final String audioNameSnapshot = audioName;
        List<SubtitleDownloads.Track> captionSnapshot = List.copyOf(captions);
        if (!ACTIVE.add(id)) return true;
        Utils.showToastShort(captions.isEmpty()
                ? L10n.t(muted
                        ? "Saving the selected video quality without sound"
                        : "Saving the selected video quality")
                : L10n.f("Saving video and subtitles to %1$s", path));
        MediaJobScheduler.JobHandle job = MediaJobScheduler.submit("video", () -> {
            List<File> temporary = new ArrayList<>();
            try {
                File picture = temp(app, temporary);
                RemoteMedia.fetch(videoUrls, picture, false);
                File result = picture, sound = null;
                if (dash && !muted) {
                    // The sound is a separate stream here and the save is not finished without
                    // it, so a failure to fetch it fails the whole thing.
                    sound = temp(app, temporary);
                    RemoteMedia.fetch(audioUrls, sound, false);
                    result = temp(app, temporary);
                    TrackMuxer.combine(picture, sound, result);
                } else if (dash && AudioDownloads.enabled() && !audioUrls.isEmpty()) {
                    // Muted, but the sound is wanted beside it as an .m4a. That is a second
                    // file, so losing it is not a reason to lose the video as well.
                    try {
                        File separate = temp(app, temporary);
                        RemoteMedia.fetch(audioUrls, separate, false);
                        sound = separate;
                    } catch (IOException | RuntimeException exception) {
                        Logger.printException(() -> "Could not fetch the sound to save beside a muted video", exception);
                    }
                } else if (!dash && muted) {
                    // One file with both tracks in it, so the picture is copied out on its own.
                    result = temp(app, temporary);
                    TrackMuxer.videoOnly(picture, result);
                }
                String savedName = MediaFileWriter.publish(app, result, name, "video/mp4", path, true);
                // The sound is already on disk: the separate stream when the video has one,
                // otherwise the video itself, which still carries it because the copy that
                // dropped it went to a different file. Fetching it again would download twice.
                if (audioNameSnapshot != null) {
                    AudioDownloads.write(app, audioNameSnapshot, sound == null ? picture : sound);
                }
                int saved = SubtitleDownloads.save(app, captionSnapshot, savedName, path);
                Utils.showToastLong(subtitleResult(captionSnapshot.size(), saved, path));
            } catch (IOException | RuntimeException exception) {
                if (!MediaBudget.isCancellation(exception)) {
                    Logger.printException(() -> "Selected-quality download failed", exception);
                    Utils.showToastLong(L10n.t("The video couldn't be saved. Try again, or choose Automatic."));
                }
            } finally {
                for (File file : temporary) if (!MediaCache.delete(file)) Logger.printInfo(() -> "Could not remove video temporary file");
                ACTIVE.remove(id);
            }
        }, () -> ACTIVE.remove(id));
        if (job == null) {
            ACTIVE.remove(id);
            return false;
        }
        return true;
    }

    /**
     * What to say once the file is written. Three separate sentences rather than one built
     * from pieces: a translator needs the whole sentence to move the words around inside it.
     */
    private static String subtitleResult(int wanted, int saved, String path) {
        if (wanted == 0) return L10n.t("Video saved");
        if (saved == wanted) {
            return L10n.f("Video saved with %1$s subtitles in %2$s", saved, path);
        }
        return L10n.f("Video saved in %1$s, but only %2$s of %3$s subtitles came with it",
                path, saved, wanted);
    }

    private static File temp(Context context, List<File> files) throws IOException {
        File file = MediaCache.createTempFile(context, "selected-video-", ".mp4");
        files.add(file);
        return file;
    }

    /** Every address the video itself can be fetched from, best first. */
    static List<String> sourceUrls(Object video) {
        List<String> found = urls(Reflect.property(video, "getDownloadNoWatermarkAddr", "downloadNoWatermarkAddr"));
        if (found.isEmpty()) found = urls(Reflect.property(video, "getDownloadAddr", "downloadAddr"));
        if (found.isEmpty()) found = urls(Reflect.property(video, "getPlayAddr", "playAddr"));
        return found;
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

    /** The https addresses inside a UrlModel, in the order it lists them. */
    static List<String> urls(Object address) {
        Object raw = Reflect.property(address, "getUrlList", "urlList");
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?>) for (Object url : (List<?>) raw) {
            if (url instanceof String && ((String) url).startsWith("https://")) result.add((String) url);
        }
        return result;
    }
}
