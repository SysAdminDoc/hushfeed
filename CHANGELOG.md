# Changelog

Every Hushfeed release, newest first.

## Unreleased

* **TikTok:** Camera and microphone indicator draws a green square for the camera and an orange diamond for the microphone, so telling them apart doesn't depend on seeing green and orange. The text on Hushfeed's banners over a video is easier to read against a bright frame or TikTok's light comment sheet.
* **TikTok:** In settings, the back arrow is announced as a button, and the search box's clear button, the Lab's switch rows and its Technical details heading show when they're pressed or focused. A chosen SIM preset no longer looks like the row that has focus. When a budget change or Pause is refused, the reason stays on screen in the settings banner instead of a toast that was gone before the time in it could be read.
* **TikTok:** Settings text reads more naturally, with contractions throughout, one way of asking you to restart TikTok and one of saying to try again in a moment. Number rows show their range the way they show the value, as in "0 to 86,400 seconds". The Feature Gate Lab says On or off where its filter said Boolean, tells you what to fix in a bad value or bad JSON, uses a real ellipsis while it loads, and its menu has a title and a Cancel.
* **TikTok:** The settings home's status mark is a rounded square now, on the same corner scale as the cards and icon tiles, and the three quick routes no longer sit on the search card's edge. The Feature Gate Lab's icon buttons show where keyboard or d-pad focus is, which the round ripple they had never did.
* **TikTok:** The page that opens when settings can't load now has a proper title, and Retry is drawn as the way forward instead of a second plain row next to Back. Choice dialogs line their marks up under the title and show Cancel in the dialog's own language. What's new offers Got it and Later and has an icon of its own. When Undo or Restart now fails, the message says which one failed, where it used to talk about undoing a clear either way.
* **TikTok:** A What's new row sits at the top of Hushfeed's settings after an update. It opens the changelog for the version you're on, and for any releases you skipped since you last dismissed it. Later keeps the row for another look, and Got it takes it away until the next release.
* **TikTok:** Hide Play Store update offer is a new optional patch. On the S22, Play changed from Update to Open while Morphe Manager kept showing TikTok 47.0.3. Android won't install a lower-code APK over this one, so leave the patch off if you expect to switch back without removing the app.
* **TikTok:** The settings home has pink section icons, quieter group labels and a warmer active card in dark mode. Search now has its own framed field and a separate results card. Section pages keep pink headings and larger row titles.
* **TikTok:** Repost diagnostics writes the request, TikTok's response status and the next repost-list result into a diagnostic report when logging is on. It keeps the video's ID and any repost note out of the report.
* **TikTok:** Taking LIVE off the top feed tabs now keeps the corner LIVE button available, just as taking LIVE off the bottom bar does. Hide the LIVE button still works on its own.

## 0.60.0 (2026-09-25)

A small release with three fixes. Comments that TikTok dropped without a word now go out, the caption no longer shows over the video when Caption above comments is on, and Allow Duet and Stitch covers creators who limit duets on their whole account.

* **TikTok:** Comment send fix, a new patch that's on by default, covers a way TikTok drops a comment without a word. TikTok checks a send against the page opened most recently, and when that page has already lost its screen, the check stops the comment and shows nothing, so the text just stays in the box. The check now gets the comment panel's own screen in that case. The diagnostic export names the page that had none, so a report from a phone where comments still don't post says whether this was the reason.
* **TikTok:** With Caption above comments on, the caption over the video goes away, since the whole caption now sits at the top of the comments. It used to show in both places. Hide the caption still hides it on its own.
* **TikTok:** Allow Duet and Stitch now answers the creator's account-wide choice as well as the video's own. TikTok checks both, and on the S22 a video that allowed anyone still had no Duet entry because its creator's account allowed only people they follow back. With the switch on, Duet and Stitch both show on those videos now. Whether TikTok's servers accept the upload is still up to them.

## 0.59.0 (2026-09-25)

The biggest release so far, with everything built since 0.58.0 and this week's bug reports. The seek bar that TikTok's experiment took away is back (#16), Watch history works with the browser guard on (#16), the tab names above the feed can go (#32), a video can fill the screen (#29), the corner LIVE button survives a filtered bottom bar (#28), split comment view follows the fold (#26), and the Products block in search has a switch (#21).

* **TikTok:** Taking the LIVE tab off the bottom bar no longer takes TikTok's LIVE button off the top of the feed too. TikTok hides that corner button while LIVE has a bottom tab, and it checked its own list of bottom tabs, which still had LIVE after Filter bottom tabs removed it, so LIVE had no way in from the feed (#28). Hide the LIVE button still hides the corner button when you want it gone.
* **TikTok:** Fill the screen with the video, a new switch under Playback beside Fit the video to the screen, crops a video until it covers the whole window, for the phones where TikTok leaves a black strip under a 9:16 video (#29). Turning either of the two switches on turns the other off.
* **TikTok:** Hide the tab names above the feed, a new switch on the Feed tabs page, takes the Following and For You names off the top of the feed. Swiping between the feeds and the search button stay (#32).
* **TikTok:** Show the progress bar now beats the two TikTok experiments that hide the seek bar. One hides it on every video except paid content for whoever the server puts in that group, which left the bar gone from the whole feed but present in the drama player, and it can come and go between restarts (#16). The other hides it on any video the server hasn't marked as draggable. Both ran before Hushfeed's switch was asked.
* **TikTok:** Wait a day to loosen the budget, a new switch on the Screen time page, keeps a change that loosens the daily budget until the day starts over, while one that tightens it applies at once. A higher budget or none, a shorter hold, more times to open the feed anyway, another hour for the day to start and the switch itself going off all count as loosening. The row says what it will change to and at what time. Restoring a backup, resetting and Undo follow the same rule, and Start today over and Pause Hushfeed are refused while the switch is on, since each would take the budget off at once. A restore on a locked day now leaves every budget setting as it is, where before it could hand a locked day a looser budget.
* **TikTok:** More opens the comments, a new switch under Comments, makes tapping more under a video open its comments with the whole caption as the first row, instead of spreading the caption over the video (upstream #156). A tap on the caption text does the same. The row is TikTok's own, the one its comment list shows when an opener asks for it, so the caption keeps its links and its own more and Hide. Caption above comments, the switch beside it, does the same for the comment button. Photo posts keep TikTok's own page for this, which already puts the caption above the comments, and ads keep TikTok's handling. Both switches are part of Comment tools, which now brings Double-tap controls with it to open the comments. On the S22 more on a video opened its comments with the caption on top in place of TikTok's Tako panel, the comment button did the same with the second switch on, a photo post still opened TikTok's post page, and with the switches off everything was TikTok's again.
* **TikTok:** Three new switches under Feed screen > Gestures let a long press on Comment, Share or Favorites play the video at the hold speed, the way it did before TikTok gave those buttons menus of their own (upstream #87). With its switch off a button keeps TikTok's menu, which is the emoji row on Comment, the quick share row on Share and the new collection offer on Favorites. The hold is TikTok's own and shows its speed banner. Letting go doesn't count as a tap, so it doesn't open the comments or save the video. A press TikTok won't hold, because the finger slid off the spot or a second finger landed early on, opens the button's menu as before. The switches need the Long press row on TikTok default and Seek from the edges off, since both take the right side of the screen for themselves, and they grey out otherwise. They're part of the Long-press controls patch. On the S22 a hold on each switched-on button played at 2x with no menu and went back to normal speed on release. A hold on Favorites left the video unsaved, and a tap on Comment still opened the comments. Every menu came back with its switch off, and with edge seeking on.
* **TikTok:** Show poll results before voting, a new switch under Comments, draws a creator's comment poll with its bars and percentages before you pick an answer. The counts already come with the comments whether or not you've voted, and TikTok only waits for your vote or the poll's end to draw them. Tapping an answer still votes, and no answer is marked as yours until you do. It's part of the Comment tools patch. On the S22 a poll the test account hadn't voted on arrived with every answer's count and the total, and with the switch on it drew 72, 26 and 2 percent of 276 votes with no answer marked, where TikTok's own row showed only the answers.
* **TikTok:** Show TikTok's feed buttons, a new switch on the Feed tabs page, puts TikTok's own play and pause button and its previous and next buttons on the feed. TikTok builds that row into every feed page for people who use a screen reader, and shows it only while one is running. With the switch on it shows for everyone, and a change to the switch shows as soon as you're back on the feed. TikTok's own analytics note the row and each press on it, as they do for screen reader users. TikTok's layout lets each button take focus on a tap, which a screen reader never runs into, so the patch also makes each one act on the first tap instead of the second. Previous on the first video of For You reloads the feed, as it does with TalkBack, unless Keep For You on a pull down is on. On the S22 each button acted on the first tap whichever one was tapped before it, and the row stayed hidden with the switch off.
* **TikTok:** Keep a paused video paused, a new switch on the Screen time page, stops TikTok from starting a video you paused when you come back to the app. TikTok plays it again twice as it returns, once as the feed resumes and once as the video's surface is rebuilt, and both plays go through one method, which now turns down a play of that video for three seconds after the return. Your own tap plays it as usual, and so do TikTok's feed buttons and a screen reader's play action. A video you left playing starts again the way it always did, whether you went to the home screen or to another TikTok screen such as your messages. It needs Android 10 or newer, the first version that lets the app read the video before TikTok stops it, and the Block author button patch, which carries the other playback switches too. On the S22 a paused video stayed still after a trip to the home screen, played at a tap, and one left playing picked up again on return. TikTok doesn't draw its play button over the kept video, so it shows as a still frame.
* **TikTok:** Open TikTok on, a new choice on the Feed tabs page, picks the tab TikTok starts on when you open it from its icon: For You, Following, Friends, Inbox or Profile. TikTok default leaves the pick to TikTok. A notification or a link still opens where it points, and a TikTok that Android restores goes back to the page it was on. A tab hidden on the same page isn't opened, so TikTok starts where it would have, and neither is Inbox without a signed-in account, since TikTok sends a start there to its login screen. On the S22 a start from the icon opened on Inbox, Profile, Friends, For You and Following in turn, a hidden Friends or Following tab left TikTok's own pick in place, and a start carrying a notification's tab still went to that tab.
* **TikTok:** Two new switches on the Feed tabs page keep For You where it is. Keep For You on a Home tap stops a tap on Home, or on the For You tab while For You is showing, from reloading the feed, and Keep For You on a pull down does the same for a pull at the top of the feed. Both are off by default, and the other feeds refresh as before. A kept refresh ends the way TikTok ends one it skips, so the Home icon turns back from its refresh arrow and no spinner hangs. TikTok's own reloads, like the one after you block the creator on screen, still go through. Keep For You on a pull down also covers the Previous action TikTok's accessibility tools offer at the top of the feed. On the S22 a Home tap and a pull each kept the video playing with its switch on and reloaded the feed with it off.
* **TikTok:** Confirm feed interactions has three new switches under Feed screen, for a comment's like, a story's like and TikTok's quick repost from the bar under a video. Each is off by default. The first tap arms and a second one within four seconds goes through, the way the Follow and like switches already work, and removing a like stays immediate. A comment's like asks from the heart in the comment list, from a double tap on the comment and from the like on a comment's photo page. A repost from the bar asks whether it comes from the bar's repost button or from the repost guide TikTok shows on the same bar, since both send it at once. On the S22 a comment's heart and a story's heart each armed on the first tap, liked on the second and unliked at once, and with the switch off one tap liked as before. The photo page's like and the quick repost bar didn't turn up for the test account, so those two haven't been seen on a phone yet.
* **TikTok:** The diagnostic export now counts the strips TikTok shows above a caption and the banners under a video, by kind, whatever the logging switch says. A kind is TikTok's own key for it, such as a place or an effect, and never what the strip says. Some phones get strips that promote something else, like a Lemon8 link or a "Find ..." search, and a switch that hides them needs those keys, so an export from such a phone now carries them. With diagnostic logging on, a banner kind that first showed up before logging went on used to stay unnamed until TikTok restarted. It's named the next time it shows up now.
* **TikTok:** AMOLED dark theme paints the right colors on TikTok 47.0.3. TikTok added a color ahead of its dark palette in 47.0.3, which moved every name in it one along, and the patch kept rewriting the 46.x names. So it turned TikTok's pink-red accent, an orange and two see-through overlays black, and it left four of the five dark grays as they were, among them the one fifty of TikTok's layouts use. Each build now has its own palette, read off that build, and on a build nobody has read yet the patch stops with nothing changed instead of guessing. On the S22 the only colors the rebuilt table changed were those five grays, and TikTok ran in Dark as before.
* **TikTok:** Hide mini dramas now takes TikTok's Short Drama block out of search results as well. That's the header with an Explore button, a row of category chips and a strip of dramas that a search for "short drama" opens with. The block isn't one of the results the other search filters read. TikTok streams the Top results in pieces and builds it afterwards as a server-drawn card, so it's now caught where its row is drawn and folded away. On the S22 a search for "short drama" showed the block with the switch off and went straight to the videos with it on. The diagnostic export also names each server-drawn card a search shows, by its template and never by what it says, so a report of another block can say which one it was.
* **TikTok:** Disable telemetry now stops the analytics uploads themselves. With the switch on, TikTok's AppLog SDK still sent its queued packs to the log hosts (session starts and ends, and events that reach it past the entry points the switch covers). Its forward worker posted copies of them to a second path and a backup, and the install SDK checked in once a start with the advertising id, carrier, SIM region and time zone in the query. On the S22 that came to 10 requests and 134 KB over 20 videos, about as much as with the switch off. Three walks since the fix sent none. The pack send answers as if the pack had gone out, so the SDK clears its queue and nothing piles up for later. The forward send stops before its post, and the SDK has already dropped those rows by then. The check-in answers success without leaving the phone. The SDK's priority uploader gets the same treatment. It sent nothing on the S22, but a setting it stored earlier could turn it back on. Device registration and the settings fetch aren't touched. With debug on, the network report also writes the path of each request to a log host into the log, so a leftover line in the report can be traced to the SDK behind it.
* **TikTok:** The comment keyword filter now reaches comments TikTok shows translated. It judged a comment's text when the page loaded, and a translation landed in that same text afterwards, so a blocked word the original never had could still show up: with batch translation on, 2 of 14 shown comments on the S22 held the blocked word. A translation is now judged as it arrives, before TikTok shows it, and again whenever a translated comment is drawn, and a comment whose translation holds a blocked word collapses out of the list. TikTok's own translations count too, and Comment tools does this on its own, without Translate comments. A collapsed comment comes back as soon as the filter is off. Comments shown in their original language are judged as before, and a voice comment's translated transcript is left alone, like its original one.
* **TikTok:** The Bottom tabs to keep row under Feed tabs now says to open the feed once while TikTok hasn't been seen loading a tab yet, and its dialog says the same above the two tabs every install has. The note was written for both tab editors but could never show for this one: Home and Profile are always offered, so the two rows counted as tabs already seen, and the row said All loaded tabs when nothing had loaded. Select every tab waits for a seen tab, as it does for feed tabs.
* **TikTok:** The Long press row under Feed screen > Gestures gains Find the sound on YouTube Music. A long press opens YouTube Music's search for the sound's title and artist, the pair TikTok's own sound page shows, so the track can be saved to a playlist there. TikTok used to offer that and took it away. Nothing is sent anywhere else and no account is involved. Without YouTube Music installed, or for a sound with no title, a short toast says so and nothing opens, and so it does for the video's own original sound, which YouTube Music won't have.
* **TikTok:** New Creator exceptions row under Feed filter, beside the block lists. Name a few accounts by handle or id and their videos stay when only a preference filter would have hidden them: stories, photo posts, location tags, AI labels, verified accounts, playlists, age, length and the view, like, comment, favorite and share ranges. Ads, blocked creators, words, sounds and countries, paid and Shop content, LIVE, mini dramas, Series and seen videos still hide them. The row names any entry a block list also holds, since that creator stays hidden, and refuses a pattern or a display name before it saves. One that arrives in a settings backup is ignored and named on the row. The list rides along in a settings backup with the other rule lists.
* **TikTok:** Hide the Tako bubble now takes the Ask Tako pill off the search results page too, the first tab on every search that the switch had left standing. It isn't one of the served tabs but a view TikTok builds into the strip, so it is hidden by the id name its text keeps once the page is built. The other tabs keep their order, and switching it off brings the pill back after a restart. The served tab list is filtered as well, for a build that ever serves the tab as data, and with diagnostic logging on the export lists the tab keys the strip held.
* **TikTok:** A save of three or more files (a post's original photos, a story's photos, a video with its sound and subtitles) now shows a running count in a row above the save banner, with a Cancel that lets the file under way finish and leaves the rest. A file that fails is skipped instead of ending the save, and the result says how many landed, how many were skipped and whether the rest were cancelled, or stopped for lack of space or time. The row is read out once when it appears, once when Cancel is pressed and the result once at the end, not on every file.
* **TikTok:** Saving a live photo from a comment now keeps its motion. TikTok's own Save photo hands its downloader the still alone, so the clip never landed (upstream #169). Once TikTok has the comment and the tapped photo in hand, Hushfeed fetches the clip and saves it beside the still as an MP4, with a banner of its own. A plain photo comment saves as before. Needs the Downloads patch. Not yet seen on a phone: no live-photo comment turned up on the test phone, so the clip's own landing there is unverified.
* **TikTok:** Remove ads now reaches the cold-start TopView, the full-screen ad TikTok can open with. Its ads never pass through a feed list the filter sees: the feed fetch hands them straight to TikTok's splash service before the list is filtered, which is why an earlier reset of that list at filter time reached nothing. The list is emptied as TikTok reads it, so TikTok's own "nothing to preload" path runs, the one it takes on every start served no ads. The diagnostic export carries a TopViewPreload line saying the route ran, ads or none. The test account is served no TopView, so the empty handoff was checked on a phone and the ad itself gone was not.
* **TikTok:** The speed menu's toast now names the speed you picked. Choosing a custom speed such as 2.5x played at 2.5x but the toast said "Playing at 2x speed", because TikTok labels a custom speed with its nearest built-in and only 0.5, 1.5, 2 and 3 have their own. It reads "Playing at 2.5x speed" now, and TikTok's own speeds are unchanged.
* **TikTok:** New switch under Screen time, Leave when TikTok says time is up. TikTok's own daily screen-time reminder (the full-screen card once the limit under TikTok's Time and well-being settings is spent) sends the app to the background instead of waiting to be dismissed. Coming straight back within a few minutes leaves the reminder on screen, so the limit and the switch itself stay reachable. (upstream #2)
* **TikTok:** Every full patch run on a fixture now checks the rebuilt resource table against TikTok's own, across all twelve of its packages. A resource that no longer resolves, a lost value or style item, a missing file or a reference to nothing fails the run and names the id. That's the kind of failure behind upstream's crash on a layout in TikTok's search package (upstream #84). The report also lists every value the patches changed, which is how the dark theme's wrong palette on 47.0.3 turned up. On all five fixtures, TikTok 46.2.3 to 47.0.3, every patch applies and every one of the 80,632 to 83,292 resources resolves.
* **TikTok:** Every runtime view ID Hushfeed uses is now tied to TikTok 47.0.3 code, including share actions, comment dislike controls and caption text. Each one is also told apart from the other ids the same TikTok class or layout loads, by the view it is set on, cast to or handed to, so a build that moves a name to a neighbouring view fails the test instead of passing it. Every lookup in the code is traced back to that table too, whatever helper, loop or constant carries the name, along with the package it resolves under.
* **TikTok:** In-app browser privacy guard now protects external websites without breaking Activity center, Watch history, shop checkout or CAPTCHA. Its Privacy switch has clearer wording and still starts off.
* **TikTok:** New Pause Hushfeed switch at the top of Hushfeed settings. From the next start TikTok runs as if it weren't patched, which answers "is it Hushfeed?" without turning things off one at a time, and your settings stay put for when you switch it back. Hushfeed also pauses itself after TikTok crashes within a minute of starting three times in a row. If the settings can't be reached at all, an empty file named `hushfeed-safe-mode` in TikTok's folder under Android/data pauses it too.
* **TikTok:** New switch on the Feed filter page, Hide TikTok Shop in search, takes the Products block and single product cards out of search results. No account here is shown that block, so if it still turns up for you, a diagnostic export made right after that search now says what kind of card it was. (#21)
* **TikTok:** Foldable split comment view now follows the fold while TikTok is open. Unfolding past your width setting refreshes the feed so comments can sit beside the video, and folding back refreshes it again. Before, it only took effect if TikTok was started already unfolded. (#26)
* **TikTok:** AMOLED dark theme works on an .apkm again when Morphe Manager merges it. It used to refuse every merged bundle, because rebuilding the desktop CLI's merge made TikTok crash at launch. Manager's merge keeps TikTok's own file paths and loses nothing, so the dark theme now refuses only an APK whose paths clash with the rebuild, which is what the desktop CLI's merge still produces. (#21)
* **TikTok:** Hide comment popup ads now stops the brand animation itself. On 47.0.3 it only turned off TikTok's analytics report about the animation, so the animation still played. TikTok's own celebration when you post a video's first comment still plays. It now tells TikTok's own celebrations from a campaign's by where the surprise comes from rather than by what is written on it: a surprise sent with a comment page stays only when the page was fetched for one of TikTok's own scenes, a surprise sent with a comment you posted stays only when it is the first-comment kind, and the replayed first-comment milestone always stays. Before, a campaign's surprise with no trigger word written on it played, and a celebration with one written on it didn't. Comment pages and posting a comment were checked on a phone, where no surprise came either way. The celebrations and the campaign case were checked against TikTok's code, not with a real campaign or a first comment on a video that had none. Tell us if one still plays for you, or if a celebration stops.
* **TikTok:** AMOLED dark theme now blackens 47.0.3's sheets and panels too, the comment panel and the share sheet among them, along with menus and pop-ups. They had stayed dark gray, because 47.0.3 paints them from a different color than 46.x did.
* **TikTok:** New Swipe-left controls patch. A left swipe on a feed video opens the creator's profile in TikTok, and the new Swipe left row under Feed screen > Gestures can make it do nothing or open the video's comments instead. Only a swipe toward the profile changes. Swiping right still opens TikTok's side panel, a photo post keeps its own swipe between pictures, and where TikTok doesn't page to a profile itself (the Inbox, your own profile) nothing happens. Changes take effect without a restart.
* **TikTok:** Video download quality now takes effect on TikTok 47.0.3. Hushfeed looked for the list of qualities under the name older versions used, found nothing there, and every chosen quality fell back to TikTok's own save. A saved video is also never taken from ByteVC2, TikTok's own format that other players can't open, and at the same size H.264 comes before HEVC. When only ByteVC2 is left, TikTok's own save runs as before, and when the size you picked comes only as ByteVC2, TikTok's own watermark-free file is saved if it says it's closer to that size, rather than a smaller copy.
* **TikTok:** Save the original photos works on TikTok 47.0.3. A photo post's Download never reached the step Hushfeed took the save from, so TikTok's own save ran and no original was fetched. Now "Download image", a photo saved without asking, and the photos picked in TikTok's selection sheet save the originals under your filename template, numbered by their place in the post. A live photo you chose still comes down as TikTok's video. TikTok's picker for posts with several photos goes through the same step and saves only the ones you pick, but it wasn't tried on a phone yet. One photo now says "Saving one original photo".
* **TikTok:** Turning automatic clear display off while it had the controls hidden no longer keeps TikTok's top bar, and its search button, away on the next videos. TikTok brought its other controls back by itself, but Hushfeed still thought they were hidden. The same went for a feed item that comes without a video id after a cleared video, with clear display automatic or remembered.
* **TikTok:** Clear display, TikTok's own or the automatic one, no longer lifts the daily hold. It hides TikTok's tab bar, and Hushfeed took that as you leaving the feed, so once the budget ran out the hold's panel came down and the sound came back. Now the panel stays up. With Remember clear display patched in, TikTok's controls come back as the panel goes up, so the Inbox and Profile tabs the panel points you to are right there, and nothing clears again until the hold is over. Without it, TikTok's own clear display bar stays uncovered under the panel, and its X brings the tabs back. The same mistake hid Keep captions in clear display as soon as the controls cleared. The caption stays now, but never on top of the hold's panel. The Block and Not interested buttons still go away with TikTok's controls.
* **TikTok:** A finished save now offers a way to it. Saving a video at a chosen quality, a story, original photos, a profile picture, a sound or an original sound used to flash a two-second toast with a folder path. The same words sit on the six-second banner now, and its Open button hands the saved file to a viewer. One banner per save: a video or story that carries its sound as well announces the sound with a plain toast beside the banner. Saves made of several photos open on the post's last one. Where there is nothing to open, or no app on the phone opens that kind of file, the toast says so.
* **TikTok:** A bigger caption size no longer splits words in the middle. The caption kept the width TikTok measured for its own smaller text, so at size 28 a word could break across two lines ("conditio" over "ns."). It lays out wider now, up to TikTok's own caption width so it stays clear of the buttons on the right, and longer lines wrap between words. Only a single word wider than that still breaks. At any size the caption's box now fits its text, with no empty band beside it, and the size is applied to the caption itself, so other text TikTok draws the same way, such as a video's description, keeps TikTok's size.
* **TikTok:** The daily hold pauses the video on TikTok 47.0.3 again. The panel went up but the video kept playing behind it, because the pause Hushfeed called by its TikTok name had turned into something else in 47.0.3. The hold now finds TikTok's own pause and resume where TikTok's feed uses them, and Open the feed anyway picks the same video back up.
* **TikTok:** The two pause switches work on TikTok 47.0.3. Silence the feed while comments are open did nothing there at all, sound included, because 47.0.3 draws the comments into the app's own screen rather than a window of their own, and a window was what the switch watched for. It goes by the comment sheet itself now. Under "Tap to start the feed" after coming back to the app, the video kept playing, since 47.0.3's player plays on when something else asks for the sound. Both switches now pause the video with the same TikTok pause the daily hold uses, and it carries on from the same spot when the comments close or you tap. A video that was already paused when the comments opened stays paused, turning the switch off with the comments still open leaves the video quiet until you tap it, and the comments switch's description says it pauses now.
* **TikTok:** New Network request report patch, off by default. It counts the requests TikTok's own API client sends, grouped by domain and by kind (API, log, monitoring, messages, LIVE and the rest), and adds them to the diagnostic export. Video and image downloads and other companies' SDKs use connections of their own and aren't counted, and the report says so. Nothing about the requests changes.
* **TikTok:** Feed filter adds Hide mini dramas, for TikTok's short drama episodes and the cards that promote them. It's off by default. Hide Series already caught drama episodes, since TikTok sells them as Series, but this one leaves other Series alone. (upstream #155)
* **TikTok:** Feed tabs adds Hide the unread badges on the bottom tabs, for the red counts and dots on Inbox, Profile and any other bottom tab. The inbox itself still shows what came in.
* **TikTok:** Playback adds Hide TikTok's Auto scroll button, for readers who want videos to advance on their own but not TikTok's own Auto scroll action in the video panel.
* **TikTok:** Playback adds Speed while you hold the video, so the hold gesture and its pull-down lock run at 1.25x to 3x instead of TikTok's fixed 2x. TikTok's own banner, lock guide and lock toast say the speed you picked.
* **TikTok:** Leaving Hushfeed's settings search with the Back arrow now takes the keyboard down with it. It used to stay up over the next page until Back was pressed once more.
* **TikTok:** The four optional cleanup patches accept APKMirror's 47.0.3 bundle too, the way they accept 46.2.3's. A merged 47.0.3 bundle carries the full APK's files less 92 armeabi-v7a libraries, byte for byte, and 27 of the 64 language packs. Every file they empty is still checked first. (#21)
* **TikTok:** Hushfeed's settings now survive TikTok's own crash recovery. After three crashes in a row at launch, TikTok deletes every settings file it doesn't recognize, and that used to take all of Hushfeed's settings with it, Lab rules and the Calm feed undo included.

## 0.58.0 (2026-09-21)

This week's bug reports, two requested switches and Turkish.

* **TikTok:** Hide series no longer empties creator profiles. TikTok 47.0.3 tags every ordinary profile video as episode 0, and Hushfeed took that for a series. Only a real episode number counts now. (#20)
* **TikTok:** Hide the Tako bubble now also removes the Ask Tako button floating over the search page, including the version paired with a Voice button. (#22)
* **TikTok:** The Save media button is back on comment stickers. TikTok renames its button class with every build, so the button had quietly gone missing since 46.7.3. Hushfeed finds the sheet's buttons by their layout now. (#23)
* **TikTok:** Cut the in-app browser off from TikTok now says plainly that it also stops Activity center and Watch history from responding. If Watch history ignores your taps, turn that switch off under Privacy. (#16)
* **TikTok:** Feed screen adds Hide the Report button on videos, for the flag button some regions get above the creator's picture. App adds Hide search rewards, for the points banner under the search box and the coin counter floating over results. Both are off by default. TikTok doesn't show either one in our region, so they were checked against the app's code and not on a phone. Tell us how they behave for you. (#21)
* **TikTok:** Hushfeed's settings now come in Turkish. It's a first pass, and corrections from Turkish speakers are very welcome. (#13)

## 0.57.0 (2026-09-20)

Block a creator and move on.

* **TikTok:** The one-tap block button now skips to the next video as soon as TikTok confirms the block. A small Unblock button appears at the top left for two seconds instead of the large bottom banner. It always reverses the original block, even after the video changes.
* **TikTok:** If you've already scrolled or left the feed while a block is being confirmed, Hushfeed won't move you again. Failed requests don't skip a video. Other Undo notices keep their existing recovery time.

## 0.56.0 (2026-09-20)

Give video controls the space used by suggested searches.

* **TikTok:** Feed screen adds Hide the search bar below videos. It removes the suggested-search strip above the bottom tabs so video details and side controls can use that space. The top search button and suggestions above comments stay unchanged. It's off by default and needs a restart.
* **TikTok:** Includes the independent location badge hiding and location-tagged video filtering from 0.55.0, plus Full screen button hiding and Easier comment likes.

## 0.55.0 (2026-09-20)

Choose which video overlays you want to see.

* **TikTok:** Feed screen adds Hide the Full screen button and Hide location labels. Each has its own switch inside Hide video overlays. Hiding a location card doesn't filter the video or change location permissions. Full screen hiding leaves playback and automatic rotation alone. Both options are off by default and need a restart.
* **TikTok:** Location hiding covers multi-place badges too. Filter location-tagged videos is a separate option under Feed filter > Ads. It skips videos with place badges, whether or not they're paid ads. Hiding a badge doesn't erase the location markers used by the filter. Both controls are optional.
* **TikTok:** Includes Easier comment likes from 0.54.0. The heart can receive taps in nearby blank space without making comment rows taller. Enable it under Comments.

## 0.54.0 (2026-09-20)

Comment hearts are easier to tap.

* **TikTok:** Comment tools adds Easier comment likes. It extends the heart's touch area into nearby blank space without enlarging the icon or adding space between comments. Text, Reply and neighboring controls keep their own areas. Swipes and interrupted presses don't turn into likes. It's off by default and needs a restart.

## 0.53.0 (2026-09-20)

Give the comment list more room.

* **TikTok:** Comment tools adds Compact comment header. It removes the comment count, sort and close buttons, plus the suggestion area above them, including its empty space. Comments and the reply box stay available. Use Back or swipe down to close the panel. Headers with list-navigation tabs stay visible. The switch is off by default and needs a restart.
* **TikTok:** Fixed a local release check that could block a new tag even though its source commit was already on GitHub. Update-index changes still require a published, verified bundle.

## 0.52.0 (2026-09-20)

Comments get their own suggested-search control, and search settings now say where they work.

* **TikTok:** Comment tools adds Hide search suggestions above comments. It removes TikTok's "Search: ..." banner above the comment count without removing comments, their controls or Hushfeed's separate search box. It's off by default and needs a restart.
* **TikTok:** Search controls now distinguish the feed button, Inbox button, LIVE drawer, image-search prompts, main search-page suggestions and searching within loaded comments. Poll hiding also explains that it removes voting cards and ended poll results.
* **TikTok:** Fixed the Hushfeed source metadata that made Morphe report "The remote metadata file is unavailable." Refreshing the source is enough. The release check now rejects timestamps Manager can't read.

## 0.51.0 (2026-09-20)

Commission posts now use the same location-affiliate signal TikTok checks before showing its disclosure.

* **TikTok:** Remove feed ads catches location videos marked "Creator earns commission" even when TikTok leaves the shopping and standard ad fields empty. It filters the video before display, without matching English text. Ordinary location tags aren't enough to remove a post.
* **TikTok:** Paid partnerships and branded content are included in Remove feed ads. The separate paid-partnership switch remains available, and the related controls now sit together under Ads.
* **TikTok:** When For You is the only remaining top tab, its title and underline are hidden. The feed and Search stay available.
* **TikTok:** App settings can hide the purple rewards shortcut beside Add friends on Profile. This belongs to Hide floating promotions and is off by default.

## 0.50.0 (2026-09-20)

Settings failures are recoverable, movable controls stay reachable and the largest settings catalog no longer ranks searches on TikTok's UI thread.

### Interface and accessibility

* **TikTok:** Large translated settings titles now keep words whole at enlarged text sizes. The title adapts within a limited range while Android's chosen font scale remains unchanged everywhere else.
* **TikTok:** Movable feed controls now stay clear of status bars, navigation insets, display cutouts and TikTok's bottom tab row. They recheck those boundaries whenever the window geometry changes.

### Performance

* **TikTok:** Feature Gate Lab now precomputes searchable gate text and performs fuzzy ranking on a cancellable worker. Typing a newer query immediately invalidates older results, so a slow search can't replace what the reader just requested.

### Reliability

* **TikTok:** A failed settings write now keeps the live value aligned with the value that will survive a restart. Concurrent writes can't cross its rollback window, and feed actions show a failure instead of a false success or Undo state.
* **TikTok:** Settings restore now rejects fractional and overflowing backup schema numbers instead of truncating them into a supported version.
* **TikTok:** Session budget resets now invalidate older queued writes, so one test can't quietly restore a spent budget after the next test has cleared it.
* **TikTok:** A first push of a branch now checks its complete resulting tree. A documentation-only tip or stale tracking ref can no longer hide a code path from the local gates, and the script contracts follow the current TikTok 47.0.3 target.
* **TikTok:** Guarded S22 acceptance now checks the focused window on Android's default display and sends every input to that same display. TikTok on another task or display can't authorize input, and the helper still resolves Windows `adb.exe` and evidence paths correctly under WSL.

## 0.49.0 (2026-09-20)

Settings feedback is clearer, easier to recover from and more consistent across every page.

### Premium settings polish

* **TikTok:** Invalid number, text, range, creator, folder and Feature Gate values now show an inline explanation inside the editor. The field keeps focus, announces the problem once and clears the message when typing resumes.
* **TikTok:** Text cursors, selection handles, picker frames and list edge effects now use the Hushfeed palette instead of leaking TikTok's theme into the settings screen.
* **TikTok:** Clear seen videos and Start today over now offer Undo in a ten-second settings banner. The action survives page changes and remains at least 48 dp for touch and accessibility input.
* **TikTok:** Restart-gated changes now offer Restart now in the same banner while keeping the pinned restart row for later. Returning a value to what TikTok already runs no longer asks for a restart.
* **TikTok:** Feed and bottom tab pickers now join the persistent restart state correctly instead of showing a restart message and then losing it.
* **TikTok:** Forty-one labels and descriptions now use one naming system across Feed filter, Feed screen, Inbox, Playback, Region, search and the share sheet. German, Spanish, Indonesian and Brazilian Portuguese were updated with them.
* **TikTok:** The three home shortcuts now become full-width rows at Android's first large-text step, so Privacy and Screen time stay intact instead of breaking in the middle of a word. The full settings matrix was rendered again in dark and light themes, large text and mirrored layout.

## 0.48.0 (2026-09-20)

Feed filter now has a reversible starting point for a calmer feed.

### Calm feed

* **TikTok:** Calm feed turns on six focused filters together: feed ads, Shop posts, LIVE videos, LIVE replays, promotional music and paid partnerships.
* **TikTok:** Applying the preset saves the previous values first. Restore puts that exact setup back, even after TikTok restarts or after one of the six switches is changed by hand.
* **TikTok:** The preset leaves stories, photo posts, AI labels, creator rules, keywords and time limits alone.
* **TikTok:** Its status card shows when the preset is on or has been adjusted. The action is a real 48 dp button, stacks at large text sizes and is translated with the rest of Hushfeed settings.

## 0.47.0 (2026-09-20)

The settings home is faster to scan and easier to use with touch or a screen reader.

### Settings

* **TikTok:** A new status card confirms Hushfeed is active and shows the installed Hushfeed and TikTok versions. Diagnostics opens directly from the card.
* **TikTok:** Feed filter, Privacy and Screen time now have direct buttons beside Search at the top of the settings home.
* **TikTok:** The new actions use real button roles, visible focus and press feedback, and touch targets of at least 48 dp. They stack instead of clipping when text is enlarged.
* **TikTok:** The settings home screenshot has been refreshed from the tested native Android view in both theme and accessibility checks.

## 0.46.0 (2026-09-20)

All 91 patches now support TikTok 47.0.3.

### TikTok 47.0.3

* **TikTok:** Hushfeed is the first row in Settings and privacy again. TikTok 47.0.3 changed how that menu is assembled, and the patch now moves the existing row instead of creating a duplicate.
* **TikTok:** Downloads now follows the actual comment-image watermark draw when TikTok moves nearby MIME and pending-state strings.
* **TikTok:** Feed filter now handles TikTok's separate golden-cache and offline-cache delivery methods. Hide already seen videos follows the same repaired boundary.
* **TikTok:** Playback speed now finds the player boundary by its behavior after TikTok obfuscated both method names.
* **TikTok:** Empty comment pages and ordinary posts without a survey no longer appear as broken hooks in Diagnostics.
* **TikTok:** Block P2P video relay, Remove LIVE extras, Remove content credential and card scanner assets, Remove creation tools, and Remove unused language packs now use reviewed 47.0.3 file inventories.
* **TikTok:** Feature Gate Lab now treats 47.0.3 as its target. Saved rules from an older target stay disabled until they are reviewed.

## 0.45.2 (2026-09-19)

One settings search fix found during the TikTok interface audit.

### Settings search

* **TikTok:** Opening a search result and pressing Back now keeps the query visible. Before this release the filtered result list stayed on screen, but the search box looked empty.

## 0.45.1 (2026-09-19)

Two fixes for TikTok 46.9.3, one of them a crash (#12).

### Fixes

* **TikTok:** On TikTok 46.9.3, Feed filter no longer closes the app when the Following feed loads. That build renamed where the feed keeps its videos, and the filter now finds them under either name.
* **TikTok:** On TikTok 46.9.3, a download with no clean address falls back to the video's own playback address again, the way it does on 46.2.3.

## 0.45.0 (2026-09-18)

TikTok 46.2.3 from APKMirror's bundle download, an .apkm file, now patches cleanly (#9).

### Fixes

* **TikTok:** The four optional cleanup patches now accept APKMirror's 46.2.3 bundle. They refused it before, because a merged bundle carries fewer files and only 25 of the 64 language packs. Every file they empty is still checked byte for byte first.
* **TikTok:** AMOLED dark theme now refuses an APK merged from a bundle instead of building one that crashes at launch, because rebuilding a merged bundle's resources loses about 1,400 of them. Use the plain APK from APKMirror if you want the dark theme.

## 0.44.0 (2026-09-18)

Separate share lists for profiles and LIVEs, and two fixes from a patching report (#9).

### Share sheet

* **TikTok:** A profile's share sheet and a LIVE's share sheet can now hide a different set of actions from a video's. Each has its own checklist under Hushfeed settings > Share sheet, and until you save one it hides whatever your video list hides, so nothing you set up before changes.

### Fixes

* **TikTok:** Downloads no longer fails to patch on TikTok builds where the download step's code is larger than usual. A reporter's 46.8.3 lost Downloads to this.
* **TikTok:** When one of the four optional cleanup patches meets a TikTok APK it hasn't checked, its error now says nothing was removed and what to do next: untick it, or patch the full APK from APKMirror. A split bundle (an .apkm file) can cause this.

## 0.43.1 (2026-09-18)

A packaging fix. No patch changed.

### Fixes

* **TikTok:** The 0.43.0 bundle was published without its patch code, so Morphe Manager showed zero patches for the source and could not build anything. This rebuilds it correctly, with all 91 patches. If you updated to 0.43.0 and saw no patches, update the source again.

## 0.43.0 (2026-09-18)

One new patch, from a feature request.

### App

* **TikTok:** A new Use system font switch draws TikTok's text in your device's own font instead of TikTok Sans. The icons, the gift animations and the @ and # glyphs keep their own fonts. It is off by default and needs a restart. Switch: Hushfeed settings > App.

## 0.42.0 (2026-09-18)

Two ad and Tako surfaces that slipped past the feed filter, both reported on 0.40.0 and traced to routes the earlier hooks never saw.

### Feed filter

* **TikTok:** Ads no longer appear while you scroll a creator's videos from their profile. The profile's own video pager was asking a separate ad endpoint and splicing the answer between the creator's videos, so those ads were in none of the lists the filter already cleaned. That request is refused now while Remove feed ads is on.
* **TikTok:** The "Ask" bar that sat under some videos is gone with Hide Tako AI on. It looked like a Tako feature but was one of TikTok's bottom banners, so Hide Tako AI now covers it along with the floating bubble, and its switch says so.

## 0.41.0 (2026-09-18)

A polish release. No patches were added or removed. Most of it makes the settings screen and the Feature Gate Lab read as one app, with a round of accessibility fixes and a changelog Morphe Manager can finally read.

### Morphe Manager

* **TikTok:** Every patch description now says where its switches live, for example "Switch: Hushfeed settings > Feed filter.", so choosing a patch in Manager tells you which page to open afterwards. Eight descriptions that opened with "Adds an option to" now start with what the patch does.

* **TikTok:** Manager's update screen can show what changed. It only reads changelog headings that carry a date, and every release since 0.23.0 had none, so updating Hushfeed showed an empty list. Every heading here has its date now, and each entry names TikTok so Manager can tell the update touches it.

### Settings search

* **TikTok:** Search keeps what you typed when the phone rotates or the text size changes, and the keyboard no longer covers the last results or the empty-state message.

* **TikTok:** Tapping a search result scrolls to it with a little of the page still showing above it, and the row flashes briefly so you can see where you landed. With TalkBack on, focus lands on that row too.

* **TikTok:** The clear button is a drawn cross at the same stroke weight as the back arrow, with a ripple when you press it. It used to be a typed × character that grew with the text size. The query and the result count now share one left edge.

### Settings pages

* **TikTok:** A setting set to zero says what zero means. Caption text size reads "TikTok's size" instead of "0 points", and Clear display delay reads "No delay".

* **TikTok:** Opening a page slides it in from the side you're moving toward, and Back slides it out the way it came, instead of the same fade both ways. Right-to-left languages mirror the motion, and with animations turned off in Android's settings, pages switch instantly.

* **TikTok:** Page titles break between words at large text sizes instead of mid-word, so German no longer shows "Kommen-tare". Menu summaries are 14sp like every other summary, not 12.8sp.

* **TikTok:** Disabled text in the light theme is readable now, at about 4.5:1 against the page where it was 3:1, and a disabled row's arrow dims along with its title.

* **TikTok:** Dialogs share one corner radius, one set of margins and one 20sp title size. The SIM preset and tab dialogs had tighter corners, and the gate recording dialog's title was 28sp.

* **TikTok:** The About row says it opens github.com in your browser, and it no longer shows the arrow that means another settings page.

* **TikTok:** Browse in a download folder dialog keeps what you typed. The dialog stays open under the folder picker, a folder you pick fills the field, and a folder Hushfeed can't use, like one on an SD card, shows the reason under the field rather than in a toast after the dialog has closed.

* **TikTok:** The hidden creators editor labels its search and add fields the same way, and adding a creator now confirms it the way removing one already did. With only a few creators in it, the list shrinks to fit instead of leaving a tall empty band.

* **TikTok:** Clear seen videos becomes "Undo clearing seen videos" while you can still undo, says the undo only lasts until TikTok closes, and shows how many videos a clear would forget.

* **TikTok:** The Hook status row on Diagnostics starts with a green check when every hook found its target and an amber warning sign when something is missing, and TalkBack reads that state before the sentence.

* **TikTok:** Cancel buttons follow Hushfeed's language instead of the phone's. The tab picker explains why it's empty until TikTok has loaded the feed, and its Select every tab button waits until there are tabs to select.

* **TikTok:** Choosing a SIM preset no longer toasts what the row already shows, a speed list that can't be saved shows the reason under the field, and the Hide feed save button row explains how it differs from the Save entry in the checklist above it.

### Feature Gate Lab

* **TikTok:** The Lab looks like the rest of settings. Its title matches every other page title, row titles are 16sp, the section titles on a gate's page match the settings categories, and the scope row underlines its choice the way the source tabs above it do.

* **TikTok:** The account warning on the Lab and the sensitivity note on a gate's page sit inside a card with a warning sign, instead of hanging between controls as loose orange text.

* **TikTok:** A field that can't be saved on a gate's page is marked where it is, with the cursor moved into it, instead of reported in a toast. The custom value dialog says "unverified" once, and asks for a whole number rather than naming a data type.

* **TikTok:** Holding a gate to select several is easier to discover. The selection bar says how, the count is announced as it changes, and TalkBack offers "Select" on each row.

* **TikTok:** The gate list scrolls without reading every row's saved override from storage. Leaving the Lab no longer repeats "Restart TikTok to apply this." after each change already said it, and an export's file name follows the TikTok version the Lab targets.

### On the video

* **TikTok:** The ring that arms a Follow or Like confirmation is white with a dark inner edge, so it shows on TikTok's red buttons, and arming it vibrates. It used to be the same red as the button it circled.

* **TikTok:** Block, local hide, block sound and Not interested vibrate when tapped, like the share sheet's confirm step. The comment block used the long-press vibration for a tap.

* **TikTok:** When automatic advance reaches its limit, the notice offers Keep going, which starts a fresh run from the video you're on. Before, the only way on was to open settings and raise the limit.

* **TikTok:** While a block or a sticker save is running, the button stays readable instead of fading into the video, and a screen reader hears "Blocking" or "Saving". The sticker Save button also follows the sheet's own light or dark look.

* **TikTok:** The comment search box lines up with the comment text instead of running edge to edge. The daily hold panel keeps its text away from the screen edges at large text sizes, and the tip about dragging a control shows only until you've moved it once.

* **TikTok:** The CAPTCHA notice stays up long enough to read. On Android 13 and newer, copying a link no longer shows Hushfeed's toast on top of Android's own, and a failed hand-off to a downloader app names the app instead of its package name.

### Accessibility

* **TikTok:** The range dialog reads its Minimum and Maximum labels once, as part of their fields, and the keyboard no longer autocorrects a value like "1.5M" into something that won't parse.

* **TikTok:** Pressing Done on a dialog's keyboard saves it just like tapping Save, including on number keyboards that send Enter instead of Done.

* **TikTok:** The hold panel's two actions are announced as buttons. A gate's Technical details heading says whether it's expanded, once rather than twice, and each info row on a gate's page is read as a single item.

### Wording

* **TikTok:** Failure messages end with what to do next. A failed save now says "Allow storage for TikTok in Android settings to save sounds." rather than only that permission is missing.

* **TikTok:** The feed is called the feed everywhere, not "the homepage", "Home feed" or "TikTok home", and TikTok LIVE is spelled LIVE throughout.

* **TikTok:** Remove creation tools says plainly that the Create tab stops working, rather than that it may.

### Diagnostics

* **TikTok:** The diagnostic export lists Hide Tako AI whenever one of its hooks attached, even with the switch off. Before, the Tako family could be missing from a report entirely.

* **TikTok:** When every mirror fails a download, the error keeps the cause of each attempt instead of only the last one.

## 0.40.0 (2026-09-17)

* The settings menu is four groups instead of one list. Your feed holds Feed filter, Feed tabs and Feed screen. Watching and sharing holds Playback, Screen time, Comments, Downloads, Share sheet and Inbox. Privacy and system holds Privacy, Region, App, the Feature Gate Lab, Diagnostics and Backup and restore, and About sits on its own at the end. The "YOUR EXPERIENCE" label over a single card of fourteen rows is gone. Five pages have shorter names: Interface is Feed screen, Feed navigation is Feed tabs, Comments and translation is Comments, Region settings is Region, and App behavior is App.

* Screen time is its own page. The daily video and time budgets, the reminder, the hold and its fade, the budget label, the lock, the day's start and the passes all sat at the bottom of Playback under a "Daily budget" heading. They have a page of their own now, with "Do not start the feed on returning" under a Focus heading at the top of it.

* Backup and restore is its own page. Back up, Restore, Reset and Undo used to be the tail of Diagnostics, so anyone who patched without the diagnostics patch found them on a page named for something they didn't have. The Feature gate recorder row moved the other way, off the menu and into Diagnostics, where it stops being the one row without an icon.

* The Feed screen page is laid out by where things sit. Right column first, with one checklist row over the six hides and the counts (its second line says what is hidden, "Hidden: Like, Share", instead of seven rows each saying "Hide the ... button"), then Video info, Around the video, Popups, Captions, Screen, Clear display and Gestures. The three long-press rows that lived under Player on the App page sit beside the Long press row now, and the playlist bar, event badge and inserted cards rows moved here from Feed filter, since they hide things drawn around a video rather than videos.

* Feed filter reads in the order people ask: Kinds of post with Remove feed ads first, Limits, Creators and sounds, Words and countries, Seen videos, then one Advanced switch. The four player buttons (block, local hide, block sound, Not interested) moved here from the App page, next to the lists they add to.

* The App page keeps what is about the app: Layout, Player, Search (with Hide search suggestions), Profile (Keep the Favorites tab) and System. Downloads has Files, Video, Photos and stickers, Long press, Subtitles, Hand-off and Offline videos headings, and Comments has Reading, Hiding and Blocking.

* Every menu icon is its own picture. Feed screen and App shared the sliders glyph; Feed screen draws the feed now, a screen with the right column down its edge, Screen time is a clock and Backup and restore is a box with an arrow rising out of it. The App page has Search and System headings so its last four rows stop reading as part of Player.

* A Privacy page. Everything that decides what TikTok learns sits in one place now. Disable analytics and tracking and Ghost mode moved there from App behavior, the three link rows came with them, and each of the device-access patches below has a switch of its own. Before this the seven device patches had no row anywhere, so there was no way to see they were installed and no way to turn one off short of patching again.

* Seven patches that stand between TikTok and the phone: Block contact list access, Block installed app scanning, Location access governor, Device privacy guard for clipboard reads, Resource and battery governor for the motion sensors, In-app browser privacy guard, and the Camera and microphone indicator. All but the browser guard are on as soon as the patch is chosen. The browser guard stays off until you turn it on, because TikTok's own web pages are built on the bridge it cuts, the shop checkout and the CAPTCHA page among them.

* Three of those patches said more than they did, and two did nothing at all. The contact blocker matched the plain call form only, and a five-argument ContentResolver.query is always the ranged form, so it intercepted zero call sites and applied anyway. The installed-app blocker watched getInstalledPackages, which TikTok 46.2.3 never calls. The app list is read through the launcher enumeration, which is what it watches now, and a check for one named app is left alone so opening another app still works. The device privacy guard claimed to block local network scanning and the battery governor claimed to throttle preloading. Neither did, and both descriptions now say what the code does. Every one of these patches reads both call forms now and refuses to apply when it finds nothing to intercept.

* The camera and microphone indicator draws the dot it promised. A green dot sits in the top corner while TikTok has the camera open and an orange one while it records sound, both when both, on whichever screen is in front. It follows Camera.open, camera2's openCamera and AudioRecord.startRecording, and goes when the camera is released or the recorder stops. The first version wrote a log line and nothing else.

* Block P2P video relay strips TikTok's peer-to-peer CDN libraries so the phone isn't used as a relay for other people's video. Stop on-device AI profiling returns early from the Pitaya inference engine's start. Remove content credential and card scanner assets now also empties six Pitaya model libraries per ABI, the LIVE casting feature and the ART log monitor probe. It leaves libbytemonitor, libprofiler and libAndroidPitayaCore alone: other libraries in the APK link against those, and an emptied one fails their load. The first cut emptied libbytemonitor too, which libbytebench needs, which the whole video editor needs, so the Create tab killed the app until the S22 showed it.

* Maximum views per comment joins Maximum views per like under Feed filter, for hiding videos with a lot of views and few comments.

* The six right column switches (hide the like, comment, favorite, share, avatar and music buttons) work again on 46.2.3. Since 0.35.0 they looked for the video cell under the long-press layer, which on this build sits beside the column rather than above it, so the walk found no buttons and hid nothing. It starts from the cell's own root now, which holds both.

* Feed button size makes the right column's icons a quarter larger for anyone who finds them small. Under Feed screen. The first cut offered 1.5x and 2x as well and scaled the whole button, and on the phone that did nothing: TikTok animates those buttons itself and writes its own scale back on every frame. The setting scales the icon inside each button now, from the icon's base so it grows upward, and puts the size back before each frame TikTok draws. Past 1.25x an icon runs into the next button, so the two larger sizes are gone, and a stored 1.5x or 2x reads as 1.25x.

* A feed check asked from a new window no longer takes its answer from a Home tab that belongs to an older one. Anything drawn only on the recommendation feed could otherwise show up, or stay away, on the wrong screen for as long as the old window lingered.

* The share prompt that pops up after a like can be hidden. With the switch on, the bubble asking you to share a video with friends never appears. Under Feed screen, off by default. Upstream #22.

* The four feed overlay controls are drawn by one class at one stroke weight. Sound was a Unicode note in whatever font TikTok picked, local hide was a bold ×, and not interested was a thin dash; only the block symbol was drawn. They all draw through `OverlayGlyphDrawable` now, at the same 2dp stroke and the same radius fraction, so they look like one set.

* The remembered speed can be turned off. It was always on, so a reader who wanted TikTok's per-video reset had to drop the whole playback speed patch. With the new switch off, each video starts at 1x and a manual choice from the menu lasts for that video only. Under Playback, on by default.

* "Enter a number. The previous value was kept." is gone: the dialog stays open for a correction now, so no value was kept. Five Playback summaries that opened with "Off by default." lead with what the feature does instead; the switch already says whether it is on.

* The asset strips patch the newer TikTok builds again. Remove content credential and card scanner assets knew one reviewed set of files, 46.2.3's, and 46.7.3 and 46.8.3 ship 23 of those 33 paths while 46.9.3 ships different bytes behind 15 of them; Block P2P video relay carried a hash from the wrong build for one 46.9.3 library. A forced patch on any of those builds refused both. Each build has its own reviewed set now, read off the real files, and a test holds every set of every strip against every fixture on the machine before a release is built.

* Holding Like does nothing with Disable the long press repost on. It kept the repost panel away, but it told TikTok the hold was not handled, so Android treated the release as a tap and a hold liked the video: on the S22 the heart went red and the count rose by one. The hold is consumed now.

* "Save the sound as well" saves the sound. Every attempt ended in "The sound couldn't be saved. Try again." and the export said "Download contains no media samples". TikTok's AAC tracks open with an edit list that puts the encoder's priming frames before zero, so the extractor hands the first frames back at a negative time (the S22's files start at -161 ms), and the copy loop read the very first one as the end of the track. A track's lead-in is now taken off every stamp, and when a picture and a sound are put together they move by the same amount, so nothing drifts. Found on the S22 on 2026-09-17.

* A sticker save says where it landed, the same as every other download. It used to say "Sticker saved as MP4" and the reader had to guess the folder. It says the folder now, matching the four other savers.

* The Feature Gate Lab speaks in plain terms. "Getter used", "Loaded", "All actionable", "Applies saved rules at supported getters", "Effective getter result", "TikTok cached value", "Not present in the current cache", "Getter not requested yet" and "Override saved but off" were implementation words that meant nothing to anyone who didn't write the code. The tabs say Seen, All, Overridden; the filter says Not seen; the states say TikTok read it, Not seen yet, Saved, override off; the detail page says What TikTok gets, TikTok's own value, Not read yet; and the master switch says Replace values when TikTok asks for them. The three source-name tables are two: the row badge matches its tab label, and the detail page spells it out.

* Hide Tako AI now covers three additional trigger components. The slot component hooked in 0.39.0 only draws the ask bar on some accounts; on others, TikTok routes through a trigger component in the tikbot package instead, plus a "roof" variant and a detail-page trigger. A reporter's export on 0.39.0 showed the slot hook never firing while the bar was still drawn: the feed-level trigger was the one that ran. All four trigger binds now guard the same way, and the hook status family records whichever path fired.

* Text rows in the settings show their value. The twenty text preferences (Country ISO, Video filename, Blocked caption words and the rest) only showed their description, so the only way to learn what was in one was to open its editor. Each one now carries a "Current:" line underneath, the same as the numeric rows, truncated to roughly sixty characters so a list of blocked words stays one line. An empty field says "Empty", and the value updates after Save, after a backup restore and after Undo.

* Three things in the Feature Gate Lab. The "Unloaded" state label was painted in the disabled color, about 3.7:1 on the dark surface and 3.4:1 on white, under the 4.5:1 floor for 12sp text, on a row that is fully tappable. It uses the secondary color now, and the word carries the meaning instead. Picking a filter used to move the tab and picking a tab used to reset the filter, with nothing on screen saying why either happened; now only the one real contradiction (asking the Loaded tab for unloaded gates) is resolved, and the picker offers only the filters the current tab can answer, so nothing moves. The overflow was a platform PopupMenu in grey next to everything the bundle draws, Undo was offered whether or not there was anything to undo, and the two Resets sat side by side with nothing saying the second also clears the switch, the acknowledgement and the recordings. It is a themed list now, Undo is disabled when there is nothing to undo, and the labels say what they take away.

* Save is reachable in the tab picker at a large text size. The dialog was one column: a title, a two-sentence helper, a list that is never shorter than 220dp, and its own Save and Cancel underneath. At twice the system text size on a short phone that comes to more than the screen, and the panel a custom dialog view sits in clips rather than scrolls, so the actions were what got cut and Save could not be reached at all. Save, Cancel and Select every tab are the platform's own buttons now, which it keeps on screen and shrinks the list for. The SIM preset picker had the same shape and got the same repair, so Clear preset and Cancel stay put too.

* The feed filter notice is a banner over the feed instead of a dialog across it. A scroll used to be stopped dead by a modal whose only action was opening a settings page, and it was written in the code's own words: "No videos remained after Hushfeed applied your feed filters to 3 batches. Most matches: Ads (12)." It now reads "Your filters hid everything TikTok sent, 3 times in a row. Most were Ads (12)", takes no focus, announces itself once, takes itself away after six seconds, and carries a Filter settings action for anyone who wants it. The banner the block controls already used can now carry an action other than Undo, which is what made this possible.

* A web address someone left in a comment can be tapped. TikTok draws comment text as plain text, so the only way to follow a link in a comment was to copy it out by hand, which is what an upstream reader asked for a year ago. The comment text view is found by what it is showing rather than by a resource id, so it is the same answer on any build, and a tap that lands anywhere other than the address still does what it always did: a comment that carries a link does not stop opening the replies. Under Comment tools, on by default, and off in one switch.

* Comment search counts the comments it has seen instead of the rows on screen, and the box has an X that clears it. The count was taken from the rows the list had attached, so scrolling turned "3 results" into "5 results" into "no matching comments" while nobody was searching for anything, and the line is a live region, so a screen reader read out every one of those. A comment is counted once, when it comes into view, and scrolling past it again neither doubles it nor takes it away. The line says "so far", because TikTok pages comments and this has only seen the ones it loaded. The empty state used to tell a reader to clear the search when there was nothing on screen that could; there is now an X at the end of the box, and a screen reader gets a Clear the search action on the field rather than a second stop in the traversal.

* Hide quick comment reactions hides the row above the comment box. The switch only reached the quick comment strip TikTok draws on some videos, so the emoji clusters in the comment sheet stayed put with it on, which the S22 showed. The comment keyboard adds that row through a trigger rather than a visibility write, and the switch now answers the trigger before its own checks run, so the row is gone whether or not the keyboard is up. TikTok builds that slot tree once per session, so the switch takes effect after a restart and its row says so.

* The block control on a comment now looks like one. With Block from comments on, a tap on what was still drawn as TikTok's thumbs down blocked the account, and the only thing that said so was a tint that arrives after the block has already happened. The control draws the block symbol instead, in the same color the icon beside it uses so the row keeps its weight, and TikTok's own icon goes straight back the moment the switch is turned off.

* The register search follows a switch instead of stopping at one. It used to treat a packed or sparse switch as the end of the road and answer with whatever it had proved free before reaching it, which at the top of a method is nothing, so the patch was told there were no free registers and refused to apply. R8 turns a chain of string comparisons into a switch, which means the same method can be an if-else chain in one TikTok build and a switch in the next with nothing about the patch having changed. A switch is now read the way a two-way branch already was: a register is free at the switch only when it is free down every arm and down the fall-through. A payload it cannot read still ends the search where it used to, so an odd one is no worse than before.

* A feed batch that came out empty now says so in the diagnostic export. Hiding livestreams over a page of nothing but livestreams legitimately leaves zero videos, and putting one back would be the switch not working, but from the outside an empty batch and a broken feed look the same: there is nothing to swipe to until TikTok asks for another one. Upstream reported the feed freezing on exactly that swipe. A route's line now carries how many of its batches it handed back empty, so a report about a stuck feed can say whether anything was ever delivered to show.

* A share hook that fails now leaves TikTok's own share sheet alone instead of taking it down. Three of them ran inside the share model's constructor with nothing catching behind them, and the link rewrite replaced a native method outright, so one bad read anywhere in there reached TikTok as a Share button that did nothing, followed by the app stopping. That is what upstream's report looked like from the outside. Each of those boundaries now catches for itself and hands back exactly what TikTok passed in, and a failure that used to be silent shows up in the diagnostic export as a named hook that threw. The confirm step keeps holding a send to a person, but it no longer eats a tap on Repost, Copy link or Save when it cannot tell what it is looking at.

## 0.39.0 (2026-09-16)

* Ads no longer appear while paging through a creator's videos from their profile. TikTok has a mid-roll ad component that waits for the pager to load, then takes the video on screen and an ad, finds the video in the pager and puts the ad in its place. That runs after every list the feed filter reads, which is why issue #2's exports showed a profile list of 184 organic videos with nothing removed while the reporter was looking at an ad: the ad was never in the list. Remove feed ads now refuses that swap, on all four retained builds, and the diagnostic export carries a line for it, so a report can say whether the route ran and what it kept out. The video the ad would have replaced stays where it was.

* Hide Tako AI now covers the "Ask" strip that appears under some videos' captions, the one offering to answer a question about the video. It is drawn by a different component from the floating Tako button the switch already hid, so a reader who turned the switch on still saw it. With the switch on, the strip is hidden before it is filled, and the diagnostic export names it either way.

## 0.38.0 (2026-09-16)

* Hide series no longer empties the feed. TikTok hangs a paid content struct on ordinary recommended videos with its fields left at defaults, and the filter treated the struct being there as the marker, so with the switch on nine of ten videos in a batch were removed and For You never loaded anything. A video counts as a series when it says it is paid content, or when that struct carries a collection behind it: an id, a name, an episode number or the intro flag. Hide playlist videos and Hide AI generated videos had the same fault and got the same repair, so an empty mix or moderation struct is no longer a match. Reported on 0.36.0 with a diagnostic export that named the filter.

## 0.37.0 (2026-09-16)

* The Android 17 audit covers the changes the platform will actually enforce, not just the three on the developer site. A phone running Android 16 already carries eleven compat changes gated at target 37, and three of them could have reached injected code. None does: the payload loads no code from a file, nothing in it subclasses Thread, and a handshake the platform refuses is reported rather than retried, which is the same answer Certificate Transparency gets. Each verdict is held by a test, so a change to any of the three fails the suite instead of a phone.

* Every source notice that cites ReVanced's blocked GitHub repository now carries the GitLab mirror beside it, so a reader following the origin of a file reaches the file rather than a takedown page. The original line stays as the record of which revision the code came from, and a test fetches each mirror once so the next takedown is found by the suite.

* The legacy settings import and export path is gone from the shared library. Nothing but its own test reached it, and it held the last English toasts the translation tables could not cover. Backups go through the settings backup, which still reads the older metra-settings format.

* Inside the patch bundle, thirteen hand-rolled register checks, sixteen hand-written entry guards, two copies of the argument-register helper with different casts, two same-named string helpers with different meanings and four superclass walks are one helper each. Nothing a user sees changes; the next TikTok build that moves a shape is one fix in one file instead of a hunt.

* Auto translate comments no longer walks every field of every comment cell's manager, and the manager's declared methods, three times per cell on the thread that binds the list. The manager's shape is a property of its class, so the first cell of a class pays and the rest read three fields.

* Follow diagnostics lets go of a request when its response is parsed or it fails, instead of holding up to 160 request graphs, bodies and buffers included, until TikTok is killed. A request that never finishes is held weakly, and the readback context expires after its thirty-second window.

## 0.36.0 (2026-09-16)

* The feature gate recorder's report dialog puts Save JSON last with the accent, and Copy report no longer closes the dialog, so the preview stays where it was. A stop with nothing read says so in a sentence instead of showing an empty JSON object, and offers nothing to copy or save. A fresh install's row says no recording has run yet rather than "Last recording: 0 gates", and it has no chevron while a tap starts recording on the spot. Once a report exists, the row offers Start recording or Show last report, so a report closed too soon can be opened again until the next recording.

* Saved gate reports are named by date, like a settings backup, and land in Download/Hushfeed, which is the folder the toast names. They used to be an epoch number under a Morphe folder with a toast naming a Downloads folder the phone doesn't have. When the background pool cannot take the write, Save JSON now says so instead of doing nothing.

* A session hold no longer keeps a once-a-second timer running while TikTok sits in the background. With Lock today's budget on, a hold ran to the reset hour, and the countdown, the layout pass and the budget checks ran every second of it for a screen nobody was looking at. The countdown stops when the app leaves the screen and comes back with it.

* Settings rows keep their drawables when the list rebinds the same view. Scrolling a long page rebuilt the ripple, the card, its mask and a switch's track and thumb for every row that came into view, on every frame.

* A settings backup without a schema number is read as the first schema, and a schema newer than this build reads, or one that is not a number, is refused with the schema reason rather than a generic rejection.

* Every patch applies on TikTok 46.9.3, the build after the three the bundle is checked against. Fourteen patches used to fail there, all for the same reason: 46.9.3's build of TikTok marks methods final and stops merging small lambdas into shared classes, so a getter the patches matched by exact access flags, a lambda they matched by its outlined static shape, a service class they matched by name and a CAPTCHA overload they matched by its parameter list all moved without changing what they do. Feed filter, Hide already seen videos, Hide CAPTCHA popups, Hide the risk control CAPTCHA, Comment sort controls, Comment tools, Advanced downloads, Subtitle tools, Share sheet tools, Translate comments and Disable the long press quick share now find their targets by what the code does rather than by how the compiler shaped it, and the three resource optimizers carry a reviewed profile for the new build. Nothing changes on 46.2.3, 46.7.3 or 46.8.3, which still apply 81 of 81; the declared target stays 46.2.3.

* Playback quality now reaches the player. The setting picked a gear out of the video model's own list and reported it, but TikTok's player builds its models from a different, unfiltered list, so every video still played at whatever quality the player chose. That is what issue #3 saw on a Galaxy A56. The choice is now made at the one door into the player's list, on all four retained builds, and the report names it: a line like "picked lowest_540_1 540p of 5 gears from SimVideoUrlModel#setBitRate" is the player's list, not a bystander's. Checked on a Galaxy S22 with the setting on lowest and then on highest. The hook status row also stops counting a non-adaptive video's empty model as a missing hook, which is what turned "2 missing" into a false alarm, and single-gear lists no longer write a report line each.

* Playback speed and the Feature Gate Lab (and the Feature Gate Recorder with it) apply on TikTok 46.9.3. That build carries the speed menu's list factory twice, two identical copies of one outlined lambda, and a second raw App AB getter next to the first. Both patches used to refuse with "found 2". The speed patch now hooks every identical copy and still refuses when the copies differ, and the Lab holds both raw getters open. Nothing moves on 46.2.3, 46.7.3 or 46.8.3, which carry one of each.

## 0.35.0 (2026-09-15)

* Hide feed surveys no longer empties the profile's Favorites tab. TikTok gives the Favorites page the same view id as the feed survey card, and the overlay hider took every view with that id anywhere in the window. Feed furniture (the caption, the music block, the action column, the survey card, the rail buttons and their counts) is now hidden only inside a feed cell; the tab strip is unchanged. Found on a Galaxy S25 by restoring the settings one group at a time, and the fix was checked there.

* A Feature Gate Lab import that accepted something now reports in a dialog: one line per count, the first rejection selectable so it can be copied, and Undo beside Done. It was a five-clause toast that vanished in 3.5 seconds. An import that changed nothing keeps its one-line toast.

* The Feature Gate Lab's switch rows (Enable overrides and the detail page's Override and Forced result rows) are one screen-reader stop each with the Switch role, and the whole row toggles the switch. They used to read twice and only answer a tap on the switch itself.

## 0.34.0 (2026-09-15)

* A new patch, Comment publish diagnostics, on by default. When a comment will not post, the text stays in the box with no toast and nothing in the log, and TikTok's publish code has a dozen ways to return before the request without a word. The diagnostic report now says whether the send reached that code, what it had in hand (the text's length, the attachments, a reply target, the video), and whether it returned early or handed the comment to the request.

* Reposting a video no longer arms the send-confirmation step. With Confirm before sending on, tapping Repost, Copy link or another share-channel action used to show "tap again to send" and act only on a second tap, because the confirmation gate treated every share-sheet button as a person to send to. It now gates only the recipients you can send to.

* The feed filter table in the diagnostic report now says when a profile or insertion route was handed something that is not a video, and with diagnostic logging on the profile routes list every video they kept with its ad markers (ad, soft ad, raw ad, promotion, commission disclosure). A report about an ad that got through a profile page (issue #2) used to carry the counts and nothing about the ad itself.

* The diagnostic report now has a Feature Gate Lab section: whether overrides are on, each rule with its value, and for each one whether TikTok has read that gate since the app started, from where, and what TikTok had before the override. Until now an export from a phone where an override changed nothing could not say whether the override was live at all.

* Editing the fields of a structured gate in the Feature Gate Lab now shows that a save is pending, offers Discard edits beside it, and says so if you leave with edits unsaved. Before, Save looked the same before and after typing and Back dropped everything without a word. When overrides are off, the page says so above the controls instead of after the last one, with a Turn on overrides action that enables them without leaving the page.

* While a settings backup, restore, reset or undo is running, the four rows on the Diagnostics page go out of reach and the one acting says what it is doing. Before, they looked exactly as they had a moment earlier, a second tap earned "A settings operation is already running", and a screen reader was told nothing at all.

* A settings row greyed out by another switch now says which switch would turn it on, and a greyed switch shows whether it is on or off instead of looking the same either way. On the Region page the two rows that only said in prose that they need Override SIM details are now actually wired to it, so they grey out rather than letting you switch on something that does nothing.

* The SIM country preset list now marks the preset that is already chosen and opens scrolled to it, and a Clear preset action takes the row back to no preset. It used to be sixty identical rows with no sign of which one was on, and no way back short of retyping three fields.

* When a settings dialog refuses a value, the focus now moves to the field that was refused and the reason is read out. Before, the dialog stayed open with the reason under the field while focus sat on Save, and a screen reader said nothing at all.

* Search settings now opens with the search box focused and the keyboard up, the way every other search on the phone does, with a Search key that takes the keyboard down again so the results can be read. Autocorrect is off in that box, so it can no longer rewrite a setting's name into another word.

* The "Blocked" banner with Undo now sits above TikTok's tab bar on the feed and above the input row in the comments sheet, instead of a fixed distance up from the bottom of whichever screen it was on, which put it over the tabs on one phone and on top of the comment box in the sheet. It is drawn on the same scrim, hairline and corner radius as the chips beside it.

* New patch, Keep the Favorites tab, on by default. Two people found the Favorites tab on their profile empty after patching (issue #4): the tab was there, the saved videos were not. TikTok's server can put an account into an experiment that skips building that tab's pages, and forcing either of the two gates behind it on the test phone produced the reporters' page exactly. The patch answers those two reads with the values that keep the tab, the diagnostic report says when it did, and the switch is in Settings, Interface if you'd rather see what the server sends.

* AMOLED dark theme now rewrites the theme style values behind the comments sheet and the share sheet, and one more page background color. Both sheets are painted from style values that never go through the color table the patch rewrote, which is why they stayed TikTok's grey with the patch on. Only dark values are touched, so the light theme keeps its white sheets. On a TikTok build the patch is forced onto, a sheet style name that build does not have is skipped instead of failing the whole patch.

* The feed filter table in the diagnostic report gives the profile detail pager's ad event a line of its own. It used to share the profile grid's line, so a report about an ad seen while watching videos from a profile (issue #2) couldn't say whether that route ran at all or what it took out.

* The diagnostic report now says which playback quality gear was picked and from what. The gear-list path was silent, so a report from a phone whose model strings come back empty (issue #3) couldn't say whether a gear was chosen and then ignored or never offered at all. Hook status lists the gear list getter, and one line per distinct choice names the mode, the gear it settled on and every gear it had to choose from.

* The diagnostic report's feed filter table now counts a profile list or a late insertion that arrived empty. A report from a phone showing an empty Favorites tab used to carry no profile line at all, which read the same as the hook never running, so it couldn't say whether TikTok asked for the list and got nothing back or never asked.

## 0.33.0 (2026-09-15)

* Hide suggested accounts now covers every "People you may like" card, not only the inbox lists. The profile header, the Friends tab and the feed's account cards each build their own list, so the switch used to leave them all standing. Every one of those cards is collapsed as it is bound, and comes back at its own size when the switch goes off.

* A change that needs a restart now leaves a row pinned to the top of every settings page, "Restart TikTok to apply this change" (or how many), and tapping it restarts TikTok. Before, the only sign was a toast that disappeared, so a switch that hadn't taken effect yet looked broken. The rows still waiting say "Restart pending" in place of the usual sentence, a switch flipped back to where it was owes nothing, and the row goes away once TikTok has been restarted.

* Hiding a rail button now hides its count too. Hide like button used to leave the number standing under an empty space, half under the creator's avatar, which looked like the switch had done nothing. Counts under buttons you keep are untouched.

* Clear all in the inbox's suggested accounts now counts as it goes ("Clearing, 12 so far") and a second tap stops it. It used to read "Clearing" and refuse every press for up to eighteen seconds with nothing to say how many were left. Stopping reports how many it dismissed, and the next tap picks up the rest.

* The diagnostic report now says what the CAPTCHA gate saw. A report of a comment or a follow that went nowhere used to carry no CAPTCHA line at all, so nobody could tell whether Hushfeed had seen the request, whether TikTok raised a puzzle, or whether the gate was in that build. The report now names the gate as installed, logs the first write it sees go out and the first puzzle that reaches it, keeps the reason a puzzle was shown next to the line it already kept for one it hid, and puts all of that back after you clear the diagnostics.

* Coming back to TikTok with "Do not start the feed on returning" on now shows a "Tap to start the feed" label instead of a silent, frozen feed with nothing to say why. TalkBack announces it once and can no longer swipe through it to the like and comment buttons underneath. And if you tap Profile or Inbox instead, the stop goes away on its own: it used to stay up over the new page, eat your first tap there, and keep the sound held for a feed you had left.

* The block, hide and sound chips no longer start out on top of TikTok's own avatar, like and share column. Out of the box they sat right on it, so a tap on the creator's face hid the creator instead of opening the profile. They now sit a finger's width to the left of that column. If you have already dragged them somewhere, they stay where you put them.

* When a structured override in the Feature Gate Lab can't be applied, the page now says why, and what to do about it. Before, the status stopped at "could not be applied" and the reason only went to the log. It now sits under the status as a warning line: the field this build doesn't have, the value that can't be copied, the list the override never named, or the type the catalog disagrees on, each with a next step. Translated into German, Spanish, Indonesian and Brazilian Portuguese alongside the English.

* The actions that appear when you hold a gate in the Feature Gate Lab fit the screen now. Reset, Disable, Enable and Cancel sat in a row that couldn't wrap, so at larger text sizes the last of them were squeezed down to a box too small for the word and the labels were cut. In German they don't fit at ordinary size either. They wrap onto a second line instead. Cancel is also no longer painted like Enable, so the action that just puts the selection down doesn't look like the one that writes to every gate in it.

* A boolean gate in the Feature Gate Lab has its own override switch now. Importing a profile brings its rules in with the overrides off, so a gate the file said should be true opened with the switch showing on, and the first tap turned it off and forced false. Two taps to get what the file asked for, and the wrong value was live in between. Turning an imported override on takes one tap, and the switch that says what to force is no longer the same one that says whether to force it.

* A download that was interrupted at the wrong moment no longer leaves an invisible half-saved entry in your gallery forever. The cleanup that was meant to find those gave up as soon as it saw a finished file of the same name, so anyone who had already saved a video called the same thing kept every leftover. It also could not find a leftover at all when the gallery had renamed it, which is what happens when the name is already taken. Both are fixed, and the filename a save reports back is now the one the file actually got.

* The diagnostic report now says what Ghost mode did. It stops TikTok telling other people you looked at a story or a profile, or that you are typing, and until now an export said nothing about any of that, so a report of missing follower counts could not be told from a page TikTok never asked about. The report names each of the three places it answered and what it answered, and says Ghost mode is in the build even on a run where none of them came up. It still names no creator.

* Settings, About now has a Licenses row. It shows the notices Hushfeed and the projects it was built on ask to be passed along, with the full licence a tap away. They were only ever in the repository before, which is no use to anyone holding a patched phone, and the projects this is built on ask that the notices reach that person too. The settings search finds it.

* **Hushfeed now needs Morphe Manager 1.30.0 or newer.** The bundle is built against patcher 1.13.0, which Manager 1.30.0 was the first stable release to ship, and Manager refuses a bundle built against a patcher newer than its own. On 1.29.0 the bundle will not load at all, so update Manager before the next release.

* The build classpath no longer carries a Bouncy Castle release with known advisories against it. Nothing here reaches the app on your phone, which is signed by your own Manager, but the version used to sign and assemble during a build sat inside two advisories and now does not.

* The block, hide and sound chips no longer sit over a creator's profile opened from the feed. Tapping a name or an avatar slides the profile in beside the feed rather than over it, and the feed's bottom navigation, still laid out a screen width to the left, kept telling Hushfeed the feed was showing. The chips were live there too, so a press blocked the video underneath rather than the creator on screen. They hide while the profile is up and come back when you swipe back to the feed.

* The same controls no longer sit over a story. Tapping an avatar with a ring opens the story viewer, which hides the feed rather than sliding it aside, and the controls stayed on top of it. A press there acted on the video you had been watching, not the person whose story was playing.

* Comment translation now tells the Hook status row what it found. The four places TikTok calls into it read fields of TikTok's own objects, and a release that renames one used to leave the switch on with nothing happening and nothing said. The row names the class and the field instead, and it travels in the exported diagnostic report.

* Settings rows show where the focus is. Moving through the list with a keyboard, a d-pad or switch access left nothing on screen to say which row you were on, because the only thing a row painted was a ripple, and a ripple draws focus at a fraction of its own strength. A focused row now carries an accent ring inside the card's corners, and so does the back button in the header. A press still looks like a press.

* A text field in settings shows which one you are typing into. Every field wore the same accent underline whether or not it had the cursor, which the Min and Max dialog made worst: two accent underlines and one caret to tell them apart. The field with the cursor keeps the accent and the rest sit quieter.

* A Feature Gate Lab override that fails to save no longer leaves the switch showing the value that was refused. The toast said it could not save while the screen carried on claiming the new value; the controls go back to what is actually stored.

## 0.32.0 (2026-09-15)

* Every control Hushfeed draws over the video answers a press and shows where the focus is. The four feed controls, the two actions on the daily hold, Undo, Clear all in the inbox and the sticker Save button used to paint the same thing pressed, focused or at rest, so a keyboard, a d-pad or switch access moved through them with nothing on screen saying so. Each one ripples under a finger now and carries a white ring while it holds focus.

* The settings screen's hand-built actions do the same. The Feature Gate Lab's view tabs, source tabs, Filter, its selection actions and the value picker, the creator editor's actions, the settings search box and the rows in the SIM preset and tab pickers were flat in every state. Tabbing through the Lab shows the focus moving now, a tap on a tab ripples, and the value picker has the frame that says it is a control. The preset and tab rows painted an opaque fill that covered the press highlight the list would have drawn; the fill sits under it now.

* A gate you have chosen in the Feature Gate Lab looks chosen. Selecting rows for an action changed nothing about them, so the count in the action bar was the only sign anything was picked. A chosen row now carries a tinted fill, an accent edge and a check mark at its start.

* The daily hold no longer reads itself out once a second. The whole panel was a live region and three of its lines were rewritten every tick, so TalkBack repeated the hold over the audio for as long as it stood. The panel is announced once when it appears, only the time remaining is live, and nothing is rewritten unless it changed.

* The daily budget label stays on the feed. It asked the loose question the block button asks, which counts a video opened from a profile grid or a search result and says nothing about the comment sheet, so opening the comments took the feed controls away and left the label sitting on top of the sheet. It now shows only when the Home tab is on screen and selected and nothing is covering it.

* The Region rows stop asking for a restart twice. Both summaries carried their own "and a restart" and then the shared restart sentence was added under it. Each says it once.

* The Region summaries name the switches they depend on by the titles those switches actually carry in German, Spanish, Indonesian and Brazilian Portuguese. A German phone told you to turn on "SIM-Details überschreiben" while the row above it was titled "SIM-Angaben überschreiben", and the other three tables had the same drift. A check now holds every summary that names another row to that row's title in every language.

* The Diagnostic tools description in Morphe Manager says Hushfeed rather than the project it was forked from, and says where the switches it adds live: under Diagnostics in Hushfeed settings.

* The README's settings captures were re-taken after the recent changes, and the page that shows a settings failure is captured in English rather than in the language of whichever phone took it last.

* The hidden creators editor keeps what you typed and says what is wrong under the box. Type a handle and press Save without pressing Add and the dialog used to close, then a message appeared over whatever was behind it, and the handle was gone. Adding an empty, duplicate or broken entry did the same thing over the open dialog. The reason now sits under the field it is about, the dialog stays open, and nothing is written until it can be.

* One sentence asks you to restart TikTok, not nine. The same screen used to say it four or five different ways, sometimes twice in view of each other, and each wording was its own thing to translate. Every row summary and every single-change message now says "Restart TikTok to apply this." Restoring, resetting or undoing settings, where several moved at once, says "Restart TikTok to apply all changes." Nothing else.

* The section rows on the settings screen say how many settings you have changed, not how many are on. The number was always a count of settings away from their default, and two of them arrive switched on, so turning one of those off read as having turned something on.

* Four patch descriptions in Morphe Manager no longer end in a stray space left behind when a sentence was cut, and two no longer carry a credit line in the text somebody reads while deciding whether to select the patch. The credit is in the README, where it has a link to follow.

* The release gate is now itself tested. The check that holds the README and the published index to the generated patch catalog runs on every push and before every release, and had never once been shown to refuse anything. It is now driven against a copy of the checkout with one fact moved at a time: an index naming another version, a patch count that is not the catalog's in either the index or the README, a README pointing at a Manager too old for the patcher, and a download link that answers nothing.

* Hook status speaks for the sensitive-warning switch. Turning those overlays off means writing one flag on a model TikTok owns, and if that model is renamed the switch still reads on while every warning stays exactly where it was, which looks the same as a video that was never flagged. The row now names the field and the model it looked in.

* The README and the Region page answer the three questions people keep asking. Downloads has always ignored the flag TikTok sets when a creator turns downloading off, and nothing said so. Region settings now says plainly what spoofing cannot change, which is your IP address, your account's history and the language you read in. And a patch run that sits at 24 or 25 percent forever is a memory problem with a known way through it, which the install steps now give.

* The daily budget rows and the feed tab picker are translated. Six Playback descriptions, the unit words beside their numbers and every tab name in both pickers had no entry in any table, so a German, Spanish, Indonesian or Brazilian phone showed them in English under a translated title. Both translation checks were blind to it, because a row's text is joined with its range and current value before anything sees it. The check now reads the wording from the row itself, where nothing has been joined to it yet. The feed tab names also read the same way as their siblings now: Nearby, Friends, LIVE, STEM and Drama and Series.

* The two country lists in the feed filter check what you type, and "UK" now works. Only from these countries took anything at all, and an entry that is not a country code matched no video, so one typo hid the whole feed with nothing on screen saying why. The row names the entry it cannot use before it saves, "UK" is read as the GB the videos carry, and a list saved before this check existed can no longer empty the feed either.

* Copying a sound link now cleans it the way copying a video link already did. TikTok's share URL for a sound carries the parameters that say who sent it, and the long press put it on the clipboard exactly as written, so the identifiers travelled with every shared sound. The link is stripped now and takes the custom share domain like the video link does. Both copied links are also marked sensitive, which they were not: they were the only clips the bundle wrote without that flag.

* The Not interested button no longer goes dead for the rest of the session. It marks itself busy before handing the request to the shared worker pool, and when that pool is full the request is refused and never runs, so nothing ever cleared the busy mark. The reader got one "Sending feedback" that never resolved and then a button that did nothing at all. A refused request now says so and leaves the button ready for the next tap.

* The App behavior page can be opened again on a bundle that carries only the launcher shortcuts patch. The row into that page asked a list of patches kept separately from the page's own, and the two had drifted by one: the shortcut switch was built, and nothing on the home screen led to it, so the settings search was the only way in. The page answers for itself now, and the test that sweeps one patch at a time covers it.

* The text, range and tab rows say when they need a restart, the way the switches already did. Nine of them carry restart-gated settings, the country lists, the five count filters and the two tab pickers, and said nothing about it, so a change that showed no effect looked like a hook that had stopped working. A test now walks every settings page and holds every row whose setting needs a restart to the sentence.

* The About row names the TikTok build as well as the Hushfeed version, and the settings search finds it. The row sat outside the search index with no key, so "version" and "hushfeed" both came back empty on the phones that showed it. Searching either now lands on the row, and the second question on every bug report, which TikTok the bundle was applied to, is answered on the same line.

* Diagnostics has a row for the failure messages. The switch behind them was on from the first release and had no row anywhere, so the only way to stop a message was to turn diagnostic logging off with it. Show failures on screen now sits under the logging switch and decides on its own whether a failure is put in front of you while logging is on. Off keeps failures in the report alone.

* The exported diagnostic report no longer names the creators you block, hide or follow. The block, hide and Not interested paths wrote the creator's display name into the log beside the toast that showed it, and every toast is written to the report as it is shown, so a report attached to a public bug had the names in it. Log lines now carry a pseudonym instead, the same one the follow report uses for the same account, and the name in a toast is marked so the export leaves it out in every language. Handles standing on their own are dropped too. Follow diagnostics phases are debug-level lines now; the refusal itself still reports at once.

* Double tap set to open comments now opens them. It pressed the comment button's click listener, which on the current app is a placeholder that does nothing while the real press is handled as a touch, so the double tap returned true and nothing opened. The press now goes through the comment assem's own icon-press method, the one TikTok's keyboard shortcut uses, with all of TikTok's own checks in front of it. Confirmed on the S22 on 46.2.3: do nothing leaves the like and the video alone, comments opens the sheet with the like untouched, and the default still likes.


## 0.31.0 (2026-09-14)

* The exported diagnostic report now says which feed filter route handled each list, whether or not diagnostic logging is on. Several routes can put a video on a profile page or in the feed, and an advert in a screenshot cannot say which one delivered it, so every ad report so far has needed a second round of questions. Each route now reports the lists it was handed, the videos in them and what it took out. A route that has never run has no line at all, which is the answer that was hardest to get before.


* Double-tap controls now reach the feed the app actually uses. Setting it to "do nothing" still liked the video, because the guard sat on the older feed panel and the current one never goes through it: its gesture listener holds the like component and calls it directly. Every route from a double tap to a like was traced on the app itself, and there are four of them, including the landscape player and the Friends tab. All four are guarded now. Two more classes carry the same method and neither can like anything, so they are left alone.


* Playback quality stops complaining about a model it cannot read. On some builds and accounts TikTok's video model getter hands back an empty string, and the quality picker parsed it anyway, failed, and put "Could not read the playback quality model" on screen once per video. It now hands back whatever it was given and leaves the quality to the app, which is what it did all along, without the message. The Hook status row names the getter that supplied the unusable model, so a diagnostic report says which one it was instead of the feed saying so repeatedly.


* Error messages from inside Hushfeed no longer appear over the feed unless diagnostic logging is on. They carry the name of the class that failed and the exception's own English text, past every translation the bundle ships, and on a path the feed runs one of them fired once per video. They still reach the log, the diagnostic buffer and the exported report, which is what a bug report needs, and the same message now shows at most once per run rather than queueing behind itself.

* A switch that needs TikTok restarted says so. Fifty settings do nothing until the app starts again and only thirty-four of them mentioned it, so sixteen switches moved and nothing happened, with nothing on screen explaining the gap. The sentence comes from the setting itself now, so it is on all fifty and cannot be forgotten on the next one added.

* The four controls Hushfeed draws on the feed, the budget label and the hold's release control now share one backdrop and one corner radius. They were built five separate times from the same two colors and then rounded three different ways, so a column of controls sitting on the same video read as three unrelated add-ons.

* The About row shows which version of Hushfeed is installed. It was only in the exported diagnostic report before, so there was no way to read it off the screen, and searching the settings for "version" found nothing.

* The Feature Gate Lab says which of the search and the filter emptied the list, and offers to clear the search when that is what did it. It used to blame the search either way, because the message was written once when the catalogue loaded. The gate details page's "no longer available" state now offers a way back to the Lab it tells you to refresh.

* When settings will not open, Try again comes first and is drawn in the accent. It was listed under Go back in the same weight and color, so the first thing offered was the way out.

* Save media on a sticker, the tap that starts the feed after a hold, and both actions on the settings recovery page are offered to a screen reader as buttons rather than as text. Save media also has a 48dp floor now, which it did not when the TikTok row it copies its size from was shorter.

* Four places spaced a row from the physical left edge instead of the start edge, so in a right-to-left layout the gap between a menu icon and its label, the gap before the chevron, the indent under a share action and the inset in the Lab's search row all stayed on the wrong side.

* Comment search takes its eight colors from the same palette as the rest of the settings screen instead of its own copies of them. It still works out the comment sheet's theme itself, because that is not always the system's.


* Ghost mode no longer closes the app when a story is opened. The old guard returned early from TikTok's story and profile reporters with a made-up null where the caller expected the lazy request it was about to enqueue or subscribe to, and the caller fell over on it. The same null was handed to the profile page's view model halfway through building the page. The guard now sits at each place a reporter is called and steps over the whole send, so the reporter is never called and the caller carries on with what it was doing. A reporter whose caller only checks for a suspended coroutine completes with nothing instead, and the typing indicator, which returns nothing, keeps its early return. A patch test walks the four caller shapes shared by 46.2.3, 46.7.3 and 46.8.3 with the guard answering both ways, and the patch refuses to apply to any chain it cannot follow to its send rather than guessing.

* New Playback setting, "Show what is left of the budget", off by default. A small label at the top of the feed shows the minutes or videos left of today's budget, whichever is closer to running out. Both budgets were previously only visible at their own edges: a reminder partway through, a notice when the day was spent, a hold if one was set. Between those you were guessing. The label appears only on the feed, goes away under the hold, changes at most once a minute, and is never read out on its own. A screen reader finds it in traversal and hears the value then.

* Reset position on a feed control now moves only that control. It used to re-place every control that had never been moved by hand, which meant resetting one of them could jump two others across the screen if the block button had been dragged since.

* Inbox Clear all says it is working on Android 10 and older too. The control's description is what a screen reader is given instead of its label, so changing the label alone left the whole eighteen second run announced as an ordinary disabled button. A heading TikTok rebuilds mid-run now also comes up saying the run is still going, instead of looking pressable while silently refusing.

* The four feed controls can now be moved with a screen reader. Each one offers Move up, Move down, Move left, Move right and Reset position, in the phone's language. A move shifts that control by its own width and stops at the screen edge, reset puts it back where it started, and neither touches the other three. The pointer long-press drag is unchanged, and an accessibility long-click is still refused because it has no release to end the gesture with.

* Inbox Clear all now shows what it is doing. Clearing up to 60 suggestions takes about eighteen seconds, and the control used to sit there looking pressable the whole time. It now reads as a button to screen readers, fades and refuses presses while a run is going, says "Clearing" as its state, and comes back on every ending: finished, nothing to clear, the 60 cap, the Inbox closing, or a dismissal that failed. A failure says how many accounts it got through before it stopped, and the run can be started again straight away. Every outcome is announced as well as shown.

* Robolectric tests now use Bouncy Castle 1.85.2 instead of 1.81. Every TikTok extension test inspects the resolved dependency graph first, and Gradle's reviewed checksums no longer accept the vulnerable jar.

* Feature Gate Recorder copies now use Android's sensitive clipboard flag while keeping the existing success, large-report and failure messages. Empty reports remain safe to copy, and reports above 60,000 characters still require Save JSON without replacing the clipboard.

* Settings pages now replace partial content with translated Back and Retry actions when loading fails. A failed preference update restores the typed saved value, dependent controls and default-value storage on the main thread, then reports one translated result. Nested recovery callbacks keep the outer update locked, and restart-required settings report success only after the complete update succeeds.

* Comment search now shows a translated result count below the field and explains when nothing matches. The polite status follows scrolling, recycled rows and cached sheet reattachment without moving focus. Turning search back on restores row tracking even when TikTok does not bind the rows again.

* Feature Gate Lab messages now stay in the selected language, including errors and current state text. Block and unblock results do too. Gate keys, raw type spelling, values and creator identifiers remain unchanged.

* Text and number settings, download destination dialogs and Feature Gate object fields now give screen readers one named editor. The spoken node keeps the current value, required keyboard and disabled state without repeating the visible label.

* Caption and creator filters now parse unchanged lists once per stored value instead of once per video. Feed-rule lists accept up to 10,000 entries or 256 KB, and oversized dialog edits or imports stay out of storage with a translated explanation.

* Downloads with a missing or malformed size header now recheck free space while streaming. They stop and remove partial media before reaching the 32 MB floor, with another 8 MB kept for publication.

* Rapid Feature Gate detail edits now save in tap order. Only the newest result can update the screen, and one Undo returns the whole burst to the value that was present before the first tap.

* Settings backup, restore, reset and Feature Gate Lab changes no longer stay locked when the shared worker queue is full. Rejected Lab switches return to their stored position so the next tap retries the change, and a translated message asks you to try again.

* The Add Hushfeed links now use the encoded repository slug. The install steps also include a manual Manager path for browsers that refuse to open other apps. The browser button, source download and Manager 1.29.0 and 1.30.0 flows were exercised on the S22.

* Remove feed ads now catches videos carrying TikTok's Creator earns commission disclosure. It reads the structured anchor label rather than matching visible English text, and malformed or unrelated anchor data stays in the feed.

* Comment tools can now remove TikTok's polls from the comment sheet before they are drawn. A missing or changed poll model leaves comments working and identifies the broken contract in Hook status.

* The block, local-hide, sound and Not interested controls now disappear when TikTok's comment sheet covers the feed. The local-hide and sound buttons have their own switches, and each visible control can be moved and remembered on its own. Enabling local hide after the controls attach keeps it in its own slot instead of covering the sound button.


* Eight optional TikTok optimizer patches from Kveld are now built into Hushfeed. The four resource patches validate complete path and SHA-256 profiles before emptying language, creator, LIVE, C2PA or card-scanner assets. The startup, network, Fresco and update patches use exact reviewed method contracts, stay off by default and describe their feature tradeoffs. Disable telemetry also covers TikTok's two Npth startup tasks behind its existing switch. The pre-push preparation check can keep the published 71-patch index intact while this unreleased catalog grows at the same version. Strict publication checks still reject the mismatch.

* Media downloads now validate the original address and every redirect. They allow only public HTTPS destinations with no embedded credentials, stop redirect loops after five hops, recheck DNS before connecting, and remove partial files when a destination or body is refused.

* Right-column counts now follow both parts of TikTok's feed layout: the outer count row and the numeric text inside it. Layout variants that replace the row can no longer leave like, comment, favorite or share counts visible, and turning the switch off restores only the views Hushfeed hid.

* Hide inbox stories now responds without restarting TikTok. It hides only the story tray, restores recycled rows when they change purpose, and shares one live Inbox observer with Hide inbox items when both patches are selected.

* Remove feed ads now recognizes TikTok's raw auction metadata even when both native Boolean ad flags are false. The shared check covers response, cache, Following, Friends, profile, search and late-insertion feeds while leaving ordinary paid-partnership posts alone.

* Share sheet confirmation now handles every click path, including accessibility actions and keyboard input. It follows the recipient's account or conversation ID. Reusing a row or letting the timer expire resets the check, and TikTok's original row highlight comes back. If the confirmation setup or setting read fails, the tap is stopped and an older confirmation is forgotten. Turning confirmation off restores normal sending even if an old row highlight cannot be cleared.

* Feature Gate Lab now checks its 1,024-rule limit before writing a change or importing a file. Reset all Lab data can recover an older oversized store without clearing other Hushfeed settings.

* Settings Undo now recognizes an interrupted current or legacy undo write when only its recovery file remains. It always prefers the current Hushfeed copy, and the next verified snapshot moves storage off the old Metra filename.

* Injected-register verification now requires identical Android verifier message counts from clean and patched APKs. Its static check also rejects removed host methods and DEX files unless an exact reviewed removal is listed beside the verifier.

* Device verification now removes each uploaded APK and its generated ART files after every result, including failed uploads and disconnected runs. Cleanup errors are reported without replacing the verification failure that caused them.

* Session budget recovery now validates the whole persisted record before making any of it live. Damaged fields and invalid shapes reset to clean current-day values and are rewritten, while historical five-field records and the current nine-field format keep their exact meaning.

## 0.30.2 (2026-09-13)

* GIF conversion now has separate deadline regressions for its color-precision, palette and frame-encoding passes. Each check has to stop before the second frame, so losing one cannot hide behind an earlier pass.

* Logger keeps a failed message builder contained even when the thrown object also breaks its own `toString()` or `getMessage()`. The emergency line falls back to fixed text and the original hook can keep running.

* Comment tools now resolves every native anchor, reply model, field and register before it changes the APK. If a later TikTok build makes the reply row ambiguous, patching fails without leaving the settings flag or dislike redirect behind.

* The obfuscated identity audit now records Java binary class names such as `X.0GSy`, as well as smali descriptors. Exact token boundaries keep longer names and ordinary Java text from being counted, and a regression proves a new `Class.forName` literal stops the build.

* Patch result checks now accept dependency rows declared by a selected patch while still rejecting a missing root or any duplicate or unrelated row. Device patching and fixture checks use the rule, as does heap measurement.

* Cached model reflection now finds public methods inherited from interfaces. Follow diagnostics uses the required lookup for a request path, so a future TikTok rename appears in Hook status instead of making every path look unknown.

* Video fitting now hands a changed center gravity back to Android so the view is laid out again. The story path forgets its fitted result after both offset reads, and its tests now prove the feed and story paths independently read an uncopyable result.

* Lazy AB gate discovery now follows one integer register move back to the factory number, including a second four-instruction lookback. A merged-lambda entry point must switch on its own integer `$t` field, so an unrelated field and switch cannot make a decoy class look like the requested gate.

* Passing a Java directory without `bin/java.exe` or `bin/java` now stops with that directory in the error. It can no longer ignore the explicit choice and run a different Java from `PATH`. Java discovery also works in Windows PowerShell, where the normal `java -version` banner arrives on the error stream.

* Device replacement now checks whether TikTok is installed before trying to remove it, so a clean phone proceeds to installation. Device patching, fixture verification, heap checks and release validation all read the target package and version from the generated patch catalog.

* CAPTCHA patch validation now follows request and callback methods inherited from superclasses or interfaces. The risk-control hook also refuses a static `execute` method because its injected parameter registers require an instance receiver.

* Settings journal recovery now has direct regression coverage for a valid record that storage refuses to apply. The test requires the failed recovery notice, retains the record for diagnostics and confirms that the next settings change can start.

* Each unreadable settings journal now gets its own recovery notice. Its fallback identity includes the actual file path, length and modification time, so another damaged record at the same path is not hidden for the rest of that process.

* A settings journal that was recovered successfully keeps that result when only deletion of the journal fails. The record is set aside for diagnostics, while the notice still reports recovered or already completed settings.

* The release check now ties the README's minimum Morphe Manager version to a `manager-floor` value beside the patcher pin. Device-only signing helpers read their password from `HUSHFEED_SIDELOAD_KEYSTORE_PASSWORD`, retain `sideload` as the documented local test-key fallback, and keep the value out of child process command lines.

* The README patch table now uses the same descriptions Morphe Manager reads from the shipped catalog. A regression checks every name and description, so changing either side cannot leave the project page describing a different feature.

* Patch failures now name the selector or method contract that changed instead of ending in a collection or null crash. The offline option hook requires the exact six-register enum constructor it reads. Story downloads require both bind methods, and the settings row reserves low registers for its plain invokes. Region spoof keeps the getter's documented first-field rule. Screen-capture patching no longer writes a count to standard output.

* The release gate now checks the published bundle against the version tag during the final index push. The index is committed after the artifact exists, so comparing the bundle to that later commit rejected a correct release.

## 0.30.1 (2026-09-12)

* TikTok's own Photo Mode save now gives every image its own `{index}` number instead of naming the whole slideshow as image 1. This also prevents later photos replacing the first one on Android 6 through 9. Animated WebP to GIF conversion now budgets the canvas and decoded frame alongside the stored frame pixels, which cuts the worst pixel allocation from about 100 MiB to 32 MiB before encoder overhead. Turning off Keep captions in clear display removes its overlay and layout observer immediately. Media jobs keep their two minute deadline without carrying a cancel path that no shipped control could call.

* Creator identity now uses the same canonical value for equality and hash collections. Fire-and-forget background and main-thread callbacks report every failure instead of letting an Error escape into TikTok. Android API checks now cover the full shared and settings source rather than being disabled for two whole files, the TikTok stub matches the app's API 23 floor and has its own namespace, and artifact verification rejects any DEX payload with no classes.

* Silent reflection failures now reach Hook status for CAPTCHA account checks, external browser handoff, sticker source adapters and story saves. Each feature keeps its existing fallback, while a renamed required class, method or field identifies the affected family and member in Diagnostics.

* The dark and light Included diagnostics screenshots now come from the shipped preference. Both show its eight real choices and Apply and Cancel actions. The capture test rejects a missing choice or the return of the invented Done button.

* The Feature Gate Lab filter now uses the same themed radio rows as the other settings pickers. Rows stay fully readable while selecting several gates. Exporting before values have loaded says why it cannot start, and a selected import keeps going if the Lab closes while its file is being read. The Lab itself now appears when its title or description is entered in Settings search.

* Numeric dialogs let long descriptions scroll without squeezing out the field, and an empty number stays open with a clear explanation. The sticker save button follows the current text colors when TikTok gives it no text button to copy. Clearing diagnostic data can be undone with the next tap, including buffered events, saved crashes and hook findings.

* Feature Gate Lab saves now confirm when they finish, and disabled values have a visible muted state. Its custom actions are announced as buttons, source tabs report the selected tab from the focused control, and filtered lists in the Lab and settings announce their result count. Creator removal says which entry was removed and moves focus to the next action. Number fields now carry a hint, and custom dialog titles are marked as headings for screen readers.

* Hooks that change a value as TikTok returns it now keep the return's branch label on the hook. A direct jump to that return can no longer skip Hushfeed's filter, setting override, diagnostic callback or UI attachment. The same rule now covers every current return hook, including ones added after the original audit.

* Settings no longer unpacks the APK just to find the Hushfeed row's gear icon. The patch reads the icon from TikTok's own Ad Browser settings action, following the method that the stable icon renderer calls. Ordinary patch jobs can now stay in bytecode-only mode. The full resource rewrite still runs when the optional AMOLED theme is selected.

* Feed filtering follows TikTok's rebuilt cache delivery on 46.7.3 and 46.8.3. Cached videos now pass through the same filters before either new callback wrapper sees them, including the local seen-video history. The older 46.2.3 chain stays supported. A rejected reach-bottom cache result is also marked unsuccessful, so TikTok asks the next source for a video instead of treating the rejected one as delivered. The friend recommendation-card selector now tolerates TikTok swapping its String and List parameters, and the Tako button selector follows stable view behavior instead of its changing method name.

* Hide quick comment reactions works on TikTok 46.8.3 after TikTok removed the experiment the patch used to change. The reaction row still exists, but an Aweme check now decides whether to show it. The patch follows the row itself and applies the setting to every native visibility change, which also keeps 46.2.3 and 46.7.3 working without their old experiment name.

* The project page now opens with a 1600 by 900 hero built around the approved H and current native settings screens. The artwork carries no release number, so a routine version bump cannot make it stale.

* The README puts the Morphe source link, latest bundle, settings tour and patch list near the top. Its feature overview is shorter, the exact supported TikTok build is harder to miss, and the release link points at the current bundle.

* The previous header, both finished hero layouts, README-width previews, fresh offscreen screenshots and rejected mark studies now live in the marketing concept archive. A regression test keeps the selected hero first, prevents a second copy, checks its dimensions and locks the approved H to the supplied file.

## 0.30.0 (2026-09-10)

* Fit the video to the screen now works on the For You feed. The feed cell never sized its video through the method the patch hooked; it hands the size to a helper of its own, and that helper is what the patch reaches now, on every build seen. A fitted video is handed back as a copy of TikTok's own answer, so the size the feed applies and the size it checks against are the same one. The story cell's own path is still covered, and the copy it keeps is looked up by the object rather than by its numbers, which a size change had moved out from under.

* Fit the video to the screen no longer writes a size of nothing into a story when it can't read the video's size. On every path where it has nothing to fit, it leaves TikTok's own numbers alone, the way it did before the feed rework. The patch also refuses a build where either feed helper it hooks has a twin, instead of hooking whichever one it met first, and checks the size getters and the copy method the feed path relies on before it writes anything. With the switch off, the feed skips the size lookups altogether.

* Anchors that find a setting behind a lazily read value no longer skip a class that carries two methods of the shape they were looking for. Before, such a class was passed over as if it read some other key, and the failure said no class read the key at all. The walk through R8's merged lambda groups also follows entry points that take an argument, reads every no-argument method of a lambda class rather than the first, takes the constant from the register the factory call actually reads, and answers only from the switch that opens the dispatcher.

* A settings journal that can't be read or applied is set aside instead of being left in place. Left there, every later settings change, Undo and Restore included, was refused with a notice telling you to use Undo or Restore. The notice now says what happened. The lock that guards the journal is also released through a finally block, so an out-of-memory error during recovery can't leave every later change waiting forever.

* Twelve toasts and one dialog in the Feature Gate Lab and the diagnostics export reached the reader in English on a translated phone, several of them branded for the shared library rather than for Hushfeed, and two carried an exception's own text. All of them go through the translation table now. The Undo entry says there is nothing to undo instead of showing the path of a file that doesn't exist, and an import that accepted nothing no longer offers an Undo that would revert the previous Lab change.

* The creator list dialog saves a handle that was typed into the box and never added with the button. It was dropped without a word.

* The free-space check before a save no longer stands down when the destination folder doesn't exist yet, which is exactly the first save into it. A crash report cut at the size ceiling now says so, at the top as well as where it stops, and is cut on a character boundary rather than in the middle of one. Converting a sticker to GIF checks the job's deadline once per frame, while it decodes and again while it encodes, the way the MP4 path does, so a small canvas with tens of thousands of frames can't hold a media worker for good. A muxer that fails mid-copy no longer replaces the real error with its own complaint on release.

* A log message that fails while being built, or a log line that can't be written, no longer throws out of the hook that was logging, into TikTok. The follow diagnostics path looks up the request's path through the cached reflection helper rather than an uncached lookup on every network request.

* Patches refuse more of the builds they can't patch, by name, rather than applying and doing nothing or shipping an instruction a phone's verifier rejects: every injection that writes a scratch register at the start of a method checks that the method has a local to write; every call that names a register read off a return uses the range form; the quick reactions gate reads its return register off each return instead of assuming v0; follow diagnostics counts its hooks and fails when a rename leaves one unhooked; the photo download anchor no longer matches the lookup method beside it; the risk-control CAPTCHA patch checks the request and callback shapes it calls; and a missing instruction is reported with the method it was missing from.

* Follow diagnostics checks all three of its hooks before writing any of them. A build that renamed one used to fail the patch with the other two already written into the app.

* Resume videos after scrolling no longer writes three of TikTok's internal class names into the code it adds. Those names only mean the right thing on 46.2.3. On a newer TikTok they belong to other classes, so the patch applied without complaint there and the first video played to the end with the switch on would have stopped the app. It now reads where TikTok keeps its playback positions off TikTok's own code, and refuses a build where that doesn't add up.

* Comment tools reads the reply row's model off TikTok's own code too, instead of naming it. The search box above the comments uses that model to keep a collapsed reply row collapsed, and three of the four names it needs are different on a newer TikTok.

* The thumbs down that Comment tools turns into a block button is found by what it does now. TikTok installs the like and the dislike touch handlers back to back, and the patch used to take the second of the two by the name of the view it sat on. It now follows each handler to the code that runs on a touch and takes the one that asks whether the comment is disliked, so the block gesture can't end up on the like button if the two ever swap places. The same change lets Comment tools apply to TikTok 46.7.3 and 46.8.3, where it used to fail.

* Custom offline videos limit finds TikTok's lists of offline choices through the offline page that reads them, instead of by the name the class had in 46.2.3. Newer builds keep four lists instead of two, and the custom limit is added to every one of them, so the patch applies to TikTok 46.7.3 and 46.8.3 as well.

* The Hook status row in Hushfeed's debug settings says so when a build has renamed the bottom navigation tabs. The block button relies on the Home tab to tell the feed from other screens, and on a build without it the button quietly stopped hiding itself off the feed.

* Search features, Allow Duet and Stitch and Open links in external browser no longer refuse a build just because the value they read sits in a high register. They use the instruction forms that reach it.

* A crash report that was being written when the app died is read back from the last whole copy. It used to look as though no crash had been saved.

* The included diagnostics picker won't apply with every kind cleared. It used to save that as all events and then say it included all of them.

* The Feature Gate Lab says why it turned down a loaded-values file: it's from another TikTok version, it isn't that kind of file, it has no values in it, or it has more than the Lab takes at once. It used to call every one of them invalid or too large.

* Automatic video advance and Comment sort controls override the setting they read by following the setting's key to the lookup it is handed to. They used to take the first answer after the key, which on a build with another call in between would have been some other value.

* A settings backup carrying more Feature Gate Lab rules than the Lab keeps is refused, and the refusal says so. A hand-edited one used to restore, and after that every backup, every Reset and every Lab change failed because the next copy was too large to write.

* The pre-push check runs the patch module's tests as well as the runtime ones, and treats the version catalog, the settings script, the dependency verification file and the Gradle wrapper as release facts. A push touching only one of those ran no gate at all. A README edit on a clean checkout is no longer blocked by a release check that wanted a built bundle it had just said it didn't need.

* The device scripts read as success when adb refused an install, chose the clean APK by file size, could carry one dex2oat exit code over to the next run, left gigabytes of unpacked APK behind, and wrote an empty screenshot when the capture failed. Each of those is fixed. The verification probe compiles again and holds its broadcasts to the DUMP permission, which the shell has and an ordinary app can only be given over adb.

* The register check keeps its report when it fails. Run without a report path, it named the report in its FAIL line and then deleted the folder the report was in.

* The README's Patches heading renders on GitHub again; a stray line break tag had swallowed it into body text. The bug report template asks for the phone and its Android version, and its example names a Manager release that can load this bundle. The split-view width setting says dp, which is what it compares against, instead of pixels. The Feature Gate Lab no longer says "No gates match" under "Loading" before it has looked, every switch on its detail page has a name for a screen reader, its page title follows the font scale like every other page, the share checklist's boxes take the theme, and the greyed Undo row says why it's greyed.

## 0.29.0 (2026-09-10)

* A new switch empties the menu that opens when you press and hold TikTok's icon on the home screen. Those entries are not declared anywhere in the app, TikTok builds them while it runs, and it only rewrites them when it notices a difference. So the switch takes away what is already published and answers the handover that would publish more. Turning it off asks TikTok to build them again. Tapping the icon still opens the app, and a shortcut you pinned to a home screen yourself is left alone.

* The v0.28.0 download has been replaced. The bundle pins a build stamp to the commit it was built from, so that anyone can rebuild it and check the published checksum against their own, and that file was built a couple of commits before the release it shipped as. Its patches were the right ones, entry for entry; only the stamp was wrong. The release check now reads the stamp back and refuses a bundle that was not built from the commit being released.

* Everything the patches inject is now checked against the register count of the method it lands in, and against the Android runtime's own verifier on a phone. A patch that writes into a register a method never declared assembles cleanly, applies cleanly, and fails only on a device; nothing here does that.

## 0.28.0 (2026-09-10)

* Automatic video advance now works from a cold start. Two things stood in the way. TikTok builds its auto scroll component only once somebody opens the video panel and asks for it by hand, so with the setting already on, a fresh launch had nothing to work with. And the check for whether the feed was on screen asked TikTok's auto scroll indicator about itself, which TikTok keeps hidden until scrolling is already running, so the answer was always no. With the setting on, the component is now built alongside the ones TikTok always builds, and the feed it sits in is what answers for being on screen. With the setting off, TikTok's own choice is left alone. Automatic advance also looks again when a video ends rather than staying down for the rest of the session, so changing the session limit or coming back to the feed picks it up again without restarting TikTok.

* Changing the auto-advance session limit now starts a fresh count on the same feed. The new session can count its current video and show its own stop notice. Saving the same limit, changing another setting or returning from settings keeps the existing count.

* A final playback report can arrive before TikTok applies a daily hold's queued pause. That report now keeps the matching resume ready for audio focus to return. Playback after an observed pause still releases that ownership.

* A daily hold now keeps its paused video ready when another app owns audio focus at expiry. A later native focus grant can resume that same video once, provided its feed is still visible. A reported independent resume gives up that ownership, so a later pause won't be undone.

* Profile-picture saves now try TikTok's largest image before the medium and smaller variants. The previous priority picked a 300-pixel image even when the current profile supplied a 1080-pixel original. Current-profile ownership and secure fallback URLs are preserved.

* Long-press actions now run from the current feed's coordinate callback as well as its older gesture listener. The patch keeps TikTok's own timer and menu coordinates, and hands the gesture back when the chosen action doesn't handle it.

* Edge seeking now converts the requested position into the percentage TikTok's player expects. Its position, video identity and duration checks still use milliseconds. The player fixtures were corrected to use the native conversion, which exposed this mismatch.

* Turning comment search off removes its box from an already open comment sheet and restores the rows it hid. Turning it back on works with those same loaded comments, while rows TikTok collapsed keep their native state.

* Selected patches now activate on app launch, before their first runtime hook. Opening Hushfeed settings is no longer needed to turn them on, including search controls used before that page opens.

* Settings changes now survive an older settings page remaining open. Each page reads the saved choice before refreshing its controls, so an old switch or text field can't undo a newer choice.

* Custom long-press actions now take precedence over TikTok's 2x edge hold, including seeking by the chosen distance. The default keeps TikTok's behavior.

* Hiding search suggestions now also covers cached recommendations and search-page returns. Your search history stays available.

* The comment-sort description now explains that media and creator filters depend on the post's native availability flags. Device checks confirmed those optional categories and preserved the stock menu after disabling the switch.

* Profile-picture saving now handles the current profile header and resolves the picture from the pressed component. Story long presses reach the native child that consumes the gesture and save its current story, even when the outer view holds a collection. Turning either option off keeps the native gesture.

* Comment search now hides reply controls with their nonmatching parent comments. Clearing search restores the rows. Turning search off also releases its saved heights and visibility during native rebinding, with either comment-blocking setting, while preserving rows TikTok hid itself.

* Follow readback now recognizes the modern profile response and keeps each relationship state with its own account identity. A reported zero remains zero; conflicting states stay unclear. Diagnostics can report a mismatch after an accepted follow without guessing its cause.

* Daily-budget holds pause the current native player and resume only the player they held, while its feed is visible and audio focus permits playback. Holds survive settings returns and activity recreation. Their lower edge follows the actual tab row, including the system navigation gap, so Home and messages remain tappable.

* Enabling the block or Not interested button now attaches it to the current creator immediately. Disabling the last button removes the controls. A daily-budget hold keeps those controls hidden while the feed is covered.

* The phone helper rejects a log query without its required filter. That usage error no longer exits successfully after printing an error.

* The phone helper reports failed activity, log and frame-stat queries instead of hiding their exit status behind output filters. A screenshot no longer claims success when its foreground check fails.

* Changing font size or navigation mode keeps the Hushfeed settings page you were using. The restored page reuses its original container, and Back still returns through the pages you opened.

* Follow diagnostics no longer injects a String logger over integer arguments in TikTok's detailed follow caller. Both direct follow methods are covered inside their shared native API; the separate stream path keeps its own hook. Skipped and failed requests can't inherit an earlier diagnostic ID. Account pseudonyms now use keyed SHA-256 instead of a reversible short hash.

* New regressions press the installed search, Hook status and About controls, render the block symbol, and load all four generated gate catalogs. Caption checks drive real pre-draw callbacks through detach and activity replacement. Seen-history checks exercise initial loading, expired SQL records and the 200th progress-write cleanup.

* Comment blocking recovers when the worker queue is full instead of leaving the button busy. Turning it off restores the original dislike touch handler and the actual comment cell's appearance. A press already started as a block can't become a native dislike, and discarded cells can be collected. Rebinding a row during a press also keeps that release away from a cancelled native gesture.

* On Android 6 through 9, a profile-picture long press explains when storage permission prevents saving. Granting permission lets the same picture save. The message is translated into all four supported languages.

* Saving a video without sound preserves its rotation metadata before the muxer starts. Tests also check sample timestamps, flags and cancellation without deleting the input.

* Native GIPHY sticker URLs now obey the same HTTPS restriction as other sticker sources. Rebinding the save button to an invalid source can't leave the previous sticker attached.

* Story saving no longer keeps discarded story screens alive through its owner index. A live or rebound story still resolves its own media; a missing owner reports that the story is unavailable.

* An MP4 sticker that fails during graphics setup releases its encoder surface and graphics resources. Cleanup still runs if another release step fails, and the original error remains available.

* The two long-press copy choices now copy the current video or sound link. They were wired to double tap, whose settings never offered them. Default double tap is unchanged.

* Turning Inbox hide and expansion options off now preserves TikTok's own decisions. Suggested accounts and stories aren't forced into otherwise unavailable layouts, and a list TikTok already expanded stays expanded.

* Repeated taps on Inbox's Clear all control keep one paced run. Failed dismissals and a closing screen release the control so it can be used again.

* Enabling or saving a Feature Gate Lab override keeps populated arrays intact, including arrays loaded from TikTok. An empty default no longer replaces the edited value.

* Native graphics tests serialize font initialization across Android sandboxes. Graphics errors still fail the run, and every tested Android version remains enabled.

* Feed tests now run all five count ranges through real responses, with lower and upper boundaries, missing counts and disabled settings. The content fixtures also fail if the runtime catches an unstubbed getter, so an incomplete fixture can't quietly pass.

- Follow diagnostics now recognizes the same real follow write routes as the CAPTCHA gate, including nearby follows and follower removals. Diagnostic requests stay bounded after the session limit while refusal notices still work.

* The settings screen uses TikTok's theme before painting behind the system bars. Light TikTok on a dark phone no longer leaves the clock and battery on a black strip, and the reverse combination is covered too.

* The device helper starts correctly in Bash and refuses any serial except the S22 test phone. Failed device commands stop the step before it reports success.

* The oversized animated-sticker check now closes its test file on Windows. It still rejects the canvas before allocation and checks the rejection reason.

## 0.27.0 (2026-09-09)

* The settings screens are done being half English. Around eighty strings still went out in English whatever your phone was set to, nearly all of them in the Feature Gate Lab: the gate details page top to bottom, both override switches and the sentences under them, the overflow menu, the filter, the three buttons that reset or force a selection, and nineteen of its messages. The close button on the Lab's search box and the three buttons under the tab picker went with them. All of it is in the four tables now.

* Two of the Lab's lines count things, and they were built from a verb, a number and a couple of tails. No table row can hold half a sentence, and the plural rule of the language never got a say. Those are whole sentences now with the counts as placeholders, and so are the export count and the four-part import summary.

* The list under the diagnostics picker reads correctly in every language. It was "Includes downloads, errors events.", lower-cased from labels that every table capitalises, which suits English and nothing else. The list comes after a colon now: "Includes these events: Downloads, Errors".

* An undo of a settings backup says it's an undo. If the copy it put back held Feature Gate Lab rules for another TikTok build, the line on screen said your settings had been restored, and that's a different thing.

* Thirteen wrong translations. Brazilian Portuguese said to restart TikTok "para valer", which means for real rather than to take effect. German and Indonesian pointed at two settings by names those settings don't carry, and both called a video's caption a subtitle. Spanish and Portuguese write maximum as "máx.", and ten rows had lost the period that belongs to the word.

* The README named two patches by names they lost in 0.26.0, so looking either one up in Morphe Manager found nothing. The 0.26.0 notes also said seven patches were renamed when ten were, and gave the wrong number for how long a caption problem goes unreported.

* Four of the checks meant to catch all of this were letting it through themselves, and each now has its own failing case pinned in front of it, because a check that has only ever seen code it passes proves nothing. A patch-time helper that overrides the result of a call reads what an instruction writes more carefully too: five comparison opcodes were in no write set at all, and a conversion away from a long was read as though its answer took two registers.

## 0.26.0 (2026-09-09)

* The last English left on the settings screen is translated. The diagnostics picker with its eight kinds of event, the line you see after saving any of the fifty settings that ask for a restart, the message when diagnostic data is cleared, and every line of the hook status report. These live in code shared with other bundles that carry no translations at all, so they could not simply be wrapped: the shared classes ask this bundle for the words and fall back to English for anyone else. The report line was five pieces glued together, which no translation can hold, and is one sentence now.

* Thirteen more strings in the Feature Gate Lab are translated: the screen titles, the two warnings about what an override can and cannot do, the empty and loading states, and the reset action. They had been going out in English because the check that catches this looks at what is handed to a view, and these went through a helper first. The check follows the helper now.

* One word for one thing across the settings screens. The strip down the right of a video is the right column everywhere, not the action bar in one row and the action rail in the next. LIVE is written the way TikTok writes it. Eleven ways of saying "restart TikTok" are now one. A save that fails says so in one shape and tells you what to try. Favourite is spelled the American way, like the App behavior section it sits under. And nine patches were renamed to match the row they add, so a patch you applied by name can be found by that name in the settings: Skip content warnings, Fit the video to the screen, Keep the screen's refresh rate, Use non-personalized search, Show LIVE search, Show the progress bar, Show the progress bar thumbnail, Disable the long press quick share and Disable the long press repost. If you had any of those selected, select them again after updating.

* A pass over the words on the settings screens. Photo posts are called photo posts rather than "image video", which is TikTok's own internal name. The region rows talk about the region TikTok reports instead of "region getters". The auto-advance limit lost sixty words about prefetches and component lifecycles and says what it counts. A restore, a reset and an undo say which of the three just happened, rather than all three saying "Settings saved". Both "picker is not available" messages say what to do instead. The ghost mode summary got the word it was missing, and the file name help points at a row that exists. In the patch list, "Hide BdTuring CAPTCHA popups" is now "Hide the risk control CAPTCHA"; BdTuring is ByteDance's name for the service and it stays in the description.

* The dialogs that were still in English are not. The SIM country picker, the tab pickers and their help text, the Feature Gate Lab from its search box to its selection count, the gate editor with its value hints and status lines, and what a screen reader is told about all of them. Thirty-five more strings in German, Indonesian, Spanish and Brazilian Portuguese. Two of them had a German row all along and never asked for it, which is the sort of thing a check catches and reading does not, so there is a check now: text handed straight to a view in English fails the build.

* The daily budget costs less to keep. Working out which day it is used to read the device's timezone on every frame the player reported, and on Android that hands back a copy each time. It now listens for the timezone changing instead, which is the one moment the answer can be wrong, so the check on the hot path is two comparisons.

* One line of the settings screen was still in English on a translated phone: the summary under "Start today over" after you have tapped it. It is translated in all four languages now, and the check that catches this kind of gap taps the row itself rather than waiting for it to be found by accident.

* Hook status catches the other way the caption settings can go quiet. It already reported an id this build does not have. Now it also reports an id the build does have that has been handed to some other view, which is the likelier of the two, and which used to leave the caption size and background doing nothing while the row said everything was fine. It takes twenty renders of a container holding a caption view before that is called a problem, and forty for a container that holds neither, because TikTok's own render method returns early often and an empty container is not a broken build.

* The little "2 on" numbers on the settings menu mean what they say now. Each one counts the settings on the page behind it that you have moved off their default, worked out from the page itself rather than from a list kept beside it. That list had drifted: it named 17 of the 34 settings on the Feed filter page, so nine switches there changed nothing, and it counted switches that ship on as things you had turned on, which is why a fresh install showed "Comments and translation, 3 on" before you had touched anything. The numbers also move while you use the screen. Turn three filters on, press back, and the count is three; it used to be whatever it was when you opened settings.

* The settings screen speaks Spanish and Brazilian Portuguese. Both tables carry all 687 strings, so a phone set to either language gets the whole screen rather than a half translated one. Neither has been read by a native speaker yet. They were written here, against the English, and corrections are welcome: each language is one file under extensions/tiktok/src/main/l10n and a pull request against it needs nothing else.

* Two switches for a feed that plays when nobody is watching it. "Quieten the feed while comments are open" takes the sound the moment a comment sheet opens and hands it back when it closes. "Do not start the feed on returning" holds the feed after you come back to the app until you tap once, and leaves the tab bar alone so messages, a profile and search are still one tap away. Both are off by default. Neither presses a pause button, because there isn't one to press: they ask for the audio focus, the way the session hold does, which is how one app tells another to stop.

* You can see the hold coming now. "Fade the feed out before the hold" is off by default; switched on, the feed dims over the last three quarters of a minute of a time budget, most of it in the final half minute, and the last shade of the fade is the shade of the hold itself, so there is no jump. It needs a budget in minutes and a hold to lead into, since a budget counted in videos has no time left to follow and a fade towards nothing is just a dark feed, and it does nothing at all if you have turned system animations off. It takes no touches and a screen reader is told nothing until the hold speaks for itself.

* A value a settings dialog will not take no longer closes the dialog first. Type a creator pattern that will not compile, a smallest larger than the largest, or a folder Android will not write to, and the dialog stays where it is with what you typed still in it and the reason under the box. Change a budget row while today's budget is locked and the dialog stays put too, though that refusal is about the day rather than the field, so it is still said in a line at the bottom of the screen. Before this all three closed, threw the text away and put the reason in a toast over whatever was behind, so you reopened the row and typed it again.

* Small things across the settings screens. "Add" in the hidden creators dialog is flat like the Cancel and Save below it, instead of the one raised button in the app, and an empty list says so where the list is rather than under a blank band of it. "Reset to loaded" in the tab pickers ticks every row and leaves the dialog open, so pressing it to see what it does no longer saves and closes. Rows say milliseconds, pixels and points instead of ms, dp and sp. Clearing diagnostics says that it also resets the hook status. The SIM switch stops promising operator fields on a build that does not have them. And blocking a creator twice says the same thing from both places it can be said.

* At a large text size the settings page shows the page, not just its title. A page called "Kommentare und Ubersetzung" took five lines and most of the screen at 2x text, and the first row of the page was below the fold. The title still grows with your text size, just not without limit. The Feature Gate Lab's search box grows with it too, instead of clipping the letters in a fixed height.

* The hand-drawn arrows and chevrons turn round in a right-to-left language, and they are the width they were meant to be. Their containers already mirrored, so an Arabic or Hebrew reader had a back arrow pointing left at the right edge of the screen and chevrons pointing back into the text. Separately, every one of these strokes was given its width in raw pixels, so on a dense screen the settings menu tiles drew lines under a pixel wide next to very large type, and the shadow behind a caption was almost nothing on a 3x screen, which matters most when you pick the transparent caption background. The chevron on the Lab's value picker turns round too, which it could not do before: it hangs off the text rather than sitting in its own box, and a row built by a list adapter has no direction to take one from yet.

* Three small things on screens you actually touch. The four buttons on the feed are 48dp instead of 44, and "Not interested" is the same round shape over the same shade as the three it sits with, rather than a rounded rectangle over a darker one. Pressing a settings row at the corner of a card no longer paints outside the card. And every flat action in a dialog, the Saves and Cancels and Applies, is at least 48dp and is announced by a screen reader as a button rather than as a label.

* A quiet reminder partway through, if you want one. "Remind me every" takes a number of minutes and shows a short line at the bottom of the feed after that much watching, then again after the same again. It goes on its own, it takes no focus, and a screen reader hears it once. It counts feed time only, so messages and profiles do not bring one on, and nothing appears while the feed is on hold. Three wordings take turns, because one sentence stops being read. Zero, the default, means none.

* The Feature Gate Lab acts on a whole selection at once. Press and hold a gate to start one, tap to add and remove, then Enable, Disable or Reset the lot. It goes through as a single change, so one Undo puts all of it back rather than the last one you touched. Gates that do not take a true or false value are skipped and it says how many.

* Both daily budgets show how much of today has gone. Set one to 200 videos and the row reads "Today: 57 videos" under it, so the day's progress is visible where you set the budget instead of only in the one notice when it runs out. With a budget of zero there is nothing to measure against and the line is absent.

* The hold panel offers a way to your messages. It always said messages, profiles and search still work, and then covered the screen, so you had to already know the Inbox tab was underneath. There is an "Open messages" action under the countdown now. It opens the Inbox tab exactly as tapping it would, the hold stays up behind it, and it comes back when you return to the feed. If you have hidden Inbox in Feed navigation, the action is not there.

* The install steps say what Android now asks for. From 2026-09-30, phones in Brazil, Indonesia, Singapore and Thailand put an app from an unverified developer through an extra flow: Developer options, confirmation of the device lock, a restart, then a 24 hour wait before it installs, and after that a window of 7 days or indefinitely. Every release is an update, so it comes round again once that window closes. The README says so, and says that installing over adb skips it. It also now says that 640 MB is the manager's default rather than a measured minimum.

* You can cap how many times a day the hold lets you through. Until now the way out on the countdown was either always there or, with Lock today's budget on, gone entirely. The new row sits between the two: pick a number and the control counts down, saying how many are left, then goes once they are spent. Zero, the default, changes nothing. The count survives the app being killed and comes back when the day starts over.

* A saved gate override no longer costs 19 MB on every launch. The check that refuses an override whose type TikTok's own catalogue disagrees with used to load the whole catalogue, 17,393 entries, and hold it for as long as the app was running, on launches where the Feature Gate Lab was never opened. It now loads the one thing it reads, a type per AB key, which measured 1.9 MB against 19.1 MB. The Lab screen still loads the whole thing when you open it, and refuses exactly what it refused before.

* With a SIM preset on, TikTok no longer reports your SIM changing. Swap a SIM, or change which one carries data, and a startup check sends one event: how many SIMs the phone has, how long since it last looked, and the country and carrier before and after. The preset covers the country and the carrier, so both halves read as the preset, but the SIM count comes from a part of Android the preset never reached, and the event still says the hardware changed. With a preset on the check is skipped. Nothing else in the app reads what it stored.

* "Resume videos after scrolling" works on the Following and Friends tabs. TikTok kept a position for every feed it played, then handed one back only for a short list of places it built into the app, and the Following tab is not on that list. The switch looked broken there while the position was being stored all along. With the switch on, any feed that has a stored position now uses it.

* Every number row says what it accepts, and says so when it moves what you typed. Twelve of the fourteen stated their range nowhere: type 5000 into "Daily time budget" and it came back "Current: 600" with no explanation. Each row now carries its range under its own wording, and a number outside it is reported rather than quietly pulled in. The seven rows whose text says zero turns them off read "Current: Off" at zero instead of "Current: 0 videos", which was a limit of none rather than no limit.

* Each download destination dialog is headed by the row you tapped. Video, photo and sticker all opened a window titled "Download path", so nothing on the screen said which of the three you were editing, and the sentence under it stayed English on a German or Indonesian phone. Both follow the language now.

* TalkBack says which box is which in the five Min and Max dialogs. It read them as "edit box" and "edit box, Unlimited": the headings above them are separate views and the only hint on either field was a value rather than a name, and that one vanished the moment anything was typed. Both fields are labelled now. The sentence above them still says an empty maximum means no upper bound.

* A gate override whose type the catalogue disagrees with is refused on a fresh launch too. The check that catches a rule which would hand TikTok a word where its own code expects a number could only work while the catalogue happened to be loaded, and nothing loaded it until the Feature Gate Lab screen was opened, which is exactly the launch where the check was needed. Reaching that path now asks for the catalogue in the background, so one read goes through unchecked instead of all of them.

* Restoring an older backup keeps the settings it predates. Anything added since the file was written went back to its default, so a backup taken before the download destinations were split put your video, photo and sticker folders back to DCIM/TikTok without a word, and so did every other setting added since. A backup is a set of values to apply now, not a picture of the whole app: what the file does not mention stays as you have it, the restore and the undo both say how many that was, and a file carrying only the old single download folder fills in all three.

* The cached-feed filter stops working in a register TikTok owns. It read its answer into the register holding the cache payload and then jumped back into TikTok's own code at two points, both of which are also reached with a reference in that register. On this build each of those points overwrites it before anything looks at it, so the app was never wrong; on a build that reads it first the feed would have refused to load. The filter has a register of its own now.

* The comment image watermark keeps the position TikTok gave it. The patch read its own on/off answer into the register holding the watermark's x coordinate, then wrote a zero back before drawing. That is the same picture on this build, because TikTok moves the canvas first and draws at nothing but zero, and it would have pinned the watermark to the left edge on a build that draws anywhere else. The switch has a register of its own now.

* The patched app carries less code. Twenty classes inherited from ReVanced were compiled into the shared payload of every build and nothing in this project called any of them, including a color picker, a second settings backup, a network helper and an environment nag screen that had been switched off and left in place. They are gone. Nothing reachable changes.

* The two caption settings say so when a TikTok build drops them. Caption text size and the strip behind the captions found their views by a number baked into this project, which the next TikTok build is free to reassign, and both settings would then have done nothing while the Hook status row reported everything fine. They are looked up by name now, the way every other lookup here is, and a build without those names is reported under "captions" in the diagnostics.

* The five Min and Max rows show the range they actually hold. Reset settings, restore a backup or undo one, and rows like "Views" kept the old "20K to 1.5M" under them while the stored value had already gone back to its default. Only closing the dialog rebuilt that line; every path that changes the value rebuilds it now.

* "Hide comments with pictures" leaves your own alone. Your stickers and images disappeared from threads you were in, which reads as the comment having failed to send rather than as a filter doing its job. Other people's are hidden as before, and if nobody is signed in nothing changes.

* Undo is greyed out until there is something to undo. On a clean install it was offered like any other row, and tapping it said the settings could not be restored, which reads as a breakage rather than as an empty drawer. If it does somehow run with nothing saved it now says there is nothing to undo yet.

* Saving a gate configuration writes what is on the screen. Rotating the phone, changing the text size or theme, or coming back from a deeper screen rebuilt the page and kept the fields from every earlier version of it, so a save could collect text from boxes nobody could see. Those pages are also released now instead of being held for the life of the app, and a custom value box left open when the screen goes is closed with it.

## 0.25.0 (2026-09-08)

* A Lab change that fails halfway no longer erases what the Lab had recorded. Saving a set of overrides, resetting them, undoing that, or restoring a backup puts the previous configuration back when the write fails, and the record of which gates had actually fired was thrown away with it, so the detail screen reported "not triggered" for gates that were.

* An override profile written somewhere other than the Lab's own export applies on a Turkish phone. Turkish capitalises i to a dotted letter, so a lowercase `int` in the file folded to a different word than the catalogue's, and the rule was refused as a type mismatch on that phone and no other.

* A gate override that replaces a whole configuration object is worked out once instead of on every read. TikTok reads some of these constantly, and each read used to parse the stored value from scratch, up to 64 KB of it, on the thread asking for the gate. A value nested past a sane limit is also refused now with a message saying so; the check that appeared to do that sat on a method that never recurses and so could never fire.

* Comment translation stops doing work for batches it never asked for. The hook that notices a finished translation sits on TikTok's own completion path, so it ran for every batch in the app, and with the feature switched off it still walked the fields of two objects and took a lock on that thread each time. A request already in flight when the switch goes off is still finished properly.

* Turning clear display on by itself no longer looks to TikTok like you asked for it. The patch stops the events its own code sends when clear display changes, and two of the three it was written to stop had not matched anything in this version of TikTok for some time. Both were looked up in a way that gave no sign when they were missing, so the patch reported success and sent the events anyway. The patched app now silences four of those events where it silenced one.

* A video reached through TikTok's translated-video path now gets the same treatment as any other. The hook that prepares a video for download was installed on the last way out of the method and there are two, so one route returned a video this project had never seen. That is fixed, along with three more hooks that covered a single exit and would have missed the others on a build that returns from more than one place.

* The download path redirect checks the four instructions it replaces before replacing them. It looked at two of them and deleted four, which on a build that lays that run out differently would have removed unrelated code with nothing said. Writing the check found that the five places it runs are not identical after all: four append "/Camera/" and the fifth appends "/Camera".

* A patch that cannot apply to a future TikTok build now says what it was looking for. Fourteen checks threw an error naming nothing, so a failure read as a crash in the patcher rather than as the patch reporting a missing anchor. The telemetry description also promised to stop location uploads that the supported build does not contain.

* Five more injections and eight register checks stop assuming the shape of TikTok's own methods. Every register is now read off the instruction it belongs to, and the settings row this project adds took three working registers on trust: one of them was still in use, which a check now catches. A parameter that holds a long or a double occupies two registers and was being counted as one, so eight checks meant to prove a spare register existed could pass on a method that had none.

* Follow diagnostics keeps working when TikTok's own code moves. Eight registers were written out by hand, and every anchor it looks for was optional, so a build that renamed one would have left the patch reporting that it applied while logging the wrong thing, or crashing the app's own network layer. Each register is now read off the instruction it belongs to, and a missing anchor fails the build with a message naming it.

* A gate override whose type the catalogue disagrees with is no longer handed to TikTok. It only mattered on one path, where TikTok holds no cached value and so there is no type to check the rule against: a text rule on a key the app reads as a number came back as text, and the app crashed in its own code rather than in anything this project added. The Lab's detail screen says why the rule was refused. The catalogue has to have been loaded for this to bite, which today means the Lab screen has been opened.

## 0.24.0 (2026-09-08)

* The Save button on a sticker is in your language. It was the one piece of text this project adds to TikTok that never went through the translations, and it could not simply be translated: the button's own English label was also how the code recognised its own button, so a German one would have been added a second time on every sheet. It carries a marker now, and reads "Medien speichern" or "Simpan media" where it should.

* The daily hold works with a screen reader. It stopped touches and nothing else: it went up without being announced, "Open the feed anyway" was read as ordinary text rather than as a button, and the feed's like, comment and share controls behind it could still be swiped to, which is the one thing the hold is there to stop. The hold announces itself now, its way out is offered as a button, and what it covers is out of the way until it lifts.

* The Feature Gate Lab no longer freezes while it saves. Turning overrides on, and saving or resetting one gate, wrote to storage on the thread that draws the screen, so the tap sat there until the write finished. If a settings restore was running at the same time it sat there until the restore finished. All three now work the way the Lab's other changes already did: the write happens in the background and the screen catches up.

* Comment translation stops rebuilding one of TikTok's own services over and over. Working out which language you read in, and which languages you asked not to be translated, meant building that service and looking for the right method. The answer was only remembered when the method was found, so on a build that does not have one it was built again for every comment in view, while holding the lock that TikTok needs to hand a finished batch back. It is asked once now, and once is enough either way.

* Every row in a settings dialog list keeps Hushfeed's check mark. The list was restyled once, just after it opened, so any row you had to scroll to reach had never been touched and came up with Android's own check mark on the wrong side and TikTok's text color. The eight row "Included diagnostics" picker scrolls on a small screen and on every screen at large text. Rows are styled as they appear now.

* The patches keep applying when TikTok's own methods grow. A call Hushfeed injects can only name sixteen registers, and a method with enough locals pushes its arguments past that, which fails the patch with an error that says nothing about registers. Thirty five injections were written the short way and would have broken on the first TikTok build that crossed the line. They now use the long form only where they have to, so nothing about the current build changes, and a frame that cannot be patched at all says so in those words.

* A sticker save no longer keeps the screen it started from in memory. The job held the Save button, and a button holds the whole screen behind it, so closing the sheet freed nothing until the save finished, up to two minutes later with eight more saves queued behind it. It holds the button weakly now and simply skips handing it back if the sheet has gone.

* Saving an animated sticker as a video can be given up on. If the phone's encoder stopped producing frames partway, the save sat in a loop that neither the cancel nor the two minute limit could reach, and one of the three background workers that save media was gone until the app was killed. Two more stickers after that and saving stopped working entirely, with nothing said. The loop now gives up the moment the job is cancelled or runs out of time.

* Translators can work in Weblate. The settings strings were one tab separated file per language, a shape Weblate cannot host, so translating meant editing a file in a pull request. A language table can now be either that file or the comma form Weblate exports, and the generator writes the list of source strings a Weblate project translates from. A table that has been through a spreadsheet is read as well, byte order mark and all. Nothing changes for anyone already editing the tab form.

## 0.23.0 (2026-09-08)

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

* The settings screen no longer draws its first row under the status bar and its last under the navigation bar. TikTok targets a recent enough Android that every window is edge to edge whether the app asks for it or not, and the two calls that used to color the bars stopped doing anything at the same time. The screen now measures the bars and the display cutout and moves its content clear of both, with its own background reaching behind them.

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

* Clear all in the inbox is readable in both themes. It was picking its color from a flag the settings screen sets, which away from that screen answers for the system theme rather than TikTok's own, so it could draw dark red on a dark sheet.

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

* Incorrect settings text was fixed. The thumbs down block claimed it needed a restart, which it never did. The two region switches claimed one and never asked for it, and now they do, because TikTok reads the country at startup. A dozen summaries that talked about secure window flags, native seekbars, candidates and pages now say what actually happens.

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

* Review pass over the last three switches. The refresh rate one was overwriting the register TikTok stores its own record of the rate in, so it now replaces the store outright and leaves the value alone; it also only declines a request that is slower than the screen, because not every one of the six places that asks is asking for less. Duet and Stitch was skipping the commercial refusal as well as the creator's, which is the one most likely to get a post taken down, so only the creator's is answered now and its description is accurate again. A muted save no longer downloads a sound it will not use, no longer fails outright when that download fails, no longer quietly saves with sound when the stream is missing, and on Automatic keeps the file TikTok would have saved rather than jumping to the largest one. Saving a story asks the other app too.

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

* Review pass over the sticker formats and the profile picture save. The profile hook was reading the user through the very method it had been added to, so every profile open burned a stack and swallowed the overflow. It reads the field now. The long press is only taken when the feature is on, so TikTok's own is left alone otherwise, and a picture is refused rather than saved when the handle on screen belongs to somebody else. A sticker with more colors than a palette can hold no longer counts every one of them: 64 frames of noise went from running out of memory to under a second.

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

* **build:** move to Morphe patcher 1.12.0 and patches plugin 1.3.4, which is what Morphe Manager 1.29 ships. Typed patch options, sliders and color pickers are available to patches from here on
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

## [0.7.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.1...v0.7.0) (2026-08-23)

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

## [0.7.0-dev.8](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.7...v0.7.0-dev.8) (2026-08-22)

### Bug Fixes

* **tiktok:** preserve swipe-lock playback speed ([dfbe2a5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/dfbe2a55aaced183f97a457b494893c31cebf596))

## [0.7.0-dev.7](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.6...v0.7.0-dev.7) (2026-08-20)

### Bug Fixes

* **tiktok:** consume download filename mappings ([864fc15](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/864fc1526d51f819daea8e0730716d62a4c6b662))
* **tiktok:** harden bytecode hook resolution ([95e0a3f](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/95e0a3f8d6c45e97eb44c3066bd201e2d9ab1843))

### Features

* **tiktok:** add repost and cached feed controls ([446ee90](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/446ee90123b118642fe3c50f5da9221d984667c2))

## [0.7.0-dev.6](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.5...v0.7.0-dev.6) (2026-08-20)

### Features

* **tiktok:** expand offline video limits ([35eff0e](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/35eff0e4f84ea69925bbddc71eb04c6d0bf5e66d))

## [0.7.0-dev.5](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.4...v0.7.0-dev.5) (2026-08-16)

### Bug Fixes

* **build:** complete feed model stubs ([053ce6c](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/053ce6cd064c16c1a06e87e10b76896da7c00a83))
* **tiktok:** filter cached feed insertions ([d30a6dd](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/d30a6dd37f9eaa1c1faecdf2b30b047707170860))

## [0.7.0-dev.4](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.3...v0.7.0-dev.4) (2026-08-13)

### Bug Fixes

* **clear-display:** preserve state across feed transitions ([a433fe0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a433fe03b98ecee8814a83f94aaef760222e538f))

## [0.7.0-dev.3](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.2...v0.7.0-dev.3) (2026-08-11)

### Bug Fixes

* **playback:** persist explicit speed selections ([0f785fc](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/0f785fc8685c30793bf7ffea028d1223613f52c9))
* **settings:** make custom dialogs fit device screens ([7592339](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/75923397520ff45fde1b5f618c1de2010de422e0))
* **tiktok:** expand startup and runtime hook coverage ([bac0ba8](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/bac0ba8d2697a60d2c66d723fba75e211ca48a49))

## [0.7.0-dev.2](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.7.0-dev.1...v0.7.0-dev.2) (2026-08-11)

### Bug Fixes

* **tiktok:** prevent settings crash and expand crash reports ([08186e7](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/08186e77ba89d92debfcd319fe8fcc745e2a363b))

## [0.7.0-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.2-dev.1...v0.7.0-dev.1) (2026-08-10)

### Features

* **downloads:** support separate media destinations ([f4580c9](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f4580c94b3b2c0d2d79c2bb0bfa6a544abeaedb3))

## [0.6.2-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.1...v0.6.2-dev.1) (2026-08-10)

### Bug Fixes

* **tiktok:** cover direct Turing CAPTCHA dialogs ([27b2639](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/27b263920ffadaf2e27777c703c225f2e4f3ce40))

## [0.6.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.6.0...v0.6.1) (2026-08-09)

### Bug Fixes

* **morphe:** support patching on older Android versions ([01463a0](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/01463a04e2a64a2a1400391778576779cfb2b211)), closes [#85](https://github.com/icysymmetra/tiktok-patches-for-morphe/issues/85)

## [0.6.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.5.0...v0.6.0) (2026-08-09)

### Features

* **tiktok:** restore download templates and sticker formats ([91d158c](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/91d158ca3da1126a2d2ca9f4c2330e81d3ea96f9))

## [0.5.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.4.1...v0.5.0) (2026-08-09)

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

## [0.4.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.4.0...v0.4.1) (2026-07-28)

### Bug Fixes

* **tiktok:** prevent seekbar recursion on stories ([9d2c0bc](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/9d2c0bc823ce661b385fff12945ac5c2ace0223f))

## [0.4.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.1...v0.4.0) (2026-07-26)

### Features

* **tiktok:** add feature gate lab and UI suppressors ([72bf32a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/72bf32aa6991257ace0bab91d3a8767617cbd802))
* **tiktok:** add translation language exclusions ([8f60185](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/8f60185434a3cbb13653438a6561e2d7fc45f4dd))
* **tiktok:** enable Feature Gate Lab by default ([a164b70](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a164b70cf046a738d3b0a817a57b1fedf515ef2e))
* **tiktok:** enable hold-and-slide 2x lock ([a2fd626](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a2fd626ecb1fb116461b760445128bfdd8980f29))
* **tiktok:** improve feature gate lab search ([ffc19de](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ffc19dece888a6ef814d20401d0bc4b9ba93858d))
* **tiktok:** port external browser patch from lyyako ([7a517c2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7a517c218a35508ac4f284450ff6d6c4d3ea05d8))
* **tiktok:** port publish date patch from lyyako ([1b6c386](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/1b6c386611239c39242bc813fca9614c8de3d3d1))
* **tiktok:** redesign support row ([cc39469](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/cc39469c98eabc0ccdea40fc423e94639c34703f))

## [0.4.0-dev.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.1...v0.4.0-dev.1) (2026-07-26)

### Features

* **tiktok:** add feature gate lab and UI suppressors ([72bf32a](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/72bf32aa6991257ace0bab91d3a8767617cbd802))
* **tiktok:** add translation language exclusions ([8f60185](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/8f60185434a3cbb13653438a6561e2d7fc45f4dd))
* **tiktok:** enable Feature Gate Lab by default ([a164b70](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a164b70cf046a738d3b0a817a57b1fedf515ef2e))
* **tiktok:** enable hold-and-slide 2x lock ([a2fd626](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/a2fd626ecb1fb116461b760445128bfdd8980f29))
* **tiktok:** improve feature gate lab search ([ffc19de](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/ffc19dece888a6ef814d20401d0bc4b9ba93858d))
* **tiktok:** port external browser patch from lyyako ([7a517c2](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7a517c218a35508ac4f284450ff6d6c4d3ea05d8))
* **tiktok:** port publish date patch from lyyako ([1b6c386](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/1b6c386611239c39242bc813fca9614c8de3d3d1))
* **tiktok:** redesign support row ([cc39469](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/cc39469c98eabc0ccdea40fc423e94639c34703f))

## [0.3.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.3.0...v0.3.1) (2026-06-17)

### Bug Fixes

* avoid concurrent feed list iteration crash ([275e8d5](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/275e8d5f3ad273b24a5bd592b0da86bfe7dc3ee4))

## [0.3.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.2.0...v0.3.0) (2026-06-17)

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

## [0.2.0](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.5...v0.2.0) (2026-06-06)

### Bug Fixes

* improve settings menu dark mode layout ([4da9d87](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/4da9d8785f0e6ad97f8c9ad7e4e3cf5655f2e0f4))
* reduce repeated feed filter scans ([370b806](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/370b806cc1c64574d767b397d612e79a58ed6896))
* restore downloads fallback for restricted videos ([277f400](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/277f4003643b35045b62f9f95f63ed2d8bb889bf))

### Features

* add SIM spoof country presets ([7f8fb32](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7f8fb32dd7bc1f0b34078482216c087914b8ff4c))

### Performance Improvements

* reduce feed filter scanning overhead ([007d1be](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/007d1be0a93393a7ca1935cea8740cb167b5e6f9))

## [0.1.5](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.4...v0.1.5) (2026-06-01)

### Bug Fixes

* limit TikTok compatibility to global package ([49806ad](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/49806ad5a1c75bc2d7f052abf227b7d055879935))
* sanitize TikTok sharing links before shortening ([53a3558](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/53a3558f7e1921ccd2636fe2d322a4b7c1ca3d04))

## [0.1.4](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.3...v0.1.4) (2026-05-30)

### Bug Fixes

* improve settings readability and debug logging ([f4f4bc4](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/f4f4bc4dc9104182c1a419878ddf837057555340))

## [0.1.3](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.2...v0.1.3) (2026-05-29)

### Bug Fixes

* support global settings top row insertion ([c38f1b6](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/c38f1b63d32b3d7218de1f5c46243f728c6c5868))

## [0.1.2](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.1...v0.1.2) (2026-05-29)

### Bug Fixes

* fallback open debug settings insertion ([e201d53](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e201d533d19504f4e32001d6985cad7ee9804b15))

## [0.1.1](https://github.com/icysymmetra/tiktok-patches-for-morphe/compare/v0.1.0...v0.1.1) (2026-05-29)

### Bug Fixes

* add release changelog script ([74fa14e](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/74fa14ebe975509816a9344031dd176a76595407))
* distinguish global and jp tiktok packages ([7e1f7df](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/7e1f7dfd23d79346529715854a9b1840af210d47))
* soften support preference copy ([5cbfd26](https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/5cbfd26030b35d5b2996ead5073b2ffd7df166a5))
