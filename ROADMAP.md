# ROADMAP

Findings from the 2026-09-05 audit that were not fixed in that pass, plus upstream
requests worth building. P0 is broken, P3 is nice to have.

- [ ] P1 - Filter the Inbox at the data level
  Why: rows are hidden as they lay out, so a row can flash for a frame, and the five system
  rows match on English titles because they share one container id.
  Where: extensions/tiktok/.../inbox/InboxFilter.java; needs the inbox adapter located in the APK.
  Note (research 2026-09-05): hxreborn hooks the widget injectors instead of the adapter
  (`*WidgetV2Injector.enable()`), which covers stories and suggested accounts with no flash.
  See the Research-Driven Additions below; after that port, only the five system rows remain here.
- [ ] P2 - Draw the block glyph instead of relying on the font having U+2298
  Why: a font without the glyph shows a tofu box.
  Where: extensions/tiktok/.../blockauthor/BlockAuthorOverlay.java
- [ ] P2 - "Not interested" one-tap button beside the block button
  Why: same shape as the block button. Confirmed endpoint /aweme/v1/commit/dislike/item/, but it
  sits behind an obfuscated Kotlin suspend interface (X.0MlK on 46.2.3) taking a Map body and a
  Continuation, which churns every build. Needs a Proxy-built Continuation and a fingerprint on
  the endpoint string rather than the class name.
  Where: new patch; scratchpad tt/out3/sources/X/InterfaceC17470MlK.java shows the shape.
  Note (research 2026-09-05): no ReVanced or Morphe patch calls a suspend function today. The
  technique is a `java.lang.reflect.Proxy` Continuation (getContext returns
  EmptyCoroutineContext.INSTANCE, resumeWith receives a kotlin.Result) and a fingerprint that
  scans `classDef.methods[].annotations[].elements[].value` for "/aweme/v1/commit/dislike/item/"
  (present in one 46.2.3 dex) instead of the class name. `/aweme/v1/commit/item/skip/` is absent.
- [ ] P3 - Bulk unfollow / follower management (upstream #108)
  Why: IUserService follow calls are already mapped by FollowDiagnosticsPatch. Simplest shape is
  the Clear all pattern: an injected control on the Following list that presses each Following
  button in turn. Needs a uiautomator dump of that screen for the row and button ids.
  Where: new patch; extensions/tiktok/.../inbox/InboxFilter.java shows the pattern.
- [ ] P2 - AMOLED black theme (upstream #72, 8 reactions)
  Why: the user's default for Android; needs the theme resource hook.
  Note (research 2026-09-05): resource patches do run on-device (manager Session.kt builds
  PatcherConfig without aapt; the patcher picks ResourceMode.FULL through ARSCLib), and our
  AntiRecordingPatch is already a resourcePatch. Copy Morphe's shared/layout/theme/BaseThemePatch
  shape: append <color> entries to res/values/colors.xml and values-night, restyle the window
  background, and expose a colorOption once the patcher pin is 1.12.0. First step is one
  throwaway build that edits colors.xml to prove the round trip on the 590 MB APK.
- [ ] P3 - Localise the five inbox system labels
  Why: they only match in English.
  Where: InboxFilter.matchesSystemLabel
- [ ] P2 - Verify 0.9.0 to 0.12.0 on device
  Why: the sound filter, feed switches and comment keyword filter have not run on a device;
  the thumbs down block (0.12.0), the visual search hide, the Live entrance hide and the share
  sheet confirm step have not been seen working. The 0.11.0 comment buttons did render (seen
  in a panel dump). Confirm each once, then remove this item.
  Where: extensions/tiktok/.../feedfilter/SoundFilter.java, ContentMarkerFilters.java,
  comment/CommentTools.java, blockauthor/BlockAuthorOverlay.java
- [ ] P2 - Replace the fragment back stack guard in FeedVisibility
  Why: getSupportFragmentManager cannot be found by reflection on 46.2.3 (logcat: "Fragment
  back stack not readable"), so backing out of a grid video to its profile still leaves the
  block button showing until the bottom navigation reappears. Needs a different "page popped"
  signal: a hook on TikTok's own back handling, or a fingerprint on the detail page's
  lifecycle method.
  Where: extensions/tiktok/.../blockauthor/FeedVisibility.java backStackDepth()

## Research-Driven Additions

From the 2026-09-05 research pass (see RESEARCH.md, gitignored). Candidate patches come from
upstream's dev branch, upstream PRs #143 and #145, and the hxreborn, BlueDragon4251 and
eduardo3677-ai forks; every fingerprint anchor was checked against the 46.2.3 dex files.
Clones of the source repos sit in the session scratchpad under others/. Within a tier: ports
with anchors present first, then adaptations, then new builds.

### P1

- [ ] P1 - Port the four upstream dev feed toolbar hides
  Why: LIVE (#123), search, follow "+" (#129) and save buttons hidden at the generator or assem
  level, no per-frame view lookup; retires our `jup` hide in VideoOverlayHider.
  Evidence: upstream/dev interaction/feedtoolbar/{FeedToolbarHooks,HideFeedLiveButtonPatch,
  HideFeedSearchButtonPatch}.kt, feedfollowbutton/HideFeedFollowButtonPatch.kt,
  feedbookmark/HideFeedSaveButtonPatch.kt; anchors LiveIconGenerator.enabled() (classes19, 50),
  HomePageUIFrameService.getInflatedSearchIcon, FeedAvatarDefaultAssem, VideoFavoriteAssem +
  "VideoFavoriteAssem showFavoriteState " all present.
  Touches: new patch files, FeatureControls.java (+4 helpers), Settings.java (+4),
  SettingsStatus.java, InterfacePreferenceCategory, TikTokPreferenceFragment; remove
  LIVE_ENTRANCE_ID from feed/VideoOverlayHider.java and migrate `hide_live_entrance`. The follow
  button patch uses plain `invoke-static {p2}`; switch it to `invoke-static/range { p2 .. p2 }`.
  Acceptance: each of the four switches removes its button on the next feed page; the Live
  entrance switch keeps its stored value after the migration.
  Complexity: S
- [ ] P1 - Port hxreborn's inbox trio: Hide suggested accounts, Hide inbox stories, Expand activity list
  Why: injector-level hiding covers Inbox, Activity and New followers (#132) with no one-frame
  flash, and Expand activity list replaces the "View all" truncation.
  Evidence: others/hxreborn_hxreborn-tiktok-patches patches/.../inbox/{HideSuggestedAccountsPatch,
  HideInboxStoriesPatch,ExpandActivityListPatch,Fingerprints}.kt, extension inbox/InboxControls.java;
  anchors NotificationRecommendUserWidgetV2Injector, FollowerUserCardWidgetV2Injector,
  RecommendUserWidgetV2Injector, InboxSkylightWidgetV2Injector (classes15, 19),
  NotificationWidgetContainer + "expandNotification()" (classes15), FollowerWidgetContainer present.
  Touches: new patch files, Settings.java (reuse `hide_inbox_suggested_accounts` and
  `hide_inbox_stories`, add `expand_activity_list`), SettingsStatus.java, InboxPreferenceCategory,
  inbox/InboxFilter.java (drop the stories and suggested-accounts row rules, keep Clear all).
  Acceptance: with the switches on, the stories tray and suggested accounts never render on any
  of the three pages; Activity shows the full list; Clear all still works on rows that remain.
  Complexity: S
- [ ] P1 - Port hxreborn's Disable telemetry
  Why: upstream #51 (4 reactions); every live anchor is a named SDK class, so it survives TikTok
  updates.
  Evidence: others/hxreborn_hxreborn-tiktok-patches patches/.../misc/telemetry/{DisableTelemetryPatch,
  Fingerprints}.kt (327 lines) + extension telemetry/DisableTelemetryPatch.java; AppLog (12 dex),
  AppsFlyerLib (6), "XY8Lpakui8g4kBcposRgxA" (classes25), FirebaseAnalytics.setCurrentScreen
  (classes7), MonitorCrash.reportCustomErr (classes26) present; BDLocationConfig absent and guarded
  by methodOrNull.
  Touches: new patch, Settings.java (`disable_analytics`, FALSE), SettingsStatus.java,
  TikTokPreferenceFragment (Behavior or Diagnostics section).
  Acceptance: patch applies; with the switch on, logcat shows no AppLog `onEventV3` traffic
  during a five minute feed session; TikTok still plays and follows.
  Complexity: S
- [ ] P1 - Port hxreborn's BdTuring CAPTCHA hook behind our `hide_captcha_popups`
  Why: covers the risk-control dialog our HideCaptchaPopupsPatch misses (upstream #100, #93
  CAPTCHA loops); must stay off by default because a hidden real check makes follows fail.
  Evidence: others/hxreborn_hxreborn-tiktok-patches patches/.../captchapopup/BdTuringCaptchaPopupPatch.kt;
  RiskControlService (classes26), getServiceType, "twice_verify" present.
  Touches: new patch, featurecontrols/FeatureControls.java (`shouldHideTuringCaptchaPopup`),
  a log line per suppression.
  Acceptance: patch applies; a browsing CAPTCHA is suppressed and logged; "sms" and
  "twice_verify" still show.
  Complexity: S
- [ ] P1 - Port hxreborn's five feed card filters
  Why: friend recommendation cards (#132), bulletin and inserted cards (aweme type 105), in-feed
  playlist bar, floating event badge, and scrolling past countdown-locked short-drama ads.
  Evidence: others/hxreborn_hxreborn-tiktok-patches feedfilter/FeedFilterPatch.kt (+75 over ours),
  extension feedfilter/{CardInsertFilter,DramaBlockingAdFilter,EventBadgeFilter,
  FriendRecommendationFilter,PlaylistBarFilter}.java; anchors InteractPlayListBottomBarAssem,
  "friend_recommend_card", "feedDynamicComponentLoadSuccess", DramaBlockingAdServiceImpl,
  specact/SpecActServiceImpl present.
  Touches: FeedFilterPatch.kt, Fingerprints.kt, FeedItemsFilter.java CONTENT_FILTERS,
  Settings.java (+3), FeedFilterPreferenceCategory (+3).
  Acceptance: each switch removes its card type; the drama ad no longer locks scrolling.
  Complexity: M
- [ ] P1 - Upgrade the Morphe patcher pin from 1.5.1 to 1.12.0
  Why: unlocks typed options (colour picker for the theme, sliders), PatchAvailability, and the
  Kotlin context-parameter syntax; the manager on the phone already ships 1.12.x and refuses
  only bundles built on a newer patcher.
  Evidence: MorpheApp/morphe-patcher CHANGELOG 1.5.2 to 1.12.0 (1.10.0 replaced context
  receivers); gradle/libs.versions.toml; patches/build.gradle.kts `-Xcontext-receivers`;
  patches/.../misc/settings/LegacySettingsEntryPatch.kt `context(BytecodePatchContext)`.
  Touches: gradle/libs.versions.toml, settings.gradle.kts plugin version, patches/build.gradle.kts,
  every `context(...)` use, patches/src/main/kotlin/app/morphe/util/*.kt (vendored copies may
  now duplicate the library).
  Acceptance: both governor tasks build; the bundle loads in Morphe Manager 1.29 with all
  patches listed; a test patch with an `intSliderOption` renders a slider in expert options.
  Complexity: M
- [ ] P1 - Ghost mode (no story view, profile view or typing reports)
  Why: upstream #109, #67, #63 (4 reactions); anchors are real names in 46.2.3.
  Evidence: others/eduardo3677-ai_tiktok-patches-for-morphe patches/.../ghostmode/{GhostModePatch,
  Fingerprints}.kt; `/StoryApi;` (7 dex), reportStoryViewed, reportUserInteraction,
  reportStoryReveal, `/ProfileViewerApiService;`.reportView (classes15, 17),
  `/TypingStatusSenderTimer;` (classes11), `/IMActiveStatusImpl;` (classes11, 24) present;
  the obfuscated `LJIILL` presence method is unverified.
  Touches: new patch and extension class, Settings.java (`ghost_mode`), SettingsStatus.java,
  a Privacy section in TikTokPreferenceFragment.
  Acceptance: jadx confirms the matched StoryApi methods have bodies; with the switch on, viewing
  a friend's story does not mark it viewed on their side (check with a second account) and no
  typing indicator shows in a DM.
  Complexity: M
- [ ] P1 - Hide already seen videos
  Why: unique to BlueDragon; a local watch history that drops rewatched videos from the FYP and
  Follow feeds.
  Evidence: others/BlueDragon4251_tiktok-patches-for-morphe interaction/seen/HideSeenVideosPatch.kt,
  extension seen/SeenVideoHistory.java (SQLite), feedfilter/SeenVideoFeedFilter.java;
  `onPlayProgressChange(String,J,J)V` on `/PlayerController;` present in 18 dex; its ForYou
  fingerprint is 46.4.3 only and must be swapped for our MainFeedResponseFingerprint.
  Touches: new patch (one hook), SeenVideoHistory + SeenVideoFeedFilter registered as an IFilter
  in FeedItemsFilter.CONTENT_FILTERS, Settings.java (`hide_seen_videos` FALSE, retention days),
  FeedFilterPreferenceCategory (switch, retention, clear history).
  Acceptance: a video watched past 3 s does not reappear after refresh; Clear history empties the
  table; retention prunes rows older than the setting.
  Complexity: M

### P2

- [ ] P2 - Share sheet data-level backend from PR #143
  Why: filters the panel model before the sheet builds (no flash), can drop the whole Send to row
  through the builder flag, and adds the Video actions row; keeps our confirm step and label list.
  Evidence: others/pr143.diff misc/sharesheet/{Fingerprints,ShareSheetPatch}.kt, extension
  sharesheet/{ShareSheetFilter,ShareChannelOptions,VideoActionOptions}.java; `LX/0oVo;` and
  `LX/0oVp;` present and co-located; builder fields LIZ, LJFF, LJJIIJZLJL need a jadx check.
  Re-fingerprint the constructor structurally (the class whose <init> takes the builder that
  owns two List fields and the IM boolean) instead of the `LX/0oVo;` literal.
  Touches: new patch, share/ShareSheetTools.java (keep confirm and label hiding as fallback),
  Settings.java (reuse `share_hidden_items`, `hide_share_contacts`).
  Acceptance: a hidden channel never renders; Hide the Send to row removes the header too.
  Complexity: M
- [ ] P2 - Advanced feed rules our way
  Why: caption keyword blocklist (#140, 5 sources), creator blocklist, max duration (Toki,
  Plugin, FreedomPlus), promotional music, LIVE replays, views-per-like ratio.
  Evidence: others/BlueDragon4251_tiktok-patches-for-morphe feedfilter/advanced/ (681 lines of
  reflective rules over Aweme and AwemeStatistics, including the "keep one nearest reject when a
  page would be empty" guard); `getDuration`, `isAd`, `getRegion` present.
  Touches: new IFilter classes beside feedfilter/ContentMarkerFilters.java, Settings.java (string
  lists and ints), FeedFilterPreferenceCategory, TikTokPreferenceFragment counts.
  Acceptance: a caption containing a listed word is skipped; a creator on the list never
  appears; a page is never emptied entirely.
  Complexity: M
- [ ] P2 - Confirm before follow and like
  Why: same accidental-tap problem the share sheet confirm step solves; BHTikTok, BHTikTok++ and
  the Android port all ship it.
  Evidence: BandarHL/BHTikTok Tweak.x `like_confirm`, `follow_confirm`; our
  share/ShareSheetTools.java ConfirmTouchListener; upstream dev
  interaction/feedfollowbutton/HideFeedFollowButtonPatch.kt locates the follow control.
  Touches: new patch or an extension of Hide feed follow button, Settings.java, Interface
  category.
  Acceptance: first tap on Follow shows the red ring and toast, second tap follows; same for
  the like heart when enabled.
  Complexity: M
- [ ] P2 - Disable double-tap like, optional double-tap opens comments
  Why: accidental likes; FreedomPlus and DouyinEnhancer ship both variants.
  Evidence: `double_click` string present in 9 dex; BlueDragon's gesture remapper identifies the
  listener as `LX/0QeR;` with `onDoubleTap(MotionEvent)Z` and `handleDoubleClick` (present,
  identity unverified).
  Touches: new patch (fingerprint the listener structurally: a class implementing
  onSingleTapConfirmed, onDoubleTap and onLongPress whose fields reference the feed panel),
  Settings.java, Interface category.
  Acceptance: a double tap no longer likes; with the option on it opens the comment panel.
  Complexity: M
- [ ] P2 - Download quality selector and original photo downloader
  Why: force the highest gear or a target height for saves (#146 in spirit), and save Photo
  Mode originals instead of TikTok's rendered copies.
  Evidence: others/BlueDragon4251_tiktok-patches-for-morphe interaction/downloads/
  {AdvancedDownloadsPatch,OriginalPhotoModeDownloaderPatch}.kt, extension
  download/DownloadQualitySelector.java (288 lines, reflective), OriginalPhotoModeDownloader.java
  (516); `getBitRate`, `gearName`, `qualityType` present; our DownloadSuccessCoroutineFingerprint
  is the same hook site.
  Touches: download/DownloadsPatch.java (`select()` call in patchVideoObject), one invoke at the
  download-success site, Settings.java, DownloadsPreferenceCategory.
  Acceptance: a save with "highest" picks the largest gear (compare file size to auto); a Photo
  Mode save writes the origin image URLs.
  Complexity: M
- [ ] P2 - Video quality selector for playback
  Why: upstream #146 and ReVanced #6713 (11 comments); mobile-data quality control.
  Evidence: others/eduardo3677-ai_tiktok-patches-for-morphe videoquality/ (scoring table is
  reusable; its hook recurses through getBitRate and must not be copied);
  `Lcom/ss/android/ugc/aweme/feed/model/Video;`, `getBitRate` present.
  Touches: new patch reading the bit-rate backing field directly (name from a jadx dump of
  Video) or hooking the gear-selection caller, Settings.java, a Playback category.
  Acceptance: with "lowest" selected, a 1080p video plays at the smallest gear (visible in
  TikTok's debug info or by network use); no StackOverflow in logcat.
  Complexity: M
- [ ] P2 - Automatic clear display with a delay
  Why: BlueDragon's most requested feature in its README; we already post the clear-display
  event on 46.2.3.
  Evidence: our interaction/cleardisplay/RememberClearDisplayPatch.kt and
  extension cleardisplay/RememberClearDisplayPatch.java (OnRenderFirstFrame hook);
  others/BlueDragon4251_tiktok-patches-for-morphe cleardisplay/AutomaticClearDisplayPatch.kt
  (its runtime names 0SKe, 12x2, 093F, Rv0 are not ours and are not needed).
  Touches: cleardisplay/RememberClearDisplayPatch.java (Handler.postDelayed of the existing
  event), Settings.java (`automatic_clear_display`, delay ms), Interface category.
  Acceptance: after the delay, every new video enters clear display; tapping restores.
  Complexity: S
- [ ] P2 - Feature Gate Recorder
  Why: baseline-and-diff over Feature Gate Lab observations makes finding the gate behind a
  TikTok behaviour a two-minute job (needed for #6, #136 and future hides).
  Evidence: others/BlueDragon4251_tiktok-patches-for-morphe misc/featuregatelab/
  FeatureGateRecorderPatch.kt, extension featuregatelab/{FeatureGateLearnMode,
  FeatureGateRecorderPreference}.java; the rest of the Lab is byte-identical to ours.
  Touches: new patch (dependsOn featureGateLabPatch), FeatureGateLabPreferenceCategory.
  Acceptance: Start, reproduce, Stop yields a diff listing the gates read in between.
  Complexity: S
- [ ] P2 - Screenshot black screen and Circle to Search (upstream #6, 14 comments)
  Why: our Disable screen capture detection stops the reaction but not the FLAG_SECURE blackout.
  Evidence: `circle_search_block` gate present in 2 dex (a commenter's workaround sets it to 0);
  `setSecure` present in 3 dex; Morphe's all/misc/screencapture/RemoveScreenCaptureRestrictionPatch
  shows the FLAG_SECURE clear.
  Touches: new patch (override the gate via Feature Gate Lab or clear the secure flag at the
  Window and SurfaceView calls), Settings.java.
  Acceptance: a screenshot of a playing video is not black; Circle to Search sees the frame.
  Complexity: S
- [ ] P2 - Subtitle download and caption styling (upstream #57)
  Why: subtitle size, background and survival in clear display; 2 reactions.
  Evidence: `getClaInfo`, `getCaptionInfos` present (classes with cla_info); yt-dlp's tiktok.py
  documents `video.cla_info.caption_infos` with json, srt and vtt URLs.
  Touches: download/DownloadsPatch.java (write .srt beside the video), a caption view hook for
  size and background, Settings.java, Downloads and Interface categories.
  Acceptance: a save of a captioned video writes an .srt; caption text size follows the setting.
  Complexity: M
- [ ] P2 - Region spoof beyond SIM
  Why: upstream #83 (6 comments); SIM values alone are not enough on 46.x.
  Evidence: MeiYongAI/Toki and Xposed-Modules-Repo/com.tiktok.unlocker hook Locale.getDefault,
  TimeZone.getDefault and GPS; beekamai/tt-unlock documents the region hub getters
  (`carrier_region`, `store_region`, both present in 46.2.3 in 14 and 19 dex) and warns that
  patching store_region regressed search.
  Touches: misc/spoof/sim/SpoofSimPatch.kt (add locale and timezone), a new hub-getter hook,
  SimSpoofPreferenceCategory.
  Acceptance: with a preset selected, TikTok's region-gated feature (a Live gift or a Shop tab)
  matches the preset after a restart.
  Complexity: M
- [ ] P2 - Default playback speed for every new video, with speeds above 2x
  Why: five sources (BHTikTok++, TikTok Plugin, Toki, VideoSpeed, mod APKs); ours remembers the
  chosen speed but has no "apply to every video" default and stops at TikTok's list.
  Evidence: our interaction/speed/PlaybackSpeedPatch.kt and extension speed/; Toki's rule
  "apply on new video unless the user set a speed manually".
  Touches: extension speed/ (default speed and custom list), Settings.java, Playback category.
  Acceptance: with a default of 1.5x, each new video starts at 1.5x; choosing 2.5x in the menu
  works.
  Complexity: M
- [ ] P2 - Auto-advance at the end of a video
  Why: seven sources (BHTikTok, BHTikTok++, TikTok God, mod APKs, DouyinEnhancer); TikTok's own
  auto scroll un-toggles itself.
  Evidence: our interaction/looping/StopVideoLoopingPatch.kt hooks the loop callback, which is
  the same site BHTikTok uses (`playerWillLoopPlaying:`).
  Touches: extension looping/ (call the feed's next-page scroll instead of pausing),
  Settings.java, Playback category.
  Acceptance: with the switch on, a video ending scrolls to the next one.
  Complexity: M
- [ ] P2 - Foldable side-by-side comments (PR #145)
  Why: upstream #144; cheap and off by default.
  Evidence: others/pr145.diff misc/foldable/{Fingerprints,ForceFoldableSplitViewPatch}.kt;
  `LX/0oq9;` with "isOptCommentSplit" and "isOptSplitContainer" present in classes17; the two
  strings identify the methods on their own, so drop the class literal.
  Touches: new patch, Settings.java (+2), ExtensionPreferenceCategory.
  Acceptance: on a device wider than the threshold, the comment panel opens beside the video.
  Complexity: S
- [ ] P2 - Post-build bundle assertion
  Why: the "0 patches" failure (generatePatchesList strips classes.dex) has no automated guard.
  Evidence: CLAUDE.md gotcha; scratchpad verification greps done by hand after every build.
  Touches: a PowerShell or Gradle step after buildAndroid that fails unless classes.dex,
  extensions/tiktok.mpe and extensions/shared.mpe are present and the patch count matches
  patches-list.json.
  Acceptance: deleting classes.dex from a built bundle makes the step fail.
  Complexity: S

### P3

- [ ] P3 - Sensitive-content warning and mask disable
  Why: BHTikTok++, Toki, Unicorn, the Android port; the interstitial re-appears per video.
  Evidence: `getMaskInfos` is absent in 46.2.3, so the model name differs; BHTikTok++ hooks
  `AWEMaskInfoModel` and `AWEPlayInteractionWarningElementView`; needs a jadx pass on Aweme's
  mask fields.
  Touches: FeedItemsFilter or a view hide, Settings.java.
  Acceptance: a video with a sensitive warning plays without the interstitial.
  Complexity: M
- [ ] P3 - Author region flag beside the username
  Why: BHTikTok++, Toki, TikTok++; `getRegion` present in 25 dex.
  Evidence: raulsaeed/BHTikTokPlusPlus `uploadRegion`; our blockauthor/CurrentVideoAuthor reads
  the Aweme already.
  Touches: blockauthor overlay or a username view hook, Settings.java.
  Acceptance: the author's country code shows next to the name on the feed.
  Complexity: S
- [ ] P3 - Granular clean-mode hides (caption, music line, action bar, status bar, surveys)
  Why: the most common feature across 13 sources; ours has tabs and overlays only.
  Evidence: MeiYongAI/Toki page purification list; TikTok Plugin "hide post captions";
  our feed/VideoOverlayHider pattern and a paused-feed uiautomator dump for ids.
  Touches: feed/VideoOverlayHider.java (id table), Settings.java, Interface category.
  Acceptance: each switch hides its element on the next layout pass.
  Complexity: M
- [ ] P3 - Settings backup, restore and reset
  Why: Toki and DouyinEnhancer ship it; re-patching after a TikTok update loses nothing today,
  but a reinstall does.
  Evidence: MeiYongAI/Toki home dashboard; twyora/DouyinEnhancer PM.md.
  Touches: a Diagnostics preference that exports and imports the shared preferences JSON.
  Acceptance: export, clear app data, import, every switch is back.
  Complexity: S
- [ ] P3 - Bulk favourites clear, block list viewer, own comment delete
  Why: management endpoints TikTok exposes but hides; the app's own signer makes them free.
  Evidence: `/aweme/v1/aweme/collect/` (3 dex), `/aweme/v1/user/block/list/` (1),
  `/aweme/v1/comment/delete/` (2) present; `multi_delete` and `block/story/list` absent;
  haglooo/TikTok-remove-saved abuses collect; our BlockAuthorService shows the service reuse.
  Touches: new extension screens under Settings, fingerprints on the three interfaces.
  Acceptance: Clear favourites empties the Favorites tab; the block list screen lists blocked
  accounts with an Unblock control.
  Complexity: L
- [ ] P3 - Settings localisation through AddResourcesPatch (upstream #115)
  Why: 2 reactions; every string is hardcoded English in Java.
  Evidence: MorpheApp/morphe-patches all/misc/resources/AddResourcesPatch.kt and its
  `resources/addresources/values*/` layout.
  Touches: every preference category, a strings.xml per locale.
  Acceptance: switching the phone to German shows German settings text.
  Complexity: L
- [ ] P3 - Gesture remapper (single, double, long press actions)
  Why: BlueDragon's headline feature; conflicts with Hold-and-slide 2x and Disable long-press
  quick share unless designed together.
  Evidence: others/BlueDragon4251_tiktok-patches-for-morphe interaction/gesture/ (reads
  obfuscated `LX/0QeR;` fields; do not copy); structural fingerprint idea in the double-tap item.
  Touches: new patch after the double-tap item lands.
  Acceptance: long press can be set to "nothing", "2x", or "open comments".
  Complexity: L
- [ ] P3 - Unit tests for the feed IFilter classes
  Why: every new feed rule is a pure function over reflective Aweme reads and has no test.
  Evidence: extensions/.../feedfilter/ContentMarkerFilters.java, SoundFilter.java.
  Touches: extensions/tiktok/src/test with a stub Aweme exposing the getters.
  Acceptance: `gradle :extensions:tiktok:test` runs and a broken filter fails it.
  Complexity: M
