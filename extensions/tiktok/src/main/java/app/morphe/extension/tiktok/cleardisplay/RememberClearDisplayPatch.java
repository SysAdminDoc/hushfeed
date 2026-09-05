package app.morphe.extension.tiktok.cleardisplay;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class RememberClearDisplayPatch {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String currentId;
    private static Runnable pending;
    private static boolean posting;

    // Kept for already-patched first-frame hooks.
    public static boolean getClearDisplayState() {
        return !Settings.AUTOMATIC_CLEAR_DISPLAY.get() && Settings.CLEAR_DISPLAY.get();
    }

    public static void onFirstFrame(Object controller) {
        WeakReference<Object> owner = new WeakReference<>(controller);
        MAIN.post(() -> {
            Object player = owner.get();
            if (player == null) return;
            String id = videoId(player);
            firstFrame(id, () -> {
                Object live = owner.get();
                Object context = Reflect.readField(live, "activity");
                return live != null && id != null && id.equals(videoId(live))
                        && context instanceof Activity && !((Activity) context).isFinishing()
                        && !((Activity) context).isDestroyed() && ((Activity) context).hasWindowFocus();
            }, RememberClearDisplayPatch::postClear);
        });
    }

    private static String videoId(Object controller) {
        return Reflect.string(readCurrentAweme(controller), "getAid", "aid");
    }

    static void firstFrame(String id, BooleanSupplier stillCurrent, Consumer<Boolean> event) {
        if (id == null || id.isEmpty()) return;
        if (!Settings.AUTOMATIC_CLEAR_DISPLAY.get()) {
            cancel();
            currentId = null;
            if (Settings.CLEAR_DISPLAY.get()) emit(event, true);
            return;
        }
        if (id.equals(currentId)) return;
        cancel();
        currentId = id;
        emit(event, false);
        pending = () -> {
            pending = null;
            if (Settings.AUTOMATIC_CLEAR_DISPLAY.get() && id.equals(currentId) && stillCurrent.getAsBoolean()) {
                emit(event, true);
            }
        };
        MAIN.postDelayed(pending, Math.max(0, Math.min(30000, Settings.AUTOMATIC_CLEAR_DISPLAY_DELAY.get())));
    }

    private static void emit(Consumer<Boolean> event, boolean clear) {
        posting = true;
        try { event.accept(clear); }
        catch (RuntimeException error) { Logger.printException(() -> "Could not change clear display", error); }
        finally { posting = false; }
    }

    static void cancel() {
        if (pending != null) MAIN.removeCallbacks(pending);
        pending = null;
    }

    public static void rememberClearDisplayEvent(Object event) {
        if (event == null) return;
        Object clear = Reflect.readField(event, "LIZ");
        Object type = Reflect.readField(event, "LIZIZ");
        if (!(clear instanceof Boolean) || !(type instanceof Integer)) return;
        if ((Integer) type == 3 || (Integer) type == 9) return;
        if (posting) return;
        if (Looper.myLooper() == Looper.getMainLooper()) cancel();
        else MAIN.post(RememberClearDisplayPatch::cancel);
        Settings.CLEAR_DISPLAY.save((Boolean) clear);
    }

    // Resolved from native first-frame code and clear-display event at patch time.
    private static Object readCurrentAweme(Object controller) { return null; }
    private static void postClear(boolean clear) { }
}
