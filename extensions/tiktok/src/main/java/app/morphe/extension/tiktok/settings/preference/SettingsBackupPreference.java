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
public final class SettingsBackupPreference extends Preference
        implements app.morphe.extension.shared.settings.preference.ImmediateAction {
    private static final int EXPORT = 7311, IMPORT = 7312, RESET = 7313, UNDO = 7314;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    /** Reset and Undo act on the tap. Back up and Restore open a file picker first. */
    @Override public boolean actsOnTap() {
        return rowAction == RESET || rowAction == UNDO;
    }

    private final int rowAction;

    private SettingsBackupPreference(TikTokPreferenceFragment fragment, int action, String title, String summary) {
        super(fragment.getActivity());
        this.rowAction = action;
        setKey("settings_backup_" + action);
        setTitle(title);
        setSummary(summary);
        refreshUndoAvailability();
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

    /**
     * The same readable stamp the diagnostics export and the Lab export use. Epoch milliseconds
     * put a name in the picker that nobody could tell two backups apart by.
     */
    public static String suggestedExportName() {
        // The diagnostics export stamps in UTC, and a backup made in the same second used to get
        // a name hours away from it because this one used local time.
        return "hushfeed-settings-"
                + app.morphe.extension.shared.settings.preference.LogBufferManager.fileTimestamp()
                + ".json";
    }

    private static void pickFile(TikTokPreferenceFragment fragment, int action) {
        Intent intent = new Intent(action == EXPORT ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        if (action == EXPORT) intent.putExtra(Intent.EXTRA_TITLE, suggestedExportName());
        try { fragment.startActivityForResult(intent, action); }
        catch (RuntimeException error) {
            Logger.printException(() -> "Could not open settings file picker", error);
            Utils.showToastLong(L10n.t(
                    "This phone has no file picker, so there is no way to choose a file here."));
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
        // One line per action. "Updating settings" was said for a restore, a reset and an
        // undo alike, so the one thing on screen did not say which of the three was running.
        Utils.showToastShort(L10n.t(action == EXPORT ? "Saving settings backup"
                : action == IMPORT ? "Restoring your settings"
                : action == RESET ? "Putting the settings back to their defaults"
                : "Undoing the last change"));
        Utils.runOnBackgroundThread(() -> {
            boolean labRulesSkipped = false;
            int keptAsTheyWere = 0;
            try {
                if (action == EXPORT) {
                    byte[] bytes = SettingsBackup.create(false).getBytes(StandardCharsets.UTF_8);
                    try (var output = context.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("Could not open destination");
                        output.write(bytes);
                    }
                } else if (action == IMPORT) {
                    // Reading inside restoreFrom rather than here, so an unreadable or oversized
                    // file is refused with a reason. Read separately, those two came out as a
                    // bare IOException and reached the user as the generic rejection.
                    String text = SettingsBackup.restoreFrom(
                            context, context.getContentResolver().openInputStream(uri), true);
                    labRulesSkipped = SettingsBackup.labRulesWereSkipped(text);
                    keptAsTheyWere = SettingsBackup.settingsNotInFile(text);
                } else if (action == RESET) SettingsBackup.reset(context);
                else {
                    // An undo copy written before a retarget holds Lab rules for the older build,
                    // and dropping them silently is the same surprise as on an import. An undo
                    // copy older than a setting is the same case as a backup older than one, so
                    // it is counted the same way.
                    String undone = SettingsBackup.undo(context);
                    labRulesSkipped = SettingsBackup.labRulesWereSkipped(undone);
                    keptAsTheyWere = SettingsBackup.settingsNotInFile(undone);
                }
                // Said before the success line, so the success line is the one left on screen.
                // Anything the file did not carry stayed as the device had it, which is worth
                // saying: an older backup used to put every setting added since back to its
                // default, download folders included, without a word.
                if (keptAsTheyWere == 1) {
                    Utils.showToastLong(L10n.f(
                            "%1$d setting was not in that file and was left as it is.", keptAsTheyWere));
                } else if (keptAsTheyWere > 1) {
                    Utils.showToastLong(L10n.f(
                            "%1$d settings were not in that file and were left as they are.", keptAsTheyWere));
                }
                // Each of these is one literal, because the translation gate reads the literal
                // handed to L10n and a string built from two of them is two entries it cannot find.
                // The action decides the sentence, and only then does it matter whether Lab
                // rules were dropped. An undo copy written before a retarget carries rules for
                // the older build too, so testing that first made every such undo say a restore
                // had happened. The chain stays flat: a bracketed group around a nested ternary
                // puts the literal inside brackets that are not L10n's, which is the one thing
                // the translation gate reads.
                Utils.showToastLong(L10n.t(
                        action == EXPORT ? "Settings backup saved"
                                : action == IMPORT && labRulesSkipped
                                ? "Settings restored. The Feature Gate Lab rules were for another TikTok version and were left out. Restart TikTok to apply all changes."
                                : action == IMPORT
                                ? "Settings restored. Restart TikTok to apply all changes."
                                : action == RESET
                                ? "Settings are back to their defaults. Restart TikTok to apply all changes."
                                : labRulesSkipped
                                ? "The last change is undone. The Feature Gate Lab rules were for another TikTok version and were left out. Restart TikTok to apply all changes."
                                : "The last change is undone. Restart TikTok to apply all changes."));
            } catch (Exception error) {
                Logger.printException(() -> "Settings backup operation failed", error);
                Utils.showToastLong(L10n.t(failureMessage(action, error)));
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

    static String failureMessage(int action, Exception error) {
        if (action == EXPORT) return "Could not save settings backup.";
        if (error instanceof SettingsBackup.RestoreException) {
            SettingsBackup.RestoreException restore = (SettingsBackup.RestoreException) error;
            switch (restore.getFailure()) {
                case REJECTED_INPUT:
                    // One sentence per reason. All of these read as the same rejection before,
                    // so a truncated download and a backup from a newer Hushfeed were
                    // indistinguishable to the person holding the file.
                    switch (restore.getReason()) {
                        case SIZE:
                            return "That file is too large to be a settings backup. Nothing was altered.";
                        case DAMAGED:
                            return "That settings backup is damaged or only partly downloaded. "
                                    + "Nothing was altered.";
                        case ENCODING:
                            return "That file is not readable text, so it may have been damaged in "
                                    + "transit. Nothing was altered.";
                        case FORMAT:
                            return "That is not a Hushfeed settings backup. Nothing was altered.";
                        case SCHEMA:
                            return "That backup was written by a newer Hushfeed than this one. "
                                    + "Nothing was altered.";
                        case INCOMPLETE:
                            return "That settings backup is incomplete, so it may have been cut "
                                    + "short. Nothing was altered.";
                        case VALUE:
                            return "That settings backup holds a value Hushfeed cannot read. "
                                    + "Nothing was altered.";
                        default:
                            return "The settings backup was rejected. Nothing was altered.";
                    }
                case ROLLED_BACK:
                    return "That settings change did not go through. Nothing was altered.";
                case RECOVERY_REQUIRED:
                    return restore.isRecoveryAvailable()
                            ? "Restore failed. Some settings may still be changed. Use Undo to recover."
                            : "Restore failed. Some settings may still be changed.";
                default:
                    break;
            }
        }
        if (action == UNDO && hasCause(error, java.io.FileNotFoundException.class)) {
            return "There is nothing to undo yet.";
        }
        return "Could not restore settings.";
    }

    /** Whether the throwable, or anything it wraps, is of the given kind. */
    private static boolean hasCause(Throwable error, Class<? extends Throwable> kind) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (kind.isInstance(current)) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }

    /**
     * Greys out Undo when there is nothing to undo.
     *
     * <p>The row used to be offered on a clean install, and tapping it reported that the settings
     * could not be restored, which reads as something having gone wrong rather than as there
     * being nothing there. Checked again on every bind, so a reset or a restore enables it
     * without the page being rebuilt.
     */
    private void refreshUndoAvailability() {
        if (rowAction != UNDO) return;
        boolean available = SettingsBackup.hasUndo(getContext());
        if (isEnabled() != available) setEnabled(available);
    }

    @Override protected void onBindView(View view) {
        refreshUndoAvailability();
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
