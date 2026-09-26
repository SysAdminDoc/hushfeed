package app.morphe.extension.tiktok.blockauthor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import android.preference.PreferenceActivity;

import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.Rule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The feed binds the items either side of the current one before the user reaches them, so
 * "most recently bound" is not "on screen". These cover the difference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CurrentVideoAuthorTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    public static final class TestActivity extends PreferenceActivity {}

    @Before public void setUp() {
        CurrentVideoAuthor.resetForTests();
    }

    @After public void tearDown() {
        CurrentVideoAuthor.resetForTests();
    }

    /** Stands in for VideoItemParams. */
    public static final class Params {
        public final Clip aweme;

        Params(String awemeId, String uid) {
            aweme = new Clip(awemeId, uid);
        }
    }

    public static final class Clip {
        public final String aid;
        public final Author author;

        Clip(String aid, String uid) {
            this.aid = aid;
            this.author = new Author(uid);
        }
    }

    public static final class Author {
        public final String uid;
        public final String uniqueId;

        Author(String uid) {
            this.uid = uid;
            this.uniqueId = uid;
        }
    }

    @Test
    public void bindingTheNextVideoDoesNotChangeWhoIsTargeted() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());

            // The video on screen, bound and then confirmed by the player.
            CurrentVideoAuthor.update(new Params("aweme_one", "creator_one"));
            CurrentVideoAuthor.onPlaying("aweme_one");
            assertEquals("creator_one", CurrentVideoAuthor.get().uid);

            // The feed prefetches the next item while the first is still playing. This is
            // the bug: taking the latest bind as current armed the button on creator_two.
            CurrentVideoAuthor.update(new Params("aweme_two", "creator_two"));
            assertEquals("creator_one", CurrentVideoAuthor.get().uid);

            // Only the player moving on changes the target.
            CurrentVideoAuthor.onPlaying("aweme_two");
            assertEquals("creator_two", CurrentVideoAuthor.get().uid);
        }
    }

    @Test
    public void aBindThatLandsWhileThePlayerIsChoosingIsKept() throws Exception {
        // The player names the next video on its own thread and finds it not bound yet. The bind
        // landing on another thread right then selected it, and the player's "nothing" was
        // written after, so the video played out with no creator to block and no sound to save.
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            CurrentVideoAuthor.update(new Params("aweme_one", "creator_one"));
            CurrentVideoAuthor.onPlaying("aweme_one");

            Thread[] binder = new Thread[1];
            CurrentVideoAuthor.setBetweenLookupAndSelectForTests(() -> {
                binder[0] = new Thread(() -> CurrentVideoAuthor.update(new Params("aweme_two", "creator_two")));
                binder[0].start();
                try {
                    // Long enough for an unguarded bind to finish; a guarded one waits its turn.
                    binder[0].join(300);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                CurrentVideoAuthor.onPlaying("aweme_two");
            } finally {
                CurrentVideoAuthor.setBetweenLookupAndSelectForTests(null);
            }
            binder[0].join(5_000);
            assertEquals("a bind that landed mid-choice was overwritten",
                    "creator_two", CurrentVideoAuthor.get() == null ? null : CurrentVideoAuthor.get().uid);
        }
    }

    @Test
    public void aVideoThePlayerNamesBeforeItIsBoundTargetsNobody() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());

            CurrentVideoAuthor.update(new Params("aweme_one", "creator_one"));
            CurrentVideoAuthor.onPlaying("aweme_one");
            assertEquals("creator_one", CurrentVideoAuthor.get().uid);

            // Nothing is known about this one yet, so the button must hide rather than
            // stay pointed at the previous creator.
            CurrentVideoAuthor.onPlaying("aweme_unbound");
            assertNull(CurrentVideoAuthor.get());

            // It arms once the bind arrives.
            CurrentVideoAuthor.update(new Params("aweme_unbound", "creator_three"));
            assertEquals("creator_three", CurrentVideoAuthor.get().uid);
        }
    }

    @Test
    public void trackingSurvivesTheBlockButtonBeingTurnedOff() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            Utils.setContext(controller.get());
            Settings.BLOCK_AUTHOR_BUTTON.save(false);

            // Not interested, the follow and like confirmations, double tap to open
            // comments and subtitle tracking all read this and depend on this patch for
            // it, so the block button's own switch must not decide whether it runs.
            BlockAuthorPatch.setCurrentVideoParams(new Params("aweme_gated", "creator_gated"));
            BlockAuthorPatch.setPlayingAweme("aweme_gated");

            assertEquals("aweme_gated",
                    Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"));
            assertEquals("creator_gated", CurrentVideoAuthor.get().uid);
        } finally {
            Settings.BLOCK_AUTHOR_BUTTON.save(false);
        }
    }

    @Test
    public void authorEqualityAndHashingUseTheSameCanonicalIdentifier() {
        VideoAuthor first = new VideoAuthor("uid-1", "sec-1", "First", "video-1");
        VideoAuthor rebound = new VideoAuthor("uid-1", "sec-2", "Renamed", "video-2");
        VideoAuthor secureOnly = new VideoAuthor(null, "sec-1", "First", "video-3");
        VideoAuthor emptyUid = new VideoAuthor("", "sec-1", "First", "video-4");

        assertEquals(first, rebound);
        assertEquals(first.hashCode(), rebound.hashCode());
        assertNotEquals(first, secureOnly);
        assertEquals(secureOnly, emptyUid);
        assertEquals(secureOnly.hashCode(), emptyUid.hashCode());

        java.util.Set<VideoAuthor> authors = new java.util.HashSet<>();
        authors.add(first);
        authors.add(rebound);
        authors.add(secureOnly);
        authors.add(emptyUid);
        assertEquals(2, authors.size());
    }
}
