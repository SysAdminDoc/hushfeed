package app.morphe.extension.tiktok.settings.preference.categories;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.preference.PreferenceScreen;
import android.view.View;

import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.preference.ClearLogBufferPreference;
import app.morphe.extension.shared.settings.preference.ExportDiagnosticReportPreference;
import app.morphe.extension.shared.settings.preference.LogExportFilterPreference;
import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.HookStatusPreference;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class DebugPreferenceCategory extends ConditionalPreferenceCategory {
    public DebugPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Diagnostics");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.diagnosticsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(
                context,
                "Enable diagnostic logging",
                "Only enable when recording logs to report an issue. Leaving it on for too long can make TikTok feel laggy and may lead to crashes.",
                BaseSettings.DEBUG
        ));

        addPreference(new TogglePreference(
                context,
                "Capture crash reports locally",
                "Save the latest available TikTok crash report for diagnostic export.",
                BaseSettings.CAPTURE_JAVA_CRASHES
        ));

        addPreference(new HookStatusPreference(context));

        var logFilter = new TintedLogExportFilterPreference(context);
        logFilter.setTitle(L10n.t(context, "Included diagnostics"));
        addPreference(logFilter);

        var exportLogs = new TintedExportDiagnosticReportPreference(context);
        exportLogs.setTitle(L10n.t(context, "Export diagnostic report"));
        exportLogs.setSummary(L10n.t(context, "Copy a quick report or save the full report as a file."));
        addPreference(exportLogs);

        var clearLogs = new TintedClearLogBufferPreference(context);
        clearLogs.setTitle(L10n.t(context, "Clear diagnostic data"));
        clearLogs.setSummary(L10n.t(context,
                "Clear buffered events, saved crash reports and the hook status above."));
        addPreference(clearLogs);
    }

    private static class TintedExportDiagnosticReportPreference extends ExportDiagnosticReportPreference {
        TintedExportDiagnosticReportPreference(Context context) {
            super(context);
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
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }

        // The dialog's own words. Without these a translated row opened an English dialog.
        @Override
        protected CharSequence dialogTitle() {
            return L10n.t(getContext(), "Export diagnostic report");
        }

        @Override
        protected CharSequence[] labels() {
            return new CharSequence[]{
                    L10n.t(getContext(), "Copy quick report"),
                    L10n.t(getContext(), "Save full report"),
            };
        }

        @Override
        protected CharSequence negativeText() {
            return L10n.t(getContext(), "Cancel");
        }


        @Override
        protected void onDialogShown(AlertDialog dialog) {
            SettingsUi.styleStandardAlertDialog(dialog);
        }
    }

    private static class TintedLogExportFilterPreference extends LogExportFilterPreference {
        TintedLogExportFilterPreference(Context context) {
            super(context);
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
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }

        @Override
        protected void onDialogShown(AlertDialog dialog) {
            SettingsUi.styleStandardAlertDialog(dialog);
        }
        // The picker's own words. The shared class cannot reach a translation table, because it
        // is shared with bundles that carry none, so this bundle hands its own back.
        @Override
        protected String[] labels() {
            String[] english = super.labels();
            String[] translated = new String[english.length];
            for (int index = 0; index < english.length; index++) {
                translated[index] = L10n.t(getContext(), english[index]);
            }
            return translated;
        }

        @Override
        protected CharSequence dialogTitle() {
            return L10n.t(getContext(), "Include diagnostic events");
        }

        @Override
        protected CharSequence positiveText() {
            return L10n.t(getContext(), "Apply");
        }

        @Override
        protected CharSequence negativeText() {
            return L10n.t(getContext(), "Cancel");
        }

        @Override
        protected CharSequence allEventsSummary() {
            return L10n.t(getContext(), "Includes all diagnostic events.");
        }

        @Override
        protected CharSequence includesSummary(String kinds) {
            return L10n.f(getContext(), "Includes these events: %1$s", kinds);
        }
    }

    private static class TintedClearLogBufferPreference extends ClearLogBufferPreference {
        TintedClearLogBufferPreference(Context context) {
            super(context);
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
        protected void onBindView(View view) {
            super.onBindView(view);
            Utils.setTitleAndSummaryColor(view);
        }
    }
}
