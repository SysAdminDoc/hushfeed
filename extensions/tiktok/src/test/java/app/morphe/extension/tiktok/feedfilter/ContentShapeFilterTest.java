/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The filters that read the shape of a feed item rather than a count or a rule: a story, a
 * photo post, a shop placeholder, an inserted card, a video already watched, and the two
 * count ranges that had no test of their own.
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
        Item counts(long plays, long likes) {
            statistics = new AwemeStatistics() {
                @Override public long getPlayCount() { return plays; }
                @Override public long getDiggCount() { return likes; }
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

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        BaseSettings.DEBUG.save(false);
        for (BooleanSetting setting : shapeSwitches()) setting.save(false);
        Settings.MIN_MAX_VIEWS.save(UNSET_RANGE);
        Settings.MIN_MAX_LIKES.save(UNSET_RANGE);
        SeenVideoHistory.clear();
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    @After
    public void tearDown() {
        for (BooleanSetting setting : shapeSwitches()) setting.resetToDefault();
        Settings.MIN_MAX_VIEWS.resetToDefault();
        Settings.MIN_MAX_LIKES.resetToDefault();
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
    public void theViewAndLikeRangesFilterOnTheirOwnCount() {
        // The range filters read their setting once, when the filter list is built, so the
        // ones on the list cannot be reconfigured here. The classes are exercised directly,
        // and CountRangeFilterTest.everyRangeIsWiredIntoTheFeedFilter pins that these two
        // are on the list.
        Settings.MIN_MAX_VIEWS.save("1000-5000");
        Settings.MIN_MAX_LIKES.save("10-50");
        ViewCountFilter views = new ViewCountFilter();
        LikeCountFilter likes = new LikeCountFilter();
        assertTrue(views.getEnabled());
        assertTrue(likes.getEnabled());

        Item inside = new Item("inside").counts(3000, 30);
        assertFalse(views.getFiltered(inside));
        assertFalse(likes.getFiltered(inside));

        Item fewViews = new Item("fewViews").counts(999, 30);
        assertTrue(views.getFiltered(fewViews));
        assertFalse("likes answered for views", likes.getFiltered(fewViews));

        Item manyLikes = new Item("manyLikes").counts(3000, 51);
        assertTrue(likes.getFiltered(manyLikes));
        assertFalse("views answered for likes", views.getFiltered(manyLikes));

        // Both ends inclusive, the same as the other three ranges.
        assertFalse(views.getFiltered(new Item("low").counts(1000, 30)));
        assertFalse(views.getFiltered(new Item("high").counts(5000, 30)));
        assertTrue(views.getFiltered(new Item("past").counts(5001, 30)));
    }

    @Test
    public void anUnsetViewOrLikeRangeIsOffAndAnItemWithoutCountsIsKept() {
        assertFalse(new ViewCountFilter().getEnabled());
        assertFalse(new LikeCountFilter().getEnabled());

        Settings.MIN_MAX_VIEWS.save("1000-5000");
        Settings.MIN_MAX_LIKES.save("10-50");
        assertFalse(new ViewCountFilter().getFiltered(new Item("noCounts")));
        assertFalse(new LikeCountFilter().getFiltered(new Item("noCounts")));
    }

    @Test
    public void theViewAndLikeRangesAreOnTheList() throws Exception {
        java.lang.reflect.Field field = FeedItemsFilter.class.getDeclaredField("RANGE_FILTERS");
        field.setAccessible(true);
        List<Class<?>> classes = new ArrayList<>();
        for (Object filter : (List<?>) field.get(null)) classes.add(filter.getClass());
        assertTrue("views", classes.contains(ViewCountFilter.class));
        assertTrue("likes", classes.contains(LikeCountFilter.class));
    }

    @Test
    public void everyShapeFilterIsOnTheContentList() throws Exception {
        java.lang.reflect.Field field = FeedItemsFilter.class.getDeclaredField("CONTENT_FILTERS");
        field.setAccessible(true);
        List<Class<?>> classes = new ArrayList<>();
        for (Object filter : (List<?>) field.get(null)) classes.add(filter.getClass());
        for (Class<?> expected : new Class<?>[]{
                StoryFilter.class, ImageVideoFilter.class, ShopFilter.class,
                CardFilters.InsertedCardFilter.class, SeenVideoFilter.class}) {
            assertTrue(expected.getSimpleName() + " is not on the list",
                    classes.contains(expected));
        }
    }
}
