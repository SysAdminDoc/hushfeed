# ROADMAP

Findings from the 2026-09-05 audit that were not fixed in that pass, plus upstream
requests worth building. P0 is broken, P3 is nice to have.

- [ ] P3 - Bulk unfollow / follower management (upstream #108)
  Why: IUserService follow calls are already mapped by FollowDiagnosticsPatch. Simplest shape is
  the Clear all pattern: an injected control on the Following list that presses each Following
  button in turn. Needs a uiautomator dump of that screen for the row and button ids.
  Where: new patch; extensions/tiktok/.../inbox/InboxFilter.java shows the pattern.
- [ ] P3 - Localise the five inbox system labels
  Why: they only match in English.
  Where: InboxFilter.matchesSystemLabel
## Research-Driven Additions

From the 2026-09-05 research pass (see RESEARCH.md, gitignored). Candidate patches come from
upstream's dev branch, upstream PRs #143 and #145, and the hxreborn, BlueDragon4251 and
eduardo3677-ai forks; every fingerprint anchor was checked against the 46.2.3 dex files.
Clones of the source repos sit in the session scratchpad under others/. Within a tier: ports
with anchors present first, then adaptations, then new builds.

### P1

### P2

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
