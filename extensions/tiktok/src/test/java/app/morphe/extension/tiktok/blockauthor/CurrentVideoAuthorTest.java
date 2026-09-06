package app.morphe.extension.tiktok.blockauthor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.preference.PreferenceActivity;

import app.morphe.extension.shared.Utils;

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
    public static final class TestActivity extends PreferenceActivity {}

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

}
