/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.extension.tiktok.wellbeing;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
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
    /** What each view behind the panel said about itself before the hold covered it. */
    private static final java.util.WeakHashMap<View, Integer> previousAccessibilityImportance =
            new java.util.WeakHashMap<>();
    private static WeakReference<TextView> remainingReference = new WeakReference<>(null);
    private static WeakReference<TextView> releaseReference = new WeakReference<>(null);
    private static WeakReference<TextView> hintReference = new WeakReference<>(null);
    private static WeakReference<TextView> messagesReference = new WeakReference<>(null);
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

    /**
     * Held for the life of the hold. Nothing is done with the callbacks: this is here to stop the
     * feed playing, not to play anything.
     */
    private static final AudioManager.OnAudioFocusChangeListener QUIET = change -> {
        // Losing it for good, to a call or to another app, drops us off the focus stack, and
        // the flag has to follow or nothing would ever ask again. What must not happen is
        // asking again a second later: that is how you take the sound off the call that just
        // took it, once a second, for the length of the hold. So the flag is cleared and the
        // next request waits for the panel to be put up again, which only happens when the
        // reader comes back to the feed.
        if (change == AudioManager.AUDIOFOCUS_LOSS) quietened = false;
    };
    /** Written from the audio focus callback, which is not guaranteed to be the main thread. */
    private static volatile boolean quietened;

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
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                // Mid teardown, so there is nothing to attach to yet. The hold is still on and
                // the player may still be running, so the sound is still worth asking for.
                requestQuiet();
                return;
            }
            if (!FeedVisibility.isOnFeed(activity)) {
                // Messages, profiles and search still work, which the panel says in as many
                // words, so the hold has no business silencing anything played there.
                releaseQuiet();
                View existing = overlayReference.get();
                if (existing != null) {
                    existing.setVisibility(View.GONE);
                    // Hidden is not attached: the panel is left in place while the reader is on
                    // messages or a profile, and the feed has to be readable again meanwhile.
                    hideBehind(parentOf(existing), existing, false);
                }
                return;
            }
            View before = overlayReference.get();
            View overlay = attach(activity);
            if (overlay == null) {
                // No content root to hold the panel. The feed is still running, so ask for the
                // sound anyway rather than leaving the hold with nothing at all.
                requestQuiet();
                return;
            }
            // A panel that was just built, or one coming back from the reader being away on
            // messages or search. Not every tick: a tick that asked again would be asking a
            // phone call to give the sound back once a second, for as long as the hold runs.
            boolean goingUp = before != overlay || overlay.getVisibility() != View.VISIBLE;
            overlay.setVisibility(View.VISIBLE);
            if (goingUp) hideBehind(parentOf(overlay), overlay, true);
            if (goingUp) requestQuiet();
            // Only as the panel goes up. Whether there is an Inbox tab changes when TikTok
            // rebuilds its tab bar, not second by second, and for a reader who has hidden Inbox
            // the answer is a failed lookup every time: asking on every tick would be a walk of
            // the whole view tree once a second for the length of the hold.
            if (goingUp) applyMessagesAction(activity);
            TextView remaining = remainingReference.get();
            if (remaining != null) remaining.setText(remainingLabel());
            applyLockedState();
        } catch (Throwable error) {
            Logger.printException(() -> "Could not update the session lock overlay", error);
        }
    }

    /**
     * Offers the way to messages only while there is an Inbox tab to open.
     *
     * <p>Absent when this build renames the tab and when the reader has hidden Inbox in Feed
     * navigation, which is the same thing as far as the panel is concerned: the filter drops the
     * tab from the model, so no view is ever built and there is nothing to offer.
     */
    private static void applyMessagesAction(Activity activity) {
        TextView messages = messagesReference.get();
        if (messages == null) return;
        boolean reachable = activity != null && FeedVisibility.inboxTabView(activity) != null;
        messages.setVisibility(reachable ? View.VISIBLE : View.GONE);
    }

    /**
     * Takes the way out away on a day the reader locked, and says when it comes back.
     *
     * <p>Run on every sync rather than only when the panel is built, because the panel can
     * outlive the day: it is left attached while the reader is on messages or a profile.
     */
    private static void applyLockedState() {
        boolean locked = SessionBudget.lockedToday();
        TextView release = releaseReference.get();
        if (release != null) {
            int left = SessionBudget.passesLeftToday();
            // Spent is the same as locked as far as this control goes: there is no way through
            // today. The hint below still says what does work, so the panel is not a dead end.
            release.setVisibility(locked || left == 0 ? View.GONE : View.VISIBLE);
            release.setText(releaseLabel(left));
            release.setContentDescription(release.getText());
        }
        TextView hint = hintReference.get();
        if (hint == null) return;
        // One literal, because the translation gate reads the literal handed to L10n and a
        // string built from two of them is two entries it cannot find.
        hint.setText(locked
                ? L10n.f("Today's budget is locked. The feed opens again at %1$s. Messages, profiles and search still work.", resetTimeLabel())
                : L10n.t("Messages, profiles and search still work."));
    }

    /**
     * What the way out says, which is how many are left when the reader capped them.
     *
     * <p>Each form is its own literal rather than a label with a count appended, because the
     * translation gate reads the literal handed to L10n and a sentence built from two of them is
     * two entries it cannot find.
     */
    static String releaseLabel(int passesLeft) {
        if (passesLeft == Integer.MAX_VALUE) return L10n.t("Open the feed anyway");
        if (passesLeft == 1) return L10n.t("Open the feed anyway, the last time today");
        return L10n.f("Open the feed anyway, %1$d left today", passesLeft);
    }

    /** The hour the locked day ends, on the reader's own clock. */
    public static String resetTimeLabel() {
        long until = SessionBudget.lockedUntilMs();
        if (until <= 0) return "";
        Context context = Utils.getContext();
        if (context == null) {
            return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                    .format(new java.util.Date(until));
        }
        return android.text.format.DateFormat.getTimeFormat(context)
                .format(new java.util.Date(until));
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
        // The same shade the ramp finishes on, taken from there rather than written twice, so
        // the last frame before the hold and the first frame of it cannot drift apart.
        panel.setBackgroundColor(Color.argb(HoldRamp.FULL_ALPHA, 0, 0, 0));
        // Swallows every touch, so the feed underneath stops scrolling without being emptied.
        panel.setClickable(true);
        panel.setFocusable(true);
        // Touch was the only thing it swallowed. A screen reader was told nothing when the hold
        // went up, and could still swipe through to the like, comment and share controls behind
        // it, which is the one thing the hold exists to stop.
        panel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        TextView title = new TextView(activity);
        String titleText = SessionBudgetNotice.spentMessage();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            panel.setAccessibilityPaneTitle(titleText);
        }
        title.setText(titleText);
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
        hintReference = new WeakReference<>(hint);

        // The way out. A budget nobody can overrule is a budget people switch off instead, and
        // this has to be here rather than on a banner, which the panel would cover.
        TextView release = new TextView(activity);
        release.setText(L10n.t(activity, "Open the feed anyway"));
        release.setContentDescription(L10n.t(activity, "Open the feed anyway"));
        // A TextView with a click listener is read as text. It is the only way out of the hold,
        // so it has to be offered as something to press.
        release.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
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
            // The model refuses this on a locked day and on a day whose passes are spent.
            // Checked through its answer, so a refused tap says nothing rather than claiming
            // the feed opened.
            if (SessionBudget.lockedToday()) return;
            if (!SessionBudget.releaseLock()) {
                sync();
                return;
            }
            sync();
            Utils.showToastShort(L10n.t("The feed is open again"));
        });
        panel.addView(release);
        releaseReference = new WeakReference<>(release);

        // The panel says messages still work and then covers everything, so the reader has to
        // already know the Inbox tab is under it. This is that tab's own view, clicked the way a
        // finger would: no call into a renamed method, and TikTok's own listener does the rest.
        TextView messages = new TextView(activity);
        messages.setText(L10n.t(activity, "Open messages"));
        messages.setContentDescription(L10n.t(activity, "Open messages"));
        messages.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        messages.setTextColor(Color.argb(200, 255, 255, 255));
        messages.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        messages.setGravity(Gravity.CENTER);
        messages.setPadding(padding, SettingsUi.dp(activity, 12), padding,
                SettingsUi.dp(activity, 12));
        messages.setMinimumHeight(SettingsUi.dp(activity, 48));
        LinearLayout.LayoutParams messagesParams = new LinearLayout.LayoutParams(-2, -2);
        messagesParams.topMargin = SettingsUi.dp(activity, 8);
        messages.setLayoutParams(messagesParams);
        messages.setOnClickListener(view -> {
            View inbox = FeedVisibility.inboxTabView(activity);
            if (inbox == null) return;
            inbox.performClick();
            // Off the feed the panel hides itself rather than detaching, so the hold is still
            // running and comes back the moment the reader does.
            sync();
        });
        panel.addView(messages);
        messagesReference = new WeakReference<>(messages);
        applyMessagesAction(activity);

        applyLockedState();

        // Stops above the navigation. Covering the whole content root would take the tab bar
        // with it, and then messages, profiles and search are not reachable at all, which is the
        // one thing the panel says it leaves alone.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        params.bottomMargin = navigationHeight(activity, root);
        panel.setLayoutParams(params);
        root.addView(panel);
        hideBehind(root, panel, true);
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

    /**
     * The panel covers the feed and swallows every touch, but the video underneath kept playing,
     * with sound, which reads as the app having broken rather than as a hold. There is no pause
     * hook in this extension, so the hold asks for the audio focus instead, which is how one app
     * tells another to stop. A player that ignores it is no worse off than before.
     */
    @SuppressWarnings("deprecation")
    private static void requestQuiet() {
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
            Logger.printDebug(() -> "The hold could not take the audio focus");
        }
    }

    @SuppressWarnings("deprecation")
    private static void releaseQuiet() {
        if (!quietened) return;
        quietened = false;
        AudioManager audio = audioManager();
        if (audio == null) return;
        try {
            audio.abandonAudioFocus(QUIET);
        } catch (Exception ignored) {
            Logger.printDebug(() -> "The hold could not hand the audio focus back");
        }
    }

    private static AudioManager audioManager() {
        Context context = Utils.getContext();
        return context == null ? null
                : (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    private static void detach() {
        releaseQuiet();
        View overlay = overlayReference.get();
        overlayReference = new WeakReference<>(null);
        remainingReference = new WeakReference<>(null);
        releaseReference = new WeakReference<>(null);
        hintReference = new WeakReference<>(null);
        if (overlay == null) return;
        ViewGroup parent = parentOf(overlay);
        if (parent != null) {
            hideBehind(parent, overlay, false);
            parent.removeView(overlay);
        }
    }

    private static ViewGroup parentOf(View view) {
        if (view == null) return null;
        return view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
    }

    /**
     * Takes everything behind the panel out of the reading order, or puts it back.
     *
     * <p>The panel covers the feed for anyone looking at it, and covers nothing at all for
     * anyone swiping through it with a screen reader. Each view's own setting is kept so that
     * putting it back does not hand TikTok a value this project invented.
     */
    private static void hideBehind(ViewGroup root, View panel, boolean hidden) {
        if (root == null) return;
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child == panel) continue;
            if (hidden) {
                if (!previousAccessibilityImportance.containsKey(child)) {
                    previousAccessibilityImportance.put(child, child.getImportantForAccessibility());
                }
                child.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            } else {
                Integer previous = previousAccessibilityImportance.remove(child);
                if (previous != null) child.setImportantForAccessibility(previous);
            }
        }
    }
}
