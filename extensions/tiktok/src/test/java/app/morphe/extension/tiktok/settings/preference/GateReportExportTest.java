package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.*;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GateReportExportTest {
    @Test public void multiMegabyteReportSavesInFullAndNeverReachesClipboard() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        String small = "{\"gates\":[]}";
        assertTrue(GateReportExport.copy(context, small));
        String large = "{\"value\":\"" + "Caption \uD83C\uDF0D ".repeat(200000) + "\"}";
        assertFalse(GateReportExport.copy(context, large));
        var clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals(small, clipboard.getPrimaryClip().getItemAt(0).getText());
        File saved = new File(GateReportExport.write(context, large));
        try { assertEquals(large, new String(Files.readAllBytes(saved.toPath()), java.nio.charset.StandardCharsets.UTF_8)); }
        finally { assertTrue(saved.delete()); }
    }
    @Test public void clipboardFailureIsReportedWithoutCrashing() {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        Context denied = new ContextWrapper(context) {
            @Override public Object getSystemService(String service) { throw new SecurityException("Clipboard denied"); }
        };
        assertFalse(GateReportExport.copy(denied, "{}"));
    }
}
