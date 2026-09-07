package app.morphe.extension.shared.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Environment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class LogBufferManagerExportTest {
    @Test public void repeatedLegacyExportsUseTheRealUniqueDocumentsPaths() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String report = "MORPHE DIAGNOSTIC REPORT\nschema: 1\n";
        String first = LogBufferManager.writeToFile(context, report);
        String second = LogBufferManager.writeToFile(context, report);
        File firstFile = new File(first);
        File secondFile = new File(second);
        try {
            File documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            assertTrue(documents != null);
            assertTrue(first.startsWith(documents.getAbsolutePath()));
            assertTrue(second.startsWith(documents.getAbsolutePath()));
            assertNotEquals(first, second);
            assertEquals(report, new String(Files.readAllBytes(firstFile.toPath()), StandardCharsets.UTF_8));
            assertEquals(report, new String(Files.readAllBytes(secondFile.toPath()), StandardCharsets.UTF_8));
        } finally {
            firstFile.delete();
            secondFile.delete();
        }
    }
}
