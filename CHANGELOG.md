## Unreleased

* The Friends tab gets filtered like the rest of the feed. It arrives as its own kind of response, so none of the existing hooks ever saw it and sponsored posts kept turning up there with every filter switched on. Ads, and LIVE cards when you have livestreams hidden, are dropped from it now.

* The Save button on a sticker saves the sticker you are looking at. TikTok reuses the preview sheet for whatever sticker you open next, and the button was holding the one it was built for, so opening a second sticker and saving it could write the first one instead.

* Settings only shows switches for patches you actually selected. Show seekbar and the two sharing-link controls were drawn whatever you picked in the patcher, so a bundle without them offered three switches that did nothing and the App behavior row claimed two were on before you had touched anything. The count beside each section is also more honest now: it was ignoring the mobile-data quality cap and three of the comment switches.

* Crash reports stop carrying addresses and session tokens. The reports TikTok's own crash handler produced were already scrubbed, but the ones from the patch's Java handler were not, and a network exception puts the whole request URL in its message. Those reports go to shared storage and to the clipboard so they can be attached to a bug report, so anything in them travels. Both paths now go through the same scrub.

* Saving a video no longer falls over when one of its captions names no language. TikTok sends the language as free text, and a value that cleans up to nothing but separators used to slip past the unknown-language fallback and then crash the save outright. It is treated as unknown now, like an empty one always was.

* Clearing the seen video history keeps its way back when you tap twice quickly. The record is read off the database on a background thread, and a second tap that arrived before that finished used up the offer and left nothing to put back, which on a cold database is exactly the window the toast invites you into. The offer now stands until the copy is really there, and the row says so. Putting the record back also keeps a video you watched again in the meantime at its newer time instead of the older one.

* The box above the comments now actually turns up. It was being added from the callback that fills a comment row in, and a list fills a row in before it puts it on screen, so the code went looking for the comment sheet from a row that was not in it yet and found nothing. It waits for the row to land now. It also looks a little further up for something to sit above, because the list is not always a direct child of the column that stacks the sheet, and a search you typed on one video no longer follows you to the next one.

## 0.19.0 (2026-09-06)

* A box above the comments that narrows them to what you are looking for, by what a comment says or who said it. Nothing is taken out of TikTok's list: comments that do not match are collapsed where they sit, so clearing the box brings them all back and paging, replies and counts never know anything happened.

* Review pass over the last three switches. The refresh rate one was overwriting the register TikTok stores its own record of the rate in, so it now replaces the store outright and leaves the value alone; it also only declines a request that is slower than the screen, because not every one of the six places that asks is asking for less. Duet and Stitch was skipping the commercial refusal as well as the creator's, which is the one most likely to get a post taken down, so only the creator's is answered now and the description that said so is true. A muted save no longer downloads a sound it will not use, no longer fails outright when that download fails, no longer quietly saves with sound when the stream is missing, and on Automatic keeps the file TikTok would have saved rather than jumping to the largest one. Saving a story asks the other app too.

* A switch that ignores the creator's Duet and Stitch setting, so the entries appear on videos that closed them. Only that one check is skipped. A photo post still cannot be duetted, a private video still cannot, and the app's other reasons for refusing all still stand. Whether the upload is accepted is the server's call and not something a patch can decide, so treat this as making the button available rather than a promise the post will stay up.

* Videos can be saved without their sound, and the save button can hand the link to a downloader you already use instead of saving anything here. Put that app's package name in the box and the button opens it with the video's link; leave it empty and nothing changes. Saving the sound as its own .m4a still works alongside the muted save, so you can have the picture and the sound in separate files if that is what you wanted.

* A switch to stop TikTok pulling the screen down to the frame rate of the video. When a video starts, the app asks the window to run at that video's rate, so a 30 fps clip puts a 120 Hz phone at 30 Hz for everything, scrolling included. With the switch on the window says it has no preference and the phone decides. It does not raise anything, it only stops the asking.

* Review pass over the three new switches. The edge seek was moving whatever the player last reported, which after scrolling on to a photo post is the video before it, so a press did nothing visible and swallowed TikTok's own hold as well: the seek now names the video it means. Its zones were also measured across the display rather than across TikTok's window, which in a side by side split view made the whole screen a forward-seek. A video fitted to the screen says it sits in the middle, because one large enough to fill did not have to. Fitting also measures the window TikTok has rather than the whole display, which is the case the switch exists for.

* The whole of a video, on screens that are not 9:16. TikTok picks a size for the video that fills the window, which on a folding phone opened up, a squarer screen or a split view means the sides or the ends are cut off. The switch takes the same arithmetic and the smaller scale, so the video sits inside the window whole. A tall phone looks exactly as it did, because the video already fits there.

* Two notifications TikTok gives you no way to turn off. One switch drops the "somebody started following you" notification before Android is asked to post it, which is the usual complaint when a wave of empty accounts follows you overnight. The follower still turns up in the Inbox and nothing else in the drawer changes, because the app tags each notification with the channel it belongs on and only that one is dropped. The other switch takes away message streaks: the streak button on a chat and the reminder that goes with it.

* Press and hold the left or right third of the screen to jump back or forward. How far is up to you, five seconds to start with, and the middle third still does whatever Long press is set to. It rides on the Long-press controls patch and is off until you turn it on, because holding a video is also how TikTok starts playing it at double speed.

## 0.18.0 (2026-09-06)

* Review pass over the story save, the search suggestions and the share host. Hiding the suggestions was guarding two methods that build the request rather than the two that make it and show it, so the page fetched exactly as before and only the display was dropped: it now guards the request and the hand-off to the screen, which is what the switch said it did. A story is kept with the play area that bound it, because the viewer binds the pages either side of the one you are looking at and a single last-seen story would be the neighbour's by the time you pressed. The story save also runs the quality, sound and subtitle settings the same way a feed download does, says what it did when a photo story stops part way, and hands the press back to TikTok when it is not going to save anything. The host box refuses anything that is not a host, since a "?" in it would have left every shared link pointing at a front page. The search switch was also unreachable unless another Interface patch happened to be installed.

* Shared and copied links can go through a host of your choosing. Put one in the box under Sanitize sharing links and tiktok.com is swapped for it, which is how you get a link that shows a preview where TikTok's own does not. Only TikTok's own hosts are swapped, and only the host: the rest of the link is untouched and nothing is sent anywhere.

* A switch for the searches TikTok puts on the search page before you type. It stops the app asking for them as well as showing them, so the rows are not fetched and then hidden. What you have searched for yourself is a different list and is left alone.

* Stories can be saved. They are ordinary posts with no save button anywhere on them, so press and hold one and it goes to your usual video or photo destination, photo posts and all. Holding a story is also how TikTok pauses it, so the switch is off until you ask for it.

## 0.17.0 (2026-09-06)

* Review pass over the sticker formats and the profile picture save. The profile hook was reading the user through the very method it had been added to, so every profile open burned a stack and swallowed the overflow. It reads the field now. The long press is only taken when the feature is on, so TikTok's own is left alone otherwise, and a picture is refused rather than saved when the handle on screen belongs to somebody else. A sticker with more colours than a palette can hold no longer counts every one of them: 64 frames of noise went from running out of memory to under a second.

* Press and hold a profile picture to save it. TikTok never puts the full size avatar on screen, so the file comes from the profile the app loaded rather than from what is drawn, which means you get the original rather than a display crop. It lands in your photo destination, named after the account.

* Animated stickers save in the format you pick. The old behaviour turned every one into an MP4, which messaging apps that take animated stickers reject. WebP now writes the file TikTok sent, byte for byte, and GIF is written here rather than handed to a converter app. If a conversion fails the sticker is still saved as the WebP it arrived as, so a save never comes back empty handed.

* Review pass over the settings lookup and the sound save. A phone set to two languages reads them in order again, so French and then German shows German rather than English: the new table lookup was only reading the first language, which is not what Android does with resources. Saving the sound no longer downloads the same bytes a second time; it takes them from the file the video download already has, which also means the .m4a and the video carry the same audio stream.

* Advanced downloads can keep the sound on its own. Turn on Save the sound as well and every video you download also writes an .m4a named the same way, copied out of the track TikTok already serves rather than re-encoded. Android 10 and later file audio separately from video, so it goes to Music under whatever folder name your videos use.

* The settings screen speaks Indonesian. All 404 strings are translated, and the tests now walk every screen under the Indonesian locale the same way they do under German, so a missing line fails the build rather than showing up as English on a phone.

* Patching fits the memory Morphe Manager gives by default. The settings text used to be merged into TikTok's own resources, which meant decoding a resource table of 74,765 strings and needing 768 MB to do it. The translations now travel in the extension's code instead, and screen capture detection, which only wanted the manifest, no longer asks for resources at all. The default set of patches finishes in 512 MB. AMOLED dark theme still rewrites colors, so picking it still means raising the limit to 768 MB.

* Review pass over the new filters and the history clear. Clearing the seen video history now copies the rows off the database rather than out of memory, which could be empty when the feature has never run in that session: the clear deleted everything and offered nothing back. A blocked creator entry that opens a pattern and never closes it no longer swallows the names after it, patterns stop at the end of their line, and each pattern keeps its own compiled form, so two of them no longer take turns handing each other the wrong one.

* A switch hides the numbers under the like, comment, favourite and share buttons while leaving the buttons themselves.

* Clearing the seen video history happens on the tap, with no dialog first. The row says what it cleared and a second tap puts it all back, in the database as well as in memory.

* The install steps say what to do about an out of memory error while patching. The same work needs more than Morphe Manager's 640 MB default on a desktop, and 1024 MB is comfortable.

* Comment tools can hide comments that are a picture or a sticker instead of words, replies included. It works on its own, without the keyword filter.

* Clear display now hides the tab strip by asking whether the controls are actually hidden, rather than reading a stored setting the automatic path never writes. The strip stayed up in exactly the case the change was meant to fix.
* The search filter leaves a page alone when every card on it looks like an advert. A whole page of adverts is less likely than one of the card shapes being wrong, and an empty grid tells the user nothing.

* Two more feed filter lists: only from these countries, and never from these countries. They read the same value the author row shows, and a video TikTok sends without one is always kept.

* A blocked creator entry between slashes, like /^news_/, is a pattern instead of one handle. It is matched against the @name and the display name, and a pattern that will not compile is dropped and said once rather than quietly doing nothing.

* Feed filter takes ranges for comments, favourites and shares as well as views and likes. They read the same statistics the app already sends with every video.

* Each of the six buttons in the right column has its own switch: the avatar and follow button, like, comments, favourite, the music disc and share. Hiding the whole column is still one switch.

* Clear display keeps the top tab strip away. TikTok hid it when the mode started and brought it back on the first swipe; it now stays gone until the tap that ends the mode.

* Show the handle instead of the name puts the creator's @username beside a video in place of the display name they chose. It works alongside the country switch, which still follows whichever of the two is showing.

* Sponsored cards are gone from the search Top and Videos grids. Search results are not videos but cards, so the card's own ad flag decides, along with the multi-ad, AI and brand zone shapes and the video it wraps.

* Review pass over the CAPTCHA gate and the follow verdict. The request recorder is its own dependency now, so selecting only the BdTuring patch cannot leave the gate blind and hiding every check. A verification request whose scene cannot be read is shown rather than hidden. Follow paths on a relation route count as follows. The verdict reader no longer keeps a context per request when diagnostic logging is off, and cannot throw into TikTok's network stack. A status code nested under `data` no longer answers for the whole response, a static getter is not read as this response's verdict, and 0, "0" and "0.0" all read as accepted. Both windows use the monotonic clock, so a corrected system clock cannot open or close them.

* New patch: Hide comment popup ads. Commenting with a word or emoji an advertiser has bought sets off a full brand animation over the comment sheet. The switch stops it.

* Playback quality has a second choice for mobile data. It is a ceiling, so it lowers what plays on a metered connection and never raises it.

* Ads no longer come back in the profile video viewer. Opening a video from a profile was filtered, but scrolling past it refilled the viewer through the profile detail panel's own two delivery methods, which nothing was watching.

* Follow diagnostics ships. The patch was in the tree but hidden, so nothing built it, which is why nobody could attribute a follow that quietly failed. It now reads the server's `status_code` and `status_msg` off the answer, says once per session when TikTok refused a follow and why, notes whether a puzzle was hidden just before, and writes the whole exchange to the diagnostic report when logging is on.

* Hide CAPTCHA popups no longer hides a puzzle the server raised over a follow, like, comment, repost or story action. Those puzzles gate the action behind them, so hiding one made the action fail with nothing on screen. Every risk check is now classified by the request it belongs to, in one place shared by all five hooks, and a hidden check is logged with its id and says so once per session.

## 0.16.0 (2026-09-06)

* Settings now use AMOLED backgrounds, grouped rows and larger page headings. Each section has its own icon, with active counts on the home page.
* Feature Gate Lab, gate details and recording reports follow the same layout. Switches, value pickers and long labels stay readable in both themes.
* Native preference actions and saved values stay intact. English and German labels wrap with the phone's text size.
* Updated the settings screenshots and added navigation, value-saving and theme coverage to the local runtime tests.

## 0.15.1 (2026-09-06)

* The repository is public. Morphe Manager can add it as a source straight from the project page.
* Build tooling refreshed: Gradle 9.7.1, guava 33.7.1 and androidx annotation 1.10.0. The Android Gradle plugin and Kotlin come from the Morphe patches plugin (AGP 9.1.0, Kotlin 2.4.10), and the patcher stays at 1.12.0 because that is what the stable manager ships. Unused version pins were removed from the catalog.
* The bundle is otherwise the same as 0.15.0.

## 0.15.0 (2026-09-06)

* The project has its own name. The bundle, the entry in TikTok's settings and the settings screen now say Hushfeed. The repository moved to SysAdminDoc/hushfeed, the source index points at its releases, and the about row links to the project page instead of a donation link.
* Settings backups are written with the hushfeed-settings tag. Backups and the undo copy made under the old tag still restore.
* Everything listed under 0.14.1 ships for the first time in this release.

## 0.14.1 (2026-09-05)

* The settings screen follows the phone's language. German ships with this release, and a translation table plus a generator make the next language a single file. Falls back to English for anything untranslated.

* New Long-press controls patch. A long press on a video can keep TikTok's own action, do nothing, or open the comments. Off by default.

* Hide video overlays gained switches for the caption, the music line, the action column on the right, the survey cards and the status bar. Each puts its element back when the switch goes off.

* New Hide content warnings patch plays videos TikTok has classified without the overlay asking to be tapped through first. Off by default.

* New Show author region patch puts the country a video was posted from next to the creator's name on the feed. Off by default.

* Not interested, the follow and like confirmations, double tap to open comments and subtitle tracking keep working when the block button is switched off. They all read which video is on screen, and that reading was tied to the block button's own switch.

* The block button now blocks the creator of the video you are watching. It was following the feed's prefetch instead, so it usually targeted the account one video ahead. The same fix reaches Not interested, the follow and like confirmations, double tap to open comments, the sound button and caption tracking, which all read the same thing.

## 0.14.0 (2026-09-05)

* Preserve exact numeric tokens in settings imports and reject hidden trailing data. Backup restore and Lab undo use the same parser.

* Feature Gate Lab applies its master switch, resets and loaded-value imports without confirmations. Reset works while overrides are off, and the menu can undo the last reset or import. Migration and restart guidance use notifications.

* Keep inbox category models available so disabling a hide switch restores the row and its height on the next layout, including Archive and Shop.

* Reject incomplete settings backups using their saved key inventory. Recovery attempts both preference stores independently when a save fails.

* Fixed the shared text importer rejecting complete JSON and partially applying malformed profiles. Fractional or overflowing whole numbers are rejected before preferences change.

* Added settings backup, restore, reset and undo in Diagnostics. Backups include Feature Gate Lab rules and switches. Files are validated before changes, and failed saves attempt to restore the prior values.

* Added feed filter regression tests for content labels, paid content, verified authors and blocked sounds. They cover empty metadata, independent switches, exact sound ids and case-insensitive sound phrases.

* Inbox system switches now use bound category data in every language, including rows already on screen. Recycled rows recover their original height, and conversation titles do not trigger system filters.

* Added persistent automatic video advance using TikTok's native completion handler, with pause and dialog checks preserved. Disabling the option stops the scrolling it started.

* Added a default playback speed for each new video and custom speed menu choices through 3x. Manual choices remain active for the current video, and the native menu state follows the player.

* Reject malformed country input and preserve locale scripts and extensions when a legacy variant needs fallback handling.

* Settings that need a restart now save immediately and show a notification instead of a restart dialog.

* Keep all subtitle languages when native metadata supplies a Unicode language name or no language code. Distinct names get distinct filenames.

* Added locale, timezone and native region overrides using the SIM preset. Store-region overrides are separately opt-in. SIM hooks now handle high registers and ignored return values safely.

* Added an optional side-by-side comment layout for wider windows, with a configurable width threshold.

* Added SRT subtitle saves with language selection and matching video filenames. Caption size, background and visibility in clear display are configurable.

* Cancel automatic clear display immediately when focus or its settings change. Save large feature gate reports as JSON with a bounded preview and clipboard error handling.

* Added an option to allow screenshots and Circle to Search by removing secure window flags and disabling the native search block.

* Added Feature Gate Recorder with Start and Stop controls, a gate-read diff and a copyable report.

* Added automatic clear display with a configurable delay. Manual restoration cancels the timer until the next video.

* Added playback quality choices for regular and adaptive video streams. Playback limits leave the full set of download variants available.

* Selected-quality saves now pair adaptive video with its matching audio track before writing the final MP4.

* Added download quality choices and direct Photo Mode image saving. Separate video and audio tracks are combined without re-encoding. Original photos keep their source bytes and format, with fallback URLs tried when a server fails.

* Fixed stale confirmation rings after a video change and removed old ownership when a comment control is recycled.

* Added a double-tap choice for the feed: normal behavior, no action, or comments for the current video.

* Optional second-tap protection now guards the feed Follow button and like heart with a red ring and a four-second timeout.

* Feed rules now support caption phrases, exact creator handles or ids, maximum length, promotional music, LIVE replays and views per like. If quality limits reject a whole page, retain its closest eligible video.

* Share sheet tools can hide sharing apps, video actions and the complete Send to row before the panel is built. Hidden names also accept stable action keys.

* Prevent a rapid feedback tap from racing the completion of the previous request.

* Feed overlay controls now follow the detail page lifecycle and disappear when a grid video is closed.

* AMOLED dark theme adds an optional black background palette with a patcher color picker.

* **feed:** add a Not interested button beside the block control. It uses TikTok's own service and reports success only when the response says the feedback was accepted.

* **privacy:** require all story, profile and typing hooks. Remove the unsupported online-status claim and describe the Firebase screen hook precisely. Seen history now clears reliably, retains at most 10,000 videos and rejects the video just watched on refreshed pages.

* **build:** reject bundles missing a DEX payload or containing a stale patch list. The build instructions now put the Android task last.

## 0.13.0 (2026-09-05)

### Features

* **feed:** four upstream patches for the feed toolbar: hide the LIVE button, the search button, the follow plus under the avatar, and the save button. The LIVE one shares the existing Live entrance switch and stops the button before it is built

* **build:** move to Morphe patcher 1.12.0 and patches plugin 1.3.4, which is what Morphe Manager 1.29 ships. Typed patch options, sliders and colour pickers are available to patches from here on
* **feed:** a Hide already seen videos patch. Watched video ids go into a local database and are dropped from later feed pages, with a retention setting and a clear button. Nothing leaves the device
* **feed:** switches for the playlist bar, the floating event badge and the cards TikTok inserts between videos, plus the countdown lock on short drama adverts, which now releases when ads are being removed
* **captcha:** a second CAPTCHA patch for TikTok's risk control dialog, answering the existing switch. Off by default, it never touches SMS or two factor verification, and it logs every suppression
* **privacy:** a Ghost mode patch that stops the reports telling other people what you looked at: story views, profile views, the typing indicator. Online status is unchanged
* **privacy:** a Disable telemetry patch that stops ByteDance AppLog, AppsFlyer, explicit Firebase screen reports and crash reporting at their SDK entry points. Off by default, and it silences TikTok's own diagnostics too
* **inbox:** the Archive and TikTok Shop rows are now stopped at their widget injectors, so they never lay out
* **inbox:** three patches that work on TikTok's widgets instead of the finished views: suggested accounts and the stories tray are never built (and suggested accounts is now covered on Activity and New followers too), and the Activity and New followers lists can show in full instead of stopping at View all

### Bug Fixes

* **block button:** draw the block symbol instead of setting it as text, so a font without U+2298 cannot leave a tofu box on the button

* **feed:** close the ad delivery gaps upstream found: profile grid videos, the detail page, late insertions, the cache delivery chain, play-lag inserts and reach-bottom cache all run through the filters now
* **feed:** the cached video switch is now "Filter offline fallback videos" and applies to the videos TikTok falls back on when the feed cannot load enough new items

## 0.12.0 (2026-09-05)

### Features

* **comments:** the thumbs down on each comment now blocks the commenter instead of disliking. The icon stays, the row dims and the icon tints while the account is blocked, a second tap unblocks, and the overlay block button is gone

## 0.11.0 (2026-09-05)

### Features

* **share:** new Share sheet tools patch: a second tap is needed before a video goes to a friend, and people, share targets or actions can be hidden by name, or the whole Send to row

### Bug Fixes

* **comments:** the block button never appeared because a comment cell is bound before it is attached, so its root view was not the window. The button layer now attaches once the cell is in a window

## 0.10.0 (2026-09-05)

### Features

* **interface:** hide the "Search this image" prompt over videos and the top left Live entrance, each with its own switch
* **comments:** replace the two finger hold with a one tap block button drawn beside each comment

### Bug Fixes

* **comments:** draw the dimmed row and the undo banner in the comment panel's own window, so blocking a commenter is visibly confirmed

## 0.9.0 (2026-09-05)

### Features

* **feed-filter:** skip videos by sound, with a block-sound button on the player and a name list for re-uploads
* **feed-filter:** switches for paid partnerships, AI generated videos, verified accounts, Series and playlist videos
* **comments:** hide comments by keyword or by account, and block a commenter with a two finger hold

### Bug Fixes

* **comments:** watch the two finger hold at the window rather than on the comment cell, whose clickable children swallowed every touch

## 0.8.0 (2026-09-05)

### Features

* **tiktok:** add a one-tap block button to the video player, with an undo banner and a drag-to-move position that is remembered
* **tiktok:** add a switch for each Inbox row and header control, and a Clear all control for suggested accounts

### Bug Fixes

* **block-button:** hide the button off the video feed, and keep it available on videos longer than thirty seconds
* **block-button:** keep the button on a video opened from a profile or search, hide it over a profile page, and drop it on cards that have no account to block
* **block-button:** treat a block reply with a non-zero status code as a refusal instead of a success
* **inbox:** stop hiding every conversation when only message requests are switched off
* **inbox:** match the suggested accounts section, which uses none of the row title ids
* **inbox:** resolve view ids once instead of on every layout pass

Everything below this line is the release history of icysymmetra/tiktok-patches-for-morphe, which Hushfeed was forked from.

# [0.7.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.1...v0.7.0) (2026-08-23)


### Bug Fixes

* **build:** complete feed model stubs ([053ce6c](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/053ce6cd064c16c1a06e87e10b76896da7c00a83))
* **clear-display:** preserve state across feed transitions ([a433fe0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a433fe03b98ecee8814a83f94aaef760222e538f))
* Merge branch `dev` to `main` ([#95](https://github.com/icysymmetra/tiktok-patches-for-morphe/issues/95)) ([1ba91a3](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/1ba91a3d45ffdd8ee237105b8dd3ad8853a7b7fe))
* merge dev into main ([3c81835](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/3c8183512dfb9dbe7b7e93979e5a419e428ff605))
* **playback:** persist explicit speed selections ([0f785fc](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/0f785fc8685c30793bf7ffea028d1223613f52c9))
* **settings:** make custom dialogs fit device screens ([7592339](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/75923397520ff45fde1b5f618c1de2010de422e0))
* **tiktok:** consume download filename mappings ([864fc15](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/864fc1526d51f819daea8e0730716d62a4c6b662))
* **tiktok:** cover direct Turing CAPTCHA dialogs ([27b2639](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/27b263920ffadaf2e27777c703c225f2e4f3ce40))
* **tiktok:** expand startup and runtime hook coverage ([bac0ba8](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/bac0ba8d2697a60d2c66d723fba75e211ca48a49))
* **tiktok:** filter cached feed insertions ([d30a6dd](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/d30a6dd37f9eaa1c1faecdf2b30b047707170860))
* **tiktok:** harden bytecode hook resolution ([95e0a3f](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/95e0a3f8d6c45e97eb44c3066bd201e2d9ab1843))
* **tiktok:** preserve swipe-lock playback speed ([dfbe2a5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/dfbe2a55aaced183f97a457b494893c31cebf596))
* **tiktok:** prevent settings crash and expand crash reports ([08186e7](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/08186e77ba89d92debfcd319fe8fcc745e2a363b))


### Features

* **downloads:** support separate media destinations ([f4580c9](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f4580c94b3b2c0d2d79c2bb0bfa6a544abeaedb3))
* **tiktok:** add repost and cached feed controls ([446ee90](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/446ee90123b118642fe3c50f5da9221d984667c2))
* **tiktok:** expand offline video limits ([35eff0e](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/35eff0e4f84ea69925bbddc71eb04c6d0bf5e66d))





## 0.7.0

# [0.7.0-dev.8](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.7...v0.7.0-dev.8) (2026-08-22)


### Bug Fixes

* **tiktok:** preserve swipe-lock playback speed ([dfbe2a5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/dfbe2a55aaced183f97a457b494893c31cebf596))





## 0.7.0-dev.8

# [0.7.0-dev.7](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.6...v0.7.0-dev.7) (2026-08-20)


### Bug Fixes

* **tiktok:** consume download filename mappings ([864fc15](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/864fc1526d51f819daea8e0730716d62a4c6b662))
* **tiktok:** harden bytecode hook resolution ([95e0a3f](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/95e0a3f8d6c45e97eb44c3066bd201e2d9ab1843))


### Features

* **tiktok:** add repost and cached feed controls ([446ee90](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/446ee90123b118642fe3c50f5da9221d984667c2))





## 0.7.0-dev.7

# [0.7.0-dev.6](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.5...v0.7.0-dev.6) (2026-08-20)


### Features

* **tiktok:** expand offline video limits ([35eff0e](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/35eff0e4f84ea69925bbddc71eb04c6d0bf5e66d))





## 0.7.0-dev.6

# [0.7.0-dev.5](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.4...v0.7.0-dev.5) (2026-08-16)


### Bug Fixes

* **build:** complete feed model stubs ([053ce6c](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/053ce6cd064c16c1a06e87e10b76896da7c00a83))
* **tiktok:** filter cached feed insertions ([d30a6dd](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/d30a6dd37f9eaa1c1faecdf2b30b047707170860))





## 0.7.0-dev.5

# [0.7.0-dev.4](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.3...v0.7.0-dev.4) (2026-08-13)


### Bug Fixes

* **clear-display:** preserve state across feed transitions ([a433fe0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a433fe03b98ecee8814a83f94aaef760222e538f))





## 0.7.0-dev.4

# [0.7.0-dev.3](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.2...v0.7.0-dev.3) (2026-08-11)


### Bug Fixes

* **playback:** persist explicit speed selections ([0f785fc](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/0f785fc8685c30793bf7ffea028d1223613f52c9))
* **settings:** make custom dialogs fit device screens ([7592339](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/75923397520ff45fde1b5f618c1de2010de422e0))
* **tiktok:** expand startup and runtime hook coverage ([bac0ba8](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/bac0ba8d2697a60d2c66d723fba75e211ca48a49))





## 0.7.0-dev.3

# [0.7.0-dev.2](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.1...v0.7.0-dev.2) (2026-08-11)


### Bug Fixes

* **tiktok:** prevent settings crash and expand crash reports ([08186e7](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/08186e77ba89d92debfcd319fe8fcc745e2a363b))





## 0.7.0-dev.2

# [0.7.0-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.2-dev.1...v0.7.0-dev.1) (2026-08-10)


### Features

* **downloads:** support separate media destinations ([f4580c9](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f4580c94b3b2c0d2d79c2bb0bfa6a544abeaedb3))





## 0.7.0-dev.1

## [0.6.2-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.1...v0.6.2-dev.1) (2026-08-10)


### Bug Fixes

* **tiktok:** cover direct Turing CAPTCHA dialogs ([27b2639](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/27b263920ffadaf2e27777c703c225f2e4f3ce40))





## 0.6.2-dev.1

## [0.6.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.0...v0.6.1) (2026-08-09)


### Bug Fixes

* **morphe:** support patching on older Android versions ([01463a0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/01463a04e2a64a2a1400391778576779cfb2b211)), closes [#85](https://github.com/icysymmetra/tiktok-patches-for-morphe/issues/85)





## 0.6.1

# [0.6.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.5.0...v0.6.0) (2026-08-09)


### Features

* **tiktok:** restore download templates and sticker formats ([91d158c](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/91d158ca3da1126a2d2ca9f4c2330e81d3ea96f9))





## 0.6.0

# [0.5.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.4.1...v0.5.0) (2026-08-09)


### Bug Fixes

* **build:** select current bundle for patch catalog ([69bd1e9](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/69bd1e93194825b7af567e8456de4f29831fd9a8))
* **tiktok:** filter following ads at cached feed reads ([13d74f0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/13d74f00b1d076aa22b7a4ab8bbe92bfaf2c4b6b))
* **tiktok:** filter late following feed ads ([a232cba](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a232cbaeb3725f0c1361d20a99e9a6fd31a0be53))
* **tiktok:** hook native video looping boundary ([2721f01](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/2721f010b670842f9776b2afb34486de418f29d9))
* **tiktok:** preserve account verification CAPTCHA flows ([dd35d89](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/dd35d8948b002f0d098ed1f83a3f64cddc5f7904))
* **tiktok:** preserve native download addresses ([61da3c6](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/61da3c6413ed75cdb04f5eb1cb4b7292dfd06089))
* **tiktok:** repair automatic comment translation ([636ef64](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/636ef648c18be2610c6ee48beed7270e37ffce41))
* **tiktok:** reset completed video resume state ([100cab5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/100cab58fc178f360c2e727308e161e3e3e2f9ad))
* **tiktok:** skip same-language automatic translations ([3415710](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/3415710facfc60db19943599112b9bc47cf82647))
* **tiktok:** use native video loop control ([f508adb](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f508adbdbf6229e7a993a42616ed00cc0feadcd2))


### Features

* **tiktok:** add native search and seekbar controls ([c36bfaf](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/c36bfaf51d6e8d30fbef6e5c6ba99bfdeae283e2))
* **tiktok:** add playback completion controls ([9a51c2f](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/9a51c2f614145250177600144abd61eb30cf0841))
* **tiktok:** add quick-action controls ([fe29a24](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/fe29a249e42620d53dc6224c347c74aadc291c08))
* **tiktok:** port core patches to TikTok 46.2.3 ([2c52b6a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/2c52b6aa1362a3a9ccbcdd8dad2e16ec4b9a06cc))
* **tiktok:** port Feature Gate Lab to TikTok 46.2.3 ([4bdbdee](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/4bdbdeed1649bdc6177ea6a2c62bd2d9300ac8d6))
* **tiktok:** redesign patch settings navigation ([a479bdb](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a479bdb09a4e01584f9d683cdfda1c65d3ab6dff))
* **tiktok:** separate optional diagnostics from settings entry ([aad18c3](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/aad18c3e4b301366685174b0faa5a23a8a2850de))





## 0.5.0

## [0.4.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.4.0...v0.4.1) (2026-07-28)


### Bug Fixes

* **tiktok:** prevent seekbar recursion on stories ([9d2c0bc](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/9d2c0bc823ce661b385fff12945ac5c2ace0223f))





## 0.4.1

# [0.4.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.1...v0.4.0) (2026-07-26)


### Features

* **tiktok:** add feature gate lab and UI suppressors ([72bf32a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/72bf32aa6991257ace0bab91d3a8767617cbd802))
* **tiktok:** add translation language exclusions ([8f60185](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/8f60185434a3cbb13653438a6561e2d7fc45f4dd))
* **tiktok:** enable Feature Gate Lab by default ([a164b70](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a164b70cf046a738d3b0a817a57b1fedf515ef2e))
* **tiktok:** enable hold-and-slide 2x lock ([a2fd626](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a2fd626ecb1fb116461b760445128bfdd8980f29))
* **tiktok:** improve feature gate lab search ([ffc19de](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ffc19dece888a6ef814d20401d0bc4b9ba93858d))
* **tiktok:** port external browser patch from lyyako ([7a517c2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7a517c218a35508ac4f284450ff6d6c4d3ea05d8))
* **tiktok:** port publish date patch from lyyako ([1b6c386](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/1b6c386611239c39242bc813fca9614c8de3d3d1))
* **tiktok:** redesign support row ([cc39469](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/cc39469c98eabc0ccdea40fc423e94639c34703f))





## 0.4.0

# [0.4.0-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.1...v0.4.0-dev.1) (2026-07-26)


### Features

* **tiktok:** add feature gate lab and UI suppressors ([72bf32a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/72bf32aa6991257ace0bab91d3a8767617cbd802))
* **tiktok:** add translation language exclusions ([8f60185](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/8f60185434a3cbb13653438a6561e2d7fc45f4dd))
* **tiktok:** enable Feature Gate Lab by default ([a164b70](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a164b70cf046a738d3b0a817a57b1fedf515ef2e))
* **tiktok:** enable hold-and-slide 2x lock ([a2fd626](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a2fd626ecb1fb116461b760445128bfdd8980f29))
* **tiktok:** improve feature gate lab search ([ffc19de](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ffc19dece888a6ef814d20401d0bc4b9ba93858d))
* **tiktok:** port external browser patch from lyyako ([7a517c2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7a517c218a35508ac4f284450ff6d6c4d3ea05d8))
* **tiktok:** port publish date patch from lyyako ([1b6c386](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/1b6c386611239c39242bc813fca9614c8de3d3d1))
* **tiktok:** redesign support row ([cc39469](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/cc39469c98eabc0ccdea40fc423e94639c34703f))





## 0.4.0-dev.1

## [0.3.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.0...v0.3.1) (2026-06-17)


### Bug Fixes

* avoid concurrent feed list iteration crash ([275e8d5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/275e8d5f3ad273b24a5bd592b0da86bfe7dc3ee4))





## 0.3.1

# [0.3.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.2.0...v0.3.0) (2026-06-17)


### Bug Fixes

* improve settings menu dark mode layout ([2aa837b](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/2aa837b165e16c3bf435b385f15160c0ad6c41d1))
* prefer largest clean download source ([4f63b8a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/4f63b8aab765373041cbce77135efd55f34623c0))
* reduce repeated feed filter scans ([d161ef2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/d161ef20c8d9648fb4e48d84655f062c9be99813))
* restore downloads fallback for restricted videos ([32cfafe](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/32cfafeaf10ad1bcf87d72a38927977f76000ec5))


### Features

* add navigation translation and settings controls ([72ad5de](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/72ad5de04b8f7c670f25ca718d219d06124fe553))
* add SIM spoof country presets ([bb73b3d](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/bb73b3dfebdcbbf9f612a7716e408a8928b56568))
* copy comments without username ([ac01d5f](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ac01d5f8f2e673d6b72a38a6abf5d5da27330208))
* filter bottom navigation tabs ([e1f01b2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e1f01b25e35bb6d284691c273ae4b0b4778a57dc))
* save comment media downloads ([77b4dab](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/77b4dabcd6f327a7b9f06e5646193a0d7e54a52f))
* **tiktok:** add custom offline videos limit ([bfcb717](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/bfcb717d9e6fdcfb69ec7d0b30016352ed6d6c90))
* **tiktok:** add focused debug diagnostics ([878d0f4](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/878d0f44b4b1bc1846ecb888ca3f9536fcf4db11))


### Performance Improvements

* reduce feed filter scanning overhead ([ac13277](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ac13277743e2c5f4022a4e1bafea1caf067520fe))





## 0.3.0

# [0.2.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.5...v0.2.0) (2026-06-06)


### Bug Fixes

* improve settings menu dark mode layout ([4da9d87](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/4da9d8785f0e6ad97f8c9ad7e4e3cf5655f2e0f4))
* reduce repeated feed filter scans ([370b806](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/370b806cc1c64574d767b397d612e79a58ed6896))
* restore downloads fallback for restricted videos ([277f400](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/277f4003643b35045b62f9f95f63ed2d8bb889bf))


### Features

* add SIM spoof country presets ([7f8fb32](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7f8fb32dd7bc1f0b34078482216c087914b8ff4c))


### Performance Improvements

* reduce feed filter scanning overhead ([007d1be](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/007d1be0a93393a7ca1935cea8740cb167b5e6f9))





## 0.2.0

## [0.1.5](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.4...v0.1.5) (2026-06-01)


### Bug Fixes

* limit TikTok compatibility to global package ([49806ad](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/49806ad5a1c75bc2d7f052abf227b7d055879935))
* sanitize TikTok sharing links before shortening ([53a3558](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/53a3558f7e1921ccd2636fe2d322a4b7c1ca3d04))





## 0.1.5

## [0.1.4](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.3...v0.1.4) (2026-05-30)


### Bug Fixes

* improve settings readability and debug logging ([f4f4bc4](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f4f4bc4dc9104182c1a419878ddf837057555340))





## 0.1.4

## [0.1.3](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.2...v0.1.3) (2026-05-29)


### Bug Fixes

* support global settings top row insertion ([c38f1b6](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/c38f1b63d32b3d7218de1f5c46243f728c6c5868))





## 0.1.3

## [0.1.3](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.2...v0.1.3) (2026-05-29)


### Bug Fixes

* support global settings top row insertion ([c38f1b6](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/c38f1b63d32b3d7218de1f5c46243f728c6c5868))





## 0.1.3

## [0.1.2](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.1...v0.1.2) (2026-05-29)


### Bug Fixes

* fallback open debug settings insertion ([e201d53](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e201d533d19504f4e32001d6985cad7ee9804b15))





## 0.1.2

## [0.1.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.0...v0.1.1) (2026-05-29)


### Bug Fixes

* add release changelog script ([74fa14e](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/74fa14ebe975509816a9344031dd176a76595407))
* distinguish global and jp tiktok packages ([7e1f7df](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7e1f7dfd23d79346529715854a9b1840af210d47))
* soften support preference copy ([5cbfd26](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/5cbfd26030b35d5b2996ead5073b2ffd7df166a5))





## 0.1.1
