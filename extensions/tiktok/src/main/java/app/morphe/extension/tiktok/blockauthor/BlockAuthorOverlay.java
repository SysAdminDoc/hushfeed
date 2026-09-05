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
import android.view.View;
import android.view.ViewGroup;
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
 */
public final class BlockAuthorOverlay {
    private static final String BLOCK_GLYPH = "⊘";
    private static final int BUTTON_SIZE_DP = 44;
    private static final int EDGE_MARGIN_DP = 12;
    private static final long UNDO_VISIBLE_MS = 6_000L;

    private static WeakReference<View> buttonReference = new WeakReference<>(null);
    private static WeakReference<View> undoReference = new WeakReference<>(null);

    /** Guards against a double tap blocking, then unblocking, the same account. */
    private static volatile boolean requestInFlight;

    private BlockAuthorOverlay() {
    }

    static void onAuthorChanged(VideoAuthor author) {
        if (!Settings.BLOCK_AUTHOR_BUTTON.get()) {
            Utils.runOnMainThread(BlockAuthorOverlay::detach);
            return;
        }
        Utils.runOnMainThread(() -> attach(author));
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

            View button = createButton(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(activity, BUTTON_SIZE_DP),
                    dp(activity, BUTTON_SIZE_DP),
                    Gravity.START | Gravity.CENTER_VERTICAL
            );
            params.leftMargin = dp(activity, EDGE_MARGIN_DP);
            button.setLayoutParams(params);

            root.addView(button);
            buttonReference = new WeakReference<>(button);

            Logger.printDebug(() -> "Block button attached for " + author.label());
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not attach the block button", ex);
        }
    }

    private static void detach() {
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

        button.setOnClickListener(view -> onBlockTapped());
        return button;
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
