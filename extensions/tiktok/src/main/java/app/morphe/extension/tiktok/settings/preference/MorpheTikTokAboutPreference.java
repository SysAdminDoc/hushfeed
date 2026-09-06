package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import app.morphe.extension.tiktok.settings.L10n;

@SuppressWarnings("deprecation")
public final class MorpheTikTokAboutPreference extends Preference {
    public MorpheTikTokAboutPreference(Context context) {
        super(context);
        setTitle("Hushfeed");
        setSummary(L10n.t(context, "Source code and releases"));
        setOnPreferenceClickListener(preference -> {
            app.morphe.extension.shared.Utils.openLink("https://github.com/SysAdminDoc/hushfeed");
            return true;
        });
    }
}
