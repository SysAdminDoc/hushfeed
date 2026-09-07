/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.shared.diagnostics;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.preference.LogBufferManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each hook family found in this TikTok build, and what it did not.
 *
 * <p>The patches attach to code TikTok renames on every release, and a hook that no longer
 * finds its anchor fails quietly: the switch still reads on and the thing it promises simply
 * does not happen. Nothing told anyone, so the lookups report here and the Diagnostics screen
 * shows a family at a time.
 *
 * <p>Only lookups the caller has no fallback for belong here. Most readers try a getter and
 * then a field, or several shapes of row in turn, and expect most of those to miss.
 */
public final class HookStatus {
    /** Enough detail to describe a broken build; past this a family stops growing. */
    private static final int MAX_ENTRIES_PER_FAMILY = 100;

    private static final class Family {
        final Set<String> bound = new LinkedHashSet<>();
        final List<String> missing = new ArrayList<>();
    }

    /** Insertion ordered so the report reads in the order the app touched each surface. */
    private static final Map<String, Family> FAMILIES = new LinkedHashMap<>();

    private HookStatus() {
    }

    /** A lookup that found what it wanted. Repeats of the same {@code detail} count once. */
    public static void bound(String family, String detail) {
        synchronized (FAMILIES) {
            Family entry = family(family);
            if (entry.bound.size() < MAX_ENTRIES_PER_FAMILY) entry.bound.add(detail);
        }
    }

    /** A view this build does not have under the id the extension knows it by. */
    public static void missingViewId(String family, String name) {
        record(family, "view id '" + name + "'", "no view id '" + name + "' for " + family);
    }

    /**
     * A view the id resolves to but that this build does not put where the hook looks for
     * it. The id existing and the view being there are separate questions, and a layout
     * TikTok reshuffled answers the first yes and the second no.
     */
    public static void missingView(String family, String name) {
        record(family, "view '" + name + "'",
                "no view '" + name + "' where " + family + " looks for it");
    }

    /** A member the extension asked for by name and this build does not have. */
    public static void missingMember(String family, String kind, String owner, String name) {
        record(family, kind + " " + owner + "#" + name,
                "no " + kind + " " + owner + "#" + name + " for " + family);
    }

    private static void record(String family, String detail, String message) {
        synchronized (FAMILIES) {
            Family entry = family(family);
            if (entry.missing.contains(detail)) return;
            if (entry.missing.size() >= MAX_ENTRIES_PER_FAMILY) return;
            entry.missing.add(detail);
        }

        Logger.printInfo(() -> "This TikTok build has " + message);
        LogBufferManager.appendEvent(DiagnosticCategory.PATCH_ERRORS, "HookStatus", "WARN", message);
    }

    private static Family family(String name) {
        Family entry = FAMILIES.get(name);
        if (entry == null) {
            entry = new Family();
            FAMILIES.put(name, entry);
        }
        return entry;
    }

    /** Every lookup that missed, across every family, first miss first. */
    public static List<String> missing() {
        List<String> all = new ArrayList<>();
        synchronized (FAMILIES) {
            for (Map.Entry<String, Family> entry : FAMILIES.entrySet()) {
                for (String detail : entry.getValue().missing) {
                    all.add(entry.getKey() + ": " + detail);
                }
            }
        }
        return all;
    }

    /** What one family looked for and did not find, first miss first. */
    public static List<String> missing(String family) {
        synchronized (FAMILIES) {
            Family entry = FAMILIES.get(family);
            return entry == null ? new ArrayList<>() : new ArrayList<>(entry.missing);
        }
    }

    /** True once any family has reported a miss, so a caller can say "all bound" cheaply. */
    public static boolean anyMissing() {
        synchronized (FAMILIES) {
            for (Family entry : FAMILIES.values()) {
                if (!entry.missing.isEmpty()) return true;
            }
        }
        return false;
    }

    /**
     * One line per family it has heard from: how many lookups bound, how many did not, and the
     * first thing that went missing. A family nothing has touched yet says nothing, because a
     * hook that has not run cannot be called broken.
     */
    public static List<String> report() {
        List<String> lines = new ArrayList<>();
        synchronized (FAMILIES) {
            for (Map.Entry<String, Family> named : FAMILIES.entrySet()) {
                Family entry = named.getValue();
                StringBuilder line = new StringBuilder(named.getKey())
                        .append(": ").append(entry.bound.size()).append(" bound, ")
                        .append(entry.missing.size()).append(" unbound");
                if (!entry.missing.isEmpty()) {
                    line.append("; first miss: ").append(entry.missing.get(0));
                }
                lines.add(line.toString());
            }
        }
        return lines;
    }

    /** Cleared between tests; nothing in the app resets this. */
    public static void resetForTests() {
        synchronized (FAMILIES) {
            FAMILIES.clear();
        }
    }
}
