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
    public void aHashedAccountIdCannotBeCheckedAgainstAGuess() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        java.lang.reflect.Method hash =
                FollowDiagnostics.class.getDeclaredMethod("hash", String.class);
        hash.setAccessible(true);
        java.lang.reflect.Field cached = FollowDiagnostics.class.getDeclaredField("salt");
        cached.setAccessible(true);

        try {
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
            String first = (String) hash.invoke(null, "6812345678901234567");
            assertNotEquals("the salt must be made and kept", "",
                    Settings.DIAGNOSTIC_REPORT_SALT.get());

            // Same install, same id: two reports from one phone still line up.
            assertEquals(first, hash.invoke(null, "6812345678901234567"));

            // Another install hashes the same id differently, so holding the id tells you
            // nothing about whether it appears in somebody else's report.
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
            assertNotEquals(first, hash.invoke(null, "6812345678901234567"));

            assertEquals("empty", hash.invoke(null, (Object) null));
        } finally {
            Settings.DIAGNOSTIC_REPORT_SALT.save("");
            cached.set(null, null);
        }
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

    public static final class CaptchaGateRequest {
        private final String path;
        CaptchaGateRequest(String path) { this.path = path; }
        public String getPath() { return path; }
    }
}
