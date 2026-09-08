package app.morphe.extension.tiktok.interaction;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class AutoAdvance {
    // Not Supplier: java.util.function arrived at API 24, and a type D8 cannot backport fails
    // to resolve on Android 6, which the payload's own floor of API 23 still allows.
    /** Reads the host's current auto scroll state, which is an enum this cannot name. */
    interface StateReader {
        Object read();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Control> CONTROLS = new WeakHashMap<>();
    private static boolean observing;
    private static final SharedPreferences.OnSharedPreferenceChangeListener PREFERENCES = (preferences, key) -> {
        if (key == null || Settings.AUTO_ADVANCE.key.equals(key)
                || Settings.AUTO_ADVANCE_LIMIT.key.equals(key)) MAIN.post(() -> {
            for (Object component : new ArrayList<>(CONTROLS.keySet())) update(component);
        });
    };
    private AutoAdvance() { }

    public static boolean available(boolean nativeValue) { return Settings.AUTO_ADVANCE.get() || nativeValue; }

    public static void onView(Object component, View view) {
        if (Looper.myLooper() != Looper.getMainLooper() || component == null || view == null) return;
        if (!observing) {
            Setting.preferences.preferences.registerOnSharedPreferenceChangeListener(PREFERENCES);
            observing = true;
        }
        Control next = new Control(view);
        Control previous = CONTROLS.put(component, next);
        next.owned = previous != null && previous.owned;
        onResume(component);
    }

    public static void onResume(Object component) {
        WeakReference<Object> owner = new WeakReference<>(component);
        MAIN.post(() -> update(owner.get()));
    }

    public static void beforeCompletion(Object component, String completedId) {
        if (Looper.myLooper() != Looper.getMainLooper() || completedId == null) return;
        Control control = CONTROLS.get(component);
        if (control == null || !control.owned) return;
        // Before the completion is recorded, not after. Reaching the hold check further down
        // through update() stood down one video late: the video that finished behind the panel
        // still spent a place in this session's limit, and could put its "stopped after N
        // videos" toast on top of the hold.
        if (SessionBudget.isLocked()) {
            update(component);
            return;
        }
        String current = Reflect.string(readAweme(component), "getAid", "aid");
        if (!completedId.equals(current)) return;
        if (!control.recordCompletion(completedId)) return;
        if (control.limitReached() && control.claimLimitNotice()) {
            Utils.showToastShort(control.completedCount == 1
                    ? L10n.t("Automatic advance stopped after one video")
                    : L10n.f("Automatic advance stopped after %1$d videos",
                            control.completedCount));
        }
        update(component);
    }

    public static void onDestroy(Object component) { CONTROLS.remove(component); }

    private static void update(Object component) {
        Control control = CONTROLS.get(component);
        if (control == null) return;
        try {
            control.update(() -> readState(component), () -> start(component), () -> stop(component));
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not update automatic advance", error);
        }
    }

    static final class Control {
        final WeakReference<View> view;
        boolean owned;
        int completedCount;
        private String lastCompletedId;
        private boolean limitNoticeShown;
        Control(View view) { this.view = new WeakReference<>(view); }

        boolean recordCompletion(String completedId) {
            if (!owned || completedId.equals(lastCompletedId)) return false;
            lastCompletedId = completedId;
            completedCount++;
            return true;
        }

        boolean limitReached() {
            int limit = Settings.AUTO_ADVANCE_LIMIT.get();
            return limit > 0 && completedCount >= limit;
        }

        boolean claimLimitNotice() {
            if (!limitReached() || limitNoticeShown) return false;
            limitNoticeShown = true;
            return true;
        }

        void update(StateReader state, Runnable start, Runnable stop) {
            if (!Settings.AUTO_ADVANCE.get()) {
                if (owned) { stop.run(); owned = false; }
                return;
            }
            if (limitReached()) {
                if (owned) { stop.run(); owned = false; }
                return;
            }
            // A hold covers the feed. Advancing behind it walks through videos nobody can see,
            // and each one used to spend a place in this session's own limit as well. Ownership
            // is released rather than only stopped, so the next call after the hold ends starts
            // it again from the video that is actually on screen.
            if (SessionBudget.isLocked()) {
                if (owned) { stop.run(); owned = false; }
                return;
            }
            View live = view.get();
            if (live == null || !live.isAttachedToWindow() || !live.isShown() || !live.hasWindowFocus()) return;
            if (!named(state.read(), "AUTO_SCROLL_STATE_STOP")) return;
            start.run();
            owned = !named(state.read(), "AUTO_SCROLL_STATE_STOP");
        }
    }

    private static boolean named(Object state, String name) {
        return state instanceof Enum<?> && ((Enum<?>) state).name().equals(name);
    }

    // Replaced with native calls when the patch is applied.
    private static Object readAweme(Object component) { return null; }
    private static Object readState(Object component) { return null; }
    private static void start(Object component) { }
    private static void stop(Object component) { }
}
