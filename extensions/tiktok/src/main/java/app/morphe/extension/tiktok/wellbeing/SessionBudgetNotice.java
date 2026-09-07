/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Says once that the day's budget has gone, and gets out of the way.
 *
 * <p>With no hold set there is nothing to argue with, so this is a toast. With one, it is the
 * banner the block button already uses, carrying an Undo that lifts the hold. A budget nobody
 * can overrule is a budget people switch off instead, and someone who decides they want another
 * ten minutes should not have to go through settings to get them.
 */
public final class SessionBudgetNotice {
    private SessionBudgetNotice() {
    }

    public static void show() {
        String message = L10n.f("That is %1$d videos today", SessionBudget.videosSeen());
        if (Settings.SESSION_BUDGET_LOCK_MINUTES.get() <= 0) {
            Utils.showToastShort(message);
            return;
        }

        BlockAuthorOverlay.showUndoBanner(message, () -> {
            SessionBudget.releaseLock();
            SessionLockOverlay.sync();
            Utils.showToastShort(L10n.t("The feed is open again"));
        });
        SessionLockOverlay.ensureRunning();
    }
}
