/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/categories/FeedFilterPreferenceCategory.java
 */

package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.RangeValuePreference;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.ClearSeenVideoHistoryPreference;
import app.morphe.extension.tiktok.settings.preference.NumberInputPreference;
import app.morphe.extension.tiktok.settings.preference.CreatorListPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.feedfilter.AdvancedFeedRules;

@SuppressWarnings("deprecation")
public class FeedFilterPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedFilterPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feed filter");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.feedFilterEnabled
                || SettingsStatus.seenVideoFilterEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        // Each patch's rows go behind its own flag. The page is reachable when either patch
        // is in the bundle, and a page reachable because of one of them must not offer the
        // other's settings, which would sit there doing nothing.
        if (SettingsStatus.feedFilterEnabled) addFeedFilterRules(context);
        if (SettingsStatus.seenVideoFilterEnabled) addSeenVideoRules(context);
    }

    private void addFeedFilterRules(Context context) {
        addPreference(new InputTextPreference(context, "Blocked caption words",
                "Comma separated words or phrases. Matching captions are skipped. Case doesn't matter. Two phrases in quotes can be joined: \"a\" & \"b\" needs both, \"a\" !& \"b\" needs the first without the second.",
                Settings.BLOCKED_CAPTION_WORDS)
                .withCheck(app.morphe.extension.tiktok.feedfilter.KeywordRules::problem));
        addPreference(new InputTextPreference(context, "Only from these countries",
                "Comma separated country codes, like GB, IE. Videos posted from anywhere else are hidden. Leave empty for all countries.",
                Settings.REGION_ONLY_FROM));
        addPreference(new InputTextPreference(context, "Never from these countries",
                "Comma separated country codes. Videos posted from these are hidden, whatever the list above says.",
                Settings.REGION_NEVER_FROM));
        addPreference(new InputTextPreference(context, "Blocked creators", "Comma separated account handles or user ids. These accounts are always skipped. An entry between slashes, like /^news_/, is a pattern matched against the handle and the display name.", Settings.BLOCKED_CREATORS)
                .withCheck(AdvancedFeedRules::creatorEntryProblem));
        addPreference(new CreatorListPreference(context, "Locally hidden creators",
                "Creators hidden from later feed batches by the player action. Search the list and remove one entry at a time.",
                Settings.LOCAL_HIDDEN_CREATORS));
        addPreference(new NumberInputPreference(context, "Maximum video length", "Seconds. Zero keeps every length. If a whole batch would be filtered out, the video closest to your limit is kept so the feed is not empty.", Settings.MAX_VIDEO_SECONDS, "second", "seconds").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Maximum post age",
                "Days. Zero keeps every age. Posts without a usable timestamp, including future posts, stay visible.",
                Settings.MAX_PUBLICATION_AGE_DAYS, "day", "days").zeroMeansOff());
        addPreference(new NumberInputPreference(context, "Maximum views per like", "Hide videos with a lot of views and few likes. Lower numbers are stricter, zero turns the rule off, and one video is kept back if a whole batch would go.", Settings.MAX_VIEWS_PER_LIKE, "view per like", "views per like").zeroMeansOff());
        addPreference(new TogglePreference(context, "Hide promotional music", "Skip videos marked as using promotional music.", Settings.HIDE_PROMOTIONAL_MUSIC));
        addPreference(new TogglePreference(context, "Hide LIVE replays", "Skip recorded LIVE broadcasts in the feed.", Settings.HIDE_LIVE_REPLAYS));
        addPreference(new TogglePreference(
                context,
                "Remove feed ads", "Remove the ads TikTok puts between videos.",
                Settings.REMOVE_ADS
        ));
        addPreference(new TogglePreference(
                context,
                "Hide TikTok Shop", "Hide TikTok Shop posts from the feed.",
                Settings.HIDE_SHOP
        ));
        addPreference(new TogglePreference(
                context,
                "Hide livestreams", "Hide livestreams from the feed.",
                Settings.HIDE_LIVE
        ));
        addPreference(new TogglePreference(
                context,
                "Hide story", "Hide story from feed.",
                Settings.HIDE_STORY
        ));
        addPreference(new TogglePreference(
                context,
                "Hide image video", "Hide image video from feed.",
                Settings.HIDE_IMAGE
        ));
        addPreference(new RangeValuePreference(
                context,
                "Min/Max views", "The minimum or maximum views of a video to show.",
                Settings.MIN_MAX_VIEWS
        ));
        addPreference(new RangeValuePreference(
                context,
                "Min/Max likes", "The minimum or maximum likes of a video to show.",
                Settings.MIN_MAX_LIKES
        ));
        addPreference(new RangeValuePreference(
                context,
                "Min/Max comments", "The minimum or maximum comments on a video to show.",
                Settings.MIN_MAX_COMMENTS
        ));
        addPreference(new RangeValuePreference(
                context,
                "Min/Max favourites", "The minimum or maximum favourites of a video to show.",
                Settings.MIN_MAX_FAVOURITES
        ));
        addPreference(new RangeValuePreference(
                context,
                "Min/Max shares", "The minimum or maximum shares of a video to show.",
                Settings.MIN_MAX_SHARES
        ));
        addPreference(new TogglePreference(
                context,
                "Hide paid partnerships",
                "Hide videos marked as paid partnership or branded content.",
                Settings.HIDE_PAID_PARTNERSHIP
        ));
        addPreference(new TogglePreference(
                context,
                "Hide AI generated videos",
                "Hide videos carrying TikTok's AI generated label.",
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
                "Hide playlist videos",
                "Hide videos posted as part of a playlist.",
                Settings.HIDE_PLAYLIST_VIDEOS
        ));
        addPreference(new TogglePreference(
                context,
                "Skip blocked sounds",
                "Skip videos that use a sound blocked with the player's sound button, or named below.",
                Settings.HIDE_BLOCKED_SOUNDS
        ));
        addPreference(new InputTextPreference(
                context,
                "Blocked sound names",
                "Comma separated words to match against a sound's name, like saxophone. Case does not matter.",
                Settings.BLOCKED_SOUND_NAMES
        ));
        addPreference(new InputTextPreference(
                context,
                "Blocked sound ids",
                "Comma separated sound ids recorded by the player's sound button. Remove one to unblock it.",
                Settings.BLOCKED_SOUND_IDS
        ));
        addPreference(new TogglePreference(
                context,
                "Hide the playlist bar",
                "Hide the playlist bar along the bottom of videos that belong to a series.",
                Settings.HIDE_PLAYLIST_BAR
        ));
        addPreference(new TogglePreference(
                context,
                "Hide the event badge",
                "Hide the floating promotional badge over the feed.",
                Settings.HIDE_EVENT_BADGE
        ));
        addPreference(new TogglePreference(
                context,
                "Hide inserted cards",
                "Hide the friend recommendation card and the other cards TikTok slots between videos.",
                Settings.HIDE_INSERTED_CARDS
        ));
        addPreference(new TogglePreference(
                context,
                "Filter offline fallback videos",
                "Also apply these filters to downloaded videos TikTok uses when the feed cannot load enough new items.",
                Settings.FILTER_OFFLINE_FALLBACK_VIDEOS
        ));
    }

    private void addSeenVideoRules(Context context) {
        addPreference(new TogglePreference(
                context,
                "Hide videos you have already seen",
                "Keep a local record of what you have watched and drop those videos from "
                        + "later feed pages.",
                Settings.HIDE_SEEN_VIDEOS
        ));
        addPreference(new NumberInputPreference(
                context,
                "Forget seen videos after",
                "Days to remember a video. Zero removes the age limit. History keeps at most 10,000 videos.",
                Settings.SEEN_VIDEO_RETENTION_DAYS, "day", "days"
        ).zeroMeansOff());
        addPreference(new ClearSeenVideoHistoryPreference(context));
    }
}
