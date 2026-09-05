/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.blockauthor;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import app.morphe.extension.tiktok.settings.Settings;

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
    private static final String BLOCK_GLYPH = "⊘";
    private static final int BUTTON_SIZE_DP = 44;
    private static final long UNDO_VISIBLE_MS = 6_000L;

    /** Right edge, just above TikTok's own action rail. */
    private static final float DEFAULT_X_FRACTION = 0.91f;
    private static final float DEFAULT_Y_FRACTION = 0.40f;

    private static WeakReference<View> buttonReference = new WeakReference<>(null);
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

    static void onAuthorChanged(VideoAuthor author) {
        if (!Settings.BLOCK_AUTHOR_BUTTON.get()) {
            Utils.runOnMainThread(BlockAuthorOverlay::detach);
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
        int wanted = visible ? View.VISIBLE : View.GONE;
        if (button.getVisibility() != wanted) {
            button.setVisibility(wanted);
            if (!visible) {
                dismissUndo();
            }
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
        setFeedVisible(FeedVisibility.isOnFeed(activity));
    }

    private static void attach(VideoAuthor author) {
        try {
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }

            View existing = buttonReference.get();
            if (existing != null && existing.getParent() == root) {
                existing.setVisibility(View.VISIBLE);
                return;
            }

            final View button = createButton(activity);
            final int size = dp(activity, BUTTON_SIZE_DP);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    size, size, Gravity.TOP | Gravity.START);
            button.setLayoutParams(params);

            root.addView(button);
            buttonReference = new WeakReference<>(button);

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
        dismissUndo();
    }

    private static View createButton(Activity activity) {
        TextView button = new TextView(activity);
        button.setText(BLOCK_GLYPH);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription("Block this account");

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(140, 0, 0, 0));
        background.setStroke(dp(activity, 1), Color.argb(90, 255, 255, 255));
        button.setBackground(background);

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
            Utils.showToastShort("Drag to move, release to place");
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
            Utils.showToastShort("No account to block on this video");
            return;
        }

        requestInFlight = true;
        setButtonEnabled(false);

        BlockAuthorService.block(author, (success, message) -> {
            requestInFlight = false;
            setButtonEnabled(true);

            if (success) {
                showUndo(author);
            } else {
                Utils.showToastLong("Could not block " + author.label()
                        + (message == null ? "" : ": " + message));
            }
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
        try {
            Activity activity = Utils.getActivity();
            if (activity == null || activity.isFinishing()) {
                Utils.showToastShort("Blocked " + author.label());
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Utils.showToastShort("Blocked " + author.label());
                return;
            }

            dismissUndo();

            LinearLayout banner = new LinearLayout(activity);
            banner.setOrientation(LinearLayout.HORIZONTAL);
            banner.setGravity(Gravity.CENTER_VERTICAL);
            banner.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));

            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(activity, 10));
            background.setColor(Color.argb(235, 28, 28, 30));
            banner.setBackground(background);

            TextView label = new TextView(activity);
            label.setText("Blocked " + author.label());
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            banner.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView undo = new TextView(activity);
            undo.setText("UNDO");
            undo.setTextColor(Color.rgb(254, 44, 85));
            undo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            undo.setPadding(dp(activity, 12), 0, 0, 0);
            undo.setOnClickListener(view -> {
                dismissUndo();
                BlockAuthorService.unblock(author, (success, message) -> Utils.showToastShort(
                        success
                                ? "Unblocked " + author.label()
                                : "Could not unblock " + author.label()));
            });
            banner.addView(undo, new LinearLayout.LayoutParams(-2, -2));

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.setMargins(dp(activity, 16), 0, dp(activity, 16), dp(activity, 96));
            banner.setLayoutParams(params);

            root.addView(banner);
            undoReference = new WeakReference<>(banner);

            Utils.runOnMainThreadDelayed(BlockAuthorOverlay::dismissUndo, UNDO_VISIBLE_MS);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not show the undo banner", ex);
            Utils.showToastShort("Blocked " + author.label());
        }
    }

    private static void dismissUndo() {
        View banner = undoReference.get();
        if (banner != null && banner.getParent() instanceof ViewGroup) {
            ((ViewGroup) banner.getParent()).removeView(banner);
        }
        undoReference = new WeakReference<>(null);
    }

    private static int dp(Activity activity, int value) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
