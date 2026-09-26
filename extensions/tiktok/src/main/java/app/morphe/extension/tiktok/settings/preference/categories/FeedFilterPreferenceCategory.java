/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/FeedFilterPreferenceCategory.java
 * Mirror, since GitHub blocks the original: https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/FeedFilterPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import java.text.NumberFormat;

import app.morphe.extension.tiktok.seen.SeenVideoHistory;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.RangeValuePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.ClearSeenVideoHistoryPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.CreatorListPreference;
import app.morphe.extension.tiktok.settings.preference.CalmFeedPresetPreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.feedfilter.AdvancedFeedRules;
import app.morphe.extension.tiktok.feedfilter.CreatorExceptions;

/**
 * What reaches the feed, in the order a reader asks: which kinds of post, how much of them,
 * from whom and with which sounds, which words and countries, what was already seen, and the
 * one advanced switch. The player's four buttons live here too, since blocking a creator or a
 * sound from a video is the same choice as listing them.
 */
@SuppressWarnings("deprecation")
public class FeedFilterPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedFilterPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feed filter");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.feedFilterEnabled
                || SettingsStatus.seenVideoFilterEnabled
                || SettingsStatus.blockAuthorEnabled
                || SettingsStatus.notInterestedEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        // Each patch's rows go behind its own flag. The page is reachable when any of them
        // is in the bundle, and a page reachable because of one of them must not offer the
        // others' settings, which would sit there doing nothing.
        if (SettingsStatus.feedFilterEnabled) addQuickSetup(context);
        if (SettingsStatus.feedFilterEnabled) addAds(context);
        if (SettingsStatus.feedFilterEnabled) addKindsOfPost(context);
        if (SettingsStatus.feedFilterEnabled) addLimits(context);
        addCreatorsAndSounds(context);
        if (SettingsStatus.feedFilterEnabled) addWordsAndCountries(context);
        if (SettingsStatus.seenVideoFilterEnabled) addSeenVideoRules(context);
        if (SettingsStatus.feedFilterEnabled) addAdvanced(context);
    }

    private void addQuickSetup(Context context) {
        addPreference(new SectionHeadingPreference(context, "Quick setup"));
        addPreference(new CalmFeedPresetPreference(context));
    }

    private void addKindsOfPost(Context context) {
        addPreference(new SectionHeadingPreference(context, "Kinds of post"));
        addPreference(new TogglePreference(
                context,
                "Hide TikTok Shop", "Hide TikTok Shop posts from the feed.",
                Settings.HIDE_SHOP
        ));
        addPreference(new TogglePreference(
                context,
                "Hide TikTok Shop in search", "Hide the Products block and product cards in search results.",
                Settings.HIDE_SEARCH_SHOP
        ));
        addPreference(new TogglePreference(
                context,
                "Hide LIVE videos", "Hide LIVE videos from the feed.",
                Settings.HIDE_LIVE
        ));
        addPreference(new TogglePreference(context, "Hide LIVE replays", "Hide recorded LIVE broadcasts from the feed.", Settings.HIDE_LIVE_REPLAYS));
        addPreference(new TogglePreference(
                context,
                "Hide stories", "Hide stories from the feed.",
                Settings.HIDE_STORY
        ));
        addPreference(new TogglePreference(
                context,
                "Hide photo posts", "Hide photo posts from the feed.",
                Settings.HIDE_IMAGE
        ));
        addPreference(new TogglePreference(
                context,
                "Hide AI-generated videos",
                "Hide videos carrying TikTok's AI-generated label.",
                Settings.HIDE_AI_GENERATED
        ));
        addPreference(new TogglePreference(
                context,
                "Hide verified accounts",
                "Hide videos posted by verified accounts.",
                Settings.HIDE_VERIFIED
        ));
        addPreference(new TogglePreference(
                context,
                "Hide Series",
                "Hide videos that belong to a paid Series.",
                Settings.HIDE_SERIES
        ));
        addPreference(new TogglePreference(
                context,
                "Hide mini dramas",
                "Hide TikTok's short drama episodes and the cards that promote them.",
                Settings.HIDE_MINI_DRAMAS
        ));
        addPreference(new TogglePreference(
                context,
                "Hide playlist videos",
                "Hide videos posted as part of a playlist.",
                Settings.HIDE_PLAYLIST_VIDEOS
        ));
    }

    private void addAds(Context context) {
        addPreference(new SectionHeadingPreference(context, "Ads"));
        addPreference(new TogglePreference(
                context,
                "Remove feed ads",
                "Remove feed ads, creator commission posts, paid partnerships, and branded content.",
                Settings.REMOVE_ADS
        ));
        addPreference(new TogglePreference(
                context,
                "Hide paid partnerships",
                "Hide videos marked as paid partnership or branded content.",
                Settings.HIDE_PAID_PARTNERSHIP
        ));
        addPreference(new TogglePreference(context, "Hide promotional music",
                "Hide videos marked as using promotional music.", Settings.HIDE_PROMOTIONAL_MUSIC));
        addPreference(new TogglePreference(context, "Filter location-tagged videos",
                "Hide feed videos with place badges, even when they aren't paid ads. To keep the video and hide only its badge, use Hide location labels in Feed screen.",
                Settings.FILTER_LOCATION_VIDEOS));
    }

    private void addLimits(Context context) {
        addPreference(new SectionHeadingPreference(context, "Limits"));
        addPreference(new NumberInputPreference(context, "Maximum video length", "Seconds. Zero keeps every length. If everything the feed just sent would be filtered out, the video closest to your limit is kept so the feed isn't empty.", Settings.MAX_VIDEO_SECONDS, "%1$s second", "%1$s seconds").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Maximum post age",
                "Days. Zero keeps every age. Posts without a usable timestamp, including future posts, stay visible.",
                Settings.MAX_PUBLICATION_AGE_DAYS, "%1$s day", "%1$s days").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Maximum views per like", "Hide videos with a lot of views and few likes. Lower numbers are stricter, zero turns the rule off, and one video is kept back if everything the feed just sent would go.", Settings.MAX_VIEWS_PER_LIKE, "%1$s view per like", "%1$s views per like").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Maximum views per comment", "Hide videos with a lot of views and few comments. Works the same way as views per like. Zero turns it off.", Settings.MAX_VIEWS_PER_COMMENT, "%1$s view per comment", "%1$s views per comment").zeroMeansOff());
        addPreference(new RangeValuePreference(
                context,
                "Views range", "Show only videos with this many views.",
                Settings.MIN_MAX_VIEWS
        ));
        addPreference(new RangeValuePreference(
                context,
                "Likes range", "Show only videos with this many likes.",
                Settings.MIN_MAX_LIKES
        ));
        addPreference(new RangeValuePreference(
                context,
                "Comments range", "Show only videos with this many comments.",
                Settings.MIN_MAX_COMMENTS
        ));
        addPreference(new RangeValuePreference(
                context,
                "Favorites range", "Show only videos with this many favorites.",
                Settings.MIN_MAX_FAVOURITES
        ));
        addPreference(new RangeValuePreference(
                context,
                "Shares range", "Show only videos with this many shares.",
                Settings.MIN_MAX_SHARES
        ));
    }

    private void addCreatorsAndSounds(Context context) {
        boolean any = SettingsStatus.feedFilterEnabled || SettingsStatus.blockAuthorEnabled
                || SettingsStatus.notInterestedEnabled;
        if (!any) return;
        addPreference(new SectionHeadingPreference(context, "Creators and sounds"));
        if (SettingsStatus.feedFilterEnabled) {
            addPreference(new InputTextPreference(context, "Blocked creators", "Comma separated account handles or user ids. Videos from these accounts are always hidden. An entry between slashes, like /^news_/, is a pattern matched against the handle and the display name.", Settings.BLOCKED_CREATORS)
                    .withCheck(AdvancedFeedRules::creatorEntryProblem));
            addPreference(new CreatorListPreference(context, "Creators hidden on this phone",
                    "Creators you hid from a video. Search the list and remove one at a time.",
                    Settings.LOCAL_HIDDEN_CREATORS));
            addPreference(new InputTextPreference(context, "Creator exceptions",
                    "Comma separated account handles or user ids. Their videos stay when only a "
                            + "filter on the kind of post, its labels, age, length or counts would "
                            + "hide them. Ads, blocked creators, words, sounds and countries, and "
                            + "seen videos still apply.",
                    Settings.CREATOR_FILTER_EXCEPTIONS)
                    .withCheck(CreatorExceptions::entryProblem)
                    .withNote(CreatorExceptions::conflictNote));
        }
        // The player's own buttons. They were the middle of the App page's Player card,
        // three pages away from the lists they add to.
        if (SettingsStatus.blockAuthorEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Show the block button on videos",
                    "Block the creator in one tap. Once TikTok confirms, skip to the next video. "
                            + "A small Unblock button appears at the top left for two seconds. "
                            + "You can also unblock later in TikTok's Privacy > Blocked accounts.",
                    Settings.BLOCK_AUTHOR_BUTTON
            ));
            addPreference(new TogglePreference(
                    context,
                    "Show the hide button on videos",
                    "Add a button that hides the current creator on this phone without blocking them.",
                    Settings.LOCAL_HIDE_BUTTON
            ));
            addPreference(new TogglePreference(
                    context,
                    "Show the block sound button",
                    "Add a separate button that skips videos using the current sound.",
                    Settings.BLOCK_SOUND_BUTTON
            ));
        }
        if (SettingsStatus.notInterestedEnabled) {
            addPreference(new TogglePreference(context, "Show the Not interested button",
                    "Add a button beside the block control to send feedback about the current video.",
                    Settings.NOT_INTERESTED_BUTTON));
        }
        if (SettingsStatus.feedFilterEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide videos with blocked sounds",
                    "Hide videos that use a sound blocked with the player's sound button, or named below.",
                    Settings.HIDE_BLOCKED_SOUNDS
            ));
            addPreference(new InputTextPreference(
                    context,
                    "Blocked sound names",
                    "Comma separated words to match against a sound's name, like saxophone. Case doesn't matter.",
                    Settings.BLOCKED_SOUND_NAMES
            ));
            addPreference(new InputTextPreference(
                    context,
                    "Blocked sound ids",
                    "Comma separated sound ids recorded by the player's sound button. Remove one to unblock it.",
                    Settings.BLOCKED_SOUND_IDS
            ));
        }
    }

    private void addWordsAndCountries(Context context) {
        addPreference(new SectionHeadingPreference(context, "Words and countries"));
        addPreference(new InputTextPreference(context, "Blocked caption words",
                "Comma separated words or phrases. Videos whose caption matches are hidden. Case doesn't matter. Two phrases in quotes can be joined: \"a\" & \"b\" needs both, \"a\" !& \"b\" needs the first without the second.",
                Settings.BLOCKED_CAPTION_WORDS)
                .withCheck(app.morphe.extension.tiktok.feedfilter.KeywordRules::problem));
        addPreference(new InputTextPreference(context, "Only from these countries",
                "Comma separated country codes, like GB, IE. Videos posted from anywhere else are hidden. Leave empty for all countries.",
                Settings.REGION_ONLY_FROM)
                .withCheck(app.morphe.extension.tiktok.feedfilter.RegionFilter::countryProblem));
        addPreference(new InputTextPreference(context, "Never from these countries",
                "Comma separated country codes. Videos posted from these are hidden, whatever the list above says.",
                Settings.REGION_NEVER_FROM)
                .withCheck(app.morphe.extension.tiktok.feedfilter.RegionFilter::countryProblem));
    }

    private void addSeenVideoRules(Context context) {
        addPreference(new SectionHeadingPreference(context, "Seen videos"));
        addPreference(new TogglePreference(
                context,
                "Hide videos you have already seen",
                "Keep a local record of what you have watched and hide those videos when "
                        + "the feed sends them again.",
                Settings.HIDE_SEEN_VIDEOS
        ));
        addPreference(new NumberInputPreference(
                context,
                "Forget seen videos after",
                L10n.f(context, "Days to remember a video. Zero removes the age limit. History keeps at most %1$s videos.",
                        NumberFormat.getInstance().format(SeenVideoHistory.MAX_RECORDS)),
                Settings.SEEN_VIDEO_RETENTION_DAYS, "%1$s day", "%1$s days"
        ).zeroMeansOff());
        addPreference(new ClearSeenVideoHistoryPreference(context));
    }

    private void addAdvanced(Context context) {
        addPreference(new SectionHeadingPreference(context, "Advanced"));
        addPreference(new TogglePreference(
                context,
                "Filter TikTok's offline videos",
                "Also apply these filters to downloaded videos TikTok uses when the feed can't load enough new items.",
                Settings.FILTER_OFFLINE_FALLBACK_VIDEOS
        ));
    }
}
