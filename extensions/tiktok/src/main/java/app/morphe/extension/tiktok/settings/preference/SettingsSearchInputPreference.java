/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.tiktok.settings.L10n;

/** An inline search field used by the settings index. */
@SuppressWarnings("deprecation")
public final class SettingsSearchInputPreference extends Preference {
    public interface QueryListener {
        void onQueryChanged(String query);
    }

    private final QueryListener queryListener;
    private EditText editText;
    private TextView clearButton;

    public SettingsSearchInputPreference(Context context, QueryListener queryListener) {
        super(context);
        this.queryListener = queryListener;
        setSelectable(false);
        setOrder(-800);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                SettingsUi.dp(context, 18),
                SettingsUi.dp(context, 8),
                SettingsUi.dp(context, 18),
                SettingsUi.dp(context, 8)
        );

        editText = new EditText(context);
        editText.setTag("settings_search_input");
        editText.setSingleLine(true);
        editText.setTextSize(16);
        editText.setHint(L10n.t(context, "Search settings"));
        editText.setContentDescription(L10n.t(context, "Search settings"));
        editText.setMinimumHeight(SettingsUi.dp(context, 48));
        editText.setPadding(0, 0, 0, 0);
        SettingsUi.styleEditText(editText);
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateClearButton();
                if (queryListener != null) {
                    queryListener.onQueryChanged(text == null ? "" : text.toString());
                }
            }

            @Override public void afterTextChanged(Editable text) {
            }
        });
        row.addView(editText, new LinearLayout.LayoutParams(0, -2, 1));

        clearButton = new TextView(context);
        clearButton.setTag("settings_search_clear");
        clearButton.setText("×");
        clearButton.setTextSize(24);
        clearButton.setTextColor(SettingsUi.accent());
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setContentDescription(L10n.t(context, "Clear search"));
        clearButton.setFocusable(true);
        clearButton.setClickable(true);
        clearButton.setMinimumWidth(SettingsUi.dp(context, 48));
        clearButton.setMinimumHeight(SettingsUi.dp(context, 48));
        clearButton.setOnClickListener(view -> editText.setText(""));
        row.addView(clearButton, new LinearLayout.LayoutParams(-2, -2));
        updateClearButton();
        return row;
    }

    private void updateClearButton() {
        if (clearButton != null) {
            clearButton.setVisibility(editText != null && editText.length() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    public String getQuery() {
        return editText == null ? "" : editText.getText().toString();
    }
}
