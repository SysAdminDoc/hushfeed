/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.featurecontrols;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * The single place that decides whether one of TikTok's risk control puzzles may be hidden.
 *
 * TikTok raises a puzzle either while the user is browsing or because the server refused a
 * write the user just asked for. Hiding the first kind is the point of the setting. Hiding
 * the second kind makes a follow, like, comment or repost fail with no message at all, which
 * is what the reports on the upstream trackers describe. Every hook answers here so the two
 * cases cannot drift apart.
 *
 * Which kind a check is comes from the request it gates: the network interceptor records the
 * path of each outbound call, and a check that arrives while a write is still in flight is
 * treated as gating that write.
 */
public final class CaptchaGate {
    /**
     * How long a write request keeps a risk check attached to it. The server raises the
     * check in its response, so the request has always just been sent, but the puzzle can
     * take a moment to reach the UI thread.
     */
    static final long WRITE_WINDOW_MS = 15_000L;

    private static final String ACCOUNT_ACTIVITY_PREFIX = "com.ss.android.ugc.aweme.account.";
    private static final String SERVICE_MANAGER_CLASS =
            "com.ss.android.ugc.aweme.framework.services.ServiceManager";
    private static final String ACCOUNT_USER_SERVICE_CLASS =
            "com.ss.android.ugc.aweme.IAccountUserService";

    /** Written whole so a reader never sees one call's action with another call's time. */
    private static final class Stamped {
        final String value;
        final long atMs;

        Stamped(String value, long atMs) {
            this.value = value;
            this.atMs = atMs;
        }
    }

    private static volatile Stamped pendingWrite;
    private static volatile Stamped lastSuppressed;
    private static final AtomicBoolean warnedThisSession = new AtomicBoolean();

    private CaptchaGate() {
    }

    /**
     * The clock the windows are measured against. Wall time can jump when the network or the
     * user corrects it, which would either close a window early or hold it open forever.
     */
    static long now() {
        return SystemClock.elapsedRealtime();
    }

    // ---------------------------------------------------------------- what the check gates

    /** Called for every outbound request by the network interceptor hook. */
    public static void recordRequest(Object request) {
        Object path = Reflect.invoke(request, "getPath");
        if (path instanceof String) {
            recordRequestPath((String) path);
        }
    }

    static void recordRequestPath(String path) {
        String action = writeActionFor(path);
        if (action != null) {
            pendingWrite = new Stamped(action, now());
        }
    }

    /**
     * The user action an API path performs, or null for anything that only reads.
     *
     * A story like is a like on the story's own Aweme, so it arrives on the ordinary digg
     * path rather than one of its own.
     */
    public static String writeActionFor(String path) {
        if (path == null) return null;
        String value = path.toLowerCase(Locale.ROOT);

        // Shared with follow diagnostics. These are the write families in TikTok 46.2.3;
        // following/follower lists only read state and must not open a write window.
        if (value.contains("/commit/follow/")
                || value.contains("/f2f/follow/")
                || value.contains("/remove/follower/")) {
            return "follow";
        }
        if (value.contains("/commit/item/digg/")
                || value.contains("/commit/game/item/digg/")
                || value.contains("/comment/digg/")
                || value.contains("/unlogged/digg/")
                || value.contains("/danmaku/digg")
                || value.contains("/upvote/digg")
                || value.contains("/upvote/comment_digg")) {
            return "like";
        }
        // Checked before the repost paths: publish_comment also contains upvote/publish.
        if (value.contains("/comment/publish")
                || value.contains("/comment/delete/")
                || value.contains("/comment/pin")
                || value.contains("/upvote/publish_comment")
                || value.contains("/webcast/room/chat/")) {
            return "comment";
        }
        if (value.contains("/upvote/publish")
                || value.contains("/upvote/batch_publish")
                || value.contains("/repost/create/")) {
            return "repost";
        }
        if (value.contains("/story/maf/mute") || value.contains("/story/maf/unmute")) {
            return "story";
        }
        return null;
    }

    /** The write still inside its window, or null when the app is only browsing. */
    public static String pendingWriteAction() {
        return pendingWriteAction(now());
    }

    static String pendingWriteAction(long nowMs) {
        return fresh(pendingWrite, nowMs);
    }

    static void resetForTests() {
        pendingWrite = null;
        lastSuppressed = null;
        warnedThisSession.set(false);
    }

    private static String fresh(Stamped stamped, long nowMs) {
        if (stamped == null) return null;
        return nowMs - stamped.atMs <= WRITE_WINDOW_MS ? stamped.value : null;
    }

    // ---------------------------------------------------------------- the decision

    /**
     * Why this risk check has to be shown, or null when it may be hidden.
     *
     * @param detail the check's own description: the risk info, the service type or the
     *               verification scene, depending on which hook asked.
     */
    static String showReason(Activity activity, String detail, long nowMs) {
        if (!Settings.HIDE_CAPTCHA_POPUPS.get()) return "the setting is off";

        String writeAction = pendingWriteAction(nowMs);
        if (writeAction != null) return "it gates a " + writeAction;

        if (isAccountVerification(activity, detail)) return "it is account verification";
        if (!isLoggedIn()) return "no account is signed in";
        return null;
    }

    /** The whole decision for one check. True hides it. */
    public static boolean shouldHide(Activity activity, String checkId, String detail) {
        String reason = showReason(activity, detail, now());
        if (reason != null) {
            String shown = reason;
            Logger.printDebug(() -> "Showing risk check " + checkId + " because " + shown);
            return false;
        }
        noteSuppressed(checkId);
        return true;
    }

    /**
     * Records a hidden check. The log line carries its id so a report can be traced back to
     * one puzzle, and the first suppression of a session says so on screen, because the
     * effect of a hidden check is otherwise invisible.
     */
    public static void noteSuppressed(String checkId) {
        lastSuppressed = new Stamped(checkId, now());
        Logger.printInfo(() -> "Hid risk check " + checkId);

        if (!warnedThisSession.compareAndSet(false, true)) return;
        Utils.showToastShort(L10n.t("Hushfeed hid a TikTok puzzle. Turn off Hide CAPTCHA popups if something stops working."));
    }

    /** The id of the last hidden check, or null when none was hidden recently. */
    public static String recentlySuppressedCheckId() {
        return fresh(lastSuppressed, now());
    }

    // ---------------------------------------------------------------- the hooks

    /** {@code SecApiImpl.popCaptchaV2} and the LIVE host's copy of it. */
    public static boolean shouldHideCaptchaPopup(Activity activity, String riskInfo) {
        return shouldHide(activity, checkId("risk", riskInfo), riskInfo);
    }

    /** {@code SecApiImpl.popCaptcha}, which carries only the server's error code. */
    public static boolean shouldHideLegacyCaptchaPopup(Activity activity, int errorCode) {
        return shouldHide(activity, "error " + errorCode, null);
    }

    /** The oecverify risk control service, whose request names the verification scene. */
    public static boolean shouldHideOecCaptchaPopup(Object verifyRequest) {
        return shouldHideVerifyRequest(null, verifyRequest);
    }

    /** {@code BdTuring.showVerifyDialog}, reached when network verification skips SecApiImpl. */
    public static boolean shouldHideTuringDialog(Activity activity, Object verifyRequest) {
        return shouldHideVerifyRequest(activity, verifyRequest);
    }

    /**
     * A request whose scene cannot be read is shown, not hidden: without the scene there is
     * no way to tell an account check from a browsing one.
     */
    private static boolean shouldHideVerifyRequest(Activity activity, Object verifyRequest) {
        String scene = verificationScene(verifyRequest);
        if (scene == null) return false;
        return shouldHide(activity, checkId("scene", scene), scene);
    }

    /**
     * The bdturing risk control service. SMS and two factor checks are account security and
     * are never touched, whatever else is going on.
     */
    public static boolean shouldHideTuringCaptchaPopup(Activity activity, String serviceType) {
        if ("sms".equals(serviceType) || "twice_verify".equals(serviceType)) return false;
        return shouldHide(activity, checkId("service", serviceType), serviceType);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A short name for one check. ByteDance's risk info is a long JSON blob, so the named
     * fields inside it are preferred over a truncated copy of the whole thing.
     */
    static String checkId(String kind, String detail) {
        if (detail == null || detail.isEmpty()) return kind + " unknown";

        String named = jsonValue(detail, "subtype");
        if (named == null) named = jsonValue(detail, "scene");
        if (named == null) named = jsonValue(detail, "verify_way");
        if (named != null) return kind + " " + named;

        String single = detail.replace('\n', ' ').replace('\r', ' ').trim();
        return kind + " " + (single.length() > 80 ? single.substring(0, 80) : single);
    }

    /** The string value of {@code key} in a JSON-ish blob, without parsing the whole thing. */
    private static String jsonValue(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) return null;
        int colon = json.indexOf(':', at + key.length() + 2);
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close <= open + 1) return null;
        return json.substring(open + 1, close);
    }

    private static String verificationScene(Object verifyRequest) {
        Object value = Reflect.invoke(verifyRequest, "LJIIJ");
        return value instanceof String ? (String) value : null;
    }

    private static boolean isAccountVerification(Activity activity, String detail) {
        if (activity != null
                && activity.getClass().getName().startsWith(ACCOUNT_ACTIVITY_PREFIX)) {
            return true;
        }
        Intent intent = activity == null ? null : activity.getIntent();
        String route = intent == null ? null : intent.getDataString();
        return isAccountRoute(route) || isAccountRoute(detail);
    }

    private static boolean isAccountRoute(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.equals("login")
                || normalized.equals("passport")
                || normalized.contains("/passport/")
                || normalized.contains("/login/")
                || normalized.contains("\"passport\"")
                || normalized.contains("\"login\"");
    }

    private static boolean isLoggedIn() {
        try {
            Class<?> serviceManagerClass = Class.forName(SERVICE_MANAGER_CLASS);
            Object serviceManager = serviceManagerClass.getMethod("get").invoke(null);
            Class<?> accountServiceClass = Class.forName(ACCOUNT_USER_SERVICE_CLASS);
            Object accountService = serviceManagerClass
                    .getMethod("getService", Class.class)
                    .invoke(serviceManager, accountServiceClass);
            return accountService != null
                    && Boolean.TRUE.equals(accountServiceClass.getMethod("isLogin").invoke(accountService));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
