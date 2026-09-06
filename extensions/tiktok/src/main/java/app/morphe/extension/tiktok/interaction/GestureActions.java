package app.morphe.extension.tiktok.interaction;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
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

    private static CommentControl commentControl(Object owner) {
        CommentControl control = COMMENTS.get(owner);
        if (control == null) {
            control = new CommentControl();
            COMMENTS.put(owner, control);
        }
        return control;
    }

    public static void registerCommentView(Object owner, View view) {
        for (Map.Entry<Object, CommentControl> entry : COMMENTS.entrySet()) {
            if (entry.getKey() != owner && entry.getValue().view.get() == view) entry.getValue().view.clear();
        }
        commentControl(owner).view = new WeakReference<>(view);
    }

    public static void bindCommentView(Object owner, Object params) {
        Object aweme = Reflect.property(params, "getAweme", "aweme");
        commentControl(owner).videoId = Reflect.string(aweme, "getAid", "aid");
    }

    public static boolean onDoubleTap() {
        String action = Settings.DOUBLE_TAP_ACTION.get();
        if ("nothing".equals(action)) return true;
        if (!"comments".equals(action)) return false;
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort(L10n.t("Comments aren't available for this video"));
        }
        return true;
    }

    /**
     * How far a long press at this point should move the video, in milliseconds. Zero when
     * the press landed in the middle, when edge seeking is off, or when there is no screen
     * to measure the press against.
     */
    static long edgeSeekDelta(MotionEvent event) {
        if (event == null || !Settings.EDGE_SEEK.get()) return 0;
        int seconds = Settings.EDGE_SEEK_SECONDS.get();
        if (seconds <= 0) return 0;
        // The dialog offers at most 60. A restored backup is never asked, and a press that
        // jumps an hour is indistinguishable from the video ending.
        seconds = Math.min(60, seconds);
        Context context = Utils.getContext();
        if (context == null) return 0;
        int width = context.getResources().getDisplayMetrics().widthPixels;
        if (width <= 0) return 0;

        // Whole pixels, so a press exactly on a third lands on one side of the line every
        // time. A screen width times a third does not, and 106.666664 sits below 106.66667.
        int third = width / 3;
        // The listener sits on the cell's touch layer, which fills the window, so the press
        // is placed against the window. getRawX would be the position on the whole display,
        // which in a side by side split view puts every press in the right hand third.
        float x = event.getX();
        if (x < third) return -seconds * 1000L;
        if (x >= width - third) return seconds * 1000L;
        return 0;
    }

    /**
     * Runs before TikTok's own long press handling. Returning true swallows the gesture,
     * which is what keeps the 2x hold and the quick share sheet from also firing.
     */
    public static boolean onLongPress(MotionEvent event) {
        long delta = edgeSeekDelta(event);
        if (delta != 0) {
            // Named, so a post that never reported progress cannot move the video before it.
            String videoId = Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid");
            if (!FeedSeek.seekBy(videoId, delta)) Utils.showToastShort(L10n.t("Nothing is playing to seek"));
            // The edge belongs to the seek whether or not it worked, so the 2x hold that would
            // otherwise start under the finger does not fire on top of it.
            return true;
        }

        String action = Settings.LONG_PRESS_ACTION.get();
        if ("nothing".equals(action)) return true;
        if (!"comments".equals(action)) return false;
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort(L10n.t("Comments aren't available for this video"));
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
