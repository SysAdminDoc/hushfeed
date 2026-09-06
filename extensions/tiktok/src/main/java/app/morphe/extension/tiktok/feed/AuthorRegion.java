/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.feed;

import android.app.Activity;
import android.text.TextUtils;
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

    /** Exactly what was written over it, so a row TikTok has since rebound is left alone. */
    private static CharSequence decoratedText;

    /** The region is read by reflection, so it is resolved once per video, not per frame. */
    private static WeakReference<Object> regionAweme = new WeakReference<>(null);
    private static String regionValue;

    private static WeakReference<Object> handleAweme = new WeakReference<>(null);
    private static String handleValue;

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

            boolean wantsRegion = Settings.SHOW_AUTHOR_REGION.get();
            boolean wantsHandle = Settings.SHOW_AUTHOR_HANDLE.get();
            if ((!wantsRegion && !wantsHandle) || !FeedVisibility.isOnFeed(activity)) {
                restore();
                return;
            }

            // Resolving the text first keeps the view tree search off the layout path for
            // every video that has nothing to show.
            String region = wantsRegion ? region() : null;
            String handle = wantsHandle ? handle() : null;
            if (region == null && handle == null) {
                restore();
                return;
            }

            decorate(findName(activity.findViewById(android.R.id.content)), handle, region);
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
     * Writes the row once. The handle replaces the display name; the country is appended
     * after whichever of the two is showing. TikTok rewrites the row's text on every bind,
     * so whatever is read here is its own text unless this already ran against the view.
     */
    static void decorate(TextView name, String handle, String region) {
        if (name == null || (handle == null && region == null)) {
            restore();
            return;
        }

        CharSequence current = name.getText();
        CharSequence written = decoratedText;
        boolean ours = name == decoratedName.get() && current != null && written != null
                && current.toString().equals(written.toString());

        if (ours) {
            CharSequence settled = build(originalName, handle, region);
            if (settled != null && settled.toString().equals(current.toString())) {
                // Already saying this. Every layout pass lands here.
                return;
            }
        }

        // Put back whatever was written before, then read the name again: on a video
        // change the text read a moment ago belonged to the previous video, and building
        // on that is how a row ends up reading "creator - US - GB".
        restore();

        CharSequence text = name.getText();
        CharSequence updated = build(text, handle, region);
        if (text == null || updated == null || updated.toString().equals(text.toString())) {
            return;
        }

        decoratedName = new WeakReference<>(name);
        originalName = text;
        decoratedText = updated;
        name.setText(updated);
    }

    /**
     * What the row should say. The handle replaces the display name outright; the country
     * follows whichever of the two is showing. concat rather than string addition, so a
     * styled name keeps its spans.
     */
    private static CharSequence build(CharSequence original, String handle, String region) {
        if (original == null) {
            return null;
        }
        CharSequence text = handle == null ? original : "@" + handle;
        return region == null ? text : TextUtils.concat(text, SEPARATOR + region);
    }

    /** Lets a test drive the ids the activity's resources would otherwise supply. */
    static void setViewIds(int nameId, int postTimeId) {
        nameViewId = nameId;
        postTimeViewId = postTimeId;
    }

    /** The two letter country the current video was posted from, upper case. */
    private static String region() {
        Object aweme = CurrentVideoAuthor.getAweme();
        if (aweme == null) {
            regionAweme = new WeakReference<>(null);
            regionValue = null;
            return null;
        }
        if (aweme == regionAweme.get()) {
            return regionValue;
        }

        String region = Reflect.string(aweme, "getRegion", "region");
        String trimmed = region == null ? null : region.trim();
        regionValue = trimmed == null || trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
        regionAweme = new WeakReference<>(aweme);
        return regionValue;
    }

    /** The creator's @name for the current video, without the at sign. */
    private static String handle() {
        Object aweme = CurrentVideoAuthor.getAweme();
        if (aweme == null) {
            handleAweme = new WeakReference<>(null);
            handleValue = null;
            return null;
        }
        if (aweme == handleAweme.get()) {
            return handleValue;
        }

        Object author = Reflect.property(aweme, "getAuthor", "author");
        String unique = Reflect.string(author, "getUniqueId", "uniqueId");
        String trimmed = unique == null ? null : unique.trim();
        handleValue = trimmed == null || trimmed.isEmpty() ? null : trimmed;
        handleAweme = new WeakReference<>(aweme);
        return handleValue;
    }

    static void restore() {
        TextView name = decoratedName.get();
        CharSequence previous = originalName;
        CharSequence written = decoratedText;
        decoratedName = new WeakReference<>(null);
        originalName = null;
        decoratedText = null;

        if (name == null || previous == null || written == null) {
            return;
        }

        // Only undo the exact edit made here. A row TikTok has rebound since carries its
        // own text, and a new creator's name can begin with the old one, so a prefix test
        // would truncate a genuine name.
        CharSequence now = name.getText();
        if (now != null && now.toString().equals(written.toString())) {
            name.setText(previous);
        }
    }
}
