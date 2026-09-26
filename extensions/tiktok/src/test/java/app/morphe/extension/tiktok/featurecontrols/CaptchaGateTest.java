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
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
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
        HookStatus.clear();
        CaptchaGate.resetForTests();
        Settings.HIDE_CAPTCHA_POPUPS.save(true);
        now = CaptchaGate.now();
    }

    @After
    public void tearDown() {
        HookStatus.clear();
    }

    /** A clock past every write recorded up to now, whatever ran before this test. */
    private long afterEveryWrite() {
        return now + CaptchaGate.WRITE_WINDOW_MS + 1_000L;
    }

    @Test
    public void everyWriteActionIsRecognisedByItsPath() {
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v1/commit/follow/user/"));
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v3/f2f/follow/"));
        assertEquals("follow", CaptchaGate.writeActionFor("/aweme/v1/commit/follow/batchuser/"));
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

    /**
     * The export tells three states apart: the gate is in the build, it has seen a write, and
     * a check has reached it.
     *
     * <p>A report of a comment that went nowhere carried no CAPTCHA line at all, so nobody
     * could say whether Hushfeed had seen the publish request, whether a puzzle had been
     * raised, or whether the gate was even in that build. A family that is simply absent says
     * all three.
     */
    @Test
    public void theExportSaysWhetherTheGateSawAWriteAndACheck() {
        assertFalse("the family is in the export before the settings load",
                String.join(" ", HookStatus.report()).contains("CAPTCHA account state"));

        app.morphe.extension.tiktok.settings.SettingsStatus.enableCaptchaPopupSuppression();
        assertTrue(HookStatus.report().toString(), String.join(" ", HookStatus.report())
                .contains("CAPTCHA account state: 1 found, 0 missing"));

        CaptchaGate.recordRequest(new Request("/aweme/v1/comment/publish/"));
        CaptchaGate.recordRequest(new Request("/aweme/v1/comment/publish/"));
        assertTrue("a write the recorder saw is not in the export: " + HookStatus.report(),
                String.join(" ", HookStatus.report())
                        .contains("CAPTCHA account state: 2 found, 0 missing"));

        // A read is not a write, so it changes nothing.
        CaptchaGate.recordRequest(new Request("/aweme/v1/comment/list/"));
        assertTrue(String.join(" ", HookStatus.report())
                .contains("CAPTCHA account state: 2 found, 0 missing"));

        assertFalse(CaptchaGate.shouldHide(null, "risk slide", "{\"subtype\":\"slide\"}"));
        assertTrue("a check that reached the gate is not in the export: " + HookStatus.report(),
                String.join(" ", HookStatus.report())
                        .contains("CAPTCHA account state: 3 found, 0 missing"));

        // A diagnostic clear drops every entry. The next write puts the family back, with
        // itself, or an export taken after a clear would say this build has no gate.
        HookStatus.clear();
        assertFalse(String.join(" ", HookStatus.report()).contains("CAPTCHA account state"));
        CaptchaGate.recordRequest(new Request("/aweme/v1/comment/publish/"));
        assertTrue("the family did not come back after a clear: " + HookStatus.report(),
                String.join(" ", HookStatus.report())
                        .contains("CAPTCHA account state: 2 found, 0 missing"));
    }

    @Test
    public void aRenamedAccountServiceIsVisibleInHookStatus() {
        assertEquals("no account is signed in",
                CaptchaGate.showReason(null, "{\"subtype\":\"slide\"}", afterEveryWrite()));

        java.util.List<String> missing = HookStatus.missing("CAPTCHA account state");
        assertEquals(missing.toString(), 1, missing.size());
        assertTrue(missing.get(0), missing.get(0).contains("ServiceManager"));
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
    public void aNumberIsNotReadAsTheNextKeysName() {
        // The first quote after the colon belonged to the next key, so the check was named
        // after a field rather than by what it is.
        assertEquals("risk login", CaptchaGate.checkId("risk", "{\"subtype\":3,\"scene\":\"login\"}"));
        assertEquals("risk slide", CaptchaGate.checkId("risk", "{\"subtype\": \"slide\"}"));
        assertEquals("risk {\"subtype\":3}", CaptchaGate.checkId("risk", "{\"subtype\":3}"));
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

    @Test
    public void everyAccountWriteKeepsItsChallengeVisible() {
        String[][] writes = {
                {"/aweme/v1/commit/follow/user/", "follow"},
                {"/aweme/v3/f2f/follow/", "follow"},
                {"/aweme/v1/remove/follower/", "follow"},
                {"/aweme/v1/commit/follow/batchuser/", "follow"},
                {"/aweme/v1/commit/item/digg/", "like"},
                {"/aweme/v1/commit/game/item/digg/", "like"},
                {"/aweme/v1/comment/digg/", "like"},
                {"/aweme/v1/unlogged/digg/", "like"},
                {"/aweme/v1/danmaku/digg/", "like"},
                {"/aweme/v1/upvote/digg/", "like"},
                {"/aweme/v1/upvote/comment_digg/", "like"},
                {"/aweme/v1/comment/publish/", "comment"},
                {"/aweme/v1/comment/delete/", "comment"},
                {"/aweme/v1/comment/pin/", "comment"},
                {"/aweme/v1/upvote/publish_comment/", "comment"},
                {"/webcast/room/chat/", "comment"},
                {"/tiktok/v1/upvote/publish/", "repost"},
                {"/tiktok/v1/upvote/batch_publish/", "repost"},
                {"/tiktok/v1/repost/create/", "repost"},
                {"/tiktok/story/maf/mute", "story"},
                {"/tiktok/story/maf/unmute", "story"},
        };

        for (String[] write : writes) {
            CaptchaGate.resetForTests();
            CaptchaGate.recordRequest(new Request(write[0]));
            assertEquals(write[1], CaptchaGate.pendingWriteAction(now));
            assertEquals("it gates a " + write[1],
                    CaptchaGate.showReason(null, "{\"subtype\":\"slide\"}", now));

            // Every native popup route shares this answer, including account security wrappers.
            assertFalse(CaptchaGate.shouldHideCaptchaPopup(null, "{\"subtype\":\"slide\"}"));
            assertFalse(CaptchaGate.shouldHideLegacyCaptchaPopup(null, 2148));
            assertFalse(CaptchaGate.shouldHideOecCaptchaPopup(new VerifyRequest("follow")));
            assertFalse(CaptchaGate.shouldHideTuringDialog(null, new VerifyRequest("follow")));
            assertFalse(CaptchaGate.shouldHideTuringCaptchaPopup(null, "common_verify"));
        }
    }
}
