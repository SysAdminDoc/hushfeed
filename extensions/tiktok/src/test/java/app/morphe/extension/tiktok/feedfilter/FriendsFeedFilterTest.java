package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** What survives the Friends tab, which arrives as its own response rather than a feed list. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FriendsFeedFilterTest {
    /** A video the ads filter can read. Nothing else is enabled while these tests run. */
    public static class Video extends Aweme {
        private final boolean advert;
        Video(boolean advert) { this.advert = advert; }
        @Override public String getAid() { return advert ? "ad" : "video"; }
        @Override public boolean isAd() { return advert; }
        @Override public boolean isSoftAd() { return false; }
        @Override public boolean isWithPromotionalMusic() { return false; }
    }

    /** Stands in for FriendsFeed: the video, or a room when the card is a LIVE one. */
    public static final class Entry {
        public Aweme aweme;
        public Object roomStruct;
        Entry(Aweme aweme) { this.aweme = aweme; }
        static Entry live() {
            Entry entry = new Entry(null);
            entry.roomStruct = new Object();
            return entry;
        }
    }

    /** Stands in for FriendsFeedResponse, whose list field kept its name. */
    public static final class Response {
        public List<Entry> friendFeedData;
        Response(Entry... entries) { friendFeedData = new ArrayList<>(List.of(entries)); }
    }

    private final Map<BooleanSetting, Boolean> saved = new LinkedHashMap<>();

    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        // Built here, not in a static field: reading a Setting runs the Settings class
        // initializer, which needs the context that was set on the line above.
        BooleanSetting[] others = {
                Settings.HIDE_STORY, Settings.HIDE_IMAGE, Settings.HIDE_SHOP,
                Settings.HIDE_BLOCKED_SOUNDS, Settings.HIDE_PAID_PARTNERSHIP,
                Settings.HIDE_AI_GENERATED, Settings.HIDE_VERIFIED, Settings.HIDE_SERIES,
                Settings.HIDE_PLAYLIST_VIDEOS, Settings.HIDE_INSERTED_CARDS,
                Settings.HIDE_SEEN_VIDEOS, Settings.HIDE_PROMOTIONAL_MUSIC,
                Settings.HIDE_LIVE_REPLAYS,
        };
        for (BooleanSetting setting : others) {
            saved.put(setting, setting.get());
            setting.save(false);
        }
        saved.put(Settings.REMOVE_ADS, Settings.REMOVE_ADS.get());
        saved.put(Settings.HIDE_LIVE, Settings.HIDE_LIVE.get());
    }

    @After public void tearDown() {
        for (var entry : saved.entrySet()) entry.getKey().save(entry.getValue());
    }

    @Test public void anAdvertAndALiveCardAreBothDroppedFromTheFriendsTab() {
        Settings.REMOVE_ADS.save(true);
        Settings.HIDE_LIVE.save(true);

        Entry ordinary = new Entry(new Video(false));
        Response response = new Response(ordinary, new Entry(new Video(true)), Entry.live());

        FeedItemsFilter.filterFriendsFeed(response);

        assertEquals(1, response.friendFeedData.size());
        assertSame(ordinary, response.friendFeedData.get(0));
    }

    @Test public void aLiveCardStaysWhenThatSwitchIsOff() {
        Settings.REMOVE_ADS.save(true);
        Settings.HIDE_LIVE.save(false);

        Response response = new Response(new Entry(new Video(false)), Entry.live());
        FeedItemsFilter.filterFriendsFeed(response);
        assertEquals(2, response.friendFeedData.size());
    }

    @Test public void nothingIsTouchedWhenEveryFilterIsOff() {
        Settings.REMOVE_ADS.save(false);
        Settings.HIDE_LIVE.save(false);

        Response response = new Response(new Entry(new Video(true)));
        List<Entry> before = response.friendFeedData;

        FeedItemsFilter.filterFriendsFeed(response);
        assertSame("the list is left exactly as it arrived", before, response.friendFeedData);
    }

    @Test public void aResponseWithNothingToReadIsSurvivable() {
        Settings.REMOVE_ADS.save(true);
        Settings.HIDE_LIVE.save(false);

        Response empty = new Response();
        List<Entry> before = empty.friendFeedData;
        FeedItemsFilter.filterFriendsFeed(empty);
        assertSame(before, empty.friendFeedData);

        // A shape the filter cannot read is left alone rather than throwing.
        FeedItemsFilter.filterFriendsFeed(null);
        FeedItemsFilter.filterFriendsFeed(new Object());

        // An entry carrying neither a video nor a room is kept: nothing says to drop it.
        Response odd = new Response(new Entry(null));
        FeedItemsFilter.filterFriendsFeed(odd);
        assertEquals(1, odd.friendFeedData.size());
    }

    @Test public void everyEntryCanBeDroppedWhenTheyAllMatch() {
        Settings.REMOVE_ADS.save(true);
        Settings.HIDE_LIVE.save(false);

        Response response = new Response(new Entry(new Video(true)), new Entry(new Video(true)));
        FeedItemsFilter.filterFriendsFeed(response);
        assertTrue("a page of adverts leaves nothing, and the next page loads",
                response.friendFeedData.isEmpty());
    }
}
