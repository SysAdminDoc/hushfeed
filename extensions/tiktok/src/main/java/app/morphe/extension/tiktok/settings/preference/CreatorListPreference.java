/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.tiktok.feedfilter.AdvancedFeedRules;
import app.morphe.extension.tiktok.settings.L10n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A searchable, one-row-per-entry editor for locally hidden creators. */
@SuppressWarnings("deprecation")
public class CreatorListPreference extends DialogPreference {
    private String value;
    private boolean valueSet;
    private List<String> pendingEntries = new ArrayList<>();
    private LinearLayout entriesContainer;
    private EditText searchEditText;
    private EditText addEditText;
    private TextView emptyState;

    public CreatorListPreference(Context context, String title, String summary, StringSetting setting) {
        super(context);
        setTitle(title);
        setSummary(summary);
        setKey(setting.key);
        setValue(setting.get());
    }

    public void setValue(String nextValue) {
        String normalized = nextValue == null ? "" : nextValue;
        boolean changed = !TextUtils.equals(value, normalized);
        if (changed || !valueSet) {
            value = normalized;
            valueSet = true;
            persistString(normalized);
            if (changed) {
                notifyDependencyChange(shouldDisableDependents());
                notifyChanged();
            }
        }
    }

    public String getValue() {
        return value;
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        pendingEntries = new ArrayList<>(AdvancedFeedRules.creatorEntries(getValue()));

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

        if (getSummary() != null && getSummary().length() > 0) {
            TextView summary = SettingsUi.text(
                    context,
                    getSummary().toString(),
                    14,
                    SettingsUi.textSecondary(),
                    Typeface.NORMAL
            );
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            summaryParams.setMargins(0, SettingsUi.dp(context, 14), 0, SettingsUi.dp(context, 10));
            dialogView.addView(summary, summaryParams);
        }

        TextView searchLabel = SettingsUi.text(
                context,
                L10n.t(context, "Search hidden creators"),
                13,
                SettingsUi.textSecondary(),
                Typeface.BOLD
        );
        dialogView.addView(searchLabel);

        searchEditText = editor(context, L10n.t(context, "Filter the list"));
        searchEditText.setTag("creator_list_search");
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                refreshEntryRows();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(0, SettingsUi.dp(context, 5), 0, SettingsUi.dp(context, 10));
        dialogView.addView(searchEditText, searchParams);

        LinearLayout addRow = new LinearLayout(context);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addEditText = editor(context, L10n.t(context, "Creator handle or id"));
        addEditText.setTag("creator_list_add");
        addRow.addView(addEditText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button addButton = new Button(context);
        addButton.setText(L10n.t(context, "Add"));
        addButton.setContentDescription(L10n.t(context, "Add hidden creator"));
        addButton.setAllCaps(false);
        addButton.setMinimumHeight(SettingsUi.dp(context, 48));
        SettingsUi.styleActionButton(addButton, true);
        addButton.setOnClickListener(view -> addEntry());
        LinearLayout.LayoutParams addButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        addButtonParams.setMargins(SettingsUi.dp(context, 8), 0, 0, 0);
        addRow.addView(addButton, addButtonParams);
        dialogView.addView(addRow);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        entriesContainer = new LinearLayout(context);
        entriesContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entriesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsUi.dialogListHeight(context, 230)
        );
        scrollParams.setMargins(0, SettingsUi.dp(context, 12), 0, 0);
        dialogView.addView(scroll, scrollParams);

        emptyState = SettingsUi.text(context, "", 14, SettingsUi.textSecondary(), Typeface.NORMAL);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(SettingsUi.dp(context, 12), SettingsUi.dp(context, 18),
                SettingsUi.dp(context, 12), SettingsUi.dp(context, 18));
        dialogView.addView(emptyState, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        refreshEntryRows();
        return dialogView;
    }

    private static EditText editor(Context context, String hint) {
        EditText editor = new EditText(context);
        editor.setInputType(InputType.TYPE_CLASS_TEXT);
        editor.setSingleLine(true);
        editor.setHint(hint);
        editor.setMinHeight(SettingsUi.dp(context, 48));
        SettingsUi.styleEditText(editor);
        return editor;
    }

    private void addEntry() {
        String candidate = addEditText == null ? "" : addEditText.getText().toString().trim();
        if (candidate.isEmpty()) {
            Utils.showToastShort(L10n.t("Enter a creator handle or id"));
            return;
        }
        String problem = AdvancedFeedRules.creatorEntryProblem(candidate);
        if (problem != null) {
            Utils.showToastLong(problem);
            return;
        }
        if (AdvancedFeedRules.hasCreatorEntry(AdvancedFeedRules.joinCreatorEntries(pendingEntries), candidate)) {
            Utils.showToastShort(L10n.t("That creator is already in the list"));
            return;
        }
        pendingEntries.add(candidate);
        addEditText.setText("");
        refreshEntryRows();
    }

    private void refreshEntryRows() {
        if (entriesContainer == null) return;
        entriesContainer.removeAllViews();
        String query = searchEditText == null ? "" : searchEditText.getText().toString()
                .trim().toLowerCase(Locale.ROOT);
        int visible = 0;
        for (String entry : pendingEntries) {
            if (!query.isEmpty() && !entry.toLowerCase(Locale.ROOT).contains(query)) continue;
            addEntryRow(entry);
            visible++;
        }
        if (visible == 0) {
            emptyState.setText(pendingEntries.isEmpty()
                    ? L10n.t(getContext(), "No creators are hidden yet")
                    : L10n.t(getContext(), "No hidden creators match this search"));
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }

    private void addEntryRow(String entry) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(SettingsUi.dp(context, 12), SettingsUi.dp(context, 3),
                SettingsUi.dp(context, 4), SettingsUi.dp(context, 3));
        row.setBackground(SettingsUi.borderedSurface(context, 8, true));
        TextView label = SettingsUi.text(context, entry, 15, SettingsUi.textPrimary(), Typeface.NORMAL);
        label.setTextIsSelectable(true);
        label.setContentDescription(entry);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView remove = new TextView(context);
        remove.setText(L10n.t(context, "Remove"));
        remove.setContentDescription(L10n.f(context, "Remove %1$s", entry));
        remove.setGravity(Gravity.CENTER);
        remove.setMinHeight(SettingsUi.dp(context, 48));
        remove.setMinWidth(SettingsUi.dp(context, 72));
        remove.setPadding(SettingsUi.dp(context, 8), 0, SettingsUi.dp(context, 8), 0);
        SettingsUi.styleTextAction(remove, false);
        remove.setTag("creator_remove_" + entry);
        remove.setOnClickListener(view -> {
            pendingEntries.remove(entry);
            refreshEntryRows();
        });
        row.addView(remove, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, SettingsUi.dp(context, 4), 0, 0);
        entriesContainer.addView(row, rowParams);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
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
            setValue(AdvancedFeedRules.joinCreatorEntries(pendingEntries));
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
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
