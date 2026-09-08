package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * Settings search folds accents away before comparing, so a query of nothing but combining marks
 * folds to nothing. The empty check ran on the query as typed, which such a query passes, and
 * every setting then matched because they all contain the empty string.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@SuppressWarnings("deprecation")
public class SettingsSearchQueryTest {
    private final Map<Field, Boolean> statuses = new LinkedHashMap<>();

    @Before public void setUp() throws Exception {
        Utils.setContext(RuntimeEnvironment.getApplication());
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                statuses.put(field, field.getBoolean(null));
                field.setBoolean(null, true);
            }
        }
    }

    @After public void tearDown() throws Exception {
        for (Map.Entry<Field, Boolean> entry : statuses.entrySet()) {
            entry.getKey().setBoolean(null, entry.getValue());
        }
        statuses.clear();
    }

    @Test public void aQueryOfNothingButAccentMarksMatchesNothing() throws Exception {
        TikTokPreferenceFragment search = attachSearch();
        int all = search(search, "").size();
        assertTrue("the search page showed no rows at all", all >= 1);

        // U+0301 on its own: not empty as typed, nothing at all once folded.
        assertEquals("an accent mark on its own matched the whole catalogue",
                all, search(search, "́").size());
    }

    @Test public void anOrdinaryQueryStillNarrowsTheList() throws Exception {
        TikTokPreferenceFragment search = attachSearch();
        int indexed = indexSize(search);
        int matches = search(search, "comment").size();

        assertTrue("the index is too small to tell narrowing from matching everything", indexed > 20);
        assertTrue("nothing matched an ordinary word", matches > 1);
        // Against the whole catalogue, not against the one row the empty state shows: comparing
        // with the empty query only proved the word produced two rows rather than one.
        assertTrue("the query matched the whole catalogue: " + matches + " rows of " + indexed,
                matches < indexed);
    }

    private static int indexSize(TikTokPreferenceFragment fragment) throws Exception {
        java.lang.reflect.Field field = TikTokPreferenceFragment.class.getDeclaredField("searchIndex");
        field.setAccessible(true);
        return ((java.util.List<?>) field.get(fragment)).size();
    }

    @Test public void anAccentedQueryStillFindsItsUnaccentedSetting() throws Exception {
        TikTokPreferenceFragment search = attachSearch();
        assertEquals("folding stopped matching the word it is there for",
                search(search, "comment").size(), search(search, "cómment").size());
    }

    /** The rows the search page is showing for a query, by title. */
    private static java.util.List<String> search(TikTokPreferenceFragment fragment, String query)
            throws Exception {
        Method update = TikTokPreferenceFragment.class
                .getDeclaredMethod("updateSearchResults", String.class);
        update.setAccessible(true);
        update.invoke(fragment, query);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        java.util.List<String> titles = new java.util.ArrayList<>();
        PreferenceScreen screen = fragment.getPreferenceScreen();
        for (int index = 0; index < screen.getPreferenceCount(); index++) {
            Preference row = screen.getPreference(index);
            if (row.getTitle() != null) titles.add(row.getTitle().toString());
        }
        return titles;
    }

    private static TikTokPreferenceFragment attachSearch() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean("morphe_settings_search", true);
        fragment.setArguments(arguments);
        activity.getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
        activity.getFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return fragment;
    }

    @Test public void theSearchFindsTheRowsPeopleReachForWhenSomethingHasGoneWrong() throws Exception {
        // The header promises a search of every title, description and category, and every row
        // that acts rather than holds a value was missing from it: the four backup rows because
        // they are added to the section screen rather than into its category, and the rest
        // because a row with no key was skipped. Those are exactly the rows someone goes looking
        // for when they need to undo, clear or report something.
        TikTokPreferenceFragment search = attachSearch();
        String[][] wanted = {
                {"restore", "Restore settings"},
                {"back up", "Back up settings"},
                {"undo", "Undo last restore or reset"},
                {"hook", "Hook status"},
                {"diagnostic", "Export diagnostic report"},
                {"seen", "Clear the seen video history"},
        };
        for (String[] pair : wanted) {
            java.util.List<String> titles = search(search, pair[0]);
            assertTrue("searching \"" + pair[0] + "\" did not find \"" + pair[1] + "\", it found "
                    + titles, titles.contains(pair[1]));
        }
    }

    @Test public void openingAFoundBackupRowLandsOnDiagnostics() throws Exception {
        TikTokPreferenceFragment search = attachSearch();
        java.lang.reflect.Field field = TikTokPreferenceFragment.class.getDeclaredField("searchIndex");
        field.setAccessible(true);
        Object found = null;
        for (Object entry : (java.util.List<?>) field.get(search)) {
            java.lang.reflect.Field title = entry.getClass().getDeclaredField("title");
            title.setAccessible(true);
            if ("Restore settings".equals(title.get(entry))) {
                found = entry;
                break;
            }
        }
        assertNotNull("the Restore settings row is not in the index at all", found);
        java.lang.reflect.Field section = found.getClass().getDeclaredField("section");
        section.setAccessible(true);
        assertEquals("opening it would not land on Diagnostics",
                "DIAGNOSTICS", ((Enum<?>) section.get(found)).name());
    }
}
