package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.speed.PlaybackSpeedPatch;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("deprecation")
public final class PlaybackPreferenceCategory extends ConditionalPreferenceCategory {
    public PlaybackPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Playback");
    }

    @Override public boolean getSettingsStatus() {
        return SettingsStatus.playbackQualityEnabled || SettingsStatus.playbackSpeedEnabled
                || SettingsStatus.autoAdvanceEnabled || SettingsStatus.videoFitEnabled;
    }

    @Override public void addPreferences(Context context) {
        if (SettingsStatus.autoAdvanceEnabled) {
            addPreference(new TogglePreference(context, "Advance when a video ends",
                    "Keep automatic advance enabled. Pauses and open dialogs still stop scrolling. Restart after enabling it; use this switch to turn it off.",
                    Settings.AUTO_ADVANCE));
        }
        if (SettingsStatus.playbackSpeedEnabled) {
            addPreference(new TogglePreference(context, "Use a default playback speed",
                    "Start each new video at your default. A manual choice lasts until the video changes.",
                    Settings.DEFAULT_SPEED_ENABLED));
            addPreference(new ChoicePreference(context, "Default playback speed", Settings.DEFAULT_SPEED,
                    new String[]{"0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x", "2.5x", "3x"},
                    new String[]{"0.5", "0.75", "1", "1.25", "1.5", "1.75", "2", "2.5", "3"}));
            InputTextPreference speeds = new InputTextPreference(context, "Speed menu choices",
                    "Up to 8 speeds from 0.5 to 3, separated by commas. Example: 0.5, 1, 1.5, 2, 2.5, 3. Leave empty for TikTok's list. Restart after changing this list.",
                    Settings.CUSTOM_SPEEDS);
            speeds.setOnPreferenceChangeListener((preference, value) -> {
                try { PlaybackSpeedPatch.parseMenuSpeeds(value.toString()); return true; }
                catch (IllegalArgumentException error) {
                    Utils.showToastShort("Enter up to 8 comma-separated speeds from 0.5 to 3");
                    return false;
                }
            });
            addPreference(speeds);
        }
        if (SettingsStatus.playbackQualityEnabled) {
            addPreference(new ChoicePreference(context, "Video playback quality", Settings.PLAYBACK_QUALITY,
                    new String[]{"Automatic", "Highest", "Lowest", "1080p", "720p", "540p", "480p", "360p"},
                    new String[]{"auto", "highest", "lowest", "1080", "720", "540", "480", "360"}));
            addPreference(new ChoicePreference(context, "On mobile data", Settings.PLAYBACK_QUALITY_METERED,
                    new String[]{"No limit", "Highest", "Lowest", "1080p", "720p", "540p", "480p", "360p"},
                    new String[]{"off", "highest", "lowest", "1080", "720", "540", "480", "360"}));
        }
        if (SettingsStatus.videoFitEnabled) {
            addPreference(new TogglePreference(context, "Fit the video to the screen",
                    "Show the whole video instead of cropping it to the window. Nothing changes "
                            + "on a tall phone, where it already fits. On a folding phone opened "
                            + "up, a squarer screen or a split view the sides or the ends stop "
                            + "being cut off.",
                    Settings.FIT_VIDEO_TO_SCREEN));
        }
    }
}
