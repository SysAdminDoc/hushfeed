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
import app.morphe.extension.tiktok.download.ExternalDownloader;

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
            addPreference(new TogglePreference(context, "Save a profile picture on a long press",
                    "Press and hold a profile picture to save the full size original to your photo destination.",
                    Settings.SAVE_PROFILE_PICTURE));
            addPreference(new TogglePreference(context, "Save a story on a long press",
                    "Press and hold a story to save it. Stories have no save button of their own, "
                            + "and holding one is how TikTok pauses it, so this takes that gesture over.",
                    Settings.SAVE_STORY));
            addPreference(new TogglePreference(context, "Save the sound as well",
                    "Write the video's sound beside it as an .m4a. Android 10 and later file audio separately, so it lands in Music under the same folder name as your videos.", Settings.DOWNLOAD_AUDIO_TRACK));
            addPreference(new TogglePreference(context, "Save videos without sound",
                    "Leave the sound out of the saved video. Save the sound as well still writes "
                            + "the .m4a beside it if you want both.", Settings.DOWNLOAD_WITHOUT_SOUND));
            addPreference(new app.morphe.extension.tiktok.settings.preference.InputTextPreference(
                    context,
                    "Send links to another app",
                    "An app's package name, like com.dv.adm. The save button sends the video's "
                            + "link there instead of saving it here. Leave it empty to save here.",
                    Settings.EXTERNAL_DOWNLOADER_PACKAGE)
                    .withCheck(value -> ExternalDownloader.packageNameProblem(value.trim())));
            ChoicePreference ytdlnisType = new ChoicePreference(context, "YTDLnis download type",
                    Settings.YTDLNIS_DOWNLOAD_TYPE, new String[]{"Video", "Audio"},
                    new String[]{"video", "audio"});
            ytdlnisType.setSummary("For com.deniscerri.ytdl only. Ask YTDLnis for audio or video "
                    + "when the save button hands it a link.");
            addPreference(ytdlnisType);
            addPreference(new TogglePreference(context, "YTDLnis background mode",
                    "For com.deniscerri.ytdl only. Hide its download card and start the handoff in "
                            + "the background.", Settings.YTDLNIS_BACKGROUND));
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
                "Applies to both video and photo downloads.",
                Settings.DOWNLOAD_WATERMARK
        ));
        addPreference(new TogglePreference(
                context,
                "Custom offline videos",
                "Let the Offline videos menu use your own limit instead of TikTok's fixed one. Restart TikTok after turning this on.",
                Settings.CUSTOM_OFFLINE_VIDEOS
        ));
        addPreference(new NumberInputPreference(
                context,
                "Offline videos limit",
                "Choose 1-1000 videos. Values outside this range use the nearest valid limit. Restart TikTok after saving.",
                Settings.CUSTOM_OFFLINE_VIDEO_LIMIT
        ));

    }
}
