/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.interaction;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.download.OriginalSoundDownloads;
import app.morphe.extension.tiktok.download.ExternalDownloader;
import app.morphe.extension.tiktok.share.ShareUrlSanitizer;
import app.morphe.extension.tiktok.feedfilter.SoundIdentity;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class GestureActions {
    private static final Map<Object, CommentControl> COMMENTS = new WeakHashMap<>();
    private GestureActions() {}

    private static final class CommentControl {
        WeakReference<View> view = new WeakReference<>(null);
        String videoId;
    }

    private static CommentControl commentControl(Object owner) {
        CommentControl control = COMMENTS.get(owner);
        if (control == null) {
            control = new CommentControl();
            COMMENTS.put(owner, control);
        }
        return control;
    }

    public static void registerCommentView(Object owner, View view) {
        for (Map.Entry<Object, CommentControl> entry : COMMENTS.entrySet()) {
            if (entry.getKey() != owner && entry.getValue().view.get() == view) entry.getValue().view.clear();
        }
        commentControl(owner).view = new WeakReference<>(view);
    }

    public static void bindCommentView(Object owner, Object params) {
        Object aweme = Reflect.property(params, "getAweme", "aweme");
        commentControl(owner).videoId = Reflect.string(aweme, "getAid", "aid");
    }

    public static boolean onDoubleTap() {
        String action = Settings.DOUBLE_TAP_ACTION.get();
        if ("nothing".equals(action)) return true;
        if (!"comments".equals(action)) return false;
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort(L10n.t("Comments aren't available for this video"));
        }
        return true;
    }

    /** Eligibility callback from the native edge-speedup component before its 300 ms timer. */
    public static boolean allowNativeEdgeSpeedup(float x) {
        if (edgeSeekDelta(x) != 0) return false;
        return !takesLongPress(Settings.LONG_PRESS_ACTION.get());
    }

    /** Whether this Long press choice gives the press to an action of Hushfeed's, not TikTok's. */
    public static boolean takesLongPress(String action) {
        return "nothing".equals(action) || "comments".equals(action)
                || "original_sound".equals(action) || "copy_link".equals(action)
                || "copy_sound_link".equals(action) || "youtube_music".equals(action);
    }

    /**
     * How far a long press at this point should move the video, in milliseconds. Zero when
     * the press landed in the middle, when edge seeking is off, or when there is no screen
     * to measure the press against.
     */
    static long edgeSeekDelta(MotionEvent event) {
        return event == null ? 0 : edgeSeekDelta(event.getX());
    }

    private static long edgeSeekDelta(float x) {
        if (!Settings.EDGE_SEEK.get()) return 0;
        int seconds = Settings.EDGE_SEEK_SECONDS.get();
        if (seconds <= 0) return 0;
        // The dialog offers at most 60. A restored backup is never asked, and a press that
        // jumps an hour is indistinguishable from the video ending.
        seconds = Math.min(60, seconds);
        Context context = Utils.getContext();
        if (context == null) return 0;
        int width = context.getResources().getDisplayMetrics().widthPixels;
        if (width <= 0) return 0;

        // Whole pixels, so a press exactly on a third lands on one side of the line every
        // time. A screen width times a third does not, and 106.666664 sits below 106.66667.
        int third = width / 3;
        // The listener sits on the cell's touch layer, which fills the window, so the press
        // is placed against the window. getRawX would be the position on the whole display,
        // which in a side by side split view puts every press in the right hand third.
        if (x < third) return -seconds * 1000L;
        if (x >= width - third) return seconds * 1000L;
        return 0;
    }

    /**
     * Runs before TikTok's own long press handling. Returning true swallows the gesture,
     * which is what keeps the 2x hold and the quick share sheet from also firing.
     */
    public static boolean onLongPress(MotionEvent event) {
        return handleLongPress(edgeSeekDelta(event));
    }

    /** Ordinary feed cells deliver the saved local DOWN x through their native timer. */
    public static boolean onLongPress(float x) {
        return handleLongPress(edgeSeekDelta(x));
    }

    private static boolean handleLongPress(long delta) {
        if (delta != 0) {
            // Named, so a post that never reported progress cannot move the video before it.
            String videoId = Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid");
            if (!FeedSeek.seekBy(videoId, delta)) Utils.showToastShort(L10n.t("Nothing is playing to seek"));
            // The edge belongs to the seek whether or not it worked, so the 2x hold that would
            // otherwise start under the finger does not fire on top of it.
            return true;
        }

        String action = Settings.LONG_PRESS_ACTION.get();
        if ("nothing".equals(action)) return true;
        if ("copy_link".equals(action)) {
            String link = ExternalDownloader.shareUrl(CurrentVideoAuthor.getAweme());
            // The same treatment a shared link gets: TikTok's own link carries the parameters
            // that say who sent it, and the clipboard is somewhere else that goes.
            String clean = link == null ? null : ShareUrlSanitizer.rewriteShareUrl(link);
            if (copyToClipboard("TikTok link", clean)) {
                if (android.os.Build.VERSION.SDK_INT < 33) {
                    Utils.showToastShort(L10n.t("Link copied"));
                }
            } else {
                Utils.showToastShort(L10n.t("This video has no link to copy"));
            }
            return true;
        }
        if ("copy_sound_link".equals(action)) {
            // The same treatment the video link above gets. TikTok's own sound share URL is a
            // share URL like any other: it carries the parameters that say who sent it, and the
            // custom share domain belongs on it too. Only the link this builds from the sound's
            // id has never had a query on it.
            String sound = soundLink(CurrentVideoAuthor.getAweme());
            String clean = sound == null ? null : ShareUrlSanitizer.rewriteShareUrl(sound);
            if (copyToClipboard("TikTok sound", clean)) {
                if (android.os.Build.VERSION.SDK_INT < 33) {
                    Utils.showToastShort(L10n.t("Sound link copied"));
                }
            } else {
                Utils.showToastShort(L10n.t("This video has no sound of its own"));
            }
            return true;
        }
        if ("original_sound".equals(action)) {
            OriginalSoundDownloads.start(CurrentVideoAuthor.getAweme(), Utils.getActivity());
            return true;
        }
        if ("youtube_music".equals(action)) {
            Context context = Utils.getActivity();
            YouTubeMusicSearch.open(CurrentVideoAuthor.getAweme(),
                    context != null ? context : Utils.getContext());
            return true;
        }
        if (!"comments".equals(action)) return false;
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort(L10n.t("Comments aren't available for this video"));
        }
        return true;
    }

    /** The page for the post's sound, or null when the post carries only its own audio. */
    static String soundLink(Object aweme) {
        Object music = aweme == null ? null : Reflect.property(aweme, "getMusic", "music");
        if (music == null) return null;
        Object shareInfo = Reflect.property(music, "getShareInfo", "shareInfo");
        String shared = shareInfo == null
                ? null : Reflect.string(shareInfo, "getShareUrl", "shareUrl");
        if (shared == null) shared = Reflect.string(music, "getShareUrl", "shareUrl");
        if (shared != null && shared.startsWith("https://")) return shared;

        String id = SoundIdentity.idOf(music);
        if (id == null || id.isEmpty()) return null;
        // The slug in front of the id is decoration; the id is what the page is looked up by.
        return "https://www.tiktok.com/music/x-" + id;
    }

    /**
     * Puts a link on the clipboard through the shared helper, which marks the clip sensitive on
     * the Android versions that understand the flag. These two were the only clips in the bundle
     * building their own {@code ClipData}, so they were the only ones a clipboard viewer could
     * read back without the warning.
     */
    static boolean copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) return false;
        Context context = Utils.getContext();
        if (context == null) return false;
        // Asked once and used once. Checking one call and letting the helper make its own
        // would be a guard over a different answer than the one that gets dereferenced.
        if (context.getSystemService(Context.CLIPBOARD_SERVICE) == null) return false;
        try {
            Utils.setClipboard(context, label, text);
        } catch (RuntimeException unavailable) {
            Logger.printException(() -> "Could not put " + label + " on the clipboard", unavailable);
            return false;
        }
        return true;
    }

    static boolean openComments(String videoId) {
        if (videoId == null || videoId.isEmpty()) return false;
        Map.Entry<Object, CommentControl> hidden = null;
        for (Map.Entry<Object, CommentControl> entry : COMMENTS.entrySet()) {
            CommentControl control = entry.getValue();
            View view = control.view.get();
            if (!videoId.equals(control.videoId) || view == null || !view.isAttachedToWindow()) continue;
            if (view.isShown() && view.getGlobalVisibleRect(new Rect())) return press(entry.getKey(), view);
            // Clear display can hide the action rail while its native click handler remains usable.
            hidden = entry;
        }
        return hidden != null && press(hidden.getKey(), hidden.getValue().view.get());
    }

    /** The Hook status family the comment press reports under. */
    static final String FAMILY = "double tap";
    /** The ability the comment assem implements; its one no-argument method is the icon press. */
    static final String COMMENT_ABILITY = "com.ss.android.ugc.aweme.feed.assem.ability.IVideoCommentAbility";

    /**
     * Presses the comment button the way TikTok's own keyboard shortcut does.
     *
     * <p>The registered view's click listener does nothing. TikTok wires the real handler as a
     * touch listener and leaves a placeholder click listener beside it, so {@code performClick()}
     * fires the placeholder, reports true, and no sheet opens; that is what a double tap set to
     * comments did on the S22 with 46.2.3 on 2026-09-14. The assem the view belongs to implements
     * {@code IVideoCommentAbility}, whose only no-argument method is the comment-icon press with
     * all of TikTok's own gating in front of it (comments turned off, a private account, FTC).
     * It is that shape on 46.2.3, 46.7.3 and 46.8.3 under three different names, so it is found
     * by shape. A build without the ability falls back to the click, and Hook status says so.
     */
    private static boolean press(Object owner, View view) {
        Method icon = commentPress(owner);
        if (icon == null) return click(view);
        try {
            icon.invoke(owner);
            Logger.printDebug(() -> "Pressed the comment button through " + icon.getName());
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Logger.printException(() -> "Could not press the comment button from the feed gesture", exception);
            return false;
        }
    }

    /**
     * The ability's one no-argument method, or null when the owner has no such ability or the
     * ability has grown a second method of that shape and the press can no longer be told apart.
     */
    static Method commentPress(Object owner) {
        if (owner == null) return null;
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> ability : type.getInterfaces()) {
                if (!COMMENT_ABILITY.equals(ability.getName())) continue;
                Method found = null;
                for (Method candidate : ability.getDeclaredMethods()) {
                    if (candidate.getParameterTypes().length != 0
                            || candidate.getReturnType() != void.class) {
                        continue;
                    }
                    if (found != null) {
                        HookStatus.missingMember(FAMILY, "one no-argument method on",
                                ability.getName(), "press");
                        return null;
                    }
                    found = candidate;
                }
                if (found == null) break;
                found.setAccessible(true);
                HookStatus.bound(FAMILY, ability.getName() + "#" + found.getName());
                return found;
            }
        }
        HookStatus.missingMember(FAMILY, "ability", owner.getClass().getName(),
                "IVideoCommentAbility");
        return null;
    }

    private static boolean click(View view) {
        try {
            return view.performClick();
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not open comments from the feed gesture", exception);
            return false;
        }
    }

    /**
     * Whether TikTok's main pager is on its feed page, the one a left swipe leaves for the creator's
     * profile. The profile is the pager's last page and the feed the one before it: on 47.0.3 the
     * home pager's adapter holds three pages, a side panel at 0, the feed at 1 and the profile at 2
     * (the probe's pagerstate on the S22, 2026-09-23), and a video opened from a profile, search or
     * a link sits in a pager of two, the video and the profile. Unreadable reads as not the feed.
     */
    static boolean onFeedPage(Object pager) {
        int page = currentPage(pager);
        int count = pageCount(pager);
        if (page < 0 || count < 2) return false;
        if (!swipeBound) {
            swipeBound = true;
            HookStatus.bound(SWIPE_FAMILY, "main pager");
        }
        return page == count - 2;
    }

    /*
     * Swipe left. TikTok's feed-to-profile pager (on 47.0.3 the base class X.05lU: the home
     * pager's own class extends it, and a video opened from a profile, search or a link sits in a
     * bare one inflated from XML) asks one private check whether it may page, from its dispatch,
     * its intercept and its own touch handling, each only while its paging valve is set. The
     * patch reads the gesture at the start of the dispatch, before anything asks, and hears
     * TikTok's own answer at the check's return.
     */

    /** The Hook status family the left swipe reports under. */
    static final String SWIPE_FAMILY = "swipe left";

    private static boolean swipeBound;
    private static float swipeDownX;
    private static float swipeDownY;
    /** Where the gesture is now, for the direction {@link #allowProfileSwipe} holds. */
    private static float swipeLastX;
    /** Whether TikTok itself would page during this gesture: its own check answered yes. */
    private static boolean swipePagingOn;
    /** Whether this gesture was held: TikTok said yes and the setting said no. */
    private static boolean swipeHeld;
    private static boolean swipeTracking;
    private static boolean swipeFired;

    /** Each event, at the start of the pager's dispatch: before anything in it asks whether it may page. */
    public static void onMainPagerDispatch(Object pager, MotionEvent event) {
        if (event == null) return;
        swipeLastX = event.getX();
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
        swipeDownX = event.getX();
        swipeDownY = event.getY();
        swipePagingOn = false;
        swipeHeld = false;
        swipeTracking = "comments".equals(Settings.SWIPE_LEFT_ACTION.get()) && onFeedPage(pager);
        swipeFired = false;
    }

    /**
     * TikTok's own answer to whether the pager may page, as its check returns it, and the answer
     * the pager gets. TikTok's no stays no: it turns paging off itself on surfaces that aren't
     * the feed (the Inbox, your own profile), and nothing here pages or opens anything there.
     */
    public static boolean pagingEnabled(Object pager, boolean original) {
        if (!original) return false;
        swipePagingOn = true;
        if (allowProfileSwipe(pager)) return true;
        swipeHeld = true;
        return false;
    }

    /**
     * Whether a pager TikTok lets page may page for this gesture. Only a gesture heading for the
     * profile is held, and only on the feed page: at the touch down TikTok still records where the
     * gesture starts, a swipe the other way still opens TikTok's side panel, from the profile the
     * swipe back stays TikTok's, and a pager whose pages can't be read pages as usual.
     */
    static boolean allowProfileSwipe(Object pager) {
        if ("default".equals(Settings.SWIPE_LEFT_ACTION.get())) return true;
        if (towardProfile(pager, swipeLastX - swipeDownX) <= 0) return true;
        return !onFeedPage(pager);
    }

    /**
     * How far a horizontal movement goes toward the profile page: to the left in a left-to-right
     * layout, to the right in a right-to-left one, where TikTok's pager runs the other way. Decided
     * the way TikTok decides it (X.05QH.LIZJ): the layout direction of the configuration.
     */
    static float towardProfile(Object pager, float dx) {
        boolean rtl = pager instanceof View
                ? ((View) pager).getContext().getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL
                : TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
        return rtl ? dx : -dx;
    }

    /**
     * Each event the pager's intercept or its own touch handling sees: from the intercept until it
     * takes the gesture, from onTouchEvent after. A child that takes the gesture for itself, a
     * photo carousel for one, keeps the rest from both, so only a swipe nothing else wanted gets
     * this far. With Swipe left set to comments, a clearly sideways move toward the profile from
     * the feed page opens the video's comments, once a swipe, where TikTok itself would page.
     */
    public static void onMainPagerTouch(Object pager, MotionEvent event) {
        if (event == null || event.getActionMasked() != MotionEvent.ACTION_MOVE) return;
        if (!swipeTracking || swipeFired || !swipePagingOn) return;
        float dx = event.getX() - swipeDownX;
        float dy = event.getY() - swipeDownY;
        if (towardProfile(pager, dx) < swipeDistance() || Math.abs(dx) < 2 * Math.abs(dy)) return;
        swipeFired = true;
        swipeCommentsOpener.run();
    }

    /**
     * The pager's own touch handling. TikTok hands it the up of every gesture whatever the check
     * says, and the pager picks its page there from the drag it saw and the fling: a gesture that
     * dragged a little before it was held, or touched down while the pager was settling back from
     * the profile, could land on the profile. A held gesture ends in a cancel instead, which
     * settles the pager on the page it is on.
     */
    public static void onMainPagerOwnTouch(Object pager, MotionEvent event) {
        onMainPagerTouch(pager, event);
        if (event != null && swipeHeld && event.getActionMasked() == MotionEvent.ACTION_UP) {
            event.setAction(MotionEvent.ACTION_CANCEL);
        }
    }

    /** How far left counts as a swipe: an eighth of the screen's width. */
    static float swipeDistance() {
        Context context = Utils.getContext();
        int width = context == null ? 0 : context.getResources().getDisplayMetrics().widthPixels;
        return (width > 0 ? width : 1080) / 8f;
    }

    /** What a left swipe set to comments does; a test stands in its own. */
    static Runnable swipeCommentsOpener = () -> {
        if (!openComments(Reflect.string(CurrentVideoAuthor.getAweme(), "getAid", "aid"))) {
            Utils.showToastShort(L10n.t("Comments aren't available for this video"));
        }
    };

    private static Method currentItem;
    private static Class<?> currentItemOwner;
    private static Method adapterGetter;
    private static Class<?> adapterOwner;
    private static Method countGetter;
    private static Class<?> countOwner;

    /** The pager's current page, or -1 when it can't be read. Asked on every touch, so the lookup is kept. */
    static int currentPage(Object pager) {
        if (pager == null) return -1;
        try {
            Method getter = currentItem;
            if (getter == null || currentItemOwner != pager.getClass()) {
                getter = pager.getClass().getMethod("getCurrentItem");
                getter.setAccessible(true);
                currentItem = getter;
                currentItemOwner = pager.getClass();
            }
            Object page = getter.invoke(pager);
            return page instanceof Integer ? (Integer) page : -1;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            HookStatus.missingMember(SWIPE_FAMILY, "method", pager.getClass().getName(), "getCurrentItem");
            return -1;
        }
    }

    /** How many pages the pager's adapter holds, or -1 when it can't be read. */
    static int pageCount(Object pager) {
        if (pager == null) return -1;
        try {
            Method getter = adapterGetter;
            if (getter == null || adapterOwner != pager.getClass()) {
                getter = pager.getClass().getMethod("getAdapter");
                getter.setAccessible(true);
                adapterGetter = getter;
                adapterOwner = pager.getClass();
            }
            Object adapter = getter.invoke(pager);
            if (adapter == null) return -1;
            Method count = countGetter;
            if (count == null || countOwner != adapter.getClass()) {
                // Made accessible: the adapter's own class need not be public.
                count = adapter.getClass().getMethod("getCount");
                count.setAccessible(true);
                countGetter = count;
                countOwner = adapter.getClass();
            }
            Object pages = count.invoke(adapter);
            return pages instanceof Integer ? (Integer) pages : -1;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            HookStatus.missingMember(SWIPE_FAMILY, "method", pager.getClass().getName(), "getAdapter().getCount()");
            return -1;
        }
    }
}
