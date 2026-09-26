/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.content.Context;
import android.preference.ListPreference;
import android.view.View;
import app.morphe.extension.shared.settings.StringSetting;

@SuppressWarnings("deprecation")
public final class ChoicePreference extends ListPreference {
    public ChoicePreference(Context context, String title, StringSetting setting, String[] labels, String[] values) {
        super(context);
        setTitle(title);
        setDialogTitle(title);
        setKey(setting.key);
        setEntries(labels);
        setEntryValues(values);
        setValue(setting.savedValue());
        setSummary("%s");
        // Left unset, the platform's own Cancel shows, in the phone's language rather than the
        // one the rest of the dialog is translated into.
        setNegativeButtonText(L10n.t(context, "Cancel"));
    }

    @Override protected void showDialog(android.os.Bundle state) {
        super.showDialog(state);
        SettingsUi.styleStandardAlertDialog((android.app.AlertDialog) getDialog());
    }

    @Override protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }

    @Override
    public void setDialogTitle(CharSequence title) {
        super.setDialogTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setEntries(CharSequence[] entries) {
        CharSequence[] translated = new CharSequence[entries.length];
        for (int i = 0; i < entries.length; i++) {
            translated[i] = L10n.t(getContext(), entries[i]);
        }
        super.setEntries(translated);
    }
}
