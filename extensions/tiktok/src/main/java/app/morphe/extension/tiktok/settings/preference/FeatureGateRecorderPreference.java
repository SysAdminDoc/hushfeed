package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import app.morphe.extension.tiktok.Utils;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLearnMode;

@SuppressWarnings("deprecation")
public final class FeatureGateRecorderPreference extends Preference {
    public FeatureGateRecorderPreference(Context context) {
        super(context);
        setKey("feature_gate_recorder");
        refresh();
        setOnPreferenceClickListener(preference -> {
            if (FeatureGateLearnMode.isRecording()) {
                showReport(context, FeatureGateLearnMode.stopAndBuildReport());
            } else {
                FeatureGateLearnMode.begin();
                app.morphe.extension.shared.Utils.showToastShort("Recording gate reads. Use a feature, then return here to stop.");
            }
            refresh();
            notifyChanged();
            return true;
        });
    }

    public static void showReport(Context context, String report) {
        ScrollView scroll = new ScrollView(context);
        TextView text = new TextView(context);
        text.setText(report.length() <= GateReportExport.MAX_CLIPBOARD_CHARS ? report
                : report.substring(0, GateReportExport.MAX_CLIPBOARD_CHARS) + "\n\nPreview shortened. Save JSON includes the full report.");
        text.setTextIsSelectable(true);
        text.setTextColor(SettingsUi.textPrimary());
        text.setTextSize(13);
        int padding = SettingsUi.dp(context, 16);
        text.setPadding(padding, padding, padding, padding);
        scroll.addView(text);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(L10n.f(context, "Recorded gate reads (%d)", FeatureGateLearnMode.lastCandidateCount()))
                .setView(scroll).setPositiveButton(L10n.t(context, "Close"), null)
                .setNegativeButton(L10n.t(context, "Save JSON"), (ignored, which) -> GateReportExport.save(context, report));
        if (report.length() <= GateReportExport.MAX_CLIPBOARD_CHARS) {
            builder.setNeutralButton(L10n.t(context, "Copy report"), (ignored, which) -> GateReportExport.copy(context, report));
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        SettingsUi.styleFramedDialog(dialog);
    }

    private void refresh() {
        setTitle(FeatureGateLearnMode.isRecording() ? "Stop feature gate recording" : "Start feature gate recording");
        setSummary(FeatureGateLearnMode.isRecording()
                ? "Return after using a TikTok feature to see every gate read during the recording."
                : L10n.f(getContext(), "Compare gate reads with their previous values. Last recording: %d gates.",
                        FeatureGateLearnMode.lastCandidateCount()));
    }

    @Override protected void onBindView(View view) {
        refresh();
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
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
