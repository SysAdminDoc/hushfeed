package app.morphe.extension.tiktok.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.DiagnosticCategory;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.preference.LogBufferManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class LogBufferRedactionTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        BaseSettings.DEBUG_LOG_FILTERS.save("downloads");
        LogBufferManager.clearLogBuffer();
    }

    @After public void tearDown() {
        LogBufferManager.clearLogBuffer();
    }

    @Test public void selectedEventsAreRedactedAtExport() {
        LogBufferManager.appendEvent(
                DiagnosticCategory.DOWNLOADS,
                "RemoteMedia",
                "ERROR",
                "GET https://api16-normal-c-useast1a.tiktokv.com/feed?sessionid=secret123");

        String report = LogBufferManager.buildExportText();

        assertTrue(report.contains("RemoteMedia"));
        assertTrue(report.contains("[url omitted]"));
        assertFalse(report.contains("tiktokv.com"));
        assertFalse(report.contains("secret123"));
    }

    @Test public void persistedCrashTextIsRedactedAlongsideEvents() throws Exception {
        LogBufferManager.persistCrashReport(
                context,
                "Exception: https://api16.tiktokv.com/feed?token=crash-secret");
        LogBufferManager.appendEvent(
                DiagnosticCategory.DOWNLOADS,
                "RemoteMedia",
                "ERROR",
                "sessionid=event-secret");

        String report = LogBufferManager.buildExportText();

        assertTrue(report.contains("[LATEST JAVA CRASH]"));
        assertTrue(report.contains("[SELECTED EVENTS]"));
        assertFalse(report.contains("crash-secret"));
        assertFalse(report.contains("event-secret"));
        assertFalse(report.contains("tiktokv.com"));
    }

    @Test public void diagnosticClipboardContentIsMarkedSensitive() {
        LogBufferManager.appendEvent(
                DiagnosticCategory.DOWNLOADS,
                "RemoteMedia",
                "INFO",
                "download complete");

        LogBufferManager.exportToClipboard();

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        assertTrue(clip != null);
        assertTrue(clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"));
    }
}
