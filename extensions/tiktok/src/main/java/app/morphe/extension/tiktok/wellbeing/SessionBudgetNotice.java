/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Says once that the day's budget has gone, and gets out of the way.
 *
 * <p>With no hold set there is nothing to argue with, so this is a toast and that is the end of
 * it. With a hold, the overlay is what carries the message and the way out of it: a banner would
 * be drawn under the panel, which covers the whole content root and swallows the touch, so the
 * Undo has to live on the panel itself.
 */
public final class SessionBudgetNotice {
    private SessionBudgetNotice() {
    }

    public static void show() {
        if (Settings.SESSION_BUDGET_LOCK_MINUTES.get() > 0) {
            SessionLockOverlay.ensureRunning();
            return;
        }
        Utils.showToastShort(spentMessage());
    }

    /**
     * The quiet reminder partway through, if one is due.
     *
     * <p>A toast rather than a banner: it takes no focus, goes on its own, and TalkBack reads it
     * once. That is the same shape the spent notice uses when no hold is set, and for the same
     * reason. A banner over the feed would need a dismissal of its own and would be drawn under
     * the hold panel on exactly the days the hold is up.
     *
     * <p>Three wordings in rotation, because a fixed friction stops being read. None of them
     * names the reader or the count: an interrupt that does reads as being watched, and the one
     * lab study to measure it found that made people watch more, not less.
     */
    public static void showIntervalNoticeIfDue() {
        int wording = SessionBudget.claimIntervalNotice();
        if (wording < 0) return;
        Utils.showToastShort(intervalMessage(wording));
    }

    static String intervalMessage(int wording) {
        switch (wording) {
            case 0: return L10n.t("Still here. Nothing is waiting.");
            case 1: return L10n.t("A good place to stop, if you want one.");
            default: return L10n.t("The feed does not end. This is a fine time to leave it.");
        }
    }

    /** What the day came to, in whichever budget ran out. */
    static String spentMessage() {
        int minuteBudget = Settings.SESSION_BUDGET_MINUTES.get();
        long watchedMinutes = SessionBudget.watchedMs() / 60_000L;
        // Both can be set; whichever is over is the one worth naming, and the videos come first
        // because that is the number a reader recognises.
        int videoBudget = Settings.SESSION_BUDGET_VIDEOS.get();
        if (videoBudget > 0 && SessionBudget.videosSeen() >= videoBudget) {
            return videosToday(SessionBudget.videosSeen());
        }
        if (minuteBudget > 0 && watchedMinutes >= minuteBudget) {
            return watchedMinutes == 1
                    ? L10n.t("That is one minute today")
                    : L10n.f("That is %1$d minutes today", watchedMinutes);
        }
        return videosToday(SessionBudget.videosSeen());
    }

    /** A budget of one produced "That is 1 videos today", which no phrasebook forgives. */
    private static String videosToday(int videos) {
        return videos == 1
                ? L10n.t("That is one video today")
                : L10n.f("That is %1$d videos today", videos);
    }
}
