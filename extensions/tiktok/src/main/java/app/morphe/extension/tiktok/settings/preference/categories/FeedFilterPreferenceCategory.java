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
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class FeedFilterPreferenceCategory extends ConditionalPreferenceCategory {
    public FeedFilterPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Feed filter");
    }

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.feedFilterEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        addPreference(new TogglePreference(
                context,
                "Remove feed ads", "Remove ads from feed.",
                Settings.REMOVE_ADS
        ));
        addPreference(new TogglePreference(
                context,
                "Hide TikTok Shop", "Hide TikTok shop from feed.",
                Settings.HIDE_SHOP
        ));
        addPreference(new TogglePreference(
                context,
                "Hide livestreams", "Hide livestreams from feed.",
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
        if (SettingsStatus.seenVideoFilterEnabled) {
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
                    "Days to remember a watched video for. Zero remembers them forever.",
                    Settings.SEEN_VIDEO_RETENTION_DAYS,
                    0,
                    3650
            ));
            addPreference(new ClearSeenVideoHistoryPreference(context));
        }
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
}

