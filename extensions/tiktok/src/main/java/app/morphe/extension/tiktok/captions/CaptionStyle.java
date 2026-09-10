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
     * A name this build does not have is reported. So is a name it does have that names nothing
     * in the container, which is the likelier of the two: obfuscated entry names are assigned
     * by a counter, so a reshuffle moves a name onto a different view more often than it drops
     * one. That one cannot be reported on sight, though. The hook is installed on every way out
     * of TikTok's render method, including the ones that rendered nothing, and it runs before
     * the check that this renderer is the video on screen, so an empty container is an ordinary
     * outcome rather than a broken build. See {@link #noteLookup}.
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
        // On the host's render path, so nothing here may throw. The context is set in
        // attachBaseContext, long before a caption is drawn, but a null answer costs one line.
        android.content.Context context = Utils.getContext();
        if (context == null) return original;
        TextPaint paint = new TextPaint(original.getPaint());
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, context.getResources().getDisplayMetrics()));
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
        noteLookup(root, text != null, background != null, textId != 0, backgroundId != 0);
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

    /**
     * How many renders of a container that has something in it have to miss before this says so.
     *
     * <p>One miss is not evidence of anything. The hook is injected at every return-void in the
     * host's render method, early bail-outs included, and HookStatus never forgets a miss, so a
     * single empty renderer would make the Hook status row read broken for the rest of the
     * process. Twenty consecutive renders of a container that is really a caption container is
     * not a bail-out; on a working build the first of them finds the view and the count is
     * dropped.
     */
    private static final int MISSES_BEFORE_REPORTING = 20;

    /**
     * Consecutive misses per name, and the names that have ever been found.
     *
     * <p>Only ever touched from apply, which runs on the thread that lays the caption out.
     */
    private static final Map<String, Integer> MISSES = new java.util.HashMap<>();
    private static final java.util.Set<String> FOUND = new java.util.HashSet<>();

    /**
     * Follows an id that resolved but found nothing under this container.
     *
     * <p>Silent while the container is empty, because that is what the early returns in TikTok's
     * own render method look like from here, and silent forever once the name has been found
     * once: a build where it works has nothing to report, and every later miss on it is another
     * bail-out.
     */
    private static void noteLookup(View root, boolean foundText, boolean foundBackground,
                                   boolean textResolved, boolean backgroundResolved) {
        if (foundText) FOUND.add(TEXT_ID);
        if (foundBackground) FOUND.add(BACKGROUND_ID);
        // Either one being found says this container really is a caption container, which is
        // what makes the other one's absence worth counting rather than a bail-out. Counting
        // the pair as one thing instead made a build where exactly one id had moved
        // unreportable for the life of the process: the working name cleared the count on
        // every render and the broken one never reached the threshold.
        boolean aCaptionContainer = foundText || foundBackground;
        if (!aCaptionContainer && !hasChildren(root)) return;
        note(TEXT_ID, textResolved, foundText, aCaptionContainer);
        note(BACKGROUND_ID, backgroundResolved, foundBackground, aCaptionContainer);
    }

    private static void note(String name, boolean resolved, boolean found,
                             boolean aCaptionContainer) {
        if (found) {
            MISSES.remove(name);
            return;
        }
        if (!resolved || FOUND.contains(name)) return;
        int misses = (MISSES.containsKey(name) ? MISSES.get(name) : 0) + 1;
        MISSES.put(name, misses);
        // A container with children but neither caption view in it might be somebody else's
        // view, so those count towards the same total but only report once the count is long
        // past anything a handful of stray renders could reach.
        int needed = aCaptionContainer ? MISSES_BEFORE_REPORTING : MISSES_BEFORE_REPORTING * 2;
        if (misses >= needed) {
            HookStatus.missingMember("captions", "view", "caption container", name);
        }
    }

    private static boolean hasChildren(View root) {
        return root instanceof android.view.ViewGroup
                && ((android.view.ViewGroup) root).getChildCount() > 0;
    }

    /** Lets a test stand in for a TikTok resource id, which only the real APK resolves. */
    static void resolveForTests(String name, int id) {
        RESOURCE_IDS.putForTests(APP_PACKAGE, name, id);
    }

    /** Forgets what has been looked up, between tests. */
    static void resetLookupsForTests() {
        MISSES.clear();
        FOUND.clear();
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
