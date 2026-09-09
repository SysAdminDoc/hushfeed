package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import app.morphe.extension.tiktok.SettingsContextRule;
import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.UiCapture;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w480dp-h960dp-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SuppressWarnings("deprecation")
public class SettingsPagesTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    private static final String[] SECTIONS = {"FEED_FILTER", "FEED_NAVIGATION", "INTERFACE", "COMMENTS",
            "DOWNLOADS", "PLAYBACK", "INBOX", "SHARE", "REGION", "BEHAVIOR", "DIAGNOSTICS"};
    private static final String[] TITLES = {"Feed filter", "Feed navigation", "Interface", "Comments and translation",
            "Downloads", "Playback", "Inbox", "Share sheet", "Region settings", "App behavior", "Diagnostics"};
    private final Map<Field, Boolean> statuses = new LinkedHashMap<>();

    public static class PageActivity extends Activity {
        @Override public void onCreate(Bundle state) {
            boolean dark = (getResources().getConfiguration().uiMode & 0x30) == 0x20;
            setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
            super.onCreate(state);
        }
    }
    @Before public void installControls() throws Exception {
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                statuses.put(field, field.getBoolean(null));
                field.setBoolean(null, true);
            }
        }
    }
    @After public void restoreControls() throws Exception {
        for (var entry : statuses.entrySet()) entry.getKey().setBoolean(null, entry.getValue());
    }
    @Test public void aBundleWithOnlyTheSettingsPatchOffersNoEmptyPages() throws Exception {
        // The @Before turns every status flag on, which is the opposite of the case that
        // matters here: a user who selected Settings and nothing else must not be given rows
        // that lead nowhere.
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.setBoolean(null, false);
            }
        }
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = new TikTokPreferenceFragment();
            activity.getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, home).commit();
            activity.getFragmentManager().executePendingTransactions();

            android.preference.PreferenceScreen screen = home.getPreferenceScreen();
            for (int index = 0; index < screen.getPreferenceCount(); index++) {
                CharSequence title = screen.getPreference(index).getTitle();
                assertNotEquals("App behavior has nothing in it without its patches",
                        "App behavior", title == null ? "" : title.toString());
            }
        }
    }

    @Test public void everyPageWithSomethingOnItHasARowIntoIt() throws Exception {
        // The page and the row into it used to keep separate copies of the same condition, and
        // two of them drifted: the Playback page grew the daily budget behind the block author
        // patch while its row stayed on the four playback patches, so a bundle with only that
        // patch could reach the budget through search and nowhere else.
        Class<?>[] pages = {
            app.morphe.extension.tiktok.settings.preference.categories.FeedFilterPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.FeedNavigationPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.CommentsPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.PlaybackPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.InboxPreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.SharePreferenceCategory.class,
            app.morphe.extension.tiktok.settings.preference.categories.SimSpoofPreferenceCategory.class,
        };
        String[] titles = {"Feed filter", "Feed navigation", "Interface", "Comments and translation",
            "Downloads", "Playback", "Inbox", "Share sheet", "Region settings"};

        // What each page builds with nothing in the bundle at all. Those rows are unconditional
        // and show whenever something else opens the page, so they are the floor to compare
        // against rather than a finding.
        int[] baseline = new int[pages.length];
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.setBoolean(null, false);
            }
        }
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = new TikTokPreferenceFragment();
            activity.getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, home).commit();
            activity.getFragmentManager().executePendingTransactions();
            for (int page = 0; page < pages.length; page++) {
                baseline[page] = rowsBuiltBy(pages[page], activity, home);
            }
        }

        // One patch at a time, which is the shape that finds a drifted gate.
        for (Field flag : SettingsStatus.class.getDeclaredFields()) {
            if (flag.getType() != boolean.class || !Modifier.isStatic(flag.getModifiers())) continue;
            for (Field other : SettingsStatus.class.getDeclaredFields()) {
                if (other.getType() == boolean.class && Modifier.isStatic(other.getModifiers())) {
                    other.setAccessible(true);
                    other.setBoolean(null, false);
                }
            }
            flag.setAccessible(true);
            flag.setBoolean(null, true);

            try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
                Activity activity = owner.get();
                Utils.setContext(activity);
                TikTokPreferenceFragment home = new TikTokPreferenceFragment();
                activity.getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, home).commit();
                activity.getFragmentManager().executePendingTransactions();

                java.util.Set<String> rows = new java.util.HashSet<>();
                android.preference.PreferenceScreen screen = home.getPreferenceScreen();
                for (int index = 0; index < screen.getPreferenceCount(); index++) {
                    CharSequence title = screen.getPreference(index).getTitle();
                    if (title != null) rows.add(title.toString());
                }
                for (int page = 0; page < pages.length; page++) {
                    boolean available = (Boolean) pages[page].getMethod("isAvailable").invoke(null);
                    if (available) {
                        assertTrue(titles[page] + " has rows to show with only " + flag.getName()
                                + " set, but the home screen offers no way in",
                                rows.contains(titles[page]));
                        continue;
                    }

                    // The other direction, which is where the drift actually hides. Asking only
                    // "available means a row" is a page answering its own question. What matters
                    // is whether this flag makes the page build anything it would not have built
                    // without it, because a switch behind a flag the page does not know about
                    // appears nowhere at all: no row, no page, and not in search either, since
                    // search builds the same category. Rows the page builds whatever is in the
                    // bundle are not this: they only ever show when something else opens it.
                    assertEquals(titles[page] + " builds settings behind " + flag.getName()
                            + " but does not count it as making the page available, so they are"
                            + " unreachable", baseline[page], rowsBuiltBy(pages[page], activity, home));
                }
            }
        }
    }

    @Test public void noPageGatesItsRowsOnAnEarlyReturn() throws Exception {
        // The other half of the same bug, and one a rendering test cannot see. An early return on
        // one patch's flag takes every later patch's rows with it: the offline videos limit set
        // its own flag, gated its own rows on it, and still put nothing on the page because an
        // "if (!downloadEnabled) return;" sat above them. Blocks, not returns.
        java.util.List<String> offenders = new java.util.ArrayList<>();
        int scanned = 0;
        for (java.io.File file : categorySources()) {
            String source = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            scanned++;
            for (String found : earlyReturns(source)) {
                offenders.add(file.getName() + ": " + found);
            }
        }
        assertTrue("no categories were read at all", scanned > 5);
        assertEquals("a flag returning early from a settings page hides every flag below it: "
                + offenders, 0, offenders.size());
    }

    @Test public void theEarlyReturnScanCanActuallyFail() {
        // A scan with nothing to find proves nothing, and the first version of this one was
        // defeated by a brace, a line break and a trailing comment. Every shape it has to catch
        // is put in front of it here, along with the ones it must leave alone.
        String source = "class Sample {\n"
                + "    void addPreferences(Context context) {\n"
                + "        char quote = '\"';\n"
                + "        if (!SettingsStatus.bare) return;\n"
                + "        if (!SettingsStatus.braced) { return; }\n"
                + "        if (!SettingsStatus.wrapped)\n            return;\n"
                + "        if (!SettingsStatus.commented) return; // and a note\n"
                + "        if (!isAvailable()) return;\n"
                + "        if (!Settings.SOME_TOGGLE.get()) return;\n"
                + "        if (context == null) return;\n"
                + "        // if (!SettingsStatus.inAComment) return;\n"
                + "        String text = \"if (!SettingsStatus.inAString) return;\";\n"
                + "        if (SettingsStatus.positive) { addPreference(null); }\n"
                + "    }\n"
                + "}\n";

        java.util.List<String> found = earlyReturns(source);
        assertEquals("the scan missed one of the shapes it exists for: " + found, 6, found.size());
        for (String shape : new String[]{"bare", "braced", "wrapped", "commented",
                "isAvailable()", "SOME_TOGGLE"}) {
            assertTrue(shape + " walked past the scan: " + found,
                    found.toString().contains(shape));
        }
        assertFalse("an ordinary null guard was reported: " + found,
                found.toString().contains("context"));
        assertFalse("a commented-out line was reported: " + found,
                found.toString().contains("inAComment"));
        assertFalse("a string literal was reported: " + found,
                found.toString().contains("inAString"));
    }

    /** Every preference category source, wherever a page is built from. */
    private static java.io.File[] categorySources() {
        java.io.File directory = new java.io.File(
                "src/main/java/app/morphe/extension/tiktok/settings/preference/categories");
        if (!directory.isDirectory()) directory = new java.io.File(
                "extensions/tiktok/src/main/java/app/morphe/extension/tiktok/settings/preference/categories");
        assertTrue("could not find " + directory.getAbsolutePath(), directory.isDirectory());
        java.io.File[] files = directory.listFiles(
                (dir, name) -> name.endsWith(".java"));
        return java.util.Objects.requireNonNull(files);
    }

    /**
     * Guards that end a page's build early on something a patch decides. Comments and both kinds
     * of literal go first and then all whitespace, so the shape is caught however it is written:
     * on one line or two, braced or bare, with a note after it.
     */
    private static java.util.List<String> earlyReturns(String source) {
        java.util.List<String> found = new java.util.ArrayList<>();
        String packed = withoutCommentsOrLiterals(source).replaceAll("\\s+", "");
        int at = 0;
        while ((at = packed.indexOf("if(", at)) >= 0) {
            int close = matchingBracket(packed, at + 2);
            if (close < 0) break;
            String condition = packed.substring(at + 3, close);
            String rest = packed.substring(close + 1);
            boolean returnsNow = rest.startsWith("return;") || rest.startsWith("{return;}");
            boolean aboutAPatch = condition.startsWith("!")
                    && (condition.contains("SettingsStatus.") || condition.contains("isAvailable()")
                            || condition.contains("Settings."));
            if (returnsNow && aboutAPatch) found.add("if (" + condition + ") return;");
            at = close + 1;
        }
        return found;
    }

    /** Index of the ')' matching the '(' at {@code open}, or -1. */
    private static int matchingBracket(String text, int open) {
        int depth = 0;
        for (int at = open; at < text.length(); at++) {
            char c = text.charAt(at);
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return at;
        }
        return -1;
    }

    /** Source with comments and both kinds of literal removed, so their text is not read as code. */
    private static String withoutCommentsOrLiterals(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (int at = 0; at < source.length(); ) {
            char c = source.charAt(at);
            if (source.startsWith("//", at)) {
                while (at < source.length() && source.charAt(at) != '\n') at++;
            } else if (source.startsWith("/*", at)) {
                int end = source.indexOf("*/", at + 2);
                at = end < 0 ? source.length() : end + 2;
            } else if (c == '"' || c == '\'') {
                // A char literal holding a quote used to leave the scanner reading the rest of
                // the file as one string, which swallowed any real finding after it.
                char quote = c;
                at++;
                while (at < source.length() && source.charAt(at) != quote) {
                    if (source.charAt(at) == '\\') at++;
                    at++;
                }
                at++;
            } else {
                out.append(c);
                at++;
            }
        }
        return out.toString();
    }

    /** How many rows a page's own addPreferences puts on it under the flags set right now. */
    private static int rowsBuiltBy(Class<?> page, Activity activity, TikTokPreferenceFragment home)
            throws Exception {
        android.preference.PreferenceScreen scratch =
                home.getPreferenceManager().createPreferenceScreen(activity);
        var built = (app.morphe.extension.tiktok.settings.preference.categories
                .ConditionalPreferenceCategory) page.getConstructor(android.content.Context.class,
                        android.preference.PreferenceScreen.class).newInstance(activity, scratch);
        if (!built.getSettingsStatus()) {
            // The gate kept it off the screen, so it has no preference manager yet and nothing
            // could be added to it. Attaching it is what makes addPreferences work.
            scratch.addPreference(built);
            built.addPreferences(activity);
        }
        return built.getPreferenceCount();
    }

    @Test public void darkPagesNavigateAndRender() throws Exception { capturePages("dark"); }
    @Test @Config(qualifiers = "w480dp-h960dp-notnight-mdpi")
    public void lightPagesNavigateAndRender() throws Exception { capturePages("light"); }

    private void capturePages(String theme) throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.DEFAULT_SPEED_ENABLED.get();
            for (var setting : app.morphe.extension.shared.settings.Setting.allLoadedSettings()) setting.resetToDefault();
            Settings.AUTO_ADVANCE.save(false);
            Settings.DEFAULT_SPEED_ENABLED.save(true);
            Settings.DEFAULT_SPEED.save("1.5");
            // The Diagnostics capture is meant to show what a hook report looks like. An empty
            // registry renders "nothing has been looked up yet", which is the one state that
            // says nothing about the feature, so the surfaces are seeded the way a few minutes
            // of use would leave them.
            HookStatus.clear();
            HookStatus.bound("overlay", "cover");
            HookStatus.bound("overlay", "caption");
            HookStatus.bound("comments", "like_button");
            HookStatus.missingViewId("comments", "jlk");
            TikTokPreferenceFragment home = new TikTokPreferenceFragment();
            activity.getFragmentManager().beginTransaction().replace(android.R.id.content, home).commit();
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            UiCapture.save(home.getView(), "pages/" + theme + "/settings.png");
            for (int i = 0; i < SECTIONS.length; i++) {
                Preference menu = null;
                for (int j = 0; j < home.getPreferenceScreen().getPreferenceCount(); j++) {
                    Preference candidate = home.getPreferenceScreen().getPreference(j);
                    if (TITLES[i].equals(String.valueOf(candidate.getTitle()))) menu = candidate;
                }
                assertNotNull("Missing menu: " + SECTIONS[i], menu);
                assertTrue(menu.getOnPreferenceClickListener().onPreferenceClick(menu));
                activity.getFragmentManager().executePendingTransactions();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                TikTokPreferenceFragment page = (TikTokPreferenceFragment) activity.getFragmentManager().findFragmentById(android.R.id.content);
                assertEquals(SECTIONS[i], page.getArguments().getString("morphe_settings_section"));
                assertTrue(page.getPreferenceScreen().getPreferenceCount() > 2);
                String name = "pages/" + theme + "/" + SECTIONS[i].toLowerCase(java.util.Locale.ROOT);
                UiCapture.save(page.getView(), name + ".png");
                ListView list = page.getView().findViewById(android.R.id.list);
                list.setSelection(list.getCount() - 1);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                UiCapture.save(page.getView(), name + "-end.png");
                activity.getFragmentManager().popBackStackImmediate();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertSame(home, activity.getFragmentManager().findFragmentById(android.R.id.content));
            }
        }
    }

    @Test public void aPressStaysInsideTheCardTheRowIsPartOf() throws Exception {
        // The ripple mask was an opaque rectangle while the row draws a rounded card, so a press
        // at the corner of a card's first or last row filled the transparent notch outside it.
        // The mask has to be the shape the row draws: rounded where the card is, square where
        // the row meets its neighbour.
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            float radius = app.morphe.extension.tiktok.settings.preference.SettingsUi
                    .dp(activity, 10);

            for (boolean[] ends : new boolean[][]{{true, false}, {false, true},
                    {true, true}, {false, false}}) {
                var mask = (android.graphics.drawable.GradientDrawable)
                        app.morphe.extension.tiktok.settings.preference.SettingsUi
                                .groupRowMask(activity, ends[0], ends[1]);
                float[] radii = mask.getCornerRadii();
                assertNotNull("the mask went back to a plain rectangle", radii);
                float top = ends[0] ? radius : 0f;
                float bottom = ends[1] ? radius : 0f;
                assertEquals("the top of a first=" + ends[0] + " row", top, radii[0], 0.01f);
                assertEquals(top, radii[3], 0.01f);
                assertEquals("the bottom of a last=" + ends[1] + " row", bottom, radii[4], 0.01f);
                assertEquals(bottom, radii[7], 0.01f);
            }
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "xhdpi")
    public void theHandDrawnGlyphsMirrorAndAreDrawnInDp() throws Exception {
        // Two things, both invisible on the captured screenshots because those are density 1.
        // The containers mirror by margin and gravity, so an Arabic or Hebrew reader had a left
        // pointing back arrow at the right edge and chevrons pointing back into the text. And
        // Paint.setStrokeWidth takes canvas pixels, so on a dense screen the menu tile's lines
        // came out under a dp wide beside 40sp type.
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            float density = activity.getResources().getDisplayMetrics().density;
            assertTrue("the fixture is not a dense screen, so this proves nothing", density >= 2f);

            var back = new app.morphe.extension.tiktok.settings.preference
                    .SettingsHeaderPreference.BackDrawable(activity);
            var chevron = new app.morphe.extension.tiktok.settings.preference
                    .SettingsMenuPreference.ChevronDrawable(activity);

            assertTrue("the back arrow does not mirror", back.isAutoMirrored());
            assertTrue("the chevron does not mirror", chevron.isAutoMirrored());

            assertEquals("the back arrow is a hairline on a dense screen",
                    2.1f * density, strokeOf(back), 0.01f);
            assertEquals("the chevron is a hairline on a dense screen",
                    1.8f * density, strokeOf(chevron), 0.01f);
            assertTrue("the chevron is under 2dp wide", strokeOf(chevron) >= 2f);

            // And the glyph itself turns round, rather than only its container.
            assertEquals("the arrow points the same way in both directions",
                    View.LAYOUT_DIRECTION_RTL, mirroredArrowDirection(back, activity));
        }
    }

    /** The stroke the drawable's own paint carries. */
    private static float strokeOf(android.graphics.drawable.Drawable drawable) throws Exception {
        var field = drawable.getClass().getDeclaredField("paint");
        field.setAccessible(true);
        return ((android.graphics.Paint) field.get(drawable)).getStrokeWidth();
    }

    /**
     * Draws the arrow both ways round and answers which direction put its point on the right,
     * which is where a mirrored back arrow points.
     */
    private static int mirroredArrowDirection(
            android.graphics.drawable.Drawable arrow, Activity activity) {
        arrow.setBounds(0, 0, 48, 48);
        arrow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return arrow.getLayoutDirection();
    }

    @Test public void aLongPageTitleLeavesRoomForThePageAtAnyTextScale() throws Exception {
        // 40sp against a 2x text scale is 80sp of page title. The two-times-text capture showed
        // "Kommentare und Uebersetzung" on five lines taking 85% of the screen, with the first
        // card of the page below the fold.
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            float original = configuration.fontScale;
            try {
                configuration.fontScale = 1.0f;
                assertEquals("the ordinary scale should be untouched", 40,
                        app.morphe.extension.tiktok.settings.preference.SettingsHeaderPreference
                                .headingSizeSp(activity));

                configuration.fontScale = 2.0f;
                int large = app.morphe.extension.tiktok.settings.preference
                        .SettingsHeaderPreference.headingSizeSp(activity);
                assertTrue("the title still fills the screen at 2x: " + large, large <= 26);
                assertTrue("the title shrank past readable: " + large, large >= 24);
                assertTrue("the rendered height grew by more than a third",
                        large * 2.0f <= 40 * 1.35f);
            } finally {
                configuration.fontScale = original;
            }
        }
    }

    @Test public void everyDialogActionIsPressableAndReadsAsAButton() throws Exception {
        // The flat actions in the hand built dialogs are TextViews with a click listener, so
        // TalkBack read them as labels rather than as something to press, and the two pickers
        // gave them about 35dp of touch height against a 48dp guideline. Asserted at the one
        // point every one of them goes through.
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);

            for (boolean primary : new boolean[]{true, false}) {
                android.widget.TextView action = new android.widget.TextView(activity);
                action.setText("Save");
                app.morphe.extension.tiktok.settings.preference.SettingsUi
                        .styleTextAction(action, primary);

                int expected = app.morphe.extension.tiktok.settings.preference.SettingsUi
                        .dp(activity, 48);
                assertEquals("a dialog action under the touch guideline",
                        expected, action.getMinimumHeight());
                assertEquals(expected, action.getMinimumWidth());

                var info = android.view.accessibility.AccessibilityNodeInfo.obtain();
                action.onInitializeAccessibilityNodeInfo(info);
                assertEquals("a screen reader would read this as text",
                        android.widget.Button.class.getName(),
                        String.valueOf(info.getClassName()));
            }
        }
    }

    @Test public void renderedControlsSaveAndOpenTheirNativeEditors() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.AUTO_ADVANCE.save(false);
            TikTokPreferenceFragment page = attachSection(activity, "PLAYBACK");
            UiCapture.save(page.getView(), "pages/dark/playback-controls.png");
            ListView list = page.getView().findViewById(android.R.id.list);
            int autoAdvancePosition = positionOf(list, "auto_advance");
            assertTrue(autoAdvancePosition >= 0);
            assertTrue(list.performItemClick(list.getChildAt(autoAdvancePosition), autoAdvancePosition,
                    list.getAdapter().getItemId(autoAdvancePosition)));
            assertTrue(Settings.AUTO_ADVANCE.get());
            int defaultSpeedPosition = positionOf(list, "default_speed");
            assertTrue(defaultSpeedPosition >= 0);
            list.performItemClick(list.getChildAt(defaultSpeedPosition), defaultSpeedPosition,
                    list.getAdapter().getItemId(defaultSpeedPosition));
            android.app.AlertDialog dialog = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            UiCapture.save(dialog.getWindow().getDecorView(), "pages/dark/speed-picker.png");
            ListView choices = dialog.getListView();
            int selected = -1;
            int unselected = -1;
            for (int position = 0; position < choices.getChildCount(); position++) {
                CheckedTextView row = findCheckedTextView(choices.getChildAt(position));
                if (row != null && row.isChecked()) selected = position;
                if (row != null && !row.isChecked() && unselected < 0) unselected = position;
            }
            assertTrue(selected >= 0);
            assertTrue(unselected >= 0);
            assertChoiceIndicator(choices, selected, true, true);
            assertChoiceIndicator(choices, unselected, false, true);
            choices.performItemClick(null, 0, choices.getAdapter().getItemId(0));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("0.5", Settings.DEFAULT_SPEED.get());
            assertFalse(dialog.isShowing());
        }
    }

    @Test public void settingsSearchFindsTranslatedControlAndOpensOriginal() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = attachHome(activity);
            Preference search = findPreference(home.getPreferenceScreen(), "Search settings");
            assertNotNull(search);
            assertTrue(search.getOnPreferenceClickListener().onPreferenceClick(search));
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            TikTokPreferenceFragment page = (TikTokPreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            EditText input = (EditText) page.getView().findViewWithTag("settings_search_input");
            assertNotNull(input);
            input.setText("video length");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            UiCapture.save(page.getView(), "pages/dark/search.png");
            java.util.List<android.widget.EditText> editors = new java.util.ArrayList<>();
            collectEditors(page.getView(), editors);
            assertEquals(1, editors.size());
            Preference result = findPreference(page.getPreferenceScreen(), "Maximum video length");
            assertNotNull(result);
            assertTrue(String.valueOf(result.getSummary()).startsWith("Feed filter"));

            assertTrue(result.getOnPreferenceClickListener().onPreferenceClick(result));
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TikTokPreferenceFragment targetPage = (TikTokPreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            assertEquals("FEED_FILTER", targetPage.getArguments().getString("morphe_settings_section"));
            ListView list = targetPage.getView().findViewById(android.R.id.list);
            int targetPosition = positionOf(list, "max_video_seconds");
            assertTrue(targetPosition >= 0);
            assertTrue(list.getFirstVisiblePosition() <= targetPosition);
            assertTrue(list.getLastVisiblePosition() >= targetPosition);
        }
    }

    @Test @Config(qualifiers = "de-rDE-w480dp-h960dp-night-mdpi")
    public void settingsSearchUsesGermanTextAndClearState() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = attachHome(activity);
            Preference search = findPreference(home.getPreferenceScreen(), "Einstellungen durchsuchen");
            assertNotNull(search);
            assertTrue(search.getOnPreferenceClickListener().onPreferenceClick(search));
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TikTokPreferenceFragment page = (TikTokPreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            EditText input = (EditText) page.getView().findViewWithTag("settings_search_input");
            assertNotNull(input);
            input.setText("Videolänge");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Preference result = findPreference(page.getPreferenceScreen(), "Maximale Videolänge");
            assertNotNull(result);
            assertTrue(String.valueOf(result.getSummary()).startsWith("Feed-Filter"));

            input.setText("kein Treffer");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(findPreference(page.getPreferenceScreen(), "Keine passenden Einstellungen"));
            TextView clear = page.getView().findViewWithTag("settings_search_clear");
            assertNotNull(clear);
            assertTrue(clear.performClick());
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("", input.getText().toString());
            assertNotNull(findPreference(page.getPreferenceScreen(), "Tippe, um Einstellungen zu durchsuchen"));
            View toolbar = page.getView().findViewWithTag("metra_toolbar");
            assertNotNull(toolbar);
            assertTrue(((android.view.ViewGroup) toolbar).getChildAt(0).performClick());
            activity.getFragmentManager().executePendingTransactions();
            assertSame(home, activity.getFragmentManager().findFragmentById(android.R.id.content));
        }
    }

    @Test @Config(qualifiers = "in-rID-w480dp-h960dp-night-mdpi")
    public void settingsSearchUsesIndonesianTextAndCategoryContext() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = attachHome(activity);
            Preference search = findPreference(home.getPreferenceScreen(), "Cari setelan");
            assertNotNull(search);
            assertTrue(search.getOnPreferenceClickListener().onPreferenceClick(search));
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TikTokPreferenceFragment page = (TikTokPreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            EditText input = (EditText) page.getView().findViewWithTag("settings_search_input");
            assertNotNull(input);
            input.setText("Durasi video maksimum");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Preference result = findPreference(page.getPreferenceScreen(), "Durasi video maksimum");
            assertNotNull(result);
            assertTrue(String.valueOf(result.getSummary()).startsWith("Filter feed"));
        }
    }

    @Test public void settingsSearchExcludesStatusGatedControls() throws Exception {
        SettingsStatus.feedFilterEnabled = false;
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment home = attachHome(activity);
            Preference search = findPreference(home.getPreferenceScreen(), "Search settings");
            assertNotNull(search);
            assertTrue(search.getOnPreferenceClickListener().onPreferenceClick(search));
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TikTokPreferenceFragment page = (TikTokPreferenceFragment) activity.getFragmentManager()
                    .findFragmentById(android.R.id.content);
            EditText input = (EditText) page.getView().findViewWithTag("settings_search_input");
            input.setText("video length");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull(findPreference(page.getPreferenceScreen(), "Maximum video length"));
            assertNotNull(findPreference(page.getPreferenceScreen(), "No matching settings"));
        }
    }

    @Test public void localCreatorEditorFiltersAndRemovesIndividualEntries() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.LOCAL_HIDDEN_CREATORS.save("alpha, beta");
            TikTokPreferenceFragment page = attachSection(activity, "FEED_FILTER");
            Preference preference = page.findPreference("local_hidden_creators");
            assertNotNull(preference);
            ListView list = page.getView().findViewById(android.R.id.list);
            int position = positionOf(list, "local_hidden_creators");
            assertTrue(position >= 0);
            assertTrue(list.performItemClick(list.getChildAt(position), position,
                    list.getAdapter().getItemId(position)));
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            android.app.AlertDialog dialog = (android.app.AlertDialog)
                    org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            EditText search = dialog.getWindow().getDecorView().findViewWithTag("creator_list_search");
            assertNotNull(search);
            search.setText("beta");
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            UiCapture.save(dialog.getWindow().getDecorView(), "pages/dark/creator-list.png");
            View remove = dialog.getWindow().getDecorView().findViewWithTag("creator_remove_beta");
            assertNotNull(remove);
            assertTrue(remove.performClick());
            EditText add = dialog.getWindow().getDecorView().findViewWithTag("creator_list_add");
            assertNotNull(add);
            add.setText("gamma");
            TextView addButton = findTextViewContaining(dialog.getWindow().getDecorView(), "Add");
            assertNotNull(addButton);
            assertTrue(addButton.performClick());
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("alpha, gamma", Settings.LOCAL_HIDDEN_CREATORS.get());
            Settings.LOCAL_HIDDEN_CREATORS.save("");
        }
    }

    @Test @Config(qualifiers = "de-rDE-w360dp-h800dp-night-mdpi")
    public void longGermanLabelsWrapAtLargeTextSize() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            configuration.fontScale = 1.3f;
            activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
            TikTokPreferenceFragment page = attachSection(activity, "COMMENTS");
            layout(page.getView(), 360, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            UiCapture.save(page.getView(), "pages/dark/comments-german-large.png", 360, 800);
            android.widget.TextView heading = page.getView().findViewWithTag("metra_page_title");
            assertNotNull(heading);
            assertTrue(heading.getLineCount() > 1);
            assertEquals(0, heading.getLayout().getEllipsisCount(heading.getLineCount() - 1));
            assertTrue(heading.getHeight() >= heading.getLayout().getHeight());
            TextView caption = findTextViewContaining(page.getView(), "Filter, Übersetzung");
            assertNotNull(caption);
            assertTextFits(caption);
            assertRowsReadable(page.getView().findViewById(android.R.id.list), 48);
        }
    }

    @Test @Config(qualifiers = "de-rDE-w320dp-h800dp-notnight-mdpi")
    public void controlsAndEditorsStayReadableAtTwoTimesTextSize() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            configuration.fontScale = 2.0f;
            activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
            TikTokPreferenceFragment page = attachSection(activity, "COMMENTS");
            layout(page.getView(), 320, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TextView caption = findTextViewContaining(page.getView(), "Filter, Übersetzung");
            assertNotNull(caption);
            assertTextFits(caption);
            assertRowsReadable(page.getView().findViewById(android.R.id.list), 48);
            assertEditorRowFits(page, "comment_blocked_keywords", activity, 320, 800, 1);
            // Captured here: the fragment is replaced below, and its view goes with it.
            UiCapture.save(page.getView(), "pages/light/two-times-text.png", 320, 800);
            TikTokPreferenceFragment filterPage = attachSection(activity, "FEED_FILTER");
            layout(filterPage.getView(), 320, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEditorRowFits(filterPage, "max_video_seconds", activity, 320, 800, 1);
            filterPage = attachSection(activity, "FEED_FILTER");
            layout(filterPage.getView(), 320, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEditorRowFits(filterPage, "min_max_views", activity, 320, 800, 2);
        }
    }

    /**
     * The same at twice the text size in the dark theme, which is what most readers are on. A
     * capture of one theme says nothing about the other: the row backgrounds, the dividers and
     * the disabled colours are all different, and a caption that fits on white can still be
     * unreadable on black.
     */
    @Test @Config(qualifiers = "de-rDE-w320dp-h800dp-night-mdpi")
    public void controlsAndEditorsStayReadableAtTwoTimesTextSizeInTheDark() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            configuration.fontScale = 2.0f;
            activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());

            for (String section : MAIN_PAGES) {
                TikTokPreferenceFragment page = attachSection(activity, section);
                layout(page.getView(), 320, 800);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                TextView heading = page.getView().findViewWithTag("metra_page_title");
                assertNotNull(section, heading);
                assertTextFits(heading);
                assertRowsReadable(page.getView().findViewById(android.R.id.list), 48);
                if ("COMMENTS".equals(section)) {
                    UiCapture.save(page.getView(), "pages/dark/two-times-text.png", 320, 800);
                }
            }
        }
    }

    @Test @Config(qualifiers = "ar-rXB-w320dp-h800dp-night-mdpi")
    public void rtlLargeTextKeepsLabelsOrderedAndTouchTargetsUsable() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            configuration.fontScale = 2.0f;
            configuration.setLayoutDirection(new java.util.Locale("ar", "XB"));
            activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
            TikTokPreferenceFragment page = attachSection(activity, "COMMENTS");
            // Robolectric keeps the activity root's direction from its creation configuration;
            // apply the pseudolocale direction to the fragment tree before measuring its rows.
            forceRtl(page.getView());
            layout(page.getView(), 320, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            forceRtl(page.getView());
            assertEquals(View.LAYOUT_DIRECTION_RTL, configuration.getLayoutDirection());
            assertRowsReadable(page.getView().findViewById(android.R.id.list), 48);
            TextView heading = page.getView().findViewWithTag("metra_page_title");
            assertNotNull(heading);
            assertTextFits(heading);
            UiCapture.save(page.getView(), "pages/dark/rtl-large.png", 320, 800);

            // One page proves the row shape; the rest are where a caption written for one
            // language quietly runs off the edge in a mirrored layout.
            for (String section : MAIN_PAGES) {
                TikTokPreferenceFragment other = attachSection(activity, section);
                forceRtl(other.getView());
                layout(other.getView(), 320, 800);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                forceRtl(other.getView());
                TextView title = other.getView().findViewWithTag("metra_page_title");
                assertNotNull(section, title);
                assertTextFits(title);
                assertRowsReadable(other.getView().findViewById(android.R.id.list), 48);
            }
        }
    }

    /**
     * The mirrored layout in the light theme. Both themes are captured because the two are laid
     * out from the same code but read very differently, and the only way to know a label has not
     * run off the edge in one of them is to have both.
     */
    @Test @Config(qualifiers = "ar-rXB-w320dp-h800dp-notnight-mdpi")
    public void rtlLargeTextIsReadableInTheLightThemeToo() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            var configuration = activity.getResources().getConfiguration();
            configuration.fontScale = 2.0f;
            configuration.setLayoutDirection(new java.util.Locale("ar", "XB"));
            activity.getResources().updateConfiguration(configuration, activity.getResources().getDisplayMetrics());
            TikTokPreferenceFragment page = attachSection(activity, "COMMENTS");
            forceRtl(page.getView());
            layout(page.getView(), 320, 800);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            forceRtl(page.getView());

            assertEquals(View.LAYOUT_DIRECTION_RTL, configuration.getLayoutDirection());
            TextView heading = page.getView().findViewWithTag("metra_page_title");
            assertNotNull(heading);
            assertTextFits(heading);
            assertRowsReadable(page.getView().findViewById(android.R.id.list), 48);
            UiCapture.save(page.getView(), "pages/light/rtl-large.png", 320, 800);
        }
    }

    /**
     * The pages a reader actually opens. Not every section: the Lab and the gate editors have
     * their own capture suite, and walking them here would double this test's runtime for
     * coverage that already exists.
     */
    private static final String[] MAIN_PAGES = {"FEED_FILTER", "INTERFACE", "COMMENTS",
            "DOWNLOADS", "PLAYBACK", "INBOX", "SHARE", "BEHAVIOR", "DIAGNOSTICS"};

    private static TikTokPreferenceFragment attachSection(Activity activity, String section) {
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString("morphe_settings_section", section);
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return fragment;
    }

    private static TikTokPreferenceFragment attachHome(Activity activity) {
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return fragment;
    }

    private static Preference findPreference(android.preference.PreferenceScreen screen, String title) {
        for (int index = 0; index < screen.getPreferenceCount(); index++) {
            Preference preference = screen.getPreference(index);
            if (title.equals(String.valueOf(preference.getTitle()))) {
                return preference;
            }
        }
        return null;
    }

    private static int positionOf(ListView list, String key) {
        for (int position = 0; position < list.getAdapter().getCount(); position++) {
            Object item = list.getAdapter().getItem(position);
            if (item instanceof Preference && key.equals(((Preference) item).getKey())) {
                return position;
            }
        }
        return -1;
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static TextView findTextViewContaining(View view, String token) {
        if (view instanceof TextView && String.valueOf(((TextView) view).getText()).contains(token)) {
            return (TextView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView result = findTextViewContaining(group.getChildAt(i), token);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void assertTextFits(TextView text) {
        assertNotNull(text.getLayout());
        int lastLine = Math.max(0, text.getLineCount() - 1);
        assertEquals(0, text.getLayout().getEllipsisCount(lastLine));
        assertTrue(text.getHeight() >= text.getCompoundPaddingTop()
                + text.getLayout().getHeight() + text.getCompoundPaddingBottom());
        assertTrue(text.getBottom() <= ((View) text.getParent()).getHeight());
    }

    private static void assertRowsReadable(ListView list, int minTouchDp) {
        assertNotNull(list);
        int minTouch = Math.round(minTouchDp * list.getResources().getDisplayMetrics().density);
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            assertTrue("row " + i + " is smaller than its touch target", row.getHeight() >= minTouch);
            TextView title = row.findViewById(android.R.id.title);
            if (title != null && title.getLayout() != null) assertTextFits(title);
            TextView summary = row.findViewById(android.R.id.summary);
            if (summary != null && summary.getLayout() != null) assertTextFits(summary);
        }
    }

    private static void assertEditorRowFits(TikTokPreferenceFragment page, String key,
                                            Activity activity, int width, int height,
                                            int expectedEditors) {
        Preference target = page.findPreference(key);
        assertNotNull(target);
        ListView list = page.getView().findViewById(android.R.id.list);
        int position = -1;
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            if (list.getAdapter().getItem(i) == target) position = i;
        }
        assertTrue(position >= 0);
        assertTrue(list.performItemClick(list.getChildAt(position), position, list.getAdapter().getItemId(position)));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        layout(dialog.getWindow().getDecorView(), width, height);
        TextView dialogTitle = findTextViewContaining(dialog.getWindow().getDecorView(),
                String.valueOf(target.getTitle()));
        assertNotNull(dialogTitle);
        assertTextFits(dialogTitle);
        java.util.List<android.widget.EditText> editors = new java.util.ArrayList<>();
        collectEditors(dialog.getWindow().getDecorView(), editors);
        assertEquals(expectedEditors, editors.size());
        for (android.widget.EditText editor : editors) {
            assertTrue(editor.isFocusable());
            assertTrue(editor.getHeight() >= Math.round(48 * activity.getResources().getDisplayMetrics().density));
        }
        dialog.dismiss();
    }

    private static void collectEditors(View view, java.util.List<android.widget.EditText> editors) {
        if (view instanceof android.widget.EditText) editors.add((android.widget.EditText) view);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectEditors(group.getChildAt(i), editors);
        }
    }

    private static void forceRtl(View view) {
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forceRtl(group.getChildAt(i));
        }
    }

    @Config(fontScale = 2.0f, qualifiers = "w320dp-h320dp")
    @Test public void aDiagnosticsRowReachedByScrollingKeepsItsCheckMark() throws Exception {
        // Eight rows at about 48dp is taller than the dialog on a small screen, and taller than
        // any screen at this font scale. The rows past the fold are built after the styling pass
        // has run, so they used to arrive with the platform's end-side check mark and TikTok's
        // text colour.
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokPreferenceFragment page = attachSection(activity, "DIAGNOSTICS");
            ListView list = page.getView().findViewById(android.R.id.list);
            int position = positionOf(list, "action_included_diagnostics");
            assertTrue("the Included diagnostics row is gone", position >= 0);
            list.performItemClick(list.getChildAt(position), position,
                    list.getAdapter().getItemId(position));

            android.app.AlertDialog dialog =
                    (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(dialog.isShowing());
            ListView choices = dialog.getListView();
            assertNotNull(choices);
            int rows = choices.getAdapter().getCount();
            assertTrue("nothing to scroll through", rows >= 6);

            // Short before anything is idled, so the styling pass sees only the top of the list.
            // Laying it out at its natural height first would build every row while the styling
            // is still to come, and then nothing would ever be built unstyled.
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            int styled = choices.getChildCount();
            assertTrue("the whole list fits, so no row is ever built later",
                    styled > 0 && styled < rows);

            choices.setSelection(rows - 1);
            layout(choices, choices.getWidth(), choices.getHeight());
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            int last = choices.getChildCount() - 1;
            assertTrue("no rows after scrolling to the end", last >= 0);
            assertEquals("did not reach the last row", rows - 1,
                    choices.getFirstVisiblePosition() + last);
            CheckedTextView row = findCheckedTextView(choices.getChildAt(last));
            assertNotNull("the last row is not a choice row", row);
            assertNull("the platform check mark came back on a scrolled row",
                    row.getCheckMarkDrawable());
            Drawable indicator = row.getCompoundDrawablesRelative()[0];
            assertNotNull("a scrolled row has no check mark at all", indicator);
            assertEquals("DialogCheckMarkDrawable", indicator.getClass().getSimpleName());
            assertEquals("a scrolled row kept TikTok's text colour",
                    app.morphe.extension.tiktok.settings.preference.SettingsUi.textPrimary(),
                    row.getCurrentTextColor());
        }
    }

    private static void assertChoiceIndicator(ListView list, int position, boolean checked, boolean radio)
            throws Exception {
        CheckedTextView row = findCheckedTextView(list.getChildAt(position));
        assertNotNull(row);
        assertEquals(checked, row.isChecked());
        assertNull(row.getCheckMarkDrawable());
        Drawable indicator = row.getCompoundDrawablesRelative()[0];
        assertNotNull(indicator);
        assertEquals("DialogCheckMarkDrawable", indicator.getClass().getSimpleName());
        Field field = indicator.getClass().getDeclaredField("radio");
        field.setAccessible(true);
        assertEquals(radio, field.getBoolean(indicator));
    }

    private static CheckedTextView findCheckedTextView(android.view.View view) {
        if (view instanceof CheckedTextView) return (CheckedTextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CheckedTextView result = findCheckedTextView(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    @Test public void theResetHourReadsAsATimeOfDay() throws Exception {
        // The row used "o'clock" as its unit for both singular and plural, so it said
        // "Current: 13 o'clock" and, at midnight, "Current: 0 o'clock".
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.SESSION_BUDGET_RESET_HOUR.save(4);
            var row = new app.morphe.extension.tiktok.settings.preference.ClockHourPreference(
                    activity, "Start the day at", "The hour both budgets reset.",
                    Settings.SESSION_BUDGET_RESET_HOUR);

            String summary = String.valueOf(row.getSummary());
            assertTrue("the hour does not read as a time: " + summary, summary.contains("04:00"));
            assertTrue("the unit is still there: " + summary, summary.indexOf("o'clock") < 0);

            // The two values the old wording actually broke on.
            Settings.SESSION_BUDGET_RESET_HOUR.save(13);
            row.setValue("13");
            assertTrue("an afternoon hour: " + row.getSummary(),
                    String.valueOf(row.getSummary()).contains("13:00"));

            Settings.SESSION_BUDGET_RESET_HOUR.save(0);
            row.setValue("0");
            assertTrue("midnight: " + row.getSummary(),
                    String.valueOf(row.getSummary()).contains("00:00"));
            Settings.SESSION_BUDGET_RESET_HOUR.resetToDefault();
        }
    }
}
