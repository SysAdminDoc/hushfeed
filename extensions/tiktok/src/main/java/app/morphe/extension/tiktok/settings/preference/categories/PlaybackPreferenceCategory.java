/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.speed.PlaybackSpeedPatch;


@SuppressWarnings("deprecation")
public final class PlaybackPreferenceCategory extends ConditionalPreferenceCategory {
    public PlaybackPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Playback");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.playbackQualityEnabled || SettingsStatus.playbackSpeedEnabled
                || SettingsStatus.autoAdvanceEnabled || SettingsStatus.videoFitEnabled
                // The comment sheet switch is a playback switch, and on a bundle with the
                // comment tools and none of the players it is the only thing on this page.
                || SettingsStatus.commentToolsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override public void addPreferences(Context context) {
        if (SettingsStatus.autoAdvanceEnabled) {
            addPreference(new SectionHeadingPreference(context, "Auto-advance"));
            addPreference(new TogglePreference(context, "Auto-advance videos",
                    "Move to the next video when the current one ends. Pauses, open dialogs and "
                            + "screen-time holds stop it. Restart TikTok to apply this.",
                    Settings.AUTO_ADVANCE));
            addPreference(new NumberInputPreference(context, "Auto-advance session limit",
                    "Zero means no limit. Counts the videos Hushfeed advanced past for you, "
                            + "not the ones you swiped yourself, and starts again when the feed "
                            + "is rebuilt or you change this number.",
                    Settings.AUTO_ADVANCE_LIMIT, "%1$s video", "%1$s videos").zeroMeansOff());
            addPreference(new TogglePreference(context, "Hide TikTok's Auto scroll button",
                    "Takes TikTok's own Auto scroll action out of the video panel. "
                            + "Auto-advance keeps working.",
                    Settings.AUTO_ADVANCE_HIDE_PANEL_ACTION));
        }
        if (SettingsStatus.commentToolsEnabled) {
            addPreference(new TogglePreference(context, "Silence the feed while comments are open",
                    "Pauses the video behind the comment sheet while you read. "
                            + "It plays on from the same spot when the sheet closes.",
                    Settings.PAUSE_ON_COMMENTS));
        }

        if (SettingsStatus.playbackSpeedEnabled) {
            addPreference(new SectionHeadingPreference(context, "Speed"));
            addPreference(new TogglePreference(context, "Remember the last speed",
                    "Keep the speed you chose for the next video. Off, each new video "
                            + "starts at 1x and a manual choice lasts for that video only.",
                    Settings.REMEMBER_SPEED));
            addPreference(new TogglePreference(context, "Use a default playback speed",
                    "Start each new video at your default. A manual choice lasts until the video changes.",
                    Settings.DEFAULT_SPEED_ENABLED));
            addPreference(new ChoicePreference(context, "Default playback speed", Settings.DEFAULT_SPEED,
                    new String[]{"0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x", "2.5x", "3x"},
                    new String[]{"0.5", "0.75", "1", "1.25", "1.5", "1.75", "2", "2.5", "3"}));
            String slowest = PlaybackSpeedPatch.speedLabel(PlaybackSpeedPatch.MIN_SPEED);
            String fastest = PlaybackSpeedPatch.speedLabel(PlaybackSpeedPatch.MAX_SPEED);
            InputTextPreference speeds = new InputTextPreference(context, "Speed menu choices",
                    L10n.f(context, "Up to %1$d speeds from %2$s to %3$s, separated by commas. Example: 0.5, 1, 1.5, 2, 2.5, 3. Leave empty for TikTok's list. Restart TikTok to apply this.",
                            PlaybackSpeedPatch.MAX_MENU_SPEEDS, slowest, fastest),
                    Settings.CUSTOM_SPEEDS);
            speeds.withCheck(value -> {
                if (value == null || value.isEmpty()) return null;
                try { PlaybackSpeedPatch.parseMenuSpeeds(value); return null; }
                catch (IllegalArgumentException error) {
                    return L10n.f(context, "Enter up to %1$d comma-separated speeds from %2$s to %3$s",
                            PlaybackSpeedPatch.MAX_MENU_SPEEDS, slowest, fastest);
                }
            });
            speeds.setOnPreferenceChangeListener((preference, value) -> {
                if (value == null || value.toString().isEmpty()) return true;
                try { PlaybackSpeedPatch.parseMenuSpeeds(value.toString()); return true; }
                catch (IllegalArgumentException error) { return false; }
            });
            addPreference(speeds);
            addPreference(new ChoicePreference(context, "Speed while you hold the video", Settings.HOLD_SPEED,
                    new String[]{"1.25x", "1.5x", "1.75x", "2x", "2.5x", "3x"},
                    new String[]{"1.25", "1.5", "1.75", "2", "2.5", "3"}));
        }
        if (SettingsStatus.playbackQualityEnabled || SettingsStatus.videoFitEnabled) {
            addPreference(new SectionHeadingPreference(context, "Quality"));
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
            TogglePreference fit = new TogglePreference(context, "Fit the video to the screen",
                    "Show the whole video instead of cropping it to the window. Nothing changes "
                            + "on a tall phone, where it already fits. On a folding phone opened "
                            + "up, a squarer screen or a split view the sides or the ends stop "
                            + "being cut off.",
                    Settings.FIT_VIDEO_TO_SCREEN);
            TogglePreference fill = new TogglePreference(context, "Fill the screen with the video",
                    "Crop the video until it covers the whole window. On a tall phone the black "
                            + "strip TikTok leaves under a video goes and so does a little of each side.",
                    Settings.FILL_VIDEO_TO_SCREEN);
            // One or the other: the whole video inside the window and the window covered can't
            // both hold, so turning either on turns the other off.
            fit.setOnPreferenceChangeListener((preference, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    Settings.FILL_VIDEO_TO_SCREEN.save(false);
                    fill.setChecked(false);
                }
                return true;
            });
            fill.setOnPreferenceChangeListener((preference, value) -> {
                if (Boolean.TRUE.equals(value)) {
                    Settings.FIT_VIDEO_TO_SCREEN.save(false);
                    fit.setChecked(false);
                }
                return true;
            });
            addPreference(fit);
            addPreference(fill);
        }
    }
}
