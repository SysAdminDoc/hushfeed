package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GateReportExportTest {
    @Test public void multiMegabyteReportSavesInFullAndNeverReachesClipboard() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        String small = "{\"gates\":[]}";
        ShadowToast.reset();
        assertTrue(GateReportExport.copy(context, small));
        assertEquals("Feature gate report copied", String.valueOf(ShadowToast.getTextOfLatestToast()));
        String large = "{\"value\":\"" + "Caption \uD83C\uDF0D ".repeat(200000) + "\"}";
        ShadowToast.reset();
        assertFalse(GateReportExport.copy(context, large));
        assertEquals("Use Save JSON for this large report",
                String.valueOf(ShadowToast.getTextOfLatestToast()));
        var clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("Feature gate recording", clip.getDescription().getLabel());
        assertEquals(small, clip.getItemAt(0).getText());
        assertTrue(clip.getDescription().getExtras()
                .getBoolean("android.content.extra.IS_SENSITIVE"));
        File saved = new File(GateReportExport.write(context, large));
        try { assertEquals(large, new String(Files.readAllBytes(saved.toPath()), java.nio.charset.StandardCharsets.UTF_8)); }
        finally { assertTrue(saved.delete()); }
    }

    @Test
    @Config(sdk = {23, 35})
    public void emptyReportUsesSharedSensitiveClipboardHandlingWhereSupported() {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        ShadowToast.reset();

        assertTrue(GateReportExport.copy(context, ""));

        ClipboardManager clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull(clip);
        assertEquals("Feature gate recording", clip.getDescription().getLabel());
        assertEquals("", clip.getItemAt(0).getText());
        if (Build.VERSION.SDK_INT >= 24) {
            assertNotNull(clip.getDescription().getExtras());
            assertTrue(clip.getDescription().getExtras()
                    .getBoolean("android.content.extra.IS_SENSITIVE"));
        }
        assertEquals("Feature gate report copied",
                String.valueOf(ShadowToast.getTextOfLatestToast()));
    }

    @Test public void aFullPoolSaysTheExportCouldNotStart() throws Exception {
        // Save JSON used to hand the write to a pool that could refuse it and say nothing: the
        // reader saw neither the saved path nor the failure sentence.
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        ShadowToast.reset();
        try (var saturation = app.morphe.extension.shared.BackgroundPoolSaturation.fill()) {
            GateReportExport.save(context, "{}");
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("Couldn't start the report export. Try again in a moment.",
                    String.valueOf(ShadowToast.getTextOfLatestToast()));
        }
    }

    @Test public void theSavedNameReadsAsADateInAHushfeedFolder() throws Exception {
        // Two reports a second apart get names a reader can tell apart, the way the settings
        // backup is named, and the folder is the one a file manager shows.
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        assertTrue(GateReportExport.fileName(), GateReportExport.fileName()
                .matches("hushfeed-gate-report-\\d{8}-\\d{6}\\.json"));
        assertEquals("Download/Hushfeed", GateReportExport.FOLDER);
        File first = new File(GateReportExport.write(context, "{\"gates\":[]}"));
        File second = new File(GateReportExport.write(context, "{\"gates\":[]}"));
        try {
            assertTrue(first.getName(), first.getName().startsWith("hushfeed-gate-report-"));
            assertNotEquals("two reports in one second overwrote each other", first, second);
        } finally {
            assertTrue(first.delete());
            assertTrue(second.delete());
        }
    }

    @Test public void clipboardFailureIsReportedWithoutCrashing() {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        Context denied = new ContextWrapper(context) {
            @Override public Object getSystemService(String service) { throw new SecurityException("Clipboard denied"); }
        };
        ShadowToast.reset();
        assertFalse(GateReportExport.copy(denied, "{}"));
        assertEquals("Couldn't copy the report. Use Save JSON instead.",
                String.valueOf(ShadowToast.getTextOfLatestToast()));
    }
}
