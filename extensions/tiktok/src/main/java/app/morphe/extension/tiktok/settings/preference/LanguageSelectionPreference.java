/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.preference.Preference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.settings.StringSetting;

@SuppressWarnings("deprecation")
public final class LanguageSelectionPreference extends Preference {
    private static final String SERVICE_MANAGER_CLASS =
            "com.ss.android.ugc.aweme.framework.services.ServiceManager";
    private static final String CONTENT_LANGUAGE_SERVICE_CLASS =
            "com.ss.android.ugc.aweme.contentlanguage.api.IContentLanguageService";
    private static final String[] BUNDLED_LANGUAGE_CODES = {
            "af", "ar", "az", "bg", "bn", "ca", "ceb", "cs", "da", "de", "el", "en",
            "es", "et", "fa", "fi", "fil", "fr", "ga", "gu", "he", "hi", "hr", "hu",
            "id", "is", "it", "ja", "jv", "kk", "km", "kn", "ko", "lt", "lv", "ml",
            "mr", "ms", "my", "nb", "nl", "or", "pa", "pl", "pt", "ro", "ru", "sk",
            "sl", "sq", "sv", "sw", "ta", "te", "th", "tr", "uk", "ur", "uz", "vi",
            "zh", "zu"
    };

    private final StringSetting setting;
    private String value;
    private boolean valueSet;

    public LanguageSelectionPreference(Context context, StringSetting setting) {
        super(context);
        this.setting = setting;
        setTitle(L10n.t(context, "Do not translate languages"));
        setKey(setting.key);
        setValue(setting.get());
    }

    public String getValue() {
        return value;
    }

    public boolean setValue(String newValue) {
        String sanitized = serialize(parseCodes(newValue));
        boolean changed = !TextUtils.equals(value, sanitized);
        if (changed || !valueSet) {
            value = sanitized;
            valueSet = true;
            setting.save(sanitized);
            refreshSummary();
            if (changed) notifyChanged();
        }
        return changed;
    }

    @Override
    protected void onClick() {
        showLanguageDialog();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }

    private void refreshSummary() {
        Set<String> selected = parseCodes(value);
        if (selected.isEmpty()) {
            setSummary("No additional languages excluded");
            return;
        }

        Map<String, String> labels = labelsByCode();
        List<String> names = new ArrayList<>();
        for (String code : selected) {
            String label = labels.get(code);
            names.add(label == null ? code : label);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        if (names.size() <= 3) {
            setSummary(TextUtils.join(", ", names));
        } else {
            setSummary(L10n.f(getContext(), "%d languages excluded", names.size()));
        }
    }

    private void showLanguageDialog() {
        Context context = getContext();
        Set<String> selected = new LinkedHashSet<>(parseCodes(value));
        List<LanguageOption> allOptions = buildOptions();
        List<LanguageOption> visibleOptions = new ArrayList<>(allOptions);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(18));
        root.setBackground(SettingsUi.borderedSurface(context, 6, true));

        TextView title = new TextView(context);
        title.setText(L10n.t(context, "Do not translate languages"));
        title.setTextColor(SettingsUi.textPrimary());
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView helper = new TextView(context);
        helper.setText(L10n.t(context, "Checked languages keep their original comments. TikTok's current catalog is supplemented by languages bundled with this app version."));
        helper.setTextColor(SettingsUi.textSecondary());
        helper.setTextSize(14);
        LinearLayout.LayoutParams helperParams = matchWrap();
        helperParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(helper, helperParams);

        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(L10n.t(context, "Search language or code"));
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        SettingsUi.styleEditText(search);
        root.addView(search, matchWrap());

        ListView list = new ListView(context);
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDividerHeight(Math.max(1, dp(1)));
        LanguageAdapter adapter = new LanguageAdapter(context, visibleOptions, selected);
        list.setAdapter(adapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsUi.dialogListHeight(context, 430)
        );
        listParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(list, listParams);

        TextView nothing = new TextView(context);
        nothing.setText(L10n.t(context, "No language matches that"));
        nothing.setTextColor(SettingsUi.textSecondary());
        nothing.setTextSize(14);
        nothing.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nothingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsUi.dialogListHeight(context, 430)
        );
        nothingParams.setMargins(0, dp(8), 0, dp(10));
        root.addView(nothing, nothingParams);
        // The list swaps itself for this and back again as the search narrows and widens.
        list.setEmptyView(nothing);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView clear = action(context, L10n.t(context, "Clear"), false);
        TextView cancel = action(context, L10n.t(context, "Cancel"), false);
        TextView save = action(context, L10n.t(context, "Save"), true);
        actions.addView(clear);
        actions.addView(cancel);
        actions.addView(save);
        root.addView(actions, matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(context).setView(root).create();
        list.setOnItemClickListener((parent, view, position, id) ->
                toggleSelection(visibleOptions, selected, adapter, position));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                visibleOptions.clear();
                for (LanguageOption option : allOptions) {
                    if (query.isEmpty() || option.searchText.contains(query)) {
                        visibleOptions.add(option);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
            return true;
        });
        clear.setOnClickListener(view -> {
            selected.clear();
            adapter.notifyDataSetChanged();
        });
        cancel.setOnClickListener(view -> dialog.dismiss());
        save.setOnClickListener(view -> {
            setValue(serialize(selected));
            dialog.dismiss();
        });

        dialog.show();
        SettingsUi.styleDialog(dialog);
    }

    private List<LanguageOption> buildOptions() {
        Map<String, LanguageOption> unique = new LinkedHashMap<>();
        try {
            Class<?> serviceManagerClass = Class.forName(SERVICE_MANAGER_CLASS);
            Object serviceManager = serviceManagerClass.getMethod("get").invoke(null);
            Class<?> serviceClass = Class.forName(CONTENT_LANGUAGE_SERVICE_CLASS);
            Object service = serviceManagerClass
                    .getMethod("getService", Class.class)
                    .invoke(serviceManager, serviceClass);
            if (service != null) {
                Object result = serviceClass.getMethod("getLanguage").invoke(service);
                if (result instanceof List) {
                    for (Object language : (List<?>) result) {
                        if (language == null) continue;
                        String rawCode = invokeString(language, "getLanguageCode");
                        String code = normalizeCode(rawCode);
                        if (code == null || unique.containsKey(code)) continue;

                        String name = invokeString(language, "getEnglishName");
                        if (TextUtils.isEmpty(name)) {
                            name = invokeString(language, "getLocalName");
                        }
                        if (TextUtils.isEmpty(name)) name = rawCode;
                        unique.put(code, new LanguageOption(code, name));
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // The target-owned catalog can be unavailable early in app startup.
        }

        addBundledLanguages(unique);
        List<LanguageOption> result = new ArrayList<>(unique.values());
        Collections.sort(result, (left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name));
        return result;
    }

    private static void addBundledLanguages(Map<String, LanguageOption> unique) {
        for (String code : BUNDLED_LANGUAGE_CODES) {
            if (unique.containsKey(code)) continue;
            Locale locale = Locale.forLanguageTag(code);
            String name = locale.getDisplayLanguage(Locale.ENGLISH);
            if (TextUtils.isEmpty(name)) name = code.toUpperCase(Locale.ROOT);
            unique.put(code, new LanguageOption(code, name));
        }
    }

    private Map<String, String> labelsByCode() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (LanguageOption option : buildOptions()) labels.put(option.code, option.name);
        return labels;
    }

    private Set<String> parseCodes(String raw) {
        Set<String> codes = new LinkedHashSet<>();
        if (raw == null) return codes;
        for (String token : raw.split("[,;\\s]+")) {
            String code = normalizeCode(token);
            if (code != null) codes.add(code);
        }
        return codes;
    }

    private String serialize(Set<String> codes) {
        List<String> sorted = new ArrayList<>(codes);
        Collections.sort(sorted);
        return TextUtils.join(",", sorted);
    }

    private String normalizeCode(String raw) {
        if (raw == null) return null;
        String code = raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        int separator = code.indexOf('-');
        if (separator > 0) code = code.substring(0, separator);
        if ("in".equals(code)) code = "id";
        if ("iw".equals(code)) code = "he";
        if ("ji".equals(code)) code = "yi";
        if (code.length() < 2 || code.length() > 3) return null;
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) < 'a' || code.charAt(i) > 'z') return null;
        }
        return "und".equals(code) ? null : code;
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void toggleSelection(
            List<LanguageOption> visibleOptions,
            Set<String> selected,
            LanguageAdapter adapter,
            int position
    ) {
        if (position < 0 || position >= visibleOptions.size()) return;
        LanguageOption option = visibleOptions.get(position);
        if (!selected.add(option.code)) selected.remove(option.code);
        adapter.notifyDataSetChanged();
    }

    private TextView action(Context context, String label, boolean primary) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        SettingsUi.styleTextAction(button, primary);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return SettingsUi.dp(getContext(), value);
    }

    private static final class LanguageOption {
        final String code;
        final String name;
        final String searchText;

        LanguageOption(String code, String name) {
            this.code = code;
            this.name = name;
            this.searchText = (name + " " + code).toLowerCase(Locale.ROOT);
        }
    }

    private static final class LanguageAdapter extends ArrayAdapter<LanguageOption> {
        private final List<LanguageOption> options;
        private final Set<String> selected;

        LanguageAdapter(Context context, List<LanguageOption> options, Set<String> selected) {
            super(context, 0, options);
            this.options = options;
            this.selected = selected;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            Context context = getContext();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(SettingsUi.dp(context, 8), SettingsUi.dp(context, 8),
                    SettingsUi.dp(context, 8), SettingsUi.dp(context, 8));
            row.setBackgroundColor(SettingsUi.surface());
            row.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

            LanguageOption option = getItem(position);
            CheckBox check = new CheckBox(context);
            check.setClickable(false);
            check.setFocusable(false);
            check.setFocusableInTouchMode(false);
            check.setChecked(option != null && selected.contains(option.code));
            SettingsUi.styleCheckBox(check);
            row.addView(check);

            TextView text = new TextView(context);
            text.setText(option == null ? "" : option.name + "  (" + option.code + ")");
            text.setTextColor(SettingsUi.textPrimary());
            text.setTextSize(16);
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            return row;
        }
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
