package app.morphe.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
        text.setText(report);
        text.setTextIsSelectable(true);
        text.setTextColor(SettingsUi.textPrimary());
        text.setTextSize(13);
        int padding = SettingsUi.dp(context, 16);
        text.setPadding(padding, padding, padding, padding);
        scroll.addView(text);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Recorded gate reads (" + FeatureGateLearnMode.lastCandidateCount() + ")")
                .setView(scroll).setPositiveButton("Close", null)
                .setNeutralButton("Copy report", (ignored, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard == null) {
                        app.morphe.extension.shared.Utils.showToastShort("Clipboard is unavailable");
                        return;
                    }
                    clipboard.setPrimaryClip(ClipData.newPlainText("Feature gate recording", report));
                    app.morphe.extension.shared.Utils.showToastShort("Copied feature gate report");
                }).create();
        dialog.show();
        SettingsUi.styleFramedDialog(dialog);
    }

    private void refresh() {
        setTitle(FeatureGateLearnMode.isRecording() ? "Stop feature gate recording" : "Start feature gate recording");
        setSummary(FeatureGateLearnMode.isRecording()
                ? "Return after using a TikTok feature to see every gate read during the recording."
                : "Compare gate reads with their previous values. Last recording: "
                        + FeatureGateLearnMode.lastCandidateCount() + " gates.");
    }

    @Override protected void onBindView(View view) {
        refresh();
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
    }
}
