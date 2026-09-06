/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows BlueDragon4251/tiktok-patches-for-morphe.
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.seen.SeenVideoHistory;

@SuppressWarnings("deprecation")
public final class ClearSeenVideoHistoryPreference extends Preference {
    public ClearSeenVideoHistoryPreference(Context context) {
        super(context);
        setTitle("Clear the seen video history");
        setSummary("Delete the local record of the videos you have watched.");
        setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(context)
                    .setTitle(L10n.t(context, "Clear the seen video history?"))
                    .setMessage(L10n.t(context, "This deletes the local record only. Your TikTok account history "
                            + "is not touched."))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(L10n.t(context, "Clear"), (dialog, which) -> {
                        SeenVideoHistory.clear();
                        Utils.showToastShort(L10n.t(context, "Seen video history cleared"));
                    })
                    .show();
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
