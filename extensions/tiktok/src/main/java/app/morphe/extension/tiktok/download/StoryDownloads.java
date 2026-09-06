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
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves a story from a press and hold on it.
 *
 * A story is an Aweme like anything else in the feed, so the saves beside this one do the work;
 * what stories lack is any way to ask. The play area component hands over both the view to press
 * and the story bound to it, and the two are kept together rather than in one field: the story
 * viewer binds the pages either side of the one you are looking at, so a single "last story
 * seen" would be somebody else's by the time you pressed.
 *
 * The gesture is only taken when the switch is on, because holding a story is how TikTok pauses
 * it.
 */
@SuppressWarnings("unused")
public final class StoryDownloads {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Set<String> ACTIVE = ConcurrentHashMap.newKeySet();

    /** The story each play area is showing, and the view each play area put on screen. */
    private static final Map<Object, Object> STORIES = new WeakHashMap<>();
    private static final Map<View, Object> OWNERS = new WeakHashMap<>();

    private StoryDownloads() {
    }

    /** Called as a play area binds a story, with the component doing the binding. */
    public static void recordStory(Object component, int position, Object aweme) {
        if (component == null || aweme == null) return;
        synchronized (STORIES) {
            STORIES.put(component, aweme);
        }
    }

    /**
     * Called with a play area and the view it created. A view holds one long click listener, so
     * this only takes it when the feature is on; turning the switch on takes effect the next time
     * the story viewer opens.
     */
    public static void attachPlayArea(Object component, View view) {
        if (component == null || view == null || !enabled()) return;
        synchronized (OWNERS) {
            OWNERS.put(view, component);
        }
        try {
            view.setOnLongClickListener(anchor -> {
                if (!enabled()) return false;
                return save(anchor, storyFor(anchor));
            });
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not attach the story save", exception);
        }
    }

    static boolean enabled() {
        return SettingsStatus.advancedDownloadsEnabled && Settings.SAVE_STORY.get();
    }

    /** The story the pressed view's own play area last bound. */
    static Object storyFor(View view) {
        Object component;
        synchronized (OWNERS) {
            component = OWNERS.get(view);
        }
        if (component == null) return null;
        synchronized (STORIES) {
            return STORIES.get(component);
        }
    }

    /** @return true when the press was used, so TikTok's own hold keeps working when it was not. */
    static boolean save(View anchor, Object aweme) {
        if (anchor == null) return false;
        Context context = anchor.getContext();
        if (context == null) return false;
        if (aweme == null) {
            Utils.showToastShort(L10n.t("Open the story again and try once more"));
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Utils.showToastLong(L10n.t("Storage permission is needed to save a story"));
            return false;
        }

        String id = Reflect.string(aweme, "getAid", "aid");
        if (id == null) {
            Utils.showToastShort(L10n.t("This story isn't available to save"));
            return true;
        }
        if (ACTIVE.contains(id)) {
            Utils.showToastShort(L10n.t("Still saving the last one"));
            return true;
        }

        // The quality download handles the video, its sound and its subtitles the way the
        // settings ask for. It declines when there is nothing for it to do differently, and
        // then the story is fetched from whatever address it carries.
        if (VideoDownloads.start(aweme, context)) return true;

        List<List<String>> photos = OriginalPhotos.sources(aweme);
        List<String> video = photos.isEmpty()
                ? VideoDownloads.sourceUrls(Reflect.property(aweme, "getVideo", "video"))
                : Collections.emptyList();
        if (photos.isEmpty() && video.isEmpty()) {
            Utils.showToastShort(L10n.t("This story isn't available to save"));
            return true;
        }

        Context app = context.getApplicationContext();
        if (!ACTIVE.add(id)) {
            Utils.showToastShort(L10n.t("Still saving the last one"));
            return true;
        }
        Utils.showToastShort(L10n.t("Saving the story"));
        try {
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
        } catch (RuntimeException exception) {
            ACTIVE.remove(id);
            Logger.printException(() -> "Could not start the story download", exception);
            Utils.showToastLong(L10n.t("The story couldn't be saved."));
        }
        return true;
    }

    private static void saveVideo(Context app, Object aweme, List<String> urls) throws IOException {
        File temp = File.createTempFile("story-", ".mp4", app.getCacheDir());
        try {
            RemoteMedia.fetch(urls, temp, false);
            String path = DownloadsPatch.getVideoDownloadPath();
            MediaFileWriter.publish(app, temp, DownloadFilenameFormatter.formatSelectedVideoName(aweme),
                    "video/mp4", path, true);
            AudioDownloads.write(app, aweme, temp);
            Utils.showToastShort(L10n.f("Story saved to %1$s", path));
        } finally {
            if (!temp.delete()) Logger.printInfo(() -> "Could not remove story temporary file");
        }
    }

    private static void savePhotos(Context app, Object aweme, List<List<String>> photos) throws IOException {
        String path = DownloadsPatch.getPhotoDownloadPath();
        List<File> temporary = new ArrayList<>();
        int saved = 0;
        try {
            for (int index = 0; index < photos.size(); index++) {
                File temp = File.createTempFile("story-photo-", ".tmp", app.getCacheDir());
                temporary.add(temp);
                String extension = RemoteMedia.fetch(photos.get(index), temp, true);
                String mime = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
                String name = DownloadFilenameFormatter.formatOriginalPhotoName(aweme, index + 1, extension);
                MediaFileWriter.publish(app, temp, name, mime, path, false);
                saved++;
            }
            Utils.showToastShort(L10n.f("Story saved to %1$s", path));
        } catch (IOException | RuntimeException exception) {
            // Say what did land: a story that stopped part way through has files in the gallery
            // already, and "nothing was saved" would send the user back for duplicates.
            final int completed = saved;
            Logger.printException(() -> "Story photo save stopped after " + completed, exception);
            Utils.showToastLong(L10n.f("Saved %1$s of %2$s photos before the story failed",
                    String.valueOf(completed), String.valueOf(photos.size())));
        } finally {
            for (File file : temporary) {
                if (!file.delete()) Logger.printInfo(() -> "Could not remove story temporary file");
            }
        }
    }
}
