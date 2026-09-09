/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * The editor for locally hidden creators: one row per entry, a search box over them, an add
 * field with the same checks the feed applies, and a save that only lands on Save.
 *
 * <p>Nothing named this class in a test. Its rules live in AdvancedFeedRules, which is
 * tested, but the dialog is where they are wired to the rows a reader sees, and a dialog
 * can call the right helper with the wrong list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CreatorListTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    @org.junit.Before
    public void thePageIsThere() {
        // The Feed filter page only exists on a bundle with the feed filter patch in it.
        app.morphe.extension.tiktok.settings.SettingsStatus.feedFilterEnabled = true;
    }

    @After
    public void reset() {
        app.morphe.extension.tiktok.settings.SettingsStatus.feedFilterEnabled = false;
        Settings.LOCAL_HIDDEN_CREATORS.resetToDefault();
        ShadowToast.reset();
    }

    /**
     * The editor as the Feed filter page carries it. On its own the preference has no
     * PreferenceManager, so persistString writes nowhere and a save proves nothing. On the
     * page it writes the settings' own preferences file, which is what the phone does.
     */
    private static CreatorListPreference open(Activity activity) {
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putString("morphe_settings_section", "FEED_FILTER");
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        CreatorListPreference preference =
                (CreatorListPreference) fragment.findPreference(Settings.LOCAL_HIDDEN_CREATORS.key);
        assertNotNull("the Feed filter page has no hidden creators row", preference);
        return preference;
    }

    /** The remove buttons present, which is one per visible row, in order. */
    private static java.util.List<String> rows(View view) {
        java.util.List<String> found = new java.util.ArrayList<>();
        collect(view, found);
        return found;
    }

    private static void collect(View view, java.util.List<String> into) {
        Object tag = view.getTag();
        if (tag instanceof String && ((String) tag).startsWith("creator_remove_")) {
            into.add(((String) tag).substring("creator_remove_".length()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) collect(group.getChildAt(index), into);
        }
    }

    private static View byDescription(View view, String description) {
        if (view.getContentDescription() != null
                && description.contentEquals(view.getContentDescription())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = byDescription(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The text of the empty state when it is showing, or null when rows are. */
    private static String emptyState(View view) {
        for (String text : new String[]{"No creators are hidden yet", "No hidden creators match this search"}) {
            if (visibleText(view, text)) return text;
        }
        return null;
    }

    private static boolean visibleText(View view, String text) {
        if (view instanceof TextView && view.getVisibility() == View.VISIBLE
                && text.contentEquals(((TextView) view).getText())) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (visibleText(group.getChildAt(index), text)) return true;
            }
        }
        return false;
    }

    @Test
    public void theSavedListIsShownOneRowEachAndSavedBackOnlyOnSave() {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.LOCAL_HIDDEN_CREATORS.save("alice, bob");

            CreatorListPreference preference = open(activity);
            View view = preference.onCreateDialogView();
            assertEquals(java.util.List.of("alice", "bob"), rows(view));
            assertNull(emptyState(view));

            // Remove one, then cancel: the setting is untouched.
            byDescription(view, "Remove bob").performClick();
            assertEquals(java.util.List.of("alice"), rows(view));
            preference.onDialogClosed(false);
            assertEquals("alice, bob", Settings.LOCAL_HIDDEN_CREATORS.get());

            // Do it again and save: the setting follows the rows.
            view = preference.onCreateDialogView();
            byDescription(view, "Remove bob").performClick();
            preference.onDialogClosed(true);
            assertEquals("alice", Settings.LOCAL_HIDDEN_CREATORS.get());
        }
    }

    @Test
    public void addingAnEntryChecksItTheWayTheFeedWould() {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.LOCAL_HIDDEN_CREATORS.save("alice");

            CreatorListPreference preference = open(activity);
            View view = preference.onCreateDialogView();
            EditText add = view.findViewWithTag("creator_list_add");
            View addButton = byDescription(view, "Add hidden creator");
            assertNotNull(add);
            assertNotNull(addButton);

            // Nothing typed: told so, nothing added.
            ShadowToast.reset();
            addButton.performClick();
            assertEquals("Enter a creator handle or id", ShadowToast.getTextOfLatestToast());
            assertEquals(java.util.List.of("alice"), rows(view));

            // The same creator with a different case and an @: the feed treats those as one
            // entry, so the editor refuses the second copy.
            ShadowToast.reset();
            add.setText("@Alice");
            addButton.performClick();
            assertEquals("That creator is already in the list", ShadowToast.getTextOfLatestToast());
            assertEquals(java.util.List.of("alice"), rows(view));

            // A pattern that will not compile is refused here rather than at the next feed
            // page, with the entry named.
            ShadowToast.reset();
            add.setText("/[/");
            addButton.performClick();
            assertTrue(String.valueOf(ShadowToast.getTextOfLatestToast()),
                    ShadowToast.getTextOfLatestToast().contains("/[/"));
            assertEquals(java.util.List.of("alice"), rows(view));

            // A good one lands as a row and clears the field.
            add.setText("  carol  ");
            addButton.performClick();
            assertEquals(java.util.List.of("alice", "carol"), rows(view));
            assertEquals("", add.getText().toString());

            preference.onDialogClosed(true);
            assertEquals("alice, carol", Settings.LOCAL_HIDDEN_CREATORS.get());
        }
    }

    @Test
    public void theSearchNarrowsTheRowsAndSaysWhenNothingMatches() {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.LOCAL_HIDDEN_CREATORS.save("alice, bob, bobby");

            CreatorListPreference preference = open(activity);
            View view = preference.onCreateDialogView();
            EditText search = view.findViewWithTag("creator_list_search");
            assertNotNull(search);

            search.setText("BOB");
            assertEquals(java.util.List.of("bob", "bobby"), rows(view));
            assertNull(emptyState(view));

            search.setText("zed");
            assertEquals(java.util.List.of(), rows(view));
            assertEquals("No hidden creators match this search", emptyState(view));

            search.setText("");
            assertEquals(java.util.List.of("alice", "bob", "bobby"), rows(view));

            // A search never changes what is saved, only what is shown.
            search.setText("bob");
            preference.onDialogClosed(true);
            assertEquals("alice, bob, bobby", Settings.LOCAL_HIDDEN_CREATORS.get());
        }
    }

    @Test
    public void anEmptyListSaysSoInsideTheListRatherThanUnderIt() {
        try (var owner = Robolectric.buildActivity(app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            Settings.LOCAL_HIDDEN_CREATORS.save("");

            View view = open(activity).onCreateDialogView();
            assertEquals(java.util.List.of(), rows(view));
            assertEquals("No creators are hidden yet", emptyState(view));
        }
    }
}
