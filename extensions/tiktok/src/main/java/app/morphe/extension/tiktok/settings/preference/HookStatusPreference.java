/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.view.View;

import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.settings.L10n;

import java.util.List;

/**
 * Says whether the hooks found the app they attach to.
 *
 * <p>The patcher only knows what it wrote into the APK. Whether a hook then found its anchor at
 * runtime is a separate question, and when it does not the switch above still reads on while
 * nothing happens. This row answers that question before someone files a report about a feature
 * that was never running.
 */
@SuppressWarnings("deprecation")
public class HookStatusPreference extends Preference {
    public HookStatusPreference(Context context) {
        super(context);
        setTitle(L10n.t(context, "Hook status"));
        setOnPreferenceClickListener(preference -> {
            showReport();
            return true;
        });
    }

    /**
     * Read fresh every time the row is drawn. A surface reports the first time the app opens it,
     * so the answer changes while the settings screen is still up.
     */
    @Override
    public CharSequence getSummary() {
        Context context = getContext();
        List<String> report = HookStatus.report();
        if (report.isEmpty()) {
            return L10n.t(context,
                    "Nothing has been looked up yet. Use the app for a moment, then come back.");
        }
        if (!HookStatus.anyMissing()) {
            return L10n.f(context, "Every hook found what it needed across %d surfaces.",
                    report.size());
        }
        return L10n.t(context,
                "Something is missing from this TikTok build. Tap to see which surface.");
    }

    private void showReport() {
        Context context = getContext();
        List<String> report = HookStatus.report();
        StringBuilder message = new StringBuilder();
        if (report.isEmpty()) {
            message.append(L10n.t(context,
                    "No surface has looked anything up yet, so there is nothing to report."));
        } else {
            for (String line : report) {
                if (message.length() > 0) message.append("\n\n");
                message.append(line);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(L10n.t(context, "Hook status"))
                .setMessage(message.toString())
                .setPositiveButton(L10n.t(context, "Close"), null)
                .show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
    }
}
