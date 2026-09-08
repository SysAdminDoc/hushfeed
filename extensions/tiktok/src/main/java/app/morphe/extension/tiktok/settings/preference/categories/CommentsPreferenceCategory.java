package app.morphe.extension.tiktok.settings.preference.categories;

import android.content.Context;
import android.preference.PreferenceScreen;

import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.InputTextPreference;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;

@SuppressWarnings("deprecation")
public class CommentsPreferenceCategory extends ConditionalPreferenceCategory {
    public CommentsPreferenceCategory(Context context, PreferenceScreen screen) {
        super(context, screen);
        setTitle("Comments and translation");
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
        if (SettingsStatus.commentTranslationEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Auto translate comments",
                    "Translate comments as they load, using TikTok's own translator.",
                    Settings.COMMENT_BATCH_TRANSLATION
            ));
        }
        if (SettingsStatus.hideCommentQuickReactionsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Hide quick comment reactions",
                    "Hide TikTok's exposed quick emoji row in supported comment inputs.",
                    Settings.HIDE_COMMENT_QUICK_REACTIONS
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
        if (SettingsStatus.commentSortControlsEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Full comment sort options",
                    "Show TikTok's own sort sheet on every post, with its hot, newest, media and "
                            + "creator options, instead of whichever cut-down row your account was "
                            + "given.",
                    Settings.COMMENT_SORT_CONTROLS
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
                    "Search the comments",
                    "Put a box above the comments that narrows them to the ones matching "
                            + "what you type, by what they say or who said it. Clearing the "
                            + "box brings the rest back.",
                    Settings.COMMENT_SEARCH
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
                    "Comma separated. A comment is hidden if its text contains any of them. Case does not matter.",
                    Settings.COMMENT_BLOCKED_KEYWORDS
            ));
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
                    "Thumbs down blocks the commenter",
                    "The thumbs down on each comment blocks the account that posted it instead of "
                            + "disliking. The comment dims, an undo banner follows, and a second tap "
                            + "unblocks.",
                    Settings.BLOCK_FROM_COMMENT
            ));
        }
    }
}
