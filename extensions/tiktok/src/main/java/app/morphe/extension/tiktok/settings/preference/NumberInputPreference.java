package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.tiktok.Utils;

@SuppressWarnings("deprecation")
public class NumberInputPreference extends EditTextPreference {
    private final String baseSummary;
    private final IntegerSetting setting;
    private final int minValue;
    private final int maxValue;
    private final String singularUnit;
    private final String pluralUnit;

    public NumberInputPreference(Context context, String title, String summary,
                                 IntegerSetting setting) {
        this(context, title, summary, setting, "video", "videos");
    }

    /**
     * The range comes from the setting, not from here. Passing it separately meant the dialog
     * and the stored value could disagree, and only the dialog was ever enforcing it: a
     * restored backup went straight into the setting without being asked anything.
     */
    public NumberInputPreference(Context context, String title, String summary,
                                 IntegerSetting setting, String unit) {
        this(context, title, summary, setting, unit, unit);
    }

    /** Uses the singular label only for one; zero and every other value use the plural. */
    public NumberInputPreference(Context context, String title, String summary,
                                 IntegerSetting setting, String singularUnit, String pluralUnit) {
        super(context);
        if (!setting.hasRange()) {
            throw new IllegalArgumentException(setting.key + " has no range to offer");
        }
        this.singularUnit = singularUnit;
        this.pluralUnit = pluralUnit;
        this.baseSummary = summary;
        this.setting = setting;
        this.minValue = setting.minimum();
        this.maxValue = setting.maximum();
        setTitle(title);
        setKey(setting.key);
        setValue(String.valueOf(clamp(setting.get())));
        getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    }

    public String getValue() {
        return String.valueOf(parseAndClamp(getText()));
    }

    public void setValue(String value) {
        int clampedValue = parseAndClamp(value);
        String text = String.valueOf(clampedValue);
        setText(text);
        String shown = displayValue(clampedValue);
        String unit = L10n.t(getContext(), unitForValue(clampedValue));
        setSummary(L10n.t(getContext(), baseSummary) + "\n" + (unit.isEmpty()
                ? L10n.f(getContext(), "Current: %1$s", shown)
                : L10n.f(getContext(), "Current: %1$s %2$s", shown, unit)));
    }

    /**
     * How the number itself reads in the summary. Most rows are a count and read as one; an hour
     * of the day is not "13 o'clock".
     */
    protected String displayValue(int value) {
        return String.valueOf(value);
    }

    private String unitForValue(int value) {
        return value == 1 ? singularUnit : pluralUnit;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        Utils.setTitleAndSummaryColor(view);
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        LinearLayout dialogView = new LinearLayout(context);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        int padding = SettingsUi.dp(context, 22);
        dialogView.setPadding(padding, padding, padding, SettingsUi.dp(context, 8));

        TextView title = SettingsUi.text(
                context,
                getTitle() == null ? "" : getTitle().toString(),
                20,
                SettingsUi.textPrimary(),
                android.graphics.Typeface.BOLD
        );
        dialogView.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (getSummary() != null && getSummary().length() > 0) {
            TextView summary = SettingsUi.text(
                    context,
                    getSummary().toString(),
                    14,
                    SettingsUi.textSecondary(),
                    android.graphics.Typeface.NORMAL
            );
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            summaryParams.setMargins(0, SettingsUi.dp(context, 14), 0, SettingsUi.dp(context, 10));
            dialogView.addView(summary, summaryParams);
        }

        EditText editText = getEditText();
        ViewGroup parent = (ViewGroup) editText.getParent();
        if (parent != null) {
            parent.removeView(editText);
        }
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setSingleLine(true);
        editText.setSelectAllOnFocus(true);
        SettingsUi.styleEditText(editText);
        dialogView.addView(editText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        return dialogView;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setPositiveButton(L10n.t(getContext(), "Save"), (dialog, which)
                -> this.onClick(dialog, DialogInterface.BUTTON_POSITIVE));
        builder.setNegativeButton(android.R.string.cancel, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            int value = parseAndClamp(getEditText().getText().toString());
            String text = String.valueOf(value);
            if (callChangeListener(text)) {
                setValue(text);
            }
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
    }

    private int parseAndClamp(String value) {
        try {
            return clamp(Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            // An empty or unreadable box is not a request for the smallest value. For
            // several of these settings the smallest value means off, so falling to it
            // would quietly turn a feature off because somebody cleared the field.
            return clamp(setting.get());
        }
    }

    protected int clamp(int value) {
        return Math.max(minValue, Math.min(maxValue, value));
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
