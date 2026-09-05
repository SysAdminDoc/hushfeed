# ROADMAP

Findings from the 2026-09-05 audit that were not fixed in that pass, plus upstream
requests worth building. P0 is broken, P3 is nice to have.

- [ ] P1 - Filter the Inbox at the data level
  Why: rows are hidden as they lay out, so a row can flash for a frame, and the five system
  rows match on English titles because they share one container id.
  Where: extensions/tiktok/.../inbox/InboxFilter.java; needs the inbox adapter located in the APK.
- [ ] P1 - Verify unblock (block_type 0) against a real unblock call site
  Why: the Undo path sends 0, inferred from TikTok passing 1 to block. Never observed.
  Where: extensions/tiktok/.../blockauthor/BlockAuthorService.java
- [ ] P2 - Draw the block glyph instead of relying on the font having U+2298
  Why: a font without the glyph shows a tofu box.
  Where: extensions/tiktok/.../blockauthor/BlockAuthorOverlay.java
- [ ] P2 - "Not interested" one-tap button beside the block button
  Why: same shape as the block button. Confirmed endpoint /aweme/v1/commit/dislike/item/, but it
  sits behind an obfuscated Kotlin suspend interface (X.0MlK on 46.2.3) taking a Map body and a
  Continuation, which churns every build. Needs a Proxy-built Continuation and a fingerprint on
  the endpoint string rather than the class name.
  Where: new patch; scratchpad tt/out3/sources/X/InterfaceC17470MlK.java shows the shape.
- [ ] P3 - Bulk unfollow / follower management (upstream #108)
  Why: IUserService follow calls are already mapped by FollowDiagnosticsPatch. Simplest shape is
  the Clear all pattern: an injected control on the Following list that presses each Following
  button in turn. Needs a uiautomator dump of that screen for the row and button ids.
  Where: new patch; extensions/tiktok/.../inbox/InboxFilter.java shows the pattern.
- [ ] P3 - AMOLED black theme (upstream #72)
  Why: the user's default for Android; needs the theme resource hook.
- [ ] P3 - Localise the five inbox system labels
  Why: they only match in English.
  Where: InboxFilter.matchesSystemLabel
- [ ] P2 - Verify 0.9.0 on device
  Why: none of the sound filter, feed switches, comment keyword filter or two finger block has
  run on a device. Confirm each once, then remove this item.
  Where: extensions/tiktok/.../feedfilter/SoundFilter.java, ContentMarkerFilters.java,
  comment/CommentTools.java, blockauthor/BlockAuthorOverlay.java
- [ ] P2 - Replace the fragment back stack guard in FeedVisibility
  Why: getSupportFragmentManager cannot be found by reflection on 46.2.3 (logcat: "Fragment
  back stack not readable"), so backing out of a grid video to its profile still leaves the
  block button showing until the bottom navigation reappears. Needs a different "page popped"
  signal: a hook on TikTok's own back handling, or a fingerprint on the detail page's
  lifecycle method.
  Where: extensions/tiktok/.../blockauthor/FeedVisibility.java backStackDepth()
