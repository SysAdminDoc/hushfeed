package app.morphe.extension.tiktok.feed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The feed's author row and a comment row both carry a {@code title} view, so the row is
 * identified by the post time sitting beside the name.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AuthorRegionTest {
    private static final int NAME_ID = 0x7f0a0001;
    private static final int POST_TIME_ID = 0x7f0a0002;

    /** Stands in for the Aweme the player says is on screen. */
    public static final class Clip {
        public final String aid;
        public final String region;
        public final Author author;

        Clip(String aid, String handle, String nickname, String region) {
            this.aid = aid;
            this.region = region;
            this.author = new Author(handle, nickname);
        }
    }

    public static final class Author {
        public final String uid = "1234";
        public final String uniqueId;
        public final String nickname;

        Author(String uniqueId, String nickname) {
            this.uniqueId = uniqueId;
            this.nickname = nickname;
        }
    }

    /** A row that says how many times its text was written, not just what it says. */
    public static final class CountingTextView extends TextView {
        int writes;

        CountingTextView(Context context) {
            super(context);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            writes++;
        }
    }

    private static Clip playing(String aid, String handle, String nickname, String region) {
        return new Clip(aid, handle, nickname, region);
    }

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        AuthorRegion.setViewIds(NAME_ID, POST_TIME_ID);
        AuthorRegion.restore();
    }

    /** name + post time in one row, the shape the feed uses. */
    private LinearLayout feedRow(String name) {
        LinearLayout row = new LinearLayout(context);
        TextView title = new TextView(context);
        title.setId(NAME_ID);
        title.setText(name);
        TextView postTime = new TextView(context);
        postTime.setId(POST_TIME_ID);
        postTime.setText("· 4h ago");
        row.addView(title);
        row.addView(postTime);
        return row;
    }

    /** A comment row: same name id, no post time beside it. */
    private LinearLayout commentRow(String name) {
        LinearLayout row = new LinearLayout(context);
        TextView title = new TextView(context);
        title.setId(NAME_ID);
        title.setText(name);
        row.addView(title);
        return row;
    }

    @Test
    public void theCountryIsAppendedOnceAndRemovedAgain() {
        LinearLayout row = feedRow("My path forward");
        TextView name = AuthorRegion.findName(row);
        assertSame(row.getChildAt(0), name);

        AuthorRegion.decorate(name, null, "US");
        assertEquals("My path forward · US", name.getText().toString());

        // Every layout pass runs this, so it must not keep appending.
        AuthorRegion.decorate(name, null, "US");
        AuthorRegion.decorate(name, null, "US");
        assertEquals("My path forward · US", name.getText().toString());

        // Switching the option off, or leaving the feed, puts the name back.
        AuthorRegion.restore();
        assertEquals("My path forward", name.getText().toString());
    }

    @Test
    public void aCommentRowIsNotTheAuthorRow() {
        assertNull(AuthorRegion.findName(commentRow("Joshbetancourt28")));
    }

    @Test
    public void aVideoWithNoRegionLeavesTheNameAlone() {
        LinearLayout row = feedRow("My path forward");
        TextView name = AuthorRegion.findName(row);

        AuthorRegion.decorate(name, null, null);
        assertEquals("My path forward", name.getText().toString());
    }

    @Test
    public void movingToAnotherVideoDecoratesTheNewNameAndReleasesTheOld() {
        TextView first = AuthorRegion.findName(feedRow("first creator"));
        AuthorRegion.decorate(first, null, "US");
        assertEquals("first creator · US", first.getText().toString());

        // TikTok rebinds the row for the next video before this runs again.
        TextView second = AuthorRegion.findName(feedRow("second creator"));
        AuthorRegion.decorate(second, null, "GB");
        assertEquals("second creator · GB", second.getText().toString());
        assertEquals("first creator", first.getText().toString());
    }

    @Test
    public void aVideoChangeOnTheSameRowReplacesTheCountryInsteadOfStackingIt() {
        // The feed recycles the row: the same TextView is rebound for the next video, and
        // the player can name that video before the row is redecorated.
        LinearLayout row = feedRow("alice");
        TextView name = AuthorRegion.findName(row);

        AuthorRegion.decorate(name, null, "US");
        AuthorRegion.decorate(name, null, "GB");
        AuthorRegion.decorate(name, null, "DE");
        assertEquals("alice · DE", name.getText().toString());

        AuthorRegion.restore();
        assertEquals("alice", name.getText().toString());
    }

    @Test
    public void aRebuiltRowWhoseNameExtendsTheOldOneIsLeftAlone() {
        LinearLayout row = feedRow("Sam");
        TextView name = AuthorRegion.findName(row);
        AuthorRegion.decorate(name, null, "US");
        assertEquals("Sam · US", name.getText().toString());

        // TikTok rebinds the recycled row to a creator whose name starts with the old one.
        name.setText("Sam Smith");
        AuthorRegion.restore();
        assertEquals("Sam Smith", name.getText().toString());
    }

    @Test
    public void theHandleReplacesTheDisplayName() {
        LinearLayout row = feedRow("Sam Smith");
        TextView name = AuthorRegion.findName(row);

        AuthorRegion.decorate(name, "samsmith", null);
        assertEquals("@samsmith", name.getText().toString());

        // Every layout pass runs this, so it must settle.
        AuthorRegion.decorate(name, "samsmith", null);
        assertEquals("@samsmith", name.getText().toString());

        AuthorRegion.restore();
        assertEquals("Sam Smith", name.getText().toString());
    }

    @Test
    public void bothSwitchesTogetherGiveTheHandleAndTheCountry() {
        LinearLayout row = feedRow("Sam Smith");
        TextView name = AuthorRegion.findName(row);

        AuthorRegion.decorate(name, "samsmith", "US");
        assertEquals("@samsmith · US", name.getText().toString());

        AuthorRegion.restore();
        assertEquals("Sam Smith", name.getText().toString());
    }

    @Test
    public void aRecycledRowTakesTheNextCreatorsHandle() {
        // The feed rebinds the same TextView for the next video.
        LinearLayout row = feedRow("alice");
        TextView name = AuthorRegion.findName(row);

        AuthorRegion.decorate(name, "alice_v", "US");
        AuthorRegion.decorate(name, "bob_v", "GB");
        assertEquals("@bob_v · GB", name.getText().toString());

        AuthorRegion.restore();
        assertEquals("alice", name.getText().toString());
    }

    @Test
    public void eachSwitchOnlyEverAsksForItsOwnValue() {
        Clip video = playing("one", "samsmith", "Sam Smith", "GB");

        Settings.SHOW_AUTHOR_HANDLE.save(false);
        Settings.SHOW_AUTHOR_REGION.save(false);
        assertNull(AuthorRegion.decoration(video));

        // The country switch on its own must not start replacing names with handles.
        Settings.SHOW_AUTHOR_REGION.save(true);
        assertArrayEquals(new String[]{null, "GB"}, AuthorRegion.decoration(video));

        Settings.SHOW_AUTHOR_HANDLE.save(true);
        Settings.SHOW_AUTHOR_REGION.save(false);
        assertArrayEquals(new String[]{"samsmith", null}, AuthorRegion.decoration(video));

        Settings.SHOW_AUTHOR_REGION.save(true);
        assertArrayEquals(new String[]{"samsmith", "GB"}, AuthorRegion.decoration(video));
    }

    @Test
    public void aVideoWithNothingToShowAsksForNothing() {
        Settings.SHOW_AUTHOR_HANDLE.save(true);
        Settings.SHOW_AUTHOR_REGION.save(true);

        assertNull(AuthorRegion.decoration(playing("two", null, "Nobody", null)));

        // A handle with no country still gives the handle.
        assertArrayEquals(new String[]{"someone", null},
                AuthorRegion.decoration(playing("three", "someone", "Some One", null)));
    }

    @Test
    public void aSettledRowIsNotWrittenAgainOnEveryLayoutPass() {
        // This runs from a global layout listener, so writing on every pass would ask for
        // another layout, and another.
        LinearLayout row = new LinearLayout(context);
        CountingTextView name = new CountingTextView(context);
        name.setId(NAME_ID);
        name.setText("alice");
        TextView postTime = new TextView(context);
        postTime.setId(POST_TIME_ID);
        row.addView(name);
        row.addView(postTime);

        AuthorRegion.decorate(name, null, "US");
        int afterFirst = name.writes;
        assertEquals("alice · US", name.getText().toString());

        for (int i = 0; i < 5; i++) AuthorRegion.decorate(name, null, "US");
        assertEquals(afterFirst, name.writes);

        AuthorRegion.restore();
    }

    @Test
    public void aStyledNameKeepsItsSpans() {
        LinearLayout row = feedRow("");
        TextView name = AuthorRegion.findName(row);
        SpannableString styled = new SpannableString("alice");
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setText(styled);

        AuthorRegion.decorate(name, null, "US");

        assertEquals("alice · US", name.getText().toString());
        CharSequence decorated = name.getText();
        assertTrue(decorated instanceof Spanned);
        assertEquals(1, ((Spanned) decorated).getSpans(0, 5, StyleSpan.class).length);
    }
}
