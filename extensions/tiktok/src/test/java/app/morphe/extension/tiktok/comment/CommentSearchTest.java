package app.morphe.extension.tiktok.comment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.CommentsPreferenceCategory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Which comments a search leaves on screen, and what happens when it is cleared. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentSearchTest {
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }

    public static final class Author {
        public final String uniqueId, nickname;
        Author(String handle, String name) { uniqueId = handle; nickname = name; }
    }

    /** Stands in for TikTok's Comment, whose text and user accessors kept their names. */
    public static final class Comment {
        public final String text;
        public final Author user;
        Comment(String said, String handle, String name) {
            text = said;
            user = handle == null && name == null ? null : new Author(handle, name);
        }
    }

    @Test public void aCommentMatchesOnWhatItSaysOrWhoSaidIt() {
        Comment comment = new Comment("Where is this Filmed?", "traveller99", "Jo Traveller");

        // What it says, whatever case either side is typed in.
        assertTrue(CommentSearch.matches(comment, "filmed"));
        assertTrue(CommentSearch.matches(comment, "where is"));
        // The handle, and the name they show.
        assertTrue(CommentSearch.matches(comment, "traveller99"));
        assertTrue(CommentSearch.matches(comment, "jo trav"));
        // Something in none of the three.
        assertFalse(CommentSearch.matches(comment, "recipe"));

        // An empty box is not a search, so everything stays.
        assertTrue(CommentSearch.matches(comment, ""));
        assertTrue(CommentSearch.matches(new Comment(null, null, null), ""));
        // A comment we cannot read is not claimed to match.
        assertFalse(CommentSearch.matches(null, "filmed"));
        assertFalse(CommentSearch.matches(new Object(), "filmed"));
        assertFalse(CommentSearch.matches(new Comment(null, null, null), "filmed"));
        // A comment with no author still matches on its text.
        assertTrue(CommentSearch.matches(new Comment("filmed here", null, null), "filmed"));
    }

    @Test public void theBoxIsTrimmedAndFoldedOnce() {
        try {
            CommentSearch.setQuery("  Filmed  ");
            assertEquals("filmed", CommentSearch.query());
            CommentSearch.setQuery(null);
            assertEquals("", CommentSearch.query());
        } finally {
            CommentSearch.setQuery("");
        }
    }

    @Test public void rowsCollapseAndComeBackAtTheHeightTheyHad() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Settings.COMMENT_SEARCH.save(true);
            CommentSearch.setQuery("");

            // A column holding the list, which is the shape the box needs to be added.
            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout listView = new LinearLayout(activity);
            listView.setOrientation(LinearLayout.VERTICAL);
            column.addView(listView);
            activity.setContentView(column);

            View first = new View(activity), second = new View(activity);
            for (View row : new View[]{first, second}) {
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 120));
                listView.addView(row);
            }
            CommentSearch.onCellBound(first, new Comment("Where is this filmed?", "traveller99", "Jo"));
            CommentSearch.onCellBound(second, new Comment("Great recipe", "cook", "Sam"));

            // Nothing typed: both rows stand at the height the list gave them.
            assertEquals(View.VISIBLE, first.getVisibility());
            assertEquals(View.VISIBLE, second.getVisibility());
            assertEquals(120, first.getLayoutParams().height);

            // The box went in above the list rather than into it.
            assertEquals(2, column.getChildCount());
            EditText box = (EditText) column.getChildAt(0);
            assertEquals(listView, column.getChildAt(1));

            box.setText("filmed");
            assertEquals(View.VISIBLE, first.getVisibility());
            // A list measures its children itself, so hiding one has to take its height too.
            assertEquals(View.GONE, second.getVisibility());
            assertEquals(0, second.getLayoutParams().height);

            // The author counts as well as the words.
            box.setText("cook");
            assertEquals(View.GONE, first.getVisibility());
            assertEquals(View.VISIBLE, second.getVisibility());
            assertEquals(120, second.getLayoutParams().height);

            // Cleared: everything comes back exactly as it was.
            box.setText("");
            assertEquals(View.VISIBLE, first.getVisibility());
            assertEquals(View.VISIBLE, second.getVisibility());
            assertEquals(120, first.getLayoutParams().height);
            assertEquals(120, second.getLayoutParams().height);

            // A row bound while a search is running arrives already narrowed.
            box.setText("recipe");
            View third = new View(activity);
            third.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 120));
            listView.addView(third);
            CommentSearch.onCellBound(third, new Comment("Nothing alike", "someone", "Someone"));
            assertEquals(View.GONE, third.getVisibility());
            // And the same row recycled onto a comment that does match is put right again.
            CommentSearch.onCellBound(third, new Comment("Best recipe here", "someone", "Someone"));
            assertEquals(View.VISIBLE, third.getVisibility());
            assertEquals(120, third.getLayoutParams().height);
        } finally {
            CommentSearch.setQuery("");
            Settings.COMMENT_SEARCH.save(false);
        }
    }

    @Test public void nothingIsTouchedWhileTheSwitchIsOff() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Settings.COMMENT_SEARCH.save(false);
            CommentSearch.setQuery("filmed");

            LinearLayout column = new LinearLayout(activity);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout listView = new LinearLayout(activity);
            column.addView(listView);
            View row = new View(activity);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120));
            listView.addView(row);

            CommentSearch.onCellBound(row, new Comment("Great recipe", "cook", "Sam"));
            assertEquals(View.VISIBLE, row.getVisibility());
            assertEquals(120, row.getLayoutParams().height);
            // No box either, so the sheet looks exactly as TikTok drew it.
            assertEquals(1, column.getChildCount());
        } finally {
            CommentSearch.setQuery("");
        }
    }

    @Test public void theSwitchIsReachable() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.commentToolsEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new CommentsPreferenceCategory(activity, screen);
            assertNotNull(screen.findPreference("comment_search"));
        } finally {
            SettingsStatus.commentToolsEnabled = false;
        }
    }
}
