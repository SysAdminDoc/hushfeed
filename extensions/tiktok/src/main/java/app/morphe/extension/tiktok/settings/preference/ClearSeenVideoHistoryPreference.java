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
public final class ClearSeenVideoHistoryPreference extends Preference {
    static final String CLEAR_SUMMARY = "Delete the local record of the videos you have watched.";
    static final String UNDO_SUMMARY = "Cleared. Tap again to put the record back.";

    public ClearSeenVideoHistoryPreference(Context context) {
        super(context);
        setTitle("Clear the seen video history");
        // Whether a clear is waiting to be undone outlives this row, so the row has to ask
        // rather than assume it is the first one ever built.
        setSummary(SeenVideoHistory.canUndo() ? UNDO_SUMMARY : CLEAR_SUMMARY);

        // One tap clears it. Nothing is lost that cannot be put back, so the way back is
        // the next tap rather than a dialog asking permission first.
        setOnPreferenceClickListener(preference -> {
            if (SeenVideoHistory.canUndo()) {
                boolean restored = SeenVideoHistory.undoClear();
                Utils.showToastShort(L10n.t(context, restored
                        ? "Seen video history put back"
                        : "There was nothing to put back"));
                setSummary(CLEAR_SUMMARY);
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
