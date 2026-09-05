package app.morphe.extension.tiktok.interaction;

import android.graphics.Rect;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

public final class GestureActions {
    private static final Map<Object, CommentControl> COMMENTS = new WeakHashMap<>();
    private GestureActions() {}

    private static final class CommentControl {
        WeakReference<View> view = new WeakReference<>(null);
        String videoId;
    }

    public static void registerCommentView(Object owner, View view) {
        COMMENTS.computeIfAbsent(owner, ignored -> new CommentControl()).view = new WeakReference<>(view);
    }

    public static void bindCommentView(Object owner, Object params) {
        Object aweme = Reflect.property(params, "getAweme", "aweme");
        COMMENTS.computeIfAbsent(owner, ignored -> new CommentControl()).videoId = Reflect.string(aweme, "getAid", "aid");
    }

    public static boolean onDoubleTap() {
        String action = Settings.DOUBLE_TAP_ACTION.get();
        if ("nothing".equals(action)) return true;
        if (!"comments".equals(action)) return false;
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort("Comments aren't available for this video");
        }
        return true;
    }

    static boolean openComments(String videoId) {
        if (videoId == null || videoId.isEmpty()) return false;
        View hidden = null;
        for (CommentControl control : COMMENTS.values()) {
            View view = control.view.get();
            if (!videoId.equals(control.videoId) || view == null || !view.isAttachedToWindow()) continue;
            if (view.isShown() && view.getGlobalVisibleRect(new Rect())) return click(view);
            // Clear display can hide the action rail while its native click handler remains usable.
            hidden = view;
        }
        return hidden != null && click(hidden);
    }

    private static boolean click(View view) {
        try {
            return view.performClick();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not open comments from the feed gesture", exception);
            return false;
        }
    }
}
