package app.morphe.extension.tiktok.captions;

import static org.junit.Assert.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CaptionToolsTest {
    public static final class CaptionActivity extends android.app.Activity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }
    public static final class Video { public String aid; Video(String id) { aid = id; } }
    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.CAPTION_TEXT_SIZE.save(0);
        Settings.CAPTION_BACKGROUND.save("default");
        Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(false);
        CaptionTools.onVideoChanged(null);
    }
    @Test public void bothCaptionRenderersResizeAndRestoreWithoutChangingSourceLayout() {
        TextPaint paint = new TextPaint();
        paint.setTextSize(16);
        Layout original = new StaticLayout("A longer caption that wraps at the chosen width", paint, 180, Layout.Alignment.ALIGN_CENTER, 1, 0, true);
        assertSame(original, CaptionStyle.layout(original));
        Settings.CAPTION_TEXT_SIZE.save(32);
        Layout styled = CaptionStyle.layout(original);
        assertTrue(styled.getHeight() > original.getHeight());
        assertEquals(16, original.getPaint().getTextSize(), 0);
        assertEquals(original.getText(), styled.getText());
        FrameLayout root = new FrameLayout(Utils.getContext());
        TextView text = new TextView(Utils.getContext());
        text.setId(2131366636);
        text.setTextSize(16);
        float originalSize = text.getTextSize();
        FrameLayout background = new FrameLayout(Utils.getContext());
        background.setId(2131366629);
        background.setBackgroundColor(Color.BLUE);
        root.addView(background);
        background.addView(text);
        Settings.CAPTION_BACKGROUND.save("black");
        CaptionStyle.apply(root);
        assertTrue(text.getTextSize() > originalSize);
        assertEquals(Color.BLACK, ((ColorDrawable) background.getBackground()).getColor());
        Settings.CAPTION_TEXT_SIZE.save(0);
        Settings.CAPTION_BACKGROUND.save("default");
        CaptionStyle.apply(root);
        assertEquals(originalSize, text.getTextSize(), 0);
        assertEquals(Color.BLUE, ((ColorDrawable) background.getBackground()).getColor());
        assertSame(original, CaptionStyle.layout(original));
    }
    @Test public void clearDisplayKeepsOnlyTheCurrentCueAndHidesOnFocusLossOrVideoChange() throws Exception {
        try (var owner = Robolectric.buildActivity(CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            FrameLayout source = new FrameLayout(activity);
            source.setBackgroundColor(0xFF181825);
            activity.setContentView(source);
            owner.windowFocusChanged(true);
            Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(true);
            Settings.CAPTION_TEXT_SIZE.save(24);
            CaptionTools.onVideoChanged("one");
            CaptionTools.onCaption(source, "one", "EXPANDED", "Current spoken caption", false);
            CaptionTools.onClear(new Video("one"), true);
            TextView caption = find(activity.getWindow().getDecorView(), "Current spoken caption");
            assertNotNull(caption);
            assertEquals(View.VISIBLE, caption.getVisibility());
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "clear-display-captions.png");
            owner.windowFocusChanged(false);
            CaptionTools.refresh();
            assertEquals(View.GONE, caption.getVisibility());
            owner.windowFocusChanged(true);
            CaptionTools.refresh();
            assertEquals(View.VISIBLE, caption.getVisibility());
            CaptionTools.onCaption(source, null, "HIDDEN", "", false);
            assertEquals(View.GONE, caption.getVisibility());
            CaptionTools.onCaption(source, null, "EXPANDED", "Another cue", false);
            assertEquals(View.VISIBLE, caption.getVisibility());
            CaptionTools.onVideoChanged("two");
            CaptionTools.onCaption(source, "one", "EXPANDED", "Stale cue", false);
            CaptionTools.onClear(new Video("one"), true);
            assertEquals(View.GONE, caption.getVisibility());
        }
    }
    @Test public void subtitlePatchExposesCaptionControls() throws Exception {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.interaction.GestureActionsTest.TestActivity.class).setup()) {
            var activity = owner.get();
            Utils.setContext(activity);
            Utils.setIsDarkModeEnabled(true);
            SettingsStatus.subtitleToolsEnabled = true;
            var screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("caption_text_size"));
            assertNotNull(screen.findPreference("caption_background"));
            assertNotNull(screen.findPreference("keep_captions_clear_display"));
            var size = (app.morphe.extension.tiktok.settings.preference.NumberInputPreference) screen.findPreference("caption_text_size");
            size.setValue("1");
            assertEquals("12", size.getValue());
            size.setValue("0");
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "caption-settings.png");
        }
    }
    private static TextView find(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = find(((ViewGroup) view).getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
}
