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
    private static TikTokPreferenceFragment activeFragment;
    private static DownloadPathPreference pendingDownloadPathPreference;
    private SettingsListAdapter styledAdapter;

    private enum Section {
        FEED_FILTER("Feed filter", "Ads, Shop, livestreams, and view limits."),
        FEED_NAVIGATION("Feed navigation", "Feed tabs, bottom tabs, and Tako AI."),
        INTERFACE("Interface", "Promotions, popups, publish dates, and feed controls."),
        COMMENTS("Comments and translation", "Auto translate, quick reactions, and copy options."),
        DOWNLOADS("Downloads", "Path, watermark, and offline videos."),
        PLAYBACK("Playback", "Video quality, speed and automatic advance."),
        INBOX("Inbox", "Rows, stories tray, and header controls."),
        SHARE("Share sheet", "Confirm before sending, and hidden people and options."),
        REGION("Region settings", "Country, operator, locale and timezone."),
        BEHAVIOR("App behavior", "Sharing, playback, and gestures."),
        DIAGNOSTICS("Diagnostics", "Settings backup and diagnostic reports.");

        final String title;
        final String description;

        Section(String title, String description) {
            this.title = title;
            this.description = description;
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

        pendingDownloadPathPreference = preference;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activeFragment.startActivityForResult(intent, REQUEST_DOWNLOAD_PATH_FOLDER);
        } catch (ActivityNotFoundException exception) {
            pendingDownloadPathPreference = null;
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
        } else if (pref instanceof LanguageSelectionPreference) {
            LanguageSelectionPreference languagePreference = (LanguageSelectionPreference) pref;
            if (applySettingToPreference) {
                languagePreference.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, languagePreference.getValue());
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
        if (pref instanceof RangeValuePreference) {
            return defaultValue.equals(((RangeValuePreference) pref).getValue());
        }
        if (pref instanceof DownloadPathPreference) {
            return defaultValue.equals(((DownloadPathPreference) pref).getValue());
        }
        if (pref instanceof TabSelectionPreference) {
            return defaultValue.equals(((TabSelectionPreference) pref).getValue());
        }
        if (pref instanceof LanguageSelectionPreference) {
            return defaultValue.equals(((LanguageSelectionPreference) pref).getValue());
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
        if (section == null) {
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

    @Override public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        ListView list = getView().findViewById(android.R.id.list);
        if (list != null && list.getAdapter() != null) {
            styledAdapter = new SettingsListAdapter(list.getAdapter());
            list.setAdapter(styledAdapter);
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

    private void createMasterMenu(Context context, PreferenceScreen screen) {
        screen.addPreference(SettingsHeaderPreference.master(context, this::closeSettings));

        if (SettingsStatus.feedFilterEnabled) {
            addMenu(screen, Section.FEED_FILTER, SettingsMenuPreference.Icon.FILTER, countEnabled(
                    !Settings.BLOCKED_CAPTION_WORDS.get().trim().isEmpty(),
                    !Settings.BLOCKED_CREATORS.get().trim().isEmpty(),
                    Settings.MAX_VIDEO_SECONDS.get() > 0,
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
                            SettingsStatus.playbackSpeedEnabled && Settings.DEFAULT_SPEED_ENABLED.get(),
                            SettingsStatus.playbackSpeedEnabled && !Settings.CUSTOM_SPEEDS.get().trim().isEmpty(),
                            SettingsStatus.autoAdvanceEnabled && Settings.AUTO_ADVANCE.get(),
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

        addMenu(screen, Section.BEHAVIOR, SettingsMenuPreference.Icon.BEHAVIOR, countBehaviorSettings());

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
        String menuDescription;
        switch (section) {
            case FEED_FILTER: menuDescription = "Choose what reaches your feed"; break;
            case FEED_NAVIGATION: menuDescription = "Arrange your feed and bottom tabs"; break;
            case INTERFACE: menuDescription = "Captions, gestures and on-screen controls"; break;
            case COMMENTS: menuDescription = "Filters, translation and copy options"; break;
            case DOWNLOADS: menuDescription = "Quality, files and subtitles"; break;
            case PLAYBACK: menuDescription = "Quality, speed and automatic advance"; break;
            case INBOX: menuDescription = "Choose which rows and controls appear"; break;
            case SHARE: menuDescription = "People, shortcuts and sending controls"; break;
            case REGION: menuDescription = "Country and network preferences"; break;
            case BEHAVIOR: menuDescription = "Links, privacy and player tools"; break;
            case DIAGNOSTICS: menuDescription = "Backups and troubleshooting"; break;
            default: menuDescription = section.description;
        }
        String description = L10n.t(getActivity(), menuDescription);
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

        PreferenceCategory category;
        switch (section) {
            case FEED_FILTER:
                category = new FeedFilterPreferenceCategory(context, screen);
                break;
            case FEED_NAVIGATION:
                category = new FeedNavigationPreferenceCategory(context, screen);
                break;
            case INTERFACE:
                category = new InterfacePreferenceCategory(context, screen);
                break;
            case COMMENTS:
                category = new CommentsPreferenceCategory(context, screen);
                break;
            case DOWNLOADS:
                category = new DownloadsPreferenceCategory(context, screen);
                break;
            case PLAYBACK:
                category = new PlaybackPreferenceCategory(context, screen);
                break;
            case INBOX:
                category = new InboxPreferenceCategory(context, screen);
                break;
            case SHARE:
                category = new SharePreferenceCategory(context, screen);
                break;
            case REGION:
                category = new SimSpoofPreferenceCategory(context, screen);
                break;
            case DIAGNOSTICS:
                category = new DebugPreferenceCategory(context, screen);
                break;
            case BEHAVIOR:
            default:
                category = new ExtensionPreferenceCategory(context, screen);
                break;
        }
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

    private void openSection(Section section) {
        FragmentManager manager = getFragmentManager();
        if (manager == null || getId() == 0) {
            Utils.showToastShort(L10n.t("Could not open settings section"));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section.name());
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

    private int countBehaviorSettings() {
        int count = countEnabled(
                SettingsStatus.refreshRateEnabled && Settings.UNCAP_REFRESH_RATE.get(),
                SettingsStatus.foldableSplitViewEnabled && Settings.FOLDABLE_SPLIT_VIEW.get(),
                SettingsStatus.blockAuthorEnabled && Settings.BLOCK_AUTHOR_BUTTON.get(),
                SettingsStatus.notInterestedEnabled && Settings.NOT_INTERESTED_BUTTON.get(),
                BaseSettings.SANITIZE_SHARING_LINKS.get(),
                !ShareUrlSanitizer.domain(Settings.CUSTOM_SHARE_DOMAIN.get()).isEmpty(),
                Settings.SHOW_SEEKBAR.get()
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
            pendingDownloadPathPreference = null;
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

        DownloadPathPreference preference = pendingDownloadPathPreference;
        pendingDownloadPathPreference = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || preference == null) {
            return;
        }

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
