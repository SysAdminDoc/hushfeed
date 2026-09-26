/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
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
    /** The four rows on the page right now, so a run can take them all out of reach. */
    private static final java.util.List<java.lang.ref.WeakReference<SettingsBackupPreference>> ROWS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Reset and Undo act on the tap. Back up and Restore open a file picker first. */
    @Override public boolean actsOnTap() {
        return rowAction == RESET || rowAction == UNDO;
    }

    private final int rowAction;
    /** What this row says when nothing is running, so the run can hand it back. */
    private final String restingSummary;
    /** The line this row shows while it is the one running, else null. */
    private String busyLine;

    static final String UNDO_SUMMARY = "Put back the settings saved before the last restore or reset.";

    private SettingsBackupPreference(TikTokPreferenceFragment fragment, int action, String title, String summary) {
        super(fragment.getActivity());
        this.rowAction = action;
        this.restingSummary = summary;
        setKey("settings_backup_" + action);
        setTitle(title);
        setSummary(summary);
        refreshUndoAvailability();
        setOnPreferenceClickListener(preference -> {
            // The rows are disabled while a run is going, so this is the race between a tap and
            // the disable rather than the ordinary second tap it used to refuse out loud.
            if (BUSY.get()) return true;
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
                        "Save Hushfeed settings and Feature Gate Lab rules to a JSON file."},
                {IMPORT, "Restore settings",
                        "Choose a backup file. Your current settings are kept for Undo."},
                {RESET, "Reset settings",
                        "Put every setting back to its default straight away. Your current settings are kept for Undo."},
                {UNDO, "Undo last restore or reset", UNDO_SUMMARY},
        }) {
            SettingsBackupPreference preference = new SettingsBackupPreference(
                    fragment, (Integer) row[0], (String) row[1], (String) row[2]);
            preference.setOrder(order++);
            screen.addPreference(preference);
            ROWS.add(new java.lang.ref.WeakReference<>(preference));
        }
    }

    /**
     * Takes the four rows out of reach while one of them runs, and puts the running line on the
     * row that is acting.
     *
     * <p>Called with a zero action and a null line when the run ends, which re-enables them and
     * gives every row its own summary back. A screen reader hears the row as disabled and reads
     * the line as its state, which is the treatment Inbox Clear all already had.
     */
    static void setRowsBusy(int action, String running) {
        for (java.lang.ref.WeakReference<SettingsBackupPreference> held : ROWS) {
            SettingsBackupPreference row = held.get();
            if (row == null) {
                ROWS.remove(held);
                continue;
            }
            if (running == null) {
                row.busyLine = null;
                row.setEnabled(true);
                row.refreshUndoAvailability();
                row.setSummary(row.restingSummary);
                continue;
            }
            row.busyLine = row.rowAction == action ? running : null;
            row.setEnabled(false);
            if (row.busyLine != null) row.setSummaryDirect(running);
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
                    "This phone has no file picker, so there's no way to choose a file here"));
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
        String running = L10n.t(action == EXPORT ? "Saving settings backup"
                : action == IMPORT ? "Restoring your settings"
                : action == RESET ? "Putting the settings back to their defaults"
                : "Undoing the last change");
        Utils.showToastShort(running);
        // A restore of a large file or a reset takes long enough to notice, and the rows used
        // to look exactly as they did before, with a second tap earning a refusal. The acting
        // row now says what is happening and all four are out of reach until it is done.
        setRowsBusy(action, running);
        boolean accepted = Utils.runOnBackgroundThread(() -> {
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
                            "%1$d setting wasn't in that file and was left as it is", keptAsTheyWere));
                } else if (keptAsTheyWere > 1) {
                    Utils.showToastLong(L10n.f(
                            "%1$d settings weren't in that file and were left as they are", keptAsTheyWere));
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
                                ? "Last change put back. The Feature Gate Lab rules were for another TikTok version and were left out. Restart TikTok to apply all changes."
                                : "Last change put back. Restart TikTok to apply all changes."));
            } catch (Exception error) {
                Logger.printException(() -> "Settings backup operation failed", error);
                Utils.showToastLong(L10n.t(failureMessage(action, error)));
            } finally {
                Utils.runOnMainThread(() -> {
                    if (action != EXPORT) AbstractPreferenceFragment.settingImportInProgress = false;
                    BUSY.set(false);
                    setRowsBusy(0, null);
                    TikTokPreferenceFragment current = owner.get();
                    if (current != null && current.isAdded()) current.refreshBackupSettings();
                });
            }
        });
        if (!accepted) {
            if (action != EXPORT) AbstractPreferenceFragment.settingImportInProgress = false;
            BUSY.set(false);
            setRowsBusy(0, null);
            Utils.showToastLong(L10n.t(
                    "Couldn't start the settings change. Try again in a moment."));
            TikTokPreferenceFragment current = owner.get();
            if (current != null && current.isAdded()) current.refreshBackupSettings();
        }
    }

    static String failureMessage(int action, Exception error) {
        if (action == EXPORT) return "Couldn't save the settings backup. Try again.";
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
                            return "That file isn't readable text, so it may have been damaged in "
                                    + "transit. Nothing was altered.";
                        case FORMAT:
                            return "That isn't a Hushfeed settings backup. Nothing was altered.";
                        case SCHEMA:
                            return "That backup was written by a newer Hushfeed than this one. "
                                    + "Nothing was altered.";
                        case INCOMPLETE:
                            return "That settings backup is incomplete, so it may have been cut "
                                    + "short. Nothing was altered.";
                        case VALUE:
                            return "That settings backup holds a value Hushfeed can't read. "
                                    + "Nothing was altered.";
                        case RULE_LIST:
                            return "That settings backup contains a feed rule list larger than "
                                    + "Hushfeed accepts. Nothing was altered.";
                        case LAB_RULES:
                            return "That settings backup holds more Feature Gate Lab rules than "
                                    + "the Lab takes. Nothing was altered.";
                        default:
                            return "The settings backup was rejected. Nothing was altered.";
                    }
                case ROLLED_BACK:
                    return "That settings change didn't go through. Nothing was altered.";
                case RECOVERY_REQUIRED:
                    return restore.isRecoveryAvailable()
                            ? "Restore failed. Some settings may still be changed. Use Undo to put them back."
                            : "Restore failed. Some settings may still be changed.";
                default:
                    break;
            }
        }
        if (action == UNDO && hasCause(error, java.io.FileNotFoundException.class)) {
            return "Nothing to undo yet.";
        }
        return "Couldn't restore the settings. Try again.";
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
        // Nothing overrides a run in progress: the row is disabled and saying what it is doing.
        if (busyLine != null || rowAction != UNDO) return;
        boolean available = SettingsBackup.hasUndo(getContext());
        if (isEnabled() != available) setEnabled(available);
        // The one greyed row on the page, and its summary went on offering a recovery. A
        // screen reader announces "disabled" with no reason; this is the reason.
        setSummary(available ? UNDO_SUMMARY : "Nothing to undo yet.");
    }

    @Override protected void onBindView(View view) {
        refreshUndoAvailability();
        if (android.os.Build.VERSION.SDK_INT >= 30 && view != null) {
            view.setStateDescription(busyLine);
        }
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

    /** Text that has already been translated, so the lookup above would find nothing. */
    private void setSummaryDirect(CharSequence summary) {
        super.setSummary(summary);
    }
}
