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

    @Override
    public boolean getSettingsStatus() {
        return SettingsStatus.commentToolsEnabled
                || SettingsStatus.commentTranslationEnabled
                || SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled;
    }

    @Override
    public void addPreferences(Context context) {
        if (SettingsStatus.commentTranslationEnabled) {
            addPreference(new TogglePreference(
                    context,
                    "Auto translate comments",
                    "Automatically translates loaded comment batches using TikTok's translation system.",
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
        if (SettingsStatus.commentToolsEnabled) {
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
                    "Block button on comments",
                    "Show a small block button beside each comment. One tap blocks the account that "
                            + "posted it, dims the comment, and offers an undo.",
                    Settings.BLOCK_FROM_COMMENT
            ));
        }
    }
}
