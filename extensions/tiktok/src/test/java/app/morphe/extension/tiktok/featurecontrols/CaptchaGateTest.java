/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.featurecontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * A risk check is classified by the request it gates. A check that arrives over a follow,
 * like, comment, repost or story action must survive the hide setting, because hiding it is
 * what makes those actions fail with no message.
 *
 * The gate owns a small process-wide queue, so each test clears that state before exercising
 * one decision. The clock remains injectable through the package-private overloads below.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CaptchaGateTest {
    /** Stands in for a retrofit request. The gate reads the path off it reflectively. */
    public static final class Request {
        private final String path;

        public Request(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    /** Stands in for the oecverify request, whose LJIIJ getter names the scene. */
    public static final class VerifyRequest {
        private final String scene;

        public VerifyRequest(String scene) {
            this.scene = scene;
        }

        public String LJIIJ() {
            return scene;
        }
    }

    private long now;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        CaptchaGate.resetForTests();
        Settings.HIDE_CAPTCHA_POPUPS.save(true);
        now = CaptchaGate.now();
    }

    /** A clock past every write recorded up to now, whatever ran before this test. */
    private long afterEveryWrite() {
        return now + CaptchaGate.WRITE_WINDOW_MS + 1_000L;
    }

    @Test
    public void everyWriteActionIsRecognisedByItsPath() {
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v1/commit/follow/user/"));
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v3/f2f/follow/"));
        // The follow probe accepts a relation route as a follow, so the gate has to as well.
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v1/relation/follow/commit/"));
        // A story like is a like on the story's own Aweme, so it uses the ordinary digg path.
        assertEquals("like", CaptchaGate.writeActionFor("/aweme/v1/commit/item/digg/"));
        assertEquals("comment", CaptchaGate.writeActionFor("/aweme/v1/comment/publish/"));
        assertEquals("comment", CaptchaGate.writeActionFor("/webcast/room/chat/"));
        assertEquals("repost", CaptchaGate.writeActionFor("/tiktok/v1/upvote/publish/"));
        assertEquals("story", CaptchaGate.writeActionFor("/tiktok/story/maf/mute"));
    }

    @Test
    public void aRepostCommentIsACommentNotARepost() {
        // publish_comment contains upvote/publish, so the order the paths are tested matters.
        assertEquals("comment", CaptchaGate.writeActionFor("/tiktok/v1/upvote/publish_comment/"));
    }

    @Test
    public void browsingPathsGateNothing() {
        assertNull(CaptchaGate.writeActionFor("/aweme/v1/feed/"));
        assertNull(CaptchaGate.writeActionFor("/tiktok/story/view/report/v1"));
        assertNull(CaptchaGate.writeActionFor("/aweme/v2/comment/list/"));
        assertNull(CaptchaGate.writeActionFor("/aweme/v1/user/following/list/"));
        assertNull(CaptchaGate.writeActionFor(null));
    }

    @Test
    public void aCheckOverAWriteIsShownEvenWithTheSettingOn() {
        CaptchaGate.recordRequest(new Request("/aweme/v1/commit/follow/user/"));

        assertEquals("follow", CaptchaGate.pendingWriteAction());
        assertEquals("it gates a follow",
                CaptchaGate.showReason(null, "{\"subtype\":\"slide\"}", now));
    }

    @Test
    public void aBrowsingRequestArmsNothing() {
        // Order independent: whatever an earlier case left pending, a browsing request must
        // not change it. A path wrongly classified as a write would.
        String before = CaptchaGate.pendingWriteAction(now);
        CaptchaGate.recordRequest(new Request("/aweme/v1/feed/"));
        assertEquals(before, CaptchaGate.pendingWriteAction(now));

        CaptchaGate.recordRequest(new Request("/aweme/v2/comment/list/"));
        assertEquals(before, CaptchaGate.pendingWriteAction(now));
    }

    @Test
    public void aCheckWhileBrowsingIsNotAttributedToAWrite() {
        CaptchaGate.recordRequest(new Request("/aweme/v1/feed/"));

        long clock = afterEveryWrite();
        assertNull(CaptchaGate.pendingWriteAction(clock));

        String reason = CaptchaGate.showReason(null, "{\"subtype\":\"slide\"}", clock);
        // Whatever else stops it, a browsing check is never held back by a write.
        assertTrue(String.valueOf(reason), reason == null || !reason.startsWith("it gates"));
    }

    @Test
    public void aWriteStopsGatingChecksOnceItsWindowPasses() {
        CaptchaGate.recordRequest(new Request("/aweme/v1/commit/item/digg/"));

        assertEquals("like", CaptchaGate.pendingWriteAction(now));
        assertEquals("like", CaptchaGate.pendingWriteAction(now + CaptchaGate.WRITE_WINDOW_MS));
        assertNull(CaptchaGate.pendingWriteAction(afterEveryWrite()));
    }

    @Test
    public void theSettingBeingOffIsTheFirstAnswer() {
        Settings.HIDE_CAPTCHA_POPUPS.save(false);
        CaptchaGate.recordRequest(new Request("/aweme/v1/commit/follow/user/"));

        assertEquals("the setting is off", CaptchaGate.showReason(null, null, now));
    }

    @Test
    public void accountVerificationIsNeverHidden() {
        assertEquals("it is account verification",
                CaptchaGate.showReason(null, "{\"scene\":\"/passport/mobile/check\"}", afterEveryWrite()));
    }

    @Test
    public void aCheckWithAReasonToBeShownIsNotHidden() {
        // shouldHide is what every hook calls. Whenever showReason has an answer, the puzzle
        // has to reach the user; only a null reason may suppress one.
        CaptchaGate.recordRequest(new Request("/aweme/v1/commit/follow/user/"));

        assertFalse(CaptchaGate.shouldHide(null, "risk slide", "{\"subtype\":\"slide\"}"));
        assertFalse(CaptchaGate.shouldHideCaptchaPopup(null, "{\"subtype\":\"slide\"}"));
        assertFalse(CaptchaGate.shouldHideLegacyCaptchaPopup(null, 2148));
        assertFalse(CaptchaGate.shouldHideTuringCaptchaPopup(null, "common_verify"));
    }

    @Test
    public void aVerificationRequestThatNamesNoSceneIsShown() {
        // Without a scene there is no way to tell an account check from a browsing one.
        assertFalse(CaptchaGate.shouldHideOecCaptchaPopup(new VerifyRequest(null)));
        assertFalse(CaptchaGate.shouldHideOecCaptchaPopup(new Object()));
        assertFalse(CaptchaGate.shouldHideTuringDialog(null, null));
    }

    @Test
    public void smsAndTwoFactorChecksAreNeverHidden() {
        assertFalse(CaptchaGate.shouldHideTuringCaptchaPopup(null, "sms"));
        assertFalse(CaptchaGate.shouldHideTuringCaptchaPopup(null, "twice_verify"));
    }

    @Test
    public void aHiddenCheckIsRecordedByItsIdAndSaidOnceOnScreen() {
        ShadowToast.reset();

        CaptchaGate.noteSuppressed(CaptchaGate.checkId("risk", "{\"subtype\":\"slide_captcha\"}"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertEquals("risk slide_captcha", CaptchaGate.recentlySuppressedCheckId());
        int afterFirst = ShadowToast.shownToastCount();
        assertTrue("no toast was shown", afterFirst >= 1);
        assertTrue(String.valueOf(ShadowToast.getTextOfLatestToast()),
                ShadowToast.getTextOfLatestToast().contains("Hide CAPTCHA popups"));

        // The same puzzle repeats on every retry, so only the first one says anything.
        CaptchaGate.noteSuppressed(CaptchaGate.checkId("risk", "{\"subtype\":\"slide_captcha\"}"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(afterFirst, ShadowToast.shownToastCount());
    }

    @Test
    public void anIdFallsBackToTheChecksOwnDescription() {
        assertEquals("service common_verify", CaptchaGate.checkId("service", "common_verify"));
        assertEquals("scene unknown", CaptchaGate.checkId("scene", null));

        String tooLong = new String(new char[200]).replace('\0', 'x');
        assertEquals("risk ".length() + 80, CaptchaGate.checkId("risk", tooLong).length());
    }

    @Test
    public void theWindowCoversTheWholeRoundTrip() {
        // The server raises the check in its answer, so the request has already been sent.
        CaptchaGate.recordRequest(new Request("/aweme/v1/commit/follow/user/"));
        assertNotNull(CaptchaGate.pendingWriteAction());
        assertEquals("follow", CaptchaGate.pendingWriteAction(now + 10_000L));
    }
}
