package app.morphe.extension.tiktok.featuregatelab;

import static org.junit.Assert.*;

import android.os.Looper;
import app.morphe.extension.shared.Utils;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GeneratedCatalogShapeTest {
    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        FeatureGateCatalog.awaitForTests();
        FeatureGateCatalog.resetForTests();
    }

    @After public void tearDown() throws Exception {
        FeatureGateCatalog.awaitForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        FeatureGateCatalog.resetForTests();
    }

    @Test public void theGeneratedAbLiveAndPiaRowsRetainTheirTypedSchema() throws Exception {
        typedRows(GeneratedFeatureGateCatalog.GZIP_BASE64, GeneratedFeatureGateCatalog.ENTRY_COUNT,
                Set.of("abmock", "live", "pia_activity_center"));
    }

    @Test public void theGeneratedPlayerRowsRetainTheirTypedSchema() throws Exception {
        typedRows(GeneratedPlayerFeatureGateCatalog.GZIP_BASE64,
                GeneratedPlayerFeatureGateCatalog.ENTRY_COUNT, Set.of("player_config"));
    }

    @Test public void theGeneratedVeRowsRetainTheirTypedSchema() throws Exception {
        typedRows(GeneratedVeFeatureGateCatalog.GZIP_BASE64,
                GeneratedVeFeatureGateCatalog.ENTRY_COUNT, Set.of("ve_config"));
    }

    @Test public void theGeneratedSettingsRowsRetainTheirClassAndDefaultShapes() throws Exception {
        List<String[]> rows = decode(GeneratedSettingsManagerCatalog.GZIP_BASE64,
                GeneratedSettingsManagerCatalog.ENTRY_COUNT, 6);
        for (String[] row : rows) {
            assertFalse(row[0], row[1].isEmpty());
            assertFalse(row[0], row[2].isEmpty());
            JSONTokener json = new JSONTokener(row[2]);
            assertNotNull(row[0], json.nextValue());
            assertEquals("trailing default data for " + row[0], 0, json.nextClean());
            assertTrue(row[0], row[3].contains("->"));
            assertTrue(row[0], row[4].matches("classes[0-9]*\\.dex"));
            assertTrue(row[0] + ": " + row[5],
                    Set.of("generated_registry", "call_site_only").contains(row[5]));
        }
    }

    @Test public void loadingTheRealCatalogPublishesEntriesFromAllFourBundles() throws Exception {
        List<FeatureGateCatalog.Snapshot> delivered = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        FeatureGateCatalog.loadAsync(false, new FeatureGateCatalog.Callback() {
            @Override public void onLoaded(FeatureGateCatalog.Snapshot snapshot) { delivered.add(snapshot); }
            @Override public void onError(String message) { errors.add(message); }
        });
        FeatureGateCatalog.awaitForTests();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(), errors);
        assertFalse("the loader delivered no catalog", delivered.isEmpty());
        FeatureGateCatalog.Snapshot snapshot = delivered.get(delivered.size() - 1);
        assertTrue("the loader stopped at its current-values preview", snapshot.catalogComplete);
        assertSame(snapshot, FeatureGateCatalog.cachedSnapshot());

        entry(snapshot, "abmock", "1005_max_limit_count_daily", "INT", "5");
        entry(snapshot, "player_config", "AWEDanmakuSupportMask", "BOOLEAN", "false");
        entry(snapshot, "ve_config", "aeabtest_v2api", "BOOLEAN", "false");
        FeatureGateCatalog.Entry array = entry(snapshot, "settings_manager", "ad_gaid_whitelist",
                "OBJECT", "[\"analytics.us.tiktok.com\"]");
        assertEquals(String[].class.getName(), array.requestedClass);
        assertTrue(array.registered);
        assertTrue(array.proof, array.proof.contains(" in classes"));
        assertEquals(GeneratedPlayerFeatureGateCatalog.ENTRY_COUNT,
                snapshot.entries.stream().filter(item -> "player_config".equals(item.manager)).count());
        assertEquals(GeneratedVeFeatureGateCatalog.ENTRY_COUNT,
                snapshot.entries.stream().filter(item -> "ve_config".equals(item.manager)).count());
    }

    private static FeatureGateCatalog.Entry entry(FeatureGateCatalog.Snapshot snapshot,
            String manager, String key, String type, String defaultValue) {
        FeatureGateCatalog.Entry entry = snapshot.byIdentity.get(manager + "\n" + key);
        assertNotNull("the loader omitted " + manager + "/" + key, entry);
        assertEquals(type, entry.type);
        assertEquals(List.of(defaultValue), entry.defaults);
        assertTrue(entry.userVisible());
        return entry;
    }

    private static void typedRows(String[] chunks, int count, Set<String> managers) throws Exception {
        Set<String> types = Set.of("BOOLEAN", "INT", "LONG", "FLOAT", "DOUBLE", "STRING");
        for (String[] row : decode(chunks, count, 10)) {
            assertTrue(row[0] + ": " + row[1], managers.contains(row[1]));
            assertTrue(row[0] + ": " + row[2], types.contains(row[2]));
            assertTrue(row[0], Set.of("0", "1").contains(row[3]));
            assertTrue(row[0], Set.of("0", "1").contains(row[4]));
            for (int column = 5; column <= 7; column++) new JSONArray(row[column]);
            assertFalse("missing provenance for " + row[0], row[9].isEmpty());
        }
    }

    private static List<String[]> decode(String[] chunks, int count, int width) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(String.join("", chunks));
        List<String[]> rows = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(bytes)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split("\t", -1);
                assertEquals("malformed row " + rows.size(), width, row.length);
                assertFalse("empty gate name", row[0].isEmpty());
                assertTrue("duplicate gate " + row[0], identities.add(row[0] + "\n" + row[1]));
                rows.add(row);
            }
        }
        assertEquals("declared count does not match the compressed rows", count, rows.size());
        assertTrue("an empty catalog proves no schema", count > 0);
        return rows;
    }
}
