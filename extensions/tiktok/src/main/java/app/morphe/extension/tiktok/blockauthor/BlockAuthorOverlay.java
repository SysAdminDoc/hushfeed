/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.feedfilter.SoundIdentity;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import app.morphe.extension.tiktok.notinterested.NotInterested;

import java.lang.ref.WeakReference;

/**
 * The block button that sits on the video player, plus the undo banner shown after a block.
 *
 * Everything is built in code. TikTok resources cannot be compiled by this patch set, so
 * the button is drawn rather than inflated, and it is attached to the activity content
 * root instead of TikTok's own action rail. That keeps it working across builds that
 * reshuffle the player view hierarchy.
 *
 * Long pressing the button enters drag mode so it can be parked anywhere. The position is
 * stored as a fraction of the screen, so it survives rotation and a different device.
 */
public final class BlockAuthorOverlay {
    private static final String SOUND_GLYPH = "♪";
    private static final int BUTTON_SIZE_DP = 44;
    private static final int BUTTON_GAP_DP = 8;
    private static final long UNDO_VISIBLE_MS = 6_000L;

    /** Right edge, just above TikTok's own action rail. */
    private static final float DEFAULT_X_FRACTION = 0.91f;
    private static final float DEFAULT_Y_FRACTION = 0.40f;

    private static WeakReference<View> buttonReference = new WeakReference<>(null);
    private static WeakReference<View> localHideReference = new WeakReference<>(null);
    private static WeakReference<View> soundButtonReference = new WeakReference<>(null);
    private static WeakReference<View> notInterestedReference = new WeakReference<>(null);

    /** Which banner a queued dismiss belongs to. Main thread only. */
    private static int undoGeneration;
    private static WeakReference<ViewGroup> rootReference = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener visibilityListener;
    private static WeakReference<View> undoReference = new WeakReference<>(null);

    /** Guards against a double tap blocking, then unblocking, the same account. */
    private static volatile boolean requestInFlight;

    /** True while the user is dragging the button, which suppresses the click. */
    private static boolean dragging;
    private static float dragOffsetX;
    private static float dragOffsetY;

    private BlockAuthorOverlay() {
    }

    /** @param author the new current author, or null when the current item has none. */
    static void onAuthorChanged(VideoAuthor author) {
        if (!Settings.BLOCK_AUTHOR_BUTTON.get() && !notInterestedEnabled()) {
            Utils.runOnMainThread(BlockAuthorOverlay::detach);
            return;
        }
        if (author == null) {
            Utils.runOnMainThread(BlockAuthorOverlay::syncVisibility);
            return;
        }
        Utils.runOnMainThread(() -> attach(author));
    }

    /**
     * Shows or hides the button as the feed comes and goes.
     *
     * The button is an overlay on the activity content root, so nothing removes it when
     * the user leaves the video feed. This is the seam that does it.
     */
    public static void setFeedVisible(boolean visible) {
        View button = buttonReference.get();
        if (button == null) {
            return;
        }
        int wanted = visible && Settings.BLOCK_AUTHOR_BUTTON.get() ? View.VISIBLE : View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
            if (!visible) {
                dismissUndo();
            }
        }
        View soundButton = soundButtonReference.get();
        if (soundButton != null) {
            // The sound button needs a sound to act on, and a feed filter to act through.
            boolean soundWanted = visible && Settings.BLOCK_AUTHOR_BUTTON.get() && SettingsStatus.feedFilterEnabled
                    && CurrentVideoSound.get() != null && CurrentVideoSound.get().isUsable();
            int soundVisibility = soundWanted ? View.VISIBLE : View.GONE;
            if (soundButton.getVisibility() != soundVisibility) {
                soundButton.setVisibility(soundVisibility);
            }
        }
        View localHide = localHideReference.get();
        if (localHide != null) {
            boolean localWanted = visible && Settings.BLOCK_AUTHOR_BUTTON.get()
                    && SettingsStatus.feedFilterEnabled;
            int localVisibility = localWanted ? View.VISIBLE : View.GONE;
            if (localHide.getVisibility() != localVisibility) {
                localHide.setVisibility(localVisibility);
            }
        }
        View feedback = notInterestedReference.get();
        if (feedback != null) feedback.setVisibility(visible && notInterestedEnabled() ? View.VISIBLE : View.GONE);
        // Off the feed every button above is GONE, so there is nothing to place. This also keeps
        // the whole of placeSoundButton off the layout callback on Profile, Inbox and Search.
        if (!visible) return;
        if (button.getParent() instanceof ViewGroup) {
            placeSoundButton(button, (ViewGroup) button.getParent());
        }
    }

    /**
     * Re-checks whether the feed is on screen. Runs on every layout pass, so it does
     * nothing but read a cached view's selected state.
     */
    private static void syncVisibility() {
        Activity activity = Utils.getActivity();
        if (activity == null) {
            return;
        }
        setFeedVisible(FeedVisibility.isOnFeed(activity) && CurrentVideoAuthor.get() != null);
    }

    private static void attach(VideoAuthor author) {
        try {
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }

            View existing = buttonReference.get();
            if (existing != null && existing.getParent() == root) {
                syncVisibility();
                return;
            }

            final View button = createButton(activity);
            final int size = SettingsUi.dp(activity, BUTTON_SIZE_DP);
            // Absolute LEFT, not START. Every position here is a pixel worked out from a raw
            // touch and written to leftMargin, and a mirrored layout resolves START to RIGHT and
            // then reads rightMargin, which nothing sets: the saved position was discarded, a
            // drag moved nothing sideways, and the Not interested button landed on top of the
            // block button because the two differ only in leftMargin.
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    size, size, Gravity.TOP | Gravity.LEFT);
            button.setLayoutParams(params);

            root.addView(button);
            buttonReference = new WeakReference<>(button);

            final View localHide = createLocalHideButton(activity);
            localHide.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
            root.addView(localHide);
            localHideReference = new WeakReference<>(localHide);

            final View soundButton = createSoundButton(activity);
            soundButton.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
            root.addView(soundButton);
            soundButtonReference = new WeakReference<>(soundButton);
            View feedback = createNotInterestedButton(activity);
            feedback.setLayoutParams(new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT));
            root.addView(feedback);
            notInterestedReference = new WeakReference<>(feedback);

            // The root has no measured size until it lays out, so the saved fraction can
            // only be turned into margins once dimensions are known.
            root.post(() -> applySavedPosition(button, root, size));

            installVisibilityListener(root);
            syncVisibility();

            Logger.printDebug(() -> "Block button attached for " + author.label());
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not attach the block button", ex);
        }
    }

    private static void installVisibilityListener(ViewGroup root) {
        if (visibilityListener != null && rootReference.get() == root) {
            return;
        }
        removeVisibilityListener();

        visibilityListener = BlockAuthorOverlay::syncVisibility;
        root.getViewTreeObserver().addOnGlobalLayoutListener(visibilityListener);
        rootReference = new WeakReference<>(root);
    }

    private static void removeVisibilityListener() {
        ViewGroup root = rootReference.get();
        if (root != null && visibilityListener != null) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(visibilityListener);
        }
        visibilityListener = null;
        rootReference = new WeakReference<>(null);
    }

    private static void detach() {
        removeVisibilityListener();
        View button = buttonReference.get();
        if (button != null && button.getParent() instanceof ViewGroup) {
            ((ViewGroup) button.getParent()).removeView(button);
        }
        buttonReference = new WeakReference<>(null);
        View soundButton = soundButtonReference.get();
        if (soundButton != null && soundButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) soundButton.getParent()).removeView(soundButton);
        }
        soundButtonReference = new WeakReference<>(null);
        View localHide = localHideReference.get();
        if (localHide != null && localHide.getParent() instanceof ViewGroup) {
            ((ViewGroup) localHide.getParent()).removeView(localHide);
        }
        localHideReference = new WeakReference<>(null);
        View feedback = notInterestedReference.get();
        if (feedback != null && feedback.getParent() instanceof ViewGroup) {
            ((ViewGroup) feedback.getParent()).removeView(feedback);
        }
        notInterestedReference = new WeakReference<>(null);
        dismissUndo();
    }

    private static View createSoundButton(Activity activity) {
        TextView button = new TextView(activity);
        button.setText(SOUND_GLYPH);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(L10n.t(activity, "Block this sound"));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(140, 0, 0, 0));
        background.setStroke(SettingsUi.dp(activity, 1), Color.argb(90, 255, 255, 255));
        button.setBackground(background);

        button.setOnClickListener(view -> onBlockSoundTapped());
        return button;
    }

    private static View createLocalHideButton(Activity activity) {
        TextView button = new TextView(activity);
        button.setText("×");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(L10n.t(activity, "Hide this creator locally"));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(140, 0, 0, 0));
        background.setStroke(SettingsUi.dp(activity, 1), Color.argb(90, 255, 255, 255));
        button.setBackground(background);
        button.setOnClickListener(view -> onLocalHideTapped());
        return button;
    }

    private static boolean notInterestedEnabled() {
        return SettingsStatus.notInterestedEnabled && Settings.NOT_INTERESTED_BUTTON.get();
    }

    private static View createNotInterestedButton(Activity activity) {
        TextView button = new TextView(activity);
        button.setText("-");
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(L10n.t(activity, "Not interested in this video"));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(180, 0, 0, 0));
        background.setCornerRadius(SettingsUi.dp(activity, 8));
        background.setStroke(SettingsUi.dp(activity, 1), Color.argb(90, 255, 255, 255));
        button.setBackground(background);
        button.setOnClickListener(view -> NotInterested.submit());
        return button;
    }

    /**
     * Records the current sound so the feed filter skips every video that uses it. Ids
     * are exact; a sound with no id is recorded by name, which also catches re-uploads.
     */
    private static void onBlockSoundTapped() {
        CurrentVideoSound sound = CurrentVideoSound.get();
        if (sound == null || !sound.isUsable()) {
            Utils.showToastShort(L10n.t("No sound to block on this video"));
            return;
        }

        final boolean byId = sound.id != null && !sound.id.isEmpty();
        if (byId) {
            Settings.BLOCKED_SOUND_IDS.save(SoundIdentity.withEntry(Settings.BLOCKED_SOUND_IDS.get(), sound.id));
        } else {
            Settings.BLOCKED_SOUND_NAMES.save(SoundIdentity.withEntry(Settings.BLOCKED_SOUND_NAMES.get(), sound.name));
        }
        Logger.printDebug(() -> "Blocked sound " + sound.label() + (byId ? " by id" : " by name"));

        showUndoBanner(L10n.f("Skipping videos with %1$s", sound.label()), () -> {
            if (byId) {
                Settings.BLOCKED_SOUND_IDS.save(SoundIdentity.withoutEntry(Settings.BLOCKED_SOUND_IDS.get(), sound.id));
            } else {
                Settings.BLOCKED_SOUND_NAMES.save(SoundIdentity.withoutEntry(Settings.BLOCKED_SOUND_NAMES.get(), sound.name));
            }
            Utils.showToastShort(L10n.f("Unblocked %1$s", sound.label()));
        });
    }

    /** Keeps the sound button parked directly under the block button. */
    private static void placeSoundButton(View blockButton, ViewGroup parent) {
        View soundButton = soundButtonReference.get();
        if (soundButton == null || soundButton.getParent() != parent) {
            return;
        }
        ViewGroup.MarginLayoutParams blockParams = (ViewGroup.MarginLayoutParams) blockButton.getLayoutParams();
        ViewGroup.MarginLayoutParams soundParams = (ViewGroup.MarginLayoutParams) soundButton.getLayoutParams();
        int size = blockParams.height > 0 ? blockParams.height : blockButton.getHeight();
        int gap = Math.round(BUTTON_GAP_DP * parent.getResources().getDisplayMetrics().density);
        int nextTop = blockParams.topMargin + size + gap;
        View localHide = localHideReference.get();
        boolean localVisible = localHide != null && localHide.getVisibility() == View.VISIBLE
                && localHide.getParent() == parent;
        if (localVisible) {
            ViewGroup.MarginLayoutParams localParams = (ViewGroup.MarginLayoutParams) localHide.getLayoutParams();
            setMargins(localHide, localParams, blockParams.leftMargin,
                    Math.min(nextTop, Math.max(0, parent.getHeight() - size)));
            nextTop += size + gap;
        }
        int top = nextTop;
        int maxTop = Math.max(0, parent.getHeight() - size);
        setMargins(soundButton, soundParams, blockParams.leftMargin, Math.min(top, maxTop));
        View feedback = notInterestedReference.get();
        if (feedback != null && feedback.getParent() == parent) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) feedback.getLayoutParams();
            int left = blockParams.leftMargin - size - gap;
            if (left < 0) left = blockParams.leftMargin + size + gap;
            setMargins(feedback, params, Math.max(0, Math.min(left, parent.getWidth() - size)),
                    blockParams.topMargin);
        }
    }

    /**
     * Writes margins only when they actually move.
     *
     * <p>{@link View#setLayoutParams} calls {@code requestLayout()} whatever it is handed, and
     * this runs from an {@code OnGlobalLayoutListener}, which the framework dispatches after
     * layout inside the same traversal. So an unconditional call schedules another traversal,
     * whose layout calls this again, once every frame for as long as the overlay is attached.
     * {@code InboxFilter.setRowHidden} and {@code ShareSheetTools.setCellHidden} guard their own
     * layout callbacks the same way.
     */
    private static void setMargins(View view, ViewGroup.MarginLayoutParams params, int left, int top) {
        if (params.leftMargin == left && params.topMargin == top) return;
        params.leftMargin = left;
        params.topMargin = top;
        view.setLayoutParams(params);
    }

    private static View createButton(Activity activity) {
        TextView button = new TextView(activity);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(L10n.t(activity, "Block this account"));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(140, 0, 0, 0));
        background.setStroke(SettingsUi.dp(activity, 1), Color.argb(90, 255, 255, 255));

        // The symbol is drawn over the disc instead of set as text, because the font
        // TikTok happens to be using may not carry it.
        Drawable glyph = new BlockGlyphDrawable(Color.WHITE, SettingsUi.dp(activity, 2));
        button.setBackground(new LayerDrawable(new Drawable[]{background, glyph}));

        button.setOnClickListener(view -> {
            // A drag ends with an ACTION_UP that would otherwise read as a click.
            if (dragging) {
                return;
            }
            onBlockTapped();
        });

        button.setOnLongClickListener(view -> {
            dragging = true;
            view.setAlpha(0.75f);
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            Utils.showToastShort(L10n.t("Drag to move, release to place"));
            return true;
        });

        button.setOnTouchListener(BlockAuthorOverlay::onButtonTouch);
        return button;
    }

    /**
     * Handles dragging. Returns false unless a drag is in progress so that normal click
     * and long press handling is left alone.
     */
    private static boolean onButtonTouch(View view, MotionEvent event) {
        if (!dragging) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragOffsetX = event.getX();
                dragOffsetY = event.getY();
            }
            return false;
        }

        ViewGroup parent = view.getParent() instanceof ViewGroup
                ? (ViewGroup) view.getParent()
                : null;
        if (parent == null) {
            dragging = false;
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float left = event.getRawX() - dragOffsetX - parentLeft(parent);
                float top = event.getRawY() - dragOffsetY - parentTop(parent);
                moveTo(view, parent, left, top);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = false;
                view.setAlpha(1f);
                savePosition(view, parent);
                return true;
            }

            default:
                return true;
        }
    }

    private static int parentLeft(ViewGroup parent) {
        int[] location = new int[2];
        parent.getLocationOnScreen(location);
        return location[0];
    }

    private static int parentTop(ViewGroup parent) {
        int[] location = new int[2];
        parent.getLocationOnScreen(location);
        return location[1];
    }

    /** Moves the button, keeping it fully inside its parent. */
    private static void moveTo(View view, ViewGroup parent, float left, float top) {
        // Before the first layout the view has no size, so fall back to the size it was
        // given, or the clamp would let it sit partly off the right and bottom edges.
        ViewGroup.LayoutParams layout = view.getLayoutParams();
        int width = view.getWidth() > 0 ? view.getWidth() : layout.width;
        int height = view.getHeight() > 0 ? view.getHeight() : layout.height;
        int maxLeft = Math.max(0, parent.getWidth() - width);
        int maxTop = Math.max(0, parent.getHeight() - height);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.leftMargin = Math.round(Math.min(Math.max(left, 0), maxLeft));
        params.topMargin = Math.round(Math.min(Math.max(top, 0), maxTop));
        view.setLayoutParams(params);
        placeSoundButton(view, parent);
    }

    private static void applySavedPosition(View view, ViewGroup parent, int size) {
        if (parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }

        float[] fractions = loadPositionFractions();
        float left = fractions[0] * parent.getWidth() - size / 2f;
        float top = fractions[1] * parent.getHeight() - size / 2f;
        moveTo(view, parent, left, top);
    }

    /** @return the stored centre position as {x, y} fractions of the parent. */
    private static float[] loadPositionFractions() {
        String stored = Settings.BLOCK_AUTHOR_BUTTON_POSITION.get();
        if (stored != null && !stored.isEmpty()) {
            String[] parts = stored.split(",");
            if (parts.length == 2) {
                try {
                    float x = Float.parseFloat(parts[0].trim());
                    float y = Float.parseFloat(parts[1].trim());
                    if (x >= 0f && x <= 1f && y >= 0f && y <= 1f) {
                        return new float[]{x, y};
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to the default.
                }
            }
        }
        return new float[]{DEFAULT_X_FRACTION, DEFAULT_Y_FRACTION};
    }

    private static void savePosition(View view, ViewGroup parent) {
        if (parent.getWidth() == 0 || parent.getHeight() == 0) {
            return;
        }

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        float x = (params.leftMargin + view.getWidth() / 2f) / parent.getWidth();
        float y = (params.topMargin + view.getHeight() / 2f) / parent.getHeight();

        Settings.BLOCK_AUTHOR_BUTTON_POSITION.save(round(x) + "," + round(y));
        Logger.printDebug(() -> "Block button moved to " + round(x) + "," + round(y));
    }

    private static String round(float value) {
        return String.valueOf(Math.round(value * 1000f) / 1000f);
    }

    private static void onBlockTapped() {
        if (requestInFlight) {
            return;
        }

        VideoAuthor author = CurrentVideoAuthor.get();
        if (author == null || !author.isUsable()) {
            Utils.showToastShort(L10n.t("No account to block on this video"));
            return;
        }

        requestInFlight = true;
        setButtonEnabled(false);

        BlockAuthorService.block(author, (result, message) -> {
            requestInFlight = false;
            setButtonEnabled(true);

            if (result == BlockAuthorService.Result.CONFIRMED) {
                showUndo(author);
            } else if (result == BlockAuthorService.Result.UNCONFIRMED) {
                Utils.showToastLong(L10n.f("Could not confirm block for %1$s", author.label()));
            } else {
                Utils.showToastLong(message == null || message.isEmpty()
                        ? L10n.f("Could not block %1$s", author.label())
                        : L10n.f("Could not block %1$s: %2$s", author.label(), message));
            }
        });
    }

    private static void onLocalHideTapped() {
        VideoAuthor author = CurrentVideoAuthor.get();
        if (author == null || !author.isUsable() || author.stableId() == null
                || author.stableId().isEmpty()) {
            Utils.showToastShort(L10n.t("No account to hide on this video"));
            return;
        }

        String before = Settings.LOCAL_HIDDEN_CREATORS.get();
        String after = app.morphe.extension.tiktok.feedfilter.AdvancedFeedRules.addCreatorEntry(
                before, author.stableId());
        if (after.equals(before)) {
            Utils.showToastShort(L10n.t("This creator is already hidden"));
            return;
        }
        Settings.LOCAL_HIDDEN_CREATORS.save(after);
        showUndoBanner(L10n.f("Hidden %1$s locally", author.label()), () -> {
            Settings.LOCAL_HIDDEN_CREATORS.save(before);
            Utils.showToastShort(L10n.f("Showing %1$s again", author.label()));
        });
    }

    private static void setButtonEnabled(boolean enabled) {
        View button = buttonReference.get();
        if (button != null) {
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    /**
     * Blocking is immediate and has no confirmation, so the undo banner is the safety net
     * for a mis-tap while scrolling.
     */
    private static void showUndo(VideoAuthor author) {
        showUndoBanner(L10n.f("Blocked %1$s", author.label()),
                () -> BlockAuthorService.unblock(author,
                        (result, message) -> Utils.showToastShort(result == BlockAuthorService.Result.CONFIRMED
                                ? L10n.f("Unblocked %1$s", author.label())
                                : result == BlockAuthorService.Result.UNCONFIRMED
                                ? L10n.f("Could not confirm unblock for %1$s", author.label())
                                : L10n.f("Could not unblock %1$s", author.label()))));
    }

    /**
     * Shows a message with an UNDO action for six seconds, over whatever activity is on
     * screen. Falls back to a plain toast when there is nowhere to draw it.
     */
    public static void showUndoBanner(String message, Runnable undoAction) {
        Utils.runOnMainThread(() -> {
            Activity activity = Utils.getActivity();
            ViewGroup root = activity == null || activity.isFinishing() || activity.isDestroyed()
                    ? null : activity.findViewById(android.R.id.content);
            showUndoBanner(root, message, undoAction);
        });
    }

    /**
     * Shows a message with an UNDO action for six seconds inside {@code root}, which
     * should be the window the user is looking at. A banner added to the activity's
     * content root is invisible under a panel that has its own window, which is why the
     * root is a parameter. Falls back to a plain toast when there is nowhere to draw it.
     */
    public static void showUndoBanner(ViewGroup root, String message, Runnable undoAction) {
        Utils.runOnMainThread(() -> {
            try {
                if (root == null) {
                    Utils.showToastShort(message);
                    return;
                }
                Activity activity = Utils.getActivity();
                if (activity == null) {
                    Utils.showToastShort(message);
                    return;
                }

                dismissUndo();

                LinearLayout banner = new LinearLayout(activity);
                banner.setOrientation(LinearLayout.HORIZONTAL);
                banner.setGravity(Gravity.CENTER_VERTICAL);
                banner.setPadding(SettingsUi.dp(activity, 16), SettingsUi.dp(activity, 12), SettingsUi.dp(activity, 16), SettingsUi.dp(activity, 12));

                GradientDrawable background = new GradientDrawable();
                background.setCornerRadius(SettingsUi.dp(activity, 10));
                background.setColor(Color.argb(235, 28, 28, 30));
                banner.setBackground(background);

                TextView label = new TextView(activity);
                label.setText(message);
                label.setTextColor(Color.WHITE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                banner.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

                TextView undo = new TextView(activity);
                undo.setText(L10n.t(activity, "Undo"));
                undo.setContentDescription(L10n.t(activity, "Undo"));
                undo.setTextColor(SettingsUi.OVERLAY_ACCENT);
                undo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                // A banner that dismisses itself is the worst place for a small target.
                undo.setPadding(SettingsUi.dp(activity, 16), SettingsUi.dp(activity, 12), SettingsUi.dp(activity, 16), SettingsUi.dp(activity, 12));
                undo.setMinimumHeight(SettingsUi.dp(activity, 48));
                undo.setMinimumWidth(SettingsUi.dp(activity, 48));
                undo.setGravity(Gravity.CENTER);
                undo.setOnClickListener(view -> {
                    dismissUndo();
                    undoAction.run();
                });
                banner.addView(undo, new LinearLayout.LayoutParams(-2, -2));
                // Nothing announced this banner, so a reader using TalkBack never knew there
                // was a way back at all.
                banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

                // Every window decor is a FrameLayout, so gravity params work in any root.
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                params.setMargins(SettingsUi.dp(activity, 16), 0, SettingsUi.dp(activity, 16), SettingsUi.dp(activity, 96));
                banner.setLayoutParams(params);

                root.addView(banner);
                undoReference = new WeakReference<>(banner);

                // A second banner inside the six seconds replaced the first, and the first
                // banner's dismiss was still queued: it took the new banner away early, with
                // its Undo. Nothing here can cancel a posted runnable, so each dismiss checks
                // whether it is still the one that was scheduled.
                final int token = ++undoGeneration;
                Utils.runOnMainThreadDelayed(() -> {
                    if (token == undoGeneration) dismissUndo();
                }, UNDO_VISIBLE_MS);
            } catch (Throwable ex) {
                Logger.printException(() -> "Could not show the undo banner", ex);
                Utils.showToastShort(message);
            }
        });
    }

    private static void dismissUndo() {
        undoGeneration++;
        View banner = undoReference.get();
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
        undoReference = new WeakReference<>(null);
    }

}

