/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.diagnostics.FeedFilterCounters;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * The Lynx cards in search results, judged as the card cell binds them. The fakes carry the
 * shapes TikTok 47.0.3 has: the cell is a RecyclerView view holder with an itemView, and the item
 * holds the card's data in one field of type DynamicPatch, whose schema names the template.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SearchLynxCardsTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();

    /** Stands in for TikTok's DynamicPatch: the two members read, and nothing a card says. */
    public static class DynamicPatch {
        public String schema;
        public String alaSrc;

        DynamicPatch(String schema, String alaSrc) {
            this.schema = schema;
            this.alaSrc = alaSrc;
        }
    }

    /** The cell's item: its patch sits in an obfuscated field, found by type. */
    public static class Item {
        public final DynamicPatch LLJJIJI;

        Item(DynamicPatch patch) {
            this.LLJJIJI = patch;
        }
    }

    /** The cell: a view holder with its row. */
    public static class Cell {
        public final View itemView;

        Cell(View row) {
            this.itemView = row;
        }
    }

    private static final String DRAMA_SCHEMA = "sslocal://lynxview?channel=search_short_drama_card&bundle=template.js&keyword=short%20drama";
    private static final String OTHER_SCHEMA = "sslocal://lynxview?surl=https%3A%2F%2Fexample.invalid%2Fobj%2Fsearch_music_card%2Ftemplate.js%3Fq%3Dx&keyword=song";

    @Before public void setUp() {
        Settings.HIDE_MINI_DRAMAS.save(false);
        SearchLynxCards.resetForTests();
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    @After public void tearDown() {
        Settings.HIDE_MINI_DRAMAS.resetToDefault();
        SearchLynxCards.resetForTests();
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    @Test public void aDramaModuleIsNamedByItsTemplateOrItsSourceType() {
        assertTrue(SearchLynxCards.isDrama(new DynamicPatch(DRAMA_SCHEMA, null)));
        assertTrue(SearchLynxCards.isDrama(new DynamicPatch(null, "Short_Drama_Hub")));
        assertFalse(SearchLynxCards.isDrama(new DynamicPatch(OTHER_SCHEMA, "music")));
        assertFalse(SearchLynxCards.isDrama(new DynamicPatch(null, null)));
        assertFalse(SearchLynxCards.isDrama(null));
    }

    /** Only the template's name leaves the schema: never the search the other parameters carry. */
    @Test public void theTemplateIsTheChannelOrTheBundleFolderAndNothingElse() {
        assertEquals("search_short_drama_card", SearchLynxCards.lynxTemplate(DRAMA_SCHEMA));
        assertEquals("search_music_card", SearchLynxCards.lynxTemplate(OTHER_SCHEMA));
        assertNull(SearchLynxCards.lynxTemplate("sslocal://lynxview?keyword=short%20drama"));
        assertNull(SearchLynxCards.lynxTemplate(null));
        String kind = SearchLynxCards.kind(new DynamicPatch(DRAMA_SCHEMA, "short_drama"));
        assertEquals("lynx card src short_drama template search_short_drama_card", kind);
        assertFalse("the search reached the card's kind: " + kind, kind.contains("short%20drama") || kind.contains("keyword"));
    }

    @Test public void withHideMiniDramasOnADramaCardsRowCollapsesAndOtherCardsStay() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        View dramaRow = row();
        View otherRow = row();

        SearchLynxCards.onCardBound(new Cell(dramaRow), new Item(new DynamicPatch(DRAMA_SCHEMA, null)));
        SearchLynxCards.onCardBound(new Cell(otherRow), new Item(new DynamicPatch(OTHER_SCHEMA, null)));

        assertCollapsed(dramaRow);
        assertShowing(otherRow);
        assertTrue("the removal was not counted: " + FeedFilterCounters.report(),
                String.join("\n", FeedFilterCounters.report()).contains(SearchLynxCards.DRAMA_REASON));
    }

    /** The Top results' own holder hands the patch itself, beside the fragment: (holder, fragment, patch). */
    @Test public void theTopResultsHolderIsJudgedByThePatchItBinds() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        View dramaRow = row();
        View otherRow = row();

        SearchLynxCards.onHolderBound(new Cell(dramaRow), new Object(), new DynamicPatch(DRAMA_SCHEMA, null));
        SearchLynxCards.onHolderBound(new Cell(otherRow), new Object(), new DynamicPatch(OTHER_SCHEMA, null));

        assertCollapsed(dramaRow);
        assertShowing(otherRow);
        SearchLynxCards.onHolderBound(null, null, null);
        SearchLynxCards.onHolderBound(new Cell(otherRow), null, new Object());
        assertShowing(otherRow);
    }

    @Test public void withHideMiniDramasOffTheDramaCardStays() {
        View row = row();
        SearchLynxCards.onCardBound(new Cell(row), new Item(new DynamicPatch(DRAMA_SCHEMA, null)));
        assertShowing(row);
    }

    /**
     * TikTok's own bind runs after the hook and may size the row again, so the row is collapsed
     * again once the bind has returned. The row is attached here, as a list row is once laid out:
     * a view that is not attached holds a posted pass until it is.
     */
    @Test public void aRowTikTokSizesAgainAfterTheHookIsCollapsedOnceTheBindReturns() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        View row = attachedRow();
        SearchLynxCards.onCardBound(new Cell(row), new Item(new DynamicPatch(DRAMA_SCHEMA, null)));
        row.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        row.setVisibility(View.VISIBLE);

        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertCollapsed(row);
    }

    /** A recycled row gets its size back for a card that stays, and the drama's late pass leaves it alone. */
    @Test public void aRowRecycledToAnotherCardComesBackAndStaysBack() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        View row = attachedRow();
        SearchLynxCards.onCardBound(new Cell(row), new Item(new DynamicPatch(DRAMA_SCHEMA, null)));
        SearchLynxCards.onCardBound(new Cell(row), new Item(new DynamicPatch(OTHER_SCHEMA, null)));
        assertShowing(row);

        Shadows.shadowOf(Looper.getMainLooper()).idle();

        assertShowing(row);
    }

    @Test public void turningTheSwitchOffGivesTheRowBackOnItsNextBind() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        View row = row();
        Item drama = new Item(new DynamicPatch(DRAMA_SCHEMA, null));
        SearchLynxCards.onCardBound(new Cell(row), drama);
        assertCollapsed(row);

        Settings.HIDE_MINI_DRAMAS.save(false);
        SearchLynxCards.onCardBound(new Cell(row), drama);

        assertShowing(row);
    }

    @Test public void oddShapesNeverThrowIntoTikTokBind() {
        Settings.HIDE_MINI_DRAMAS.save(true);
        SearchLynxCards.onCardBound(null, null);
        SearchLynxCards.onCardBound(new Object(), new Object());
        View row = row();
        SearchLynxCards.onCardBound(new Cell(row), null);
        SearchLynxCards.onCardBound(new Cell(row), new Object());
        assertShowing(row);
    }

    private static View row() {
        View row = new View(RuntimeEnvironment.getApplication());
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** A row in a window, the way a list row is once the list has laid it out. */
    private static View attachedRow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout list = new FrameLayout(activity);
        activity.setContentView(list);
        View row = new View(activity);
        list.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return row;
    }

    private static void assertCollapsed(View row) {
        assertEquals("the row is still shown", View.GONE, row.getVisibility());
        assertEquals("the row still takes its height", 0, row.getLayoutParams().height);
    }

    private static void assertShowing(View row) {
        assertEquals("the row is hidden", View.VISIBLE, row.getVisibility());
        assertEquals("the row lost its height", ViewGroup.LayoutParams.WRAP_CONTENT, row.getLayoutParams().height);
    }
}
