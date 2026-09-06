package app.morphe.extension.tiktok.follow;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.featurecontrols.CaptchaGate;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;

@SuppressWarnings("unused")
public final class FollowDiagnostics {
    private static final int MAX_EVENTS_PER_SESSION = 160;
    private static final long READBACK_WINDOW_MS = 30_000L;
    private static final AtomicInteger eventCount = new AtomicInteger();
    private static final AtomicInteger callId = new AtomicInteger();
    private static final ThreadLocal<Integer> activeCallId = new ThreadLocal<>();
    private static final Object networkContextLock = new Object();
    private static final IdentityHashMap<Object, FollowRequestContext> networkContexts = new IdentityHashMap<>();
    private static volatile boolean loggedSettingsSnapshot;
    private static volatile long followReadbackWindowUntil;
    private static volatile FollowRequestContext activeReadbackContext;

    private FollowDiagnostics() {}

    static final class FollowRequestContext {
        final int id;
        final String path;
        String action = "unknown";
        String uidHash = "empty";
        String secUidHash = "empty";
        String followFrom = "unknown";
        String source = "unknown";
        String enterFrom = "unknown";
        String payload = "none";
        String responseSuccess = "unknown";
        String responseCode = "unknown";
        String bodyFollowStatus = "unknown";
        String bodyFollowerStatus = "unknown";
        String bodyIsFollowSuccess = "unknown";
        String statusCode = FollowVerdict.UNKNOWN;
        String statusMsg = FollowVerdict.UNKNOWN;
        String riskCheck = "none";
        final long createdAtMs = System.currentTimeMillis();

        FollowRequestContext(int id, String path) {
            this.id = id;
            this.path = path;
        }

        boolean hasTarget() {
            return !"empty".equals(uidHash) || !"empty".equals(secUidHash);
        }

        String summary() {
            return "id=" + id
                    + " action=" + action
                    + " uidHash=" + uidHash
                    + " secUidHash=" + secUidHash
                    + " followFrom=" + followFrom
                    + " source=" + source
                    + " enterFrom=" + enterFrom;
        }

        void copyMissingFrom(FollowRequestContext other) {
            if (other == null) return;
            if ("unknown".equals(action)) action = other.action;
            if ("empty".equals(uidHash)) uidHash = other.uidHash;
            if ("empty".equals(secUidHash)) secUidHash = other.secUidHash;
            if ("unknown".equals(followFrom)) followFrom = other.followFrom;
            if ("unknown".equals(source)) source = other.source;
            if ("unknown".equals(enterFrom)) enterFrom = other.enterFrom;
        }
    }

    private static volatile FollowRequestContext recentDirectContext;
    private static final AtomicBoolean warnedAboutRefusedFollow = new AtomicBoolean();

    public static void logSimpleFollowRequest(int action, String uid, String secUid) {
        if (!shouldLog()) return;

        try {
            int id = nextCallId();
            activeCallId.set(id);
            rememberDirectContext(id, "LJ", action, "unknown", uid, secUid, null, null);
            logSettingsSnapshotOnce();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] request"
                    + " id=" + id
                    + " api=LJ"
                    + " action=" + action
                    + " uidHash=" + hash(uid)
                    + " secUidHash=" + hash(secUid));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] request log failed", ex);
        }
    }

    public static void logDetailedFollowRequest(
            int action,
            int followFrom,
            int fromPre,
            int followerStatus,
            String uid,
            String secUid,
            String source,
            String enterFrom,
            String previousPage,
            Map<?, ?> extra
    ) {
        if (!shouldLog()) return;

        try {
            int id = nextCallId();
            activeCallId.set(id);
            rememberDirectContext(id, "LJFF", action, String.valueOf(followFrom), uid, secUid, source, enterFrom);
            logSettingsSnapshotOnce();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] request"
                    + " id=" + id
                    + " api=LJFF"
                    + " action=" + action
                    + " followFrom=" + followFrom
                    + " fromPre=" + fromPre
                    + " followerStatus=" + followerStatus
                    + " uidHash=" + hash(uid)
                    + " secUidHash=" + hash(secUid)
                    + " source=" + safeShort(source)
                    + " enterFrom=" + safeShort(enterFrom)
                    + " previousPage=" + safeShort(previousPage)
                    + " extraKeys=" + describeMapKeys(extra));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] detailed request log failed", ex);
        }
    }

    public static void logCommonFollowRequest(
            int action,
            int followFrom,
            int channelId,
            int fromPre,
            String uid,
            String secUid,
            String itemId,
            String city,
            String recType,
            Map<?, ?> extra
    ) {
        if (!shouldLog()) return;

        try {
            int id = nextCallId();
            activeCallId.set(id);
            rememberDirectContext(id, "CommonFollowApi", action, String.valueOf(followFrom), uid, secUid, recType, null);
            logSettingsSnapshotOnce();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] request"
                    + " id=" + id
                    + " api=CommonFollowApi"
                    + " action=" + action
                    + " followFrom=" + followFrom
                    + " channelId=" + channelId
                    + " fromPre=" + fromPre
                    + " uidHash=" + hash(uid)
                    + " secUidHash=" + hash(secUid)
                    + " itemIdHash=" + hash(itemId)
                    + " city=" + safeShort(city)
                    + " recType=" + safeShort(recType)
                    + " extraKeys=" + describeMapKeys(extra));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] common request log failed", ex);
        }
    }

    public static void logJediFollowRequest(
            String uid,
            String secUid,
            int action,
            int followFrom,
            Integer fromPre,
            String source,
            Integer followerStatus,
            String enterFrom,
            String previousPage,
            String recType,
            Integer extraStatus
    ) {
        if (!shouldLog()) return;

        try {
            int id = nextCallId();
            activeCallId.set(id);
            rememberDirectContext(id, "JediFollowApi", action, String.valueOf(followFrom), uid, secUid, source, enterFrom);
            logSettingsSnapshotOnce();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] request"
                    + " id=" + id
                    + " api=JediFollowApi"
                    + " action=" + action
                    + " followFrom=" + followFrom
                    + " fromPre=" + safeInteger(fromPre)
                    + " followerStatus=" + safeInteger(followerStatus)
                    + " extraStatus=" + safeInteger(extraStatus)
                    + " uidHash=" + hash(uid)
                    + " secUidHash=" + hash(secUid)
                    + " source=" + safeShort(source)
                    + " enterFrom=" + safeShort(enterFrom)
                    + " previousPage=" + safeShort(previousPage)
                    + " recType=" + safeShort(recType));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] jedi request log failed", ex);
        }
    }

    public static void logFollowStream(Object stream) {
        if (!shouldLog()) return;

        try {
            Integer id = activeCallId.get();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] stream"
                    + " id=" + (id == null ? "unknown" : id)
                    + " class=" + (stream == null ? "null" : stream.getClass().getName()));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] stream log failed", ex);
        }
    }

    public static void logFollowResult(Object followStatus) {
        if (!shouldLog()) return;

        try {
            Integer id = activeCallId.get();
            activeCallId.remove();
            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] result"
                    + " id=" + (id == null ? "unknown" : id)
                    + " status=" + describeFollowStatus(followStatus));
        } catch (Exception ex) {
            Logger.printDebug(() -> "[Morphe TikTok FollowProbe] result log failed", ex);
        }
    }

    public static void logNetworkRequest(Object request) {
        String path = followPath(request);
        if (path == null || !reserveNetworkEvent()) return;

        FollowRequestContext context = rememberNetworkContext(request, path);
        context.payload = describeRequestPayload(request, context);

        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] network request"
                + " " + context.summary()
                + " path=" + path
                + " requestClass=" + className(request)
                + " requestPayload=" + context.payload
                + " requestFields=" + describeObjectFields(request));
    }

    public static void logNetworkResponse(Object request, Object response) {
        String path = followPath(request);
        if (path == null || !reserveNetworkEvent()) return;

        FollowRequestContext context = contextForRequest(request, path);

        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] network response"
                + " " + context.summary()
                + " path=" + path
                + " class=" + (response == null ? "null" : response.getClass().getName()));
    }

    public static void logNetworkThrowable(Object request, Throwable throwable) {
        String path = followPath(request);
        if (path == null || !reserveNetworkEvent()) return;

        FollowRequestContext context = contextForRequest(request, path);

            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] network error"
                + " " + context.summary()
                + " path=" + path
                + " class=" + (throwable == null ? "null" : throwable.getClass().getName())
                + " message=" + safeShort(throwable == null ? null : throwable.getMessage())
                + " outcome=network_error");
    }

    public static void logParsedResponse(Object request, Object response) {
        String followPath = followPath(request);
        if (followPath != null) {
            final String finalPath = followPath;
            boolean logging = loggingEnabled();

            // The verdict is read whether or not logging is on: a refused follow is the
            // thing users report, and it looks like nothing happened at all. With logging
            // off the context stays local, because the map that keeps one per request is
            // never emptied, and nothing here may throw into TikTok's network stack.
            FollowRequestContext context = logging
                    ? contextForRequest(request, finalPath)
                    : new FollowRequestContext(0, finalPath);
            try {
                readServerVerdict(response, context);
                warnAboutRefusedFollowOnce(context);
            } catch (Throwable throwable) {
                Logger.printDebug(() -> "[Morphe TikTok FollowProbe] verdict read failed: " + throwable);
            }

            if (!logging || !reserveNetworkEvent()) return;
            followReadbackWindowUntil = System.currentTimeMillis() + READBACK_WINDOW_MS;
            activeReadbackContext = context;

            Logger.printInfo(() -> "[Morphe TikTok FollowProbe] parsed response"
                    + " " + context.summary()
                    + " path=" + finalPath
                    + " " + describeParsedResponse(response, context)
                    + " outcome=" + serverOutcome(context));
            return;
        }

        String readbackPath = followReadbackPath(request);
        if (readbackPath == null || !reserveNetworkEvent()) return;
        final String finalPath = readbackPath;
        FollowRequestContext context = activeReadbackContext;

        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] readback response"
                + " afterId=" + (context == null ? "unknown" : context.id)
                + " target=" + (context == null ? "unknown" : context.summary())
                + " path=" + finalPath
                + " " + describeReadbackResponse(response, context));
    }

    public static void logParseThrowable(Object request, Throwable throwable) {
        String path = followPath(request);
        if (path == null || !reserveNetworkEvent()) return;

        FollowRequestContext context = contextForRequest(request, path);

        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] parse error"
                + " " + context.summary()
                + " path=" + path
                + " class=" + (throwable == null ? "null" : throwable.getClass().getName())
                + " message=" + safeShort(throwable == null ? null : throwable.getMessage())
                + " outcome=parse_error");
    }

    private static boolean shouldLog() {
        try {
            return BaseSettings.DEBUG.get() && eventCount.get() < MAX_EVENTS_PER_SESSION;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean loggingEnabled() {
        try {
            return BaseSettings.DEBUG.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean reserveNetworkEvent() {
        try {
            if (!BaseSettings.DEBUG.get()) return false;
            return eventCount.incrementAndGet() <= MAX_EVENTS_PER_SESSION;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int nextCallId() {
        eventCount.incrementAndGet();
        return callId.incrementAndGet();
    }

    private static FollowRequestContext rememberNetworkContext(Object request, String path) {
        FollowRequestContext context = contextForRequest(request, path);
        activeReadbackContext = context;
        return context;
    }

    private static FollowRequestContext contextForRequest(Object request, String path) {
        synchronized (networkContextLock) {
            FollowRequestContext context = request == null ? null : networkContexts.get(request);
            if (context == null) {
                context = new FollowRequestContext(callId.incrementAndGet(), path);
                context.copyMissingFrom(recentDirectContextIfFresh());
                if (request != null) {
                    networkContexts.put(request, context);
                }
            }
            return context;
        }
    }

    private static void rememberDirectContext(
            int id,
            String api,
            int action,
            String followFrom,
            String uid,
            String secUid,
            String source,
            String enterFrom
    ) {
        FollowRequestContext context = new FollowRequestContext(id, api);
        context.action = String.valueOf(action);
        context.followFrom = safeShort(followFrom);
        context.uidHash = hash(uid);
        context.secUidHash = hash(secUid);
        context.source = safeShort(source);
        context.enterFrom = safeShort(enterFrom);
        recentDirectContext = context;
        activeReadbackContext = context;
    }

    private static FollowRequestContext recentDirectContextIfFresh() {
        FollowRequestContext context = recentDirectContext;
        if (context == null) return null;
        return System.currentTimeMillis() - context.createdAtMs <= 10_000L ? context : null;
    }

    private static void logSettingsSnapshotOnce() {
        if (loggedSettingsSnapshot) return;
        loggedSettingsSnapshot = true;

        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] settings"
                + " simSpoof=" + Settings.SIM_SPOOF.get()
                + " simIso=" + safeShort(Settings.SIM_SPOOF_ISO.get())
                + " simMccMnc=" + safeShort(Settings.SIMSPOOF_MCCMNC.get())
                + " simOperator=" + safeShort(Settings.SIMSPOOF_OP_NAME.get()));
    }

    private static String describeFollowStatus(Object followStatus) {
        if (followStatus == null) return "null";

        return "class=" + followStatus.getClass().getName()
                + " userIdHash=" + hash(invokeString(followStatus, "getUserId"))
                + " followStatus=" + invokeValue(followStatus, "getFollowStatus")
                + " followerStatus=" + invokeValue(followStatus, "getFollowerStatus")
                + " followFrom=" + invokeValue(followStatus, "getFollowFrom")
                + " errorCode=" + invokeValue(followStatus, "getErrorCode");
    }

    /**
     * Fills the context with what the server actually said. A refused follow comes back as an
     * ordinary success carrying a status code in the body, so this reads that code and message
     * and notes whether a risk check was hidden just before it.
     */
    private static void readServerVerdict(Object response, FollowRequestContext context) {
        if (response == null) return;

        Object body = readField(response, "LIZIZ");
        context.responseSuccess = invokeValue(response, "LIZJ");
        context.responseCode = invokeValue(response, "LIZ");
        context.bodyFollowStatus = describeSimpleValue(firstPresentValue(body, "getFollowStatus", "followStatus"));
        context.bodyFollowerStatus = describeSimpleValue(firstPresentValue(body, "getFollowerStatus", "followerStatus"));
        context.bodyIsFollowSuccess = describeSimpleValue(firstPresentValue(body, "isFollowSuccess", "getFollowSuccess", "isFollow_success", "isFollowSuccess"));

        FollowVerdict verdict = FollowVerdict.of(body);
        context.statusCode = verdict.statusCode;
        context.statusMsg = safeShort(verdict.statusMsg);

        String suppressed = CaptchaGate.recentlySuppressedCheckId();
        context.riskCheck = suppressed == null ? "none" : safeShort(suppressed);
    }

    private static String describeParsedResponse(Object response, FollowRequestContext context) {
        if (response == null) return "response=null";

        Object body = readField(response, "LIZIZ");
        Object rawResponse = readField(response, "LIZ");

        return "responseClass=" + response.getClass().getName()
                + " responseSuccess=" + context.responseSuccess
                + " responseCode=" + context.responseCode
                + " status_code=" + context.statusCode
                + " status_msg=" + context.statusMsg
                + " riskCheck=" + context.riskCheck
                + " bodyClass=" + className(body)
                + " bodyFields=" + describeObjectFields(body)
                + " rawClass=" + className(rawResponse)
                + " rawFields=" + describeObjectFields(rawResponse);
    }

    /** True only when the server said no in a way that cannot be read as anything else. */
    static boolean followWasRefused(FollowRequestContext context) {
        return FollowVerdict.isRefusalCode(context.statusCode)
                || "false".equals(context.bodyIsFollowSuccess);
    }

    /**
     * A refused follow leaves the button looking as though it worked, so the first one of a
     * session says what happened. Once only: the same refusal repeats on every retry.
     */
    static void warnAboutRefusedFollowOnce(FollowRequestContext context) {
        if (!followWasRefused(context) || !warnedAboutRefusedFollow.compareAndSet(false, true)) return;

        String reason = FollowVerdict.UNKNOWN.equals(context.statusMsg)
                ? L10n.f("code %1$s", context.statusCode)
                : context.statusMsg;
        String message = L10n.f("TikTok refused the follow: %1$s", reason);
        if (!"none".equals(context.riskCheck)) {
            message += " " + L10n.t("A hidden puzzle may be the cause.");
        }
        Utils.showToastLong(message);
        Logger.printInfo(() -> "[Morphe TikTok FollowProbe] refused"
                + " " + context.summary()
                + " path=" + context.path
                + " status_code=" + context.statusCode
                + " status_msg=" + context.statusMsg
                + " riskCheck=" + context.riskCheck);
    }

    private static String describeReadbackResponse(Object response, FollowRequestContext context) {
        if (response == null) return "response=null";

        Object body = readField(response, "LIZIZ");
        String userSignals = describeUserSignals(body);
        String match = targetMatch(userSignals, context);

        return "responseClass=" + response.getClass().getName()
                + " responseSuccess=" + invokeValue(response, "LIZJ")
                + " responseCode=" + invokeValue(response, "LIZ")
                + " bodyClass=" + className(body)
                + " bodyFields=" + describeObjectFields(body)
                + " targetMatch=" + match
                + " readbackOutcome=" + readbackOutcome(userSignals, context, match)
                + " userSignals=" + userSignals;
    }

    private static String describeRequestPayload(Object request, FollowRequestContext context) {
        Object payload = requestPayloadRoot(request);
        if (payload == null) return "none";

        StringBuilder builder = new StringBuilder();
        builder.append("bodyClass=").append(className(payload));
        StringBuilder params = new StringBuilder();
        int[] count = new int[]{0};
        String encodedBody = encodedFormBody(payload);
        if (encodedBody != null) {
            appendEncodedPayloadValues(encodedBody, params, context, count);
            builder.append(",encodedBody=").append(count[0] > 0 ? "parsed" : "noInterestingKeys");
        }
        appendRequestPayloadValues(payload, params, context, new IdentityHashMap<>(), 0, count);
        if (params.length() > 0) {
            builder.append(",params=").append(params);
        }
        return builder.toString();
    }

    private static Object requestPayloadRoot(Object request) {
        Object direct = firstPresentValue(request,
                "getBody", "getTypedOutput", "getRequestBody", "body", "requestBody", "typedOutput");
        if (isLikelyPayloadObject(direct)) return direct;

        if (request == null) return null;

        Class<?> currentClass = request.getClass();
        while (currentClass != null) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(request);
                    if (isLikelyPayloadObject(value)) return value;
                } catch (Exception ignored) {
                    // Ignore fields that cannot be read safely.
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return null;
    }

    private static boolean isLikelyPayloadObject(Object value) {
        if (value == null || isSimpleValue(value)) return false;
        if (isReflectionMetadata(value)) return false;
        if (value instanceof Map<?, ?> || value instanceof Collection<?>) return true;

        String className = value.getClass().getName().toLowerCase(Locale.US);
        return className.contains("body")
                || className.contains("form")
                || className.contains("mime")
                || className.contains("output")
                || className.contains("param")
                || className.contains("pair");
    }

    private static void appendRequestPayloadValues(
            Object target,
            StringBuilder builder,
            FollowRequestContext context,
            IdentityHashMap<Object, Boolean> seen,
            int depth,
            int[] count
    ) {
        if (target == null || depth > 4 || count[0] >= 12) return;
        if (target instanceof String) {
            appendEncodedPayloadValues((String) target, builder, context, count);
            return;
        }
        if (isSimpleValue(target)) return;
        if (isReflectionMetadata(target)) return;
        if (seen.put(target, Boolean.TRUE) != null) return;

        if (target instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) target).entrySet()) {
                if (count[0] >= 12) break;
                appendNamedPayloadValue(String.valueOf(entry.getKey()), entry.getValue(), builder, context, count);
                appendRequestPayloadValues(entry.getValue(), builder, context, seen, depth + 1, count);
            }
            return;
        }

        if (target instanceof Collection<?>) {
            int itemCount = 0;
            for (Object item : (Collection<?>) target) {
                if (itemCount++ >= 12 || count[0] >= 12) break;
                appendRequestPayloadValues(item, builder, context, seen, depth + 1, count);
            }
            return;
        }

        Object key = firstPresentValue(target, "getName", "getKey", "name", "key");
        Object value = firstPresentValue(target, "getValue", "value");
        if (key instanceof String && appendNamedPayloadValue((String) key, value, builder, context, count)) {
            return;
        }

        Class<?> currentClass = target.getClass();
        while (currentClass != null && depth < 4 && count[0] < 12) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (count[0] >= 12) break;
                if (Modifier.isStatic(field.getModifiers())) continue;

                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(target);
                    if (shouldSkipPayloadField(field.getName(), fieldValue)) continue;
                    if (appendNamedPayloadValue(field.getName(), fieldValue, builder, context, count)) {
                        continue;
                    }
                    appendRequestPayloadValues(fieldValue, builder, context, seen, depth + 1, count);
                } catch (Exception ignored) {
                    // Ignore fields that cannot be read safely.
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private static boolean appendNamedPayloadValue(
            String name,
            Object value,
            StringBuilder builder,
            FollowRequestContext context,
            int[] count
    ) {
        if (name == null) return false;
        String key = normalizePayloadKey(name);
        if (!isInterestingPayloadKey(key)) return false;

        if (builder.length() > 0) builder.append(',');
        builder.append(safeShort(name)).append('=').append(describePayloadValue(key, value));
        recordPayloadTarget(context, key, value);
        count[0]++;
        return true;
    }

    private static boolean isInterestingPayloadKey(String key) {
        return key.equals("uid")
                || key.equals("user_id")
                || key.equals("userid")
                || key.equals("to_user_id")
                || key.equals("from_user_id")
                || key.equals("sec_uid")
                || key.equals("secuid")
                || key.equals("sec_user_id")
                || key.equals("secuserid")
                || key.equals("action")
                || key.equals("type")
                || key.equals("follow_status")
                || key.equals("followstatus")
                || key.equals("follow_from")
                || key.equals("followfrom")
                || key.equals("from_pre")
                || key.equals("frompre")
                || key.equals("enter_from")
                || key.equals("enterfrom")
                || key.equals("source")
                || key.equals("previous_page")
                || key.equals("previouspage")
                || key.equals("channel_id")
                || key.equals("channelid")
                || key.equals("item_id")
                || key.equals("itemid")
                || key.equals("aweme_id")
                || key.equals("awemeid")
                || key.equals("follower_status")
                || key.equals("followerstatus");
    }

    private static String describePayloadValue(String key, Object value) {
        if (value == null) return "null";
        if (isSensitivePayloadKey(key)) {
            String stringValue = String.valueOf(value);
            return "stringHash=" + hash(stringValue) + "/len=" + stringValue.length();
        }
        if (isSimpleValue(value)) {
            return safeShort(String.valueOf(value));
        }
        return "class=" + value.getClass().getName();
    }

    private static boolean isSensitivePayloadKey(String key) {
        if (key.contains("action") || key.contains("status") || key.contains("type") || key.contains("channel")) {
            return false;
        }
        return key.contains("uid")
                || key.contains("user")
                || key.contains("sec")
                || key.contains("item")
                || key.contains("aweme");
    }

    private static void recordPayloadTarget(FollowRequestContext context, String key, Object value) {
        if (context == null || value == null) return;

        String stringValue = String.valueOf(value);
        if (stringValue.isEmpty()) return;

        if (key.equals("sec_uid") || key.equals("secuid") || key.equals("sec_user_id") || key.equals("secuserid")) {
            context.secUidHash = hash(stringValue);
        } else if (key.equals("uid") || key.equals("user_id") || key.equals("userid") || key.equals("to_user_id")) {
            context.uidHash = hash(stringValue);
        } else if (key.equals("action") || key.equals("follow_status") || key.equals("followstatus") || key.equals("type")) {
            context.action = safeShort(stringValue);
        } else if (key.equals("follow_from") || key.equals("followfrom")) {
            context.followFrom = safeShort(stringValue);
        } else if (key.equals("source")) {
            context.source = safeShort(stringValue);
        } else if (key.equals("enter_from") || key.equals("enterfrom")) {
            context.enterFrom = safeShort(stringValue);
        }
    }

    private static String serverOutcome(FollowRequestContext context) {
        if (context == null) return "unknown";
        if (!"true".equals(context.responseSuccess)) return "response_not_success";
        if (!"200".equals(context.responseCode)) return "http_" + safeShort(context.responseCode);
        if (followWasRefused(context)) return "server_refused_" + context.statusCode;
        if ("unknown".equals(context.bodyFollowStatus) || "null".equals(context.bodyFollowStatus)) return "body_missing_follow_status";
        return "server_accept_" + context.bodyFollowStatus;
    }

    private static String readbackOutcome(String userSignals, FollowRequestContext context, String match) {
        if (context == null) return "no_request_context";
        if ("unknown".equals(match)) return context.hasTarget() ? "no_matching_readback_target" : "no_request_target";
        if ("false".equals(match)) return "target_mismatch";
        if (!"unknown".equals(context.bodyFollowStatus) && userSignals != null
                && userSignals.contains("followStatus=" + context.bodyFollowStatus)) {
            return "confirmed_" + context.bodyFollowStatus;
        }
        return "target_found_state_unclear";
    }

    private static String encodedFormBody(Object payload) {
        if (payload == null) return null;
        String className = payload.getClass().getName();
        if (!className.contains("FormUrlEncodedTypedOutput")) return null;

        try {
            Method writeTo = payload.getClass().getMethod("writeTo", OutputStream.class);
            writeTo.setAccessible(true);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeTo.invoke(payload, output);
            return output.toString("UTF-8");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void appendEncodedPayloadValues(
            String encodedBody,
            StringBuilder builder,
            FollowRequestContext context,
            int[] count
    ) {
        if (encodedBody == null || encodedBody.isEmpty()) return;

        String[] parts = encodedBody.split("&");
        for (String part : parts) {
            if (count[0] >= 12) break;
            int equalsIndex = part.indexOf('=');
            if (equalsIndex <= 0) continue;

            String key = decodeUrlComponent(part.substring(0, equalsIndex));
            String value = decodeUrlComponent(part.substring(equalsIndex + 1));
            appendNamedPayloadValue(key, value, builder, context, count);
        }
    }

    private static String decodeUrlComponent(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String normalizePayloadKey(String key) {
        return key.replace('-', '_').toLowerCase(Locale.US);
    }

    private static boolean shouldSkipPayloadField(String fieldName, Object fieldValue) {
        if (fieldName == null) return true;
        if (isReflectionMetadata(fieldValue)) return true;

        String key = normalizePayloadKey(fieldName);
        return key.startsWith("shadow$_")
                || key.contains("dextype")
                || key.contains("primitive")
                || key.contains("componenttype")
                || key.contains("resolved")
                || key.equals("status");
    }

    private static boolean isReflectionMetadata(Object value) {
        return value instanceof Class<?>
                || value instanceof Method
                || value instanceof Field
                || value instanceof ClassLoader
                || value instanceof Package;
    }

    private static String targetMatch(String userSignals, FollowRequestContext context) {
        if (context == null || !context.hasTarget()) return "unknown";
        if (userSignals == null || "none".equals(userSignals)) return "unknown";

        if (!"empty".equals(context.uidHash) && userSignals.contains("uidHash=" + context.uidHash)) {
            return "true";
        }
        if (!"empty".equals(context.secUidHash) && userSignals.contains("secUidHash=" + context.secUidHash)) {
            return "true";
        }
        return "false";
    }

    private static String invokeString(Object target, String methodName) {
        Object value = invokeValueObject(target, methodName);
        return value instanceof String ? (String) value : null;
    }

    private static String invokeValue(Object target, String methodName) {
        Object value = invokeValueObject(target, methodName);
        return value == null ? "n/a" : describeSimpleValue(value);
    }

    private static Object invokeValueObject(Object target, String methodName) {
        if (target == null) return null;

        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            Class<?> currentClass = target.getClass();
            while (currentClass != null) {
                try {
                    Method method = currentClass.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception ignoredAgain) {
                    currentClass = currentClass.getSuperclass();
                }
            }
        }

        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;

        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }

        return null;
    }

    private static String describeObjectFields(Object target) {
        if (target == null) return "null";

        StringBuilder builder = new StringBuilder();
        int count = 0;
        Class<?> currentClass = target.getClass();

        while (currentClass != null && count < 10) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (count >= 10) break;
                if (Modifier.isStatic(field.getModifiers())) continue;

                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (count > 0) builder.append(',');
                    builder.append(field.getName()).append('=').append(describeSimpleValue(value));
                    count++;
                } catch (Exception ignored) {
                    // Ignore fields that cannot be read safely.
                }
            }

            currentClass = currentClass.getSuperclass();
        }

        return count == 0 ? "none" : builder.toString();
    }

    private static String describeUserSignals(Object target) {
        StringBuilder builder = new StringBuilder();
        appendUserSignals(target, builder, new IdentityHashMap<>(), 0, new int[]{0});
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static void appendUserSignals(
            Object target,
            StringBuilder builder,
            IdentityHashMap<Object, Boolean> seen,
            int depth,
            int[] count
    ) {
        if (target == null || depth > 3 || count[0] >= 8 || isSimpleValue(target)) return;
        if (seen.put(target, Boolean.TRUE) != null) return;

        if (target instanceof Collection<?>) {
            int itemCount = 0;
            for (Object item : (Collection<?>) target) {
                if (itemCount++ >= 8) break;
                appendUserSignals(item, builder, seen, depth + 1, count);
            }
            return;
        }

        if (target instanceof Map<?, ?>) {
            int itemCount = 0;
            for (Object value : ((Map<?, ?>) target).values()) {
                if (itemCount++ >= 8) break;
                appendUserSignals(value, builder, seen, depth + 1, count);
            }
            return;
        }

        if (looksLikeUserState(target)) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append(describeUserState(target));
            count[0]++;
        }

        Class<?> currentClass = target.getClass();
        while (currentClass != null && depth < 3 && count[0] < 8) {
            for (Field field : currentClass.getDeclaredFields()) {
                if (count[0] >= 8) break;
                if (Modifier.isStatic(field.getModifiers())) continue;

                try {
                    field.setAccessible(true);
                    appendUserSignals(field.get(target), builder, seen, depth + 1, count);
                } catch (Exception ignored) {
                    // Ignore fields that cannot be read safely.
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    private static boolean looksLikeUserState(Object target) {
        String className = target.getClass().getName().toLowerCase(Locale.US);
        if (className.contains("user") || className.contains("follow")) return true;

        return firstPresentValue(target,
                "getUid", "getSecUid", "getUniqueId", "getFollowStatus", "getFollowerStatus",
                "uid", "secUid", "uniqueId", "followStatus", "followerStatus") != null;
    }

    private static String describeUserState(Object target) {
        return "class=" + target.getClass().getName()
                + ",uidHash=" + hash(firstPresentString(target, "getUid", "uid", "userId", "getUserId"))
                + ",secUidHash=" + hash(firstPresentString(target, "getSecUid", "secUid"))
                + ",uniqueIdHash=" + hash(firstPresentString(target, "getUniqueId", "uniqueId", "nickname"))
                + ",followStatus=" + describeSimpleValue(firstPresentValue(target, "getFollowStatus", "followStatus"))
                + ",followerStatus=" + describeSimpleValue(firstPresentValue(target, "getFollowerStatus", "followerStatus"))
                + ",followed=" + describeSimpleValue(firstPresentValue(target, "isFollowing", "isFollowed", "following"));
    }

    private static String firstPresentString(Object target, String... names) {
        Object value = firstPresentValue(target, names);
        return value instanceof String ? (String) value : null;
    }

    private static Object firstPresentValue(Object target, String... names) {
        if (target == null) return null;

        for (String name : names) {
            Object value = invokeValueObject(target, name);
            if (value != null) return value;

            value = readField(target, name);
            if (value != null) return value;
        }

        return null;
    }

    private static String describeSimpleValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) {
            return safeShort(String.valueOf(value));
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            return "stringHash=" + hash(stringValue) + "/len=" + stringValue.length();
        }
        if (value instanceof Enum<?>) {
            return safeShort(((Enum<?>) value).name());
        }

        return "class=" + value.getClass().getName();
    }

    private static boolean isSimpleValue(Object value) {
        return value instanceof Number
                || value instanceof Boolean
                || value instanceof String
                || value instanceof Enum<?>
                || value instanceof Character;
    }

    private static String className(Object target) {
        return target == null ? "null" : target.getClass().getName();
    }

    private static String followPath(Object request) {
        String path = requestPath(request);
        if (path == null) return null;

        if (!path.contains("follow")) return null;
        if (!path.contains("commit") && !path.contains("relation")) return null;
        return safeShort(path);
    }

    private static String followReadbackPath(Object request) {
        String path = requestPath(request);
        if (path == null) return null;

        if (followPath(request) != null) return null;

        boolean hasUserArea = path.contains("user") || path.contains("profile") || path.contains("relation");
        if (hasUserArea && System.currentTimeMillis() <= followReadbackWindowUntil) {
            return safeShort(path);
        }

        boolean hasFollowStateClue = path.contains("profile")
                || path.contains("detail")
                || path.contains("relation")
                || path.contains("follow")
                || path.contains("friend");

        return hasUserArea && hasFollowStateClue ? safeShort(path) : null;
    }

    private static String requestPath(Object request) {
        Object value = invokeValueObject(request, "getPath");
        if (!(value instanceof String)) return null;

        return (String) value;
    }

    private static String describeMapKeys(Map<?, ?> map) {
        if (map == null) return "null";
        if (map.isEmpty()) return "empty";

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Object key : map.keySet()) {
            if (count >= 8) {
                builder.append(",...");
                break;
            }
            if (count > 0) builder.append(',');
            builder.append(safeShort(String.valueOf(key)));
            count++;
        }
        return builder.toString();
    }

    private static String safeInteger(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static String safeShort(String value) {
        if (value == null) return "null";
        String sanitized = value.replace('\n', ' ').replace('\r', ' ');
        if (sanitized.length() > 48) {
            return sanitized.substring(0, 48) + "...";
        }
        return sanitized;
    }

    /**
     * Stands in for an account id in a report that gets attached to bug reports. Without the
     * salt this was a membership test: anybody holding a handful of candidate ids could hash
     * them and see which appeared. The salt is made once per install and never leaves the
     * phone, so two reports from the same device still line up with each other and a report
     * from somebody else says nothing about whose accounts are in it.
     */
    private static String hash(String value) {
        if (value == null || value.isEmpty()) return "empty";

        int hash = 0x811c9dc5;
        String salted = salt() + value;
        for (int i = 0; i < salted.length(); i++) {
            hash ^= salted.charAt(i);
            hash *= 0x01000193;
        }

        return String.format(Locale.US, "%08x", hash);
    }

    private static volatile String salt;

    private static String salt() {
        String known = salt;
        if (known != null) return known;

        synchronized (FollowDiagnostics.class) {
            if (salt != null) return salt;
            String stored = Settings.DIAGNOSTIC_REPORT_SALT.get();
            if (stored.isEmpty()) {
                stored = Long.toHexString(new java.security.SecureRandom().nextLong());
                Settings.DIAGNOSTIC_REPORT_SALT.save(stored);
            }
            salt = stored;
            return stored;
        }
    }
}
