/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/RangeValuePreference.java
 */

package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.tiktok.Utils;

@SuppressWarnings("deprecation")
public class RangeValuePreference extends DialogPreference {
    private String minValue;

    private String maxValue;

    private String mValue;

    private boolean mValueSet;

    public RangeValuePreference(Context context, String title, String summary, StringSetting setting) {
        super(context);
        setTitle(title);
        setSummary(summary);
        setKey(setting.key);
        setValue(setting.get());
    }

    public void setValue(String value) {
        final boolean changed = !TextUtils.equals(mValue, value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistString(value);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public String getValue() {
        return mValue;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected View onCreateDialogView() {
        String[] values = getValue().split("-");
        minValue = values.length > 0 ? values[0] : "0";
        maxValue = values.length > 1 ? values[1] : Long.toString(Long.MAX_VALUE);

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
                Typeface.BOLD
        );
        dialogView.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView helper = SettingsUi.text(
                context,
                L10n.t(context, "Leave maximum empty to keep it unlimited."),
                14,
                SettingsUi.textSecondary(),
                Typeface.NORMAL
        );
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        helperParams.setMargins(0, SettingsUi.dp(context, 14), 0, SettingsUi.dp(context, 12));
        dialogView.addView(helper, helperParams);

        TextView min = SettingsUi.text(context, L10n.t(context, "Minimum"), 13, SettingsUi.textSecondary(), Typeface.BOLD);
        dialogView.addView(min);

        EditText minEditText = new EditText(context);
        minEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        minEditText.setSingleLine(true);
        minEditText.setText(minValue);
        SettingsUi.styleEditText(minEditText);
        dialogView.addView(minEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView max = SettingsUi.text(context, L10n.t(context, "Maximum"), 13, SettingsUi.textSecondary(), Typeface.BOLD);
        LinearLayout.LayoutParams maxLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        maxLabelParams.setMargins(0, SettingsUi.dp(context, 12), 0, 0);
        dialogView.addView(max, maxLabelParams);

        EditText maxEditText = new EditText(context);
        maxEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxEditText.setSingleLine(true);
        maxEditText.setHint(L10n.t(context, "Unlimited"));
        maxEditText.setText(Long.toString(Long.MAX_VALUE).equals(maxValue) ? "" : maxValue);
        SettingsUi.styleEditText(maxEditText);
        dialogView.addView(maxEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        minEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                minValue = editable.toString();
            }
        });
        maxEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                maxValue = editable.toString();
            }
        });

        return dialogView;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        Utils.setTitleAndSummaryColor(view);
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
            String newValue = normalizeRangeValue(minValue, maxValue);
            setValue(newValue);
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
    }

    private static String normalizeRangeValue(String min, String max) {
        String normalizedMin = min == null || min.length() == 0 ? "0" : min;
        String normalizedMax = max == null || max.length() == 0 ? Long.toString(Long.MAX_VALUE) : max;
        return normalizedMin + "-" + normalizedMax;
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
