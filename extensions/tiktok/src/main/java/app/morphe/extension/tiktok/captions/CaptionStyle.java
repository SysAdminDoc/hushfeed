package app.morphe.extension.tiktok.captions;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.Map;
import java.util.WeakHashMap;

public final class CaptionStyle {
    private static final Map<View, Drawable.ConstantState> BACKGROUNDS = new WeakHashMap<>();
    private static final Map<TextView, Float> SIZES = new WeakHashMap<>();

    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    /**
     * The caption text view and the strip behind it, by their obfuscated names.
     *
     * <p>These move with the build the way the comment package's ids do. On 46.2.3 they are
     * 2131366636 and 2131366629, which is how they used to be written here: as integers, so a
     * build that reshuffled the resource table would have left both caption settings doing
     * nothing with the Hook status row still reporting everything bound.
     */
    private static final String TEXT_ID = "dfu";
    private static final String BACKGROUND_ID = "dfn";
    private static final ResourceIdCache RESOURCE_IDS = new ResourceIdCache();

    static int size() {
        int value = Settings.CAPTION_TEXT_SIZE.get();
        return value <= 0 ? 0 : Math.max(12, Math.min(48, value));
    }

    public static Layout layout(Layout original) {
        int size = size();
        if (original == null || size == 0) return original;
        TextPaint paint = new TextPaint(original.getPaint());
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, Utils.getContext().getResources().getDisplayMetrics()));
        return new StaticLayout(original.getText(), paint, Math.max(1, original.getWidth()),
                original.getAlignment(), original.getSpacingMultiplier(), original.getSpacingAdd(), true);
    }

    static void apply(View root) {
        int textId = identifier(root, TEXT_ID);
        TextView text = textId == 0 ? null : root.findViewById(textId);
        if (text != null) {
            if (size() > 0) {
                // Not putIfAbsent: that is an API 24 default method on the Map interface, and
                // this runs on every caption render on a build whose floor is API 23.
                if (!SIZES.containsKey(text)) SIZES.put(text, text.getTextSize());
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size());
            } else if (SIZES.containsKey(text)) text.setTextSize(TypedValue.COMPLEX_UNIT_PX, SIZES.remove(text));
        }
        int backgroundId = identifier(root, BACKGROUND_ID);
        View background = backgroundId == 0 ? null : root.findViewById(backgroundId);
        if (background == null) return;
        String color = Settings.CAPTION_BACKGROUND.get();
        if (!"default".equals(color) && !BACKGROUNDS.containsKey(background)) {
            Drawable drawable = background.getBackground();
            BACKGROUNDS.put(background, drawable == null ? null : drawable.getConstantState());
        }
        if ("default".equals(color)) {
            if (!BACKGROUNDS.containsKey(background)) return;
            Drawable.ConstantState nativeState = BACKGROUNDS.remove(background);
            background.setBackground(nativeState == null ? null : nativeState.newDrawable(background.getResources()));
        } else background.setBackgroundColor(backgroundColor());
    }

    /** Lets a test stand in for a TikTok resource id, which only the real APK resolves. */
    static void resolveForTests(String name, int id) {
        RESOURCE_IDS.putForTests(APP_PACKAGE, name, id);
    }

    /** Resolves a caption view id, saying so once when this build does not have it. */
    private static int identifier(View view, String name) {
        int id = RESOURCE_IDS.resolve(
                view == null ? null : view.getResources(), APP_PACKAGE, name, false);
        if (id == 0) HookStatus.missingViewId("captions", name);
        else HookStatus.bound("captions", name);
        return id;
    }

    static int backgroundColor() {
        switch (Settings.CAPTION_BACKGROUND.get()) {
            case "transparent": return Color.TRANSPARENT;
            case "black": return Color.BLACK;
            default: return 0xB3000000;
        }
    }
}
