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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.share.ShareActionCatalog;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Edits observed share actions while preserving manually saved unknown identifiers. */
@SuppressWarnings("deprecation")
public final class ShareActionChecklistPreference extends DialogPreference {
    private final List<ShareActionCatalog.Entry> catalog = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private LinearLayout rows;
    private EditText search;
    private String originalHidden = "";

    public ShareActionChecklistPreference(Context context) {
        super(context);
        setKey("share_action_checklist");
        setTitle("Share action checklist");
        setSummary("Choose observed share actions to hide. The stable identifier is shown below each label; unknown saved identifiers stay in the manual list.");
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }

    @Override
    protected View onCreateDialogView() {
        Context context = getContext();
        catalog.clear();
        catalog.addAll(ShareActionCatalog.entries());
        originalHidden = Settings.SHARE_HIDDEN_ITEMS.get();
        selected.clear();
        selected.addAll(selectedKeys(originalHidden));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = SettingsUi.dp(context, 22);
        root.setPadding(padding, padding, padding, SettingsUi.dp(context, 8));

        TextView title = SettingsUi.text(context, getTitle().toString(), 20,
                SettingsUi.textPrimary(), Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView summary = SettingsUi.text(context, getSummary().toString(), 14,
                SettingsUi.textSecondary(), Typeface.NORMAL);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, SettingsUi.dp(context, 14), 0, SettingsUi.dp(context, 10));
        root.addView(summary, summaryParams);

        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(L10n.t(context, "Search share actions"));
        search.setTag("share_action_search");
        SettingsUi.styleEditText(search);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dp(context, 300));
        scrollParams.topMargin = SettingsUi.dp(context, 10);
        root.addView(scroll, scrollParams);
        renderRows("");
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderRows(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        return root;
    }

    private void renderRows(String query) {
        rows.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (catalog.isEmpty()) {
            addState("No share actions have been observed yet",
                    "Open a share sheet once, then return here to choose its actions.");
            return;
        }
        int shown = 0;
        for (ShareActionCatalog.Entry entry : catalog) {
            if (!normalized.isEmpty()
                    && !((entry.label + " " + entry.key).toLowerCase(Locale.ROOT).contains(normalized))) {
                continue;
            }
            CheckBox check = new CheckBox(getContext());
            check.setText(entry.label);
            check.setTextColor(SettingsUi.textPrimary());
            check.setTag("share_action_" + entry.key);
            check.setChecked(selected.contains(entry.key));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(entry.key);
                else selected.remove(entry.key);
            });
            rows.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView identifier = SettingsUi.text(getContext(), entry.key, 12,
                    SettingsUi.textSecondary(), Typeface.NORMAL);
            identifier.setPadding(SettingsUi.dp(getContext(), 48), 0,
                    SettingsUi.dp(getContext(), 8), SettingsUi.dp(getContext(), 8));
            identifier.setTag("share_action_id_" + entry.key);
            rows.addView(identifier, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            shown++;
        }
        if (shown == 0) {
            addState("No share actions match this search", "Try a different word or clear the search.");
        }
    }

    private void addState(String title, String summary) {
        TextView state = SettingsUi.text(getContext(), L10n.t(getContext(), title), 14,
                SettingsUi.textSecondary(), Typeface.NORMAL);
        state.setPadding(SettingsUi.dp(getContext(), 8), SettingsUi.dp(getContext(), 8),
                SettingsUi.dp(getContext(), 8), SettingsUi.dp(getContext(), 8));
        rows.addView(state, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView details = SettingsUi.text(getContext(), L10n.t(getContext(), summary), 12,
                SettingsUi.textSecondary(), Typeface.NORMAL);
        details.setPadding(SettingsUi.dp(getContext(), 8), 0, SettingsUi.dp(getContext(), 8),
                SettingsUi.dp(getContext(), 8));
        rows.addView(details, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        builder.setPositiveButton(L10n.t(getContext(), "Save"), (dialog, which)
                -> onClick(dialog, DialogInterface.BUTTON_POSITIVE));
        builder.setNegativeButton(L10n.t(getContext(), "Cancel"), null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            Settings.SHARE_HIDDEN_ITEMS.save(buildHidden());
        }
        super.onDialogClosed(positiveResult);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        SettingsUi.styleFramedDialog(getDialog());
    }

    private Set<String> selectedKeys(String stored) {
        Set<String> keys = new HashSet<>();
        for (String token : tokens(stored)) {
            String canonical = ShareActionCatalog.canonical(token);
            for (ShareActionCatalog.Entry entry : catalog) {
                if (entry.key.equals(canonical)
                        || ShareActionCatalog.canonical(entry.label).equals(canonical)) {
                    keys.add(entry.key);
                }
            }
        }
        return keys;
    }

    private String buildHidden() {
        Set<String> known = new HashSet<>();
        for (ShareActionCatalog.Entry entry : catalog) {
            known.add(entry.key);
            known.add(ShareActionCatalog.canonical(entry.label));
        }
        List<String> output = new ArrayList<>();
        for (String token : tokens(originalHidden)) {
            if (!known.contains(ShareActionCatalog.canonical(token))) {
                output.add(token);
            }
        }
        for (ShareActionCatalog.Entry entry : catalog) {
            if (selected.contains(entry.key)) output.add(entry.key);
        }
        return String.join(", ", output);
    }

    private static List<String> tokens(String stored) {
        List<String> result = new ArrayList<>();
        if (stored == null) return result;
        for (String token : stored.split("[,\\n]")) {
            String value = token.trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }
}
