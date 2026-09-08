/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows BlueDragon4251/tiktok-patches-for-morphe.
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.seen.SeenVideoHistory;

@SuppressWarnings("deprecation")
public final class ClearSeenVideoHistoryPreference extends Preference
        implements app.morphe.extension.shared.settings.preference.ImmediateAction {
    @Override public boolean actsOnTap() { return true; }

    static final String CLEAR_SUMMARY = "Delete the local record of the videos you have watched.";
    static final String UNDO_SUMMARY = "Cleared. Tap again to put the record back.";
    static final String NOT_READY = "Still reading the record. Tap again in a moment.";
    static final String FAILED = "Could not put back the seen video history. Try again.";

    public ClearSeenVideoHistoryPreference(Context context) {
        super(context);
        // A key so the settings search can index this row. A key with no Setting behind
        // it is skipped by the settings framework, so nothing tries to persist it.
        setKey("action_clear_seen_video_history");
        setTitle("Clear the seen video history");
        // Whether a clear is waiting to be undone outlives this row, so the row has to ask
        // rather than assume it is the first one ever built.
        setSummary(SeenVideoHistory.canUndo() ? UNDO_SUMMARY : CLEAR_SUMMARY);

        // One tap clears it. Nothing is lost that cannot be put back, so the way back is
        // the next tap rather than a dialog asking permission first.
        setOnPreferenceClickListener(preference -> {
            if (SeenVideoHistory.canUndo()) {
                SeenVideoHistory.undoClear(result -> {
                    String message;
                    if (result == SeenVideoHistory.UndoResult.RESTORED) {
                        message = "Seen video history put back";
                    } else if (result == SeenVideoHistory.UndoResult.FAILED) {
                        message = FAILED;
                    } else if (result == SeenVideoHistory.UndoResult.EMPTY) {
                        message = "There was nothing to put back";
                    } else if (result == SeenVideoHistory.UndoResult.SUPERSEDED) {
                        message = "A newer change replaced that undo. Tap Undo again.";
                    } else {
                        message = NOT_READY;
                    }
                    Utils.showToastShort(L10n.t(context, message));
                    setSummary(result == SeenVideoHistory.UndoResult.RESTORED
                            || result == SeenVideoHistory.UndoResult.EMPTY
                            ? CLEAR_SUMMARY
                            : UNDO_SUMMARY);
                });
                // The offer survives a tap that arrives before the copy has been read or while
                // SQLite is retrying, so leave the row ready for another tap until the callback
                // reports a durable result.
                setSummary(UNDO_SUMMARY);
                return true;
            }

            SeenVideoHistory.clear();
            Utils.showToastLong(L10n.t(context, "Seen video history cleared. Tap again to put it back."));
            setSummary(UNDO_SUMMARY);
            return true;
        });
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        SettingsUi.styleTitleAndSummary(view);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }
}
