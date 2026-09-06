/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.follow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import app.morphe.extension.shared.Utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * A follow TikTok turns down comes back looking like a success, so the only thing that tells
 * the user is this notice. It has to fire on a real refusal and stay quiet otherwise.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FollowDiagnosticsTest {
    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    private static FollowDiagnostics.FollowRequestContext context() {
        return new FollowDiagnostics.FollowRequestContext(1, "/aweme/v1/commit/follow/user/");
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
}
