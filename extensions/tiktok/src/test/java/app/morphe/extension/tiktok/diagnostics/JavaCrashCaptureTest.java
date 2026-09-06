package app.morphe.extension.tiktok.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** What a crash report is allowed to carry once it leaves the device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class JavaCrashCaptureTest {
    /** A request that failed mid-flight, which is how a URL gets into an exception message. */
    private static final String URL =
            "https://api16-normal-c-useast1a.tiktokv.com/aweme/v1/feed/?device_id=6812&sessionid=abc123";

    @Test public void reportsKeepAddressesAndCredentialsOutOfTheText() {
        String redacted = JavaCrashCapture.redact("unexpected end of stream on " + URL);
        assertFalse(redacted.contains("tiktokv.com"));
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("6812"));
        assertTrue(redacted.contains("[url omitted]"));

        // A bare credential, with no address around it, still goes.
        String bare = JavaCrashCapture.redact("Auth failed sessionid=abc123 sid_tt=deadbeef");
        assertFalse(bare.contains("abc123"));
        assertFalse(bare.contains("deadbeef"));
        assertTrue(bare.contains("sessionid=[omitted]"));

        assertEquals("", JavaCrashCapture.redact(null));
        assertEquals("nothing to hide", JavaCrashCapture.redact("nothing to hide"));
    }

    @Test public void theMessagesAnAndroidNetworkFailureActuallyProducesAreRedacted() {
        // Neither of the two commonest ones carries a scheme, which is what a pattern anchored
        // on https:// misses.
        String unknownHost = JavaCrashCapture.redact(
                "java.net.UnknownHostException: Unable to resolve host "
                        + "\"api16-normal-c-useast1a.tiktokv.com\": No address associated with hostname");
        assertFalse(unknownHost, unknownHost.contains("tiktokv.com"));

        String refused = JavaCrashCapture.redact(
                "ConnectException: failed to connect to api16-normal.tiktokv.com/13.32.1.5 "
                        + "(port 443) after 15000ms");
        assertFalse(refused, refused.contains("tiktokv.com"));
    }

    @Test public void aCredentialDoesNotShieldTheOnesAfterIt() {
        // Taking everything up to the next space swallows the delimiter, and then every later
        // value on the line rides out inside the match.
        String cookies = JavaCrashCapture.redact(
                "Cookie: sessionid=abc123; msToken=SECRETVALUE; ttwid=1%7Cxyz");
        assertFalse(cookies, cookies.contains("abc123"));
        assertFalse(cookies, cookies.contains("SECRETVALUE"));
        assertFalse(cookies, cookies.contains("1%7Cxyz"));

        // A name the first version did not list, and a header rather than a query parameter.
        String others = JavaCrashCapture.redact("sessionid_ss=abc123 odin_tt=deadbeef");
        assertFalse(others, others.contains("abc123"));
        assertFalse(others, others.contains("deadbeef"));
        assertFalse(JavaCrashCapture.redact("X-Tt-Token: 03deadbeefcafe").contains("03deadbeefcafe"));

        // Ordinary text is left readable, or the report stops being worth reading.
        assertEquals("Attempt to invoke virtual method on a null object reference",
                JavaCrashCapture.redact(
                        "Attempt to invoke virtual method on a null object reference"));
    }

    @Test public void aJavaCrashReportRedactsItsMessageAndItsStack() throws Exception {
        Throwable thrown = new IOException("unexpected end of stream on " + URL);
        Method build = JavaCrashCapture.class.getDeclaredMethod(
                "buildReport", android.content.Context.class, Thread.class, Throwable.class,
                Thread.UncaughtExceptionHandler.class);
        build.setAccessible(true);
        String report = (String) build.invoke(null,
                org.robolectric.RuntimeEnvironment.getApplication(),
                Thread.currentThread(), thrown, null);

        // The message line and the stack trace are both in there, and neither carries the URL.
        assertTrue(report.contains("[STACK TRACE]"));
        assertTrue(report.contains("JavaCrashCaptureTest"));
        assertFalse("the message must not carry the address", report.contains("tiktokv.com"));
        assertFalse("nor the session", report.contains("abc123"));
        assertTrue(report.contains("[url omitted]"));
    }
}
