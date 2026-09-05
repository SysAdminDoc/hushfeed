package app.morphe.extension.tiktok.settings.preference;

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
    private final int minValue;
    private final int maxValue;
    private final String unit;

    public NumberInputPreference(Context context, String title, String summary, IntegerSetting setting,
                                 int minValue, int maxValue) {
        this(context, title, summary, setting, minValue, maxValue, "videos");
    }

    public NumberInputPreference(Context context, String title, String summary, IntegerSetting setting,
                                 int minValue, int maxValue, String unit) {
        super(context);
        this.unit = unit;
        this.baseSummary = summary;
        this.minValue = minValue;
        this.maxValue = maxValue;
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
        setSummary(baseSummary + "\nCurrent: " + text + " " + unit);
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
        builder.setPositiveButton("Save", (dialog, which)
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
            return minValue;
        }
    }

    private int clamp(int value) {
        return Math.max(minValue, Math.min(maxValue, value));
    }
}
