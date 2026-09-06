package app.morphe.extension.tiktok.interaction;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.ref.WeakReference;

public final class TapConfirmation {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> armed = new WeakReference<>(null);
    private static String armedKey;
    private static long armedAt;
    private static Drawable previousForeground;
    private static GradientDrawable ring;
    private static int generation;
    private TapConfirmation() {}

    public static void onVideoChanged() {
        if (Looper.myLooper() == Looper.getMainLooper()) clear();
        else MAIN.post(TapConfirmation::clear);
    }

    public static boolean follow(View view) {
        Object video = CurrentVideoAuthor.getAweme();
        Object user = Reflect.property(video, "getAuthor", "author");
        Object status = Reflect.property(user, "getFollowStatus", "followStatus");
        // The same native control can turn into a message button after following.
        boolean enabled = Settings.CONFIRM_FOLLOW.get()
                && (!(status instanceof Number) || ((Number) status).intValue() == 0);
        return allow(view, "follow", Reflect.string(video, "getAid", "aid"), enabled);
    }

    public static boolean like(View view) {
        Object video = CurrentVideoAuthor.getAweme();
        boolean enabled = Settings.CONFIRM_LIKE.get() && !Boolean.TRUE.equals(Reflect.invoke(video, "isLike"));
        return allow(view, "like", Reflect.string(video, "getAid", "aid"), enabled);
    }

    static boolean allow(View view, String action, String id, boolean enabled) {
        if (!enabled || view == null) {
            clear();
            return true;
        }
        String key = action + ":" + id;
        long now = SystemClock.uptimeMillis();
        if (id != null && armed.get() == view && key.equals(armedKey) && now - armedAt < 4000) {
            clear();
            return true;
        }
        clear();
        armed = new WeakReference<>(view);
        armedKey = key;
        armedAt = now;
        previousForeground = view.getForeground();
        ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(1000);
        ring.setStroke(Math.max(2, Math.round(2 * view.getResources().getDisplayMetrics().density)),
                app.morphe.extension.tiktok.settings.preference.SettingsUi.OVERLAY_ACCENT);
        view.setForeground(ring);
        int token = generation;
        MAIN.postDelayed(() -> { if (token == generation) clear(); }, 4000);
        Utils.showToastShort("Tap again to " + action);
        return false;
    }

    private static void clear() {
        View view = armed.get();
        if (view != null && view.getForeground() == ring) view.setForeground(previousForeground);
        armed.clear();
        armedKey = null;
        previousForeground = null;
        ring = null;
        generation++;
    }
}
