package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.tiktok.navigation.BottomNavigationTabOptions;
import app.morphe.extension.tiktok.navigation.NavigationTabOptions;
import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("deprecation")
public class TabSelectionPreference extends Preference {
    private final StringSetting setting;
    private final boolean bottomTabs;
    private String value;
    private boolean valueSet;

    public TabSelectionPreference(Context context, StringSetting setting) {
        this(context, setting, false);
    }

    public TabSelectionPreference(Context context, StringSetting setting, boolean bottomTabs) {
        super(context);
        this.setting = setting;
        this.bottomTabs = bottomTabs;
        setTitle(bottomTabs ? "Allowed bottom tabs" : "Allowed loaded tabs");
        setKey(setting.key);
        setValue(setting.get());
    }

    public String getValue() {
        return value;
    }

    public boolean setValue(String value) {
        String sanitizedValue = serializeEnabledKeys(parseEnabledKeys(value));
        boolean changed = !TextUtils.equals(this.value, sanitizedValue);
        if (changed || !valueSet) {
            this.value = sanitizedValue;
            valueSet = true;
            setting.save(sanitizedValue);
            refreshSummary();
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
        return changed;
    }

    @Override
    protected void onClick() {
        showSelectionDialog();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    private static final String ROW_TAG = "tab_option_";

    /** The rows and the set they show, so "Reset to loaded" can tick them where they are. */
    private LinearLayout optionsView;
    private Set<String> selectedKeys;

    private void refreshSummary() {
        Set<String> selected = parseEnabledKeys(value);
        List<OptionRow> observedOptions = getObservedOptions();
        if (observedOptions.size() <= 1) {
            setSummary(bottomTabs ? "Open TikTok home to detect loaded bottom tabs." : "Open TikTok home feed to detect loaded tabs.");
            return;
        }

        int selectedObserved = 0;
        StringBuilder builder = new StringBuilder();
        for (OptionRow option : observedOptions) {
            if (!selected.contains(option.key)) {
                continue;
            }

            selectedObserved++;
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(option.label);
        }

        if (selectedObserved == observedOptions.size()) {
            setSummary("All loaded tabs");
            return;
        }

        if (builder.length() == 0) {
            setSummary(bottomTabs ? "Home" : "For You");
            return;
        }
        setSummary(builder.toString());
    }

    private void showSelectionDialog() {
        Context context = getContext();
        Set<String> selected = new LinkedHashSet<>(parseEnabledKeys(value));
        List<OptionRow> observedOptions = getObservedOptions();

        LinearLayout dialogView = new LinearLayout(context);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setBackground(createDialogBackground());
        int padding = dpToPx(22);
        dialogView.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(context);
        title.setText(bottomTabs ? "Allowed bottom tabs" : "Allowed loaded tabs");
        title.setTextColor(getTitleTextColor());
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        dialogView.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView helper = new TextView(context);
        helper.setText(bottomTabs
                ? "Only bottom tabs TikTok has loaded on this device are shown here. This does not force unavailable tabs to appear."
                : "Only tabs TikTok has loaded on this device are shown here. This does not force unavailable tabs to appear.");
        helper.setTextColor(getSummaryTextColor());
        LinearLayout.LayoutParams helperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        helperParams.setMargins(0, dpToPx(16), 0, dpToPx(12));
        dialogView.addView(helper, helperParams);

        LinearLayout optionsContainer = new LinearLayout(context);
        optionsContainer.setOrientation(LinearLayout.VERTICAL);
        optionsContainer.setBackground(createListBackground());
        int optionInset = Math.max(1, dpToPx(1));
        optionsContainer.setPadding(optionInset, optionInset, optionInset, optionInset);

        for (OptionRow option : observedOptions) {
            optionsContainer.addView(createOptionRow(context, selected, option));
        }
        optionsView = optionsContainer;
        selectedKeys = selected;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(optionsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsUi.dialogListHeight(context, 380)
        );
        scrollParams.setMargins(0, 0, 0, dpToPx(16));
        dialogView.addView(scrollView, scrollParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView showAllButton = createActionButton(context, "Reset to loaded", false);
        TextView cancelButton = createActionButton(context, "Cancel", false);
        TextView saveButton = createActionButton(context, "Save", true);

        actions.addView(showAllButton, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));
        actions.addView(cancelButton);
        actions.addView(saveButton);
        dialogView.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .create();

        // Ticks every row and leaves the dialog open, so it is a way of filling the list in
        // rather than a second Save. It saved and dismissed, which meant a reader who pressed it
        // to see what it did lost the selection they had come in with, with no undo.
        showAllButton.setOnClickListener(view -> {
            selected.clear();
            for (OptionRow option : observedOptions) {
                selected.add(option.key);
            }
            refreshRowChecks();
        });
        cancelButton.setOnClickListener(view -> dialog.dismiss());
        saveButton.setOnClickListener(view -> {
            boolean changed = setValue(serializeEnabledKeys(selected));
            dialog.dismiss();
            if (changed && setting.rebootApp) {
                AbstractPreferenceFragment.showRestartDialog(context);
            }
        });

        dialog.show();
        SettingsUi.styleDialog(dialog);
    }

    private List<OptionRow> getObservedOptions() {
        LinkedHashSet<OptionRow> rows = new LinkedHashSet<>();
        if (bottomTabs) {
            Set<String> observed = BottomNavigationTabOptions.parseObservedKeys(Settings.BOTTOM_NAVIGATION_OBSERVED_TABS.get());
            observed.add(BottomNavigationTabOptions.HOME);
            observed.add(BottomNavigationTabOptions.PROFILE);
            for (BottomNavigationTabOptions.Option option : BottomNavigationTabOptions.optionsForKeys(observed)) {
                rows.add(new OptionRow(option.key, option.label));
            }
        } else {
            Set<String> observed = NavigationTabOptions.parseObservedKeys(Settings.FEED_NAVIGATION_OBSERVED_TABS.get());
            observed.add(NavigationTabOptions.HOT);
            for (NavigationTabOptions.Option option : NavigationTabOptions.optionsForKeys(observed)) {
                rows.add(new OptionRow(option.key, option.label));
            }
        }
        return new java.util.ArrayList<>(rows);
    }

    /** Puts every row's tick in step with the selection, without rebuilding the dialog. */
    private void refreshRowChecks() {
        if (optionsView == null || selectedKeys == null) return;
        // Walked rather than indexed: each row is a wrapper holding the row itself and the
        // divider under it, so the box is a grandchild.
        refreshRowChecks(optionsView);
    }

    private void refreshRowChecks(View view) {
        Object tag = view.getTag();
        if (view instanceof CheckBox && tag instanceof String
                && ((String) tag).startsWith(ROW_TAG)) {
            ((CheckBox) view).setChecked(
                    selectedKeys.contains(((String) tag).substring(ROW_TAG.length())));
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            refreshRowChecks(group.getChildAt(index));
        }
    }

    private View createOptionRow(Context context, Set<String> selected, OptionRow option) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(getDialogBackgroundColor());
        row.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        CheckBox checkBox = new CheckBox(context);
        // Tagged so "Reset to loaded" can tick them where they are, rather than closing the
        // dialog and saving to show what it did.
        checkBox.setTag(ROW_TAG + option.key);
        checkBox.setChecked(selected.contains(option.key));
        checkBox.setEnabled(!isRequiredOption(option.key));
        checkBox.setClickable(false);
        SettingsUi.styleCheckBox(checkBox);
        row.addView(checkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(context);
        label.setText(option.label);
        label.setTextColor(getTitleTextColor());
        label.setTextSize(16);
        textContainer.addView(label);

        if (isRequiredOption(option.key)) {
            TextView summary = new TextView(context);
            summary.setText("Required");
            summary.setTextColor(getSummaryTextColor());
            summary.setTextSize(13);
            textContainer.addView(summary);
        }

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        row.addView(textContainer, textParams);

        // The box is not clickable and the row does the toggling, so without this a screen
        // reader read the tab name and "double tap to activate" with no on or off in it, and
        // said nothing at all after the tap.
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(CheckBox.class.getName());
                info.setCheckable(!isRequiredOption(option.key));
                info.setChecked(checkBox.isChecked());
                info.setEnabled(!isRequiredOption(option.key));
            }
        });

        row.setOnClickListener(view -> {
            if (isRequiredOption(option.key)) {
                return;
            }

            if (selected.contains(option.key)) {
                selected.remove(option.key);
                checkBox.setChecked(false);
            } else {
                selected.add(option.key);
                checkBox.setChecked(true);
            }
            // What was just done, rather than leaving the reader to go back and check.
            row.sendAccessibilityEvent(
                    android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED);
        });

        View divider = new View(context);
        divider.setBackgroundColor(getDialogDividerColor());

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        wrapper.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))
        ));
        return wrapper;
    }

    private TextView createActionButton(Context context, String text, boolean primary) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(6));
        SettingsUi.styleTextAction(button, primary);
        return button;
    }

    private Set<String> parseEnabledKeys(String value) {
        return bottomTabs
                ? BottomNavigationTabOptions.parseEnabledKeys(value)
                : NavigationTabOptions.parseEnabledKeys(value);
    }

    private String serializeEnabledKeys(Set<String> selected) {
        return bottomTabs
                ? BottomNavigationTabOptions.serializeEnabledKeys(selected)
                : NavigationTabOptions.serializeEnabledKeys(selected);
    }

    private boolean isRequiredOption(String key) {
        return bottomTabs
                ? BottomNavigationTabOptions.isRequiredKey(key)
                : NavigationTabOptions.HOT.equals(key);
    }

    private static final class OptionRow {
        final String key;
        final String label;

        OptionRow(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getContext().getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createDialogBackground() {
        return SettingsUi.borderedSurface(getContext(), 6, true);
    }

    private GradientDrawable createListBackground() {
        return SettingsUi.borderedSurface(getContext(), 4, false);
    }

    private static int getDialogBackgroundColor() {
        return SettingsUi.surface();
    }

    private static int getDialogDividerColor() {
        return SettingsUi.divider();
    }

    private static int getTitleTextColor() {
        return SettingsUi.textPrimary();
    }

    private static int getSummaryTextColor() {
        return SettingsUi.textSecondary();
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
