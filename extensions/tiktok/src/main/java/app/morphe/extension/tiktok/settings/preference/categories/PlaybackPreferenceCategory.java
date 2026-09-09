package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.ClockHourPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.StartTodayOverPreference;
import app.morphe.extension.tiktok.speed.PlaybackSpeedPatch;
import android.preference.Preference;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

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
                || SettingsStatus.blockAuthorEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override public void addPreferences(Context context) {
        if (SettingsStatus.autoAdvanceEnabled) {
            addPreference(new TogglePreference(context, "Advance when a video ends",
                    "Keep automatic advance enabled, and show TikTok's own Auto scroll action in "
                            + "the video panel even if your account never had it. Pauses and open "
                            + "dialogs still stop scrolling. Restart after enabling it, then use "
                            + "this switch to turn it off.",
                    Settings.AUTO_ADVANCE));
            addPreference(new NumberInputPreference(context, "Auto-advance session limit",
                    "Zero keeps auto-advance unlimited. Count only videos that finish while Hushfeed "
                            + "started automatic advance; prefetches, manual swipes and native-only "
                            + "advance do not count. The count resets when the feed component is "
                            + "recreated and stays stopped across backgrounding until it is recreated "
                            + "or the limit changes.", Settings.AUTO_ADVANCE_LIMIT, "video", "videos").zeroMeansOff());
        }
        // The counting hangs off the hook that tracks which video is on screen, which the
        // block author patch installs. Without it these would take a number and count nothing.
        if (SettingsStatus.blockAuthorEnabled) {
        // Both budgets carry how much of today has gone, which until now was only visible in
        // the one notice when it ran out. Read when the page is built, which is what a settings
        // screen shows: it is a figure for the day, not a ticker.
        addPreference(new NumberInputPreference(context, "Daily video budget",
                "Zero switches this off. Count every video that comes up in the feed, however you "
                        + "got to it, and say so once the count is reached. This is separate from "
                        + "the auto-advance limit above, which only counts videos Hushfeed itself "
                        + "advanced past.", Settings.SESSION_BUDGET_VIDEOS, "video", "videos") {
            @Override protected String extraSummaryLine() {
                if (Settings.SESSION_BUDGET_VIDEOS.get() <= 0) return null;
                int seen = SessionBudget.videosSeen();
                return seen == 1
                        ? L10n.f(getContext(), "Today: %1$d video", seen)
                        : L10n.f(getContext(), "Today: %1$d videos", seen);
            }
        }.zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Daily time budget",
                "Zero switches this off. Count the minutes the player spends running in the feed. "
                        + "Time on messages, a profile or search does not count.",
                Settings.SESSION_BUDGET_MINUTES, "minute", "minutes") {
            @Override protected String extraSummaryLine() {
                if (Settings.SESSION_BUDGET_MINUTES.get() <= 0) return null;
                // Whole minutes down, so a budget of 30 never reads "Today: 30 minutes" while
                // there is still time left on it.
                long minutes = SessionBudget.watchedMs() / 60_000L;
                return minutes == 1
                        ? L10n.f(getContext(), "Today: %1$d minute", minutes)
                        : L10n.f(getContext(), "Today: %1$d minutes", minutes);
            }
        }.zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Remind me every",
                "Zero switches this off. A short reminder after that many minutes of watching, "
                        + "and again after the same again. Time on messages, a profile or search "
                        + "does not count, and nothing is shown while the feed is on hold.",
                Settings.SESSION_BUDGET_NOTICE_MINUTES, "minute", "minutes").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Hold the feed after the budget",
                "Zero shows the notice and leaves the feed alone. Anything else covers the feed "
                        + "for that many minutes once a budget is reached. Messages, profiles and "
                        + "search keep working, and nothing in the feed is thrown away.",
                Settings.SESSION_BUDGET_LOCK_MINUTES, "minute", "minutes"));
        addPreference(new TogglePreference(context, "Fade the feed out before the hold",
                "Off by default. Switched on, the feed dims over the last three quarters of a "
                        + "minute of a time budget, most of it in the final half minute, so the "
                        + "hold is somewhere you arrive rather than somewhere you land. Needs a "
                        + "budget in minutes: a budget counted in videos has no time left to "
                        + "follow. Nothing fades if you have turned animations off.",
                Settings.SESSION_BUDGET_RAMP));
        addPreference(new ClockHourPreference(context, "Start the day at",
                "The hour both budgets reset, on a 24 hour clock. Four in the morning by default, "
                        + "because someone still scrolling at one is having last night.",
                Settings.SESSION_BUDGET_RESET_HOUR));
        addPreference(new TogglePreference(context, "Lock today's budget",
                "Off by default. Switched on, the hold that starts when today's budget runs out "
                        + "has no way out, and the budgets, the reset hour and this switch cannot "
                        + "be changed again until the day starts over. Switch it off any time "
                        + "before the budget runs out.",
                Settings.SESSION_BUDGET_LOCK));
        addPreference(new NumberInputPreference(context, "Times you can open the feed anyway",
                "Zero leaves the way out of the hold there every time, which is what it has "
                        + "always done. Anything else is how many times a day it works, and once "
                        + "they are gone the hold stays up until the day starts over. Ignored "
                        + "while the budget is locked, which takes the way out away entirely.",
                Settings.SESSION_BUDGET_PASSES_PER_DAY, "time", "times").zeroMeansOff());

        // Everything the budget is made of, refused for the rest of a locked day. A commitment
        // anyone can edit their way out of in two taps is a suggestion.
        Preference.OnPreferenceChangeListener refuseWhileLocked = (preference, value) -> {
            if (!SessionBudget.lockedToday()) return true;
            Utils.showToastShort(L10n.f(context,
                    "Today's budget is locked. This can be changed again at %1$s.",
                    SessionLockOverlay.resetTimeLabel()));
            return false;
        };
        for (String key : new String[]{Settings.SESSION_BUDGET_VIDEOS.key,
                Settings.SESSION_BUDGET_MINUTES.key, Settings.SESSION_BUDGET_LOCK_MINUTES.key,
                Settings.SESSION_BUDGET_RESET_HOUR.key,
                Settings.SESSION_BUDGET_PASSES_PER_DAY.key,
                Settings.SESSION_BUDGET_NOTICE_MINUTES.key}) {
            Preference row = findPreference(key);
            if (row != null) row.setOnPreferenceChangeListener(refuseWhileLocked);
        }

        // The switch itself refuses the same way, and turning it on when the budget has already
        // run out locks the rest of that day. Left to work it out from the switch and the counts
        // together, lowering the budget under the count you already had locked the day for
        // someone who never reached it.
        Preference lockRow = findPreference(Settings.SESSION_BUDGET_LOCK.key);
        if (lockRow != null) {
            lockRow.setOnPreferenceChangeListener((preference, value) -> {
                if (!refuseWhileLocked.onPreferenceChange(preference, value)) return false;
                if (Boolean.TRUE.equals(value)) SessionBudget.lockIfSpent();
                return true;
            });
        }

        addPreference(new StartTodayOverPreference(context));
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
                    Utils.showToastShort(L10n.t("Enter up to 8 comma-separated speeds from 0.5 to 3"));
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
