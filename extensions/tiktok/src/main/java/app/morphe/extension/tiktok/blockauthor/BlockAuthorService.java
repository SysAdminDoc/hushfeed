/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
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

    private static final int BLOCK = 1;
    private static final int UNBLOCK = 0;

    /**
     * The {@code source} argument. TikTok passes an origin code that only affects its own
     * analytics; zero is the unspecified value.
     */
    private static final int SOURCE_UNSPECIFIED = 0;

    private static volatile Object cachedService;
    private static volatile Method cachedBlockMethod;

    private BlockAuthorService() {
    }

    interface Callback {
        void onResult(boolean success, String message);
    }

    /** Blocks {@code author}. Runs on a background thread. */
    static void block(VideoAuthor author, Callback callback) {
        submit(author, BLOCK, callback);
    }

    /** Reverses a block, used by the undo action. */
    static void unblock(VideoAuthor author, Callback callback) {
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

        // Mirrors TikTok's own call site: execute synchronously, and treat a request that
        // returns without throwing as accepted.
        Method execute = call.getClass().getMethod("execute");
        execute.setAccessible(true);
        Object response = execute.invoke(call);

        return response != null;
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
