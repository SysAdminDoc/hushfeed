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
     * <p>These used to be written here as the numbers they resolve to on 46.2.3, so a build that
     * reshuffled the resource table left both caption settings doing nothing and said nothing.
     * A name this build does not have is reported. A name it does have that is not the view in
     * this container is not, and cannot be from here: the hook is installed on every way out of
     * TikTok's render method, including the ones that rendered nothing, and it runs before the
     * check that this renderer is the video on screen, so an empty container is an ordinary
     * outcome rather than a broken build.
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
