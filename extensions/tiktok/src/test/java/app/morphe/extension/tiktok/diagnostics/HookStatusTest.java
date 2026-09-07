package app.morphe.extension.tiktok.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.DiagnosticCategory;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.preference.LogBufferManager;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A hook that no longer finds its anchor fails quietly: the switch still reads on and nothing
 * happens. The Diagnostics row and the exported report both read this registry, so what it says
 * about a surface is the only thing standing between a broken build and a bug report about a
 * feature that never ran.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class HookStatusTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        BaseSettings.DEBUG_LOG_FILTERS.resetToDefault();
        HookStatus.clear();
        LogBufferManager.clearLogBuffer();
    }

    @After public void tearDown() {
        BaseSettings.DEBUG_LOG_FILTERS.resetToDefault();
        HookStatus.clear();
        LogBufferManager.clearLogBuffer();
    }

    @Test public void aSurfaceThatFoundEverythingSaysSo() {
        HookStatus.bound("comments", "like_button");
        HookStatus.bound("comments", "dislike_button");

        assertFalse("a surface with no miss was called broken", HookStatus.anyMissing());
        assertEquals(List.of("comments: 2 bound, 0 unbound"), HookStatus.report());
    }

    @Test public void theSameLookupTwiceCountsOnce() {
        HookStatus.bound("inbox", "row");
        HookStatus.bound("inbox", "row");
        HookStatus.missingViewId("inbox", "avatar");
        HookStatus.missingViewId("inbox", "avatar");

        // Both run on every bind, so counting repeats would report thousands within a scroll.
        assertEquals(List.of("inbox: 1 bound, 1 unbound; first miss: view id 'avatar'"),
                HookStatus.report());
    }

    @Test public void theLineNamesTheFirstThingThatWentMissing() {
        HookStatus.missingViewId("share sheet", "action_row");
        HookStatus.missingViewId("share sheet", "action_label");

        assertTrue(HookStatus.anyMissing());
        assertEquals(List.of("share sheet: 0 bound, 2 unbound; first miss: view id 'action_row'"),
                HookStatus.report());
        assertEquals(List.of("share sheet"), HookStatus.familiesMissingSomething());
    }

    @Test public void eachSurfaceIsCountedOnItsOwn() {
        HookStatus.bound("overlay", "cover");
        HookStatus.missingMember("feed models", "method", "com.example.Card", "isAdOrContainAd");
        HookStatus.missingViewId("comments", "dislike_button");

        assertEquals(
                List.of("overlay: 1 bound, 0 unbound",
                        "feed models: 0 bound, 1 unbound; "
                                + "first miss: method com.example.Card#isAdOrContainAd",
                        "comments: 0 bound, 1 unbound; first miss: view id 'dislike_button'"),
                HookStatus.report());
        assertEquals(List.of("feed models", "comments"), HookStatus.familiesMissingSomething());
    }

    @Test public void aSurfaceNobodyHasTouchedIsNotCalledBroken() {
        // A hook that has not run yet has not failed, so an empty registry reports nothing at
        // all rather than a wall of surfaces sitting at zero.
        assertEquals(List.of(), HookStatus.report());
        assertFalse(HookStatus.anyMissing());
    }

    @Test public void aFamilyPastItsCapSaysItStoppedCounting() {
        // Otherwise a build where everything came apart reads exactly like one with a round
        // number of misses, and the log goes quiet at the same moment.
        for (int miss = 0; miss < 260; miss++) HookStatus.missingViewId("overlay", "id_" + miss);

        String line = HookStatus.report().get(0);
        assertTrue("the report hid the truncation: " + line,
                line.contains("and more it stopped counting"));
        assertEquals(200, HookStatus.missing("overlay").size());
    }

    @Test public void aMissReachesTheExportedReport() {
        HookStatus.bound("comments", "like_button");
        HookStatus.missingViewId("comments", "dislike_button");

        String report = LogBufferManager.buildExportText();
        assertTrue("the export carried no hook table: " + report, report.contains("[HOOK STATUS]"));
        assertTrue("the export did not name the surface: " + report,
                report.contains("comments: 1 bound, 1 unbound; first miss: view id 'dislike_button'"));
    }

    @Test public void theTableFollowsTheIncludedDiagnosticsChoice() {
        // Misses are buffered as PATCH_ERRORS events, so a reader who deselected those and gets
        // the table anyway has been given something they asked not to have.
        BaseSettings.DEBUG_LOG_FILTERS.save("downloads");
        HookStatus.missingViewId("comments", "dislike_button");
        LogBufferManager.appendEvent(DiagnosticCategory.DOWNLOADS, "Test", "INFO", "a download");

        String report = LogBufferManager.buildExportText();
        assertTrue("nothing was exported at all: " + report, report.contains("a download"));
        assertFalse("the hook table ignored the filter: " + report,
                report.contains("[HOOK STATUS]"));
    }

    @Test public void theTableIsRedactedLikeEverythingElseInTheReport() {
        // Every other section goes through the redactor. This one carries names read out of the
        // running app, and the next one added may not be a literal.
        HookStatus.missingViewId("share sheet",
                "https://api16-normal.tiktokv.com/x?sessionid=secret123");

        String report = LogBufferManager.buildExportText();
        assertTrue("the export carried no hook table: " + report, report.contains("[HOOK STATUS]"));
        assertFalse("a credential went out in the hook table: " + report,
                report.contains("secret123"));
    }

    @Test public void clearingTheDiagnosticDataClearsTheHookTable() {
        HookStatus.bound("overlay", "cover");
        HookStatus.missingViewId("overlay", "gone");

        LogBufferManager.clearLogBuffer();

        assertEquals("the table survived being cleared", List.of(), HookStatus.report());
        assertFalse(HookStatus.anyMissing());
    }

    @Test public void aReportWithOnlyHookStatusInItIsStillEmpty() {
        // A family exists from the first layout pass onward, so if the table counted towards a
        // report having content, "No matching Morphe diagnostics found" could never be said
        // again and Clear diagnostic data would look as though it had done nothing.
        HookStatus.bound("overlay", "cover");

        assertEquals("a report was built out of nothing but hook status", "",
                LogBufferManager.buildExportText());
    }

    @Test public void anEmptyRegistryExportsNothing() {
        assertEquals("a report was built with nothing to put in it", "",
                LogBufferManager.buildExportText());
    }
}
