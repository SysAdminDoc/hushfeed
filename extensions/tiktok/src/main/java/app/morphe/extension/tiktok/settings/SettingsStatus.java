/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/SettingsStatus.java
 */

package app.morphe.extension.tiktok.settings;

public class SettingsStatus {
    public static boolean regionSpoofEnabled;
    public static void enableRegionSpoof() { regionSpoofEnabled = true; }
    public static boolean foldableSplitViewEnabled;
    public static void enableFoldableSplitView() { foldableSplitViewEnabled = true; }
    public static boolean subtitleToolsEnabled;
    public static void enableSubtitleTools() { subtitleToolsEnabled = true; }
    public static boolean screenCaptureEnabled;
    public static void enableScreenCapture() { screenCaptureEnabled = true; }
    public static boolean featureGateRecorderEnabled;
    public static void enableFeatureGateRecorder() { featureGateRecorderEnabled = true; }
    public static boolean automaticClearDisplayEnabled;
    public static void enableAutomaticClearDisplay() { automaticClearDisplayEnabled = true; }
    public static boolean playbackQualityEnabled;
    public static boolean playbackSpeedEnabled;
    public static void enablePlaybackSpeed() { playbackSpeedEnabled = true; }
    public static boolean autoAdvanceEnabled;
    public static void enableAutoAdvance() { autoAdvanceEnabled = true; }
    public static void enablePlaybackQuality() { playbackQualityEnabled = true; }
    public static boolean advancedDownloadsEnabled;
    public static void enableAdvancedDownloads() { advancedDownloadsEnabled = true; }
    public static boolean doubleTapEnabled;
    public static void enableDoubleTap() { doubleTapEnabled = true; }
    public static boolean longPressEnabled;
    public static void enableLongPress() { longPressEnabled = true; }
    public static boolean confirmInteractionsEnabled;
    public static void enableConfirmInteractions() { confirmInteractionsEnabled = true; }
    public static boolean feedFilterEnabled = false;
    public static boolean feedNavigationEnabled = false;
    public static boolean commentTranslationEnabled = false;
    public static boolean hideCommentQuickReactionsEnabled = false;
    public static boolean copyCommentsWithoutUsernameEnabled = false;
    public static boolean downloadEnabled = false;
    public static boolean customOfflineVideosEnabled = false;
    public static boolean simSpoofEnabled = false;
    public static boolean captchaPopupSuppressionEnabled = false;
    public static boolean promotionalBannersEnabled = false;
    public static boolean longPressSpeedLockEnabled = false;
    public static boolean disableLongPressQuickShareEnabled = false;
    public static boolean disableLongPressRepostEnabled = false;
    public static boolean nonPersonalizedSearchEnabled = false;
    public static boolean hideSearchSuggestionsEnabled = false;
    public static boolean liveSearchEnabled = false;
    public static boolean seekbarThumbnailEnabled = false;
    public static boolean stopVideoLoopingEnabled = false;
    public static boolean resumeVideoAfterScrollEnabled = false;
    public static boolean externalBrowserEnabled = false;
    public static boolean alwaysShowPublishDateEnabled = false;
    public static boolean diagnosticsEnabled = false;
    public static boolean blockAuthorEnabled = false;
    public static boolean authorRegionEnabled = false;
    public static boolean sensitiveWarningsEnabled = false;
    public static boolean notInterestedEnabled = false;

    public static void enableNotInterested() {
        notInterestedEnabled = true;
    }
    public static boolean inboxFilterEnabled = false;
    public static boolean videoFitEnabled = false;
    public static boolean refreshRateEnabled = false;
    public static boolean duetStitchEnabled = false;
    public static boolean notificationControlsEnabled = false;
    public static boolean hideSuggestedAccountsEnabled = false;
    public static boolean hideInboxStoriesEnabled = false;
    public static boolean expandActivityListEnabled = false;
    public static boolean commentToolsEnabled = false;
    public static boolean hideCommentEggsEnabled = false;
    public static boolean videoOverlaysEnabled = false;
    public static boolean shareSheetEnabled = false;
    public static boolean seenVideoFilterEnabled = false;
    public static boolean ghostModeEnabled = false;
    public static boolean disableTelemetryEnabled = false;
    public static boolean hideFeedFollowButtonEnabled = false;
    public static boolean hideFeedSaveButtonEnabled = false;
    public static boolean hideFeedLiveButtonEnabled = false;
    public static boolean hideFeedSearchButtonEnabled = false;
    public static boolean showSeekbarEnabled = false;
    public static boolean sanitizeShareUrlsEnabled = false;

    public static void enableFeedFilter() {
        feedFilterEnabled = true;
    }

    public static void enableFeedNavigation() {
        feedNavigationEnabled = true;
    }

    public static void enableCommentTranslation() {
        commentTranslationEnabled = true;
    }

    public static void enableHideCommentQuickReactions() {
        hideCommentQuickReactionsEnabled = true;
    }

    public static void enableCopyCommentsWithoutUsername() {
        copyCommentsWithoutUsernameEnabled = true;
    }

    public static void enableDownload() {
        downloadEnabled = true;
    }

    public static void enableCustomOfflineVideos() {
        customOfflineVideosEnabled = true;
    }

    public static void enableSimSpoof() {
        simSpoofEnabled = true;
    }

    public static void enableCaptchaPopupSuppression() {
        captchaPopupSuppressionEnabled = true;
    }

    public static void enablePromotionalBanners() {
        promotionalBannersEnabled = true;
    }

    public static void enableLongPressSpeedLock() {
        longPressSpeedLockEnabled = true;
    }

    public static void enableDisableLongPressQuickShare() {
        disableLongPressQuickShareEnabled = true;
    }

    public static void enableDisableLongPressRepost() {
        disableLongPressRepostEnabled = true;
    }

    public static void enableNonPersonalizedSearch() {
        nonPersonalizedSearchEnabled = true;
    }

    public static void enableHideSearchSuggestions() {
        hideSearchSuggestionsEnabled = true;
    }

    public static void enableLiveSearch() {
        liveSearchEnabled = true;
    }

    public static void enableSeekbarThumbnail() {
        seekbarThumbnailEnabled = true;
    }

    public static void enableShowSeekbar() {
        showSeekbarEnabled = true;
    }

    public static void enableSanitizeShareUrls() {
        sanitizeShareUrlsEnabled = true;
    }

    public static void enableStopVideoLooping() {
        stopVideoLoopingEnabled = true;
    }

    public static void enableResumeVideoAfterScroll() {
        resumeVideoAfterScrollEnabled = true;
    }

    public static void enableExternalBrowser() {
        externalBrowserEnabled = true;
    }

    public static void enableAlwaysShowPublishDate() {
        alwaysShowPublishDateEnabled = true;
    }

    public static void enableDiagnostics() {
        diagnosticsEnabled = true;
    }

    public static void enableSensitiveWarnings() {
        sensitiveWarningsEnabled = true;
    }

    public static void enableAuthorRegion() {
        authorRegionEnabled = true;
    }

    public static void enableBlockAuthor() {
        blockAuthorEnabled = true;
    }

    public static void enableHideSuggestedAccounts() {
        hideSuggestedAccountsEnabled = true;
    }

    public static void enableHideInboxStories() {
        hideInboxStoriesEnabled = true;
    }

    public static void enableExpandActivityList() {
        expandActivityListEnabled = true;
    }

    public static void enableDuetStitch() {
        duetStitchEnabled = true;
    }

    public static void enableRefreshRate() {
        refreshRateEnabled = true;
    }

    public static void enableVideoFit() {
        videoFitEnabled = true;
    }

    public static void enableNotificationControls() {
        notificationControlsEnabled = true;
    }

    public static void enableInboxFilter() {
        inboxFilterEnabled = true;
    }

    public static void enableCommentTools() {
        commentToolsEnabled = true;
    }

    public static void enableHideCommentEggs() {
        hideCommentEggsEnabled = true;
    }

    public static void enableSeenVideoFilter() {
        seenVideoFilterEnabled = true;
    }

    public static void enableGhostMode() {
        ghostModeEnabled = true;
    }

    public static void enableDisableTelemetry() {
        disableTelemetryEnabled = true;
    }

    public static void enableHideFeedFollowButton() {
        hideFeedFollowButtonEnabled = true;
    }

    public static void enableHideFeedSaveButton() {
        hideFeedSaveButtonEnabled = true;
    }

    public static void enableHideFeedLiveButton() {
        hideFeedLiveButtonEnabled = true;
    }

    public static void enableHideFeedSearchButton() {
        hideFeedSearchButtonEnabled = true;
    }

    public static void enableShareSheet() {
        shareSheetEnabled = true;
    }

    public static void enableVideoOverlays() {
        videoOverlaysEnabled = true;
    }

    public static void load() {
    }
}
