package app.morphe.extension.tiktok.feed;

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
