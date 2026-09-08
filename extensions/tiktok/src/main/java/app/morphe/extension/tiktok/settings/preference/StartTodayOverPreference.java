/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.ImmediateAction;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

/**
 * Forgets what has been counted today.
 *
 * <p>One tap does it, with no dialog first, because the next tap puts it back. That is the same
 * bargain the seen video history row makes. This row used to make it without keeping it: a
 * mis-tap lifted a running hold and there was nothing at all to undo it with.
 */
@SuppressWarnings("deprecation")
public final class StartTodayOverPreference extends Preference implements ImmediateAction {
    static final String CLEAR_SUMMARY =
            "Forget what has been counted today and end any hold. The budgets themselves are left alone.";
    static final String UNDO_SUMMARY = "Today started over. Tap again to put the counts back.";

    @Override public boolean actsOnTap() {
        return true;
    }

    public StartTodayOverPreference(Context context) {
        super(context);
        // A key so the settings search can index this row. A key with no Setting behind it is
        // skipped by the settings framework, so nothing tries to persist it.
        setKey("action_start_today_over");
        setTitle(L10n.t(context, "Start today over"));
        // Whether a clear is waiting to be taken back outlives this row, so the row asks rather
        // than assuming it is the first one ever built.
        setSummary(L10n.t(context, SessionBudget.canUndoClear() ? UNDO_SUMMARY : CLEAR_SUMMARY));

        setOnPreferenceClickListener(preference -> {
            if (SessionBudget.canUndoClear()) {
                boolean back = SessionBudget.undoClear();
                SessionLockOverlay.sync();
                setSummary(L10n.t(context, CLEAR_SUMMARY));
                Utils.showToastShort(L10n.t(context, back
                        ? "Today is back where it was"
                        : "Today has moved on, so there is nothing to put back"));
                return true;
            }
            if (!SessionBudget.clear()) {
                Utils.showToastShort(L10n.f(context,
                        "Today's budget is locked. The day starts over at %1$s.",
                        SessionLockOverlay.resetTimeLabel()));
                return true;
            }
            SessionLockOverlay.sync();
            setSummary(L10n.t(context, UNDO_SUMMARY));
            Utils.showToastShort(L10n.t(context, "Today starts again"));
            return true;
        });
    }
}
