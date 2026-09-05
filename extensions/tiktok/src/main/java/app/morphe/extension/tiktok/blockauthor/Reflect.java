/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Small reflection helpers used to read TikTok model objects without compiling against
 * their obfuscated signatures.
 */
final class Reflect {
    private Reflect() {
    }

    static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Reads a property that may be exposed either as a getter or as a field.
     * Kotlin data classes in the TikTok feed models use both shapes.
     */
    static Object property(Object target, String getterName, String fieldName) {
        Object value = invoke(target, getterName);
        return value != null ? value : readField(target, fieldName);
    }

    static String string(Object target, String getterName, String fieldName) {
        Object value = property(target, getterName, fieldName);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
