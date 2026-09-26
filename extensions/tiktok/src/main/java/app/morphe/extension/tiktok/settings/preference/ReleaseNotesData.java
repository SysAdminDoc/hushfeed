/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

/** Published CHANGELOG entries carried inside the patched app. Run tools/gen-release-notes.py after release edits. */
public final class ReleaseNotesData {
    private ReleaseNotesData() {}
    public static final String TEXT =
            "## 0.60.0 (2026-09-25)\n" +
            "\n" +
            "A small release with three fixes. Comments that TikTok dropped without a word now go out, the caption no longer shows over the video when Caption above comments is on, and Allow Duet and Stitch covers creators who limit duets on their whole account.\n" +
            "\n" +
            "* **TikTok:** Comment send fix, a new patch that's on by default, covers a way TikTok drops a comment without a word. TikTok checks a send against the page opened most recently, and when that page has already lost its screen, the check stops the comment and shows nothing, so the text just stays in the box. The check now gets the comment panel's own screen in that case. The diagnostic export names the page that had none, so a report from a phone where comments still don't post says whether this was the reason.\n" +
            "* **TikTok:** With Caption above comments on, the caption over the video goes away, since the whole caption now sits at the top of the comments. It used to show in both places. Hide the caption still hides it on its own.\n" +
            "* **TikTok:** Allow Duet and Stitch now answers the creator's account-wide choice as well as the video's own. TikTok checks both, and on the S22 a video that allowed anyone still had no Duet entry because its creator's account allowed only people they follow back. With the switch on, Duet and Stitch both show on those videos now. Whether TikTok's servers accept the upload is still up to them.\n";
}
