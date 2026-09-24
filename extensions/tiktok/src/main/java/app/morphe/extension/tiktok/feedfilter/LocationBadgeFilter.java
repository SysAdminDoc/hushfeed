/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.FeedFilterCounters;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** A place badge is an explicit content preference, not proof that a post is a paid ad. */
public final class LocationBadgeFilter implements IFilter {
    private static final String FAMILY = "location badges";
    /** The export's route for the strips above a caption, one list per read of a video's strips. */
    static final String STRIP_SOURCE = "CaptionStrip";
    static final String PLACE_REASON = "placeLabel";
    private static final FeedServedKinds STRIP_KINDS = new FeedServedKinds("Caption strip");

    @Override public boolean getEnabled() { return Settings.FILTER_LOCATION_VIDEOS.get(); }

    @Override public boolean getFiltered(Aweme item) { return hasBadge(item); }

    public static boolean hasBadge(Aweme item) {
        Object value = Reflect.required(item, "getAnchors", FAMILY);
        if (!(value instanceof List<?>)) return false;
        try {
            for (Object anchor : (List<?>) value) if (isLocation(anchor)) return true;
        } catch (RuntimeException error) {
            HookStatus.threw(FAMILY, "read anchors", error);
        }
        return false;
    }

    private static boolean isLocation(Object anchor) {
        // This is TikTok's component key, not a caption, place name, locale or broad POI field.
        return "anchor_poi".equals(Reflect.required(anchor, "getComponentKey", FAMILY));
    }

    /** Called only by the native badge renderer. Never edits Aweme.getAnchors() or its elements. */
    public static List<?> visibleAnchors(List<?> anchors) {
        HookStatus.bound(FAMILY, "native badge list");
        countStrips(anchors);
        if (!Settings.HIDE_LOCATION_LABELS.get() || anchors == null || anchors.isEmpty()) return anchors;
        try {
            ArrayList<Object> kept = null;
            for (int i = 0; i < anchors.size(); i++) {
                Object anchor = anchors.get(i);
                if (isLocation(anchor)) {
                    if (kept == null) kept = new ArrayList<>(anchors.subList(0, i));
                } else if (kept != null) {
                    kept.add(anchor);
                }
            }
            if (kept == null) return anchors;
            HookStatus.bound(FAMILY, "location entries hidden");
            FeedFilterCounters.removed(STRIP_SOURCE, anchors.size() - kept.size(), PLACE_REASON);
            return kept;
        } catch (RuntimeException error) {
            HookStatus.threw(FAMILY, "badge list", error);
            Logger.printException(() -> "Could not hide location badges", error);
            return anchors;
        }
    }

    /**
     * Counts the strips the renderer is handed, by kind, whatever the logging switch and the
     * place switches say, so an export says which strips a phone is served. Strips that promote
     * something else (a Lemon8 link, a "Find ..." search) reach some phones and not others, so
     * their keys have to come from the phones that get them.
     */
    private static void countStrips(List<?> anchors) {
        if (anchors == null) return;
        try {
            FeedFilterCounters.sawList(STRIP_SOURCE, anchors.size());
            for (Object anchor : anchors) {
                if (anchor == null) continue;
                String kind = FeedServedKinds.kind(optional(anchor, "getComponentKey"), optional(anchor, "getType"));
                if (kind == null) continue;
                FeedFilterCounters.sawKind(STRIP_SOURCE, kind);
                STRIP_KINDS.note(kind);
            }
        } catch (RuntimeException error) {
            HookStatus.threw(FAMILY, "count strip kinds", error);
        }
    }

    /** A no-argument read that is only diagnostic, so a build without it is no hook failure. */
    private static Object optional(Object target, String name) {
        Method method = Reflect.method(target.getClass(), name);
        if (method == null) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    /** Forgets the strip kinds already named, between deterministic runtime tests. */
    static void resetStripKindsForTests() {
        STRIP_KINDS.resetForTests();
    }
}
