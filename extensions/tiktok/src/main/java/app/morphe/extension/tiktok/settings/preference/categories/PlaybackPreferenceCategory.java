package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;

@SuppressWarnings("deprecation")
public final class PlaybackPreferenceCategory extends ConditionalPreferenceCategory {
    public PlaybackPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Playback");
    }

    @Override public boolean getSettingsStatus() { return SettingsStatus.playbackQualityEnabled; }

    @Override public void addPreferences(Context context) {
        if (SettingsStatus.playbackQualityEnabled) {
            addPreference(new ChoicePreference(context, "Video playback quality", Settings.PLAYBACK_QUALITY,
                    new String[]{"Automatic", "Highest", "Lowest", "1080p", "720p", "540p", "480p", "360p"},
                    new String[]{"auto", "highest", "lowest", "1080", "720", "540", "480", "360"}));
        }
    }
}
