package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.SettingsBackup;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Undo is offered on the Diagnostics page whether or not there is anything to undo. On a clean
 * install tapping it reported that the settings could not be restored, which reads as something
 * having broken rather than as there being nothing there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class UndoRowTest {
    private static final int RESET = 7313, UNDO = 7314;
    private Activity activity;

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        undoFile().delete();
    }

    @Test public void undoIsOfferedOnlyWhenThereIsSomethingToUndo() throws Exception {
        Preference row = undoRow();
        assertFalse("Undo is offered on a clean install", row.isEnabled());

        try (FileOutputStream output = new FileOutputStream(undoFile())) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue("the undo copy is there and the row is still greyed out",
                undoRow().isEnabled());
    }

    @Test public void anEmptyUndoSaysSoRatherThanReportingAFailure() throws Exception {
        java.lang.reflect.Method message = SettingsBackupPreference.class
                .getDeclaredMethod("failureMessage", int.class, Exception.class);
        message.setAccessible(true);

        Exception missing = new java.io.FileNotFoundException("hushfeed-settings-undo.json");
        assertTrue("an empty undo reads as a breakage",
                "There is nothing to undo yet.".equals(message.invoke(null, UNDO, missing)));
        // A reset that really failed still says so.
        assertTrue("a real failure was softened into the empty message",
                "Could not restore settings.".equals(
                        message.invoke(null, RESET, new IllegalStateException("broken"))));
    }

    /** The real Undo row, built the way the Diagnostics page builds it. */
    private Preference undoRow() {
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString("morphe_settings_section", "DIAGNOSTICS");
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        PreferenceScreen screen = fragment.getPreferenceScreen();
        Preference row = screen == null ? null : screen.findPreference("settings_backup_" + UNDO);
        if (row == null) throw new AssertionError("the Diagnostics page has no Undo row");
        return row;
    }

    private File undoFile() {
        return new File(Utils.getContext().getFilesDir(), "hushfeed-settings-undo.json");
    }
}
