/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/TikTokPreferenceFragment.java
 * Mirror, since GitHub blocks the original: https://gitlab.com/ReVanced/revanced-patches/-/blob/main/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/TikTokPreferenceFragment.java
 */

package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.Window;
import android.widget.ListView;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.HushfeedPause;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.captions.CaptionTools;
import app.morphe.extension.tiktok.comment.CommentSearch;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabFragment;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabRuntime;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.share.ShareUrlSanitizer;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.categories.CommentsPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.DebugPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.ExtensionPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.FeedFilterPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.FeedNavigationPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.InboxPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.PlaybackPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.PrivacyPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.ScreenTimePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.SharePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.SimSpoofPreferenceCategory;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

@SuppressWarnings("deprecation")
public class TikTokPreferenceFragment extends AbstractPreferenceFragment {
    private static final String FEATURE_GATE_LAB_KEY = "action_feature_gate_lab";
    private static final String PAUSE_SUMMARY = "From the next start TikTok runs as if it were not patched, so you can tell whether a problem comes from Hushfeed. Your settings stay as they are.";
    private static final int REQUEST_DOWNLOAD_PATH_FOLDER = 8841;
    private static final String ARG_SECTION = "morphe_settings_section";
    private static final String ARG_SEARCH = "morphe_settings_search";
    private static final String ARG_TARGET_KEY = "morphe_settings_target_key";
    private static TikTokPreferenceFragment activeFragment;
    /** Pinned to the top of this page while a restart is owed; off the page otherwise. */
    private RestartPendingPreference restartPending;
    /** Rows on this page whose sentence was swapped for "Restart pending.", to swap back. */
    private final java.util.Set<String> pendingSummaries = new java.util.HashSet<>();
    /** Summaries a parent's reason was appended to, so it can be taken off again. */
    private final java.util.Map<String, String> reasonSummaries = new java.util.HashMap<>();
    /**
     * Which folder setting the picker was opened for, by key rather than by the preference
     * itself. The picker is a separate activity, so this one is routinely destroyed behind it
     * and the object that was waiting no longer belongs to the screen that comes back.
     */
    private static String pendingDownloadPathKey;
    private static final String PENDING_DOWNLOAD_PATH_STATE = "morphe_pending_download_path";
    private static final String SEARCH_QUERY_STATE = "morphe_search_query";
    private SettingsListAdapter styledAdapter;
    private PreferenceScreen searchScreen;
    private List<SearchResult> searchIndex;
    private final List<Preference> searchRows = new ArrayList<>();
    private SettingsSearchInputPreference searchInput;

    /**
     * Each section carries one sentence, used both as the subtitle on the home row and as the
     * caption at the top of its own page. There used to be two, and they disagreed: the
     * Downloads row promised quality and subtitles while its page said path and watermark.
     */
    private enum Section {
        FEED_FILTER("Feed filter", "Choose what reaches your feed"),
        FEED_NAVIGATION("Feed tabs", "Arrange your feed and bottom tabs"),
        INTERFACE("Feed screen", "Captions, gestures and on-screen controls"),
        PLAYBACK("Playback", "Quality, speed and auto-advance"),
        SCREEN_TIME("Screen time", "Daily budgets, reminders and the hold"),
        COMMENTS("Comments", "Filters, translation and copy options"),
        DOWNLOADS("Downloads", "Quality, files, subtitles and hand-off"),
        SHARE("Share sheet", "People, shortcuts and sending controls"),
        INBOX("Inbox", "Choose which rows and controls appear"),
        PRIVACY("Privacy", "Tracking, device access and links"),
        REGION("Region", "Country and network preferences"),
        BEHAVIOR("App", "Layout, player, search and system"),
        DIAGNOSTICS("Diagnostics", "Logging, hook status and reports"),
        BACKUP("Backup and restore", "Save, restore, reset and undo your settings");

        final String title;
        final String description;

        Section(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static final class SearchResult {
        final Section section;
        final String key;
        final String title;
        final String summary;
        final String category;
        /** Folded once when the index is built: it never changes, and a query is typed a letter
         *  at a time over about 170 of these. */
        final String normalized;

        SearchResult(Section section, String key, String title, String summary, String category) {
            this.section = section;
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.category = category;
            this.normalized = normalizeSearchText(title + " " + summary + " " + category);
        }

        String displaySummary() {
            if (summary.isEmpty()) {
                return category;
            }
            return category + " · " + summary;
        }
    }

    public static void openDownloadPathFolderPicker(DownloadPathPreference preference) {
        if (activeFragment == null) {
            app.morphe.extension.shared.Utils.showToastLong(L10n.t(
                    "This phone has no folder picker. Type the folder into the row instead."));
            return;
        }

        pendingDownloadPathKey = preference.getKey();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activeFragment.startActivityForResult(intent, REQUEST_DOWNLOAD_PATH_FOLDER);
        } catch (ActivityNotFoundException exception) {
            pendingDownloadPathKey = null;
            app.morphe.extension.shared.Utils.showToastLong(L10n.t(
                    "This phone has no folder picker. Type the folder into the row instead."));
        }
    }

    @Override
    protected void syncPreferenceWithStoredValue(@NonNull Preference pref,
                                                @NonNull Setting<?> setting,
                                                @NonNull SharedPreferences preferences) {
        if (pref instanceof NumberInputPreference) {
            NumberInputPreference numberPref = (NumberInputPreference) pref;
            numberPref.setValue(preferences.getString(setting.key, setting.defaultValue.toString()));
        } else if (pref instanceof CreatorListPreference) {
            CreatorListPreference creatorPref = (CreatorListPreference) pref;
            creatorPref.setValue(preferences.getString(setting.key, setting.defaultValue.toString()));
        } else if (pref instanceof RangeValuePreference) {
            RangeValuePreference rangePref = (RangeValuePreference) pref;
            rangePref.setValue(preferences.getString(setting.key, setting.defaultValue.toString()));
        } else if (pref instanceof DownloadPathPreference) {
            DownloadPathPreference pathPref = (DownloadPathPreference) pref;
            pathPref.setValue(preferences.getString(setting.key, setting.defaultValue.toString()));
        } else if (pref instanceof TabSelectionPreference) {
            TabSelectionPreference tabsPref = (TabSelectionPreference) pref;
            tabsPref.setValue(preferences.getString(setting.key, setting.defaultValue.toString()));
        } else {
            super.syncPreferenceWithStoredValue(pref, setting, preferences);
        }
    }

    @Override
    protected void syncSettingWithPreference(
            @NonNull Preference pref,
            @NonNull Setting setting,
            boolean applySettingToPreference
    ) {
        if (pref instanceof NumberInputPreference) {
            NumberInputPreference numberInputPreference = (NumberInputPreference) pref;
            if (applySettingToPreference) {
                numberInputPreference.setValue(setting.savedValue().toString());
            } else {
                Setting.privateSetValueFromString(setting, numberInputPreference.getValue());
            }
        } else if (pref instanceof CreatorListPreference) {
            CreatorListPreference creatorListPreference = (CreatorListPreference) pref;
            if (applySettingToPreference) {
                creatorListPreference.setValue(setting.savedValue().toString());
            } else {
                Setting.privateSetValueFromString(setting, creatorListPreference.getValue());
            }
        } else if (pref instanceof RangeValuePreference) {
            RangeValuePreference rangeValuePref = (RangeValuePreference) pref;
            if (applySettingToPreference) {
                rangeValuePref.setValue(setting.savedValue().toString());
            } else {
                Setting.privateSetValueFromString(setting, rangeValuePref.getValue());
            }
        } else if (pref instanceof DownloadPathPreference) {
            DownloadPathPreference downloadPathPref = (DownloadPathPreference) pref;
            if (applySettingToPreference) {
                downloadPathPref.setValue(setting.savedValue().toString());
            } else {
                Setting.privateSetValueFromString(setting, downloadPathPref.getValue());
            }
        } else if (pref instanceof TabSelectionPreference) {
            TabSelectionPreference tabSelectionPref = (TabSelectionPreference) pref;
            if (applySettingToPreference) {
                tabSelectionPref.setValue(setting.savedValue().toString());
            } else {
                Setting.privateSetValueFromString(setting, tabSelectionPref.getValue());
            }
        } else {
            super.syncSettingWithPreference(pref, setting, applySettingToPreference);
        }
        if (!applySettingToPreference
                && (setting == Settings.BLOCK_AUTHOR_BUTTON
                || setting == Settings.LOCAL_HIDE_BUTTON
                || setting == Settings.BLOCK_SOUND_BUTTON
                || setting == Settings.NOT_INTERESTED_BUTTON)) {
            BlockAuthorOverlay.refresh();
        }
        if (!applySettingToPreference && setting == Settings.COMMENT_SEARCH) {
            CommentSearch.onSettingChanged();
        }
        if (!applySettingToPreference && setting == Settings.KEEP_CAPTIONS_CLEAR_DISPLAY) {
            CaptionTools.onSettingChanged();
        }
        if (!applySettingToPreference && setting == BaseSettings.PAUSED) {
            refreshStatusCard();
        }
    }

    @Override
    protected boolean prefIsSetToDefault(Preference pref, Setting<?> setting) {
        String defaultValue = setting.defaultValue.toString();
        if (pref instanceof NumberInputPreference) {
            return defaultValue.equals(((NumberInputPreference) pref).getValue());
        }
        if (pref instanceof CreatorListPreference) {
            return defaultValue.equals(((CreatorListPreference) pref).getValue());
        }
        if (pref instanceof RangeValuePreference) {
            return defaultValue.equals(((RangeValuePreference) pref).getValue());
        }
        if (pref instanceof DownloadPathPreference) {
            return defaultValue.equals(((DownloadPathPreference) pref).getValue());
        }
        if (pref instanceof TabSelectionPreference) {
            return defaultValue.equals(((TabSelectionPreference) pref).getValue());
        }
        return super.prefIsSetToDefault(pref, setting);
    }

    @Override
    protected boolean preferenceShowsSettingValue(@NonNull Preference pref,
                                                  @NonNull Setting<?> setting) {
        String expected = setting.savedValue() instanceof Enum<?>
                ? ((Enum<?>) setting.savedValue()).name() : String.valueOf(setting.savedValue());
        if (pref instanceof NumberInputPreference) {
            return expected.equals(((NumberInputPreference) pref).getValue());
        }
        if (pref instanceof CreatorListPreference) {
            return expected.equals(((CreatorListPreference) pref).getValue());
        }
        if (pref instanceof RangeValuePreference) {
            return expected.equals(((RangeValuePreference) pref).getValue());
        }
        if (pref instanceof DownloadPathPreference) {
            return expected.equals(((DownloadPathPreference) pref).getValue());
        }
        if (pref instanceof TabSelectionPreference) {
            return expected.equals(((TabSelectionPreference) pref).getValue());
        }
        return super.preferenceShowsSettingValue(pref, setting);
    }

    @Override protected CharSequence initializationErrorTitle(Context context) {
        return L10n.t(context, "Settings couldn't open");
    }

    @Override protected CharSequence initializationErrorSummary(Context context) {
        return L10n.t(context, "Try again, or go back to TikTok.");
    }

    @Override protected CharSequence initializationBackLabel(Context context) {
        return L10n.t(context, "Back");
    }

    @Override protected CharSequence initializationRetryLabel(Context context) {
        return L10n.t(context, "Retry");
    }

    /**
     * Draws Retry as the way forward and Back as the way out.
     *
     * <p>Both were plain rows in the same weight and colour, so the page that opens when settings
     * will not load offered two identical looking choices and no sense of which one to take.
     */
    @Override protected ErrorActionStyler errorActionStyler() {
        // SettingsListAdapter styles the rows again after this, so it applies the same helper.
        return SettingsUi::styleErrorAction;
    }

    @Override protected CharSequence preferenceChangeRecoveredMessage(Context context) {
        return L10n.t(context, "The setting couldn't finish updating. Its saved value is shown.");
    }

    @Override protected CharSequence preferenceChangeRecoveryFailedMessage(Context context) {
        return L10n.t(context,
                "Settings couldn't refresh completely. Reopen settings and try again.");
    }

    @Override
    protected void initialize() {
        final var context = getActivity();
        activeFragment = this;

        // Nothing sets one. The framework only raises that dialog for a setting carrying a
        // userDialogMessage, and no setting in this bundle has one, so this was a translated
        // string for a screen nobody could reach. The safety net Reset and Undo do want is its
        // own item on the roadmap.
        confirmDialogTitle = null;

        // The two sentences the shared library says on this bundle's behalf. It cannot reach a
        // translation table itself, so they are handed to it once, from here, where the
        // settings screen is being built and a context is at hand.
        savedMessage = L10n.t(context, "Saved. Restart TikTok to apply this.");
        AbstractPreferenceFragment.setRestartFeedbackPresenter((feedbackContext, ignored) ->
                SettingsActionBanner.showRestart(feedbackContext,
                        RestartPendingPreference.label(feedbackContext)));
        app.morphe.extension.shared.settings.preference.LogBufferManager.clearedMessage =
                L10n.t(context, "Diagnostic data cleared. Tap again to put it back.");
        app.morphe.extension.shared.settings.preference.LogBufferManager.nothingToClearMessage =
                L10n.t(context, "There is no diagnostic data to clear");
        app.morphe.extension.shared.settings.preference.LogBufferManager.restoredMessage =
                L10n.t(context, "Diagnostic data put back");
        app.morphe.extension.shared.settings.preference.LogBufferManager.nothingToRestoreMessage =
                L10n.t(context, "There is no diagnostic data to put back");
        app.morphe.extension.shared.settings.preference.LogBufferManager.restoreFailedMessage =
                L10n.t(context, "Couldn't put back the diagnostic data. Try again.");
        // The rest of what the shared export path says, on a German phone in German. Every one
        // of these reached the reader in English, branded for the library rather than the
        // bundle, and two of them carried an exception's text.
        app.morphe.extension.shared.settings.preference.LogBufferManager.nothingToExportMessage =
                L10n.t(context, "No matching diagnostics found");
        app.morphe.extension.shared.settings.preference.LogBufferManager.copiedMessage =
                L10n.t(context, "Diagnostic report copied to the clipboard");
        app.morphe.extension.shared.settings.preference.LogBufferManager.exportFailedMessage =
                L10n.t(context, "The diagnostic report couldn't be saved. Try again.");
        app.morphe.extension.shared.settings.preference.LogBufferManager.noContextMessage =
                L10n.t(context, "The diagnostic report couldn't be saved yet. Try again in a moment.");
        app.morphe.extension.shared.settings.preference.LogBufferManager.alreadySavingMessage =
                L10n.t(context, "A diagnostic report is already being saved");
        app.morphe.extension.shared.settings.preference.LogBufferManager.savedToMessage =
                L10n.t(context, "Full report saved to %1$s");
        app.morphe.extension.shared.settings.preference.LogBufferManager.couldNotStartMessage =
                L10n.t(context, "Couldn't start the report export. Try again shortly.");
        // Four whole sentences rather than five fragments, so each one is a row a translator
        // can move the numbers around inside. The context is asked for when a line is written
        // rather than captured here: this writer is a static and outlives the screen.
        app.morphe.extension.shared.diagnostics.HookStatus.setLineWriter(
                (family, found, missing, truncated, firstMiss) -> {
                    Context lineContext = app.morphe.extension.shared.Utils.getContext();
                    if (firstMiss == null) {
                        return truncated
                                ? L10n.f(lineContext, "%1$s: %2$d found, %3$d missing, and more it "
                                        + "stopped counting", family, found, missing)
                                : L10n.f(lineContext, "%1$s: %2$d found, %3$d missing",
                                        family, found, missing);
                    }
                    return truncated
                            ? L10n.f(lineContext, "%1$s: %2$d found, %3$d missing, and more it "
                                    + "stopped counting. First missing: %4$s",
                                    family, found, missing, firstMiss)
                            : L10n.f(lineContext, "%1$s: %2$d found, %3$d missing. First missing: "
                                    + "%4$s", family, found, missing, firstMiss);
                });

        SettingsUi.syncDarkMode(context);

        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);

        Section section = getRequestedSection();
        if (isSearchRequested()) {
            createSearchMenu(context, preferenceScreen);
        } else if (section == null) {
            createMasterMenu(context, preferenceScreen);
        } else {
            createSectionMenu(context, preferenceScreen, section);
        }
        restartPending = new RestartPendingPreference(context);
        refreshRestartPending();
    }

    @Override
    protected void onRestartPendingChanged() {
        refreshRestartPending();
    }

    /**
     * Pins the restart row while a restart is owed and takes it away when nothing is, and lets
     * the rows that owe it say "Restart pending" in place of the sentence every restart-gated
     * row carries, so the reader can see which of their changes are still waiting.
     */
    /**
     * A row its parent has greyed says which switch would turn it on.
     *
     * <p>Four rows grey out when their parent is off and said nothing about it, so the row read
     * as broken and a screen reader announced only "dimmed". The parent's own title is read off
     * its row rather than kept in a second list, because both rows are always on the same page
     * and a title kept twice is a title that drifts.
     */
    @Override
    protected void updatePreferenceAvailability(Preference pref, Setting<?> setting) {
        super.updatePreferenceAvailability(pref, setting);
        Context context = getActivity();
        if (context == null || pref.getKey() == null) return;
        String key = pref.getKey();
        String appended = reasonSummaries.remove(key);
        if (appended != null) {
            CharSequence current = pref.getSummary();
            if (current != null && current.toString().endsWith(appended)) {
                pref.setSummary(current.toString().substring(0, current.length() - appended.length()));
            }
        }
        if (setting.isAvailable()) return;
        String parentTitle = null;
        for (Setting<?> parent : setting.getParentSettings()) {
            Preference row = findPreference(parent.key);
            CharSequence title = row == null ? null : row.getTitle();
            if (title != null && title.length() > 0) {
                parentTitle = title.toString();
                break;
            }
        }
        if (parentTitle == null) return;
        String reason = " " + L10n.f(context, "Turn on %1$s first.", parentTitle);
        CharSequence summary = pref.getSummary();
        String body = summary == null ? "" : summary.toString();
        if (body.endsWith(reason)) return;
        pref.setSummary(body + reason);
        reasonSummaries.put(key, reason);
    }

    private void refreshRestartPending() {
        PreferenceScreen screen = getPreferenceScreen();
        Context context = getActivity();
        if (screen == null || restartPending == null || context == null) return;
        boolean owed = !restartPendingKeys().isEmpty();
        boolean shown = screen.findPreference(RestartPendingPreference.KEY) != null;
        // Only touched while something is owed or shown. removePreference notifies the
        // hierarchy whether or not the row was there, which rebuilt every page's list once
        // more at open and moved twenty captures by a switch frame. While owed, the row is
        // taken out and put back so the list rebinds the count.
        if (shown) screen.removePreference(restartPending);
        if (owed) screen.addPreference(restartPending);

        String generic = L10n.t(context, TogglePreference.RESTART_SENTENCE);
        // The sentence it replaces ends in a full stop and sits inside prose ("... Restart
        // pending. If the old layout is still there, unfold again."), so this keeps one.
        String pending = L10n.t(context, "Restart pending") + ".";
        java.util.List<String> owedKeys = restartPendingKeys();
        for (String key : owedKeys) {
            Preference row = findPreference(key);
            CharSequence summary = row == null ? null : row.getSummary();
            if (summary == null || !summary.toString().contains(generic)) continue;
            row.setSummary(summary.toString().replace(generic, pending));
            pendingSummaries.add(key);
        }
        // A switch flipped back owes nothing, so its row says what it said before.
        for (String key : new java.util.ArrayList<>(pendingSummaries)) {
            if (owedKeys.contains(key)) continue;
            pendingSummaries.remove(key);
            Preference row = findPreference(key);
            CharSequence summary = row == null ? null : row.getSummary();
            if (summary == null || !summary.toString().contains(pending)) continue;
            row.setSummary(summary.toString().replace(pending, generic));
        }
    }

    private static java.util.List<String> restartPendingKeys() {
        synchronized (AbstractPreferenceFragment.restartPending) {
            return new java.util.ArrayList<>(AbstractPreferenceFragment.restartPending);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView list = view.findViewById(android.R.id.list);
        if (list != null) {
            SettingsUi.styleScrollableList(list);
            list.setBackgroundColor(SettingsUi.background());
            list.setCacheColorHint(SettingsUi.background());
            list.setDivider(null);
            list.setDividerHeight(0);
            list.setPadding(SettingsUi.dp(getActivity(), 16), 0, SettingsUi.dp(getActivity(), 16), SettingsUi.dp(getActivity(), 24));
            list.setClipToPadding(false);
            list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        // setStatusBarColor and setNavigationBarColor were here. Both do nothing from target 35,
        // which TikTok is well past, so the bars are painted by the root view's own background
        // reaching behind them instead. The icon flags below still work.
        view.setBackgroundColor(SettingsUi.background());

        View decor = window.getDecorView();
        int visibility = decor.getSystemUiVisibility();
        if (SettingsUi.isDarkMode()) {
            visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(visibility);
    }

    @SuppressWarnings("deprecation")
    @Override public android.animation.Animator onCreateAnimator(
            int transit, boolean enter, int nextAnim) {
        if ((transit == 0 && nextAnim == 0) || getActivity() == null) {
            return super.onCreateAnimator(transit, enter, nextAnim);
        }
        float dp24 = SettingsUi.dp(getActivity(), 24);
        boolean isRtl = getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
        float endEdge = isRtl ? -dp24 : dp24;
        boolean opening = transit == android.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
        float fromX, toX;
        float fromAlpha, toAlpha;
        if (opening && enter) {
            fromX = endEdge; toX = 0f; fromAlpha = 0f; toAlpha = 1f;
        } else if (opening) {
            fromX = 0f; toX = -endEdge * 0.3f; fromAlpha = 1f; toAlpha = 0f;
        } else if (enter) {
            fromX = -endEdge * 0.3f; toX = 0f; fromAlpha = 0f; toAlpha = 1f;
        } else {
            fromX = 0f; toX = endEdge; fromAlpha = 1f; toAlpha = 0f;
        }
        ObjectAnimator slide = ObjectAnimator.ofFloat(null, "translationX", fromX, toX);
        ObjectAnimator fade = ObjectAnimator.ofFloat(null, "alpha", fromAlpha, toAlpha);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(slide, fade);
        set.setDuration(220);
        set.setInterpolator(new android.view.animation.DecelerateInterpolator());
        return set;
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PENDING_DOWNLOAD_PATH_STATE, pendingDownloadPathKey);
        if (searchInput != null) {
            String query = searchInput.getQuery();
            if (!query.isEmpty()) outState.putString(SEARCH_QUERY_STATE, query);
        }
    }

    @Override public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        if (pendingDownloadPathKey == null && state != null) {
            pendingDownloadPathKey = state.getString(PENDING_DOWNLOAD_PATH_STATE);
        }
        if (state != null && searchInput != null) {
            String savedQuery = state.getString(SEARCH_QUERY_STATE);
            if (savedQuery != null && !savedQuery.isEmpty()) {
                searchInput.setQuery(savedQuery);
            }
        }
        ListView list = getView().findViewById(android.R.id.list);
        if (list != null && list.getAdapter() != null) {
            styledAdapter = new SettingsListAdapter(list.getAdapter());
            list.setAdapter(styledAdapter);
            String targetKey = getTargetKey();
            if (targetKey != null) {
                scrollToPreference(list, targetKey);
                list.post(() -> scrollToPreference(list, targetKey));
            }
        }
    }

    @Override public void onDestroyView() {
        // The search page raises the keyboard for its field, and popping the page left it up
        // over whatever page came next until Back was pressed once more. The system Back closes
        // it because the field still has focus; taking it down with the view covers the header's
        // Back and a tapped result alike, since both replace this view.
        hideKeyboard();
        if (styledAdapter != null) {
            styledAdapter.dispose();
            styledAdapter = null;
        }
        super.onDestroyView();
    }

    private Section getRequestedSection() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return null;
        }
        String name = arguments.getString(ARG_SECTION);
        if (name == null) {
            return null;
        }
        try {
            return Section.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isSearchRequested() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(ARG_SEARCH, false);
    }

    private String getTargetKey() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return null;
        }
        String key = arguments.getString(ARG_TARGET_KEY);
        return key == null || key.isEmpty() ? null : key;
    }

    private void createSearchMenu(Context context, PreferenceScreen screen) {
        searchScreen = screen;
        screen.addPreference(SettingsHeaderPreference.section(context, "Search settings", this::navigateBack));
        screen.addPreference(SettingsHeaderPreference.caption(context,
                "Search translated titles and descriptions, then open the original setting."));
        searchInput = new SettingsSearchInputPreference(context, this::updateSearchResults);
        screen.addPreference(searchInput);
        searchIndex = buildSearchIndex(context, FeatureGateLabRuntime.isInstalled());
        updateSearchResults("");
    }

    private void updateSearchResults(String query) {
        if (searchScreen == null) {
            return;
        }
        for (Preference row : searchRows) {
            searchScreen.removePreference(row);
        }
        searchRows.clear();

        // Folding happens before the empty check: a query of nothing but accent marks is not
        // empty as typed but folds away to nothing, and every setting contains "".
        String normalizedQuery = normalizeSearchText(query == null ? "" : query.trim());
        if (normalizedQuery.isEmpty()) {
            if (searchInput != null) searchInput.hideResultCount();
            addSearchState("Start typing", "Search by name, description or category.");
            return;
        }

        List<SearchResult> matches = new ArrayList<>();
        for (SearchResult result : searchIndex) {
            if (result.normalized.contains(normalizedQuery)) {
                matches.add(result);
            }
        }
        if (searchInput != null) searchInput.showResultCount(matches.size());
        if (matches.isEmpty()) {
            addSearchState("No matching settings", "Try a different word or clear the search.");
            return;
        }

        int order = 0;
        for (SearchResult result : matches) {
            Preference row = new Preference(getActivity());
            row.setTitle(result.title);
            row.setSummary(result.displaySummary());
            row.setOrder(order++);
            row.setOnPreferenceClickListener(preference -> {
                if (FEATURE_GATE_LAB_KEY.equals(result.key)) {
                    FeatureGateLabFragment.open(getActivity());
                } else if (LicensesPreference.KEY.equals(result.key)) {
                    LicensesPreference.show(getActivity());
                } else if (MorpheTikTokAboutPreference.KEY.equals(result.key)) {
                    Utils.openLink(MorpheTikTokAboutPreference.SOURCE_URL);
                } else {
                    openSection(result.section, result.key);
                }
                return true;
            });
            searchRows.add(row);
            searchScreen.addPreference(row);
        }
    }

    private void addSearchState(String title, String summary) {
        Preference state = new Preference(getActivity());
        state.setTitle(L10n.t(getActivity(), title));
        state.setSummary(L10n.t(getActivity(), summary));
        state.setSelectable(false);
        state.setOrder(0);
        searchRows.add(state);
        searchScreen.addPreference(state);
    }

    /**
     * How many settings on one section are away from their default.
     *
     * <p>Each badge used to read a list of settings kept by hand beside the page it described.
     * The Feed filter list named 17 of the 34 settings that page binds, so nine of its switches
     * moved nothing, and every list counted a switch that is on by default as "on", which is
     * why a stock install showed "Comments and translation, 3 on". Building the section is what
     * the search index already does two methods down, so the count and the page can no longer
     * disagree about what is on the page.
     */
    private int countChangedSettings(Context context, Section section) {
        int count = 0;
        // A plain loop: Collection.stream arrived in API 24 and the payload runs from 23.
        for (Setting<?> setting : settingsOn(context, section)) {
            // A setting this build cannot reach is not on the page, so it is not something the
            // reader has turned on.
            if (setting.isAvailable()
                    && !java.util.Objects.equals(setting.savedValue(), setting.defaultValue)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The settings one section binds, worked out once and remembered.
     *
     * <p>Building the page is how this knows what is on it, and building eleven pages on every
     * return to the master menu is 184 preferences of work for a number. The pages do not gain
     * or lose rows while the screen is open: what changes is the values, and those are read
     * again every time.
     */
    private List<Setting<?>> settingsOn(Context context, Section section) {
        List<Setting<?>> known = sectionSettings.get(section);
        if (known != null) {
            return known;
        }
        List<Setting<?>> found = new ArrayList<>();
        PreferenceScreen scratch = getPreferenceManager().createPreferenceScreen(context);
        PreferenceCategory category = createCategory(context, scratch, section);
        if (category != null) {
            collectSettings(category, found, new java.util.HashSet<>());
            scratch.removePreference(category);
        }
        sectionSettings.put(section, found);
        return found;
    }

    private static void collectSettings(PreferenceGroup group, List<Setting<?>> found,
            java.util.Set<String> seen) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (preference instanceof PreferenceGroup) {
                collectSettings((PreferenceGroup) preference, found, seen);
                continue;
            }
            // A checklist row stands for several switches and has no setting of its own.
            if (preference instanceof SwitchListPreference) {
                for (Setting<?> setting : ((SwitchListPreference) preference).settings()) {
                    if (seen.add(setting.key)) found.add(setting);
                }
                continue;
            }
            if (!preference.hasKey()) {
                continue;
            }
            Setting<?> setting = Setting.getSettingFromPath(preference.getKey());
            // A row with no setting behind it is an action. Two rows can share one setting, and
            // a page can bind the same row twice.
            if (setting == null || !seen.add(setting.key)) {
                continue;
            }
            found.add(setting);
        }
    }

    private List<SearchResult> buildSearchIndex(Context context, boolean featureGateLabInstalled) {
        List<SearchResult> results = new ArrayList<>();
        PreferenceScreen scratch = getPreferenceManager().createPreferenceScreen(context);
        for (Section section : Section.values()) {
            // Back up, Restore, Reset and Undo are added straight to their page rather than
            // through a category, so walking categories alone never saw them: searching
            // "backup" or "restore" answered "No matching settings" for four rows one page away.
            if (section == Section.BACKUP) {
                PreferenceScreen backupRows = getPreferenceManager().createPreferenceScreen(context);
                SettingsBackupPreference.addTo(this, backupRows);
                indexRows(results, backupRows, section, L10n.t(context, section.title));
                continue;
            }
            PreferenceCategory category = createCategory(context, scratch, section);
            if (category == null) {
                continue;
            }
            String categoryTitle = String.valueOf(category.getTitle());
            indexRows(results, category, section, categoryTitle);
            scratch.removePreference(category);
        }
        // This row opens its own fragment from the master menu rather than living in one of the
        // section categories walked above. Without an explicit entry, both its title and its
        // summary returned zero results on a patched phone even though the row was visible.
        if (featureGateLabInstalled) {
            results.add(new SearchResult(
                    null,
                    FEATURE_GATE_LAB_KEY,
                    L10n.t(context, "Feature Gate Lab"),
                    L10n.t(context, "Search and override gates"),
                    L10n.t(context, "Settings")
            ));
        }
        // Pause Hushfeed sits on the master menu too, and it is what a reader asking whether a
        // problem is Hushfeed's searches for.
        results.add(new SearchResult(
                null,
                BaseSettings.PAUSED.key,
                L10n.t(context, "Pause Hushfeed"),
                L10n.t(context, PAUSE_SUMMARY),
                L10n.t(context, "Settings")
        ));
        // The About row sits on the master menu beside the Lab, so it is indexed the same way.
        // Its summary carries the bundle version, which is what a reporter searches for.
        results.add(new SearchResult(
                null,
                MorpheTikTokAboutPreference.KEY,
                "Hushfeed",
                MorpheTikTokAboutPreference.currentSummary(context).toString(),
                L10n.t(context, "Settings")
        ));
        // Somebody looking for "licence" or "notice" is looking for exactly one thing, and it
        // sits on the master menu beside About rather than inside a section.
        results.add(new SearchResult(
                null,
                LicensesPreference.KEY,
                LicensesPreference.title(context),
                LicensesPreference.summary(context),
                L10n.t(context, "Settings")
        ));
        return results;
    }

    private void indexRows(List<SearchResult> results, PreferenceGroup group, Section section,
            String categoryTitle) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (!preference.hasKey() || !preference.isSelectable()) {
                continue;
            }
            Setting<?> setting = Setting.getSettingFromPath(preference.getKey());
            if (setting != null && !setting.isAvailable()) {
                continue;
            }
            CharSequence title = preference.getTitle();
            if (title == null || title.length() == 0) {
                continue;
            }
            CharSequence summary = preference.getSummary();
            results.add(new SearchResult(
                    section,
                    preference.getKey(),
                    title.toString(),
                    summary == null ? "" : summary.toString(),
                    categoryTitle
            ));
        }
    }

    private static String normalizeSearchText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static PreferenceCategory createCategory(
            Context context,
            PreferenceScreen screen,
            Section section
    ) {
        switch (section) {
            case FEED_FILTER:
                return new FeedFilterPreferenceCategory(context, screen);
            case FEED_NAVIGATION:
                return new FeedNavigationPreferenceCategory(context, screen);
            case INTERFACE:
                return new InterfacePreferenceCategory(context, screen);
            case COMMENTS:
                return new CommentsPreferenceCategory(context, screen);
            case DOWNLOADS:
                return new DownloadsPreferenceCategory(context, screen);
            case PLAYBACK:
                return new PlaybackPreferenceCategory(context, screen);
            case SCREEN_TIME:
                return new ScreenTimePreferenceCategory(context, screen);
            case BACKUP:
                // Its four rows need the fragment, so createSectionMenu adds them itself.
                return null;
            case INBOX:
                return new InboxPreferenceCategory(context, screen);
            case SHARE:
                return new SharePreferenceCategory(context, screen);
            case REGION:
                return new SimSpoofPreferenceCategory(context, screen);
            case PRIVACY:
                return new PrivacyPreferenceCategory(context, screen);
            case DIAGNOSTICS:
                return new DebugPreferenceCategory(context, screen);
            case BEHAVIOR:
            default:
                return new ExtensionPreferenceCategory(context, screen);
        }
    }

    private static void scrollToPreference(ListView list, String key) {
        if (list == null || list.getAdapter() == null) {
            return;
        }
        int dp24 = SettingsUi.dp(list.getContext(), 24);
        for (int position = 0; position < list.getAdapter().getCount(); position++) {
            Object item = list.getAdapter().getItem(position);
            if (item instanceof Preference && key.equals(((Preference) item).getKey())) {
                list.setSelectionFromTop(position, dp24);
                highlightRow(list, position);
                return;
            }
        }
    }

    private static void highlightRow(ListView list, int position) {
        list.post(() -> {
            int first = list.getFirstVisiblePosition();
            int index = position - first;
            if (index < 0 || index >= list.getChildCount()) return;
            android.view.View row = list.getChildAt(index);
            if (row == null) return;
            float scale = android.provider.Settings.Global.getFloat(
                    list.getContext().getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale > 0f) {
                int highlight = SettingsUi.rippleTint();
                android.graphics.drawable.ColorDrawable flash =
                        new android.graphics.drawable.ColorDrawable(highlight);
                row.setForeground(flash);
                row.postDelayed(() -> {
                    android.animation.ObjectAnimator fade = android.animation.ObjectAnimator
                            .ofInt(flash, "alpha", 0x26, 0);
                    fade.setDuration(600);
                    fade.addListener(new android.animation.AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(android.animation.Animator a) {
                            row.setForeground(null);
                        }
                    });
                    fade.start();
                }, 100);
            }
            row.performAccessibilityAction(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
    }

    private void createMasterMenu(Context context, PreferenceScreen screen) {
        screen.addPreference(SettingsHeaderPreference.master(context, this::closeSettings));
        boolean diagnosticsAvailable = DebugPreferenceCategory.isAvailable();
        SettingsStatusPreference status = new SettingsStatusPreference(
                context,
                diagnosticsAvailable ? () -> openSection(Section.DIAGNOSTICS) : null);
        status.setTurnBackOnAction(this::turnHushfeedBackOn);
        screen.addPreference(status);

        SettingsMenuPreference search = new SettingsMenuPreference(
                context,
                "Search settings",
                "Find a setting by name or description",
                SettingsMenuPreference.Icon.SEARCH,
                0,
                preference -> {
                    openSearch();
                    return true;
                }
        );
        search.setKey("action_search_settings");
        search.setOrder(-900);
        screen.addPreference(search);

        String releaseVersion = Utils.getPatchesReleaseVersion();
        if (ReleaseNotes.pending(context, releaseVersion)) {
            SettingsMenuPreference notes = new SettingsMenuPreference(
                    context,
                    L10n.t(context, "What's new"),
                    L10n.f(context, "Changes in Hushfeed %1$s", releaseVersion),
                    SettingsMenuPreference.Icon.NEWS,
                    0,
                    preference -> {
                        ReleaseNotes.show(context, releaseVersion,
                                () -> screen.removePreference(preference));
                        return true;
                    });
            notes.setKey(ReleaseNotes.KEY);
            notes.setOrder(-850);
            screen.addPreference(notes);
        }

        List<SettingsQuickActionsPreference.Action> quickRoutes = new ArrayList<>();
        if (FeedFilterPreferenceCategory.isAvailable()) {
            quickRoutes.add(new SettingsQuickActionsPreference.Action(
                    L10n.t(context, Section.FEED_FILTER.title),
                    SettingsQuickActionsPreference.FEED_TAG,
                    SettingsMenuPreference.Icon.FILTER,
                    () -> openSection(Section.FEED_FILTER)));
        }
        if (PrivacyPreferenceCategory.isAvailable()) {
            quickRoutes.add(new SettingsQuickActionsPreference.Action(
                    L10n.t(context, Section.PRIVACY.title),
                    SettingsQuickActionsPreference.PRIVACY_TAG,
                    SettingsMenuPreference.Icon.PRIVACY,
                    () -> openSection(Section.PRIVACY)));
        }
        if (ScreenTimePreferenceCategory.isAvailable()) {
            quickRoutes.add(new SettingsQuickActionsPreference.Action(
                    L10n.t(context, Section.SCREEN_TIME.title),
                    SettingsQuickActionsPreference.SCREEN_TIME_TAG,
                    SettingsMenuPreference.Icon.SCREEN_TIME,
                    () -> openSection(Section.SCREEN_TIME)));
        }
        if (!quickRoutes.isEmpty()) {
            screen.addPreference(new SettingsQuickActionsPreference(context, quickRoutes));
        }

        // Four groups, each a card of its own under a heading, and a heading only shows when
        // the bundle gives its group at least one page. Every page answers for itself, from
        // the page, so a row and its page cannot drift apart.
        boolean feed = FeedFilterPreferenceCategory.isAvailable()
                || FeedNavigationPreferenceCategory.isAvailable()
                || InterfacePreferenceCategory.isAvailable();
        if (feed) addHeading(screen, "Your feed");
        if (FeedFilterPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.FEED_FILTER, SettingsMenuPreference.Icon.FILTER);
        }
        if (FeedNavigationPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.FEED_NAVIGATION, SettingsMenuPreference.Icon.TABS);
        }
        if (InterfacePreferenceCategory.isAvailable()) {
            addMenu(screen, Section.INTERFACE, SettingsMenuPreference.Icon.LAYOUT);
        }

        boolean watching = PlaybackPreferenceCategory.isAvailable()
                || ScreenTimePreferenceCategory.isAvailable()
                || CommentsPreferenceCategory.isAvailable()
                || DownloadsPreferenceCategory.isAvailable()
                || SharePreferenceCategory.isAvailable()
                || InboxPreferenceCategory.isAvailable();
        if (watching) addHeading(screen, "Watching and sharing");
        if (PlaybackPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.PLAYBACK, SettingsMenuPreference.Icon.PLAYBACK);
        }
        if (ScreenTimePreferenceCategory.isAvailable()) {
            addMenu(screen, Section.SCREEN_TIME, SettingsMenuPreference.Icon.SCREEN_TIME);
        }
        if (CommentsPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.COMMENTS, SettingsMenuPreference.Icon.COMMENTS);
        }
        if (DownloadsPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.DOWNLOADS, SettingsMenuPreference.Icon.DOWNLOADS);
        }
        if (SharePreferenceCategory.isAvailable()) {
            addMenu(screen, Section.SHARE, SettingsMenuPreference.Icon.SHARE);
        }
        if (InboxPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.INBOX, SettingsMenuPreference.Icon.INBOX);
        }

        // Backup and restore needs no patch, so this group and its heading always exist.
        addHeading(screen, "Privacy and system");
        if (PrivacyPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.PRIVACY, SettingsMenuPreference.Icon.PRIVACY);
        }
        if (SimSpoofPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.REGION, SettingsMenuPreference.Icon.REGION);
        }
        if (ExtensionPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.BEHAVIOR, SettingsMenuPreference.Icon.BEHAVIOR);
        }
        screen.addPreference(pauseRow(context));

        if (FeatureGateLabRuntime.isInstalled()) {
            SettingsMenuPreference featureGateLab = new SettingsMenuPreference(
                    context,
                    L10n.t(context, "Feature Gate Lab"),
                    L10n.t(context, "Search and override gates"),
                    SettingsMenuPreference.Icon.LAB,
                    0,
                    preference -> {
                        FeatureGateLabFragment.open(getActivity());
                        return true;
                    }
            );
            // Stable key for settings search, UI automation and accessibility inspection.
            featureGateLab.setKey(FEATURE_GATE_LAB_KEY);
            screen.addPreference(featureGateLab);
        }

        if (diagnosticsAvailable) {
            addMenu(screen, Section.DIAGNOSTICS, SettingsMenuPreference.Icon.DIAGNOSTICS);
        }
        addMenu(screen, Section.BACKUP, SettingsMenuPreference.Icon.BACKUP);

        addHeading(screen, "About");
        screen.addPreference(new MorpheTikTokAboutPreference(context));
        // Under About, because that is where somebody looks for who wrote this. Morphe's
        // Section 7b asks that its notice reach the person using the software, and a file in the
        // repository does not reach them.
        screen.addPreference(new LicensesPreference(context));
    }

    /**
     * Pause Hushfeed. It takes the screen-time budget off with everything else, so a locked
     * day refuses it the same way it refuses every budget setting, and so does Wait a day to
     * loosen the budget, the way it refuses Start today over: paused from the next start, the
     * budget would be gone without waiting for anything.
     */
    private TogglePreference pauseRow(Context context) {
        TogglePreference pause = new TogglePreference(context, "Pause Hushfeed", PAUSE_SUMMARY,
                BaseSettings.PAUSED);
        pause.setOnPreferenceChangeListener((preference, value) -> {
            if (!Boolean.TRUE.equals(value)) return true;
            if (SessionBudget.lockedToday()) {
                Utils.showToastShort(L10n.f(context,
                        "Today's budget is locked. This can be changed again at %1$s.",
                        SessionLockOverlay.resetTimeLabel()));
                return false;
            }
            if (Settings.SESSION_BUDGET_WAIT_TO_LOOSEN.savedValue()) {
                Utils.showToastShort(L10n.t(context,
                        "Wait a day to loosen the budget is on, so Hushfeed stays on"));
                return false;
            }
            return true;
        });
        return pause;
    }

    /** The paused status card's action: safe mode, the switch and the marker file all go. */
    private void turnHushfeedBackOn() {
        Context context = getActivity();
        if (context == null) return;
        boolean pausedBefore = BaseSettings.PAUSED.savedValue();
        boolean safeModeBefore = BaseSettings.SAFE_MODE.savedValue();
        // What this process runs with goes on record before the save. The store's listener
        // reaches this page as soon as the save lands and reads the value "before" the change
        // from the setting, which by then already holds the new one, so it would record the
        // switch as never having changed and the restart it owes would go unsaid.
        noteRestartPending(BaseSettings.PAUSED, pausedBefore);
        noteRestartPending(BaseSettings.SAFE_MODE, safeModeBefore);
        boolean markerGone = HushfeedPause.turnBackOn(context);
        Preference row = findPreference(BaseSettings.PAUSED.key);
        if (row instanceof TogglePreference) ((TogglePreference) row).setChecked(false);
        noteRestartPending(BaseSettings.PAUSED, pausedBefore);
        noteRestartPending(BaseSettings.SAFE_MODE, safeModeBefore);
        if (markerGone && HushfeedPause.reason() == HushfeedPause.Reason.MARKER_FILE) {
            // The file is no setting, but the start it paused owes a restart all the same.
            AbstractPreferenceFragment.restartPending.add(HushfeedPause.MARKER_FILE_NAME);
            refreshRestartPending();
        }
        refreshStatusCard();
        if (markerGone) {
            SettingsActionBanner.showRestart(context,
                    L10n.t(context, "Hushfeed turns back on when TikTok restarts."));
        } else {
            SettingsActionBanner.showNotice(context, L10n.t(context,
                    "The file hushfeed-safe-mode couldn't be removed. Delete it from TikTok's folder under Android/data to turn Hushfeed back on."));
        }
    }

    /** The status card says again whether the next start still runs paused. */
    private void refreshStatusCard() {
        Preference status = findPreference(SettingsStatusPreference.KEY);
        if (status instanceof SettingsStatusPreference) ((SettingsStatusPreference) status).refresh();
    }

    /** A group heading on the master menu, which the list adapter treats as a card boundary. */
    private void addHeading(PreferenceScreen screen, String title) {
        screen.addPreference(new SectionHeadingPreference(getActivity(), title, true));
    }

    /** The master menu's rows and the section each one opens, for the badge refresh. */
    private final java.util.Map<SettingsMenuPreference, Section> menuSections =
            new java.util.LinkedHashMap<>();

    /** What each section binds, so counting a badge does not build the page again. */
    private final java.util.Map<Section, List<Setting<?>>> sectionSettings =
            new java.util.EnumMap<>(Section.class);

    private void addMenu(
            PreferenceScreen screen,
            Section section,
            SettingsMenuPreference.Icon icon
    ) {
        String description = L10n.t(getActivity(), section.description);
        if (description.endsWith(".")) {
            description = description.substring(0, description.length() - 1);
        }
        SettingsMenuPreference row = new SettingsMenuPreference(
                getActivity(),
                section.title,
                description,
                icon,
                countChangedSettings(getActivity(), section),
                preference -> {
                    openSection(section);
                    return true;
                }
        );
        screen.addPreference(row);
        menuSections.put(row, section);
    }

    private void createSectionMenu(Context context, PreferenceScreen screen, Section section) {
        screen.addPreference(SettingsHeaderPreference.section(context, section.title, this::navigateBack));
        screen.addPreference(SettingsHeaderPreference.caption(context, section.description));

        PreferenceCategory category = createCategory(context, screen, section);
        flattenCategory(screen, category);
        if (section == Section.BACKUP) SettingsBackupPreference.addTo(this, screen);
    }

    void refreshBackupSettings() { updateUIToSettingValues(); }

    /** Reconciles a preset's batch write with the rows and the restart debt on this page. */
    static void onSettingsBatchChanged(java.util.Map<Setting<?>, Object> previousValues) {
        TikTokPreferenceFragment current = activeFragment;
        if (current == null || !current.isAdded()) return;
        for (java.util.Map.Entry<Setting<?>, Object> entry : previousValues.entrySet()) {
            Setting<?> setting = entry.getKey();
            if (setting.rebootApp && !java.util.Objects.equals(entry.getValue(), setting.savedValue())) {
                current.noteRestartPending(setting, entry.getValue());
            }
        }
        current.updateUIToSettingValues();
        current.refreshRestartPending();
    }

    private static void flattenCategory(PreferenceScreen screen, PreferenceCategory category) {
        if (category == null) {
            return;
        }
        int count = category.getPreferenceCount();
        Preference[] children = new Preference[count];
        for (int index = 0; index < count; index++) {
            children[index] = category.getPreference(index);
        }
        for (Preference child : children) {
            category.removePreference(child);
        }
        screen.removePreference(category);
        for (int index = 0; index < children.length; index++) {
            Preference child = children[index];
            child.setOrder(index);
            screen.addPreference(child);
        }
    }

    private void openSearch() {
        FragmentManager manager = getFragmentManager();
        if (manager == null || getId() == 0) {
            Utils.showToastShort(L10n.t("Couldn't open settings search. Reopen settings and try again."));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_SEARCH, true);
        fragment.setArguments(arguments);
        manager.beginTransaction()
                .setTransition(android.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(getId(), fragment)
                .addToBackStack("search")
                .commit();
    }

    private void openSection(Section section) {
        openSection(section, null);
    }

    private void openSection(Section section, String targetKey) {
        FragmentManager manager = getFragmentManager();
        if (manager == null || getId() == 0) {
            Utils.showToastShort(L10n.t("Couldn't open that settings section. Reopen settings and try again."));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section.name());
        if (targetKey != null) {
            arguments.putString(ARG_TARGET_KEY, targetKey);
        }
        fragment.setArguments(arguments);

        manager.beginTransaction()
                .setTransition(android.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(getId(), fragment)
                .addToBackStack(section.name())
                .commit();
    }

    private void navigateBack() {
        FragmentManager manager = getFragmentManager();
        if (manager != null) {
            manager.popBackStack();
        }
    }

    private void hideKeyboard() {
        View view = getView();
        if (view == null) return;
        InputMethodManager manager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void closeSettings() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        activeFragment = this;
        refreshMenuBadges();
        // Back from a section onto the master menu: whatever was changed there is owed here.
        refreshRestartPending();
    }

    /**
     * Brings every badge on the master menu up to date.
     *
     * <p>openSection replaces this fragment and puts it on the back stack, so the master screen
     * and every one of its rows survives a trip into a section and back. Nothing rebuilt them on
     * the way back, which is why the numbers were the ones from whenever the screen was first
     * opened: turning on three filters and pressing back left the old count in place.
     */
    private void refreshMenuBadges() {
        Context context = getActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (context == null || screen == null || menuSections.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<SettingsMenuPreference, Section> entry : menuSections.entrySet()) {
            entry.getKey().setActiveCount(countChangedSettings(context, entry.getValue()));
        }
    }

    @Override
    public void onDestroy() {
        if (activeFragment == this) {
            activeFragment = null;
            // The pending key deliberately survives: the picker destroys this fragment while it
            // is open, and clearing it here dropped the folder the reader had just chosen.
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (SettingsBackupPreference.onResult(this, requestCode, resultCode, data)) return;
        if (requestCode != REQUEST_DOWNLOAD_PATH_FOLDER) {
            return;
        }

        String pendingKey = pendingDownloadPathKey;
        pendingDownloadPathKey = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Preference found = pendingKey == null ? null : findPreference(pendingKey);
        if (!(found instanceof DownloadPathPreference)) {
            app.morphe.extension.shared.Utils.showToastLong(
                    L10n.t("Couldn't tell which folder to update. Choose it again."));
            return;
        }
        DownloadPathPreference preference = (DownloadPathPreference) found;

        String relativePath = getRelativePrimaryStoragePath(data.getData());
        if (relativePath == null) {
            preference.applyPickedPath(null);
            return;
        }

        preference.applyPickedPath(relativePath);
    }

    private static String getRelativePrimaryStoragePath(Uri uri) {
        try {
            String treeDocumentId = DocumentsContract.getTreeDocumentId(uri);
            if (treeDocumentId == null) {
                return null;
            }

            String prefix = "primary:";
            if (!treeDocumentId.startsWith(prefix)) {
                return null;
            }

            return treeDocumentId.substring(prefix.length());
        } catch (Exception ignored) {
            return null;
        }
    }
}
