/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Descriptors of TikTok's block endpoint, discovered at patch time.
 *
 * The patch rewrites each accessor below to return the value it found by reading the
 * Retrofit path annotations out of the APK, so the extension never has to hard code an
 * obfuscated class or method name. An accessor that still returns an empty string means
 * the patch could not find the endpoint in this build.
 */
public final class BlockApiDescriptors {
    private BlockApiDescriptors() {
    }

    /** Rewritten by the patch, for example {@code LX/0abc;}. */
    public static String patchedServiceClassName() {
        return "";
    }

    /** Rewritten by the patch, for example {@code LIZ}. */
    public static String patchedMethodName() {
        return "";
    }

    /** Rewritten by the patch: comma separated smali parameter descriptors. */
    public static String patchedParameterTypes() {
        return "";
    }

    /** Rewritten by the patch, for example {@code /aweme/v1/user/block/}. */
    public static String patchedEndpointPath() {
        return "";
    }

    static boolean isResolved() {
        return !serviceClassName().isEmpty() && !methodName().isEmpty();
    }

    /** @return the service interface as a binary class name, or an empty string. */
    static String serviceClassName() {
        return toBinaryClassName(patchedServiceClassName());
    }

    static String methodName() {
        return patchedMethodName();
    }

    static String endpointPath() {
        return patchedEndpointPath();
    }

    /** @return the parameter types as binary class names, in declaration order. */
    static List<String> parameterTypes() {
        String raw = patchedParameterTypes();
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> types = new ArrayList<>();
        for (String descriptor : raw.split(",")) {
            String trimmed = descriptor.trim();
            if (!trimmed.isEmpty()) {
                types.add(toBinaryClassName(trimmed));
            }
        }
        return types;
    }

    /** Converts a smali type descriptor such as {@code Ljava/lang/String;} to a class name. */
    private static String toBinaryClassName(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return "";
        }
        switch (descriptor) {
            case "I":
                return "int";
            case "J":
                return "long";
            case "Z":
                return "boolean";
            case "F":
                return "float";
            case "D":
                return "double";
            case "B":
                return "byte";
            case "S":
                return "short";
            case "C":
                return "char";
            default:
                break;
        }
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
        // Already a binary name, or an array type the caller will reject.
        return descriptor.replace('/', '.');
    }
}
