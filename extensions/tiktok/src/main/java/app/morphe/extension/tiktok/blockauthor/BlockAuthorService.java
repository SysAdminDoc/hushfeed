/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Performs the block and unblock calls.
 *
 * TikTok exposes its block endpoint through {@code BlockApi}, whose names survive
 * obfuscation:
 *
 * <pre>
 * public final class BlockApi {
 *     public static final BlockService LIZ = ... .create(BlockService.class);
 * }
 *
 * public interface BlockApi$BlockService {
 *     &#64;GET("/aweme/v1/user/block/")
 *     Call&lt;BlockStruct&gt; block(&#64;Query("user_id") String userId,
 *                             &#64;Query("sec_user_id") String secUserId,
 *                             &#64;Query("block_type") int blockType,
 *                             &#64;Query("source") int source);
 * }
 * </pre>
 *
 * Reusing the app's own service instance means the request is signed and routed exactly
 * as TikTok's own block action is. Only the static field holding the service is
 * obfuscated, so it is located by type rather than by name.
 */
public final class BlockAuthorService {
    private static final String BLOCK_API = "com.ss.android.ugc.aweme.profile.api.BlockApi";
    private static final String BLOCK_SERVICE = BLOCK_API + "$BlockService";
    private static final String BLOCK_STRUCT = "com.ss.android.ugc.aweme.profile.model.BlockStruct";

    /**
     * The {@code block_type} argument. Both values were read off TikTok's own IM block
     * controller, which branches on whether the account is already blocked:
     * {@code BlockUserController.LIZ()} shows the unblock dialog when {@code isBlock()} is
     * true and the block dialog otherwise, and the two confirm buttons reach
     * {@code LIZJ(0, uid, secUid)} and {@code LIZJ(1, uid, secUid)} respectively. That int
     * is handed straight to {@code BlockApi.blockUser} and lands on the
     * {@code @Query("block_type")} parameter of the same endpoint this class calls.
     */
    private static final int BLOCK = 1;
    private static final int UNBLOCK = 0;

    /**
     * The {@code source} argument. TikTok passes an origin code that only affects its own
     * analytics; zero is the unspecified value.
     */
    private static final int SOURCE_UNSPECIFIED = 0;

    private static volatile Object cachedService;
    private static volatile Method cachedBlockMethod;
    private static volatile boolean warnedUnreadableBody;

    private BlockAuthorService() {
    }

    public interface Callback {
        void onResult(boolean success, String message);
    }

    /** Blocks {@code author}. Runs on a background thread. */
    public static void block(VideoAuthor author, Callback callback) {
        submit(author, BLOCK, callback);
    }

    /** Reverses a block, used by the undo action. */
    public static void unblock(VideoAuthor author, Callback callback) {
        submit(author, UNBLOCK, callback);
    }

    private static void submit(VideoAuthor author, int blockType, Callback callback) {
        Utils.runOnBackgroundThread(() -> {
            boolean success = false;
            String message = null;

            try {
                success = execute(author, blockType);
                if (!success) {
                    message = "TikTok rejected the request";
                }
            } catch (UnsupportedOperationException ex) {
                message = ex.getMessage();
                Logger.printInfo(() -> "Block endpoint unavailable: " + ex.getMessage());
            } catch (Throwable ex) {
                message = "Request failed";
                Logger.printException(() -> "Block request failed", ex);
            }

            final boolean result = success;
            final String finalMessage = message;
            Utils.runOnMainThread(() -> callback.onResult(result, finalMessage));
        });
    }

    private static boolean execute(VideoAuthor author, int blockType) throws Exception {
        Method block = blockMethod();
        Object service = service();

        // The endpoint accepts either identifier and ignores an empty one.
        String uid = author.uid == null ? "" : author.uid;
        String secUid = author.secUid == null ? "" : author.secUid;

        Logger.printDebug(() -> "Sending block_type=" + blockType + " for " + author.label());

        Object call = block.invoke(service, uid, secUid, blockType, SOURCE_UNSPECIFIED);
        if (call == null) {
            return false;
        }

        // Mirrors TikTok's own call site: execute synchronously, then read the status code
        // off the response body. TikTok reports a refusal (rate limited, already blocked)
        // as a body with a non-zero status code rather than an exception.
        Method execute = call.getClass().getMethod("execute");
        execute.setAccessible(true);
        Object response = execute.invoke(call);
        if (response == null) {
            return false;
        }

        Integer status = statusCodeOf(response);
        if (status == null) {
            // Body shape unknown: a request that came back without throwing is the best
            // signal available.
            return true;
        }
        if (status != 0) {
            Logger.printInfo(() -> "Block request refused with status " + status);
        }
        return status == 0;
    }

    /**
     * Digs the {@code BlockStruct} body out of the response wrapper and reads its status
     * code. The wrapper's field names are obfuscated, but the body's class name is not, so
     * the body is located by declared type.
     */
    private static Integer statusCodeOf(Object response) {
        Object body = Reflect.invoke(response, "body");
        if (body == null) {
            body = findBlockStruct(response);
        }
        if (body == null) {
            if (!warnedUnreadableBody) {
                warnedUnreadableBody = true;
                Logger.printInfo(() -> "Block reply body could not be read from "
                        + response.getClass().getName() + "; refusals will not be detected");
            }
            return null;
        }

        Object status = Reflect.property(body, "getStatusCode", "status_code");
        if (status == null) {
            status = Reflect.readField(body, "statusCode");
        }
        return status instanceof Number ? ((Number) status).intValue() : null;
    }

    /**
     * The wrapper keeps the body in a generic field, whose declared type is {@code Object}
     * once erased, so the body has to be found by the class of the value it holds.
     */
    private static Object findBlockStruct(Object response) {
        Class<?> type = response.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(response);
                    if (value != null && BLOCK_STRUCT.equals(value.getClass().getName())) {
                        return value;
                    }
                } catch (Exception ignored) {
                    // Inaccessible field; keep looking.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Method blockMethod() throws Exception {
        Method cached = cachedBlockMethod;
        if (cached != null) {
            return cached;
        }

        Class<?> serviceInterface = Class.forName(BLOCK_SERVICE);
        Method block = serviceInterface.getMethod(
                "block", String.class, String.class, int.class, int.class);
        block.setAccessible(true);

        cachedBlockMethod = block;
        return block;
    }

    /**
     * Reads the service instance out of {@code BlockApi}. The field name is obfuscated and
     * changes between builds, so it is found by its declared type instead.
     */
    private static Object service() throws Exception {
        Object cached = cachedService;
        if (cached != null) {
            return cached;
        }

        Class<?> blockApi = Class.forName(BLOCK_API);
        Class<?> serviceInterface = Class.forName(BLOCK_SERVICE);

        for (Field field : blockApi.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!serviceInterface.isAssignableFrom(field.getType())) {
                continue;
            }

            field.setAccessible(true);
            Object service = field.get(null);
            if (service != null) {
                cachedService = service;
                return service;
            }
        }

        throw new UnsupportedOperationException("Could not read TikTok's block service");
    }
}
