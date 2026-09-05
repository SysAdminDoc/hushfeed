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
