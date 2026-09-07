package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.UiCapture;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @Test public void renderedControlsSaveAndOpenTheirNativeEditors() throws Exception {
        try (var owner = Robolectric.buildActivity(PageActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.AUTO_ADVANCE.save(false);
            TikTokPreferenceFragment page = attachSection(activity, "PLAYBACK");
            UiCapture.save(page.getView(), "pages/dark/playback-controls.png");
            ListView list = page.getView().findViewById(android.R.id.list);
            assertTrue(list.performItemClick(list.getChildAt(2), 2, list.getAdapter().getItemId(2)));
            assertTrue(Settings.AUTO_ADVANCE.get());
            list.performItemClick(list.getChildAt(4), 4, list.getAdapter().getItemId(4));
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
        }
    }

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
}
