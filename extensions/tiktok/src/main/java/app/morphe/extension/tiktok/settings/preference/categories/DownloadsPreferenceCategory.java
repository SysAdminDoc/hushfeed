/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/DownloadsPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.DownloadPathPreference;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.download.DownloadDestination;
import app.morphe.extension.tiktok.offline.CustomOfflineVideosLimitPatch;

@SuppressWarnings("deprecation")
public class DownloadsPreferenceCategory extends ConditionalPreferenceCategory {
    public DownloadsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Downloads");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.downloadEnabled || SettingsStatus.advancedDownloadsEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.subtitleToolsEnabled) {
            addPreference(new TogglePreference(context, "Save subtitles beside videos",
                    "Save SRT files with the video. Paired saves use Movies on Android 11+, Download on Android 10, and your video folder on older versions.", Settings.DOWNLOAD_SUBTITLES));
            addPreference(new ChoicePreference(context, "Subtitle language", Settings.SUBTITLE_LANGUAGE,
                    new String[]{"Original language", "Device language, then original", "All available languages"},
                    new String[]{"original", "device", "all"}));
        }
        if (SettingsStatus.advancedDownloadsEnabled) {
            addPreference(new ChoicePreference(context, "Video download quality", Settings.DOWNLOAD_VIDEO_QUALITY,
                    new String[]{"Automatic", "Highest", "Lowest", "1080p", "720p", "540p", "480p", "360p"},
                    new String[]{"auto", "highest", "lowest", "1080", "720", "540", "480", "360"}));
            addPreference(new TogglePreference(context, "Download original photos",
                    "Save every photo in the post directly from its source URL, without rendering it again.", Settings.DOWNLOAD_ORIGINAL_PHOTOS));
            addPreference(new TogglePreference(context, "Save the sound as well",
                    "Write the video's sound beside it as an .m4a. Android 10 and later file audio separately, so it lands in Music under the same folder name as your videos.", Settings.DOWNLOAD_AUDIO_TRACK));
        }
        addPreference(new DownloadPathPreference(
                context,
                "Video destination",
                Settings.DOWNLOAD_VIDEO_PATH,
                DownloadDestination.Kind.VIDEO
        ));
        addPreference(new DownloadPathPreference(
                context,
                "Photo destination",
                Settings.DOWNLOAD_PHOTO_PATH,
                DownloadDestination.Kind.PHOTO
        ));
        if (SettingsStatus.downloadEnabled) {
            addPreference(new ChoicePreference(context, "Animated sticker format", Settings.DOWNLOAD_STICKER_FORMAT,
                    new String[]{"Video (MP4)", "GIF", "WebP, exactly as TikTok sent it"},
                    new String[]{"mp4", "gif", "webp"}));
            addPreference(new DownloadPathPreference(
                    context,
                    "Sticker destination",
                    Settings.DOWNLOAD_STICKER_PATH,
                    DownloadDestination.Kind.STICKER
            ));
        }
        addPreference(new InputTextPreference(
                context,
                "Video filename",
                "Tokens: {creator}, {date}, {video_id}. The file extension is kept automatically.",
                Settings.DOWNLOAD_VIDEO_FILENAME_TEMPLATE
        ));
        addPreference(new InputTextPreference(
                context,
                "Photo filename",
                "Tokens: {creator}, {date}, {video_id}, {index}. The file extension is kept automatically.",
                Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE
        ));
        if (!SettingsStatus.downloadEnabled) return;
        addPreference(new InputTextPreference(
                context,
                "Comment media filename",
                "Tokens: {date}, {media_id}. Works for image and video stickers.",
                Settings.DOWNLOAD_COMMENT_MEDIA_FILENAME_TEMPLATE
        ));
        addPreference(new TogglePreference(
                context,
                "Remove watermark",
                "Apply to video downloads and image downloads.",
                Settings.DOWNLOAD_WATERMARK
        ));
        addPreference(new TogglePreference(
                context,
                "Custom offline videos",
                "Adds a custom option to TikTok's offline videos menu after restart.",
                Settings.CUSTOM_OFFLINE_VIDEOS
        ));
        addPreference(new NumberInputPreference(
                context,
                "Offline videos limit",
                "Choose 1-1000 videos. Values outside this range use the nearest valid limit. Restart TikTok after saving.",
                Settings.CUSTOM_OFFLINE_VIDEO_LIMIT,
                CustomOfflineVideosLimitPatch.MIN_LIMIT,
                CustomOfflineVideosLimitPatch.MAX_LIMIT
        ));

    }
}
