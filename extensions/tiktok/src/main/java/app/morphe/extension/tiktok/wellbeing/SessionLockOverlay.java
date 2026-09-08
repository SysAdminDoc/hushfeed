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
 * Covers the feed while a hold is running, and nothing else.
 *
 * <p>It is a view over the activity's content root, the same place the block button lives, shown
 * only while the feed itself is on screen. Messages, a profile and search are all still there
 * underneath it, because the only thing that decides whether this is visible is whether the home
 * tab is selected. It also does not touch a single feed item, so the batch TikTok already
 * fetched is still sitting there when the hold ends, and nothing is refetched.
 *
 * <p>The way out of the hold is on the panel. Anything else drawn on the content root ends up
 * underneath it, because this covers the whole root and swallows every touch, so a banner
 * offering an Undo would be both invisible and untappable.
 */
public final class SessionLockOverlay {
    private static final long TICK_MS = 1_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<View> overlayReference = new WeakReference<>(null);
    private static WeakReference<TextView> remainingReference = new WeakReference<>(null);
    private static volatile boolean ticking;

    /**
     * Runs only while a hold is running. A repeating timer that outlives the hold would be a
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

    /**
     * Starts the countdown if a hold is running. Called from the player's progress callback, so
     * the already-running case must not reach the budget's monitor at all.
     */
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
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
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
        if (hours == 0) {
            return minutes == 1
                    ? L10n.t("One minute left")
                    : L10n.f("%1$d minutes left", minutes);
        }
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
        title.setText(SessionBudgetNotice.spentMessage());
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView remaining = new TextView(activity);
        remaining.setText(remainingLabel());
        // The panel is always this near-black scrim, so the countdown takes the colour meant
        // for what this project draws over the app rather than the settings accent, which is a
        // dark crimson in the light theme and 3:1 on black. SettingsUi.isDarkMode is a cached
        // flag the settings screen sets, so off the settings screen it answers for the system
        // theme rather than for this panel.
        remaining.setTextColor(SettingsUi.overlayAccentOn(true));
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
        panel.addView(hint);

        // The way out. A budget nobody can overrule is a budget people switch off instead, and
        // this has to be here rather than on a banner, which the panel would cover.
        TextView release = new TextView(activity);
        release.setText(L10n.t(activity, "Open the feed anyway"));
        release.setContentDescription(L10n.t(activity, "Open the feed anyway"));
        release.setTextColor(Color.WHITE);
        release.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        release.setGravity(Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(SettingsUi.dp(activity, 24));
        pill.setColor(Color.argb(70, 255, 255, 255));
        release.setBackground(pill);
        int padding = SettingsUi.dp(activity, 20);
        release.setPadding(padding, SettingsUi.dp(activity, 14), padding, SettingsUi.dp(activity, 14));
        release.setMinimumHeight(SettingsUi.dp(activity, 48));
        LinearLayout.LayoutParams releaseParams = new LinearLayout.LayoutParams(-2, -2);
        releaseParams.topMargin = SettingsUi.dp(activity, 28);
        release.setLayoutParams(releaseParams);
        release.setOnClickListener(view -> {
            SessionBudget.releaseLock();
            sync();
            Utils.showToastShort(L10n.t("The feed is open again"));
        });
        panel.addView(release);

        // Stops above the navigation. Covering the whole content root would take the tab bar
        // with it, and then messages, profiles and search are not reachable at all, which is the
        // one thing the panel says it leaves alone.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.bottomMargin = navigationHeight(activity, root);
        panel.setLayoutParams(params);
        root.addView(panel);
        overlayReference = new WeakReference<>(panel);
        Logger.printDebug(() -> "Session lock overlay attached");
        return panel;
    }

    /**
     * How much of the bottom belongs to the navigation.
     *
     * <p>Measured from the Home tab's own row rather than from a fixed number of pixels: the bar
     * is a different height on a phone with gesture navigation than on one without, and the row
     * is the view {@code FeedVisibility} already holds. Zero when this build does not have it,
     * which leaves the panel covering everything, because a hold that can be walked around is
     * worse than one that covers a tab bar.
     */
    private static int navigationHeight(Activity activity, ViewGroup root) {
        View homeTab = FeedVisibility.homeTabView(activity);
        if (homeTab == null) return 0;

        View bar = homeTab;
        // Up to the row that spans the width, which is the bar rather than the one tab in it.
        for (int step = 0; step < 4 && bar.getParent() instanceof ViewGroup; step++) {
            ViewGroup parent = (ViewGroup) bar.getParent();
            if (parent == root) break;
            bar = parent;
            if (bar.getWidth() >= root.getWidth() && bar.getWidth() > 0) break;
        }
        int height = bar.getHeight();
        return height > 0 && height < root.getHeight() / 3 ? height : 0;
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
