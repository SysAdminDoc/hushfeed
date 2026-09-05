/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public final class SharePreferenceCategory extends ConditionalPreferenceCategory {
    public SharePreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Share sheet");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.shareSheetEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(context, "Hide sharing apps", "Remove the Share via row.", Settings.HIDE_SHARE_CHANNELS));
        addPreference(new TogglePreference(context, "Hide video actions", "Remove the actions row from the share sheet.", Settings.HIDE_SHARE_ACTIONS));
        addPreference(new TogglePreference(
                context,
                "Confirm before sending to a friend",
                "A tap on a person in the Send to row only selects them. A second tap on the "
                        + "same person within four seconds sends the video.",
                Settings.SHARE_CONFIRM_SEND
        ));
        addPreference(new TogglePreference(
                context,
                "Hide the Send to row",
                "Remove the row of friends from the top of the share sheet.",
                Settings.HIDE_SHARE_CONTACTS
        ));
        addPreference(new InputTextPreference(
                context,
                "Hide people and options by name",
                "Comma separated names exactly as the share sheet shows them: friends in the "
                        + "Send to row, share targets such as Facebook, and actions such as "
                        + "Create group or Repost. Stable keys such as copy, save and dislike also work before the sheet opens.",
                Settings.SHARE_HIDDEN_ITEMS
        ));
    }
}
