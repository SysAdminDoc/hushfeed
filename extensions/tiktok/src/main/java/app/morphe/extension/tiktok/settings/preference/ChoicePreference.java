package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.view.View;
import app.morphe.extension.shared.settings.StringSetting;

@SuppressWarnings("deprecation")
public final class ChoicePreference extends ListPreference {
    public ChoicePreference(Context context, String title, StringSetting setting, String[] labels, String[] values) {
        super(context);
        setTitle(title);
        setDialogTitle(title);
        setKey(setting.key);
        setEntries(labels);
        setEntryValues(values);
        setValue(setting.get());
        setSummary("%s");
    }

    @Override protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
    }
}
