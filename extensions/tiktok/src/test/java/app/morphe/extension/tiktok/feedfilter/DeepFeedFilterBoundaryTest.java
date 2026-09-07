/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.IntegerSetting;
import app.morphe.extension.shared.settings.StringSetting;
import app.morphe.extension.tiktok.settings.Settings;

import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeed;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeedList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Direct fixtures for feed delivery, fallback and the bounded filter probe. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DeepFeedFilterBoundaryTest {
    private static final String UNSET_RANGE = "0-" + Long.MAX_VALUE;

    private final Map<BooleanSetting, Boolean> booleans = new LinkedHashMap<>();
    private final Map<StringSetting, String> strings = new LinkedHashMap<>();
    private final Map<IntegerSetting, Integer> integers = new LinkedHashMap<>();
    private boolean debug;

    /** A feed model whose native cache marker and quality data are explicit. */
    public static final class Video extends Aweme {
        private final String id;
        private final boolean ad;
        private final long durationMs;
        private final String caption;
        private final AwemeStatistics statistics;

        Video(String id, boolean ad, long durationMs, String caption) {
            this(id, ad, durationMs, caption, null);
        }

        Video(String id, boolean ad, long durationMs, String caption, AwemeStatistics statistics) {
            this.id = id;
            this.ad = ad;
            this.durationMs = durationMs;
            this.caption = caption;
            this.statistics = statistics;
        }

        @Override public String getAid() { return id; }
        @Override public boolean isAd() { return ad; }
        @Override public boolean isSoftAd() { return false; }
        @Override public boolean isWithPromotionalMusic() { return false; }
        public String getDesc() { return caption; }
        public Object getVideo() { return new Duration(durationMs); }
        @Override public AwemeStatistics getStatistics() { return statistics; }
        @Override public String getShareUrl() { return null; }
    }

    public static final class Duration {
        private final long duration;
        Duration(long duration) { this.duration = duration; }
        public long getDuration() { return duration; }
    }

    private static FollowFeed follow(Video video) {
        FollowFeed entry = new FollowFeed();
        entry.aweme = video;
        return entry;
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        debug = BaseSettings.DEBUG.get();
        BaseSettings.DEBUG.save(false);

        save(Settings.REMOVE_ADS);
        save(Settings.HIDE_LIVE);
        save(Settings.HIDE_STORY);
        save(Settings.HIDE_IMAGE);
        save(Settings.HIDE_SHOP);
        save(Settings.HIDE_BLOCKED_SOUNDS);
        save(Settings.HIDE_PAID_PARTNERSHIP);
        save(Settings.HIDE_AI_GENERATED);
        save(Settings.HIDE_VERIFIED);
        save(Settings.HIDE_SERIES);
        save(Settings.HIDE_PLAYLIST_VIDEOS);
        save(Settings.HIDE_INSERTED_CARDS);
        save(Settings.HIDE_SEEN_VIDEOS);
        save(Settings.HIDE_PROMOTIONAL_MUSIC);
        save(Settings.HIDE_LIVE_REPLAYS);
        save(Settings.FILTER_OFFLINE_FALLBACK_VIDEOS);

        save(Settings.BLOCKED_CAPTION_WORDS);
        save(Settings.BLOCKED_CREATORS);
        save(Settings.LOCAL_HIDDEN_CREATORS);
        save(Settings.BLOCKED_SOUND_IDS);
        save(Settings.BLOCKED_SOUND_NAMES);
        save(Settings.REGION_ONLY_FROM);
        save(Settings.REGION_NEVER_FROM);
        save(Settings.MIN_MAX_VIEWS);
        save(Settings.MIN_MAX_LIKES);
        save(Settings.MIN_MAX_COMMENTS);
        save(Settings.MIN_MAX_FAVOURITES);
        save(Settings.MIN_MAX_SHARES);

        save(Settings.MAX_VIDEO_SECONDS);
        save(Settings.MAX_PUBLICATION_AGE_DAYS);
        save(Settings.MAX_VIEWS_PER_LIKE);
        FeedItemsFilter.resetDiagnosticsForTests();
        disableAllFilters();
    }

    @After
    public void tearDown() {
        for (Map.Entry<BooleanSetting, Boolean> entry : booleans.entrySet()) {
            entry.getKey().save(entry.getValue());
        }
        for (Map.Entry<StringSetting, String> entry : strings.entrySet()) {
            entry.getKey().save(entry.getValue());
        }
        for (Map.Entry<IntegerSetting, Integer> entry : integers.entrySet()) {
            entry.getKey().save(entry.getValue());
        }
        BaseSettings.DEBUG.save(debug);
        FeedItemsFilter.resetDiagnosticsForTests();
    }

    private void save(BooleanSetting setting) {
        booleans.put(setting, setting.get());
    }

    private void save(StringSetting setting) {
        strings.put(setting, setting.get());
    }

    private void save(IntegerSetting setting) {
        integers.put(setting, setting.get());
    }

    private static void disableAllFilters() {
        Settings.REMOVE_ADS.save(false);
        Settings.HIDE_LIVE.save(false);
        Settings.HIDE_STORY.save(false);
        Settings.HIDE_IMAGE.save(false);
        Settings.HIDE_SHOP.save(false);
        Settings.HIDE_BLOCKED_SOUNDS.save(false);
        Settings.HIDE_PAID_PARTNERSHIP.save(false);
        Settings.HIDE_AI_GENERATED.save(false);
        Settings.HIDE_VERIFIED.save(false);
        Settings.HIDE_SERIES.save(false);
        Settings.HIDE_PLAYLIST_VIDEOS.save(false);
        Settings.HIDE_INSERTED_CARDS.save(false);
        Settings.HIDE_SEEN_VIDEOS.save(false);
        Settings.HIDE_PROMOTIONAL_MUSIC.save(false);
        Settings.HIDE_LIVE_REPLAYS.save(false);
        Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.save(true);
        Settings.BLOCKED_CAPTION_WORDS.save("");
        Settings.BLOCKED_CREATORS.save("");
        Settings.LOCAL_HIDDEN_CREATORS.save("");
        Settings.BLOCKED_SOUND_IDS.save("");
        Settings.BLOCKED_SOUND_NAMES.save("");
        Settings.REGION_ONLY_FROM.save("");
        Settings.REGION_NEVER_FROM.save("");
        Settings.MIN_MAX_VIEWS.save(UNSET_RANGE);
        Settings.MIN_MAX_LIKES.save(UNSET_RANGE);
        Settings.MIN_MAX_COMMENTS.save(UNSET_RANGE);
        Settings.MIN_MAX_FAVOURITES.save(UNSET_RANGE);
        Settings.MIN_MAX_SHARES.save(UNSET_RANGE);
        Settings.MAX_VIDEO_SECONDS.save(0);
        Settings.MAX_PUBLICATION_AGE_DAYS.save(0);
        Settings.MAX_VIEWS_PER_LIKE.save(0);
    }

    @Test
    public void repeatedResponseUsesTheFingerprintCacheWithinItsWindow() {
        Settings.REMOVE_ADS.save(true);
        BaseSettings.DEBUG.save(true);
        FeedItemList page = new FeedItemList();
        Video ordinary = new Video("ordinary", false, 500, "");
        page.items = new ArrayList<>(Arrays.asList(ordinary));

        FeedItemsFilter.filter(page);
        FeedItemsFilter.filter(page);

        assertEquals(1, page.items.size());
        assertSame(ordinary, page.items.get(0));
        String summary = FeedItemsFilter.rotateProbeSummaryForTests(System.currentTimeMillis() + 10_000L);
        assertNotNull(summary);
        assertTrue(summary, summary.contains("cacheHits=1"));
        assertTrue(summary, summary.contains("scans=1"));
    }

    @Test
    public void lateFollowDeliveryAndFinalPassBothRemoveNewAds() {
        Settings.REMOVE_ADS.save(true);
        FollowFeedList list = new FollowFeedList();
        list.mItems = new ArrayList<>(Arrays.asList(
                follow(new Video("ordinary", false, 500, "")),
                follow(new Video("first-ad", true, 500, ""))));

        FeedItemsFilter.filterLate(list);
        assertEquals(1, list.mItems.size());

        list.mItems.add(follow(new Video("late-ad", true, 500, "")));
        FeedItemsFilter.filterLateFinal(list);
        assertEquals(1, list.mItems.size());
        assertEquals("ordinary", ((FollowFeed) list.mItems.get(0)).aweme.getAid());
    }

    @Test
    public void cachedAndOfflineDeliveryRespectTheirKeepPolicies() {
        Settings.REMOVE_ADS.save(true);
        Video cachedAd = new Video("cached-ad", true, 500, "");
        cachedAd.cacheSourceType = 0;
        Video cachedOrdinary = new Video("cached-ordinary", false, 500, "");
        cachedOrdinary.cacheSourceType = 2;

        FeedItemList cached = new FeedItemList();
        cached.items = new ArrayList<>(Arrays.asList(cachedAd, cachedOrdinary));
        assertSame(cached, FeedItemsFilter.filterCachedFeedList(cached));
        assertEquals(1, cached.items.size());
        assertSame(cachedOrdinary, cached.items.get(0));

        Video offlineAd = new Video("offline-ad", true, 500, "");
        offlineAd.cacheSourceType = 3;
        FeedItemList offline = new FeedItemList();
        offline.items = new ArrayList<>(List.of(offlineAd));
        Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.save(false);
        assertSame(offline, FeedItemsFilter.filterOfflineFeedList(offline));
        assertTrue(FeedItemsFilter.shouldKeepCachedAweme(offlineAd));

        Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.save(true);
        FeedItemList filteredOffline = new FeedItemList();
        filteredOffline.items = new ArrayList<>(List.of(offlineAd));
        assertNull(FeedItemsFilter.filterOfflineFeedList(filteredOffline));
        assertFalse(FeedItemsFilter.shouldKeepCachedAweme(offlineAd));
    }

    @Test
    public void qualityFallbackNeverRestoresHardRejectedItems() {
        Settings.REMOVE_ADS.save(true);
        Settings.BLOCKED_CAPTION_WORDS.save("blocked");
        Settings.MAX_VIDEO_SECONDS.save(1);

        Video ad = new Video("ad", true, 500, "clear");
        Video blocked = new Video("blocked", false, 500, "blocked caption");
        Video closestQuality = new Video("closest", false, 2_000, "clear");
        FeedItemList page = new FeedItemList();
        page.items = new ArrayList<>(Arrays.asList(ad, blocked, closestQuality));

        FeedItemsFilter.filter(page);

        assertEquals(1, page.items.size());
        assertSame(closestQuality, page.items.get(0));
    }

    @Test
    public void probeSummariesRotateAndBothCachesStayBounded() {
        Settings.REMOVE_ADS.save(true);
        BaseSettings.DEBUG.save(true);
        List<FeedItemList> pages = new ArrayList<>();
        for (int index = 0; index < 300; index++) {
            FeedItemList page = new FeedItemList();
            page.items = new ArrayList<>(List.of(new Video("probe-" + index, false, 500, "")));
            pages.add(page);
            FeedItemsFilter.filter(page);
        }

        assertTrue(FeedItemsFilter.probeSeenListCountForTests() <= 256);
        assertTrue(FeedItemsFilter.processedListCacheSizeForTests() <= 256);
        String summary = FeedItemsFilter.rotateProbeSummaryForTests(System.currentTimeMillis() + 10_000L);
        assertNotNull(summary);
        assertTrue(summary, summary.contains("calls=300"));
        assertTrue(summary, summary.contains("uniqueLists="));
        assertNull(FeedItemsFilter.rotateProbeSummaryForTests(System.currentTimeMillis() + 10_001L));
    }
}
