/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.DiagnosticCategory;
import app.morphe.extension.shared.settings.preference.LogBufferManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small reflection helpers used to read TikTok model objects without compiling against
 * their obfuscated signatures.
 */
public final class Reflect {
    /**
     * Lookups run on every feed bind, and a miss walks the whole class hierarchy throwing
     * once per level, so both hits and misses are remembered per concrete class.
     */
    private static final Object MISSING = new Object();
    private static final Map<String, Object> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Object> FIELDS = new ConcurrentHashMap<>();

    /**
     * Members a caller depends on and this TikTok build does not have. A reader that returns null
     * because the member is gone looks exactly like one that returned null legitimately, and the
     * callers here treat both as "no", so a renamed accessor turns a filter off while its switch
     * still reads on.
     *
     * <p>Only lookups made through {@link #required} land here. Most reads in this class try a
     * getter and then a field, or several named predicates in turn, and expect most of those to
     * miss; recording those would fill the report with names that were never going to resolve.
     */
    private static final int MAX_RECORDED_MISSES = 200;
    private static final Set<String> MISSING_MEMBERS =
            Collections.synchronizedSet(new LinkedHashSet<>());

    private Reflect() {
    }

    /** Every member looked for and not found, first miss first. */
    public static List<String> missingMembers() {
        synchronized (MISSING_MEMBERS) {
            return new ArrayList<>(MISSING_MEMBERS);
        }
    }

    private static void noteMissing(String kind, Class<?> type, String name) {
        String member = type.getName() + '#' + name;
        String entry = kind + ' ' + member;
        // A filter asks this of every card it looks at, so once the answer is known it must not
        // cost a trip through the global monitor.
        if (MISSING_MEMBERS.contains(entry)) {
            return;
        }
        synchronized (MISSING_MEMBERS) {
            if (MISSING_MEMBERS.size() >= MAX_RECORDED_MISSES || !MISSING_MEMBERS.add(entry)) {
                return;
            }
        }

        Logger.printInfo(() -> "This TikTok build has no " + kind + " " + member);
        LogBufferManager.appendEvent(
                DiagnosticCategory.PATCH_ERRORS,
                "Reflect",
                "WARN",
                "no " + kind + " " + member
        );
    }

    /** The no-argument method {@code name} on {@code type} or a superclass, or null. */
    public static Method method(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Object cached = METHODS.get(key);
        if (cached == null) {
            cached = MISSING;
            for (Class<?> current = type; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    cached = method;
                    break;
                } catch (NoSuchMethodException ignored) {
                    // keep climbing
                } catch (Throwable ignored) {
                    break;
                }
            }
            METHODS.put(key, cached);
        }
        return cached == MISSING ? null : (Method) cached;
    }

    /** The field {@code name} on {@code type} or a superclass, made accessible, or null. */
    public static Field field(Class<?> type, String name) {
        String key = type.getName() + '#' + name;
        Object cached = FIELDS.get(key);
        if (cached == null) {
            cached = MISSING;
            for (Class<?> current = type; current != null && current != Object.class;
                 current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    cached = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                    // keep climbing
                } catch (Throwable ignored) {
                    break;
                }
            }
            FIELDS.put(key, cached);
        }
        return cached == MISSING ? null : (Field) cached;
    }

    /**
     * Calls a no-argument method the caller has no fallback for, and reports it once if this
     * build does not have it. Returns null both when the member is gone and when it returned
     * null; {@link #missingMembers()} is what tells those apart afterwards.
     */
    public static Object required(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        Method method = method(target.getClass(), methodName);
        if (method == null) {
            noteMissing("method", target.getClass(), methodName);
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        Method method = method(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Field field = field(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Reads a property that may be exposed either as a getter or as a field.
     * Kotlin data classes in the TikTok feed models use both shapes.
     */
    public static Object property(Object target, String getterName, String fieldName) {
        Object value = invoke(target, getterName);
        return value != null ? value : readField(target, fieldName);
    }

    public static String string(Object target, String getterName, String fieldName) {
        Object value = property(target, getterName, fieldName);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
