package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.tiktok.settings.SettingsBackup;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public final class SettingsBackupPreference extends Preference {
    private static final int EXPORT = 7311, IMPORT = 7312, RESET = 7313, UNDO = 7314;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private SettingsBackupPreference(TikTokPreferenceFragment fragment, int action, String title, String summary) {
        super(fragment.getActivity());
        setKey("settings_backup_" + action);
        setTitle(title);
        setSummary(summary);
        setOnPreferenceClickListener(preference -> {
            if (BUSY.get()) { Utils.showToastShort(L10n.t("A settings operation is already running")); return true; }
            if (action == RESET || action == UNDO) run(fragment, action, null);
            else pickFile(fragment, action);
            return true;
        });
    }

    /**
     * The four backup actions, kept together at the end of the page. They used to be added
     * without an order and so landed among the logging rows, which put Back up and Restore
     * four rows apart with unrelated switches between them.
     */
    public static void addTo(TikTokPreferenceFragment fragment, PreferenceScreen screen) {
        int order = screen.getPreferenceCount();
        for (Object[] row : new Object[][]{
                {EXPORT, "Back up settings",
                        "Save patch settings and Feature Gate Lab rules to a JSON file."},
                {IMPORT, "Restore settings",
                        "Choose a backup file. Your current settings are kept for Undo."},
                {RESET, "Reset settings",
                        "Restore defaults immediately. Your current settings are kept for Undo."},
                {UNDO, "Undo last restore or reset",
                        "Recover the settings saved before the last restore or reset."},
        }) {
            SettingsBackupPreference preference = new SettingsBackupPreference(
                    fragment, (Integer) row[0], (String) row[1], (String) row[2]);
            preference.setOrder(order++);
            screen.addPreference(preference);
        }
    }

    private static void pickFile(TikTokPreferenceFragment fragment, int action) {
        Intent intent = new Intent(action == EXPORT ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        if (action == EXPORT) intent.putExtra(Intent.EXTRA_TITLE, "hushfeed-settings-" + System.currentTimeMillis() + ".json");
        try { fragment.startActivityForResult(intent, action); }
        catch (RuntimeException error) {
            Logger.printException(() -> "Could not open settings file picker", error);
            Utils.showToastLong(L10n.t("The file picker is not available on this device."));
        }
    }

    public static boolean onResult(TikTokPreferenceFragment fragment, int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result == Activity.RESULT_OK && data != null && data.getData() != null) run(fragment, request, data.getData());
        return true;
    }

    private static void run(TikTokPreferenceFragment fragment, int action, Uri uri) {
        Activity activity = fragment.getActivity();
        if (activity == null || !BUSY.compareAndSet(false, true)) return;
        Context context = activity.getApplicationContext();
        WeakReference<TikTokPreferenceFragment> owner = new WeakReference<>(fragment);
        if (action != EXPORT) AbstractPreferenceFragment.settingImportInProgress = true;
        Utils.showToastShort(L10n.t(action == EXPORT
                ? "Saving settings backup" : "Updating settings"));
        Utils.runOnBackgroundThread(() -> {
            try {
                if (action == EXPORT) {
                    byte[] bytes = SettingsBackup.create(false).getBytes(StandardCharsets.UTF_8);
                    try (var output = context.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("Could not open destination");
                        output.write(bytes);
                    }
                } else if (action == IMPORT) {
                    SettingsBackup.restore(context, SettingsBackup.read(context.getContentResolver().openInputStream(uri)), true);
                } else if (action == RESET) SettingsBackup.reset(context);
                else SettingsBackup.undo(context);
                Utils.showToastLong(L10n.t(action == EXPORT ? "Settings backup saved"
                        : "Settings saved. Restart TikTok to apply all changes."));
            } catch (Exception error) {
                Logger.printException(() -> "Settings backup operation failed", error);
                Utils.showToastLong(L10n.t(
                    "That settings change did not go through. Nothing was altered."));
            } finally {
                Utils.runOnMainThread(() -> {
                    if (action != EXPORT) AbstractPreferenceFragment.settingImportInProgress = false;
                    BUSY.set(false);
                    TikTokPreferenceFragment current = owner.get();
                    if (current != null && current.isAdded()) current.refreshBackupSettings();
                });
            }
        });
    }

    @Override protected void onBindView(View view) {
        super.onBindView(view);
        app.morphe.extension.tiktok.Utils.setTitleAndSummaryColor(view);
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
