/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.tiktok.seen.SeenVideoHistory;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import com.ss.android.ugc.aweme.feed.model.PhotoModeImageInfo;
import com.ss.android.ugc.aweme.feed.model.PhotoModeTextInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The filters that read the shape of a feed item rather than a count or a rule: a story, a
 * photo post, a shop placeholder, an inserted card, a video already watched, and every
 * count range as reached through a feed response.
 *
 * <p>Every case here goes in through {@link FeedItemsFilter#filter(FeedItemList)}, the way
 * TikTok's response reaches the filter, rather than calling the filter on its own. A filter
 * that works on its own and is not on the list does nothing, and only the entry point can
 * show it is on the list. The item builder answers only the getters these filters read;
 * the stub throws for anything else, so a filter that starts reading a new field says so.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ContentShapeFilterTest {
    private static final String UNSET_RANGE = "0-" + Long.MAX_VALUE;
    private static final int INSERTED_CARD_TYPE = 105;

    /** A feed item whose shape is explicit and whose every other getter still throws. */
    private static final class Item extends Aweme {
        private final String aid;
        private boolean story;
        private List<Object> images;
        private PhotoModeImageInfo photoImage;
        private PhotoModeTextInfo photoText;
        private String shareUrl;
        private int type;
        private AwemeStatistics statistics;

        Item(String aid) {
            this.aid = aid;
        }

        Item story() { story = true; return this; }
        Item images(Object... infos) { images = new ArrayList<>(Arrays.asList(infos)); return this; }
        Item photoImage() { photoImage = new PhotoModeImageInfo(); return this; }
        Item photoText() { photoText = new PhotoModeTextInfo(); return this; }
        Item shareUrl(String url) { shareUrl = url; return this; }
        Item type(int awemeType) { type = awemeType; return this; }
        Item count(int index, long value) {
            long[] counts = {30, 30, 30, 30, 30};
            counts[index] = value;
            statistics = new AwemeStatistics() {
                @Override public long getPlayCount() { return counts[0]; }
                @Override public long getDiggCount() { return counts[1]; }
                @Override public long getCommentCount() { return counts[2]; }
                @Override public long getCollectCount() { return counts[3]; }
                @Override public long getShareCount() { return counts[4]; }
            };
            return this;
        }

        @Override public String getAid() { return aid; }
        @Override public boolean getIsTikTokStory() { return story; }
        @Override public List getImageInfos() { return images; }
        @Override public PhotoModeImageInfo getPhotoModeImageInfo() { return photoImage; }
        @Override public PhotoModeTextInfo getPhotoModeTextInfo() { return photoText; }
        @Override public String getShareUrl() { return shareUrl; }
        @Override public int getAwemeType() { return type; }
        @Override public AwemeStatistics getStatistics() { return statistics; }
    }

    private static FeedItemList page(Item... items) {
        FeedItemList list = new FeedItemList();
        list.items = new ArrayList<>(Arrays.asList(items));
        return list;
    }

    /** The aids left on a page after the filter, in order. */
    private static List<String> survivors(FeedItemList list) {
        FeedItemsFilter.filter(list);
        try {
            java.lang.reflect.Field errors = FeedItemsFilter.class.getDeclaredField("filterExceptionLogCount");
            errors.setAccessible(true);
            assertEquals("a filter swallowed an unstubbed getter or another runtime failure", 0,
                    ((AtomicInteger) errors.get(null)).get());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        List<String> aids = new ArrayList<>();
        for (Object item : list.items) aids.add(((Aweme) item).getAid());
        return aids;
    }

    /** Read after the context is set: a Setting touched from a static initialiser throws. */
    private static BooleanSetting[] shapeSwitches() {
        return new BooleanSetting[]{
                Settings.HIDE_STORY, Settings.HIDE_IMAGE, Settings.HIDE_SHOP,
                Settings.HIDE_INSERTED_CARDS, Settings.HIDE_SEEN_VIDEOS,
        };
    }

    private static StringSetting[] rangeSettings() {
        return new StringSetting[]{Settings.MIN_MAX_VIEWS, Settings.MIN_MAX_LIKES,
                Settings.MIN_MAX_COMMENTS, Settings.MIN_MAX_FAVOURITES, Settings.MIN_MAX_SHARES};
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        BaseSettings.DEBUG.save(false);
        Settings.REMOVE_ADS.save(false);
        for (BooleanSetting setting : shapeSwitches()) setting.save(false);
        for (StringSetting setting : rangeSettings()) setting.save(UNSET_RANGE);
        FeedItemsFilter.rebuildRangeFiltersForTests();
        SeenVideoHistory.clear();
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    @After
    public void tearDown() {
        for (BooleanSetting setting : shapeSwitches()) setting.resetToDefault();
        Settings.REMOVE_ADS.resetToDefault();
        for (StringSetting setting : rangeSettings()) setting.resetToDefault();
        FeedItemsFilter.rebuildRangeFiltersForTests();
        SeenVideoHistory.clear();
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    @Test
    public void aStoryIsDroppedOnlyWhenTheSwitchIsOn() {
        assertEquals(Arrays.asList("story", "video"),
                survivors(page(new Item("story").story(), new Item("video"))));

        Settings.HIDE_STORY.save(true);
        assertEquals(Arrays.asList("video"),
                survivors(page(new Item("story").story(), new Item("video"))));
    }

    @Test
    public void aPhotoPostIsDroppedByAnyOfItsThreeMarkers() {
        // TikTok 43.6.2 stopped exposing isImage(), so a photo post is known by its image
        // list, or by either of the photo mode objects. All three shapes exist in the feed.
        Settings.HIDE_IMAGE.save(true);
        assertEquals(Arrays.asList("video", "emptyList"),
                survivors(page(
                        new Item("images").images(new Object()),
                        new Item("photoImage").photoImage(),
                        new Item("photoText").photoText(),
                        new Item("video"),
                        // An empty image list is not a photo post; it is a video that carries
                        // the field.
                        new Item("emptyList").images())));
    }

    @Test
    public void aPhotoPostSurvivesWithTheSwitchOff() {
        assertEquals(Arrays.asList("images", "photoImage", "photoText"),
                survivors(page(
                        new Item("images").images(new Object()),
                        new Item("photoImage").photoImage(),
                        new Item("photoText").photoText())));
    }

    @Test
    public void aShopPlaceholderIsKnownByItsShareLink() {
        Settings.HIDE_SHOP.save(true);
        assertEquals(Arrays.asList("plain", "noLink"),
                survivors(page(
                        new Item("shop").shareUrl("https://www.tiktok.com/t/placeholder_product_id/"),
                        new Item("plain").shareUrl("https://www.tiktok.com/t/ZT8abc/"),
                        new Item("noLink").shareUrl(null))));

        Settings.HIDE_SHOP.save(false);
        assertEquals(Arrays.asList("shop"),
                survivors(page(new Item("shop").shareUrl("x/placeholder_product_id"))));
    }

    @Test
    public void anInsertedCardIsTypeOneHundredAndFive() {
        Settings.HIDE_INSERTED_CARDS.save(true);
        assertEquals(Arrays.asList("video", "other"),
                survivors(page(
                        new Item("card").type(INSERTED_CARD_TYPE),
                        new Item("video").type(0),
                        new Item("other").type(104))));

        Settings.HIDE_INSERTED_CARDS.save(false);
        assertEquals(Arrays.asList("card"),
                survivors(page(new Item("card").type(INSERTED_CARD_TYPE))));
    }

    @Test
    public void aWatchedVideoIsDroppedFromTheNextPage() {
        Settings.HIDE_SEEN_VIDEOS.save(true);
        // Watched to the threshold, which is what marks it seen.
        SeenVideoHistory.onPlayProgressChange("watched", 9_000, 10_000);

        assertEquals(Arrays.asList("fresh"),
                survivors(page(new Item("watched"), new Item("fresh"))));

        // With the switch off the record is kept but not acted on.
        Settings.HIDE_SEEN_VIDEOS.save(false);
        assertEquals(Arrays.asList("watched", "fresh"),
                survivors(page(new Item("watched"), new Item("fresh"))));
    }

    @Test
    public void viewRangeFiltersTheFeedResponse() { assertRange(0); }

    @Test
    public void likeRangeFiltersTheFeedResponse() { assertRange(1); }

    @Test
    public void commentRangeFiltersTheFeedResponse() { assertRange(2); }

    @Test
    public void favouriteRangeFiltersTheFeedResponse() { assertRange(3); }

    @Test
    public void shareRangeFiltersTheFeedResponse() { assertRange(4); }

    private static void assertRange(int index) {
        rangeSettings()[index].save("10-50");
        // Production snapshots ranges at process start. Use that same constructor path
        // after arranging each test's settings; keep the actual feed entry point below.
        FeedItemsFilter.rebuildRangeFiltersForTests();
        assertEquals(Arrays.asList("lower", "inside", "upper", "noCounts"),
                survivors(page(new Item("below").count(index, 9),
                        new Item("lower").count(index, 10),
                        new Item("inside").count(index, 30),
                        new Item("upper").count(index, 50),
                        new Item("above").count(index, 51), new Item("noCounts"))));

        rangeSettings()[index].save(UNSET_RANGE);
        FeedItemsFilter.rebuildRangeFiltersForTests();
        assertEquals(Arrays.asList("below", "above"),
                survivors(page(new Item("below").count(index, 9), new Item("above").count(index, 51))));
    }
}
