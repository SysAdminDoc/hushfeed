/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.SectionHeadingPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

/**
 * Reading comments, hiding some of them, and blocking who wrote them. The page was one card
 * of thirteen rows in the order the patches arrived.
 */
@SuppressWarnings("deprecation")
public class CommentsPreferenceCategory extends ConditionalPreferenceCategory {
    public CommentsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Comments");
    }

    /** Whether this page has anything on it. The row into it asks the same question. */
    public static boolean isAvailable() {
        return SettingsStatus.commentToolsEnabled
                || SettingsStatus.commentTranslationEnabled
                || SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled
                || SettingsStatus.hideCommentEggsEnabled
                || SettingsStatus.commentSortControlsEnabled;
    }

    @Override
    public boolean getSettingsStatus() {
        return isAvailable();
    }

    @Override
    public void addPreferences(Context context) {
        boolean reading = SettingsStatus.commentTranslationEnabled
                || SettingsStatus.commentSortControlsEnabled
                || SettingsStatus.commentToolsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled;
        if (reading) {
            addPreference(new SectionHeadingPreference(context, "Reading"));
        }
        if (SettingsStatus.commentTranslationEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Auto translate comments",
                    "Translate comments as they load, using TikTok's own translator.",
                    Settings.COMMENT_BATCH_TRANSLATION
            ));
        }
        if (SettingsStatus.commentSortControlsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Full comment sort options",
                    "Show TikTok's own sort sheet on every post, with its hot, newest, media and "
                            + "creator options, instead of whichever cut-down row your account was "
                            + "given. Restart TikTok to apply this. It reads the style "
                            + "once per run and remembers it.",
                    Settings.COMMENT_SORT_CONTROLS
            ));
        }
        if (SettingsStatus.commentToolsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Easier comment likes",
                    "Extend the heart's touch area into nearby blank space. The icon and row spacing stay the same. "
                            + "Text, Reply and other controls keep their own space.",
                    Settings.LARGER_COMMENT_LIKE_TARGET
            ));
            addPreference(new TogglePreference(
                    context,
                    "Search within comments",
                    "Add a search box that filters comments already loaded on this video by text or username. "
                            + "It doesn't search all of TikTok or remove TikTok's suggested-search banner.",
                    Settings.COMMENT_SEARCH
            ));
            addPreference(new TogglePreference(
                    context,
                    "Links in comments open",
                    "A web address someone left in a comment can be tapped instead of copied "
                            + "out by hand. Tapping anywhere else in the comment still does what "
                            + "it did.",
                    Settings.COMMENT_LINKS
            ));
            addPreference(new TogglePreference(
                    context,
                    "More opens the comments",
                    "Tapping more under a video opens its comments with the whole caption at the top instead of "
                            + "spreading the caption over the video. A tap on the caption itself does the same. "
                            + "Photo posts keep TikTok's own page for this.",
                    Settings.CAPTION_OPENS_COMMENTS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Caption above comments",
                    "The comment button also opens a video's comments with its caption at the top. The caption over the video goes away while this is on.",
                    Settings.CAPTION_ABOVE_COMMENTS
            ));
        }
        if (SettingsStatus.copyCommentsWithoutUsernameEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Copy comments without username",
                    "Copy only the comment text when using TikTok's copy comment action.",
                    Settings.COPY_COMMENTS_WITHOUT_USERNAME
            ));
        }

        boolean hiding = SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.hideCommentEggsEnabled
                || SettingsStatus.commentToolsEnabled;
        if (hiding) {
            addPreference(new SectionHeadingPreference(context, "Hiding"));
        }
        if (SettingsStatus.hideCommentQuickReactionsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide comment typing suggestions",
                    "Hide the quick emoji row and automatic sticker suggestions that appear while you type. The emoji and sticker buttons still work.",
                    Settings.HIDE_COMMENT_QUICK_REACTIONS
            ));
        }
        if (SettingsStatus.hideCommentEggsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide comment popup ads",
                    "Stop the brand animation that plays over the comments when what someone typed matches an advertiser's trigger.",
                    Settings.HIDE_COMMENT_EGGS
            ));
        }
        if (SettingsStatus.commentToolsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide search suggestions above comments",
                    "Hide TikTok's \"Search: ...\" banner above the comment count. "
                            + "This doesn't hide the comments or Hushfeed's comment-search box.",
                    Settings.HIDE_COMMENT_SEARCH_SUGGESTIONS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Compact comment header",
                    "Start with comments. Hide the comment count, sort and close buttons, "
                            + "plus the suggestion area above them. Use Back or swipe down to close comments.",
                    Settings.COMPACT_COMMENT_HEADER
            ));
            addPreference(new TogglePreference(
                    context,
                    "Filter comments by keyword",
                    "Hide comments that contain any of the words below, or that come from the accounts below.",
                    Settings.COMMENT_KEYWORD_FILTER
            ));
            addPreference(new InputTextPreference(
                    context,
                    "Blocked comment words",
                    "Comma separated. A comment is hidden if its text contains any of them. Case does not matter. Two phrases in quotes can be joined: \"a\" & \"b\" needs both, \"a\" !& \"b\" needs the first without the second.",
                    Settings.COMMENT_BLOCKED_KEYWORDS
            ).withCheck(app.morphe.extension.tiktok.feedfilter.KeywordRules::problem));
            addPreference(new InputTextPreference(
                    context,
                    "Hidden commenters",
                    "Comma separated usernames or display names whose comments are hidden.",
                    Settings.COMMENT_BLOCKED_USERS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide picture comments",
                    "Hide comments made of an image or a sticker instead of words.",
                    Settings.HIDE_COMMENT_MEDIA
            ));
            addPreference(new TogglePreference(
                    context,
                    "Hide comment polls",
                    "Hide voting cards and poll results above the comments, including polls that have ended.",
                    Settings.HIDE_COMMENT_POLLS
            ));
            addPreference(new TogglePreference(
                    context,
                    "Show poll results before voting",
                    "A comment poll shows how many picked each answer before you vote. Tapping an answer still votes.",
                    Settings.SHOW_POLL_RESULTS
            ));

            addPreference(new SectionHeadingPreference(context, "Blocking"));
            addPreference(new TogglePreference(
                    context,
                    "Thumbs down blocks the commenter",
                    "The thumbs down on each comment blocks the commenter instead of "
                            + "disliking. The comment dims, an undo banner follows, and a second tap "
                            + "unblocks.",
                    Settings.BLOCK_FROM_COMMENT
            ));
        }
    }
}
