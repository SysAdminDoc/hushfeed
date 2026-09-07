/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.FeedVisibility;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Covers the feed while a session lock is running, and nothing else.
 *
 * <p>It is a view over the activity's content root, the same place the block button lives, shown
 * only while the feed itself is on screen. Messages, a profile and search are all still there
 * underneath it, because the only thing that decides whether this is visible is whether the home
 * tab is selected. It also does not touch a single feed item, so the batch TikTok already
 * fetched is still sitting there when the lock ends, and nothing is refetched.
 */
public final class SessionLockOverlay {
    private static final long TICK_MS = 1_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> overlayReference = new WeakReference<>(null);
    private static WeakReference<TextView> remainingReference = new WeakReference<>(null);
    private static boolean ticking;

    /**
     * Runs only while a lock is running. A repeating timer that outlives the lock would be a
     * second-by-second wake-up for a feature nobody switched on.
     */
    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            sync();
            if (!SessionBudget.isLocked()) {
                ticking = false;
                return;
            }
            MAIN.postDelayed(this, TICK_MS);
        }
    };

    private SessionLockOverlay() {
    }

    /** Starts the countdown if a lock is running. Cheap to call on every video. */
    public static void ensureRunning() {
        if (ticking || !SessionBudget.isLocked()) return;
        Utils.runOnMainThread(() -> {
            if (ticking || !SessionBudget.isLocked()) return;
            ticking = true;
            MAIN.post(TICK);
        });
    }

    /** Puts the overlay where it belongs for the moment, attaching or removing as needed. */
    public static void sync() {
        try {
            if (!SessionBudget.isLocked()) {
                detach();
                return;
            }
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing()) return;
            if (!FeedVisibility.isOnFeed(activity)) {
                View existing = overlayReference.get();
                if (existing != null) existing.setVisibility(View.GONE);
                return;
            }
            View overlay = attach(activity);
            if (overlay == null) return;
            overlay.setVisibility(View.VISIBLE);
            TextView remaining = remainingReference.get();
            if (remaining != null) remaining.setText(remainingLabel());
        } catch (Throwable error) {
            Logger.printException(() -> "Could not update the session lock overlay", error);
        }
    }

    static String remainingLabel() {
        long remainingMs = SessionBudget.lockRemainingMs();
        long totalMinutes = (remainingMs + 59_999L) / 60_000L;
        if (totalMinutes <= 0) return L10n.t("Less than a minute left");
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours == 0) return L10n.f("%1$d minutes left", minutes);
        // Built before the call so the clock face is not mistaken for text to translate.
        String clock = String.format(Locale.getDefault(), "%d:%02d", hours, minutes);
        return L10n.f("%1$s left", clock);
    }

    private static View attach(Activity activity) {
        View existing = overlayReference.get();
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) return null;
        if (existing != null && existing.getParent() == root) return existing;

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.argb(238, 0, 0, 0));
        // Swallows every touch, so the feed underneath stops scrolling without being emptied.
        panel.setClickable(true);
        panel.setFocusable(true);

        TextView title = new TextView(activity);
        title.setText(L10n.t(activity, "That is the feed for today"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView remaining = new TextView(activity);
        remaining.setText(remainingLabel());
        remaining.setTextColor(SettingsUi.accent());
        remaining.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        remaining.setGravity(Gravity.CENTER);
        remaining.setPadding(0, SettingsUi.dp(activity, 12), 0, SettingsUi.dp(activity, 12));
        panel.addView(remaining);
        remainingReference = new WeakReference<>(remaining);

        TextView hint = new TextView(activity);
        hint.setText(L10n.t(activity, "Messages, profiles and search still work."));
        hint.setTextColor(Color.argb(200, 235, 235, 240));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setGravity(Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(SettingsUi.dp(activity, 10));
        pill.setColor(Color.argb(60, 255, 255, 255));
        hint.setBackground(pill);
        int padding = SettingsUi.dp(activity, 12);
        hint.setPadding(padding, padding / 2, padding, padding / 2);
        panel.addView(hint);

        panel.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(panel);
        overlayReference = new WeakReference<>(panel);
        Logger.printDebug(() -> "Session lock overlay attached");
        return panel;
    }

    private static void detach() {
        View overlay = overlayReference.get();
        overlayReference = new WeakReference<>(null);
        remainingReference = new WeakReference<>(null);
        if (overlay == null) return;
        ViewGroup parent = overlay.getParent() instanceof ViewGroup
                ? (ViewGroup) overlay.getParent() : null;
        if (parent != null) parent.removeView(overlay);
    }
}
