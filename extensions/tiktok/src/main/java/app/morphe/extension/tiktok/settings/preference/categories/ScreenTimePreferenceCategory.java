/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ClockHourPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.StartTodayOverPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

/**
 * The daily budget with its reminders and hold, and the switch that keeps the feed from starting
 * on its own. These sat at the bottom of Playback behind a "Daily budget" heading, which is not
 * where somebody looking for a limit on their evening would think to scroll.
 */
@SuppressWarnings("deprecation")
public final class ScreenTimePreferenceCategory extends ConditionalPreferenceCategory {
    public ScreenTimePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Screen time");
    }

    /**
     * Whether this page has anything on it. The row into it asks the same question. Every row
     * here counts through the hook that tracks which video is on screen, which the block author
     * patch installs; without it these would take a number and count nothing.
     */
    public static boolean isAvailable() {
        return SettingsStatus.blockAuthorEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override public void addPreferences(Context context) {
        addPreference(new SectionHeadingPreference(context, "Focus"));
        addPreference(new TogglePreference(context, "Do not start the feed on returning",
                "The feed waits for one tap before it starts playing again when you "
                        + "come back to the app. Messages, profiles and search are still "
                        + "one tap away.",
                Settings.NO_RESUME_ON_FOREGROUND));
        addPreference(new TogglePreference(context, "Keep a paused video paused",
                "A video you paused stays paused when you come back to the app instead of "
                        + "starting again. One you left playing starts as usual. "
                        + "It works on Android 10 and newer.",
                Settings.KEEP_PAUSED_ON_RETURN));
        addPreference(new TogglePreference(context, "Leave when TikTok says time is up",
                "TikTok's own daily screen-time reminder sends the app to the background "
                        + "instead of waiting to be dismissed. Coming straight back leaves the "
                        + "reminder on screen, so the limit stays reachable. Needs a daily limit "
                        + "set under TikTok's Settings and privacy, under Time and well-being.",
                Settings.LEAVE_ON_REST_REMINDER));

        addPreference(new SectionHeadingPreference(context, "Daily budget"));
        addPreference(new NumberInputPreference(context, "Daily video budget",
                "Zero switches this off. Count every video that comes up in the feed, however you "
                        + "got to it, and say so once the count is reached. This is separate from "
                        + "the auto-advance session limit under Playback, which only counts videos "
                        + "Hushfeed itself advanced past.", Settings.SESSION_BUDGET_VIDEOS, "%1$s video", "%1$s videos") {
            @Override protected String extraSummaryLine() {
                if (Settings.SESSION_BUDGET_VIDEOS.savedValue() <= 0) return null;
                int seen = SessionBudget.videosSeen();
                return L10n.quantity(getContext(), seen, "Today: %1$d video", "Today: %1$d videos");
            }
        }.zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Daily time budget",
                "Zero switches this off. Count the minutes the player spends running in the feed. "
                        + "Time on messages, a profile or search does not count.",
                Settings.SESSION_BUDGET_MINUTES, "%1$s minute", "%1$s minutes") {
            @Override protected String extraSummaryLine() {
                if (Settings.SESSION_BUDGET_MINUTES.savedValue() <= 0) return null;
                // Whole minutes down, so a budget of 30 never reads "Today: 30 minutes" while
                // there is still time left on it.
                long minutes = SessionBudget.watchedMs() / 60_000L;
                return L10n.quantity(getContext(), minutes, "Today: %1$d minute", "Today: %1$d minutes");
            }
        }.zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Remind me every",
                "Zero switches this off. A short reminder after that many minutes of watching, "
                        + "and again after the same again. Time on messages, a profile or search "
                        + "does not count, and nothing is shown while the feed is on hold.",
                Settings.SESSION_BUDGET_NOTICE_MINUTES, "%1$s minute", "%1$s minutes").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Hold the feed after the budget",
                "Zero shows the notice and leaves the feed alone. Anything else covers the feed "
                        + "for that many minutes once a budget is reached. Messages, profiles and "
                        + "search keep working, and nothing in the feed is thrown away.",
                Settings.SESSION_BUDGET_LOCK_MINUTES, "%1$s minute", "%1$s minutes"));
        addPreference(new TogglePreference(context, "Fade the feed out before the hold",
                "The feed dims over the last three quarters of a minute before the hold, "
                        + "so you arrive at it rather than land on it. Needs a time budget and "
                        + "a hold to arrive at.",
                Settings.SESSION_BUDGET_RAMP));
        addPreference(new TogglePreference(context, "Show what is left of the budget",
                "A small label on the feed shows the minutes or videos left of today's "
                        + "budget, whichever is closer to running out.",
                Settings.SESSION_BUDGET_CUE));
        addPreference(new ClockHourPreference(context, "Start the day at",
                "The hour both budgets reset, on a 24 hour clock. Four in the morning by default, "
                        + "because someone still scrolling at one is having last night.",
                Settings.SESSION_BUDGET_RESET_HOUR));
        addPreference(new TogglePreference(context, "Lock today's budget",
                "Once today's budget runs out, the hold stays and cannot be dismissed. "
                        + "The budget settings are locked until the day starts over. "
                        + "Turn this off any time before the budget runs out.",
                Settings.SESSION_BUDGET_LOCK));
        addPreference(new NumberInputPreference(context, "Times you can open the feed anyway",
                "Zero leaves the way out of the hold there every time, which is what it has "
                        + "always done. Anything else is how many times a day it works, and once "
                        + "they are gone the hold stays up until the day starts over. Ignored "
                        + "while the budget is locked, which takes the way out away entirely.",
                Settings.SESSION_BUDGET_PASSES_PER_DAY, "%1$s time", "%1$s times").zeroMeansOff());

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
}
