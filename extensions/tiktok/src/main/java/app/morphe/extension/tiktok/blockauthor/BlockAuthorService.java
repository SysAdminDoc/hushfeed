/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs the block and unblock calls.
 *
 * The request goes through TikTok's own Retrofit stack rather than a hand rolled HTTP
 * call, so the app applies its usual request signing. The service interface and method
 * are discovered at patch time (see {@link BlockApiDescriptors}); the arguments are
 * matched at runtime by reading the {@code @Query} names off the interface method, which
 * avoids depending on the parameter order of any one build.
 */
public final class BlockAuthorService {
    private static final int BLOCK = 1;
    private static final int UNBLOCK = 0;

    /** ByteDance's Retrofit factory. Kept unobfuscated in TikTok builds. */
    private static final String RETROFIT_UTILS = "com.bytedance.ttnet.utils.RetrofitUtils";

    /** Query parameter names the block endpoint understands. */
    private static final String QUERY_USER_ID = "user_id";
    private static final String QUERY_SEC_USER_ID = "sec_user_id";
    private static final String QUERY_BLOCK_TYPE = "block_type";
    private static final String QUERY_SOURCE = "source";

    private static final Map<String, Object> SERVICE_CACHE = new HashMap<>();

    private BlockAuthorService() {
    }

    /** Blocks {@code author}. Runs on a background thread. */
    static void block(VideoAuthor author, Callback callback) {
        submit(author, BLOCK, callback);
    }

    /** Reverses a block, used by the undo action. */
    static void unblock(VideoAuthor author, Callback callback) {
        submit(author, UNBLOCK, callback);
    }

    interface Callback {
        void onResult(boolean success, String message);
    }

    private static void submit(VideoAuthor author, int blockType, Callback callback) {
        Utils.runOnBackgroundThread(() -> {
            boolean success = false;
            String message;

            try {
                success = execute(author, blockType);
                message = success
                        ? null
                        : "TikTok rejected the request";
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
        if (!BlockApiDescriptors.isResolved()) {
            throw new UnsupportedOperationException(
                    "The block endpoint was not found in this TikTok build");
        }

        Class<?> serviceInterface = Class.forName(BlockApiDescriptors.serviceClassName());
        Method endpoint = findEndpoint(serviceInterface);
        if (endpoint == null) {
            throw new UnsupportedOperationException("The block endpoint signature changed");
        }

        Object service = serviceFor(serviceInterface);
        if (service == null) {
            throw new UnsupportedOperationException("Could not create the block API service");
        }

        Object[] arguments = buildArguments(endpoint, author, blockType);
        endpoint.setAccessible(true);
        Object response = endpoint.invoke(service, arguments);

        return awaitResult(response);
    }

    private static Method findEndpoint(Class<?> serviceInterface) {
        String name = BlockApiDescriptors.methodName();
        List<String> parameterTypes = BlockApiDescriptors.parameterTypes();

        Method fallback = null;
        for (Method method : serviceInterface.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (fallback == null) {
                fallback = method;
            }
            if (matchesParameters(method, parameterTypes)) {
                return method;
            }
        }
        return fallback;
    }

    private static boolean matchesParameters(Method method, List<String> expected) {
        Class<?>[] actual = method.getParameterTypes();
        if (actual.length != expected.size()) {
            return false;
        }
        for (int index = 0; index < actual.length; index++) {
            if (!actual[index].getName().equals(expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fills the endpoint arguments by query name, so a build that reorders or adds
     * parameters still gets a well formed request. Anything unrecognised is left at its
     * type default, which TikTok treats as "not supplied".
     */
    private static Object[] buildArguments(Method endpoint, VideoAuthor author, int blockType) {
        Class<?>[] parameterTypes = endpoint.getParameterTypes();
        Annotation[][] parameterAnnotations = endpoint.getParameterAnnotations();
        Object[] arguments = new Object[parameterTypes.length];

        Map<String, Object> byQueryName = new LinkedHashMap<>();
        byQueryName.put(QUERY_USER_ID, author.uid);
        byQueryName.put(QUERY_SEC_USER_ID, author.secUid);
        byQueryName.put(QUERY_BLOCK_TYPE, blockType);
        byQueryName.put(QUERY_SOURCE, 0);

        for (int index = 0; index < parameterTypes.length; index++) {
            String queryName = queryNameOf(parameterAnnotations[index]);
            Object value = queryName == null ? null : byQueryName.get(queryName);
            arguments[index] = coerce(value, parameterTypes[index]);
        }

        Logger.printDebug(() -> "Block request " + endpoint.getName()
                + " path=" + BlockApiDescriptors.endpointPath()
                + " args=" + Arrays.toString(arguments));

        return arguments;
    }

    /** Reads the value of a Retrofit {@code @Query} or {@code @Field} parameter annotation. */
    private static String queryNameOf(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String type = annotation.annotationType().getName();
            if (!type.endsWith(".Query") && !type.endsWith(".Field")) {
                continue;
            }
            Object value = Reflect.invoke(annotation, "value");
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    private static Object coerce(Object value, Class<?> type) {
        if (type == int.class || type == Integer.class) {
            int number = value instanceof Number ? ((Number) value).intValue() : 0;
            return number;
        }
        if (type == long.class || type == Long.class) {
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.TRUE.equals(value);
        }
        if (type == String.class) {
            return value instanceof String ? value : "";
        }
        return null;
    }

    private static Object serviceFor(Class<?> serviceInterface) throws Exception {
        synchronized (SERVICE_CACHE) {
            Object cached = SERVICE_CACHE.get(serviceInterface.getName());
            if (cached != null) {
                return cached;
            }
        }

        Class<?> retrofitUtils = Class.forName(RETROFIT_UTILS);
        Method createService = null;
        for (Method method : retrofitUtils.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == Class.class) {
                createService = method;
                break;
            }
        }
        if (createService == null) {
            throw new UnsupportedOperationException("Could not find the Retrofit service factory");
        }

        createService.setAccessible(true);
        Object service = createService.invoke(null, ApiHost.baseUrl(), serviceInterface);

        if (service != null) {
            synchronized (SERVICE_CACHE) {
                SERVICE_CACHE.put(serviceInterface.getName(), service);
            }
        }
        return service;
    }

    /**
     * Resolves the response. The endpoint returns either a parsed model or one of
     * ByteDance's call wrappers, so the wrapper is executed when present.
     */
    private static boolean awaitResult(Object response) {
        if (response == null) {
            return false;
        }

        Object executed = Reflect.invoke(response, "execute");
        Object body = executed != null ? Reflect.invoke(executed, "body") : response;
        Object payload = body != null ? body : executed;

        Object statusCode = Reflect.property(payload, "getStatusCode", "status_code");
        if (statusCode instanceof Number) {
            return ((Number) statusCode).intValue() == 0;
        }

        // No status field to read: a response that did not throw is treated as accepted.
        return true;
    }
}
