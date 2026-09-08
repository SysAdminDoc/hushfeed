## Unreleased

* The Save button on a sticker is in your language. It was the one piece of text this project adds to TikTok that never went through the translations, and it could not simply be translated: the button's own English label was also how the code recognised its own button, so a German one would have been added a second time on every sheet. It carries a marker now, and reads "Medien speichern" or "Simpan media" where it should.

* The daily hold works with a screen reader. It stopped touches and nothing else: it went up without being announced, "Open the feed anyway" was read as ordinary text rather than as a button, and the feed's like, comment and share controls behind it could still be swiped to, which is the one thing the hold is there to stop. The hold announces itself now, its way out is offered as a button, and what it covers is out of the way until it lifts.

* The Feature Gate Lab no longer freezes while it saves. Turning overrides on, and saving or resetting one gate, wrote to storage on the thread that draws the screen, so the tap sat there until the write finished. If a settings restore was running at the same time it sat there until the restore finished. All three now work the way the Lab's other changes already did: the write happens in the background and the screen catches up.

* Comment translation stops rebuilding one of TikTok's own services over and over. Working out which language you read in, and which languages you asked not to be translated, meant building that service and looking for the right method. The answer was only remembered when the method was found, so on a build that does not have one it was built again for every comment in view, while holding the lock that TikTok needs to hand a finished batch back. It is asked once now, and once is enough either way.

* Every row in a settings dialog list keeps Hushfeed's check mark. The list was restyled once, just after it opened, so any row you had to scroll to reach had never been touched and came up with Android's own check mark on the wrong side and TikTok's text colour. The eight row "Included diagnostics" picker scrolls on a small screen and on every screen at large text. Rows are styled as they appear now.

* The patches keep applying when TikTok's own methods grow. A call Hushfeed injects can only name sixteen registers, and a method with enough locals pushes its arguments past that, which fails the patch with an error that says nothing about registers. Eleven injections were written the short way and would have broken on the first TikTok build that crossed the line. They now use the long form only where they have to, so nothing about the current build changes, and a frame that cannot be patched at all says so in those words.

* A sticker save no longer keeps the screen it started from in memory. The job held the Save button, and a button holds the whole screen behind it, so closing the sheet freed nothing until the save finished, up to two minutes later with eight more saves queued behind it. It holds the button weakly now and simply skips handing it back if the sheet has gone.

* Saving an animated sticker as a video can be given up on. If the phone's encoder stopped producing frames partway, the save sat in a loop that neither the cancel nor the two minute limit could reach, and one of the three background workers that save media was gone until the app was killed. Two more stickers after that and saving stopped working entirely, with nothing said. The loop now gives up the moment the job is cancelled or runs out of time.

* Translators can work in Weblate. The settings strings were one tab separated file per language, a shape Weblate cannot host, so translating meant editing a file in a pull request. A language table can now be either that file or the comma form Weblate exports, and the generator writes the list of source strings a Weblate project translates from. A table that has been through a spreadsheet is read as well, byte order mark and all. Nothing changes for anyone already editing the tab form.

## 0.23.0

* Back keeps working on the settings screen when TikTok moves to the newer back gesture. The screen's Back rode entirely on a method Android stops calling once an app opts into predictive back, which TikTok has not done yet and will. The Feature Gate Lab already handled it; the settings screen does now too, through the same code.

* The patch bundle is byte reproducible. Two builds of the same commit used to differ, because the manifest recorded the moment it was built, so nobody could rebuild it and check the published checksum against their own. That field is pinned to the commit now, and a rebuild produces the same file down to the byte.

* The README says which Morphe Manager you need. Manager refuses a bundle built against a patcher newer than its own, so on 1.28.0 and older this one simply would not load, and nothing said so. It also now says in plain terms that everything Hushfeed adds runs inside TikTok with TikTok's permissions and data, and that this repository and its GitHub releases are the only official source.

* Comment translation notices every batch finishing, not half of them. TikTok signs off a finished batch from two different places, and Hushfeed was only listening to one, so batches that finished the other way were never marked done or failed. Depending on which way TikTok went, those comments were either never translated or asked for again on every scroll.

* A very wide animated sticker no longer saves as a black video. The converter checked how many pixels a frame held but not how big a picture the phone's graphics chip will take, which on a lot of Android phones stops at 4096 a side. A frame past that failed to upload, nothing was reading the failure, and the file came out black. It is refused now, and the sticker is saved in its original form instead.

* The blocked word lists take two operators as well as plain phrases. Put two phrases in quotes and join them: "cat" & "dog" hides only what has both, and "cat" !& "dog" hides what has the first and not the second. It works on blocked caption words and blocked comment words. Plain phrases mean exactly what they always did, a comma inside quotes is part of the phrase rather than a separator, and a line you started and did not finish is refused while the box is still open instead of quietly matching nothing.

* A settings box with a long explanation no longer squeezes the field it explains. At twice the system text size the explanation could take the whole dialog. It scrolls and gives way now, and the box keeps its full height.

* Turning "Thumbs down blocks the commenter" off now takes effect on comment sheets you had already opened. The takeover only went one way, so a comment row TikTok had kept in memory went on blocking, and a screen reader went on offering to block, until that memory was reused. Every row hands the control back the next time it is filled in.

* "Start today over" can be taken back. It ends a running hold and forgets what has been counted, and it used to leave nothing at all behind, so a mis-tap cost you the rest of the day. The row now says "Tap again to put the counts back", the same bargain the seen video history row already made. The offer lasts until the day turns over, because after that the counts belong to a day that is finished.

* The rows that do something no longer look like the rows that open a page. Reset settings, Undo, Start today over, Clear the seen video history and Clear diagnostic data all wore the same ">" as a row that opens a screen. They have dropped it. Back up and Restore keep it, because those really do open a file picker.

* A new switch turns the daily budget from advice into a commitment. Leave "Lock today's budget" off and nothing changes. Turn it on, and when today's budget runs out the hold has no "Open the feed anyway", "Start today over" is refused, and the budgets, the reset hour and the switch itself cannot be edited again until the day starts over at your chosen hour. You can turn it off freely any time before the budget runs out, and turning it on after the budget has already gone locks the rest of that day too. The lock lets go on its own when the day turns, and only then: moving the phone to another timezone does not end it early. It survives the app being killed, and messages, profiles and search keep working the whole time.

* The settings screen no longer draws its first row under the status bar and its last under the navigation bar. TikTok targets a recent enough Android that every window is edge to edge whether the app asks for it or not, and the two calls that used to colour the bars stopped doing anything at the same time. The screen now measures the bars and the display cutout and moves its content clear of both, with its own background reaching behind them.

* Comment translation stops hammering TikTok when a batch fails. A comment list binds its cells many times a second, and every one of those binds asked for the same failed batch again, with an exception logged each time. It now waits two seconds, then eight, then thirty, and after a fourth failure leaves that batch alone until the comment list loads again. A batch the service is working through a few comments at a time is not counted as failing.

* A comment batch that only half translates is asked for again. If the service came back with ten of thirty comments, the batch was marked done and the other twenty were never retried.

* If a TikTok update moves the field the translated comments arrive in, comment translation switches itself off for the session and says so in the log, instead of reading every batch as a failure and retrying each one three times.

* A settings restore from a backup made for another TikTok version could be quietly undone the next time you opened TikTok. The restore worked, and then the startup check that exists to finish interrupted changes read it as unfinished and put the old settings back. It now leaves a finished restore alone.

* A settings backup that only half downloaded says so. It used to come back as the same unexplained refusal as a photograph or a file from a newer Hushfeed. Files that are unreadable or too large reach you with their own wording too, which they never did before, because the check that produced the wording ran after the point where those files were already rejected.

* Undoing a restore tells you when it left the Feature Gate Lab rules out, the same way importing already did.

* Searching the settings finds the rows that do something, not just the switches. Back up, Restore, Reset, Undo, Hook status, Export diagnostic report, Clear diagnostic data, Included diagnostics, Clear the seen video history and Start today over all answered "No matching settings", which are exactly the rows people go looking for when something has gone wrong.

* A settings backup keeps working when Hushfeed moves to a new TikTok version. The file carries a note of which TikTok build it was made for, and that note used to refuse the whole file, so on the day this project retargets every backup anyone held would have stopped restoring. Only the Feature Gate Lab rules in it depend on the TikTok build, so those are left out and the toast says so; your settings come back either way. And a refused file now says which way it was wrong, so a download that was cut short reads differently from a backup written by a newer Hushfeed, instead of both saying only that it was rejected.

* A Feature Gate Lab rule saved at the wrong moment could do nothing at all. If TikTok happened to be reading its configuration on another thread while you saved, the rules it had already read were written back over your change, and the change stayed invisible until the next one. Reads made before TikTok has finished starting no longer queue up behind each other either.

* The Feature Gate Lab was making the whole app slower even with nothing switched on. TikTok asks itself hundreds of configuration questions a second, from every thread, and the Lab sits on the answer to each one. It was taking a single lock on every one of those before checking whether it had anything to record, which it does not unless you selected the Feature Gate Recorder, so every thread in the app queued behind every other. It checks first now and takes the lock only when there is something to write down.

* With either feed button on, the app was laying its whole screen out again on every frame, on the feed and everywhere else, for as long as it was open. Putting the buttons in place asked for a fresh layout even when nothing had moved, and the thing that puts them in place runs on every layout, so each one asked for the next. They are placed only when a position actually changes now, and not at all while the feed is off screen.

* On Android 6 the app could go down where a caption was resized, where the feed filters emptied a batch and said so, and where a creator pattern ran too long to finish, and the Hook status row quietly recorded nothing at all. Several calls and three types in the code that ships inside TikTok only exist from Android 7 onward, and both the compiler and the tests run on a desktop Java where all of them exist, so nothing ever said so. They are replaced with equivalents that work on every version the app installs on, and the build now runs Android's own API level check over both payloads, so the next one of these stops the build here rather than on a phone.

* Two more things a long press can do: copy the link to the video, or copy the link to the sound it was made with. The video link gets the same cleaning a shared link gets, so the parameters that say who sent it do not travel to the clipboard.

* New patch, Enable voice comments. TikTok builds the recording and publishing entry points behind a gate that not every account is on. It ships off, so selecting it in the patcher is the switch, and nobody here has been able to try it on a real account yet. Ported from icysymmetra's Metra patches.

* New patch, Comment sort controls. TikTok has a full comment sort sheet with hot, newest, media and creator options, and decides who gets it with a rollout gate and a per-post check. With the switch on you get the sheet TikTok already builds, on every post. Ported from icysymmetra's Metra patches.

* Automatic video advance now also shows TikTok's own Auto scroll action in the video actions panel. That entry hangs off a second flag, so accounts outside TikTok's rollout never saw it however the feed gate answered.


* Every patch in the bundle is now verified against a real TikTok 46.2.3 APK rather than a stripped fixture. All 70 apply, checked again on 8 September 2026, and the README records the exact build and its checksum so anyone can reproduce the same run.

* A hold no longer argues with a phone call over the sound. It asks once as the panel goes up, and again only when you come back to the feed, instead of asking every second for as long as the hold lasts.

* The diagnostic report names the exit of the app itself rather than whichever of TikTok's background processes the system happened to reap last, and it carries the exit status and importance alongside the reason.

* A feed filter that emptied a single batch says "one batch" rather than "1 batches".

* A settings backup and a diagnostics report made in the same second are now named with the same timestamp. The backup used local time and the report used UTC.

* Three search boxes read back what you typed instead of their own label. A content description on an editable field replaces its contents for a screen reader, so typing "cats" came back as "Search settings".

* The tab picker rows say whether they are on, and say so again after you tap one. The check box in each row is not the thing you press, so a screen reader had no state to read at all.

* A second undo banner within six seconds of the first now gets its own six seconds. The first banner's timer was still queued and took the new one away early, along with its Undo.

* The published patch list names what each patch actually depends on instead of saying "BytecodePatch" for all of them. Every patch that adds a setting now requires the Settings patch too, so the patcher cannot leave you with switches on a screen that was never built.

* The Long-press controls description in the patcher now mentions saving the original sound, which it has done since 0.21.0 while the description still listed three actions.

* The README says why saving works the way it does. It reads the addresses the app already fetched, on the session you are already signed in with, so there is nothing pretending to be a browser and nothing to keep in step with the site.

* Wording fixes across the settings and the messages the app shows you. A budget of one no longer says "That is 1 videos today". The hour the day starts at reads as a clock time rather than "13 o'clock". The confirm-a-tap message is a whole sentence in German and Indonesian instead of a translated phrase with an English verb dropped into it. A settings backup is named with a date and time you can read rather than a run of digits.

* Seven defects in the translation tables, including a placeholder that only worked while there was one value to fill it, five German summaries missing the full stop the rest of their screen has, and a quote that opened in German and closed in English. A new check refuses all three shapes from now on.

* Blocking someone from a comment now updates every comment of theirs on screen, not only the one you pressed. A screen reader was told the others were not blocked, and following that told it to unblock instead.

* A hold really does quiet the feed. It gives the sound back when you leave the feed for messages or search, and if a call takes the sound away it asks for it again rather than giving up for the rest of the hold. A video finishing behind the hold no longer counts against the automatic advance limit or puts a notice over the panel.

* Two settings pages stop offering rows for patches that are not in the bundle. The download destinations and filename templates need the downloader, and the operator code and name need the SIM patch.

* Clear all in the inbox reads as something you can press rather than as another heading.

* Two patches that could be selected on their own put their switches somewhere nobody could reach them. Hide comment popup ads built a switch on a page that never appeared, and the offline videos limit sat below an early return belonging to the Downloads patch, so selecting it alone gave a page without it. Every settings page now keeps each patch's rows behind that patch's own flag.

* Clear all in the inbox is readable in both themes. It was picking its colour from a flag the settings screen sets, which away from that screen answers for the system theme rather than TikTok's own, so it could draw dark red on a dark sheet.

* The feature gate report says more precisely what it replaces, instead of implying every value is redacted.

* A hold now actually holds. The panel covered the feed and swallowed touches, but the video underneath kept playing with sound, the feed kept advancing if automatic advance was on, and every video that went by behind the panel was counted against the day. The hold asks for the audio focus so the player stops, automatic advance stands down until the hold ends, and nothing is counted while the feed is out of sight.

* The daily budget really is free when it is switched off. The hold check ran from the player's progress callback several times a second and took a lock and built a calendar every time, whether or not a budget was set. Now the off case is two field reads, and with a budget set the day is worked out once a day instead of once per callback.

* The release check now fetches the address in the bundle index every time it runs, instead of only when the checkout happens to hold a freshly built bundle. Pointing the index at a release that does not exist yet is caught before the push rather than by someone whose Manager cannot fetch it.

* The four feed buttons work in a right-to-left layout. Their positions are pixels worked out from where you drag them, and a mirrored layout was throwing those away, so the buttons piled up on each other and would not move sideways.

* The countdown on the hold panel is readable again. The panel is always a near-black scrim, so the countdown no longer follows the settings theme onto it.

* Two patches no longer depend on another patch being selected to be usable. Custom offline videos limit sets its own flag, so its two switches appear when it is selected and stay away when it is not, instead of following the Downloads patch. Hide BdTuring CAPTCHA popups now brings Hide CAPTCHA popups with it, because that is the patch that owns the switch it reads.

* The feature gate report says in the file that its values are redacted, so a gate marked changed showing the same text twice reads as the redaction rather than a mistake. The bundle description no longer claims more redaction than ships: the Lab own value export is a file you import back, so it keeps what it read.

* Every settings page with something on it has a row into it. The page and the row used to keep separate copies of the same condition and two of them had drifted, so a bundle with the block author patch but none of the four playback patches could reach the daily budget only through search, and Hide comment popup ads on its own had a switch no page would show. Each row now asks the page itself. The Playback row also counts a set budget as an active setting.

* The feature gate recording is redacted before it leaves the app. It is the one file the issue templates ask people to attach, and gate values are server configuration, so addresses and per-install identifiers were going out in it while every other export here was already cleaned. The values are redacted and the structure is not, so it is still a readable report, and the Lab itself still shows what it actually read.

## 0.22.0 (2026-09-07)

* The block button, the sound and Not interested buttons, the undo banner and the daily budget hold come back after TikTok rebuilds its main screen. The extension kept the first activity it was handed for the life of the process, so anything drawn over the feed went to a window that was no longer on screen, and nothing noticed because a rebuilt screen is destroyed rather than finishing.

* An AAC sound is saved as AAC. It shares its frame sync with MP3, so the reader that tells containers apart called it an MP3, which left a file the gallery would not play. The two layer bits separate them.

* Saving the original sound on Android 6 to 9 says what is wrong. Those versions write a real file, and without storage permission the save failed only after the sound had already been fetched, with nothing to act on. It is asked before anything is downloaded now, the way every other save here asks it.

* The original sound and animated stickers are fetched over TLS only. Both read their addresses straight out of a server response and took whatever scheme was in them, while every other saver here already required https. A cleartext mirror is a body anyone on the network can choose, and the sticker one is handed to a decoder written in C.

* Leaving the Feature Gate Lab while a reset, an undo or an import is still running no longer breaks it. The change finishes on the main thread and put the switch back without checking the screen was still there, so it crashed, and because that happened before the busy flag was released, every later Lab change was refused with a message about one already running until TikTok was restarted.

* A settings backup can no longer take the app down when it is restored. A Lab rule holds its structured value as a string, and the depth check on the file around it said nothing about what that string contained, so a deeply nested one overflowed the stack. That is an Error rather than an exception, so it walked past every catch on the restore path and killed the process. The value now goes through the same bounded reader the rest of the file does and is refused as invalid instead.

* Saving the original sound works at all. The fetch only ever accepted an MP4 container, and TikTok hands back an MP3 for many sound addresses, so every one of those was refused as an unsupported format and the long press said the sound could not be saved. What arrived is now read from its own header, and the file is named and typed to match, so an MP3 lands as .mp3 and plays. Ogg, WAV and FLAC are recognised too.

## 0.21.0 (2026-09-07)

* Long press can save the original sound. That is a different file from the video's own track, which is the finished mix cut to the length of the post: the sound entry is the whole thing, as it appears on its own page. It is named after the sound rather than the post, so the same song saved from two videos is one file, and it goes to Music beside your other sounds. A video with no sound entry says so instead of doing nothing.

* The hold on the feed stops above the row of tabs. It covered the whole screen, which took the tab bar with it, so messages and search were not reachable after all. It measures the bar rather than guessing at it, and covers everything on a build where it cannot find one.

* A short animated sticker with a single frame saves as a GIF again. Whether a WebP moves is settled by the flag in the file rather than by counting its frames, which cannot tell a still from a one-frame animation.

* Playback has a Start today over row, for a budget you set and immediately regret. It clears what has been counted today and ends any hold, and leaves the budgets themselves alone.

* Saving a sticker tells you where it went, not where it asked to go. The gallery renames a duplicate, so saving the same sticker twice used to report a path with nothing at it.

* The daily feed budget no longer costs anything when it is switched off, which is how it ships. It counted and wrote to storage on every player report whether or not a budget was set, several times a second. Nothing is counted now until a number goes in, the record is written on a background thread, and the watched time is committed in steps rather than on every report.

* Moving the hour the day starts at, or crossing a timezone, no longer hands back a budget that has been spent. Raising a budget now also lifts a hold that was running under the old one, and a hold no longer charges you for the videos playing behind it.

* The way out of a hold is on the hold itself. It was on a banner drawn underneath it, which meant it could be neither seen nor tapped.

* The budget settings only appear where the hook that feeds them was applied. They used to show on any build with a Playback patch, take a number and count nothing.

* The settings screens are checked at twice the text size and in a mirrored layout, on every page a reader opens rather than one of them, in both themes.

* The five feed count filters take a number the way the feed writes it. Type 20K, 1.5M or 2B instead of counting zeroes, and the row reads the range back in the same form. Nothing is rounded: 1.234M is exactly 1234000, and anything the field cannot read is refused with a message rather than saved as a number nobody meant.

* A download can no longer come out as name_2 when nothing of that name was in the folder it was saved to. The check that produced the suffix was looking in TikTok's private staging directory, which has nothing to do with where the file ends up, so it could only ever be wrong. The gallery settles a name that is already taken, and it does that without a race.

* Saving a still sticker as a GIF is refused rather than written out as a one-frame animation. The model calls anything whose type merely contains "webp" animated, and the decoder reports a still picture as one frame, so between them a plain picture could reach the animation path. The bytes now decide it, and the converter refuses anything with fewer than two frames.

* The Hook status row names the surfaces that are missing something instead of saying only that something is. It reads correctly when one surface has reported, its report is redacted and cleared along with the rest of the diagnostic data, and it follows the "Included diagnostics" choice like every other section. A surface that has stopped counting says so. None of this costs the feed anything: a lookup whose answer is already known no longer takes a lock while you scroll.

* A comment without a thumbs down no longer makes Diagnostics report the whole build as broken.

* There is a daily budget for the feed, off unless you set one. Give it a number of videos or a number of minutes and it says so once when the day is used up. Give it a hold as well and the feed goes behind a countdown for that long, while messages, profiles and search carry on working and nothing in the feed is thrown away. The day starts at four in the morning by default, and you can move that. Both counts and any running hold survive the app being killed. This is separate from the auto-advance limit, which only ever counted videos Hushfeed itself advanced past.

* The converter behind "save an animated sticker as a GIF" is covered by tests for the first time. It now has checks that the frame count and every frame delay survive the conversion, that a patch which replaces what is under it really clears that area first, that a frame asking to be cleared afterwards is, and that a still picture is refused rather than written out as a one-frame animation.

* Diagnostics has a Hook status row. Patches attach to code TikTok renames on every release, and until now a hook that lost its anchor failed quietly while the switch above it still read on. The row shows one line per surface: how many of the things it looks for are in this build, how many are not, and the first one that went missing. The exported diagnostic report carries the same table.

* Two settings-backup messages are translated again. Their wording gained a full stop at some point and the tables kept the older version, so a failed backup or restore spoke English on a German or Indonesian phone.

* The shared payload is 18,032 bytes smaller. It carried an About screen that nothing ever opened, which came with a browser view that ran scripts and loaded a page over the network, and two keep rules for libraries this project does not use.

* Undo on the seen video history now answers even when a newer clear has taken over, instead of leaving the row waiting on a reply that was never coming, and it says so. Watching videos also stops running a whole-table cleanup after every single one.

* Thirty-three translations for wording the app no longer uses have been removed, and the backup screen's undo row is translated again after its wording changed and left its translation behind.

* A caption file that is nested unreasonably deeply is now refused with a reason instead of killing the download in progress, and saving a video whose frames are unusually large no longer fails on Android 8 and older. Ordinary captions and downloads are unchanged.

* Settings search no longer treats an accent typed on its own as a match for everything, and it folds each setting's words once when the page opens rather than on every letter you type. Diagnostics also stop filling with a line for every page of search results when logging is off, which was pushing out the events around a crash.

* Choosing a download folder now sticks. The folder picker is its own screen, so Android often rebuilds the settings screen behind it, and the rebuilt one had forgotten which setting was waiting. It took the folder you chose and did nothing, without saying so. The waiting setting is now remembered by name across the rebuild, and a folder that arrives with nothing waiting says so instead of vanishing.

* The player's block, hide and Not interested buttons now say what they are in your own language. They are drawn as glyphs, so their spoken labels are all a reader using TalkBack gets, and those three still read English on a German or Indonesian phone. A build check now covers every spoken label the way it already covered messages.

* A saved subtitle no longer takes the whole download down with it. The gallery decides the video's filename and can hand back one with no extension, and cutting that name at its dot threw, which the download path reports as the save having failed even though the video was already on disk. The subtitle now keeps the whole name.

* A diagnostic report no longer carries the videos and comments you were looking at. Reports already hid addresses and credentials, but the id of a video, comment or message went through untouched, and each of those opens a post anybody can read. Both the named ids and the bare lists the feed probe prints are now removed before a report is copied or saved. The counts and timings that make a report worth reading are kept.

* The thumbs down on a comment blocks only the account you pressed. Where a press started was kept on a single object shared by every comment on screen, so a second finger, or the list reusing a row between the press and the release, could let a release act on a comment it never belonged to. Each control now keeps its own press, and a release with no press behind it does nothing.

* Saving a slideshow posted by an account with a very long name no longer stalls. Each photo's name is cut down to fit the filesystem, and the counter that tells one photo from the next was the part being cut, so every photo after the first landed on a name that was already taken and the search for a free one never moved on. A shortened name now carries its counter on the end, and the search gives up and keeps the original name rather than counting upwards forever.

## 0.20.0 (2026-09-07)

* An interrupted Feature Gate recording is discarded before the next baseline is taken, so a session stopped by its setting cannot make later reads look changed. The isolated boundary test now passes on its own as well as in the full suite.

* Automatic advance now has an optional per-session video limit. It counts only visible completions from Hushfeed-owned scrolling, keeps manual and native-only advance independent, stops with a brief notice, and resets when the feed component is recreated. Runtime coverage is 460 tests.

* Advanced downloads can hand a sanitized link to YTDLnis with its documented audio or video type and optional background mode. The profile controls activate only for `com.deniscerri.ytdl`; generic package names still receive a plain link, and an unavailable target leaves TikTok's save in place. Runtime coverage is 458 tests.

* Feature Gate Lab boundary tests now cover malformed persisted scalars, disabled profile imports, master and reset cycles, recorder enablement and concurrent observation limits. Translation retry state expires cleanly, and SIM preset matching keeps null current values and unsupported region fallbacks safe. Runtime coverage is 454 tests.

* Deep feed and account checks now cover repeated response caching, late and final follow delivery, cached and offline fallback policy, hard-filter preservation, bounded probe rotation and malformed verdicts. Every supported account-write route keeps its challenge visible, and the 160-event diagnostics cap is reserved atomically when callbacks arrive together. Non-finite or malformed status codes remain unconfirmed. Runtime coverage is 446 tests.

* Worker-backed settings and Feature Gate Lab tests now drain their owned executors before asserting, reset per-sandbox state before each case, and keep the region semantics check separate from the API ICU cross-check. Runtime coverage is 412 tests.

* Native boundary coverage now exercises structured numeric coercion and overflow, URL scheme refusal, destination roots, media fallback and frame bounds, plus direct navigation, share, LIVE, sound and translation policy shapes. Codec playback and final container behavior remain native-device checks. Runtime coverage is 434 tests.

* Global-layout hooks now detach a previous root when a replacement activity has no content view or resolvable author identifiers. Author decorations are restored when installation cannot continue.

* Video overlay layout passes now reuse their id, visibility and match buffers instead of allocating traversal containers each time. A synthetic 200-pass trace over 80 cells measured 57.68 ms before the change and 56.09 ms after it.

* Feed, inbox, share and sticker helpers now share resource lookup, global-layout ownership, reflection caching and publication cleanup. Replaced roots detach their listeners, dynamic-module misses keep retrying, and legacy sticker saves keep unique filenames. Runtime coverage is 409 tests.

* The extension payload no longer carries unreachable APNG conversion, retired language-editor or Lab preference code. Declaration-only fingerprints and unused compatibility metadata are gone, and the keep rule now covers only extension classes while preserving injected and reflective entry points. The shared payload fell from 229,024 to 221,720 bytes and the TikTok payload from 1,377,012 to 1,361,300 bytes.

* Release checks can now fetch the indexed Morphe bundle and its SHA256SUMS entry, comparing both with the locally built artifact before an index is promoted.

* Repeated fully filtered feed batches now explain the top rejection reasons without exposing post data and offer a direct link back to Feed filter settings. Empty server responses do not trigger the notice.

* Share sheet settings now discover observed video actions and show readable labels with stable identifiers. The checklist keeps unknown manually saved identifiers, updates the same hidden-action list, and leaves native share delivery and target identity unchanged.

* Feed filtering can now hide posts older than a chosen number of days. The switch is off at zero, and posts with missing, zero, future or overflowing timestamps stay visible. Quality fallback never restores an age-rejected post.

* The player now has a local creator-hide action. It records the current stable creator id without calling TikTok, skips that creator on later feed batches, and offers a searchable one-entry-at-a-time list with undo.

* Settings now have a translated search page. It matches titles, descriptions and category names, hides controls from unselected or unavailable patches, and opens the existing section at the matching preference without creating duplicate editors.

* Settings headers, captions and editor labels now keep Android's font padding, so large text has room for its full glyphs instead of clipping at narrow widths. Preference rows and native editors remain content-sized for 1.3x and 2x text.

* Native settings lists now use one selection indicator. Single-choice rows draw a radio at the leading edge and multi-choice rows draw a checkbox there, while the platform list adapter still owns checked state, selection and dismissal.

* Numeric settings now label their values as seconds, days or views per like where those units apply, with singular and plural wording in English, German and Indonesian. Existing stored values and unitless controls keep their behavior.

* Support forms now capture the TikTok package and version, Morphe and Hushfeed versions, selected patches, affected surface and ordered settings changes. `validate-release-facts.ps1` checks those public release facts and the runtime test count before an index is published, rejects failed, errored or skipped test cases, and the bundle description explains that diagnostic exports redact signed URLs and credentials.

* Release dependency resolution now disables `mavenLocal()` after the Morphe settings plugin adds its own repository, unless `-PallowMavenLocal=true` is supplied. The reviewed graph is recorded in Gradle verification metadata with SHA-256 checksums, and the wrapper distribution checksum now matches Gradle's published 9.7.1 binary while the smali and Morphe patcher pins remain intact.

* Comment translation checks the switch again before a delayed native call, reserves one request per loaded batch, retries failed completions and remembers only successful ones. Expiration uses elapsed time, generation allocation is serialized with reservations, and cleanup resolves a late failure or completion against the retired request before it can clear a newer retry.

* Diagnostic file exports now build and publish on the shared worker pool. The save reports null streams and failed publication, returns the provider's actual destination, and uses unique app-document files on Android 9 and earlier so repeated reports remain available.

* Interrupted media work now lives in an extension-owned cache namespace. Startup reconciliation removes stale temporary files and only pending MediaStore rows recorded by Hushfeed, while completed media and unrelated TikTok cache stay alone.

* Media saves now share a bounded worker queue. Transfers have byte, deadline and free-space budgets, transient mirrors retry with capped backoff, request data is snapshotted before enqueueing, and cancellation removes unpublished work without falling back to the UI thread.

* Media downloads keep trying later mirrors when an earlier URL is malformed. Each mirror owns its parsing, connection and transfer errors, aggregate failures omit signed query data, and a failed run removes a partial temporary file.

* Media retries now treat socket timeouts as recoverable, keep one deadline across every part of a queued save, and cap unusually large Retry-After values. Sticker saves try every supplied mirror and cap both downloaded bytes and decoded image pixels. Pending gallery inserts are journaled before insertion, and failed cleanup stays recorded until the provider confirms deletion.

* The settings journal now survives an AtomicFile backup left by a crash, compares Lab rules without depending on their display order, and covers edits made from the Lab detail pages. Seen-history loading can retry after a clear races a failed open, reset takes its journal lock before capturing defaults, and detached exports still clean up the selected document.

* Feature Gate Lab imports are now bounded while they are decoded. Raw JSON and gzip exports use strict UTF-8 and duplicate-key checks with depth, node, string, array and rule limits, and a failed export only removes the selected new document while reporting cleanup failures.

* Settings and Feature Gate Lab changes now share an on-disk operation journal. An interrupted write is reconciled at startup to the prior or committed state, the private Undo copy is retained, and malformed recovery records produce a visible notice.

* Seen-history undo stays available until its SQLite transaction commits. Rejected inserts and write errors report failure on the settings row, while a failed first database open clears its in-memory load gate so the next read can retry.

* Patch verification now compares names with ordinal matching, accepts only JSON booleans, fixes the TikTok target in the script, and treats an out of memory process as a failed measurement when the CLI did not exit cleanly. Block status parsing also rejects fractional values that only round to an integer.

* The extension payload now keeps its Android 23 promise. Download workers, history, feed state, Lab sorting and language selection use APIs available on the declared floor, and the TikTok module declares that floor explicitly.

* Patch verification now treats the desktop process, result report, requested patch set, target version and saved APK as one result. Heap measurements use the same checks, keep expected out of memory runs visible, isolate concurrent runs, and never remove a path outside the work directory.

* Blocking only shows the undo action after TikTok returns an exact status of zero. A missing, malformed or uncertain response leaves the current view alone and reports that the result could not be confirmed. The same rule applies when undoing a block from a comment.

* Diagnostic exports scrub ordinary selected events as well as crash reports. Signed URLs, token assignments and credential-bearing exception text are removed before copy or file export, and copied reports are marked sensitive for Android versions that support the clipboard flag.

* Settings restore reports whether the two preference stores returned to their prior state. Rejected backups, complete rollback and recovery-required failures have separate feedback, and a verified Undo copy remains available when recovery is needed.

* Persistent settings and Feature Gate Lab data now accept writes only from the package's main process. Secondary processes can still read current values, but stale snapshots cannot overwrite newer settings or consume migration state.

* Settings text that said the wrong thing. The thumbs down block claimed it needed a restart, which it never did. The two region switches claimed one and never asked for it, and now they do, because TikTok reads the country at startup. A dozen summaries that talked about secure window flags, native seekbars, candidates and pages now say what actually happens.

* Every message the patches show you is now in your own language, not just the settings screens. Blocking, saving, seeking, the confirm taps and the backup actions all spoke English on a German or Indonesian phone. The ones that name something, a handle or a file, are built so the translation decides the word order rather than having English word order with the name dropped in.

* German and Indonesian corrections. The German CAPTCHA message told you to turn off a switch by a name that is not on the screen, the speed list said to use commas as decimal points when the field only takes dots, and shares were translated as arithmetic division. Indonesian had the Custom offline videos row reading as its own limit row, and For You left in English where TikTok's own Indonesian build says Untuk Kamu. Six German titles that were long enough to be cut short have been shortened.

* Three things about the settings screens. The comment search box follows your phone's theme, has a border so it looks like a field you can type in, gets a search key rather than a newline, and is a proper size to tap. Each section says the same thing on the home page as it does at the top of its own page, where the two used to disagree. And the Diagnostics page keeps the backup actions together at the end instead of scattering them between the logging switches.

* The undo on the block banner is a real target now, announces itself to a screen reader, and says Undo in your own language. Clear all on the suggested accounts row is translated too, and readable on a light inbox rather than washed out.

* Turning off the profile picture or story save gives TikTok its own long press back. Both took over the gesture while switched on and then kept it after being switched off, answering that they had not handled it, which is not the same as handing it back. Holding a story is how TikTok pauses it, so that one was worth getting right.

* Four bounds on what a download will accept from the server. A sticker is read with a ceiling instead of until the phone runs out of memory, a mirror that is not an HTTP address is skipped rather than abandoning the ones after it, a file over 2 GB has its length checked properly, and a filename built from a very long name is cut to what a filesystem takes without splitting a character in half.

* Handing a video's link to another downloader now strips the tracking from it, the same way sharing a link does. TikTok's own link carries parameters that say who sent it, and those were going out with the hand-off even with Sanitize sharing links turned on.

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
