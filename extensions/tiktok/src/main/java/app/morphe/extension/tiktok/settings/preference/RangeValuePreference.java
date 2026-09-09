/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/RangeValuePreference.java
 */

package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.feedfilter.CompactCount;
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
    /** The two boxes the dialog is showing, so a refusal can be said under the right one. */
    private EditText minField;
    private EditText maxField;
    private EditText offending;

    private String mValue;

    private boolean mValueSet;

    /** The wording the row was given, kept so the current range can be added after it. */
    private final String baseSummary;

    public RangeValuePreference(Context context, String title, String summary, StringSetting setting) {
        super(context);
        setTitle(title);
        baseSummary = summary;
        setKey(setting.key);
        setValue(setting.get());
        describeRange();
    }

    /**
     * Adds the range to the row's own wording, written the way it is typed. Reading "1.5M" back
     * off the row is the point: a row that says 1500000 is a row nobody can check at a glance.
     *
     * <p>The two halves are translated separately and joined here, so the joined text is never
     * looked up as though it were a phrase of its own.
     */
    private void describeRange() {
        String[] values = getValue() == null ? new String[0] : getValue().split("-");
        long min = parseStored(values, 0, 0L);
        long max = parseStored(values, 1, Long.MAX_VALUE);
        String range = max == Long.MAX_VALUE
                ? L10n.f(getContext(), "%1$s and above", CompactCount.format(min))
                : L10n.f(getContext(), "%1$s to %2$s",
                        CompactCount.format(min), CompactCount.format(max));
        super.setSummary(L10n.t(getContext(), baseSummary) + "\n" + range);
    }

    public void setValue(String value) {
        final boolean changed = !TextUtils.equals(mValue, value);
        if (changed || !mValueSet) {
            mValue = value;
            mValueSet = true;
            persistString(value);
            if (changed) {
                // The range line is the stored value written back out, so it has to be rebuilt
                // wherever the value moves. A restore, a reset and an undo all arrive here and
                // used to leave the row reading the range they replaced.
                describeRange();
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
        // Shown the way the feed writes a count, so 1500000 comes back as 1.5M.
        minValue = CompactCount.format(parseStored(values, 0, 0L));
        maxValue = CompactCount.format(parseStored(values, 1, Long.MAX_VALUE));

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
        // Not TYPE_CLASS_NUMBER: a number keyboard has no letter on it, and a field that
        // accepts 1.5M has to be typeable.
        minEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        minEditText.setSingleLine(true);
        // The hint is the label, which is the rule the rest of this package follows. Without one
        // TalkBack read these two as "edit box" and "edit box, Unlimited" in all five of these
        // dialogs: the visible headings above them are separate views and say nothing about the
        // field. "Unlimited" was a value rather than a label anyway, and it disappeared as soon
        // as anything was typed; the helper sentence above already says an empty maximum means
        // no upper bound.
        minEditText.setHint(L10n.t(context, "Minimum"));
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
        maxEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        maxEditText.setSingleLine(true);
        maxEditText.setHint(L10n.t(context, "Maximum"));
        maxEditText.setText(Long.toString(Long.MAX_VALUE).equals(maxValue) ? "" : maxValue);
        SettingsUi.styleEditText(maxEditText);
        dialogView.addView(maxEditText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        minField = minEditText;
        maxField = maxEditText;

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
        if (!positiveResult) return;
        saveTypedRange();
    }

    private void saveTypedRange() {
        long min = readField(minValue, 0L);
        long max = readField(maxValue, Long.MAX_VALUE);
        if (min < 0 || max < 0) {
            app.morphe.extension.shared.Utils.showToastShort(L10n.t(getContext(),
                    "Enter a whole number, or one like 20K, 1.5M or 2B"));
            return;
        }
        if (min > max) {
            app.morphe.extension.shared.Utils.showToastShort(
                    L10n.t(getContext(), "The smallest value is above the largest"));
            return;
        }
        // Only digits are stored. Everything downstream reads a plain number and always has.
        setValue(min + "-" + max);
    }

    /** An empty field means "no bound at this end" rather than a value that cannot be read. */
    private static long readField(String text, long empty) {
        return text == null || text.trim().isEmpty() ? empty : CompactCount.parse(text);
    }

    private static long parseStored(String[] values, int index, long fallback) {
        if (values.length <= index) return fallback;
        try {
            long stored = Long.parseLong(values[index].trim());
            return stored < 0 ? fallback : stored;
        } catch (NumberFormatException unreadable) {
            return fallback;
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
        // Same as the text rows: a rejected pair used to close the dialog and then say what was
        // wrong, so both numbers had to be typed again.
        SettingsUi.keepOpenOnInvalidInput(getDialog(), new SettingsUi.DialogCheck() {
            @Override public String problem() {
                long min = readField(minValue, 0L);
                long max = readField(maxValue, Long.MAX_VALUE);
                if (min < 0 || max < 0) {
                    offending = min < 0 ? minField : maxField;
                    return L10n.t(getContext(),
                            "Enter a whole number, or one like 20K, 1.5M or 2B");
                }
                if (min > max) {
                    // The pair is wrong, not either number on its own. It is said at the top
                    // of the pair, where the reader starts reading.
                    offending = minField;
                    return L10n.t(getContext(), "The smallest value is above the largest");
                }
                return null;
            }

            @Override public void report(String problem) {
                if (offending != null) offending.setError(problem);
            }

            @Override public boolean accept() {
                // Not onDialogClosed: dismissing runs that a second time, for the button the
                // platform still thinks was pressed, and only the early return on false kept
                // that from saving twice.
                saveTypedRange();
                return true;
            }
        });
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
