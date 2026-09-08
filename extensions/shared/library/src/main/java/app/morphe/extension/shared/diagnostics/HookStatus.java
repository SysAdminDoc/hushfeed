/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.shared.diagnostics;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.preference.LogBufferManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What each hook family found in this TikTok build, and what it did not.
 *
 * <p>The patches attach to code TikTok renames on every release, and a hook that no longer
 * finds its anchor fails quietly: the switch still reads on and the thing it promises simply
 * does not happen. Nothing told anyone, so the lookups report here and the Diagnostics screen
 * shows a family at a time.
 *
 * <p>Only lookups the caller has no fallback for belong here. Most readers try a getter and
 * then a field, or several shapes of row in turn, and expect most of those to miss. Neither
 * does a condition that varies from row to row: this says whether a build is broken, and a
 * single comment cell that came up without its thumbs down is not that.
 *
 * <p>Reporting runs on the layout callback of a scrolling feed, several times per pass, so an
 * answer already known has to cost a hash lookup and nothing else. No lock is taken on a
 * repeat, and the wording of a miss is composed only on the call that records it.
 */
public final class HookStatus {
    /** Enough detail to describe a broken build; past this a family says it stopped counting. */
    private static final int MAX_ENTRIES_PER_FAMILY = 200;

    private static final class Family {
        // newKeySet() is API 24 and D8 cannot backport it, so on Android 6 it throws where the
        // callers catch Throwable and the diagnostics silently record nothing. This shape is
        // the same concurrent set and resolves at API 1.
        /** Raw names, so the hot path compares what the caller already holds. */
        final Set<String> bound = Collections.newSetFromMap(new ConcurrentHashMap<>());
        final Set<String> missed = Collections.newSetFromMap(new ConcurrentHashMap<>());
        /** The same misses in the order they arrived, for the first-miss line. */
        final List<String> order = new CopyOnWriteArrayList<>();
        volatile boolean truncated;
        volatile boolean boundTruncated;
    }

    // Declared as the class rather than Map: putIfAbsent on the Map interface is an API 24
    // default method, and ConcurrentHashMap has carried its own since API 1.
    private static final ConcurrentHashMap<String, Family> FAMILIES = new ConcurrentHashMap<>();
    /** Families in the order the app first touched them; the map does not keep that. */
    private static final List<String> SEEN = new CopyOnWriteArrayList<>();

    private HookStatus() {
    }

    /** A lookup that found what it wanted. A repeat costs one hash lookup and nothing else. */
    public static void bound(String family, String name) {
        Family entry = family(family);
        if (entry.boundTruncated || entry.bound.contains(name)) return;
        if (entry.bound.size() < MAX_ENTRIES_PER_FAMILY) entry.bound.add(name);
        else entry.boundTruncated = true;
    }

    /**
     * A view this build does not have under the id the extension knows it by.
     *
     * <p>An id that stays missing is reported again on every layout pass, so the check comes
     * first and the wording is built only on the pass that records it. Formatting it as an
     * argument would allocate a string per pass and throw it away.
     */
    public static void missingViewId(String family, String name) {
        Family entry = family(family);
        // The truncated check comes first. Past the cap a key is never added, so without this
        // every later pass would miss the set and rebuild the wording it is not going to use.
        if (entry.truncated || entry.missed.contains(name)) return;
        record(entry, family, name, "view id '" + name + "'");
    }

    /** A member the extension asked for by name and this build does not have. */
    public static void missingMember(String family, String kind, String owner, String name) {
        Family entry = family(family);
        if (entry.truncated) return;
        String key = owner + '#' + name;
        if (entry.missed.contains(key)) return;
        record(entry, family, key, kind + " " + owner + "#" + name);
    }

    private static void record(Family entry, String family, String key, String detail) {
        if (entry.missed.size() >= MAX_ENTRIES_PER_FAMILY) {
            entry.truncated = true;
            return;
        }
        if (!entry.missed.add(key)) return;
        entry.order.add(detail);

        String message = "no " + detail + " for " + family;
        Logger.printInfo(() -> "This TikTok build has " + message);
        LogBufferManager.appendEvent(DiagnosticCategory.PATCH_ERRORS, "HookStatus", "WARN", message);
    }

    private static Family family(String name) {
        Family entry = FAMILIES.get(name);
        if (entry != null) return entry;
        Family created = new Family();
        Family raced = FAMILIES.putIfAbsent(name, created);
        if (raced != null) return raced;
        SEEN.add(name);
        return created;
    }

    /** Every lookup that missed, across every family, first miss first. */
    public static List<String> missing() {
        List<String> all = new ArrayList<>();
        for (String name : SEEN) {
            Family entry = FAMILIES.get(name);
            if (entry == null) continue;
            for (String detail : entry.order) all.add(name + ": " + detail);
        }
        return all;
    }

    /** What one family looked for and did not find, first miss first. */
    public static List<String> missing(String family) {
        Family entry = FAMILIES.get(family);
        return entry == null ? new ArrayList<>() : new ArrayList<>(entry.order);
    }

    /** True once any family has reported a miss, so a caller can say "all bound" cheaply. */
    public static boolean anyMissing() {
        for (Family entry : FAMILIES.values()) {
            if (!entry.order.isEmpty()) return true;
        }
        return false;
    }

    /** The families missing something, in the order the app first touched them. */
    public static List<String> familiesMissingSomething() {
        List<String> names = new ArrayList<>();
        for (String name : SEEN) {
            Family entry = FAMILIES.get(name);
            if (entry != null && !entry.order.isEmpty()) names.add(name);
        }
        return names;
    }

    /**
     * One line per family it has heard from: how many lookups bound, how many did not, and the
     * first thing that went missing. A family nothing has touched yet says nothing, because a
     * hook that has not run cannot be called broken. A family that stopped counting says so,
     * rather than letting a catastrophically broken build read like a mildly broken one.
     */
    public static List<String> report() {
        List<String> lines = new ArrayList<>();
        for (String name : SEEN) {
            Family entry = FAMILIES.get(name);
            if (entry == null) continue;
            StringBuilder line = new StringBuilder(name)
                    .append(": ").append(entry.bound.size()).append(" bound, ")
                    .append(entry.order.size()).append(" unbound");
            if (entry.truncated || entry.boundTruncated) {
                line.append(" and more it stopped counting");
            }
            if (!entry.order.isEmpty()) line.append("; first miss: ").append(entry.order.get(0));
            lines.add(line.toString());
        }
        return lines;
    }

    /** Forgets everything, which is what clearing the diagnostic data does. */
    public static void clear() {
        FAMILIES.clear();
        SEEN.clear();
    }
}
