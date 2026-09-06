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
        setSummary(CLEAR_SUMMARY);
        // One tap clears it. Nothing is lost that cannot be put back, so the way back is
        // the next tap rather than a dialog asking permission first.
        setOnPreferenceClickListener(preference -> {
            if (SeenVideoHistory.undoSize() > 0 && SeenVideoHistory.size() == 0) {
                int restored = SeenVideoHistory.undoSize();
                if (SeenVideoHistory.undoClear()) {
                    Utils.showToastShort(L10n.f(context, "Put back %1$s videos", String.valueOf(restored)));
                    setSummary(CLEAR_SUMMARY);
                    return true;
                }
            }

            int cleared = SeenVideoHistory.size();
            SeenVideoHistory.clear();
            if (cleared == 0) {
                Utils.showToastShort(L10n.t(context, "There was nothing to clear"));
                return true;
            }
            Utils.showToastLong(L10n.f(context, "Cleared %1$s videos. Tap again to put them back.",
                    String.valueOf(cleared)));
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
