package app.morphe.extension.tiktok.externalbrowser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExternalBrowserPatchTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.OPEN_EXTERNAL_LINKS.save(true);
    }

    @After public void tearDown() {
        Settings.OPEN_EXTERNAL_LINKS.save(false);
    }

    @Test public void resolverAcceptsPlainHttpsTargetsAndKnownWrappers() {
        assertEquals("https://example.com/path",
                ExternalBrowserPatch.resolveTarget("example.com/path").toString());
        assertEquals("https://example.com/path",
                ExternalBrowserPatch.resolveTarget(
                        "aweme://webview?url=https%3A%2F%2Fexample.com%2Fpath").toString());
        assertEquals("https://example.com/path",
                ExternalBrowserPatch.resolveTarget(
                        "https://redirect.example/?target=https%3A%2F%2Fexample.com%2Fpath").toString());
    }

    @Test public void resolverRefusesNonWebSchemesMissingHostsAndWrapperLoops() {
        assertNull(ExternalBrowserPatch.resolveTarget("file:///sdcard/private.txt"));
        assertNull(ExternalBrowserPatch.resolveTarget("javascript:alert(1)"));
        assertNull(ExternalBrowserPatch.resolveTarget("https:///path"));
        assertNull(ExternalBrowserPatch.resolveTarget(
                "aweme://webview?url=aweme%3A%2F%2Fwebview%3Furl%3Daweme%253A%252F%252Fwebview"));
    }

    @Test public void publicHooksRejectDisallowedScreensBeforeOpeningAService() {
        assertFalse(ExternalBrowserPatch.openSparkThirdContext(null, new Object()));
        assertFalse(ExternalBrowserPatch.openStoryLink(new Object(), new Object()));
    }
}
