/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Looper;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/**
 * A blocked creator entry is normally a handle to match exactly. Between slashes it is a
 * pattern instead, which covers a family of accounts rather than one at a time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CreatorPatternTest {
    /** Stands in for the author the feed model carries. */
    public static final class Author {
        public final String uid;
        public final String uniqueId;
        public final String nickname;

        Author(String uid, String uniqueId, String nickname) {
            this.uid = uid;
            this.uniqueId = uniqueId;
            this.nickname = nickname;
        }
    }

    private static Aweme video(String handle, String nickname) {
        Author author = new Author("1234", handle, nickname);
        return new Aweme() {
            @SuppressWarnings("unused")
            public Author getAuthor() {
                return author;
            }
        };
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.BLOCKED_CREATORS.save("");
    }

    @Test public void aPatternTooLongToBeMeantIsRefused() {
        // A creator pattern runs against every name in every feed page, and the list travels
        // in a settings backup, so somebody else's pattern can arrive that way.
        StringBuilder huge = new StringBuilder("/");
        for (int index = 0; index < 60; index++) huge.append("(a+)+");
        huge.append("$/");
        assertTrue(huge.length() > 200);
        assertNull(AdvancedFeedRules.compiled(huge.toString()));

        // Something a person would actually type still compiles.
        assertNotNull(AdvancedFeedRules.compiled("/^news_/"));
    }

    @Test
    public void aPatternMatchesAFamilyOfHandles() {
        Settings.BLOCKED_CREATORS.save("/^news_/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        assertTrue(filter.getEnabled());
        assertTrue(filter.getFiltered(video("news_uk", "The Paper")));
        assertTrue(filter.getFiltered(video("NEWS_US", "Another Paper")));
        assertFalse(filter.getFiltered(video("goodnews_uk", "Good News")));
    }

    @Test
    public void aPatternAlsoReadsTheDisplayName() {
        Settings.BLOCKED_CREATORS.save("/dropship/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        assertTrue(filter.getFiltered(video("someone", "Best Dropship Deals")));
        assertFalse(filter.getFiltered(video("someone", "Woodwork")));
    }

    @Test
    public void anOrdinaryHandleStillMatchesExactly() {
        Settings.BLOCKED_CREATORS.save("@someone, other");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        assertTrue(filter.getFiltered(video("someone", "Some One")));
        assertTrue(filter.getFiltered(video("other", "Other")));
        // An exact entry is not a substring match, which is what patterns are for.
        assertFalse(filter.getFiltered(video("someone_else", "Some One Else")));
    }

    @Test
    public void aPatternThatWillNotCompileIsDroppedAndSaidOnce() {
        ShadowToast.reset();
        Settings.BLOCKED_CREATORS.save("/([unclosed/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        assertFalse(filter.getFiltered(video("anyone", "Anyone")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("nothing was said", ShadowToast.shownToastCount() >= 1);

        int shown = ShadowToast.shownToastCount();
        assertFalse(filter.getFiltered(video("someone", "Someone")));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ShadowToast.shownToastCount() <= shown);
    }

    @Test
    public void aPatternIsCompiledOncePerEntry() {
        assertNotNull(AdvancedFeedRules.compiled("/^a/"));
        // The same entry gives back the same compiled pattern rather than a new one.
        assertTrue(AdvancedFeedRules.compiled("/^a/") == AdvancedFeedRules.compiled("/^a/"));
        assertNull(AdvancedFeedRules.compiled("/([/"));
    }

    @Test
    public void aCommaInsideAPatternIsNotASeparator() {
        // The list splits on commas, and a repetition count contains one.
        assertArrayEquals(new String[]{"/a{2,3}/", "someone"},
                AdvancedFeedRules.rawTerms("/a{2,3}/, someone"));
        assertArrayEquals(new String[]{"@one", "two"}, AdvancedFeedRules.rawTerms("@one, two"));

        Settings.BLOCKED_CREATORS.save("/^aa{1,2}b/");
        assertTrue(new AdvancedFeedRules.CreatorFilter().getFiltered(video("aaab", "Anyone")));
    }

    @Test
    public void aPatternKeepsItsCase() {
        // Lower casing the entry would turn \D into \d and match the opposite thing.
        Settings.BLOCKED_CREATORS.save("/^\\D+$/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        assertTrue(filter.getFiltered(video("letters", "Letters")));
        assertFalse(filter.getFiltered(video("1234", "1234")));
    }

    @Test
    public void aStraySlashDoesNotSwallowTheNamesAfterIt() {
        // An entry that opens a pattern and never closes it used to take the rest of the
        // list with it, and the result matched nothing at all.
        assertArrayEquals(new String[]{"/", "someone", "other"},
                AdvancedFeedRules.rawTerms("/, someone, other"));
        assertArrayEquals(new String[]{"/^news", "someone"},
                AdvancedFeedRules.rawTerms("/^news, someone"));

        Settings.BLOCKED_CREATORS.save("/, someone");
        assertTrue(new AdvancedFeedRules.CreatorFilter().getFiltered(video("someone", "Some One")));
    }

    @Test
    public void aPatternDoesNotReachPastItsLine() {
        // A pattern is written on one line; the next line starts a new entry whatever the
        // one above it left open.
        assertArrayEquals(new String[]{"/^a", "someone"},
                AdvancedFeedRules.rawTerms("/^a\nsomeone"));
        assertArrayEquals(new String[]{"/a{2,3}/", "someone"},
                AdvancedFeedRules.rawTerms("/a{2,3}/\nsomeone"));
    }

    @Test
    public void severalPatternsEachKeepTheirOwnCompiledForm() {
        // Every item on a page runs through every entry, so the entries interleave. A cache
        // of one would hand the second entry the first one's pattern.
        Settings.BLOCKED_CREATORS.save("/^news_/, /dropship/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        for (int i = 0; i < 3; i++) {
            assertTrue(filter.getFiltered(video("news_uk", "The Paper")));
            assertTrue(filter.getFiltered(video("someone", "Best Dropship Deals")));
            assertFalse(filter.getFiltered(video("someone", "Woodwork")));
        }
        assertNotNull(AdvancedFeedRules.compiled("/^news_/"));
        assertNotNull(AdvancedFeedRules.compiled("/dropship/"));
        assertTrue(AdvancedFeedRules.compiled("/^news_/") != AdvancedFeedRules.compiled("/dropship/"));
    }

    @Test
    public void oneBadPatternAmongGoodOnesIsSaidOnceAndTheOthersKeepWorking() {
        ShadowToast.reset();
        Settings.BLOCKED_CREATORS.save("/^news_/, /([bad/");
        AdvancedFeedRules.CreatorFilter filter = new AdvancedFeedRules.CreatorFilter();

        for (int i = 0; i < 4; i++) {
            assertTrue(filter.getFiltered(video("news_uk", "The Paper")));
            assertFalse(filter.getFiltered(video("someone", "Some One")));
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("the bad pattern is named once, not once per video",
                1, ShadowToast.shownToastCount());
    }

    @Test
    public void slashesOnTheirOwnAreNotAPattern() {
        assertFalse(AdvancedFeedRules.isPattern("/"));
        assertFalse(AdvancedFeedRules.isPattern("//"));
        assertTrue(AdvancedFeedRules.isPattern("/a/"));
    }
}
