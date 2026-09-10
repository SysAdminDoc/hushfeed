package app.morphe.extension.tiktok.captions;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class CaptionTools {
    private static final Map<Object, String> OWNERS = new WeakHashMap<>();
    private static WeakReference<TextView> overlay = new WeakReference<>(null);
    private static WeakReference<View> captionSource = new WeakReference<>(null);
    private static String currentId, cueId, cue = "";
    private static boolean clear;

    public static void onVideoChanged(String id) {
        Utils.runOnMainThreadNowOrLater(() -> {
            if (Objects.equals(currentId, id)) return;
            currentId = id;
            cueId = null;
            cue = "";
            clear = false;
            refresh();
        });
    }

    public static void onClear(Object aweme, boolean enabled) {
        String id = Reflect.string(aweme, "getAid", "aid");
        Utils.runOnMainThreadNowOrLater(() -> {
            if (id != null && id.equals(currentId)) {
                clear = enabled;
                refresh();
            }
        });
    }

    public static void onCaption(Object renderer, String id, Object state, String text) {
        try {
            FrameLayout root = rootOf(renderer);
            if (root == null) return;
            CaptionStyle.apply(root);
            if (id != null) OWNERS.put(renderer, id);
            String owner = OWNERS.get(renderer);
            if (owner == null || !owner.equals(currentId)) return;
            captionSource = new WeakReference<>(root);
            cueId = owner;
            cue = "EXPANDED".equals(String.valueOf(state)) && text != null ? text : "";
            if (Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.get()) attach(root);
            refresh();
        } catch (RuntimeException error) { Logger.printException(() -> "Could not update captions", error); }
    }

    private static void attach(View source) {
        Activity activity = activity(source.getContext());
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        TextView existing = overlay.get();
        if (existing != null && existing.getRootView() == decor) return;
        if (existing != null && existing.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) existing.getParent()).removeView(existing);
        }
        if (!(decor instanceof FrameLayout)) return;
        TextView text = new TextView(activity);
        text.setTextColor(Color.WHITE);
        text.setGravity(Gravity.CENTER);
        // In pixels, so on a 3x screen this was about 0.7dp of blur. It is what separates white
        // caption text from the video when the reader picks the transparent background.
        float density = activity.getResources().getDisplayMetrics().density;
        text.setShadowLayer(2 * density, 0, density, Color.BLACK);
        text.setClickable(false);
        text.setFocusable(false);
        int padding = Math.round(12 * text.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding / 2, padding, padding / 2);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = padding * 8;
        params.leftMargin = params.rightMargin = padding * 2;
        text.setVisibility(View.GONE);
        ((FrameLayout) decor).addView(text, params);
        overlay = new WeakReference<>(text);
        ViewTreeObserver.OnPreDrawListener draw = () -> { refresh(); return true; };
        decor.getViewTreeObserver().addOnPreDrawListener(draw);
        text.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnPreDrawListener(draw);
            }
        });
    }

    static void refresh() {
        TextView text = overlay.get();
        if (text == null) return;
        Activity activity = activity(text.getContext());
        View source = captionSource.get();
        boolean visible = Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.get() && clear && !cue.isEmpty()
                && cueId != null && cueId.equals(currentId) && source != null && source.isAttachedToWindow()
                && activity != null && !activity.isFinishing() && !activity.isDestroyed()
                && activity.hasWindowFocus() && FeedVisibility.isOnFeed(activity);
        if (visible) {
            if (!cue.contentEquals(text.getText())) text.setText(cue);
            int size = CaptionStyle.size();
            float wanted = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    size == 0 ? 18 : size, text.getResources().getDisplayMetrics());
            if (text.getTextSize() != wanted) text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, wanted);
            int color = CaptionStyle.backgroundColor();
            if (!Objects.equals(text.getTag(), color)) { text.setBackgroundColor(color); text.setTag(color); }
        }
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (text.getVisibility() != visibility) text.setVisibility(visibility);
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    // Replaced with the verified native renderer's FrameLayout field.
    private static FrameLayout rootOf(Object renderer) { return renderer instanceof FrameLayout ? (FrameLayout) renderer : null; }
}
