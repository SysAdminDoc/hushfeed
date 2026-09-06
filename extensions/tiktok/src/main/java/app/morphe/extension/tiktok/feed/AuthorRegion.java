/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.CurrentVideoAuthor;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Puts the country a video was posted from next to the creator's name on the feed.
 *
 * The name lives in a {@code title} button, which is a generic id the comment rows use as
 * well, so the row is identified structurally instead: the feed's author row is the parent
 * that holds both {@code title} and {@code tv_post_time}. The region comes from the Aweme
 * that {@link CurrentVideoAuthor} says is on screen, so it follows the player rather than
 * the feed's prefetch.
 */
public final class AuthorRegion {
    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    private static final String NAME_ID = "title";
    private static final String POST_TIME_ID = "tv_post_time";

    /** Separates the name from the country, matching the row's own middle dot. */
    private static final String SEPARATOR = " · ";

    private static int nameViewId;
    private static int postTimeViewId;

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener listener;

    /** The name as TikTok wrote it, before a country was appended to it. */
    private static WeakReference<TextView> decoratedName = new WeakReference<>(null);
    private static CharSequence originalName;

    private AuthorRegion() {
    }

    /** Called from the patched {@code MainActivity.onCreate}; the work is posted. */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        Utils.runOnMainThread(() -> installNow(activity));
    }

    private static void installNow(Activity activity) {
        try {
            if (activity.isFinishing()) {
                return;
            }
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Logger.printInfo(() -> "Author region found no content view to watch");
                return;
            }
            if (listener != null && activityReference.get() == activity) {
                return;
            }

            nameViewId = activity.getResources().getIdentifier(NAME_ID, "id", APP_PACKAGE);
            postTimeViewId = activity.getResources().getIdentifier(POST_TIME_ID, "id", APP_PACKAGE);
            if (nameViewId == 0 || postTimeViewId == 0) {
                Logger.printInfo(() -> "Author region could not resolve the feed name row");
                return;
            }

            listener = AuthorRegion::apply;
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            activityReference = new WeakReference<>(activity);
            Logger.printDebug(() -> "Author region installed");
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not install the author region", ex);
        }
    }

    private static void apply() {
        try {
            Activity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            if (!Settings.SHOW_AUTHOR_REGION.get() || !FeedVisibility.isOnFeed(activity)) {
                restore();
                return;
            }

            decorate(findName(activity.findViewById(android.R.id.content)), region());
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not show the author region", ex);
        }
    }

    /**
     * The feed's author row is the one holding both the name and the post time. A comment
     * row carries the same {@code title} id but no post time, which is what keeps this off
     * the comment panel.
     */
    static TextView findName(View root) {
        if (root == null) {
            return null;
        }
        View postTime = root.findViewById(postTimeViewId);
        if (postTime == null || !(postTime.getParent() instanceof ViewGroup)) {
            return null;
        }
        View name = ((ViewGroup) postTime.getParent()).findViewById(nameViewId);
        return name instanceof TextView ? (TextView) name : null;
    }

    /**
     * Appends the country once. TikTok rewrites the row's text on every bind, so whatever
     * is read here is its own text unless this already ran against the same view.
     */
    static void decorate(TextView name, String region) {
        if (name == null || region == null) {
            restore();
            return;
        }

        CharSequence text = name.getText();
        if (text == null) {
            return;
        }

        String suffix = SEPARATOR + region;
        if (text.toString().endsWith(suffix)) {
            return;
        }

        restore();
        decoratedName = new WeakReference<>(name);
        originalName = text;
        name.setText(text + suffix);
    }

    /** Lets a test drive the ids the activity's resources would otherwise supply. */
    static void setViewIds(int nameId, int postTimeId) {
        nameViewId = nameId;
        postTimeViewId = postTimeId;
    }

    /** The two letter country the current video was posted from, upper case. */
    private static String region() {
        String region = Reflect.string(CurrentVideoAuthor.getAweme(), "getRegion", "region");
        if (region == null) {
            return null;
        }
        String trimmed = region.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static void restore() {
        TextView name = decoratedName.get();
        decoratedName = new WeakReference<>(null);
        CharSequence previous = originalName;
        originalName = null;

        if (name == null || previous == null) {
            return;
        }
        // Only undo our own edit. A rebound row already carries TikTok's text.
        if (name.getText() != null && name.getText().toString().startsWith(previous.toString())) {
            name.setText(previous);
        }
    }
}
