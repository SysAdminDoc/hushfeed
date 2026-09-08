package app.morphe.extension.shared.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import java.util.LinkedHashSet;
import java.util.Set;

import app.morphe.extension.shared.settings.BaseSettings;

@SuppressWarnings({"deprecation", "unused"})
public class LogExportFilterPreference extends Preference {
    private static final String VALUE_ALL = "all";
    private static final String VALUE_FOLLOW = "follow";
    private static final String VALUE_DOWNLOADS = "downloads";
    private static final String VALUE_FEED = "feed";
    private static final String VALUE_FEATURE_GATE = "feature_gate";
    private static final String VALUE_SETTINGS = "settings";
    private static final String VALUE_ERRORS = "errors";
    private static final String VALUE_OTHER = "other";

    private static final String[] VALUES = {
            VALUE_ALL,
            VALUE_FOLLOW,
            VALUE_DOWNLOADS,
            VALUE_FEED,
            VALUE_FEATURE_GATE,
            VALUE_SETTINGS,
            VALUE_ERRORS,
            VALUE_OTHER
    };

    private static final String[] LABELS = {
            "All events",
            "Follow probe",
            "Downloads",
            "Feed and navigation",
            "Feature Gate Lab",
            "Settings",
            "Errors",
            "Other"
    };

    {
        // A key so the settings search can index this row. Nothing in the settings
        // framework treats it as a setting: a key with no Setting behind it is skipped.
        setKey("action_included_diagnostics");
        setOnPreferenceClickListener(pref -> {
            showPicker();
            return true;
        });
    }

    public LogExportFilterPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        updateSummary();
    }

    public LogExportFilterPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        updateSummary();
    }

    public LogExportFilterPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateSummary();
    }

    public LogExportFilterPreference(Context context) {
        super(context);
        updateSummary();
    }

    private void showPicker() {
        boolean[] checked = checkedValues();

        AlertDialog shownDialog = new AlertDialog.Builder(getContext())
                .setTitle("Include diagnostic events")
                .setMultiChoiceItems(LABELS, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                    AlertDialog alertDialog = (AlertDialog) dialog;

                    if (which == 0 && isChecked) {
                        for (int i = 1; i < checked.length; i++) {
                            checked[i] = false;
                            alertDialog.getListView().setItemChecked(i, false);
                        }
                    } else if (which > 0 && isChecked) {
                        checked[0] = false;
                        alertDialog.getListView().setItemChecked(0, false);
                    }
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    BaseSettings.DEBUG_LOG_FILTERS.save(serialize(checked));
                    updateSummary();
                })
                .setNegativeButton("Cancel", null)
                .show();
        onDialogShown(shownDialog);
    }

    protected void onDialogShown(AlertDialog dialog) {
    }

    private boolean[] checkedValues() {
        Set<String> selected = parse(BaseSettings.DEBUG_LOG_FILTERS.get());
        boolean[] checked = new boolean[VALUES.length];

        if (selected.isEmpty() || selected.contains(VALUE_ALL)) {
            checked[0] = true;
            return checked;
        }

        for (int i = 1; i < VALUES.length; i++) {
            checked[i] = selected.contains(VALUES[i]);
        }

        return checked;
    }

    private String serialize(boolean[] checked) {
        if (checked[0]) return VALUE_ALL;

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < checked.length; i++) {
            if (!checked[i]) continue;

            if (builder.length() > 0) builder.append(',');
            builder.append(VALUES[i]);
        }

        return builder.length() == 0 ? VALUE_ALL : builder.toString();
    }

    private void updateSummary() {
        Set<String> selected = parse(BaseSettings.DEBUG_LOG_FILTERS.get());
        if (selected.isEmpty() || selected.contains(VALUE_ALL)) {
            setSummary("Includes all Morphe diagnostic events.");
            return;
        }

        StringBuilder builder = new StringBuilder("Includes ");
        int labelCount = 0;
        for (int i = 1; i < VALUES.length; i++) {
            if (!selected.contains(VALUES[i])) continue;

            if (labelCount > 0) builder.append(", ");
            builder.append(LABELS[i].toLowerCase());
            labelCount++;
        }
        builder.append(" events.");

        setSummary(builder.toString());
    }

    public static Set<String> parse(String value) {
        Set<String> selected = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) return selected;

        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) selected.add(trimmed);
        }

        return selected;
    }
}
