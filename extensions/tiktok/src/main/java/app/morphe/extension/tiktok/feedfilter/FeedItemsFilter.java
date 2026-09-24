/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/feedfilter/FeedItemsFilter.java
 */
package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.shared.diagnostics.FeedFilterCounters;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeBizExtKt;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import com.ss.android.ugc.aweme.feed.model.FeedItemList;
import com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeed;
import com.ss.android.ugc.aweme.follow.presenter.FollowFeedList;

import app.morphe.extension.tiktok.blockauthor.Reflect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public final class FeedItemsFilter {
    /** Route names for the diagnostic report. The exported counters are read by these. */
    static final String SEARCH_SOURCE = "SearchMixFeedList";
    static final String FINAL_INSERT_SOURCE = "FinalInsert:";

    private static final AdsFilter ADS_FILTER = new AdsFilter();
    private static final LocationBadgeFilter LOCATION_FILTER = new LocationBadgeFilter();
    private static final List<IFilter> CONTENT_FILTERS = List.of(
        ADS_FILTER,
        new LiveFilter(),
        new StoryFilter(),
        new ImageVideoFilter(),
        new ShopFilter(),
        new SoundFilter(),
        new ContentMarkerFilters.PaidPartnershipFilter(),
        LOCATION_FILTER,
        new ContentMarkerFilters.AiGeneratedFilter(),
        new ContentMarkerFilters.VerifiedFilter(),
        new ContentMarkerFilters.SeriesFilter(),
        new ContentMarkerFilters.DramaFilter(),
        new ContentMarkerFilters.PlaylistFilter(),
        new CardFilters.InsertedCardFilter(),
        new SeenVideoFilter(),
        new AdvancedFeedRules.KeywordFilter(),
        new AdvancedFeedRules.CreatorFilter(),
        new AdvancedFeedRules.PromotionalMusicFilter(),
        new AdvancedFeedRules.LiveReplayFilter(),
        new RegionFilter(),
        new AdvancedFeedRules.PublicationAgeFilter(),
        new AdvancedFeedRules.QualityFilter()
    );
    private static volatile List<IFilter> RANGE_FILTERS = createRangeFilters();
    private static final List<IFilter> AD_ONLY_FILTERS = List.of(ADS_FILTER);
    private static final List<IFilter> LATE_FOLLOW_FILTERS = List.of(ADS_FILTER, LOCATION_FILTER);
    /** The card shapes TikTok uses for a bought search result. */
    private static final String[] SEARCH_AD_FIELDS = {"multiAdCard", "aiAdCard", "brandZoneCard"};
    /** The feed type TikTok 47.0.3 gives a single TikTok Shop product card in search results. */
    static final int SHOP_PRODUCT_FEED_TYPE = 96;

    private static final int CACHE_SOURCE_COLD_CACHE = 0;
    private static final int CACHE_SOURCE_FEED_UNCONSUMED = 1;
    private static final int CACHE_SOURCE_GOLDEN_HOUSE = 2;
    private static final int CACHE_SOURCE_OFFLINE_MODE = 3;
    private static final int CACHE_SOURCE_MERGE_CACHE = 4;

    private static final int MAX_NULL_ITEMS_LOGS = 3;
    private static final int MAX_BATCH_LOGS = 10;
    private static final int MAX_ITEM_LOGS = 50;
    /**
     * Lines for items a profile or insertion route kept, with logging on. Its own budget, so
     * a profile of two hundred videos cannot spend the one the removed items draw on. Issue
     * #2's ads were kept, and the removed-only log could never show what they looked like.
     */
    private static final int MAX_KEPT_ITEM_LOGS = 200;
    private static final AtomicInteger keptItemLogCount = new AtomicInteger();
    /** Classes already named as not being videos; one line per class, not per item. */
    private static final Set<String> NOT_VIDEO_CLASSES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final int MAX_NOT_VIDEO_CLASSES = 16;
    private static final boolean FILTER_CALL_PROBE_ENABLED = true;
    private static final boolean FILTER_CALL_PROBE_STACKS = false;
    private static final boolean FILTER_CALL_PROBE_SUMMARY_ENABLED = true;
    private static final int FILTER_CALL_PROBE_AID_SAMPLE_SIZE = 5;
    private static final int FILTER_CALL_PROBE_MAX_SEEN_LISTS = 256;
    private static final int FILTER_CALL_PROBE_SLOW_MS = 8;
    private static final long FILTER_CALL_PROBE_SUMMARY_WINDOW_MS = 5000;
    private static final long PROCESSED_LIST_CACHE_TTL_MS = 250;
    private static final int PROCESSED_LIST_CACHE_MAX_SEEN_LISTS = 256;
    private static final AtomicInteger feedItemListNullItemsLogCount = new AtomicInteger();
    private static final AtomicInteger followFeedListNullItemsLogCount = new AtomicInteger();
    private static final AtomicInteger batchLogCount = new AtomicInteger();
    private static final AtomicInteger itemLogCount = new AtomicInteger();
    private static final AtomicInteger filterExceptionLogCount = new AtomicInteger();
    private static final AtomicInteger listReplacementLogCount = new AtomicInteger();
    private static final AtomicInteger filterCallProbeCount = new AtomicInteger();
    private static final Map<Integer, ProbeSeenList> filterCallProbeSeenLists = new HashMap<>();
    private static final Map<Integer, ProcessedListState> processedListCache = new HashMap<>();
    private static final Object filterCallProbeSummaryLock = new Object();
    private static ProbeSummary filterCallProbeSummary = new ProbeSummary(System.currentTimeMillis());

    private FeedItemsFilter() {}

    private static List<IFilter> createRangeFilters() {
        return List.of(new ViewCountFilter(), new LikeCountFilter(), new CommentCountFilter(),
                new FavouriteCountFilter(), new ShareCountFilter());
    }

    /** Recreates the process-start snapshot after a test configures its range settings. */
    static void rebuildRangeFiltersForTests() {
        RANGE_FILTERS = createRangeFilters();
    }

    /** Clears process-wide probe state between deterministic runtime tests. */
    static void resetDiagnosticsForTests() {
        keptItemLogCount.set(0);
        NOT_VIDEO_CLASSES.clear();
        feedItemListNullItemsLogCount.set(0);
        followFeedListNullItemsLogCount.set(0);
        batchLogCount.set(0);
        itemLogCount.set(0);
        filterExceptionLogCount.set(0);
        listReplacementLogCount.set(0);
        profileAdRefusalLogCount.set(0);
        filterCallProbeCount.set(0);
        synchronized (filterCallProbeSeenLists) {
            filterCallProbeSeenLists.clear();
        }
        synchronized (processedListCache) {
            processedListCache.clear();
        }
        synchronized (filterCallProbeSummaryLock) {
            filterCallProbeSummary = new ProbeSummary(System.currentTimeMillis());
        }
        FeedFilterFeedback.resetForTests();
        FeedFilterCounters.clear();
    }

    static int probeSeenListCountForTests() {
        synchronized (filterCallProbeSeenLists) {
            return filterCallProbeSeenLists.size();
        }
    }

    static int processedListCacheSizeForTests() {
        synchronized (processedListCache) {
            return processedListCache.size();
        }
    }

    static String rotateProbeSummaryForTests(long nowMs) {
        synchronized (filterCallProbeSummaryLock) {
            return rotateProbeSummaryIfReadyLocked(nowMs);
        }
    }

    public static void filter(FeedItemList feedItemList) {
        boolean verbose = BaseSettings.DEBUG.get();

        if (feedItemList == null || feedItemList.items == null) {
            if (verbose) {
                logNullItems("FeedItemList", feedItemListNullItemsLogCount);
            }
            return;
        }

        if (verbose && shouldLogBatch()) {
            debugLogBatch(
                "FeedItemList",
                feedItemList.items,
                "fetchType=" + feedItemList.fetchType
                    + " hasMore=" + feedItemList.hasMore
                    + " cursor=" + feedItemList.cursor
                    + " requestId=" + (feedItemList.requestId == null ? "missing" : "present")
            );
        }

        filterFeedList(
            "FeedItemList:response",
            feedItemList,
            feedItemList.items,
            container -> (container instanceof Aweme) ? (Aweme) container : null,
            verbose,
            true,
            FilterPhase.RESPONSE
        );
    }

    /**
     * Last shared boundary before TikTok consumes a main-feed response.
     *
     * <p>TikTok 47.0.3 can restore or finish populating a {@link FeedItemList} without returning
     * it through {@code FeedApiService.fetchFeedList}. Every consumer still reads the response
     * through {@code FeedItemList.getItems()}, so filtering there catches cached and late-filled
     * lists as well. The wrapper must fail open because this method runs inside TikTok's model
     * getter. A filter failure must never make the feed getter throw.
     */
    public static void filterOnRead(FeedItemList feedItemList) {
        try {
            HookStatus.bound("main feed", "FeedItemList.getItems");
            filter(feedItemList);
        } catch (Throwable ex) {
            HookStatus.threw("main feed", "FeedItemList.getItems", ex);
            Logger.printException(() -> "Could not filter the main feed while reading it", ex);
        }
    }

    public static void filter(FollowFeedList followFeedList) {
        filterFollowFeedListSafely(followFeedList, true, FilterPhase.RESPONSE);
    }

    public static void filterLate(FollowFeedList followFeedList) {
        filterFollowFeedListSafely(followFeedList, true, FilterPhase.LATE_FOLLOW);
    }

    public static void filterLateFinal(FollowFeedList followFeedList) {
        filterFollowFeedListSafely(followFeedList, false, FilterPhase.LATE_FOLLOW);
    }

    /**
     * These run inside TikTok's own FollowFeedList.getItems and its response path, so anything
     * that escapes here escapes into TikTok. Issue #12 was exactly that: a NoSuchFieldError, which
     * no catch of Exception stops, closed the app every time the Following tab loaded on 46.9.3.
     * A Following feed that keeps an ad is a much smaller failure than no app.
     */
    private static void filterFollowFeedListSafely(
        FollowFeedList followFeedList,
        boolean allowRecentSkip,
        FilterPhase phase
    ) {
        try {
            filterFollowFeedList(followFeedList, allowRecentSkip, phase);
        } catch (Throwable ex) {
            HookStatus.threw(FOLLOW_FEED_HOOK_FAMILY, phase.name(), ex);
            Logger.printException(() -> "Could not filter the Following feed", ex);
        }
    }

    private static final String FOLLOW_FEED_HOOK_FAMILY = "following feed";

    /**
     * Where FollowFeedList keeps its items: mItems up to 46.8.3, items from 46.9.3, which moved
     * the class to Kotlin. getItems() exists on every build, but the late filter is hooked into
     * it, so the field is read directly, the newer name first because older builds never
     * declare it.
     */
    private static final String[] FOLLOW_ITEMS_FIELDS = {"items", "mItems"};

    private static Field followItemsField(FollowFeedList list) {
        Field field = Reflect.firstField(list.getClass(), FOLLOW_ITEMS_FIELDS);
        if (field == null) {
            HookStatus.missingMember(FOLLOW_FEED_HOOK_FAMILY, "field", "FollowFeedList", "items");
        } else {
            HookStatus.bound(FOLLOW_FEED_HOOK_FAMILY, "items field " + field.getName());
        }
        return field;
    }

    static List followItems(FollowFeedList list) {
        if (list == null) return null;
        Field field = followItemsField(list);
        if (field == null) return null;
        try {
            Object value = field.get(list);
            return value instanceof List ? (List) value : null;
        } catch (IllegalAccessException ex) {
            return null;
        }
    }

    public static List filterProfileAds(List items) {
        return filterAdOnlyAwemeList("ProfileAwemeList", items);
    }

    /**
     * The ad event the profile detail pager raises for its own list. It used to share the
     * profile line, so an ad reported from that pager (issue #2) could not be told from one on
     * the grid; a line of its own says whether the pager's route ran and what it took out.
     */
    public static List filterProfileDetailAds(List items) {
        return filterAdOnlyAwemeList("ProfileDetailAdEvent", items);
    }

    /** The counter line for the mid-roll splice, so an export names the route. */
    static final String MID_AD_SOURCE = "MidAdInsert";
    private static final String MID_AD_HOOK_FAMILY = "mid-roll ads";
    private static final String MID_AD_REASON = "MidAdFilter";

    /**
     * Called where the mid-roll ad component is created, so the family is in the export on a
     * run where no ad was ever due. Without it a missing family says both "not patched" and
     * "never fired", and issue #2 spent six releases unable to tell those apart.
     */
    public static void midAdInstalled() {
        HookStatus.bound(MID_AD_HOOK_FAMILY, "installed");
    }

    /**
     * The route issue #2 was about. TikTok's mid-roll ad component takes the video on screen
     * and an ad, finds the video in the pager adapter and puts the ad in its place, after every
     * list the other hooks filter has already gone by. That is how an ad reached the eighth
     * and the fifteenth video of a profile while the profile list itself carried 184 videos
     * with 0 removed and every marker false: the ad was never in that list.
     *
     * <p>Answers true when the splice should not happen. Everything the component splices in
     * is an ad by construction (its own show event is {@code midroll_ads_show}), so the markers
     * are logged for the record rather than consulted, and the video the ad would have
     * replaced stays where it was.
     */
    public static boolean dropMidAd(Aweme ad) {
        HookStatus.bound(MID_AD_HOOK_FAMILY, "splice");
        FeedFilterCounters.sawList(MID_AD_SOURCE, ad == null ? 0 : 1);
        if (ad == null) return false;
        boolean verbose = BaseSettings.DEBUG.get();
        if (!ADS_FILTER.getEnabled()) {
            logKeptItem(MID_AD_SOURCE, ad, verbose);
            return false;
        }
        FeedFilterCounters.removed(MID_AD_SOURCE, 1, MID_AD_REASON);
        logItem(ad, MID_AD_REASON, verbose);
        return true;
    }

    /** The counter line for the cold-start TopView preload, the one ad route no list filter sees. */
    static final String TOP_VIEW_SOURCE = "TopViewPreload";
    private static final String TOP_VIEW_HOOK_FAMILY = "topview preload";
    private static final String TOP_VIEW_REASON = "TopViewFilter";

    /**
     * Called where the feed fetch hands its preload ads to the splash ad service, so the family
     * is in the export on a cold start that was served no TopView. Without it a missing family
     * reads as both "not patched" and "never served", which is the confusion the mid-roll
     * marker exists to end.
     */
    public static void topViewPreloadInstalled() {
        HookStatus.bound(TOP_VIEW_HOOK_FAMILY, "installed");
    }

    /**
     * The cold-start TopView is the one community leak route the list filters cannot reach. The
     * feed fetch reads {@code preloadAds} off the response, stamps each ad with the request id and
     * hands the list to the splash ad service, all before {@code fetchFeedList} returns, which is
     * where {@link #filter(FeedItemList)} runs; so a reset of that field at filter time reaches
     * nothing. Called as the fetch reads the field, before TikTok's own "nothing to preload"
     * check: the route is counted on every fetch, ads or none (a delivery of nothing has to
     * leave a line too, or an account served no TopView reads the same as a build where the
     * fetch was never patched), and with Remove ads on a list of ads is replaced by an empty
     * one, so that check then sends TikTok down the path it takes on every fetch served no ads,
     * past the stamping, the handoff and the preload task. Emptying at the handoff instead would
     * hand the service an empty list it never sees in stock TikTok and run its task on the
     * result. With the switch off the list goes through untouched. Fails open: this runs inside
     * the cold-start fetch, and a failure here must never keep the feed from loading.
     *
     * @return the list to carry on with: a fresh empty one when the ads are dropped, else
     *         {@code preloads} itself (null stays null, so TikTok's own null handling holds).
     */
    public static List<?> dropTopViewPreload(List<?> preloads) {
        try {
            HookStatus.bound(TOP_VIEW_HOOK_FAMILY, "read");
            int count = preloads == null ? 0 : preloads.size();
            FeedFilterCounters.sawList(TOP_VIEW_SOURCE, count);
            if (count == 0) return preloads;
            boolean verbose = BaseSettings.DEBUG.get();
            if (!ADS_FILTER.getEnabled()) {
                for (Object ad : preloads) {
                    if (ad instanceof Aweme) logKeptItem(TOP_VIEW_SOURCE, (Aweme) ad, verbose);
                }
                return preloads;
            }
            FeedFilterCounters.removed(TOP_VIEW_SOURCE, count, TOP_VIEW_REASON);
            for (Object ad : preloads) {
                if (ad instanceof Aweme) logItem((Aweme) ad, TOP_VIEW_REASON, verbose);
            }
            // A fresh mutable list, in case anything of TikTok's adds to it before the check.
            return new ArrayList<>();
        } catch (Throwable ex) {
            HookStatus.threw(TOP_VIEW_HOOK_FAMILY, "read", ex);
            Logger.printException(() -> "Could not empty the TopView preload while the feed fetched it", ex);
            return preloads;
        }
    }

    /** The counter line for the profile pager's own ad request, so an export names the route. */
    static final String PROFILE_AD_SOURCE = "ProfileAdResponse";
    private static final String PROFILE_AD_HOOK_FAMILY = "profile ads";
    private static final String PROFILE_AD_REASON = "ProfileAdFilter";
    private static final AtomicInteger profileAdRefusalLogCount = new AtomicInteger();

    /**
     * TikTok's own answer to whether a creator's video pager should ask for ads, on its way out.
     *
     * <p>Issue #2 was still open on 0.40.0: its reporter's export showed the pager asking a
     * commerce endpoint of its own, {@code /tiktok/v1/ad/profile_page/}, whose answer carries a
     * list of ads the module splices between the creator's videos. None of those ads were ever
     * in the profile list the other hooks read, and the mid-roll splice never fired, which is
     * why every export said nothing was removed. Answering no here while Remove feed ads is on
     * means that request is never sent.
     */
    public static boolean allowProfileAdRequest(boolean eligible) {
        boolean removing = ADS_FILTER.getEnabled();
        HookStatus.bound(PROFILE_AD_HOOK_FAMILY, !eligible ? "request not wanted"
                : removing ? "request refused" : "request allowed");
        if (eligible && removing && BaseSettings.DEBUG.get()
                && profileAdRefusalLogCount.getAndIncrement() < MAX_BATCH_LOGS) {
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] Refused the profile page's own ad "
                    + "request (TikTok wanted ads on this profile)");
        }
        return eligible && !removing;
    }

    /**
     * The ad list of that request's response, wherever TikTok reads it: the request itself,
     * the coroutine that inserts the ads and the ad measurement. With the request refused above
     * this should not run at all; it is here for a request that reaches the endpoint some other
     * way. The list is emptied in place, so TikTok's own insertion answers "Response list has no
     * ads" and stops, and the later reads of the same response find nothing to count again.
     */
    @SuppressWarnings("rawtypes")
    public static List filterProfileAdResponse(List ads) {
        if (ads == null || ads.isEmpty()) return ads;
        HookStatus.bound(PROFILE_AD_HOOK_FAMILY, "response read");
        FeedFilterCounters.sawList(PROFILE_AD_SOURCE, ads.size());
        boolean verbose = BaseSettings.DEBUG.get();
        if (!ADS_FILTER.getEnabled()) {
            for (Object item : ads) {
                if (item instanceof Aweme) logKeptItem(PROFILE_AD_SOURCE, (Aweme) item, verbose);
            }
            return ads;
        }
        FeedFilterCounters.removed(PROFILE_AD_SOURCE, ads.size(), PROFILE_AD_REASON);
        for (Object item : ads) {
            if (item instanceof Aweme) logItem((Aweme) item, PROFILE_AD_REASON, verbose);
        }
        try {
            ads.clear();
            return ads;
        } catch (UnsupportedOperationException immutable) {
            return new ArrayList<>();
        }
    }

    /**
     * The Top and Videos grids on the search page. Their cards are not Awemes, so the app's
     * own verdict on each one is the reliable test, with the wrapped video checked as well
     * for anything the card itself does not admit to. TikTok Shop cards go too when Hide
     * TikTok Shop in search is on.
     *
     * Called on the parsed response before anything reads its items.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void filterSearchAds(Object searchResult) {
        if (searchResult == null) return;

        Object raw = Reflect.readField(searchResult, "mItems");
        if (!(raw instanceof List)) return;
        List items = (List) raw;
        if (items.isEmpty()) return;
        FeedFilterCounters.sawList(SEARCH_SOURCE, items.size());
        // Always, like the list count: which shapes a page carried is what a report of a card
        // that got through needs, whether or not any switch is on.
        for (Object card : items) FeedFilterCounters.sawKind(SEARCH_SOURCE, searchCardKind(card));
        boolean ads = ADS_FILTER.getEnabled();
        boolean shop = Settings.HIDE_SEARCH_SHOP.get();
        if (!ads && !shop) return;

        ArrayList kept = new ArrayList(items.size());
        int adsRemoved = 0;
        int shopRemoved = 0;
        for (Object card : items) {
            if (ads && isSearchAd(card)) {
                adsRemoved++;
            } else if (shop && isSearchShopProduct(card)) {
                shopRemoved++;
            } else {
                kept.add(card);
            }
        }
        if (kept.size() == items.size()) return;
        if (kept.isEmpty()) {
            // Every card on the page matched. A whole page of them is far less likely than one
            // of the card shapes being wrong, and an empty grid gives the user nothing to go on.
            // When the adverts alone would have left cards, it is the Shop shapes that emptied
            // the page, so only the adverts go, as they did before the Shop switch existed;
            // otherwise the page is left alone.
            if (adsRemoved == 0 || adsRemoved == items.size()) {
                Logger.printException(() -> "Every search result looked like an advert or a Shop card, so none were removed");
                return;
            }
            for (Object card : items) {
                if (!isSearchAd(card)) kept.add(card);
            }
            shopRemoved = 0;
            Logger.printException(() -> "Every search result looked like an advert or a Shop card, so only the adverts were removed");
        }

        Field field = Reflect.field(searchResult.getClass(), "mItems");
        if (field == null) return;
        try {
            field.set(searchResult, kept);
        } catch (Exception exception) {
            Logger.printException(() -> "Could not filter the search results", exception);
            return;
        }

        // Counted only once the page has actually been rewritten. The all-ads refusal above and
        // a failed write both leave the grid alone, and a counter that said otherwise would
        // point an ad report at a route that removed nothing.
        FeedFilterCounters.removed(SEARCH_SOURCE, adsRemoved, "searchAd");
        FeedFilterCounters.removed(SEARCH_SOURCE, shopRemoved, "searchShop");

        // printInfo is not gated on the debug switch, unlike printDebug, so every search page
        // used to append to the bounded diagnostic buffer and push out the events around a crash.
        if (BaseSettings.DEBUG.get() && shouldLogBatch()) {
            int before = items.size();
            int after = kept.size();
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(SearchMixFeedList): size "
                + before + " -> " + after + " (removed=" + (before - after) + ")");
        }
    }

    /**
     * A TikTok Shop card in search results (issue #21): the Products block TikTok draws from a
     * Shop dynamic patch, a single product card, or a product or shop card that is not a video.
     * A video that carries commerce data of its own stays; that is the feed filter's business.
     */
    static boolean isSearchShopProduct(Object card) {
        if (card == null) return false;
        Object patch = Reflect.readField(card, "dynamicPatch");
        if (patch != null && Boolean.TRUE.equals(Reflect.readField(patch, "isEcom"))) return true;
        Object type = Reflect.readField(card, "feedType");
        if (type instanceof Integer && (Integer) type == SHOP_PRODUCT_FEED_TYPE) return true;
        if (Reflect.readField(card, "aweme") != null) return false;
        return Reflect.readField(card, "productStruct") != null || Reflect.readField(card, "shopCard") != null;
    }

    /**
     * What a search card is made of, by shape alone, for the export: its feed type and which of
     * the video, Shop and advert shapes it carries. Never anything the card says.
     */
    static String searchCardKind(Object card) {
        if (card == null) return "none";
        Object type = Reflect.readField(card, "feedType");
        StringBuilder kind = new StringBuilder("type ").append(type instanceof Integer ? type : "?");
        if (Reflect.readField(card, "aweme") != null) kind.append(" video");
        if (Reflect.readField(card, "productStruct") != null) kind.append(" product");
        if (Reflect.readField(card, "shopCard") != null) kind.append(" shop card");
        Object patch = Reflect.readField(card, "dynamicPatch");
        if (patch != null) {
            kind.append(Boolean.TRUE.equals(Reflect.readField(patch, "isEcom")) ? " Shop patch" : " patch");
            String source = searchCardSource(Reflect.readField(patch, "alaSrc"));
            if (source != null) kind.append(" ").append(source);
        }
        if (Reflect.readField(card, "minis") != null) kind.append(" minis");
        if (Reflect.readField(card, "miniGame") != null) kind.append(" mini game");
        if (Reflect.readField(card, "entityCard") != null) kind.append(" hub");
        if (Reflect.readField(card, "nimbleCardInfo") != null) kind.append(" nimble");
        if (Reflect.readField(card, "commonAladdin") != null) kind.append(" aladdin");
        if (patch == null) {
            String source = searchCardSource(Reflect.readField(card, "mAlaSrc"));
            if (source != null) kind.append(" ").append(source);
        }
        for (String name : SEARCH_AD_FIELDS) {
            if (Reflect.readField(card, name) != null) {
                kind.append(" advert");
                break;
            }
        }
        return kind.toString();
    }

    /**
     * A search card's source type (its alaSrc), the name TikTok's server gives the kind of card
     * it sent, such as a Shop block or a drama module. A card type, not anything the card says:
     * only letters, digits and underscores are kept, and at most 40 of them.
     */
    static String searchCardSource(Object alaSrc) {
        if (!(alaSrc instanceof String)) return null;
        StringBuilder out = new StringBuilder();
        for (char c : ((String) alaSrc).toCharArray()) {
            if (out.length() == 40) break;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') out.append(c);
        }
        return out.length() == 0 ? null : "src " + out;
    }

    /** True when the card is an advert, by its own admission or by the video it wraps. */
    static boolean isSearchAd(Object card) {
        if (card == null) return false;
        // The card's own answer, with no other shape to fall back on, so a build that renamed it
        // leaves search ads unfiltered and the report has to say so.
        if (Boolean.TRUE.equals(Reflect.required(card, "isAdOrContainAd"))) return true;

        for (String name : SEARCH_AD_FIELDS) {
            if (Reflect.readField(card, name) != null) return true;
        }

        Object aweme = Reflect.readField(card, "aweme");
        return aweme instanceof Aweme && getFilterReason(AD_ONLY_FILTERS, (Aweme) aweme) != null;
    }

    /**
     * The Friends tab, which is its own feed and does not arrive as a FeedItemList. Its
     * response holds a list of FriendsFeed wrappers, each carrying the video in a real named
     * {@code aweme} field, and a LIVE card carries a {@code roomStruct} instead. Every
     * consumer reads that list straight off the field, so it is filtered where the response
     * is built rather than at any one delivery point.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void filterFriendsFeed(Object response) {
        try {
            if (response == null) return;

            List<IFilter> activeFilters = getActiveFilters(CONTENT_FILTERS);
            boolean hideLive = Settings.HIDE_LIVE.get();
            if (activeFilters.isEmpty() && !hideLive) return;

            Object raw = Reflect.readField(response, "friendFeedData");
            if (!(raw instanceof List)) return;
            List items = (List) raw;
            if (items.isEmpty()) return;

            ArrayList kept = new ArrayList(items.size());
            Map<String, Integer> reasonCounts = BaseSettings.DEBUG.get() ? new HashMap<>() : null;
            for (Object entry : items) {
                String reason = friendsFeedReason(entry, activeFilters, hideLive);
                if (reason == null) {
                    kept.add(entry);
                } else {
                    incrementReason(reasonCounts, reason);
                }
            }
            if (kept.size() == items.size()) return;

            Field field = Reflect.field(response.getClass(), "friendFeedData");
            if (field == null) return;
            field.set(response, kept);

            final int before = items.size();
            final int after = kept.size();
            final String reasons = reasonCounts == null ? "" : " reasons=" + reasonCounts;
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(FriendsFeedResponse): size "
                + before + " -> " + after + " (removed=" + (before - after) + ")" + reasons);
        } catch (Throwable throwable) {
            Logger.printException(() -> "Could not filter the Friends feed", throwable);
        }
    }

    /** Why a Friends tab entry is dropped, or null to keep it. */
    private static String friendsFeedReason(Object entry, List<IFilter> activeFilters, boolean hideLive) {
        if (entry == null) return null;
        if (hideLive && Reflect.readField(entry, "roomStruct") != null) return "LiveFilter";

        Object aweme = Reflect.readField(entry, "aweme");
        if (!(aweme instanceof Aweme)) return null;
        return getFilterReason(activeFilters, (Aweme) aweme);
    }

    public static List filterLateInsertedAds(String source, List items) {
        String insertionSource = source == null ? "unknown" : source;
        return filterAdOnlyAwemeList("FeedInsertion:" + insertionSource, items);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List filterAdOnlyAwemeList(String source, List items) {
        // Counted before the enablement check and before the empty check. A route that ran
        // with the ad filter off still proves the hook is alive, which is the half every ad
        // report so far has been missing, and a route handed nothing proves the page asked
        // and got nothing back: issue #4's export had no profile line at all, and until now
        // that read the same as a hook that never ran.
        FeedFilterCounters.sawList(source, items == null ? 0 : items.size());
        if (items == null || items.isEmpty()) return items;
        if (!ADS_FILTER.getEnabled()) return items;

        boolean verbose = BaseSettings.DEBUG.get();
        ArrayList kept = null;
        int removed = 0;
        int notVideos = 0;
        String lastReason = null;
        for (int index = 0; index < items.size(); index++) {
            Object container = items.get(index);
            Aweme item = container instanceof Aweme ? (Aweme) container : null;
            if (item == null) {
                notVideos++;
                nameNotVideo(source, container);
            }
            String reason = item == null ? null : getFilterReason(AD_ONLY_FILTERS, item);
            if (reason == null) {
                if (kept != null) kept.add(container);
                if (item != null) logKeptItem(source, item, verbose);
                continue;
            }

            if (kept == null) {
                kept = new ArrayList(items.size());
                kept.addAll(items.subList(0, index));
            }
            removed++;
            lastReason = reason;
            logItem(item, reason, verbose);
        }

        FeedFilterCounters.removed(source, removed, lastReason);
        FeedFilterCounters.unreadable(source, notVideos);
        if (kept == null) return items;
        if (verbose && shouldLogBatch()) {
            int initialSize = items.size();
            int resultSize = kept.size();
            int removedFinal = removed;
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(" + source + "): size "
                + initialSize + " -> " + resultSize + " (removed=" + removedFinal + ")");
        }
        return kept;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List filterInsertedFeedItems(
        BaseListFragmentPanel panel,
        int insertionPosition,
        String source,
        List items
    ) {
        FeedFilterCounters.sawList(FINAL_INSERT_SOURCE + source, items == null ? 0 : items.size());
        if (items == null || items.isEmpty()) return items;
        if (panel == null || !"homepage_hot".equals(panel.getEventType())) return items;

        List<IFilter> activeContentFilters = getActiveFilters(CONTENT_FILTERS);
        List<IFilter> activeRangeFilters = getActiveFilters(RANGE_FILTERS);
        if (activeContentFilters.isEmpty() && activeRangeFilters.isEmpty()) return items;

        boolean cacheInsertion = "golden_house".equals(source)
            || "middle_insert_when_video_lagging".equals(source);
        ArrayList kept = null;
        int removed = 0;
        String lastReason = null;

        for (int index = 0; index < items.size(); index++) {
            Object container = items.get(index);
            if (!(container instanceof Aweme)) {
                if (kept != null) kept.add(container);
                continue;
            }

            Aweme item = (Aweme) container;
            int cacheSourceType = AwemeBizExtKt.getCacheSourceType(item);
            if (!cacheInsertion && !isKnownFeedCacheSource(cacheSourceType)) {
                if (kept != null) kept.add(container);
                continue;
            }
            if (cacheSourceType == CACHE_SOURCE_OFFLINE_MODE &&
                    !Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.get()) {
                if (kept != null) kept.add(container);
                continue;
            }

            String reason = getFilterReason(activeContentFilters, item);
            if (reason == null) reason = getFilterReason(activeRangeFilters, item);
            if (reason == null) {
                if (kept != null) kept.add(container);
                continue;
            }

            if (kept == null) {
                kept = new ArrayList(items.size());
                kept.addAll(items.subList(0, index));
            }
            removed++;
            lastReason = reason;
            logItem(item, reason, BaseSettings.DEBUG.get());
        }

        FeedFilterCounters.removed(FINAL_INSERT_SOURCE + source, removed, lastReason);
        if (kept == null) return items;
        if (BaseSettings.DEBUG.get()) {
            int removedCount = removed;
            int keptCount = kept.size();
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] Final insert source=" + source
                + " removed=" + removedCount + " kept=" + keptCount);
        }
        return kept;
    }

    public static FeedItemList filterCachedFeedList(FeedItemList feedItemList) {
        if (feedItemList == null || feedItemList.items == null) return null;
        filterCachedFeedItems("FeedItemList:cold-cache", feedItemList);
        return feedItemList.items.isEmpty() ? null : feedItemList;
    }

    public static FeedItemList filterOfflineFeedList(FeedItemList feedItemList) {
        if (feedItemList == null || feedItemList.items == null) return null;
        if (!Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.get()) return feedItemList;
        filterCachedFeedItems("FeedItemList:offline-fallback", feedItemList);
        return feedItemList.items.isEmpty() ? null : feedItemList;
    }

    public static boolean shouldKeepCachedAweme(Aweme item) {
        if (item == null) return true;

        int cacheSourceType = AwemeBizExtKt.getCacheSourceType(item);
        if (cacheSourceType == CACHE_SOURCE_OFFLINE_MODE &&
                !Settings.FILTER_OFFLINE_FALLBACK_VIDEOS.get()) {
            return true;
        }

        List<IFilter> activeContentFilters = getActiveFilters(CONTENT_FILTERS);
        List<IFilter> activeRangeFilters = getActiveFilters(RANGE_FILTERS);
        String reason = getFilterReason(activeContentFilters, item);
        if (reason == null) reason = getFilterReason(activeRangeFilters, item);
        if (reason == null) return true;

        logItem(item, reason, BaseSettings.DEBUG.get());
        if (BaseSettings.DEBUG.get()) {
            String rejectionReason = reason;
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] Cached item aid="
                + item.getAid() + " sourceType=" + cacheSourceType
                + " rejected by " + rejectionReason);
        }
        return false;
    }

    private static void filterCachedFeedItems(String source, FeedItemList feedItemList) {
        boolean verbose = BaseSettings.DEBUG.get();
        filterFeedList(
            source,
            feedItemList,
            feedItemList.items,
            container -> (container instanceof Aweme) ? (Aweme) container : null,
            verbose,
            false,
            FilterPhase.RESPONSE
        );
    }

    private static boolean isKnownFeedCacheSource(int cacheSourceType) {
        switch (cacheSourceType) {
            case CACHE_SOURCE_COLD_CACHE:
            case CACHE_SOURCE_FEED_UNCONSUMED:
            case CACHE_SOURCE_GOLDEN_HOUSE:
            case CACHE_SOURCE_OFFLINE_MODE:
            case CACHE_SOURCE_MERGE_CACHE:
                return true;
            default:
                return false;
        }
    }

    private static void filterFollowFeedList(
        FollowFeedList followFeedList,
        boolean allowRecentSkip,
        FilterPhase phase
    ) {
        boolean verbose = BaseSettings.DEBUG.get();

        List followItems = followItems(followFeedList);
        if (followItems == null) {
            if (verbose) {
                logNullItems("FollowFeedList", followFeedListNullItemsLogCount);
            }
            return;
        }

        if (verbose && shouldLogBatch()) {
            debugLogBatch(
                "FollowFeedList",
                followItems,
                "phase=" + phase
                    + " feedType=" + followFeedList.feedType
                    + " hasMore=" + followFeedList.hasMore
                    + " cursor=" + followFeedList.cursor
                    + " requestId=" + (followFeedList.requestId == null ? "missing" : "present")
            );
        }

        filterFeedList(
            phase == FilterPhase.RESPONSE ? "FollowFeedList:response" : "FollowFeedList:late",
            followFeedList,
            followItems,
            container -> (container instanceof FollowFeed) ? ((FollowFeed) container).aweme : null,
            verbose,
            allowRecentSkip,
            phase
        );
    }

    private static void filterFeedList(
        String source,
        Object owner,
        List list,
        AwemeExtractor extractor,
        boolean verbose,
        boolean allowRecentSkip,
        FilterPhase phase
    ) {
        if (list == null) return;
        FeedFilterCounters.sawList(source, list.size());

        List<IFilter> activeContentFilters = getActiveFilters(
            phase == FilterPhase.RESPONSE ? CONTENT_FILTERS : LATE_FOLLOW_FILTERS
        );
        List<IFilter> activeRangeFilters = phase == FilterPhase.RESPONSE
            ? getActiveFilters(RANGE_FILTERS)
            : List.of();
        if (activeContentFilters.isEmpty() && activeRangeFilters.isEmpty()) return;

        String filterMask = getFilterMask(activeContentFilters, activeRangeFilters);
        ListFingerprint beforeFingerprint = ListFingerprint.from(list, extractor);
        boolean probeEnabled = verbose && FILTER_CALL_PROBE_ENABLED;
        int callId = probeEnabled ? filterCallProbeCount.incrementAndGet() : 0;
        long startNs = probeEnabled ? System.nanoTime() : 0;
        int ownerId = probeEnabled ? System.identityHashCode(owner) : 0;
        int listId = System.identityHashCode(list);
        String beforeSample = probeEnabled ? sampleAids(list, extractor) : "";
        int initialSize = list.size();
        if (probeEnabled) {
            recordProbeCall(listId, filterMask);
        }

        if (allowRecentSkip && shouldSkipRecentlyProcessedList(
            listId,
            beforeFingerprint,
            filterMask,
            callId,
            source,
            probeEnabled
        )) {
            return;
        }

        int contentRemoved = 0;
        int rangeRejected = 0;
        Map<String, Integer> reasonCounts = new HashMap<>();

        List snapshot = new ArrayList(list);
        List rangeKept = new ArrayList(snapshot.size());
        Object qualityFallback = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (Object container : snapshot) {
            Aweme item = extractor.extract(container);
            if (item == null) {
                rangeKept.add(container);
                continue;
            }

            String contentReason = getFilterReason(activeContentFilters, item);
            if (contentReason != null) {
                if (contentReason.equals("QualityFilter") && getFilterReason(activeRangeFilters, item) == null) {
                    double distance = AdvancedFeedRules.QualityFilter.distance(item);
                    if (distance < closestDistance) {
                        qualityFallback = container;
                        closestDistance = distance;
                    }
                }
                contentRemoved++;
                incrementReason(reasonCounts, contentReason);
                logItem(item, contentReason, verbose);
                continue;
            }

            String rangeReason = getFilterReason(activeRangeFilters, item);
            if (rangeReason != null) {
                rangeRejected++;
                incrementReason(reasonCounts, rangeReason);
                logItem(item, rangeReason, verbose);
                continue;
            }

            rangeKept.add(container);
        }

        // Never restore ads, blocked creators/words, seen videos, or other hard rejects.
        if (rangeKept.isEmpty() && qualityFallback != null) rangeKept.add(qualityFallback);
        List kept = rangeKept;
        int removed = initialSize - kept.size();

        // A batch filtered down to nothing is legitimate. Hide livestreams over a LIVE-only
        // page really does leave zero videos, and putting one back would be the switch not
        // working. What it is not is invisible: from the outside it looks like the feed has
        // stopped, and upstream reported exactly that after a livestream was hidden. Counted
        // so an export can say whether a stalled feed was ever handed anything to show.
        if (initialSize > 0 && kept.isEmpty()) {
            FeedFilterCounters.emptied(source);
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] " + source
                + " kept nothing out of " + initialSize + "; the feed has nothing to advance to"
                + " until TikTok asks for another batch");
        }

        List resultList = list;
        if (removed > 0) {
            try {
                list.clear();
                list.addAll(kept);
            } catch (RuntimeException exception) {
                resultList = replaceOwnerList(owner, kept);
                if (listReplacementLogCount.getAndIncrement() < 3) {
                    Logger.printException(
                        () -> "[Morphe TikTok FeedFilter] Replaced a non-mutable " + source + " list",
                        exception
                    );
                }
            }
        }

        if (probeEnabled) {
            logFilterCallProbe(
                callId,
                source,
                ownerId,
                listId,
                initialSize,
                resultList.size(),
                removed,
                rangeRejected,
                filterMask,
                beforeSample,
                sampleAids(resultList, extractor),
                reasonCounts,
                System.nanoTime() - startNs
            );
        }

        if (probeEnabled) {
            recordProbeScan(listId, removed, System.nanoTime() - startNs);
        }

        FeedFilterCounters.removed(source, removed,
            reasonCounts.isEmpty() ? null : reasonCounts.keySet().iterator().next());
        FeedFilterFeedback.onBatchResult(source, initialSize, resultList.size(), reasonCounts,
                System.currentTimeMillis());
        rememberProcessedList(listId, ListFingerprint.from(resultList, extractor), filterMask);

        if (verbose && removed > 0 && shouldLogBatch()) {
            int removedFinal = removed;
            int contentRemovedFinal = contentRemoved;
            int rangeRejectedFinal = rangeRejected;
            int resultSize = resultList.size();
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(" + source + "): size "
                + initialSize + " -> " + resultSize
                + " (removed=" + removedFinal
                + ", contentRemoved=" + contentRemovedFinal
                + ", rangeRejected=" + rangeRejectedFinal + ")");
        }
    }

    private static List replaceOwnerList(Object owner, List kept) {
        List replacement = new ArrayList(kept);
        if (owner instanceof FeedItemList) {
            ((FeedItemList) owner).items = replacement;
            return replacement;
        }
        if (owner instanceof FollowFeedList) {
            Field field = followItemsField((FollowFeedList) owner);
            if (field == null) {
                throw new IllegalStateException("FollowFeedList has no items field on this build");
            }
            try {
                field.set(owner, replacement);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("Could not replace the Following feed items", ex);
            }
            return replacement;
        }
        throw new IllegalStateException("Unsupported feed list owner: " + owner.getClass().getName());
    }

    private static void incrementReason(Map<String, Integer> reasonCounts, String reason) {
        if (reasonCounts == null) return;
        Integer count = reasonCounts.get(reason);
        reasonCounts.put(reason, count == null ? 1 : count + 1);
    }

    private static List<IFilter> getActiveFilters(List<IFilter> filters) {
        List<IFilter> activeFilters = new ArrayList<>(filters.size());
        for (IFilter filter : filters) {
            if (filter.getEnabled()) {
                activeFilters.add(filter);
            }
        }
        return activeFilters;
    }

    private static String getFilterReason(List<IFilter> activeFilters, Aweme item) {
        // Read once per item, and only once a filter it could answer for has matched.
        Boolean excepted = null;
        for (IFilter filter : activeFilters) {
            try {
                if (filter.getFiltered(item)) {
                    if (CreatorExceptions.isSubjective(filter)) {
                        if (excepted == null) excepted = CreatorExceptions.excepted(item);
                        if (excepted) {
                            if (BaseSettings.DEBUG.get()) {
                                Logger.printInfo(() -> "[Morphe TikTok FeedFilter] "
                                    + filter.getClass().getSimpleName() + " matched aid="
                                    + item.getAid() + " but its creator is excepted");
                            }
                            continue;
                        }
                    }
                    return filter.getClass().getSimpleName();
                }
            } catch (RuntimeException exception) {
                int count = filterExceptionLogCount.getAndIncrement();
                if (count < 3) {
                    Logger.printException(
                        () -> "[Morphe TikTok FeedFilter] " + filter.getClass().getSimpleName()
                            + " failed for aid=" + item.getAid() + "; keeping the item",
                        exception
                    );
                }
            }
        }
        return null;
    }

    private static void logNullItems(String source, AtomicInteger counter) {
        int count = counter.getAndIncrement();
        if (count < MAX_NULL_ITEMS_LOGS) {
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(" + source + "): items=null");
        } else if (count == MAX_NULL_ITEMS_LOGS) {
            Logger.printInfo(() -> "[Morphe TikTok FeedFilter] filter(" + source + "): items=null (further logs suppressed)");
        }
    }

    private static void debugLogBatch(String source, List list, String metadata) {
        int size = list == null ? -1 : list.size();
        Logger.printInfo(() ->
            "[Morphe TikTok FeedFilter] filter(" + source + "): size=" + size
                + " " + metadata
                + " remove_ads=" + Settings.REMOVE_ADS.get()
                + " hide_shop=" + Settings.HIDE_SHOP.get()
                + " hide_live=" + Settings.HIDE_LIVE.get()
                + " hide_story=" + Settings.HIDE_STORY.get()
                + " hide_image=" + Settings.HIDE_IMAGE.get()
                + " min_max_views=\"" + Settings.MIN_MAX_VIEWS.get() + "\""
                + " min_max_likes=\"" + Settings.MIN_MAX_LIKES.get() + "\""
        );
    }

    /**
     * Says, once per class, that a route was handed something that is not a video. On 46.2.3
     * every profile list is a List of Aweme, so this line is what a build that changes that
     * would leave in the report, in place of a filter that silently keeps everything.
     */
    private static void nameNotVideo(String source, Object container) {
        String name = container == null ? "null" : container.getClass().getName();
        if (NOT_VIDEO_CLASSES.size() >= MAX_NOT_VIDEO_CLASSES || !NOT_VIDEO_CLASSES.add(name)) return;
        Logger.printInfo(() -> "[Morphe TikTok FeedFilter] " + source + " was handed " + name
                + ", which is not a video, so no rule looked at it");
    }

    /**
     * With logging on, what a profile or insertion route kept, one line per item. The removed
     * items were always logged; the kept ones never were, so an export from a phone showing
     * an ad in a profile pager carried the counts and nothing about the ad itself.
     */
    private static void logKeptItem(String source, Aweme item, boolean verbose) {
        if (!verbose || keptItemLogCount.getAndIncrement() >= MAX_KEPT_ITEM_LOGS) return;
        Logger.printInfo(() -> "[Morphe TikTok FeedFilter] " + source + " kept " + describeItem(item));
    }

    /**
     * The ad-relevant shape of an item, which is what a kept ad has to be judged by. Each
     * field is read on its own: a getter the host has renamed, or one a test fixture leaves
     * out, costs that field and not the line.
     */
    private static String describeItem(Aweme item) {
        return "aid=" + aidOf(item)
                + " ad=" + flag(item, 0)
                + " softAd=" + flag(item, 1)
                + " rawAd=" + flag(item, 2)
                + " promo=" + flag(item, 3)
                + " commission=" + flag(item, 4)
                + " playCount=" + playCountOf(item);
    }

    private static String aidOf(Aweme item) {
        try {
            return String.valueOf(item.getAid());
        } catch (Throwable failure) {
            return "?";
        }
    }

    private static String flag(Aweme item, int which) {
        try {
            switch (which) {
                case 0: return String.valueOf(item.isAd());
                case 1: return String.valueOf(item.isSoftAd());
                case 2: return String.valueOf(item.getAwemeRawAd() != null);
                case 3: return String.valueOf(item.isWithPromotionalMusic());
                default: return String.valueOf(AdsFilter.hasCreatorCommissionDisclosure(item));
            }
        } catch (Throwable failure) {
            return "?";
        }
    }

    private static String playCountOf(Aweme item) {
        try {
            AwemeStatistics statistics = item.getStatistics();
            return statistics == null ? "-1" : String.valueOf(statistics.getPlayCount());
        } catch (Throwable failure) {
            return "?";
        }
    }

    private static void logItem(Aweme item, String reason, boolean verbose) {
        if (!verbose || reason == null || !shouldLogItem()) return;

        Logger.printInfo(() -> {
            // Inside the message, where the Logger's guard covers a host getter that throws.
            // Read outside it, one such getter threw out of the filter and into TikTok.
            String finalShareUrl = item.getShareUrl();
            if (finalShareUrl != null && finalShareUrl.length() > 140) {
                finalShareUrl = finalShareUrl.substring(0, 140) + "...";
            }
            long playCount = -1;
            long likeCount = -1;

            AwemeStatistics statistics = item.getStatistics();
            if (statistics != null) {
                playCount = statistics.getPlayCount();
                likeCount = statistics.getDiggCount();
            }

            var imageInfos = item.getImageInfos();
            boolean isImage = imageInfos != null && !imageInfos.isEmpty();
            boolean isPhotoMode = item.getPhotoModeImageInfo() != null || item.getPhotoModeTextInfo() != null;

            return "[Morphe TikTok FeedFilter] item"
                + " aid=" + item.getAid()
                + " ad=" + item.isAd()
                + " softAd=" + item.isSoftAd()
                + " rawAd=" + (item.getAwemeRawAd() != null)
                + " promo=" + item.isWithPromotionalMusic()
                + " commission=" + AdsFilter.hasCreatorCommissionDisclosure(item)
                + " liveEvidence=" + LiveFilter.getLiveEvidence(item)
                + " story=" + item.getIsTikTokStory()
                + " image=" + isImage
                + " photoMode=" + isPhotoMode
                + " playCount=" + playCount
                + " likeCount=" + likeCount
                + " shareUrl=" + (finalShareUrl == null ? "null" : "\"" + finalShareUrl + "\"")
                + " => " + (reason == null ? "KEEP" : "FILTER(" + reason + ")");
        });
    }

    private static boolean shouldLogBatch() {
        return batchLogCount.getAndIncrement() < MAX_BATCH_LOGS;
    }

    private static boolean shouldLogItem() {
        return itemLogCount.getAndIncrement() < MAX_ITEM_LOGS;
    }

    private static boolean shouldSkipRecentlyProcessedList(
        int listId,
        ListFingerprint fingerprint,
        String filterMask,
        int callId,
        String source,
        boolean probeEnabled
    ) {
        ProcessedListState state;
        long now = System.currentTimeMillis();
        String missReason = null;
        int skipCount = 0;

        synchronized (processedListCache) {
            state = processedListCache.get(listId);
            if (state == null) {
                missReason = "newList";
            } else if (!state.filterMask.equals(filterMask)) {
                missReason = "filterMask";
            } else if (state.fingerprint.size != fingerprint.size) {
                missReason = "size";
            } else if (!state.fingerprint.matches(fingerprint)) {
                missReason = "sample";
            } else if (now - state.processedAtMs > PROCESSED_LIST_CACHE_TTL_MS) {
                missReason = "expired";
            } else {
                state.skipCount++;
                skipCount = state.skipCount;
            }
        }

        if (missReason != null) {
            if (probeEnabled) {
                recordProbeCacheMiss(missReason);
            }
            return false;
        }

        if (probeEnabled) {
            recordProbeCacheHit(listId);
        }

        if (probeEnabled && shouldLogCacheSkip(skipCount)) {
            int skipCountFinal = skipCount;
            Logger.printInfo(() -> "[Morphe TikTok FeedFilterProbe]"
                + " call=" + callId
                + " source=" + source
                + " list=" + listId
                + " cacheHit=true"
                + " skipCount=" + skipCountFinal
                + " size=" + fingerprint.size
                + " filters=\"" + filterMask + "\""
                + " sample=\"" + fingerprint.toSampleString() + "\"");
        }

        return true;
    }

    private static boolean shouldLogCacheSkip(int skipCount) {
        return skipCount <= 20 || skipCount % 100 == 0;
    }

    private static void rememberProcessedList(int listId, ListFingerprint fingerprint, String filterMask) {
        synchronized (processedListCache) {
            if (processedListCache.size() > PROCESSED_LIST_CACHE_MAX_SEEN_LISTS) {
                processedListCache.clear();
            }

            processedListCache.put(listId, new ProcessedListState(fingerprint, filterMask, System.currentTimeMillis()));
        }
    }

    private static void recordProbeCall(int listId, String filterMask) {
        if (!FILTER_CALL_PROBE_SUMMARY_ENABLED) return;

        synchronized (filterCallProbeSummaryLock) {
            filterCallProbeSummary.calls++;
            filterCallProbeSummary.uniqueListIds.add(listId);
            filterCallProbeSummary.lastFilterMask = filterMask;
        }
    }

    private static void recordProbeCacheHit(int listId) {
        if (!FILTER_CALL_PROBE_SUMMARY_ENABLED) return;

        String summary = null;
        synchronized (filterCallProbeSummaryLock) {
            filterCallProbeSummary.cacheHits++;
            filterCallProbeSummary.uniqueListIds.add(listId);
            summary = rotateProbeSummaryIfReadyLocked(System.currentTimeMillis());
        }
        logProbeSummary(summary);
    }

    private static void recordProbeCacheMiss(String reason) {
        if (!FILTER_CALL_PROBE_SUMMARY_ENABLED) return;

        synchronized (filterCallProbeSummaryLock) {
            if ("newList".equals(reason)) {
                filterCallProbeSummary.missNewList++;
            } else if ("filterMask".equals(reason)) {
                filterCallProbeSummary.missFilterMask++;
            } else if ("size".equals(reason)) {
                filterCallProbeSummary.missSize++;
            } else if ("sample".equals(reason)) {
                filterCallProbeSummary.missSample++;
            } else if ("expired".equals(reason)) {
                filterCallProbeSummary.missExpired++;
            } else {
                filterCallProbeSummary.missOther++;
            }
        }
    }

    private static void recordProbeScan(int listId, int removed, long elapsedNs) {
        if (!FILTER_CALL_PROBE_SUMMARY_ENABLED) return;

        String summary = null;
        long elapsedMs = elapsedNs / 1_000_000L;
        synchronized (filterCallProbeSummaryLock) {
            filterCallProbeSummary.scans++;
            filterCallProbeSummary.removed += removed;
            filterCallProbeSummary.scanElapsedMs += elapsedMs;
            filterCallProbeSummary.maxScanMs = Math.max(filterCallProbeSummary.maxScanMs, elapsedMs);
            if (elapsedMs >= FILTER_CALL_PROBE_SLOW_MS) {
                filterCallProbeSummary.slowScans++;
            }
            filterCallProbeSummary.uniqueListIds.add(listId);
            summary = rotateProbeSummaryIfReadyLocked(System.currentTimeMillis());
        }
        logProbeSummary(summary);
    }

    private static String rotateProbeSummaryIfReadyLocked(long nowMs) {
        long windowMs = nowMs - filterCallProbeSummary.startedAtMs;
        if (windowMs < FILTER_CALL_PROBE_SUMMARY_WINDOW_MS || filterCallProbeSummary.calls == 0) {
            return null;
        }

        String summary = filterCallProbeSummary.toLogMessage(windowMs);
        filterCallProbeSummary = new ProbeSummary(nowMs);
        return summary;
    }

    private static void logProbeSummary(String summary) {
        if (summary == null) return;
        Logger.printInfo(() -> summary);
    }

    private static String getFilterMask(
        List<IFilter> activeContentFilters,
        List<IFilter> activeRangeFilters
    ) {
        StringBuilder builder = new StringBuilder();
        appendFilterMask(builder, activeContentFilters);
        appendFilterMask(builder, activeRangeFilters);
        // The exceptions decide what the same filters keep, so a changed list is a changed mask
        // and a page the filter has already seen is read again.
        String exceptions = CreatorExceptions.maskToken();
        if (!exceptions.isEmpty()) {
            if (builder.length() > 0) builder.append('|');
            builder.append(exceptions);
        }
        return builder.toString();
    }

    /** Every filter a feed response runs through, content then ranges, for the classification test. */
    static List<IFilter> allFiltersForTests() {
        List<IFilter> all = new ArrayList<>(CONTENT_FILTERS);
        all.addAll(RANGE_FILTERS);
        return all;
    }

    private static void appendFilterMask(StringBuilder builder, List<IFilter> activeFilters) {
        for (IFilter filter : activeFilters) {
            if (builder.length() > 0) builder.append('|');
            builder.append(filter.getClass().getSimpleName());
        }
    }

    private static String sampleAids(List list, AwemeExtractor extractor) {
        List snapshot = new ArrayList(list);
        StringBuilder builder = new StringBuilder();
        int sampled = 0;
        for (Object container : snapshot) {
            if (sampled >= FILTER_CALL_PROBE_AID_SAMPLE_SIZE) {
                break;
            }

            Aweme item = extractor.extract(container);
            if (item == null) {
                continue;
            }

            if (builder.length() > 0) builder.append(',');
            builder.append(item.getAid());
            sampled++;
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static void logFilterCallProbe(
        int callId,
        String source,
        int ownerId,
        int listId,
        int beforeSize,
        int afterSize,
        int removed,
        int rangeRejected,
        String filterMask,
        String beforeSample,
        String afterSample,
        Map<String, Integer> reasonCounts,
        long elapsedNs
    ) {
        long elapsedMs = elapsedNs / 1_000_000L;
        ProbeSeenList seen = updateSeenList(listId, beforeSample, afterSample, beforeSize, afterSize);
        boolean interesting = seen.seenCount > 1
            || removed > 0
            || elapsedMs >= FILTER_CALL_PROBE_SLOW_MS;

        if (!interesting) return;

        String counts = reasonCounts == null || reasonCounts.isEmpty() ? "none" : reasonCounts.toString();
        String stack = FILTER_CALL_PROBE_STACKS ? " stack=" + getProbeStack() : "";

        Logger.printInfo(() -> "[Morphe TikTok FeedFilterProbe]"
            + " call=" + callId
            + " source=" + source
            + " owner=" + ownerId
            + " list=" + listId
            + " seen=" + seen.seenCount
            + " previousBefore=\"" + seen.previousBeforeSample + "\""
            + " sameBefore=" + beforeSample.equals(seen.previousBeforeSample)
            + " size=" + beforeSize + "->" + afterSize
            + " removed=" + removed
            + " rangeRejected=" + rangeRejected
            + " reasons=" + counts
            + " filters=\"" + filterMask + "\""
            + " before=\"" + beforeSample + "\""
            + " after=\"" + afterSample + "\""
            + " elapsedMs=" + elapsedMs
            + stack);
    }

    private static ProbeSeenList updateSeenList(
        int listId,
        String beforeSample,
        String afterSample,
        int beforeSize,
        int afterSize
    ) {
        synchronized (filterCallProbeSeenLists) {
            if (filterCallProbeSeenLists.size() > FILTER_CALL_PROBE_MAX_SEEN_LISTS) {
                filterCallProbeSeenLists.clear();
            }

            ProbeSeenList seen = filterCallProbeSeenLists.get(listId);
            if (seen == null) {
                seen = new ProbeSeenList();
                filterCallProbeSeenLists.put(listId, seen);
            }

            String previousBeforeSample = seen.lastBeforeSample;
            seen.seenCount++;
            seen.previousBeforeSample = previousBeforeSample == null ? "none" : previousBeforeSample;
            seen.lastBeforeSample = beforeSample;
            seen.lastAfterSample = afterSample;
            seen.lastBeforeSize = beforeSize;
            seen.lastAfterSize = afterSize;
            return seen;
        }
    }

    private static String getProbeStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.startsWith("app.morphe.extension.tiktok.feedfilter.")
                || className.startsWith("java.lang.Thread")) {
                continue;
            }

            if (builder.length() > 0) builder.append(" <- ");
            builder.append(className).append('#').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++added >= 4) break;
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private enum FilterPhase {
        RESPONSE,
        LATE_FOLLOW
    }

    @FunctionalInterface
    interface AwemeExtractor {
        Aweme extract(Object source);
    }

    private static final class ProbeSeenList {
        int seenCount;
        String previousBeforeSample = "none";
        String lastBeforeSample = "none";
        String lastAfterSample = "none";
        int lastBeforeSize;
        int lastAfterSize;
    }

    private static final class ProbeSummary {
        final long startedAtMs;
        final Set<Integer> uniqueListIds = new HashSet<>();
        int calls;
        int scans;
        int cacheHits;
        int slowScans;
        int removed;
        int missNewList;
        int missFilterMask;
        int missSize;
        int missSample;
        int missExpired;
        int missOther;
        long scanElapsedMs;
        long maxScanMs;
        String lastFilterMask = "";

        ProbeSummary(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }

        String toLogMessage(long windowMs) {
            int terminalCalls = scans + cacheHits;
            double hitRate = terminalCalls == 0 ? 0 : (cacheHits * 100.0) / terminalCalls;
            double averageScanMs = scans == 0 ? 0 : scanElapsedMs / (double) scans;

            return "[Morphe TikTok FeedFilterProbeSummary]"
                + " windowMs=" + windowMs
                + " calls=" + calls
                + " scans=" + scans
                + " cacheHits=" + cacheHits
                + " cacheHitRate=" + Math.round(hitRate * 10.0) / 10.0 + "%"
                + " removed=" + removed
                + " slowScans=" + slowScans
                + " avgScanMs=" + Math.round(averageScanMs * 10.0) / 10.0
                + " maxScanMs=" + maxScanMs
                + " uniqueLists=" + uniqueListIds.size()
                + " missNewList=" + missNewList
                + " missFilterMask=" + missFilterMask
                + " missSize=" + missSize
                + " missSample=" + missSample
                + " missExpired=" + missExpired
                + " missOther=" + missOther
                + " filters=\"" + lastFilterMask + "\"";
        }
    }

    private static final class ProcessedListState {
        final ListFingerprint fingerprint;
        final String filterMask;
        final long processedAtMs;
        int skipCount;

        ProcessedListState(ListFingerprint fingerprint, String filterMask, long processedAtMs) {
            this.fingerprint = fingerprint;
            this.filterMask = filterMask;
            this.processedAtMs = processedAtMs;
        }

        boolean matches(ListFingerprint currentFingerprint, String currentFilterMask, long nowMs) {
            return nowMs - processedAtMs <= PROCESSED_LIST_CACHE_TTL_MS
                && filterMask.equals(currentFilterMask)
                && fingerprint.matches(currentFingerprint);
        }
    }

    private static final class ListFingerprint {
        final int size;
        final int firstIdentity;
        final int middleIdentity;
        final int lastIdentity;
        final String firstAid;
        final String middleAid;
        final String lastAid;
        final long contentSignature;

        private ListFingerprint(
            int size,
            int firstIdentity,
            int middleIdentity,
            int lastIdentity,
            String firstAid,
            String middleAid,
            String lastAid,
            long contentSignature
        ) {
            this.size = size;
            this.firstIdentity = firstIdentity;
            this.middleIdentity = middleIdentity;
            this.lastIdentity = lastIdentity;
            this.firstAid = firstAid;
            this.middleAid = middleAid;
            this.lastAid = lastAid;
            this.contentSignature = contentSignature;
        }

        static ListFingerprint from(List list, AwemeExtractor extractor) {
            int size = list.size();
            if (size == 0) {
                return new ListFingerprint(0, 0, 0, 0, "", "", "", 0L);
            }

            int middleIndex = size / 2;
            int lastIndex = size - 1;
            Aweme first = extractAt(list, extractor, 0);
            Aweme middle = extractAt(list, extractor, middleIndex);
            Aweme last = extractAt(list, extractor, lastIndex);
            long contentSignature = 1125899906842597L;
            for (int index = 0; index < size; index++) {
                Aweme item = extractAt(list, extractor, index);
                contentSignature = 31L * contentSignature + identity(item);
                contentSignature = 31L * contentSignature + aid(item).hashCode();
                contentSignature = 31L * contentSignature + AdsFilter.evidenceFingerprint(item);
                // A cached Aweme can receive its anchors after the first delivery. The UI-only
                // hide option must never erase the evidence used by this independent filter.
                if (Settings.FILTER_LOCATION_VIDEOS.get())
                    contentSignature = 31L * contentSignature + (LocationBadgeFilter.hasBadge(item) ? 1 : 0);
            }

            return new ListFingerprint(
                size,
                identity(first),
                identity(middle),
                identity(last),
                aid(first),
                aid(middle),
                aid(last),
                contentSignature
            );
        }

        private static Aweme extractAt(List list, AwemeExtractor extractor, int index) {
            try {
                return extractor.extract(list.get(index));
            } catch (RuntimeException ex) {
                return null;
            }
        }

        boolean matches(ListFingerprint other) {
            return size == other.size
                && firstIdentity == other.firstIdentity
                && middleIdentity == other.middleIdentity
                && lastIdentity == other.lastIdentity
                && firstAid.equals(other.firstAid)
                && middleAid.equals(other.middleAid)
                && lastAid.equals(other.lastAid)
                && contentSignature == other.contentSignature;
        }

        String toSampleString() {
            return firstAid + "|" + middleAid + "|" + lastAid;
        }

        private static int identity(Aweme item) {
            return item == null ? 0 : System.identityHashCode(item);
        }

        private static String aid(Aweme item) {
            if (item == null) return "";
            String aid = item.getAid();
            return aid == null ? "" : aid;
        }
    }
}
