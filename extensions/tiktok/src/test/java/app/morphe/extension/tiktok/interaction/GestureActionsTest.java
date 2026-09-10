package app.morphe.extension.tiktok.interaction;

import static org.junit.Assert.*;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.settings.preference.ChoicePreference;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GestureActionsTest {
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
    @After public void tearDown() {
        SettingsStatus.doubleTapEnabled = false;
        SettingsStatus.longPressEnabled = false;
        SettingsStatus.videoOverlaysEnabled = false;
        SettingsStatus.sensitiveWarningsEnabled = false;
    }
    public static final class TestActivity extends PreferenceActivity {
        @Override public void onCreate(android.os.Bundle state) {
            setTheme(android.R.style.Theme_Material_NoActionBar);
            super.onCreate(state);
        }
    }
    public static final class Params {
        public final Clip aweme;
        Params(String id) { aweme = new Clip(id); }
        Params(Clip clip) { aweme = clip; }
    }
    public static final class Clip {
        public final String aid;
        public final String shareUrl;
        public final SoundStub music;
        Clip(String id) { this(id, null, null); }
        Clip(String id, String shareUrl, SoundStub music) {
            aid = id;
            this.shareUrl = shareUrl;
            this.music = music;
        }
    }

    /** A press at {@code x} across the screen, which is what decides the seek zone. */
    private static MotionEvent press(float x) {
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, 10f, 0);
    }

    private static MotionEvent middleOf(android.app.Activity activity) {
        return press(activity.getResources().getDisplayMetrics().widthPixels / 2f);
    }

    /** Native seek(float) takes a percentage; 0pTR converts it to integer engine milliseconds. */
    public static final class FakePlayer {
        public final int durationMs = 30_000;
        public float sought = Float.NaN;
        public void seek(float percentage) { sought = (int) (percentage * 0.01d * durationMs); }
    }

    /** Stands in for PlayerController, whose getPlayerManager() kept its name. */
    public static final class FakeController {
        public final FakePlayer player = new FakePlayer();
        public FakePlayer getPlayerManager() { return player; }
    }

    /** A controller whose shape changed out from under us. */
    public static final class ShapelessController {
    }

    @Test public void commentsUseMatchingAttachedNativeControlAndFollowRebinding() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            FrameLayout root = new FrameLayout(activity);
            View first = new View(activity), second = new View(activity);
            root.addView(first); root.addView(second);
            activity.setContentView(root);
            int[] clicks = {0, 0};
            first.setOnClickListener(view -> clicks[0]++);
            second.setOnClickListener(view -> clicks[1]++);
            Object firstOwner = new Object(), secondOwner = new Object();
            GestureActions.registerCommentView(firstOwner, first);
            GestureActions.bindCommentView(firstOwner, new Params("one"));
            GestureActions.bindCommentView(secondOwner, new Params("two"));
            GestureActions.registerCommentView(secondOwner, second);
            assertTrue(GestureActions.openComments("two"));
            assertArrayEquals(new int[]{0, 1}, clicks);
            GestureActions.bindCommentView(secondOwner, new Params("three"));
            assertFalse(GestureActions.openComments("two"));
            assertTrue(GestureActions.openComments("three"));
            root.removeView(second);
            assertFalse(GestureActions.openComments("three"));
            Object newOwner = new Object();
            GestureActions.registerCommentView(newOwner, first);
            GestureActions.bindCommentView(newOwner, new Params("four"));
            assertFalse(GestureActions.openComments("one"));
            assertTrue(GestureActions.openComments("four"));
            assertFalse(GestureActions.openComments(null));
            Settings.DOUBLE_TAP_ACTION.save("nothing");
            assertTrue(GestureActions.onDoubleTap());
            Settings.DOUBLE_TAP_ACTION.save("default");
            assertFalse(GestureActions.onDoubleTap());
        }
    }

    @Test public void standalonePatchHasThreeReachableChoices() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.doubleTapEnabled = true;
            Utils.setIsDarkModeEnabled(true);
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            ChoicePreference choice = (ChoicePreference) screen.findPreference("double_tap_action");
            assertNotNull(choice);
            assertArrayEquals(new String[]{"default", "nothing", "comments"}, choice.getEntryValues());
            activity.setPreferenceScreen(screen);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            app.morphe.extension.tiktok.UiCapture.save(activity.getWindow().getDecorView(), "interface-settings.png");
        }
    }

    @Test public void longPressFollowsItsOwnSettingAndOpensTheCurrentVideosComments() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            FrameLayout root = new FrameLayout(activity);
            View comments = new View(activity);
            root.addView(comments);
            activity.setContentView(root);
            int[] clicks = {0};
            comments.setOnClickListener(view -> clicks[0]++);
            Object owner = new Object();
            GestureActions.registerCommentView(owner, comments);
            GestureActions.bindCommentView(owner, new Params("one"));
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setCurrentVideoParams(new Params("one"));
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setPlayingAweme("one");

            Settings.LONG_PRESS_ACTION.save("default");
            assertFalse(GestureActions.onLongPress(middleOf(activity)));
            assertEquals(0, clicks[0]);

            Settings.LONG_PRESS_ACTION.save("nothing");
            assertTrue(GestureActions.onLongPress(middleOf(activity)));
            assertEquals(0, clicks[0]);

            // Remapped to comments: the control bound to the video on screen is pressed.
            Settings.LONG_PRESS_ACTION.save("comments");
            assertTrue(GestureActions.onLongPress(middleOf(activity)));
            assertEquals(1, clicks[0]);

            // The control for a different video does not count, and the gesture is still
            // swallowed so a stale 2x hold cannot fire in its place.
            GestureActions.bindCommentView(owner, new Params("two"));
            assertTrue(GestureActions.onLongPress(middleOf(activity)));
            assertEquals(1, clicks[0]);

            // The two gestures do not share a setting.
            Settings.DOUBLE_TAP_ACTION.save("nothing");
            Settings.LONG_PRESS_ACTION.save("default");
            assertFalse(GestureActions.onLongPress(middleOf(activity)));
        } finally {
            Settings.DOUBLE_TAP_ACTION.save("default");
            Settings.LONG_PRESS_ACTION.save("default");
        }
    }

    @Test public void longPressPatchHasSixReachableChoices() throws Exception {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.longPressEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            ChoicePreference choice = (ChoicePreference) screen.findPreference("long_press_action");
            assertNotNull(choice);
            assertArrayEquals(new String[]{"default", "nothing", "comments", "original_sound",
                    "copy_link", "copy_sound_link"}, choice.getEntryValues());
            assertEquals("every value needs a label to pick it by",
                    choice.getEntryValues().length, choice.getEntries().length);
            // The edge seek rides on the same patch, so its two controls come with it.
            assertNotNull(screen.findPreference("edge_seek"));
            assertNotNull(screen.findPreference("edge_seek_seconds"));
        } finally {
            SettingsStatus.longPressEnabled = false;
        }
    }

    @Test public void longPressCopyActionsWriteTheCurrentLinksAndLeaveDefaultDoubleTapAlone() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Settings.EDGE_SEEK.save(false);
            Settings.DOUBLE_TAP_ACTION.save("default");
            Settings.CUSTOM_SHARE_DOMAIN.save("");
            BaseSettings.SANITIZE_SHARING_LINKS.save(true);
            BlockAuthorPatch.setCurrentVideoParams(new Params(new Clip("copy-actions",
                    "https://www.tiktok.com/@someone/video/7712345?sender=someone",
                    new SoundStub("7712345678901234567"))));
            BlockAuthorPatch.setPlayingAweme("copy-actions");
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            String[][] cases = {
                    {"copy_link", "https://www.tiktok.com/@someone/video/7712345", "Link copied"},
                    {"copy_sound_link", "https://www.tiktok.com/music/x-7712345678901234567",
                            "Sound link copied"}
            };
            for (String[] testCase : cases) {
                Settings.LONG_PRESS_ACTION.save(testCase[0]);
                clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"));
                ShadowToast.reset();
                assertTrue(testCase[0], GestureActions.onLongPress(middleOf(activity)));
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals(testCase[1], String.valueOf(clipboard.getPrimaryClip().getItemAt(0).getText()));
                assertEquals(L10n.t(testCase[2]), ShadowToast.getTextOfLatestToast());

                clipboard.setPrimaryClip(ClipData.newPlainText("after", "after"));
                ShadowToast.reset();
                assertFalse(GestureActions.onDoubleTap());
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals("after", String.valueOf(clipboard.getPrimaryClip().getItemAt(0).getText()));
                assertNull(ShadowToast.getTextOfLatestToast());
            }
        } finally {
            Settings.LONG_PRESS_ACTION.resetToDefault();
            Settings.DOUBLE_TAP_ACTION.resetToDefault();
            Settings.CUSTOM_SHARE_DOMAIN.resetToDefault();
            BaseSettings.SANITIZE_SHARING_LINKS.resetToDefault();
        }
    }

    @Test public void longPressCopyActionsReportMissingLinksWithoutReplacingTheClipboard() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            Settings.EDGE_SEEK.save(false);
            BlockAuthorPatch.setCurrentVideoParams(new Params("missing-links"));
            BlockAuthorPatch.setPlayingAweme("missing-links");
            ClipboardManager clipboard =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            String[][] cases = {
                    {"copy_link", "This video has no link to copy"},
                    {"copy_sound_link", "This video has no sound of its own"}
            };
            for (String[] testCase : cases) {
                Settings.LONG_PRESS_ACTION.save(testCase[0]);
                clipboard.setPrimaryClip(ClipData.newPlainText("before", "before"));
                ShadowToast.reset();
                assertTrue(testCase[0], GestureActions.onLongPress(middleOf(activity)));
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals("before", String.valueOf(clipboard.getPrimaryClip().getItemAt(0).getText()));
                assertEquals(L10n.t(testCase[1]), ShadowToast.getTextOfLatestToast());
            }
        } finally {
            Settings.LONG_PRESS_ACTION.resetToDefault();
        }
    }

    @Test public void onlyTheOuterThirdsSeekAndOnlyWhenAskedTo() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            assertTrue("the screen has a width to divide", width > 0);
            Settings.EDGE_SEEK_SECONDS.save(7);

            // Off: every zone leaves the Long press action to decide, edges included.
            Settings.EDGE_SEEK.save(false);
            for (float at : new float[]{0.02f, 0.2f, 0.5f, 0.8f, 0.98f}) {
                assertEquals(0, GestureActions.edgeSeekDelta(press(width * at)));
            }

            Settings.EDGE_SEEK.save(true);
            assertEquals(-7000L, GestureActions.edgeSeekDelta(press(0f)));
            assertEquals(-7000L, GestureActions.edgeSeekDelta(press(width * 0.3f)));
            assertEquals(7000L, GestureActions.edgeSeekDelta(press(width * 0.7f)));
            assertEquals(7000L, GestureActions.edgeSeekDelta(press(width - 1f)));

            // A value the dialog would never accept, which is how a restored backup can
            // arrive. The top of the dialog's range is what a press is worth.
            Settings.EDGE_SEEK_SECONDS.save(100000);
            assertEquals(-60_000L, GestureActions.edgeSeekDelta(press(0f)));
            assertEquals(60_000L, GestureActions.edgeSeekDelta(press(width - 1f)));
            Settings.EDGE_SEEK_SECONDS.save(-5);
            assertEquals(0, GestureActions.edgeSeekDelta(press(0f)));
            Settings.EDGE_SEEK_SECONDS.save(7);

            // The middle third keeps the Long press action.
            assertEquals(0, GestureActions.edgeSeekDelta(press(width / 2f)));
            assertEquals(0, GestureActions.edgeSeekDelta(press(width / 3f)));
            assertEquals(0, GestureActions.edgeSeekDelta(press(width * 2f / 3f)));

            // Both lines to the pixel, and the two zones are the same width.
            int third = width / 3;
            assertEquals(-7000L, GestureActions.edgeSeekDelta(press(third - 1)));
            assertEquals(0, GestureActions.edgeSeekDelta(press(third)));
            assertEquals(0, GestureActions.edgeSeekDelta(press(width - third - 1)));
            assertEquals(7000L, GestureActions.edgeSeekDelta(press(width - third)));

            // Side by side with another app, TikTok's window does not start at the left of
            // the display, so the press across the window and the press across the display
            // are different numbers. The zones follow the window, which is what the video
            // fills. An event offset this way reports the two separately.
            MotionEvent offset = press(width + 20f);
            offset.offsetLocation(-width, 0f);
            assertEquals(width + 20f, offset.getRawX(), 0.5f);
            assertEquals(20f, offset.getX(), 0.5f);
            assertEquals(-7000L, GestureActions.edgeSeekDelta(offset));

            // Nothing to act on: no event, and a distance of zero.
            assertEquals(0, GestureActions.edgeSeekDelta(null));
            Settings.EDGE_SEEK_SECONDS.save(0);
            assertEquals(0, GestureActions.edgeSeekDelta(press(0f)));
        } finally {
            Settings.EDGE_SEEK.save(false);
            Settings.EDGE_SEEK_SECONDS.save(5);
        }
    }

    @Test public void anEdgePressMovesThePlayerAndStaysInsideTheVideo() {
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup().visible()) {
            var activity = controller.get();
            Utils.setContext(activity);
            int width = activity.getResources().getDisplayMetrics().widthPixels;
            Settings.EDGE_SEEK.save(true);
            Settings.EDGE_SEEK_SECONDS.save(5);
            Settings.LONG_PRESS_ACTION.save("default");

            FakeController player = new FakeController();
            FeedSeek.recordProgress(player, "one", 10_000L, player.player.durationMs);
            // The gesture reads the video on screen from the block author tracker.
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setCurrentVideoParams(new Params("one"));
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setPlayingAweme("one");

            // Right edge: forward by the configured distance, and the press is swallowed so
            // TikTok's own 2x hold does not start under the same finger.
            assertTrue(GestureActions.onLongPress(press(width * 0.9f)));
            assertEquals(15_000f, player.player.sought, 1f);

            // A second press counts from where the first one landed, without waiting for the
            // progress tick that would say so.
            assertTrue(GestureActions.onLongPress(press(width * 0.9f)));
            assertEquals(20_000f, player.player.sought, 1f);

            // Left edge, and never before the start.
            assertTrue(GestureActions.onLongPress(press(width * 0.05f)));
            assertEquals(15_000f, player.player.sought, 1f);
            for (int i = 0; i < 5; i++) assertTrue(GestureActions.onLongPress(press(0f)));
            assertEquals(0f, player.player.sought, 0.5f);

            // Never past the end either: landing on it would finish the video.
            for (int i = 0; i < 10; i++) assertTrue(GestureActions.onLongPress(press(width - 1f)));
            assertEquals(29_999f, player.player.sought, 1f);

            // A tick with nothing playing does not replace the player that is.
            player.player.sought = Float.NaN;
            FeedSeek.recordProgress(null, "one", 1_000L, 30_000L);
            FeedSeek.recordProgress(player, "", 1_000L, 30_000L);
            FeedSeek.recordProgress(player, "one", 1_000L, 0L);
            assertTrue(FeedSeek.seekBy("one", -1_000L));
            assertEquals(28_999f, player.player.sought, 1f);

            // Scrolled on to something that never reports progress, a photo post or an ad:
            // the player still holds the video before it, and moving that one would look
            // like nothing happening. The press says which video it means.
            player.player.sought = Float.NaN;
            assertFalse(FeedSeek.seekBy("two", 5_000L));
            assertFalse(FeedSeek.seekBy(null, 5_000L));
            assertTrue(Float.isNaN(player.player.sought));
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setCurrentVideoParams(new Params("two"));
            app.morphe.extension.tiktok.blockauthor.BlockAuthorPatch.setPlayingAweme("two");
            assertTrue("the press is still ours", GestureActions.onLongPress(press(width - 1f)));
            assertTrue("but nothing moved", Float.isNaN(player.player.sought));

            // A player we cannot reach is reported rather than pretended about.
            FeedSeek.recordProgress(new ShapelessController(), "three", 5_000L, 30_000L);
            assertFalse(FeedSeek.seekBy("three", 1_000L));
        } finally {
            Settings.EDGE_SEEK.save(false);
            Settings.EDGE_SEEK_SECONDS.save(5);
            Settings.LONG_PRESS_ACTION.save("default");
        }
    }

    @Test public void everyOverlayAndWarningSwitchIsReachable() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            SettingsStatus.videoOverlaysEnabled = true;
            SettingsStatus.sensitiveWarningsEnabled = true;
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new InterfacePreferenceCategory(activity, screen);
            for (String key : new String[]{"hide_feed_caption", "hide_feed_music", "hide_feed_action_bar",
                    "hide_feed_surveys", "hide_status_bar", "hide_sensitive_warnings"}) {
                assertNotNull(key, screen.findPreference(key));
            }
        } finally {
            SettingsStatus.videoOverlaysEnabled = false;
            SettingsStatus.sensitiveWarningsEnabled = false;
        }
    }

    @Test public void copyingTheLinkPutsTheSanitisedAddressOnTheClipboard() {
        // The clipboard is somewhere the link goes, so it gets the same treatment a shared link
        // gets: TikTok's own address carries the parameters that say who sent it.
        try (var controller = Robolectric.buildActivity(android.app.Activity.class).setup()) {
            Utils.setContext(controller.get());
            assertTrue(GestureActions.copyToClipboard("TikTok link",
                    "https://www.tiktok.com/@someone/video/7712345"));

            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) controller.get()
                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = clipboard.getPrimaryClip();
            assertNotNull("nothing reached the clipboard", clip);
            assertEquals("https://www.tiktok.com/@someone/video/7712345",
                    String.valueOf(clip.getItemAt(0).getText()));

            // Nothing to copy is not something to put on the clipboard.
            assertFalse(GestureActions.copyToClipboard("TikTok link", null));
            assertFalse(GestureActions.copyToClipboard("TikTok link", ""));
        }
    }

    public static final class SoundStub {
        private final String id;
        SoundStub(String id) { this.id = id; }
        public String getId() { return id; }
        public String getMid() { return id; }
    }

    /** Only what soundLink reads: a post whose music entry may or may not be there. */
    public static final class PostWithSound {
        private final SoundStub music;
        PostWithSound(SoundStub music) { this.music = music; }
        public SoundStub getMusic() { return music; }
    }

    @Test public void theSoundLinkIsThePageForThatSound() {
        // A post with only its own audio carries no sound entry, which is the ordinary case
        // rather than a failure, so the action has to say so instead of copying nothing.
        assertNull("a post with no sound produced a link", GestureActions.soundLink(new PostWithSound(null)));
        assertNull("no aweme produced a link", GestureActions.soundLink(null));

        String link = GestureActions.soundLink(new PostWithSound(new SoundStub("7712345678901234567")));
        assertNotNull("a post with a sound produced no link", link);
        assertTrue("the link is not a sound page: " + link,
                link.startsWith("https://www.tiktok.com/music/"));
        assertTrue("the link does not name the sound: " + link,
                link.endsWith("7712345678901234567"));
    }
}
