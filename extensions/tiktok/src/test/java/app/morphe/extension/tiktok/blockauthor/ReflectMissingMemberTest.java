package app.morphe.extension.tiktok.blockauthor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
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
import org.robolectric.util.ReflectionHelpers;

/**
 * A reader that returns null because TikTok renamed the member looks the same as one that
 * returned null on purpose, and the callers treat both as "no". These pin that a member the
 * caller has no fallback for is named in the report once, and that a member which is present
 * and simply returns null says nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ReflectMissingMemberTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        BaseSettings.DEBUG_LOG_FILTERS.save("errors");
        LogBufferManager.clearLogBuffer();
        clearRegistry();
    }

    @After public void tearDown() {
        LogBufferManager.clearLogBuffer();
        clearRegistry();
    }

    /** A card from a build that still answers the question. */
    public static final class KnownCard {
        public Boolean isAdOrContainAd() { return Boolean.TRUE; }
    }

    /** A card from a build that answers it with nothing to report. */
    public static final class QuietCard {
        public Boolean isAdOrContainAd() { return null; }
    }

    /** A card from a build that renamed the member away. */
    public static final class RenamedCard {
        public Boolean somethingElse() { return Boolean.TRUE; }
    }

    @Test public void aMemberThisBuildLacksIsNamedInTheReport() {
        assertNull(Reflect.required(new RenamedCard(), "isAdOrContainAd"));

        List<String> missing = Reflect.missingMembers();
        assertEquals("Expected exactly one missing member, got " + missing, 1, missing.size());
        assertTrue(missing.get(0), missing.get(0).contains("RenamedCard#isAdOrContainAd"));

        String report = LogBufferManager.buildExportText();
        assertTrue(report, report.contains("isAdOrContainAd"));
    }

    @Test public void theSameMissingMemberIsReportedOnlyOnce() {
        for (int attempt = 0; attempt < 5; attempt++) {
            Reflect.required(new RenamedCard(), "isAdOrContainAd");
        }

        assertEquals(1, Reflect.missingMembers().size());
    }

    @Test public void aMemberThatIsPresentIsNeverReported() {
        assertEquals(Boolean.TRUE, Reflect.required(new KnownCard(), "isAdOrContainAd"));
        // Present, and answering null is a real answer rather than a missing hook.
        assertNull(Reflect.required(new QuietCard(), "isAdOrContainAd"));

        assertEquals("A member that is there was reported: " + Reflect.missingMembers(),
                0, Reflect.missingMembers().size());
    }

    @Test public void theOrdinaryReadersStaySilentAboutTheShapeTheyDidNotFind() {
        // property tries a getter and then a field and expects one of them to miss, so those
        // misses must not reach the report or it fills with names that never resolve.
        Reflect.property(new KnownCard(), "getUniqueId", "uniqueId");
        Reflect.string(new KnownCard(), "getNickname", "nickname");
        Reflect.invoke(new KnownCard(), "getAuthor");
        Reflect.readField(new KnownCard(), "author");

        assertEquals("An expected miss was reported: " + Reflect.missingMembers(),
                0, Reflect.missingMembers().size());
    }

    private static void clearRegistry() {
        ReflectionHelpers.<java.util.Set<String>>getStaticField(Reflect.class, "MISSING_MEMBERS").clear();
    }
}
