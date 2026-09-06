/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.download;

import android.content.Context;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
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
 * Saves a story from a press and hold on it.
 *
 * A story is an Aweme like anything else in the feed, so the photo and video saves beside this
 * one do the work; what stories lack is any way to ask. The play area component hands over both
 * the view to press and the story bound to it, and the gesture is only taken when the switch is
 * on, because holding a story is how TikTok pauses it.
 */
@SuppressWarnings("unused")
public final class StoryDownloads {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    private static volatile Object currentStory;

    private StoryDownloads() {
    }

    /** Called as the play area binds the story it is about to show. */
    public static void recordStory(Object aweme) {
        if (aweme != null) currentStory = aweme;
    }

    /**
     * Called with the story's play area as it is created. A view holds one long click listener,
     * so this only takes it when the feature is on; turning the switch on takes effect the next
     * time the story viewer opens.
     */
    public static void attachPlayArea(View view) {
        if (view == null || !enabled()) return;
        try {
            view.setOnLongClickListener(anchor -> {
                if (!enabled()) return false;
                save(anchor.getContext(), currentStory);
                return true;
            });
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not attach the story save", exception);
        }
    }

    static boolean enabled() {
        return SettingsStatus.advancedDownloadsEnabled && Settings.SAVE_STORY.get();
    }

    static Object recordedStory() {
        return currentStory;
    }

    static void save(Context context, Object aweme) {
        if (context == null) return;
        if (aweme == null) {
            Utils.showToastShort(L10n.t("Open the story again and try once more"));
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;

        List<List<String>> photos = OriginalPhotos.sources(aweme);
        List<String> video = photos.isEmpty()
                ? VideoDownloads.sourceUrls(Reflect.property(aweme, "getVideo", "video"))
                : Collections.emptyList();
        if (photos.isEmpty() && video.isEmpty()) {
            Utils.showToastShort(L10n.t("This story isn't available to save"));
            return;
        }

        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null || !ACTIVE.add(id)) return;
        Context app = context.getApplicationContext();
        Utils.showToastShort(L10n.t("Saving the story"));
        WORKER.execute(() -> {
            try {
                if (photos.isEmpty()) {
                    saveVideo(app, aweme, video);
                } else {
                    savePhotos(app, aweme, photos);
                }
            } catch (IOException | RuntimeException exception) {
                Logger.printException(() -> "Story download failed", exception);
                Utils.showToastLong(L10n.t("The story couldn't be saved."));
            } finally {
                ACTIVE.remove(id);
            }
        });
    }

    private static void saveVideo(Context app, Object aweme, List<String> urls) throws IOException {
        File temp = File.createTempFile("story-", ".mp4", app.getCacheDir());
        try {
            RemoteMedia.fetch(urls, temp, false);
            String path = DownloadsPatch.getVideoDownloadPath();
            MediaFileWriter.publish(app, temp, DownloadFilenameFormatter.formatSelectedVideoName(aweme),
                    "video/mp4", path, true);
            Utils.showToastShort(L10n.f("Story saved to %1$s", path));
        } finally {
            if (!temp.delete()) Logger.printInfo(() -> "Could not remove story temporary file");
        }
    }

    private static void savePhotos(Context app, Object aweme, List<List<String>> photos) throws IOException {
        String path = DownloadsPatch.getPhotoDownloadPath();
        List<File> temporary = new ArrayList<>();
        try {
            for (int index = 0; index < photos.size(); index++) {
                File temp = File.createTempFile("story-photo-", ".tmp", app.getCacheDir());
                temporary.add(temp);
                String extension = RemoteMedia.fetch(photos.get(index), temp, true);
                String mime = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
                String name = DownloadFilenameFormatter.formatOriginalPhotoName(aweme, index + 1, extension);
                MediaFileWriter.publish(app, temp, name, mime, path, false);
            }
            Utils.showToastShort(L10n.f("Story saved to %1$s", path));
        } finally {
            for (File file : temporary) {
                if (!file.delete()) Logger.printInfo(() -> "Could not remove story temporary file");
            }
        }
    }
}
