/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/TikTokPreferenceFragment.java
 */

package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.Window;
import android.widget.ListView;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
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
import app.morphe.extension.tiktok.settings.preference.categories.SharePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.SimSpoofPreferenceCategory;

@SuppressWarnings("deprecation")
public class TikTokPreferenceFragment extends AbstractPreferenceFragment {
    private static final int REQUEST_DOWNLOAD_PATH_FOLDER = 8841;
    private static final String ARG_SECTION = "morphe_settings_section";
    private static final String ARG_SEARCH = "morphe_settings_search";
    private static final String ARG_TARGET_KEY = "morphe_settings_target_key";
    private static TikTokPreferenceFragment activeFragment;
    /**
     * Which folder setting the picker was opened for, by key rather than by the preference
     * itself. The picker is a separate activity, so this one is routinely destroyed behind it
     * and the object that was waiting no longer belongs to the screen that comes back.
     */
    private static String pendingDownloadPathKey;
    private static final String PENDING_DOWNLOAD_PATH_STATE = "morphe_pending_download_path";
    private SettingsListAdapter styledAdapter;
    private PreferenceScreen searchScreen;
    private List<SearchResult> searchIndex;
    private final List<Preference> searchRows = new ArrayList<>();

    /**
     * Each section carries one sentence, used both as the subtitle on the home row and as the
     * caption at the top of its own page. There used to be two, and they disagreed: the
     * Downloads row promised quality and subtitles while its page said path and watermark.
     */
    private enum Section {
        FEED_FILTER("Feed filter", "Choose what reaches your feed"),
        FEED_NAVIGATION("Feed navigation", "Arrange your feed and bottom tabs"),
        INTERFACE("Interface", "Captions, gestures and on-screen controls"),
        COMMENTS("Comments and translation", "Filters, translation and copy options"),
        DOWNLOADS("Downloads", "Quality, files, subtitles and hand-off"),
        PLAYBACK("Playback", "Quality, speed and automatic advance"),
        INBOX("Inbox", "Choose which rows and controls appear"),
        SHARE("Share sheet", "People, shortcuts and sending controls"),
        REGION("Region settings", "Country and network preferences"),
        BEHAVIOR("App behavior", "Links, privacy and player tools"),
        DIAGNOSTICS("Diagnostics", "Backups and troubleshooting");

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

        SearchResult(Section section, String key, String title, String summary, String category) {
            this.section = section;
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.category = category;
        }

        String searchableText() {
            return title + " " + summary + " " + category;
        }

        String displaySummary() {
            if (summary.isEmpty()) {
                return category;
            }
            return category + " · " + summary;
        }
    }

    private static boolean isDarkModeEnabled(Context context) {
        final int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void openDownloadPathFolderPicker(DownloadPathPreference preference) {
        if (activeFragment == null) {
            app.morphe.extension.shared.Utils.showToastShort(L10n.t("Folder picker is not available"));
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
            app.morphe.extension.shared.Utils.showToastLong(L10n.t("Folder picker is not available on this device"));
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
                numberInputPreference.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, numberInputPreference.getValue());
            }
        } else if (pref instanceof CreatorListPreference) {
            CreatorListPreference creatorListPreference = (CreatorListPreference) pref;
            if (applySettingToPreference) {
                creatorListPreference.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, creatorListPreference.getValue());
            }
        } else if (pref instanceof RangeValuePreference) {
            RangeValuePreference rangeValuePref = (RangeValuePreference) pref;
            if (applySettingToPreference) {
                rangeValuePref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, rangeValuePref.getValue());
            }
        } else if (pref instanceof DownloadPathPreference) {
            DownloadPathPreference downloadPathPref = (DownloadPathPreference) pref;
            if (applySettingToPreference) {
                downloadPathPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, downloadPathPref.getValue());
            }
        } else if (pref instanceof TabSelectionPreference) {
            TabSelectionPreference tabSelectionPref = (TabSelectionPreference) pref;
            if (applySettingToPreference) {
                tabSelectionPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, tabSelectionPref.getValue());
            }
        } else {
            super.syncSettingWithPreference(pref, setting, applySettingToPreference);
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
    protected void initialize() {
        final var context = getActivity();
        activeFragment = this;

        confirmDialogTitle = L10n.t(getActivity(), "Do you wish to proceed?");

        Utils.setIsDarkModeEnabled(isDarkModeEnabled(context));

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
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView list = view.findViewById(android.R.id.list);
        if (list != null) {
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
        window.setStatusBarColor(SettingsUi.background());
        window.setNavigationBarColor(SettingsUi.background());
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

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PENDING_DOWNLOAD_PATH_STATE, pendingDownloadPathKey);
    }

    @Override public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        if (pendingDownloadPathKey == null && state != null) {
            pendingDownloadPathKey = state.getString(PENDING_DOWNLOAD_PATH_STATE);
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
        screen.addPreference(new SettingsSearchInputPreference(context, this::updateSearchResults));
        searchIndex = buildSearchIndex(context);
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

        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isEmpty()) {
            addSearchState("Type to search settings", "Search a title, description or category.");
            return;
        }

        String normalizedQuery = normalizeSearchText(trimmedQuery);
        List<SearchResult> matches = new ArrayList<>();
        for (SearchResult result : searchIndex) {
            if (normalizeSearchText(result.searchableText()).contains(normalizedQuery)) {
                matches.add(result);
            }
        }
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
                openSection(result.section, result.key);
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

    private List<SearchResult> buildSearchIndex(Context context) {
        List<SearchResult> results = new ArrayList<>();
        PreferenceScreen scratch = getPreferenceManager().createPreferenceScreen(context);
        for (Section section : Section.values()) {
            PreferenceCategory category = createCategory(context, scratch, section);
            if (category == null) {
                continue;
            }
            String categoryTitle = String.valueOf(category.getTitle());
            for (int index = 0; index < category.getPreferenceCount(); index++) {
                Preference preference = category.getPreference(index);
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
            scratch.removePreference(category);
        }
        return results;
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
            case INBOX:
                return new InboxPreferenceCategory(context, screen);
            case SHARE:
                return new SharePreferenceCategory(context, screen);
            case REGION:
                return new SimSpoofPreferenceCategory(context, screen);
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
        for (int position = 0; position < list.getAdapter().getCount(); position++) {
            Object item = list.getAdapter().getItem(position);
            if (item instanceof Preference && key.equals(((Preference) item).getKey())) {
                list.setSelection(position);
                return;
            }
        }
    }

    private void createMasterMenu(Context context, PreferenceScreen screen) {
        screen.addPreference(SettingsHeaderPreference.master(context, this::closeSettings));
        screen.addPreference(new SettingsMenuPreference(
                context,
                "Search settings",
                "Find a setting by title or description",
                SettingsMenuPreference.Icon.SEARCH,
                0,
                preference -> {
                    openSearch();
                    return true;
                }
        ));

        if (SettingsStatus.feedFilterEnabled) {
            addMenu(screen, Section.FEED_FILTER, SettingsMenuPreference.Icon.FILTER, countEnabled(
                    !Settings.BLOCKED_CAPTION_WORDS.get().trim().isEmpty(),
                    !Settings.BLOCKED_CREATORS.get().trim().isEmpty(),
                    !Settings.LOCAL_HIDDEN_CREATORS.get().trim().isEmpty(),
                    Settings.MAX_VIDEO_SECONDS.get() > 0,
                    Settings.MAX_PUBLICATION_AGE_DAYS.get() > 0,
                    Settings.MAX_VIEWS_PER_LIKE.get() > 0,
                    Settings.HIDE_PROMOTIONAL_MUSIC.get(),
                    Settings.HIDE_LIVE_REPLAYS.get(),
                    Settings.REMOVE_ADS.get(),
                    Settings.HIDE_SHOP.get(),
                    Settings.HIDE_LIVE.get(),
                    Settings.HIDE_STORY.get(),
                    Settings.HIDE_IMAGE.get(),
                    Settings.HIDE_PLAYLIST_BAR.get(),
                    Settings.HIDE_EVENT_BADGE.get(),
                    Settings.HIDE_INSERTED_CARDS.get(),
                    SettingsStatus.seenVideoFilterEnabled && Settings.HIDE_SEEN_VIDEOS.get()
            ));
        }
        if (SettingsStatus.feedNavigationEnabled) {
            addMenu(screen, Section.FEED_NAVIGATION, SettingsMenuPreference.Icon.TABS, countEnabled(
                    Settings.FEED_NAVIGATION.get(),
                    Settings.FEED_NAVIGATION_BLOCK_NEW_TABS.get(),
                    Settings.BOTTOM_NAVIGATION.get(),
                    Settings.BOTTOM_NAVIGATION_BLOCK_NEW_TABS.get(),
                    Settings.HIDE_TAKO_AI.get()
            ));
        }
        if (SettingsStatus.subtitleToolsEnabled || SettingsStatus.screenCaptureEnabled || SettingsStatus.automaticClearDisplayEnabled || SettingsStatus.doubleTapEnabled || SettingsStatus.longPressEnabled || SettingsStatus.confirmInteractionsEnabled || SettingsStatus.captchaPopupSuppressionEnabled
                || SettingsStatus.promotionalBannersEnabled
                || SettingsStatus.alwaysShowPublishDateEnabled
                || SettingsStatus.videoOverlaysEnabled
                || SettingsStatus.authorRegionEnabled
                || SettingsStatus.sensitiveWarningsEnabled
                || SettingsStatus.hideFeedLiveButtonEnabled
                || SettingsStatus.hideFeedSearchButtonEnabled
                || SettingsStatus.hideFeedFollowButtonEnabled
                || SettingsStatus.hideFeedSaveButtonEnabled
                || SettingsStatus.hideSearchSuggestionsEnabled) {
            addMenu(screen, Section.INTERFACE, SettingsMenuPreference.Icon.LAYOUT, countEnabled(
                    SettingsStatus.subtitleToolsEnabled && Settings.CAPTION_TEXT_SIZE.get() > 0,
                    SettingsStatus.subtitleToolsEnabled && !"default".equals(Settings.CAPTION_BACKGROUND.get()),
                    SettingsStatus.subtitleToolsEnabled && Settings.KEEP_CAPTIONS_CLEAR_DISPLAY.get(),
                    SettingsStatus.screenCaptureEnabled && Settings.ALLOW_SCREEN_CAPTURE.get(),
                    SettingsStatus.automaticClearDisplayEnabled && Settings.AUTOMATIC_CLEAR_DISPLAY.get(),
                    SettingsStatus.doubleTapEnabled && !"default".equals(Settings.DOUBLE_TAP_ACTION.get()),
                    SettingsStatus.longPressEnabled && !"default".equals(Settings.LONG_PRESS_ACTION.get()),
                    SettingsStatus.longPressEnabled && Settings.EDGE_SEEK.get(),
                    SettingsStatus.confirmInteractionsEnabled && Settings.CONFIRM_FOLLOW.get(),
                    SettingsStatus.confirmInteractionsEnabled && Settings.CONFIRM_LIKE.get(),
                    SettingsStatus.sensitiveWarningsEnabled && Settings.HIDE_SENSITIVE_WARNINGS.get(),
                    SettingsStatus.authorRegionEnabled && Settings.SHOW_AUTHOR_REGION.get(),
                    SettingsStatus.authorRegionEnabled && Settings.SHOW_AUTHOR_HANDLE.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_FEED_CAPTION.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_FEED_MUSIC.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_FEED_ACTION_BAR.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_FEED_SURVEYS.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_FOLLOW.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_LIKE.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_COMMENTS.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_FAVOURITE.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_MUSIC.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_SHARE.get(),
                    SettingsStatus.hideSearchSuggestionsEnabled && Settings.HIDE_SEARCH_SUGGESTIONS.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_RAIL_COUNTS.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_STATUS_BAR.get(),
                    SettingsStatus.videoOverlaysEnabled && Settings.HIDE_VISUAL_SEARCH.get(),
                    (SettingsStatus.videoOverlaysEnabled || SettingsStatus.hideFeedLiveButtonEnabled)
                            && Settings.HIDE_LIVE_ENTRANCE.get(),
                    SettingsStatus.hideFeedSearchButtonEnabled && Settings.HIDE_FEED_SEARCH_BUTTON.get(),
                    SettingsStatus.hideFeedFollowButtonEnabled && Settings.HIDE_FEED_FOLLOW_BUTTON.get(),
                    SettingsStatus.hideFeedSaveButtonEnabled && Settings.HIDE_FEED_SAVE_BUTTON.get(),
                    SettingsStatus.promotionalBannersEnabled && Settings.HIDE_HOMEPAGE_COIN.get(),
                    SettingsStatus.captchaPopupSuppressionEnabled && Settings.HIDE_CAPTCHA_POPUPS.get(),
                    SettingsStatus.alwaysShowPublishDateEnabled && Settings.ALWAYS_SHOW_PUBLISH_DATE.get()
            ));
        }
        if (SettingsStatus.commentToolsEnabled
                || SettingsStatus.commentTranslationEnabled
                || SettingsStatus.hideCommentQuickReactionsEnabled
                || SettingsStatus.copyCommentsWithoutUsernameEnabled) {
            addMenu(screen, Section.COMMENTS, SettingsMenuPreference.Icon.COMMENTS, countEnabled(
                    SettingsStatus.commentToolsEnabled && Settings.COMMENT_KEYWORD_FILTER.get(),
                    SettingsStatus.commentToolsEnabled && Settings.COMMENT_SEARCH.get(),
                    SettingsStatus.commentToolsEnabled && Settings.BLOCK_FROM_COMMENT.get(),
                    SettingsStatus.commentToolsEnabled && Settings.HIDE_COMMENT_MEDIA.get(),
                    SettingsStatus.hideCommentEggsEnabled && Settings.HIDE_COMMENT_EGGS.get(),
                    SettingsStatus.commentTranslationEnabled && Settings.COMMENT_BATCH_TRANSLATION.get(),
                    SettingsStatus.hideCommentQuickReactionsEnabled && Settings.HIDE_COMMENT_QUICK_REACTIONS.get(),
                    SettingsStatus.copyCommentsWithoutUsernameEnabled && Settings.COPY_COMMENTS_WITHOUT_USERNAME.get()
            ));
        }
        if (SettingsStatus.downloadEnabled || SettingsStatus.advancedDownloadsEnabled) {
            addMenu(screen, Section.DOWNLOADS, SettingsMenuPreference.Icon.DOWNLOADS, countEnabled(
                    SettingsStatus.subtitleToolsEnabled && Settings.DOWNLOAD_SUBTITLES.get(),
                    SettingsStatus.advancedDownloadsEnabled && !"auto".equals(Settings.DOWNLOAD_VIDEO_QUALITY.get()),
                    SettingsStatus.advancedDownloadsEnabled && Settings.DOWNLOAD_ORIGINAL_PHOTOS.get(),
                    SettingsStatus.advancedDownloadsEnabled && Settings.DOWNLOAD_AUDIO_TRACK.get(),
                    SettingsStatus.advancedDownloadsEnabled && Settings.DOWNLOAD_WITHOUT_SOUND.get(),
                    SettingsStatus.advancedDownloadsEnabled
                            && !Settings.EXTERNAL_DOWNLOADER_PACKAGE.get().trim().isEmpty(),
                    SettingsStatus.advancedDownloadsEnabled && Settings.SAVE_PROFILE_PICTURE.get(),
                    SettingsStatus.advancedDownloadsEnabled && Settings.SAVE_STORY.get(),
                    SettingsStatus.downloadEnabled && Settings.DOWNLOAD_WATERMARK.get(),
                    SettingsStatus.downloadEnabled && Settings.CUSTOM_OFFLINE_VIDEOS.get(),
                    SettingsStatus.downloadEnabled && !"mp4".equals(Settings.DOWNLOAD_STICKER_FORMAT.get())
            ));
        }
        if (SettingsStatus.playbackQualityEnabled || SettingsStatus.playbackSpeedEnabled
                || SettingsStatus.autoAdvanceEnabled || SettingsStatus.videoFitEnabled) {
            addMenu(screen, Section.PLAYBACK, SettingsMenuPreference.Icon.PLAYBACK,
                    countEnabled(SettingsStatus.playbackQualityEnabled && !"auto".equals(Settings.PLAYBACK_QUALITY.get()),
                            SettingsStatus.playbackQualityEnabled
                                    && !"off".equals(Settings.PLAYBACK_QUALITY_METERED.get()),
                            SettingsStatus.playbackSpeedEnabled && Settings.DEFAULT_SPEED_ENABLED.get(),
                            SettingsStatus.playbackSpeedEnabled && !Settings.CUSTOM_SPEEDS.get().trim().isEmpty(),
                            SettingsStatus.autoAdvanceEnabled && Settings.AUTO_ADVANCE.get(),
                            SettingsStatus.autoAdvanceEnabled && Settings.AUTO_ADVANCE_LIMIT.get() > 0,
                            SettingsStatus.videoFitEnabled && Settings.FIT_VIDEO_TO_SCREEN.get()));
        }
        if (SettingsStatus.inboxFilterEnabled
                || SettingsStatus.hideSuggestedAccountsEnabled
                || SettingsStatus.hideInboxStoriesEnabled
                || SettingsStatus.expandActivityListEnabled
                || SettingsStatus.notificationControlsEnabled) {
            addMenu(screen, Section.INBOX, SettingsMenuPreference.Icon.INBOX, countEnabled(
                    (SettingsStatus.inboxFilterEnabled || SettingsStatus.hideInboxStoriesEnabled)
                            && Settings.HIDE_INBOX_STORIES.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_NEW_FOLLOWERS.get(),
                    SettingsStatus.notificationControlsEnabled && Settings.HIDE_FOLLOWER_NOTIFICATIONS.get(),
                    SettingsStatus.notificationControlsEnabled && Settings.HIDE_MESSAGE_STREAKS.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_ACTIVITY.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_ARCHIVE.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_TAKO.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_SHOP.get(),
                    (SettingsStatus.inboxFilterEnabled || SettingsStatus.hideSuggestedAccountsEnabled)
                            && Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_MESSAGE_REQUESTS.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_CONVERSATIONS.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_ADD_PEOPLE.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_SEARCH.get(),
                    SettingsStatus.inboxFilterEnabled && Settings.HIDE_INBOX_ACTIVITY_STATUS.get(),
                    SettingsStatus.expandActivityListEnabled && Settings.EXPAND_ACTIVITY_LIST.get()
            ));
        }
        if (SettingsStatus.shareSheetEnabled) {
            addMenu(screen, Section.SHARE, SettingsMenuPreference.Icon.SHARE, countEnabled(
                    Settings.SHARE_CONFIRM_SEND.get(),
                    Settings.HIDE_SHARE_CONTACTS.get(),
                    Settings.HIDE_SHARE_CHANNELS.get(),
                    Settings.HIDE_SHARE_ACTIONS.get(),
                    !Settings.SHARE_HIDDEN_ITEMS.get().trim().isEmpty()
            ));
        }
        if (SettingsStatus.simSpoofEnabled) {
            addMenu(screen, Section.REGION, SettingsMenuPreference.Icon.REGION, countEnabled(
                    Settings.SIM_SPOOF.get(),
                    SettingsStatus.regionSpoofEnabled && Settings.SIM_SPOOF.get() && Settings.REGION_SPOOF.get(),
                    SettingsStatus.regionSpoofEnabled && Settings.SIM_SPOOF.get() && Settings.REGION_SPOOF.get() && Settings.REGION_STORE_SPOOF.get()
            ));
        }

        if (hasBehaviorSettings()) {
            addMenu(screen, Section.BEHAVIOR, SettingsMenuPreference.Icon.BEHAVIOR,
                    countBehaviorSettings());
        }

        if (FeatureGateLabRuntime.isInstalled()) {
            screen.addPreference(new SettingsMenuPreference(
                    context,
                    "Feature Gate Lab",
                    "Search and override gate flags",
                    SettingsMenuPreference.Icon.LAB,
                    0,
                    preference -> {
                        FeatureGateLabFragment.open(getActivity());
                        return true;
                    }
            ));
        }

        if (SettingsStatus.featureGateRecorderEnabled) {
            screen.addPreference(new FeatureGateRecorderPreference(context));
        }

        addMenu(screen, Section.DIAGNOSTICS, SettingsMenuPreference.Icon.DIAGNOSTICS, countEnabled(
                SettingsStatus.diagnosticsEnabled && BaseSettings.DEBUG.get(),
                SettingsStatus.diagnosticsEnabled && BaseSettings.CAPTURE_JAVA_CRASHES.get()
        ));

        screen.addPreference(new MorpheTikTokAboutPreference(context));
    }

    private void addMenu(
            PreferenceScreen screen,
            Section section,
            SettingsMenuPreference.Icon icon,
            int activeCount
    ) {
        String description = L10n.t(getActivity(), section.description);
        if (description.endsWith(".")) {
            description = description.substring(0, description.length() - 1);
        }
        screen.addPreference(new SettingsMenuPreference(
                getActivity(),
                section.title,
                description,
                icon,
                activeCount,
                preference -> {
                    openSection(section);
                    return true;
                }
        ));
    }

    private void createSectionMenu(Context context, PreferenceScreen screen, Section section) {
        screen.addPreference(SettingsHeaderPreference.section(context, section.title, this::navigateBack));
        screen.addPreference(SettingsHeaderPreference.caption(context, section.description));

        PreferenceCategory category = createCategory(context, screen, section);
        flattenCategory(screen, category);
        if (section == Section.DIAGNOSTICS) SettingsBackupPreference.addTo(this, screen);
    }

    void refreshBackupSettings() { updateUIToSettingValues(); }

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
            Utils.showToastShort(L10n.t("Could not open settings search"));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_SEARCH, true);
        fragment.setArguments(arguments);
        manager.beginTransaction()
                .setCustomAnimations(
                        android.R.animator.fade_in,
                        android.R.animator.fade_out,
                        android.R.animator.fade_in,
                        android.R.animator.fade_out
                )
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
            Utils.showToastShort(L10n.t("Could not open settings section"));
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
                .setCustomAnimations(
                        android.R.animator.fade_in,
                        android.R.animator.fade_out,
                        android.R.animator.fade_in,
                        android.R.animator.fade_out
                )
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

    private void closeSettings() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    /**
     * Whether App behavior has anything in it. Every row on that page belongs to a patch, so
     * with none of them installed the row would open a page with nothing but its heading.
     */
    private static boolean hasBehaviorSettings() {
        return SettingsStatus.foldableSplitViewEnabled
                || SettingsStatus.sanitizeShareUrlsEnabled
                || SettingsStatus.externalBrowserEnabled
                || SettingsStatus.showSeekbarEnabled
                || SettingsStatus.seekbarThumbnailEnabled
                || SettingsStatus.stopVideoLoopingEnabled
                || SettingsStatus.resumeVideoAfterScrollEnabled
                || SettingsStatus.longPressSpeedLockEnabled
                || SettingsStatus.disableLongPressQuickShareEnabled
                || SettingsStatus.disableLongPressRepostEnabled
                || SettingsStatus.disableTelemetryEnabled
                || SettingsStatus.ghostModeEnabled
                || SettingsStatus.blockAuthorEnabled
                || SettingsStatus.notInterestedEnabled
                || SettingsStatus.nonPersonalizedSearchEnabled
                || SettingsStatus.liveSearchEnabled
                || SettingsStatus.duetStitchEnabled
                || SettingsStatus.refreshRateEnabled;
    }

    private int countBehaviorSettings() {
        int count = countEnabled(
                SettingsStatus.refreshRateEnabled && Settings.UNCAP_REFRESH_RATE.get(),
                SettingsStatus.duetStitchEnabled && Settings.ALLOW_DUET_AND_STITCH.get(),
                SettingsStatus.foldableSplitViewEnabled && Settings.FOLDABLE_SPLIT_VIEW.get(),
                SettingsStatus.blockAuthorEnabled && Settings.BLOCK_AUTHOR_BUTTON.get(),
                SettingsStatus.notInterestedEnabled && Settings.NOT_INTERESTED_BUTTON.get(),
                SettingsStatus.sanitizeShareUrlsEnabled && BaseSettings.SANITIZE_SHARING_LINKS.get(),
                SettingsStatus.sanitizeShareUrlsEnabled
                        && !ShareUrlSanitizer.domain(Settings.CUSTOM_SHARE_DOMAIN.get()).isEmpty(),
                SettingsStatus.showSeekbarEnabled && Settings.SHOW_SEEKBAR.get()
        );
        if (SettingsStatus.externalBrowserEnabled && Settings.OPEN_EXTERNAL_LINKS.get()) {
            count++;
        }
        if (SettingsStatus.stopVideoLoopingEnabled && Settings.STOP_VIDEO_LOOPING.get()) {
            count++;
        }
        if (SettingsStatus.resumeVideoAfterScrollEnabled && Settings.RESUME_VIDEO_AFTER_SCROLL.get()) {
            count++;
        }
        if (SettingsStatus.longPressSpeedLockEnabled && Settings.ENABLE_LONG_PRESS_SPEED_LOCK.get()) {
            count++;
        }
        if (SettingsStatus.disableLongPressQuickShareEnabled
                && Settings.DISABLE_LONG_PRESS_QUICK_SHARE.get()) {
            count++;
        }
        if (SettingsStatus.disableLongPressRepostEnabled
                && Settings.DISABLE_LONG_PRESS_REPOST.get()) {
            count++;
        }
        if (SettingsStatus.disableTelemetryEnabled && Settings.DISABLE_ANALYTICS.get()) {
            count++;
        }
        if (SettingsStatus.ghostModeEnabled && Settings.GHOST_MODE.get()) {
            count++;
        }
        return count;
    }

    private static int countEnabled(boolean... values) {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void onResume() {
        super.onResume();
        activeFragment = this;
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
                    L10n.t("Could not tell which folder to update. Choose it again."));
            return;
        }
        DownloadPathPreference preference = (DownloadPathPreference) found;

        String relativePath = getRelativePrimaryStoragePath(data.getData());
        if (relativePath == null) {
            app.morphe.extension.shared.Utils.showToastLong(L10n.t("Only internal storage folders are supported"));
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
