/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.playback;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.wellbeing.SessionBudget;
import app.morphe.extension.tiktok.wellbeing.SessionLockOverlay;

import java.lang.ref.WeakReference;

/**
 * Two switches for a feed that keeps playing when nobody is watching it.
 *
 * <p>Opening the comments leaves the video running behind the sheet, so the sound of a video
 * nobody can see plays over reading. And coming back to the app starts the feed again by
 * itself, which is the one moment a reader has clearly not asked for anything.
 *
 * <p>There is no pause hook in this extension. Both of these ask for the audio focus instead,
 * the way the session hold does: it is how one app tells another to stop, and a player that
 * ignores it is no worse off than before. The hold owns the focus while it is up, and it covers
 * the feed and swallows the touches, so neither of these does anything during one.
 */
public final class PausePlayback {
    /** Written from the audio focus callback, which is not guaranteed to be the main thread. */
    private static volatile boolean quietened;

    /**
     * Held while a comment sheet is open or the feed is waiting for a tap. Nothing is done with
     * the callbacks beyond the one case that has to be handled: losing the focus for good drops
     * this off the focus stack, so the flag has to follow or nothing would ever ask again.
     */
    private static final AudioManager.OnAudioFocusChangeListener QUIET = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS) quietened = false;
    };

    private static WeakReference<View> sheetReference = new WeakReference<>(null);
    private static WeakReference<View> catcherReference = new WeakReference<>(null);
    private static boolean installed;

    /**
     * True once the app has been away. A resume that follows no pause is a dialog closing over
     * an activity that never left, and the reader did not go anywhere.
     */
    private static boolean wasAway;

    private PausePlayback() {
    }

    // ------------------------------------------------------------------ the comment sheet

    /**
     * Called as a comment cell is bound, which only happens while a sheet is open.
     *
     * <p>The cell is not attached to anything yet during the bind, so the walk up to the sheet
     * waits a post, which is the same thing the block-author takeover in the same callback does.
     */
    public static void onCommentCellBound(View cell) {
        if (cell == null || !Settings.PAUSE_ON_COMMENTS.get()) return;
        cell.post(() -> holdForSheet(cell));
    }

    private static void holdForSheet(View cell) {
        try {
            // The hold covers the feed and takes the touches, so nothing can be playing behind
            // a sheet during one, and taking the focus off it would hand the sound back.
            if (SessionBudget.isLocked()) return;
            // The sheet can have closed between the bind and this post. Quietening for a sheet
            // that has already gone would hang the listener on a view that will never detach
            // again, and the feed would stay quiet for the life of the process.
            if (cell.getWindowToken() == null) return;
            View sheet = sheetWindowOf(cell);
            if (sheet == null || sheet == sheetReference.get()) return;
            sheetReference = new WeakReference<>(sheet);
            quieten();
            sheet.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    // Only the sheet that is holding it hands it back. A second sheet opened
                    // over the first takes the focus on its own, and the first one closing
                    // underneath it would otherwise give the sound back with one still open.
                    if (sheetReference.get() != view) return;
                    sheetReference = new WeakReference<>(null);
                    // The feed is back in front of the reader, so it may have the sound again.
                    unquieten();
                }
            });
        } catch (Throwable error) {
            Logger.printException(() -> "Could not follow the comment sheet", error);
        }
    }

    /**
     * The thing that goes away when the comments close, or null when nothing here can tell.
     *
     * <p>A sheet in a window of its own, which is what a bottom sheet dialog is, has a root view
     * of its own, and that root detaches when the sheet is dismissed. That is a signal worth
     * trusting.
     *
     * <p>A sheet drawn into the activity shares the activity's root, and nothing in the
     * hierarchy says when it closes. Walking up to a fixed container and watching that would be
     * watching a view that never detaches: the sound would be handed back never, rather than
     * when the reader closed the comments. So on a build shaped that way this answers null and
     * the switch does nothing at all, which is the failure worth having. It is also why the
     * walk does not stop at android.R.id.content, which is not the top of the window.
     */
    static View sheetWindowOf(View cell) {
        View root = cell.getRootView();
        if (root == null || root == cell) return null;
        // The activity this cell belongs to, not whichever one the extension last saw. Only the
        // main activity is hooked, so asking Utils meant comparing a sheet in some other screen
        // against the wrong decor view, and returning that screen's own root: a view that
        // detaches when the whole screen goes, which is not what closing the comments means.
        Activity activity = activityOf(cell);
        if (activity == null || activity.getWindow() == null) return null;
        return root == activity.getWindow().getDecorView() ? null : root;
    }

    /** The activity a view is in, or null when its context is not one. */
    private static Activity activityOf(View view) {
        Context context = view.getContext();
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    // ------------------------------------------------------------------ coming back to the app

    /**
     * Called from the main activity's onCreate. Registers once for the whole process: an
     * activity is created again on a configuration change the host does not swallow, and a
     * second registration would quieten the feed twice for one return.
     */
    public static void install(Activity activity) {
        try {
            if (installed || activity == null) return;
            Application application = activity.getApplication();
            if (application == null) return;
            installed = true;
            application.registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityResumed(Activity resumed) {
                            onForeground(resumed);
                        }

                        @Override public void onActivityPaused(Activity paused) {
                        }

                        @Override public void onActivityStopped(Activity stopped) {
                            onBackground();
                        }

                        @Override public void onActivityCreated(Activity created, Bundle state) {
                        }

                        @Override public void onActivityStarted(Activity started) {
                        }

                        @Override public void onActivitySaveInstanceState(
                                Activity saved, Bundle state) {
                        }

                        @Override public void onActivityDestroyed(Activity destroyed) {
                        }
                    });
        } catch (Throwable error) {
            Logger.printException(() -> "Could not follow the app coming back", error);
        }
    }

    /**
     * The app is no longer on screen.
     *
     * <p>Stopped rather than paused: a dialog or an in-app screen over the feed pauses the
     * activity without the reader having gone anywhere, and gating the feed behind a tap for
     * that is not what the row says. And nothing this holds survives the app going away, since
     * a sheet dismissed while nobody was looking would otherwise leave the feed quiet until
     * the process died.
     */
    static void onBackground() {
        wasAway = true;
        letGo();
    }

    static void onForeground(Activity activity) {
        try {
            // The sheet the reader left open is the sheet they are looking at again.
            View sheet = sheetReference.get();
            if (sheet != null && sheet.getWindowToken() != null
                    && Settings.PAUSE_ON_COMMENTS.get() && !SessionBudget.isLocked()) {
                quieten();
            }
            if (!wasAway) return;
            wasAway = false;
            if (!Settings.NO_RESUME_ON_FOREGROUND.get()) return;
            // A hold is already covering the feed and already has the sound.
            if (SessionBudget.isLocked()) return;
            if (activity == null || !FeedVisibility.isOnFeed(activity)) return;
            quieten();
            waitForATap(activity);
        } catch (Throwable error) {
            Logger.printException(() -> "Could not hold the feed on returning", error);
        }
    }

    /**
     * Waits for the reader to say they want the feed, by taking the first tap and nothing else.
     *
     * <p>It stops above the tab bar, for the reason the hold's own panel does: messages, a
     * profile and search have to stay one tap away, and a reader who comes back to open their
     * messages should not have to tap twice.
     */
    private static void waitForATap(Activity activity) {
        removeCatcher();
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) {
            unquieten();
            return;
        }
        View catcher = new View(activity);
        catcher.setBackgroundColor(Color.TRANSPARENT);
        catcher.setContentDescription(L10n.t(activity, "Tap to start the feed"));
        catcher.setClickable(true);
        catcher.setFocusable(true);
        catcher.setOnClickListener(view -> {
            removeCatcher();
            unquieten();
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.bottomMargin = SessionLockOverlay.navigationHeight(activity, root);
        root.addView(catcher, params);
        catcherReference = new WeakReference<>(catcher);
    }

    /**
     * Hands the sound and the catcher back as the app goes away.
     *
     * <p>The sheet being followed is remembered rather than forgotten. A reader who switches
     * apps with the comments open comes back to the same sheet, and nothing binds a cell in a
     * list that is already full, so forgetting it here left the feed playing behind an open
     * sheet for the rest of that sheet's life.
     */
    private static void letGo() {
        removeCatcher();
        unquieten();
    }

    private static void removeCatcher() {
        View catcher = catcherReference.get();
        catcherReference = new WeakReference<>(null);
        if (catcher == null) return;
        if (catcher.getParent() instanceof ViewGroup) {
            ((ViewGroup) catcher.getParent()).removeView(catcher);
        }
    }

    // ------------------------------------------------------------------ the sound itself

    @SuppressWarnings("deprecation")
    private static void quieten() {
        if (quietened) return;
        AudioManager audio = audioManager();
        if (audio == null) return;
        try {
            // The request-object form arrived in API 26 and this runs from 23. The older call is
            // deprecated rather than gone, and it is the one both understand.
            audio.requestAudioFocus(QUIET, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            quietened = true;
        } catch (Exception refused) {
            Logger.printDebug(() -> "Could not take the audio focus to pause the feed");
        }
    }

    @SuppressWarnings("deprecation")
    private static void unquieten() {
        if (!quietened) return;
        quietened = false;
        AudioManager audio = audioManager();
        if (audio == null) return;
        try {
            audio.abandonAudioFocus(QUIET);
        } catch (Exception ignored) {
            Logger.printDebug(() -> "Could not hand the audio focus back");
        }
    }

    private static AudioManager audioManager() {
        Context context = Utils.getContext();
        return context == null ? null
                : (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    // ------------------------------------------------------------------ test seams

    static boolean quietenedForTests() {
        return quietened;
    }

    static View catcherForTests() {
        return catcherReference.get();
    }

    static void resetForTests() {
        removeCatcher();
        quietened = false;
        installed = false;
        wasAway = false;
        sheetReference = new WeakReference<>(null);
    }

    static void setWasAwayForTests(boolean away) {
        wasAway = away;
    }
}
