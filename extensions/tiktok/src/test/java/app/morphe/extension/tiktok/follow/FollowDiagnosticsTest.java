/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A follow TikTok turns down comes back looking like a success, so the only thing that tells
 * the user is this notice. It has to fire on a real refusal and stay quiet otherwise.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FollowDiagnosticsTest {
    private boolean debug;

    public static final class ParsedResponse {
        public final Object LIZIZ;
        ParsedResponse(Object body) { this.LIZIZ = body; }
        public String LIZJ() { return "true"; }
        public int LIZ() { return 0; }
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        debug = BaseSettings.DEBUG.get();
        BaseSettings.DEBUG.save(false);
        FollowDiagnostics.resetForTests();
    }

    @org.junit.After
    public void tearDown() {
        BaseSettings.DEBUG.save(debug);
        FollowDiagnostics.resetForTests();
    }

    private static FollowDiagnostics.FollowRequestContext context() {
        return new FollowDiagnostics.FollowRequestContext(1, "/aweme/v1/commit/follow/user/");
    }

    @Test
    public void accountPseudonymsStayStableWithinOneInstallAndDifferBetweenInstalls() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        BaseSettings.DEBUG.save(true);
        java.lang.reflect.Field cached = FollowDiagnostics.class.getDeclaredField("salt");
        cached.setAccessible(true);

        try {
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
            String first = requestPseudonym("6812345678901234567");
            assertNotEquals("the salt must be made and kept", "",
                    Settings.DIAGNOSTIC_REPORT_SALT.get());

            // Same install, same id: two reports from one phone still line up.
            assertEquals(first, requestPseudonym("6812345678901234567"));

            // Another install uses a different private key for the same account.
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
            assertNotEquals(first, requestPseudonym("6812345678901234567"));

            assertEquals("empty", requestPseudonym(null));
        } finally {
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
        }
    }

    private static String requestPseudonym(String uid) throws ReflectiveOperationException {
        FollowDiagnostics.logCommonFollowRequest(1, 0, 0, 0, uid, null, null, null, null, null);
        java.lang.reflect.Field recent = FollowDiagnostics.class.getDeclaredField("recentDirectContext");
        recent.setAccessible(true);
        FollowDiagnostics.FollowRequestContext context =
                (FollowDiagnostics.FollowRequestContext) recent.get(null);
        assertNotNull("the public request hook did not retain its context", context);
        assertEquals("the request reused an earlier context",
                FollowDiagnostics.eventCountForTests(), context.id);
        return context.uidHash;
    }

    @Test
    public void aStatusCodeOtherThanZeroIsARefusal() {
        FollowDiagnostics.FollowRequestContext refused = context();
        refused.statusCode = "2098";

        assertTrue(FollowDiagnostics.followWasRefused(refused));
    }

    @Test
    public void anAcceptedFollowIsNotARefusal() {
        FollowDiagnostics.FollowRequestContext accepted = context();
        accepted.statusCode = "0";
        assertFalse(FollowDiagnostics.followWasRefused(accepted));

        // The model may hold the code as any numeric type.
        accepted.statusCode = "0.0";
        assertFalse(FollowDiagnostics.followWasRefused(accepted));

        // A response that says nothing is not a refusal either: most follows land silently.
        assertFalse(FollowDiagnostics.followWasRefused(context()));
    }

    @Test
    public void anExplicitFollowSuccessFalseIsARefusal() {
        FollowDiagnostics.FollowRequestContext refused = context();
        refused.bodyIsFollowSuccess = "false";

        assertTrue(FollowDiagnostics.followWasRefused(refused));
    }

    @Test
    public void theRefusalIsNamedOnScreenOnceAndTheReasonIsTheServersOwn() {
        ShadowToast.reset();

        FollowDiagnostics.FollowRequestContext refused = context();
        refused.statusCode = "2098";
        refused.statusMsg = "You are following too fast.";
        refused.riskCheck = "risk slide_captcha";

        FollowDiagnostics.warnAboutRefusedFollowOnce(refused);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        String message = ShadowToast.getTextOfLatestToast();
        assertTrue(String.valueOf(message), message.contains("You are following too fast."));
        // A hidden puzzle is the first thing to check when a follow does not land.
        assertTrue(String.valueOf(message), message.contains("puzzle"));

        int shown = ShadowToast.shownToastCount();
        FollowDiagnostics.warnAboutRefusedFollowOnce(refused);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(shown, ShadowToast.shownToastCount());
    }

    @Test
    public void aFollowThatLandedSaysNothing() {
        ShadowToast.reset();

        FollowDiagnostics.FollowRequestContext accepted = context();
        accepted.statusCode = "0";
        FollowDiagnostics.warnAboutRefusedFollowOnce(accepted);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void diagnosticsStopAtTheirSessionLimit() {
        BaseSettings.DEBUG.save(true);
        for (int index = 0; index < 240; index++) {
            FollowDiagnostics.logSimpleFollowRequest(1, "user-" + index, "sec-" + index);
        }
        assertEquals(160, FollowDiagnostics.eventCountForTests());
    }

    @Test
    public void concurrentDirectRequestsCannotPassTheSessionLimit() throws Exception {
        BaseSettings.DEBUG.save(true);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 8; worker++) {
                final int offset = worker * 80;
                tasks.add(executor.submit(() -> {
                    for (int index = 0; index < 80; index++) {
                        FollowDiagnostics.logSimpleFollowRequest(
                                1, "user-" + (offset + index), "sec-" + (offset + index));
                    }
                }));
            }
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(160, FollowDiagnostics.eventCountForTests());
    }

    @Test
    public void aNullContextCannotBecomeARefusalOrToast() {
        assertFalse(FollowDiagnostics.followWasRefused(null));
        FollowDiagnostics.warnAboutRefusedFollowOnce(null);
        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void aParsedServerRefusalReachesTheNoticeWithoutTrustingNestedStatus() {
        ShadowToast.reset();
        String body = "{\"data\":{\"status_code\":0},"
                + "\"status_code\":2098,\"status_msg\":\"Try again later.\"}";

        FollowDiagnostics.logParsedResponse(
                new CaptchaGateRequest("/aweme/v1/commit/follow/user/"),
                new ParsedResponse(body));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertTrue(String.valueOf(ShadowToast.getTextOfLatestToast()),
                ShadowToast.getTextOfLatestToast().contains("Try again later."));
    }

    @Test
    public void malformedServerStatusDoesNotInventARefusalNotice() {
        ShadowToast.reset();
        FollowDiagnostics.logParsedResponse(
                new CaptchaGateRequest("/aweme/v1/commit/follow/user/"),
                new ParsedResponse("{\"data\":{\"status_code\":2098},\"status_code\":\"NaN\"}"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, ShadowToast.shownToastCount());
    }

    /**
     * The path is the gate. Every request TikTok makes goes through the same hook, and a
     * refusal code on a feed or profile request is that request's business: it must not be
     * read as a follow being refused. Without this the path test could match everything and
     * the refusal notice would fire on any failed request in the app.
     */
    @Test
    public void aRefusalOnARequestThatIsNotAFollowSaysNothing() {
        ShadowToast.reset();
        String refusal = "{\"status_code\":2098,\"status_msg\":\"Try again later.\"}";
        for (String path : new String[]{
                "/aweme/v1/feed/",
                "/aweme/v1/user/profile/other/",
                "/aweme/v1/user/",
                "/aweme/v1/commit/item/digg/",
                "/tiktok/v1/relation/list/",
                "/aweme/v1/following/list/"}) {
            FollowDiagnostics.logParsedResponse(
                    new CaptchaGateRequest(path), new ParsedResponse(refusal));
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, ShadowToast.shownToastCount());
    }

    /** The write routes in the 46.2.3 fixture all reach the refusal notice. */
    @Test
    public void everyVendorFollowWriteReachesTheRefusalNotice() {
        String refusal = "{\"status_code\":2098,\"status_msg\":\"Try again later.\"}";
        for (String path : new String[]{
                "/aweme/v1/commit/follow/",
                "/aweme/v1/commit/follow/user/",
                "/aweme/v1/commit/follow/batchuser/",
                "/aweme/v1/commit/follow/request/approve/",
                "/aweme/v1/commit/follow/request/reject/",
                "/aweme/v3/f2f/follow/",
                "/aweme/v1/remove/follower/"}) {
            ShadowToast.reset();
            FollowDiagnostics.resetForTests();
            FollowDiagnostics.logParsedResponse(new CaptchaGateRequest(path), new ParsedResponse(refusal));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(path, 1, ShadowToast.shownToastCount());
            assertTrue(path, ShadowToast.getTextOfLatestToast().contains("Try again later."));
        }

        ShadowToast.reset();
        FollowDiagnostics.resetForTests();
        FollowDiagnostics.logParsedResponse(new Object(), new ParsedResponse(refusal));
        FollowDiagnostics.logParsedResponse(null, new ParsedResponse(refusal));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, ShadowToast.shownToastCount());
    }

    /**
     * A response the hook cannot read is not a refusal. The envelope is reached by
     * reflection over obfuscated names, and the day TikTok renames them the read must fail
     * quietly rather than throw into the network stack or invent a verdict.
     */
    @Test
    public void anUnreadableResponseIsNeitherARefusalNorAnException() throws Exception {
        ShadowToast.reset();
        java.lang.reflect.Method read = FollowDiagnostics.class.getDeclaredMethod(
                "readServerVerdict", Object.class, FollowDiagnostics.FollowRequestContext.class);
        read.setAccessible(true);
        for (Object response : new Object[]{new Object(), null,
                new ParsedResponse(null), new ParsedResponse("not json")}) {
            // Invoke below the network boundary's catch as well: a swallowed NPE is a
            // broken reader even when the hook correctly protects TikTok from the exception.
            FollowDiagnostics.FollowRequestContext verdict = context();
            read.invoke(null, response, verdict);
            assertEquals(FollowVerdict.UNKNOWN, verdict.statusCode);
            assertFalse(FollowDiagnostics.followWasRefused(verdict));
            FollowDiagnostics.logParsedResponse(
                    new CaptchaGateRequest("/aweme/v1/commit/follow/user/"), response);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals(0, ShadowToast.shownToastCount());
    }

    @Test
    public void parsedResponsesStopRetainingRequestsAtTheLimitButStillReportRefusals() throws Exception {
        BaseSettings.DEBUG.save(true);
        ShadowToast.reset();
        for (int index = 0; index < 240; index++) {
            FollowDiagnostics.logParsedResponse(
                    new CaptchaGateRequest("/aweme/v1/commit/follow/user/"),
                    new ParsedResponse("{\"status_code\":0}"));
        }
        assertEquals(160, FollowDiagnostics.eventCountForTests());
        java.lang.reflect.Field contexts = FollowDiagnostics.class.getDeclaredField("networkContexts");
        contexts.setAccessible(true);
        assertEquals(160, ((java.util.Map<?, ?>) contexts.get(null)).size());
        assertEquals(0, ShadowToast.shownToastCount());

        FollowDiagnostics.logParsedResponse(
                new CaptchaGateRequest("/aweme/v1/commit/follow/user/"),
                new ParsedResponse("{\"status_code\":2098,\"status_msg\":\"Try again later.\"}"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ShadowToast.getTextOfLatestToast().contains("Try again later."));
        assertEquals(160, FollowDiagnostics.eventCountForTests());
        assertEquals(160, ((java.util.Map<?, ?>) contexts.get(null)).size());
    }

    public static final class CaptchaGateRequest {
        private final String path;
        CaptchaGateRequest(String path) { this.path = path; }
        public String getPath() { return path; }
    }
}
