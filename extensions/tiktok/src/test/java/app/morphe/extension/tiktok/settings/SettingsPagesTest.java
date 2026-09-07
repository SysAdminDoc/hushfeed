package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.*;
import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.graphics.drawable.Drawable;
import android.widget.CheckedTextView;
import android.widget.ListView;
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
            UiCapture.save(page.getView(), "pages/dark/comments-german-large.png", 360, 800);
            android.widget.TextView heading = page.getView().findViewWithTag("metra_page_title");
            assertNotNull(heading);
            assertTrue(heading.getLineCount() > 1);
            assertEquals(0, heading.getLayout().getEllipsisCount(heading.getLineCount() - 1));
            assertTrue(heading.getHeight() >= heading.getLayout().getHeight());
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
