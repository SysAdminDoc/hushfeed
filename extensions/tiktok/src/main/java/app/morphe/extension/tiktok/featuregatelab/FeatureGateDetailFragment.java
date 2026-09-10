/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.featuregatelab;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

@SuppressWarnings({"deprecation", "SetTextI18n"})
public final class FeatureGateDetailFragment extends Fragment {
    private static final String ARG_MANAGER = "manager";
    private static final String ARG_KEY = "key";
    private static final String ARG_TYPE = "type";

    private final app.morphe.extension.tiktok.settings.SystemBackHandler systemBack =
            new app.morphe.extension.tiktok.settings.SystemBackHandler("FeatureGateDetailBackCallback");

    private FeatureGateCatalog.Entry entry;
    private FeatureGateLabStore.Rule rule;
    private TextView status;
    private TextView effectiveValue;
    private Spinner values;
    private Switch force;
    private Switch booleanValue;
    private TextView reset;
    private TextView saveObject;
    private LinearLayout technicalDetails;
    private TextView technicalToggle;
    private List<ValueOption> options;
    private final List<ObjectFieldEditor> objectEditors = new ArrayList<>();
    /** Held so the window can be taken down with the view that opened it. */
    private AlertDialog customValueDialog;
    private boolean suppress;
    private int lastConcreteSelection;

    public static FeatureGateDetailFragment forEntry(String manager, String key, String type) {
        FeatureGateDetailFragment fragment = new FeatureGateDetailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MANAGER, manager);
        arguments.putString(ARG_KEY, key);
        arguments.putString(ARG_TYPE, type);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public View onCreateView(android.view.LayoutInflater inflater, ViewGroup container, Bundle state) {
        Context context = getActivity();
        FeatureGateLabUi.syncTheme(context);
        FeatureGateCatalog.Snapshot snapshot = FeatureGateCatalog.cachedSnapshot();
        String manager = getArguments().getString(ARG_MANAGER, "");
        String key = getArguments().getString(ARG_KEY, "");
        entry = snapshot == null ? null : snapshot.byIdentity.get(manager + "\n" + key);

        FeatureGateLabUi.BackInterceptLayout screen =
                new FeatureGateLabUi.BackInterceptLayout(context, this::leaveDetail);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(SettingsUi.background());
        screen.requestFocus();
        screen.addView(
                FeatureGateLabUi.header(context, L10n.t(context, "Gate details"), this::leaveDetail),
                FeatureGateLabUi.matchWrap()
        );

        if (entry == null) {
            TextView error = FeatureGateLabUi.body(context, L10n.t(context, "This catalog entry is no longer available. Refresh the Lab and try again."));
            error.setGravity(Gravity.CENTER);
            error.setPadding(
                    FeatureGateLabUi.dp(context, 24),
                    FeatureGateLabUi.dp(context, 24),
                    FeatureGateLabUi.dp(context, 24),
                    FeatureGateLabUi.dp(context, 24)
            );
            screen.addView(error, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return screen;
        }
        rule = FeatureGateLabStore.rule(entry.manager, entry.key, entry.type);

        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                FeatureGateLabUi.dp(context, 16),
                FeatureGateLabUi.dp(context, 8),
                FeatureGateLabUi.dp(context, 16),
                FeatureGateLabUi.dp(context, 24)
        );
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = FeatureGateLabUi.heading(context, entry.title);
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(title, FeatureGateLabUi.matchWrap());

        TextView rawKey = FeatureGateLabUi.label(context, entry.key);
        rawKey.setTextIsSelectable(true);
        LinearLayout.LayoutParams keyParams = FeatureGateLabUi.matchWrap();
        keyParams.setMargins(0, FeatureGateLabUi.dp(context, 3), 0, 0);
        content.addView(rawKey, keyParams);

        if (!entry.description.isEmpty()) {
            TextView description = FeatureGateLabUi.body(context, entry.description);
            LinearLayout.LayoutParams params = FeatureGateLabUi.matchWrap();
            params.setMargins(0, FeatureGateLabUi.dp(context, 12), 0, 0);
            content.addView(description, params);
        }

        addSectionTitle(content, L10n.t(context, "Current state"));
        LinearLayout currentState = new LinearLayout(context);
        currentState.setOrientation(LinearLayout.VERTICAL);
        currentState.setPadding(FeatureGateLabUi.dp(context, 16), 0, FeatureGateLabUi.dp(context, 16), 0);
        currentState.setBackground(SettingsUi.borderedSurface(context, 10, false));
        content.addView(currentState, FeatureGateLabUi.matchWrap());
        addInfo(currentState, L10n.t(context, "Loaded for this account"),
                L10n.t(context, entry.loaded ? "Yes" : "No"));
        addInfo(currentState, L10n.t(context, "TikTok cached value"), "OBJECT".equals(entry.type)
                ? L10n.t(context, entry.loaded
                        ? "Structured value observed" : "Not requested in this process yet")
                : (entry.loaded
                        ? entry.currentValue + " (" + entry.currentType + ")"
                        : L10n.t(context, "Not present in the current cache")));
        effectiveValue = addInfo(currentState, L10n.t(context, "Effective getter result"), effectiveValueText());
        TextView cacheNote = FeatureGateLabUi.label(
                context,
                L10n.t(context, "An override changes the value returned by the getter. It does not rewrite TikTok's cached value or prove the named feature changed.")
        );
        LinearLayout.LayoutParams cacheNoteParams = FeatureGateLabUi.matchWrap();
        cacheNoteParams.setMargins(0, FeatureGateLabUi.dp(context, 8), 0, 0);
        content.addView(cacheNote, cacheNoteParams);

        if (isSensitiveKey(entry.key)) {
            TextView sensitive = FeatureGateLabUi.label(
                    context,
                    L10n.t(context, "Account-sensitive name: forcing this key may affect security, login, compliance, region, payment, or account safety behavior.")
            );
            sensitive.setTextColor(FeatureGateLabUi.warningColor(context));
            LinearLayout.LayoutParams params = FeatureGateLabUi.matchWrap();
            params.setMargins(0, FeatureGateLabUi.dp(context, 12), 0, 0);
            content.addView(sensitive, params);
        }

        addSectionTitle(content, L10n.t(context, "Override"));
        status = FeatureGateLabUi.text(context, "", 13, SettingsUi.textSecondary(), Typeface.BOLD);
        status.setPadding(0, 0, 0, FeatureGateLabUi.dp(context, 12));
        content.addView(status, FeatureGateLabUi.matchWrap());

        boolean editable = FeatureGateLabStore.masterEnabled();
        boolean booleanEntry = "BOOLEAN".equals(entry.type);
        boolean objectEntry = "OBJECT".equals(entry.type);
        if (objectEntry) {
            LinearLayout forceRow = settingRow(
                    context,
                    L10n.t(context, "Override this configuration"),
                    L10n.t(context, "Return a copied object with the selected fields changed")
            );
            force = new Switch(context);
            force.setContentDescription(L10n.t(context, "Override this configuration"));
            forceRow.addView(force, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FeatureGateLabUi.dp(context, 48)
            ));
            content.addView(forceRow, FeatureGateLabUi.matchWrap());
            addObjectEditors(content, editable);
            saveObject = FeatureGateLabUi.text(
                    context,
                    L10n.t(context, "Save field values"),
                    14,
                    SettingsUi.accent(),
                    Typeface.BOLD
            );
            saveObject.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            saveObject.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
            saveObject.setPadding(0, FeatureGateLabUi.dp(context, 10), 0, FeatureGateLabUi.dp(context, 10));
            saveObject.setEnabled(editable);
            content.addView(saveObject, FeatureGateLabUi.matchWrap());
        } else if (booleanEntry) {
            LinearLayout valueRow = settingRow(
                    context,
                    L10n.t(context, "Forced result"),
                    L10n.t(context, "Off forces false; on forces true. Reset returns control to TikTok")
            );
            booleanValue = new Switch(context);
            booleanValue.setContentDescription(L10n.t(context, "Forced result"));
            booleanValue.setChecked(Boolean.parseBoolean(rule == null ? bestInitialValue(entry) : rule.value));
            booleanValue.setEnabled(editable);
            valueRow.addView(booleanValue, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FeatureGateLabUi.dp(context, 48)
            ));
            content.addView(valueRow, FeatureGateLabUi.matchWrap());
        } else {
            LinearLayout forceRow = settingRow(
                    context,
                    L10n.t(context, "Override this gate"),
                    L10n.t(context, "When TikTok requests this key, return the selected value below")
            );
            force = new Switch(context);
            force.setContentDescription(L10n.t(context, "Override this gate"));
            forceRow.addView(force, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FeatureGateLabUi.dp(context, 48)
            ));
            content.addView(forceRow, FeatureGateLabUi.matchWrap());

            forceRow.setBackground(SettingsUi.groupedRow(context, true, false));
            LinearLayout valueRow = new LinearLayout(context);
            valueRow.setOrientation(LinearLayout.VERTICAL);
            valueRow.setPadding(FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 14),
                    FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 8));
            valueRow.setBackground(SettingsUi.groupedRow(context, false, true));
            TextView valueLabel = FeatureGateLabUi.body(context,
                    L10n.t(context, "Value to return"));
            valueRow.addView(valueLabel, FeatureGateLabUi.matchWrap());
            options = buildOptions(entry, rule);
            values = new Spinner(context);
            applyOptionsAdapter();
            values.setEnabled(editable);
            values.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            values.setContentDescription(L10n.t(context, "Value to return"));
            values.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
            LinearLayout.LayoutParams valueParams = FeatureGateLabUi.matchWrap();
            valueParams.setMargins(0, FeatureGateLabUi.dp(context, 4), 0, FeatureGateLabUi.dp(context, 4));
            valueRow.addView(values, valueParams);
            content.addView(valueRow, FeatureGateLabUi.matchWrap());
        }

        reset = FeatureGateLabUi.text(context, L10n.t(context, "Reset override"), 14, SettingsUi.accent(), Typeface.BOLD);
        reset.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        reset.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
        reset.setPadding(0, FeatureGateLabUi.dp(context, 10), 0, FeatureGateLabUi.dp(context, 10));
        reset.setVisibility(rule == null ? View.GONE : View.VISIBLE);
        content.addView(reset, FeatureGateLabUi.matchWrap());

        if (force != null) force.setEnabled(editable);
        if (!editable) {
            TextView disabled = FeatureGateLabUi.label(context, L10n.t(context, "Overrides are disabled in the Feature Gate Lab."));
            content.addView(disabled, FeatureGateLabUi.matchWrap());
        }

        suppress = true;
        if (!booleanEntry && !objectEntry) {
            int selected = selectedIndex(options, rule == null ? bestInitialValue(entry) : rule.value);
            values.setSelection(selected);
            lastConcreteSelection = selected;
        }
        if (force != null) force.setChecked(rule != null && rule.enabled);
        suppress = false;
        updateStatus();

        if (values != null) {
            values.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppress || !FeatureGateLabStore.masterEnabled()) return;
                    ValueOption option = options.get(position);
                    if (option.custom) {
                        showCustomValue();
                        return;
                    }
                    lastConcreteSelection = position;
                    String storedValue = rule == null ? null : rule.value;
                    if ((storedValue == null && option.value.equals(bestInitialValue(entry)))
                            || option.value.equals(storedValue)) return;
                    persist(option.value, force.isChecked());
                }

                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
        }

        if (booleanValue != null) {
            booleanValue.setOnCheckedChangeListener((button, checked) -> {
                if (suppress || !FeatureGateLabStore.masterEnabled()) return;
                persist(String.valueOf(checked), true);
            });
        }

        if (force != null && !objectEntry) {
            force.setOnCheckedChangeListener((button, enabled) -> {
                if (suppress || !FeatureGateLabStore.masterEnabled()) return;
                ValueOption selectedOption = options.get(values.getSelectedItemPosition());
                if (selectedOption.custom) {
                    suppress = true;
                    force.setChecked(false);
                    suppress = false;
                    showCustomValue();
                    return;
                }
                persist(selectedOption.value, enabled);
            });
        }

        if (force != null && objectEntry) {
            force.setOnCheckedChangeListener((button, enabled) -> {
                if (suppress || !FeatureGateLabStore.masterEnabled()) return;
                String value = rule == null ? objectPatchText() : rule.value;
                persist(value, enabled);
            });
        }

        if (saveObject != null) {
            saveObject.setOnClickListener(view -> persist(objectPatchText(), force.isChecked()));
        }

        reset.setOnClickListener(view -> resetRule());
        addTechnicalDetails(content);
        SettingsUi.styleSwitches(screen);
        return screen;
    }

    @Override
    public void onResume() {
        super.onResume();
        systemBack.register(getActivity(), this::leaveDetail);
        updateStatus();
    }

    @Override
    public void onPause() {
        systemBack.unregister();
        super.onPause();
    }

    /**
     * Lets go of the view this screen was built from.
     *
     * <p>The screen sits on a back stack, so its view is built again on a rotation, a font scale
     * change, a theme change, or a return from anything deeper. Without this the editors from
     * every earlier view stayed in the list, so the text collected on save came from fields
     * nobody could see, and each of them pinned a destroyed hierarchy. A dialog left open when
     * the activity goes is a leaked window whose buttons reach for those same dead views.
     */
    @Override
    public void onDestroyView() {
        if (customValueDialog != null) {
            if (customValueDialog.isShowing()) customValueDialog.dismiss();
            customValueDialog = null;
        }
        objectEditors.clear();
        status = null;
        effectiveValue = null;
        values = null;
        force = null;
        booleanValue = null;
        reset = null;
        saveObject = null;
        technicalDetails = null;
        technicalToggle = null;
        super.onDestroyView();
    }

    /** How many object field editors the current view is holding, so a test can see them stack. */
    int objectEditorCountForTests() {
        return objectEditors.size();
    }

    boolean customValueDialogShowingForTests() {
        return customValueDialog != null && customValueDialog.isShowing();
    }

    private void leaveDetail() {
        if (getFragmentManager() != null) getFragmentManager().popBackStack();
    }

    private LinearLayout settingRow(Context context, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 16),
                FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 16));
        row.setBackground(SettingsUi.borderedSurface(context, 10, false));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(FeatureGateLabUi.body(context, title), FeatureGateLabUi.matchWrap());
        labels.addView(FeatureGateLabUi.label(context, subtitle), FeatureGateLabUi.matchWrap());
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void persist(String value, boolean enabled) {
        String error = FeatureGateLabStore.validateValue(entry.type, value);
        if (error != null) {
            Utils.showToastLong(error);
            return;
        }
        // Saving takes the journal lock and two write-and-verify cycles, the same as the Lab
        // screen's own changes, which have run off the main thread since they were written.
        Object[] saved = new Object[1];
        runDetailChange(
                () -> {
                    FeatureGateLabUndo.saveRule(entry.manager, entry.key, entry.type, value, enabled);
                    saved[0] = FeatureGateLabStore.rule(entry.manager, entry.key, entry.type);
                },
                () -> {
                    rule = (FeatureGateLabStore.Rule) saved[0];
                    reset.setVisibility(View.VISIBLE);
                    updateStatus();
                },
                L10n.t(Utils.getContext(), "Could not save this override."));
    }

    private void resetRule() {
        runDetailChange(
                () -> FeatureGateLabUndo.deleteRule(entry.manager, entry.key, entry.type),
                () -> {
                    rule = null;
                    suppress = true;
                    if (force != null) force.setChecked(false);
                    if (booleanValue != null) {
                        booleanValue.setChecked(Boolean.parseBoolean(bestInitialValue(entry)));
                    } else if (values != null) {
                        values.setSelection(selectedIndex(options, bestInitialValue(entry)));
                    }
                    suppress = false;
                    reset.setVisibility(View.GONE);
                    updateStatus();
                    Utils.showToastShort(L10n.t(Utils.getContext(), "Feature gate override reset"));
                },
                L10n.t(Utils.getContext(), "Could not reset this override."));
    }

    /** A change that touches storage, so it does not belong on the thread drawing the screen. */
    private interface DetailChange {
        void run() throws Exception;
    }

    /**
     * Runs {@code change} off the main thread, then {@code onDone} back on it.
     *
     * <p>{@code onDone} touches the views, so it is skipped when the screen has gone in the
     * meantime. A failure is reported either way: the user pressed a button and is owed an
     * answer even if they have already left.
     */
    private void runDetailChange(DetailChange change, Runnable onDone,
                                 String translatedFailurePrefix) {
        Utils.runOnBackgroundThread(() -> {
            String failure = null;
            try {
                change.run();
            } catch (Exception error) {
                failure = translatedFailurePrefix + " " + error.getMessage();
            }
            String notice = failure;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (notice != null) {
                    Utils.showToastLong(notice);
                    return;
                }
                if (getActivity() == null || reset == null) return;
                onDone.run();
            });
        });
    }

    private void showCustomValue() {
        EditText input = new EditText(getActivity());
        input.setSingleLine(!"STRING".equals(entry.type));
        // One sentence with the type in it, rather than three pieces glued together: no
        // table row can express a concatenation, and word order is not the same everywhere.
        input.setHint(L10n.f(getContext(), "Custom %1$s value (unverified)",
                entry.type.toLowerCase(Locale.ROOT)));
        input.setText(rule == null ? "" : rule.value);
        SettingsUi.styleEditText(input);
        AlertDialog dialog = customValueDialog = new AlertDialog.Builder(getActivity())
                .setTitle(L10n.t(getContext(), "Custom value (unverified)"))
                .setView(input)
                .setPositiveButton(L10n.t(getContext(), "Use value"), null)
                .setNegativeButton(L10n.t(getContext(), "Cancel"),
                        (ignored, which) -> restoreSelection())
                .setOnCancelListener(ignored -> restoreSelection())
                .create();
        dialog.setOnShowListener(ignored -> {
            FeatureGateLabUi.styleDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = input.getText().toString();
                String error = FeatureGateLabStore.validateValue(entry.type, value);
                if (error != null) {
                    input.setError(error);
                    return;
                }
                addOrSelectCustomValue(value);
                persist(value, force.isChecked());
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void addOrSelectCustomValue(String value) {
        int index = selectedIndex(options, value);
        if (index >= options.size() || options.get(index).custom || !value.equals(options.get(index).value)) {
            int customIndex = options.size() - 1;
            options.add(customIndex, new ValueOption(value, value + " (Custom, unverified)", false));
            applyOptionsAdapter();
            index = customIndex;
        }
        suppress = true;
        values.setSelection(index);
        lastConcreteSelection = index;
        suppress = false;
    }

    private void applyOptionsAdapter() {
        List<String> labels = new ArrayList<>();
        for (ValueOption option : options) labels.add(option.label);
        ArrayAdapter<String> adapter = new ValueAdapter(getActivity(), labels);
        values.setAdapter(adapter);
    }

    private static final class ValueAdapter extends ArrayAdapter<String> {
        ValueAdapter(Context context, List<String> labels) {
            super(context, android.R.layout.simple_spinner_item, labels);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return valueView(position, false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return valueView(position, true);
        }

        private TextView valueView(int position, boolean dropdown) {
            Context context = getContext();
            TextView view = FeatureGateLabUi.body(context, getItem(position));
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setSingleLine(false);
            view.setEllipsize(null);
            view.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
            view.setTextColor(SettingsUi.accent());
            if (!dropdown) {
                android.graphics.drawable.Drawable arrow =
                        new app.morphe.extension.tiktok.settings.preference.SettingsMenuPreference
                                .ChevronDrawable(context);
                arrow.setBounds(0, 0, FeatureGateLabUi.dp(context, 18), FeatureGateLabUi.dp(context, 18));
                view.setCompoundDrawablesRelative(null, null, arrow, null);
            }
            view.setPadding(
                    FeatureGateLabUi.dp(context, 12),
                    dropdown ? FeatureGateLabUi.dp(context, 10) : 0,
                    FeatureGateLabUi.dp(context, 12),
                    dropdown ? FeatureGateLabUi.dp(context, 10) : 0
            );
            view.setBackgroundColor(dropdown ? SettingsUi.surface() : android.graphics.Color.TRANSPARENT);
            return view;
        }
    }

    private void restoreSelection() {
        suppress = true;
        values.setSelection(lastConcreteSelection);
        suppress = false;
    }

    private void updateStatus() {
        if (status == null || entry == null) return;
        rule = FeatureGateLabStore.rule(entry.manager, entry.key, entry.type);
        if (rule == null) {
            status.setText(L10n.t(getContext(), "Using TikTok's value"));
            status.setTextColor(SettingsUi.textSecondary());
            if (effectiveValue != null) effectiveValue.setText(effectiveValueText());
            return;
        }
        if (!rule.enabled) {
            status.setText(L10n.t(getContext(), "Override saved but off"));
            status.setTextColor(SettingsUi.textSecondary());
            if (effectiveValue != null) effectiveValue.setText(effectiveValueText());
            return;
        }
        boolean triggered = FeatureGateLabRuntime.isTriggered(entry.manager, entry.key, entry.type);
        String failure = FeatureGateLabRuntime.structuredFailure(entry.manager, entry.key, entry.type);
        status.setText(failure != null
                ? L10n.f(getContext(), "Getter requested, override rejected: %1$s", failure)
                : L10n.t(getContext(), triggered
                        ? "Getter requested" : "Getter not requested yet"));
        status.setTextColor(triggered ? SettingsUi.accent() : FeatureGateLabUi.warningColor(getActivity()));
        if (effectiveValue != null) effectiveValue.setText(effectiveValueText());
    }

    private String effectiveValueText() {
        if (entry == null) return "Unavailable";
        FeatureGateLabStore.Rule currentRule = FeatureGateLabStore.rule(entry.manager, entry.key, entry.type);
        if (FeatureGateLabStore.masterEnabled() && currentRule != null && currentRule.enabled) {
            boolean triggered = FeatureGateLabRuntime.isTriggered(entry.manager, entry.key, entry.type);
            if ("OBJECT".equals(entry.type)) {
                int fields = structuredFieldCount(currentRule.value);
                return fields + " field" + (fields == 1 ? "" : "s")
                        + (triggered ? " overridden" : " will be overridden when requested");
            }
            return currentRule.value + (triggered
                    ? " (override returned)"
                    : " (will be returned when requested)");
        }
        return entry.loaded
                ? entry.currentValue + " (TikTok value)"
                : "No current value; no active override";
    }

    private void addObjectEditors(LinearLayout root, boolean editable) {
        JSONObject values = structuredBaseValue();
        JSONArray fieldNames = StructuredConfigController.actionableFields(entry.requestedClass);
        if (fieldNames.length() == 0) {
            TextView unavailable = FeatureGateLabUi.label(
                    root.getContext(),
                    L10n.t(root.getContext(), "This configuration has no fields that can be copied and changed safely on this build.")
            );
            unavailable.setTextColor(FeatureGateLabUi.warningColor(root.getContext()));
            root.addView(unavailable, FeatureGateLabUi.matchWrap());
            return;
        }

        TextView explanation = FeatureGateLabUi.label(
                root.getContext(),
                L10n.t(root.getContext(), "Only fields that can be type-checked and applied to a copied configuration object are shown.")
        );
        LinearLayout.LayoutParams explanationParams = FeatureGateLabUi.matchWrap();
        explanationParams.setMargins(0, FeatureGateLabUi.dp(root.getContext(), 6), 0, 0);
        root.addView(explanation, explanationParams);

        for (int index = 0; index < fieldNames.length(); index++) {
            String fieldName = fieldNames.optString(index, "");
            String kind = StructuredConfigController.fieldKind(entry.requestedClass, fieldName);
            if (fieldName.isEmpty() || "UNSUPPORTED".equals(kind)) {
                continue;
            }
            Object value = values.opt(fieldName);
            LinearLayout fieldRoot = new LinearLayout(root.getContext());
            fieldRoot.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams fieldParams = FeatureGateLabUi.matchWrap();
            fieldParams.setMargins(0, FeatureGateLabUi.dp(root.getContext(), 12), 0, 0);

            TextView title = FeatureGateLabUi.body(root.getContext(), titleForField(fieldName));
            fieldRoot.addView(title, FeatureGateLabUi.matchWrap());
            TextView rawName = FeatureGateLabUi.label(root.getContext(), fieldName + " / " + kind.toLowerCase(Locale.ROOT));
            fieldRoot.addView(rawName, FeatureGateLabUi.matchWrap());

            if ("BOOLEAN".equals(kind)) {
                Switch toggle = new Switch(root.getContext());
                toggle.setContentDescription(fieldName);
                toggle.setChecked(value instanceof Boolean
                        ? (Boolean) value
                        : Boolean.parseBoolean(String.valueOf(value)));
                toggle.setEnabled(editable);
                fieldRoot.addView(toggle, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        FeatureGateLabUi.dp(root.getContext(), 48)
                ));
                objectEditors.add(ObjectFieldEditor.toggle(fieldName, kind, toggle));
            } else {
                EditText input = new EditText(root.getContext());
                input.setEnabled(editable);
                input.setText(editorText(value, kind));
                // Three calls rather than one with a comparison inside it. "JSON" is a
                // format name, and a literal inside an L10n call is read as a key by the gate
                // that pairs a key with its translation; a hint behind a variable is read by
                // nothing at all.
                String hint;
                if (kind.startsWith("LIST_")) {
                    hint = L10n.t(root.getContext(), "One value per line");
                } else if ("JSON".equals(kind)) {
                    hint = L10n.t(root.getContext(), "Advanced JSON value");
                } else {
                    hint = L10n.t(root.getContext(), "Value");
                }
                input.setHint(hint);
                input.setSingleLine(!kind.startsWith("LIST_") && !"JSON".equals(kind));
                input.setInputType(inputTypeFor(kind));
                input.setMinimumHeight(FeatureGateLabUi.dp(
                        root.getContext(),
                        kind.startsWith("LIST_") || "JSON".equals(kind) ? 96 : 48
                ));
                SettingsUi.styleEditText(input);
                fieldRoot.addView(input, FeatureGateLabUi.matchWrap());
                objectEditors.add(ObjectFieldEditor.input(fieldName, kind, input));
            }
            root.addView(fieldRoot, fieldParams);
        }
    }

    private JSONObject structuredBaseValue() {
        boolean rootArray = StructuredConfigController.isRootArray(entry.requestedClass);
        JSONObject result = parseStructuredValue(
                entry.loaded ? entry.currentValue : null,
                rootArray
        );
        if (result == null && !entry.defaults.isEmpty()) {
            result = parseStructuredValue(entry.defaults.get(0), rootArray);
        }
        if (result == null) {
            result = new JSONObject();
        }
        // A root array is wrapped in one $value field, even when it contains many entries.
        // The small-object fallback is for unavailable configuration metadata, not arrays.
        if (!rootArray && result.length() <= 2) {
            JSONObject generatedDefault = StructuredConfigController.defaultValue(entry.requestedClass);
            if (generatedDefault != null) {
                result = generatedDefault;
            }
        }
        if (rule != null) {
            JSONObject selected = parseObject(rule.value);
            if (selected != null) {
                java.util.Iterator<String> names = selected.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    try {
                        result.put(name, selected.opt(name));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return result;
    }

    private static JSONObject parseStructuredValue(String text, boolean rootArray) {
        JSONObject object = parseObject(text);
        if (object != null || !rootArray || text == null || text.isEmpty()) {
            return object;
        }
        try {
            JSONObject result = new JSONObject();
            result.put("$value", new JSONArray(text));
            return result;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String objectPatchText() {
        JSONObject result = new JSONObject();
        try {
            for (ObjectFieldEditor editor : objectEditors) {
                result.put(editor.name, editor.value());
            }
        } catch (Throwable throwable) {
            Utils.showToastLong(L10n.f(Utils.getContext(), "Invalid field value: %1$s",
                    String.valueOf(throwable.getMessage())));
            return "{}";
        }
        return result.toString();
    }

    private static JSONObject parseObject(String text) {
        if (text == null || text.isEmpty() || "null".equals(text)) {
            return null;
        }
        try {
            return new JSONObject(text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int structuredFieldCount(String text) {
        JSONObject object = parseObject(text);
        return object == null ? 0 : object.length();
    }

    private static String editorText(Object value, String kind) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        if (!kind.startsWith("LIST_") || !(value instanceof JSONArray)) {
            if ("JSON".equals(kind) && value instanceof JSONObject) {
                return value.toString();
            }
            if ("JSON".equals(kind) && value instanceof JSONArray) {
                return value.toString();
            }
            return String.valueOf(value);
        }
        JSONArray array = (JSONArray) value;
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < array.length(); index++) {
            if (result.length() > 0) result.append('\n');
            result.append(String.valueOf(array.opt(index)));
        }
        return result.toString();
    }

    private static int inputTypeFor(String kind) {
        if ("INT".equals(kind) || "LONG".equals(kind)) {
            return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
        if ("FLOAT".equals(kind) || "DOUBLE".equals(kind)) {
            return InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        }
        if (kind.startsWith("LIST_") || "JSON".equals(kind)) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        return InputType.TYPE_CLASS_TEXT;
    }

    private static String titleForField(String fieldName) {
        if ("$value".equals(fieldName)) {
            return "Value";
        }
        StringBuilder result = new StringBuilder();
        String normalized = fieldName.replace('-', '_').replace('.', '_');
        for (String word : normalized.split("_+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }
        return result.length() == 0 ? fieldName : result.toString();
    }

    private void addTechnicalDetails(LinearLayout root) {
        Context context = root.getContext();
        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = FeatureGateLabUi.text(context, L10n.t(context, "Technical details"), 15, SettingsUi.textPrimary(), Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, FeatureGateLabUi.dp(context, 48), 1f));
        technicalToggle = FeatureGateLabUi.text(context, L10n.t(context, "Show"), 14,
                SettingsUi.accent(), Typeface.BOLD);
        technicalToggle.setGravity(Gravity.CENTER);
        technicalToggle.setPadding(
                FeatureGateLabUi.dp(context, 12),
                0,
                FeatureGateLabUi.dp(context, 12),
                0
        );
        heading.addView(technicalToggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                FeatureGateLabUi.dp(context, 40)
        ));
        LinearLayout.LayoutParams headingParams = FeatureGateLabUi.matchWrap();
        headingParams.setMargins(0, FeatureGateLabUi.dp(context, 12), 0, 0);
        root.addView(heading, headingParams);

        technicalDetails = new LinearLayout(context);
        technicalDetails.setOrientation(LinearLayout.VERTICAL);
        technicalDetails.setVisibility(View.GONE);
        addInfo(technicalDetails, L10n.t(context, "Manager"), entry.manager);
        addInfo(technicalDetails, L10n.t(context, "Type"), entry.type);
        addInfo(technicalDetails, L10n.t(context, "Source"), entry.sourceLabel());
        addInfo(technicalDetails, L10n.t(context, "Generated defaults"), join(entry.defaults));
        addInfo(technicalDetails, L10n.t(context, "Historical values"), join(entry.historical));
        addInfo(technicalDetails, L10n.t(context, "Researched values"), join(entry.researched));
        addInfo(technicalDetails, L10n.t(context, "Proof"), entry.proof);
        String original = FeatureGateLabRuntime.originalValue(entry.manager, entry.key, entry.type);
        if (original != null) {
            addInfo(technicalDetails, L10n.t(context, "Last original value"), original);
        }
        String caller = FeatureGateLabRuntime.firstCaller(entry.manager, entry.key, entry.type);
        if (caller != null) {
            addInfo(technicalDetails, L10n.t(context, "First caller"), caller);
        }
        root.addView(technicalDetails, FeatureGateLabUi.matchWrap());

        View.OnClickListener toggle = ignored -> {
            boolean show = technicalDetails.getVisibility() != View.VISIBLE;
            technicalDetails.setVisibility(show ? View.VISIBLE : View.GONE);
            technicalToggle.setText(L10n.t(getContext(), show ? "Hide" : "Show"));
        };
        heading.setOnClickListener(toggle);
        technicalToggle.setOnClickListener(toggle);
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView title = FeatureGateLabUi.text(root.getContext(), text.toUpperCase(Locale.ROOT), 11, SettingsUi.accent(), Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams params = FeatureGateLabUi.matchWrap();
        params.setMargins(0, FeatureGateLabUi.dp(root.getContext(), 18), 0, FeatureGateLabUi.dp(root.getContext(), 12));
        root.addView(title, params);
    }

    private TextView addInfo(LinearLayout root, String label, String value) {
        Context context = root.getContext();
        if (root.getChildCount() > 0) {
            View divider = new View(context);
            divider.setBackgroundColor(SettingsUi.divider());
            root.addView(divider, new LinearLayout.LayoutParams(-1, FeatureGateLabUi.dp(context, 1)));
        }
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(FeatureGateLabUi.dp(context, 60));
        row.setPadding(0, FeatureGateLabUi.dp(context, 16), 0, FeatureGateLabUi.dp(context, 16));
        TextView labelView = FeatureGateLabUi.body(context, label);
        row.addView(labelView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView valueView = FeatureGateLabUi.label(context, value == null || value.isEmpty()
                ? L10n.t(context, "None recorded") : value);
        valueView.setTextIsSelectable(true);
        valueView.setGravity(Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, -2, 1);
        valueParams.setMarginStart(FeatureGateLabUi.dp(context, 16));
        row.addView(valueView, valueParams);
        root.addView(row, FeatureGateLabUi.matchWrap());
        return valueView;
    }

    private static List<ValueOption> buildOptions(FeatureGateCatalog.Entry entry, FeatureGateLabStore.Rule rule) {
        LinkedHashMap<String, List<String>> values = new LinkedHashMap<>();
        if (entry.loaded) addValue(values, entry.currentValue, "Current");
        for (String value : entry.defaults) addValue(values, value, "Default");
        for (String value : entry.researched) addValue(values, value, "Researched");
        for (String value : entry.historical) addValue(values, value, "Historical");
        if (rule != null) addValue(values, rule.value, "Selected");
        List<ValueOption> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> item : values.entrySet()) {
            result.add(new ValueOption(item.getKey(), item.getKey() + " (" + join(item.getValue()) + ")", false));
        }
        result.add(new ValueOption(null, "Custom value...", true));
        return result;
    }

    private static void addValue(Map<String, List<String>> values, String value, String source) {
        if (value == null || "null".equals(value)) return;
        List<String> sources = values.get(value);
        if (sources == null) {
            sources = new ArrayList<>();
            values.put(value, sources);
        }
        if (!sources.contains(source)) sources.add(source);
    }

    private static int selectedIndex(List<ValueOption> options, String value) {
        for (int i = 0; i < options.size(); i++) {
            if (!options.get(i).custom && options.get(i).value.equals(value)) return i;
        }
        return 0;
    }

    private static String bestInitialValue(FeatureGateCatalog.Entry entry) {
        if (entry.loaded && entry.currentValue != null) return entry.currentValue;
        if (!entry.defaults.isEmpty()) return entry.defaults.get(0);
        if (!entry.researched.isEmpty()) return entry.researched.get(0);
        if (!entry.historical.isEmpty()) return entry.historical.get(0);
        if ("BOOLEAN".equals(entry.type)) return "false";
        return "";
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        String[] terms = {"auth", "login", "security", "risk", "compliance", "region", "age", "minor", "payment", "purchase", "wallet", "account", "ban"};
        for (String term : terms) if (lower.contains(term)) return true;
        return false;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "None recorded";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.toString();
    }

    private static final class ObjectFieldEditor {
        final String name;
        final String kind;
        final Switch toggle;
        final EditText input;

        private ObjectFieldEditor(String name, String kind, Switch toggle, EditText input) {
            this.name = name;
            this.kind = kind;
            this.toggle = toggle;
            this.input = input;
        }

        static ObjectFieldEditor toggle(String name, String kind, Switch toggle) {
            return new ObjectFieldEditor(name, kind, toggle, null);
        }

        static ObjectFieldEditor input(String name, String kind, EditText input) {
            return new ObjectFieldEditor(name, kind, null, input);
        }

        Object value() {
            if (toggle != null) {
                return toggle.isChecked();
            }
            String text = input == null ? "" : input.getText().toString();
            if (kind.startsWith("LIST_")) {
                String elementKind = kind.substring("LIST_".length());
                JSONArray result = new JSONArray();
                for (String line : text.split("\\r?\\n")) {
                    String item = line.trim();
                    if (!item.isEmpty()) {
                        result.put(scalarValue(elementKind, item));
                    }
                }
                return result;
            }
            if ("JSON".equals(kind)) {
                String trimmed = text.trim();
                try {
                    if (trimmed.startsWith("{")) {
                        return new JSONObject(trimmed);
                    }
                    if (trimmed.startsWith("[")) {
                        return new JSONArray(trimmed);
                    }
                } catch (Throwable throwable) {
                    throw new IllegalArgumentException("invalid JSON");
                }
                throw new IllegalArgumentException("expected a JSON object or array");
            }
            return scalarValue(kind, text.trim());
        }

        private static Object scalarValue(String kind, String text) {
            switch (kind) {
                case "BOOLEAN":
                    if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                        throw new IllegalArgumentException("expected true or false");
                    }
                    return Boolean.valueOf(text);
                case "INT":
                    return Integer.valueOf(text);
                case "LONG":
                    return Long.valueOf(text);
                case "FLOAT": {
                    float value = Float.parseFloat(text);
                    if (!Float.isFinite(value)) throw new IllegalArgumentException("number must be finite");
                    return value;
                }
                case "DOUBLE": {
                    double value = Double.parseDouble(text);
                    if (!Double.isFinite(value)) throw new IllegalArgumentException("number must be finite");
                    return value;
                }
                default:
                    return text;
            }
        }
    }

    private static final class ValueOption {
        final String value;
        final String label;
        final boolean custom;

        ValueOption(String value, String label, boolean custom) {
            this.value = value;
            this.label = label;
            this.custom = custom;
        }
    }
}
