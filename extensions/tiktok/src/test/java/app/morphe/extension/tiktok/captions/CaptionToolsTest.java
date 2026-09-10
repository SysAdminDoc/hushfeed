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
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import org.junit.Before;
import org.junit.After;
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
        CaptionStyle.resetLookupsForTests();
        Settings.CAPTION_TEXT_SIZE.save(0);
        Settings.CAPTION_BACKGROUND.save("default");
        Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(false);
        CaptionTools.onVideoChanged(null);
    }
    @After public void tearDown() {
        SettingsStatus.subtitleToolsEnabled = false;
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
        // The two ids are obfuscated names in the real APK and only it can resolve them, so
        // the fixture says what they stand for here rather than repeating this build's numbers.
        int textId = View.generateViewId();
        int backgroundId = View.generateViewId();
        CaptionStyle.resolveForTests("dfu", textId);
        CaptionStyle.resolveForTests("dfn", backgroundId);
        FrameLayout root = new FrameLayout(Utils.getContext());
        TextView text = new TextView(Utils.getContext());
        text.setId(textId);
        text.setTextSize(16);
        float originalSize = text.getTextSize();
        FrameLayout background = new FrameLayout(Utils.getContext());
        background.setId(backgroundId);
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
    @Test public void aBuildWithoutTheCaptionIdsSaysSoOnTheHookStatusRow() {
        HookStatus.clear();
        // What a reshuffled resource table looks like from here: the names resolve to nothing.
        CaptionStyle.resolveForTests("dfu", 0);
        CaptionStyle.resolveForTests("dfn", 0);
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        CaptionStyle.apply(new FrameLayout(Utils.getContext()));

        assertTrue(HookStatus.anyMissing());
        assertEquals(java.util.Arrays.asList("view id 'dfu'", "view id 'dfn'"),
                HookStatus.missing("captions"));
        assertTrue(String.join(" ", HookStatus.report()).contains("captions"));
        HookStatus.clear();
    }

    /**
     * The other half of the same defect, and the harder one. The names are assigned by a
     * counter, so a reshuffled resource table is likelier to move a name onto a different view
     * than to drop it: findViewById answers null, both caption settings quietly do nothing, and
     * the Hook status row used to say everything was bound.
     */
    @Test public void anIdThatNamesNothingInTheContainerSaysSoOnTheHookStatusRow() {
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
        // The ids resolve, which is what tells this apart from the case above. They just are
        // not the views in this container.
        CaptionStyle.resolveForTests("dfu", View.generateViewId());
        CaptionStyle.resolveForTests("dfn", View.generateViewId());
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        FrameLayout root = new FrameLayout(Utils.getContext());
        // A container with something in it, which is what a render that rendered looks like.
        root.addView(new TextView(Utils.getContext()));
        for (int render = 0; render < 40; render++) CaptionStyle.apply(root);

        assertTrue("a build where neither caption view is reachable says nothing",
                HookStatus.anyMissing());
        assertEquals(java.util.Arrays.asList(
                        "view caption container#dfu", "view caption container#dfn"),
                HookStatus.missing("captions"));
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
    }

    /**
     * And the reason the naive version of this was written and reverted on the same day. The
     * hook is injected at every return-void in TikTok's render method, early bail-outs
     * included, and it runs before the check that this renderer is the video on screen, so an
     * empty container is ordinary. HookStatus never forgets a miss, so reporting one would make
     * the row read broken for the rest of the process on a perfectly healthy build.
     */
    @Test public void anEmptyRendererIsNotAMissHoweverManyTimesItArrives() {
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
        CaptionStyle.resolveForTests("dfu", View.generateViewId());
        CaptionStyle.resolveForTests("dfn", View.generateViewId());
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        FrameLayout empty = new FrameLayout(Utils.getContext());
        for (int render = 0; render < 100; render++) CaptionStyle.apply(empty);

        assertFalse("a hundred empty renders were reported as a broken build",
                HookStatus.anyMissing());
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
    }

    /** And a build where it works stays quiet through the bail-outs between real renders. */
    @Test public void aWorkingBuildIsNotReportedByTheRendersThatRenderNothing() {
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
        int textId = View.generateViewId();
        int backgroundId = View.generateViewId();
        CaptionStyle.resolveForTests("dfu", textId);
        CaptionStyle.resolveForTests("dfn", backgroundId);
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        FrameLayout root = new FrameLayout(Utils.getContext());
        FrameLayout background = new FrameLayout(Utils.getContext());
        background.setId(backgroundId);
        TextView text = new TextView(Utils.getContext());
        text.setId(textId);
        root.addView(background);
        background.addView(text);

        FrameLayout bailOut = new FrameLayout(Utils.getContext());
        bailOut.addView(new TextView(Utils.getContext()));
        CaptionStyle.apply(root);
        for (int render = 0; render < 50; render++) CaptionStyle.apply(bailOut);

        assertFalse("the renderers that rendered nothing were called a broken build",
                HookStatus.anyMissing());
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
    }

    /**
     * A container holding the caption but not the strip behind it. This is the likeliest shape
     * of the defect the report exists for: the names are assigned by a counter, so a reshuffle
     * moves one of the two more often than it moves both.
     *
     * <p>Finding one of them is what makes the other one's absence worth reporting rather than
     * a bail-out, because it proves this container really is a caption container. Counting the
     * pair as one thing instead, which is what the first version of this did, made exactly this
     * build unreportable: the working name cleared the count on every render and the broken one
     * never reached the threshold, so the caption background setting did nothing while the Hook
     * status row read clean.
     */
    @Test public void oneCaptionViewFoundIsWhatMakesTheOtherOneWorthReporting() {
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
        int textId = View.generateViewId();
        CaptionStyle.resolveForTests("dfu", textId);
        CaptionStyle.resolveForTests("dfn", View.generateViewId());
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        FrameLayout root = new FrameLayout(Utils.getContext());
        TextView text = new TextView(Utils.getContext());
        text.setId(textId);
        text.setTextSize(16);
        root.addView(text);
        for (int render = 0; render < 20; render++) CaptionStyle.apply(root);

        assertTrue("the caption was never restyled, so this proves nothing",
                text.getTextSize() > 16);
        assertEquals("the half of the build that works was reported as broken, or the half "
                        + "that does not was not reported at all",
                java.util.Collections.singletonList("view caption container#dfn"),
                HookStatus.missing("captions"));
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
    }

    /**
     * And a run of containers that hold somebody else's children before the first real render.
     * The hook fires for renderers that are not the video on screen, so which arrives first is
     * not this code's to choose, and HookStatus never forgets: one report and the row reads
     * broken for the rest of the process while the feature works.
     */
    @Test public void aRunOfForeignContainersBeforeTheFirstRealRenderIsForgivenOnceItArrives() {
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
        int textId = View.generateViewId();
        int backgroundId = View.generateViewId();
        CaptionStyle.resolveForTests("dfu", textId);
        CaptionStyle.resolveForTests("dfn", backgroundId);
        Settings.CAPTION_TEXT_SIZE.save(32);
        Settings.CAPTION_BACKGROUND.save("black");

        FrameLayout foreign = new FrameLayout(Utils.getContext());
        foreign.addView(new TextView(Utils.getContext()));
        for (int render = 0; render < 39; render++) CaptionStyle.apply(foreign);
        assertFalse("thirty-nine strangers were already enough to call the build broken",
                HookStatus.anyMissing());

        FrameLayout real = new FrameLayout(Utils.getContext());
        FrameLayout background = new FrameLayout(Utils.getContext());
        background.setId(backgroundId);
        TextView text = new TextView(Utils.getContext());
        text.setId(textId);
        real.addView(background);
        background.addView(text);
        CaptionStyle.apply(real);

        for (int render = 0; render < 40; render++) CaptionStyle.apply(foreign);
        assertFalse("one good render did not clear what the strangers had counted up",
                HookStatus.anyMissing());
        HookStatus.clear();
        CaptionStyle.resetLookupsForTests();
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
            CaptionTools.onCaption(source, "one", "EXPANDED", "Current spoken caption");
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
            CaptionTools.onCaption(source, null, "HIDDEN", "");
            assertEquals(View.GONE, caption.getVisibility());
            CaptionTools.onCaption(source, null, "EXPANDED", "Another cue");
            assertEquals(View.VISIBLE, caption.getVisibility());
            CaptionTools.onVideoChanged("two");
            CaptionTools.onCaption(source, "one", "EXPANDED", "Stale cue");
            CaptionTools.onClear(new Video("one"), true);
            assertEquals(View.GONE, caption.getVisibility());
        }
    }

    @Test public void preDrawTracksFocusAndSourceAttachmentWithoutAnotherCaption() {
        try (var owner = Robolectric.buildActivity(CaptionActivity.class).setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            FrameLayout content = new FrameLayout(activity);
            FrameLayout source = new FrameLayout(activity);
            content.addView(source);
            activity.setContentView(content);
            owner.windowFocusChanged(true);
            Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(true);
            CaptionTools.onVideoChanged("predraw-video");
            CaptionTools.onCaption(source, "predraw-video", "EXPANDED", "A continuing cue");
            CaptionTools.onClear(new Video("predraw-video"), true);
            View decor = activity.getWindow().getDecorView();
            TextView caption = find(decor, "A continuing cue");
            assertNotNull(caption);
            assertEquals(View.VISIBLE, caption.getVisibility());

            owner.windowFocusChanged(false);
            decor.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(View.GONE, caption.getVisibility());
            owner.windowFocusChanged(true);
            decor.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(View.VISIBLE, caption.getVisibility());

            content.removeView(source);
            assertFalse(source.isAttachedToWindow());
            decor.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals("a detached renderer must stop lending its cue to the overlay",
                    View.GONE, caption.getVisibility());
            content.addView(source);
            assertTrue(source.isAttachedToWindow());
            decor.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals("reattaching the current renderer should restore its cue",
                    View.VISIBLE, caption.getVisibility());
        }
    }

    @Test public void replacingTheActivityMovesTheOverlayAndRetiresTheOldPreDrawListener() {
        try (var firstOwner = Robolectric.buildActivity(CaptionActivity.class).setup().visible();
             var secondOwner = Robolectric.buildActivity(CaptionActivity.class).setup().visible()) {
            var first = firstOwner.get();
            FrameLayout firstSource = new FrameLayout(first);
            first.setContentView(firstSource);
            firstOwner.windowFocusChanged(true);
            Utils.setContext(first);
            Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(true);
            CaptionTools.onVideoChanged("moving-video");
            CaptionTools.onCaption(firstSource, "moving-video", "EXPANDED", "First window cue");
            CaptionTools.onClear(new Video("moving-video"), true);
            View firstDecor = first.getWindow().getDecorView();
            TextView oldCaption = find(firstDecor, "First window cue");
            assertNotNull(oldCaption);
            assertEquals(View.VISIBLE, oldCaption.getVisibility());

            var second = secondOwner.get();
            FrameLayout secondSource = new FrameLayout(second);
            second.setContentView(secondSource);
            secondOwner.windowFocusChanged(true);
            Utils.setContext(second);
            CaptionTools.onCaption(secondSource, "moving-video", "EXPANDED", "Second window cue");
            ViewGroup secondDecor = (ViewGroup) second.getWindow().getDecorView();
            TextView caption = find(secondDecor, "Second window cue");
            assertNotNull(caption);
            assertNotSame(oldCaption, caption);
            assertNull("the previous window still owns an overlay", oldCaption.getParent());
            assertSame(secondDecor, caption.getRootView());
            assertEquals(View.VISIBLE, caption.getVisibility());
            int childCount = secondDecor.getChildCount();
            CaptionTools.onCaption(secondSource, null, "EXPANDED", "Next window cue");
            assertEquals(childCount, secondDecor.getChildCount());
            assertEquals("Next window cue", caption.getText().toString());

            // Only the new window's pre-draw may refresh its overlay. An old listener calls
            // the same static refresh method and would incorrectly change the new caption.
            Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.save(false);
            assertEquals(View.VISIBLE, caption.getVisibility());
            firstDecor.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals("the detached overlay left a listener on its previous window",
                    View.VISIBLE, caption.getVisibility());
            secondDecor.getViewTreeObserver().dispatchOnPreDraw();
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
