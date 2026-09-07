package app.morphe.extension.tiktok.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
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
        HookStatus.resetForTests();
        LogBufferManager.clearLogBuffer();
    }

    @After public void tearDown() {
        HookStatus.resetForTests();
        LogBufferManager.clearLogBuffer();
    }

    @Test public void aSurfaceThatFoundEverythingSaysSo() {
        HookStatus.bound("comments", "view id 'like_button'");
        HookStatus.bound("comments", "view id 'dislike_button'");

        assertFalse("a surface with no miss was called broken", HookStatus.anyMissing());
        assertEquals(List.of("comments: 2 bound, 0 unbound"), HookStatus.report());
    }

    @Test public void theSameLookupTwiceCountsOnce() {
        HookStatus.bound("inbox", "view id 'row'");
        HookStatus.bound("inbox", "view id 'row'");
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
    }

    @Test public void eachSurfaceIsCountedOnItsOwn() {
        HookStatus.bound("overlay", "view id 'cover'");
        HookStatus.missingMember("feed models", "method", "com.example.Card", "isAdOrContainAd");
        HookStatus.missingView("comments", "dislike_button");

        assertEquals(
                List.of("overlay: 1 bound, 0 unbound",
                        "feed models: 0 bound, 1 unbound; "
                                + "first miss: method com.example.Card#isAdOrContainAd",
                        "comments: 0 bound, 1 unbound; first miss: view 'dislike_button'"),
                HookStatus.report());
    }

    @Test public void aSurfaceNobodyHasTouchedIsNotCalledBroken() {
        // A hook that has not run yet has not failed, so an empty registry reports nothing at
        // all rather than a wall of surfaces sitting at zero.
        assertEquals(List.of(), HookStatus.report());
        assertFalse(HookStatus.anyMissing());
    }

    @Test public void aMissReachesTheExportedReport() {
        HookStatus.bound("comments", "view id 'like_button'");
        HookStatus.missingViewId("comments", "dislike_button");

        String report = LogBufferManager.buildExportText();
        assertTrue("the export carried no hook table: " + report, report.contains("[HOOK STATUS]"));
        assertTrue("the export did not name the surface: " + report,
                report.contains("comments: 1 bound, 1 unbound; first miss: view id 'dislike_button'"));
    }

    @Test public void aStatusOnlyReportIsStillWorthExporting() {
        // Nothing crashed and no event was buffered, but which hooks bound is the whole question
        // someone opens this screen to answer.
        HookStatus.bound("overlay", "view id 'cover'");
        LogBufferManager.clearLogBuffer();

        String report = LogBufferManager.buildExportText();
        assertTrue("an all-bound report came back empty", report.contains("[HOOK STATUS]"));
        assertTrue(report.contains("overlay: 1 bound, 0 unbound"));
    }

    @Test public void anEmptyRegistryExportsNothing() {
        assertEquals("a report was built with nothing to put in it", "",
                LogBufferManager.buildExportText());
    }
}
