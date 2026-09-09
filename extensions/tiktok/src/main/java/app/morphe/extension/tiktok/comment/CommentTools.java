/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.comment;

import android.graphics.PorterDuff;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorService;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.blockauthor.VideoAuthor;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.L10n;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Comment list tools: a keyword filter on loaded comments, and TikTok's thumbs down control
 * on each comment repurposed to block the commenter in one tap.
 *
 * Both entry points are called from the same places the comment translation patch hooks:
 * {@code BaseCommentCell} binding a cell, and the comment list response being handled.
 * The {@code Comment} and {@code CommentItemList} models keep their real names on TikTok
 * 46.2.3 ({@code getText}, {@code getUser}, {@code getCid}, {@code getReplyComments}, and
 * the public {@code items} list), so nothing here depends on an obfuscated name.
 *
 * The thumbs down is a RelativeLayout ({@code jlk}) holding an icon ({@code m3b}) at the
 * right end of the comment's action row; both ids were read off a live comment panel. TikTok
 * drives it with a touch listener that can be installed only once per view. A native install
 * hook keeps that listener in a view-owned wrapper, and the posted cell bind switches the
 * wrapper to blocking while the setting is enabled. The icon stays; a tap blocks the commenter
 * (a second tap unblocks), the row dims and the icon tints while the account is blocked, and
 * an undo banner is drawn in the window the comments live in, because the panel is not
 * always in the activity's window and anything added to the activity's content root then
 * sits underneath it.
 */
public final class CommentTools {
    /**
     * The brand animation TikTok plays over the comment sheet when a comment matches an
     * advertiser's trigger. Called from the trigger itself, so returning true skips it.
     */
    public static boolean shouldHideCommentEgg() {
        return Settings.HIDE_COMMENT_EGGS.get();
    }

    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    private static final String DISLIKE_BUTTON_ID = "jlk";

    /** One log line for a cell with no thumbs down, not a verdict on the build. */
    private static boolean warnedNoDislikeControl;
    private static final String DISLIKE_ICON_ID = "m3b";
    /**
     * Faded enough to read as blocked, still readable. At 0.35 the comment text dropped to about
     * 3:1 on the sheet, which is below the floor for text of that size.
     */
    private static final float BLOCKED_ROW_ALPHA = 0.55f;
    private static final int BLOCKED_TINT = SettingsUi.OVERLAY_ACCENT;

    /** Comment model bound to each cell view. */
    private static final WeakHashMap<View, Object> CELL_COMMENTS = new WeakHashMap<>();

    /**
     * The view owns its wrapper, native listener and saved state. Weak values matter too:
     * TikTok's listener owns its row, so a strong map value would retain its own weak key.
     */
    private static final WeakHashMap<View, WeakReference<ControlTouchListener>> CONTROL_TOUCHES =
            new WeakHashMap<>();

    /** The values the takeover overwrites. One instance is shared by a row's button and icon. */
    private static final class ControlState {
        final View button;
        final View icon;
        final View cell;
        final boolean buttonClickable;
        final CharSequence description;
        final CharSequence stateDescription;
        final int iconImportance;
        final android.graphics.ColorFilter iconFilter;
        final float cellAlpha;

        ControlState(View button, View icon, View cell) {
            this.button = button;
            this.icon = icon;
            this.cell = cell;
            this.buttonClickable = button != null && button.isClickable();
            this.description = button == null ? null : button.getContentDescription();
            this.stateDescription = button != null && android.os.Build.VERSION.SDK_INT >= 30
                    ? button.getStateDescription() : null;
            this.iconImportance = icon == null
                    ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO : icon.getImportantForAccessibility();
            this.iconFilter = icon instanceof ImageView ? ((ImageView) icon).getColorFilter() : null;
            this.cellAlpha = cell == null ? 1f : cell.getAlpha();
        }
    }

    /** Accounts blocked this session, by uid, so a recycled cell shows the right state. */
    private static final Set<String> BLOCKED_UIDS = Collections.synchronizedSet(new HashSet<>());

    private static final ResourceIdCache RESOURCE_IDS = new ResourceIdCache();
    private static final DislikeTouchListener DISLIKE_TOUCH = new DislikeTouchListener();

    private static volatile boolean blockInFlight;

    private CommentTools() {
    }

    /**
     * Called as a comment cell is bound. {@code manager} is the cell's state holder, whose
     * fields include the bound {@code Comment}.
     */
    public static void registerCommentCell(View itemView, Object manager) {
        if (itemView == null || manager == null) {
            return;
        }
        // Before the switches below: a sheet is open whichever of the comment tools are on,
        // and this is the only callback that says so.
        app.morphe.extension.tiktok.playback.PausePlayback.onCommentCellBound(itemView);

        boolean block = Settings.BLOCK_FROM_COMMENT.get();
        if (!block) {
            // The takeover used to be one way. A cell sitting in the RecyclerView's pool kept it
            // after the setting was turned off, so the thumbs down went on blocking and a screen
            // reader went on reading "Block this commenter", until the pool emptied, which is
            // not something a reader can see or bring about. Every pooled cell is rebound before
            // it is shown again, so handing the control back on a bind is self healing and needs
            // no record of which cells were ever taken over.
            synchronized (CELL_COMMENTS) {
                CELL_COMMENTS.remove(itemView);
            }
            itemView.post(() -> releaseDislike(itemView));
        }
        if (!block && !CommentSearch.enabled()) {
            return;
        }

        try {
            Object comment = findComment(manager);
            if (comment == null) {
                Logger.printDebug(() -> "Comment cell bound but no comment found on " + manager.getClass().getName());
                return;
            }

            CommentSearch.onCellBound(itemView, comment);
            if (!block) {
                return;
            }

            Object previous;
            synchronized (CELL_COMMENTS) {
                previous = CELL_COMMENTS.put(itemView, comment);
            }
            // A different comment in the same row, rather than the same one bound again for a
            // changed like count: only the first has to drop a press taken before the swap.
            boolean holdsAnotherComment = previous != null && previous != comment;

            // TikTok wires the thumbs down during this same bind, so the takeover runs once
            // the bind has returned. For a cell that is not attached yet, View.post runs the
            // work on attach, which is still after the bind.
            itemView.post(() -> takeOverDislike(itemView, holdsAnotherComment));
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not register a comment cell", ex);
        }
    }

    /**
     * Called with the {@code CommentItemList} once TikTok has parsed a page of comments,
     * before they are shown. Matching comments are removed from the list in place.
     */
    public static void onCommentListLoaded(Object commentItemList) {
        boolean byWord = Settings.COMMENT_KEYWORD_FILTER.get();
        boolean media = Settings.HIDE_COMMENT_MEDIA.get();
        if ((!byWord && !media) || commentItemList == null) {
            return;
        }

        try {
            List<app.morphe.extension.tiktok.feedfilter.KeywordRules.Rule> keywords = byWord
                    ? app.morphe.extension.tiktok.feedfilter.KeywordRules.parse(
                            Settings.COMMENT_BLOCKED_KEYWORDS.get())
                    : List.of();
            List<String> users = byWord ? entries(Settings.COMMENT_BLOCKED_USERS.get()) : List.of();
            if (keywords.isEmpty() && users.isEmpty() && !media) {
                return;
            }

            Object itemsObject = Reflect.readField(commentItemList, "items");
            if (!(itemsObject instanceof List)) {
                return;
            }

            // Looked up once for the page, not once per comment: two reflective calls into
            // TikTok's account service.
            String self = media ? signedInUserId() : null;

            int removed = filterComments((List<?>) itemsObject, keywords, users, media, self);
            if (removed > 0) {
                final int count = removed;
                Logger.printDebug(() -> "Comment filter removed " + count + " comment(s)");
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Comment filter failed", ex);
        }
    }

    // ---- thumbs down takeover ----------------------------------------------------------

    private static void takeOverDislike(View cell, boolean holdsAnotherComment) {
        try {
            View button = cell.findViewById(identifier(cell, DISLIKE_BUTTON_ID));
            if (button == null) {
                // Deliberately not a hook status miss. This runs per comment cell, and a row
                // variant without the control, or one not fully inflated when the posted
                // runnable lands, would otherwise mark the whole build broken for good.
                if (!warnedNoDislikeControl) {
                    warnedNoDislikeControl = true;
                    Logger.printInfo(() -> "Comment thumbs down control '" + DISLIKE_BUTTON_ID
                            + "' not found in this comment cell");
                }
                return;
            }

            // Switches the view-owned listener to blocking; the icon gets one too so a touch
            // that lands on it never reaches TikTok's handling either.
            // A press taken while this row held a different comment must not be released onto
            // the account that just arrived in it.
            View icon = cell.findViewById(identifier(cell, DISLIKE_ICON_ID));
            rememberBeforeTakeover(cell, button, icon);
            wireBlockControl(button, icon);
            if (holdsAnotherComment) {
                // Forget the old comment, but keep ownership of its unfinished press even
                // if the switch is turned off before the finger comes up.
                control(button).discardUntilRelease |= DISLIKE_TOUCH.forget(button);
                if (icon != null) control(icon).discardUntilRelease |= DISLIKE_TOUCH.forget(icon);
            }

            applyBlockedState(cell);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not take over the comment thumbs down", ex);
        }
    }

    /** Called instead of the verified native thumbs-down setOnTouchListener call. */
    public static void setDislikeTouchListener(View view, View.OnTouchListener nativeListener) {
        if (view != null) control(view).nativeListener = nativeListener;
    }

    private static ControlTouchListener existingControl(View view) {
        synchronized (CONTROL_TOUCHES) {
            WeakReference<ControlTouchListener> reference = CONTROL_TOUCHES.get(view);
            return reference == null ? null : reference.get();
        }
    }

    private static ControlTouchListener control(View view) {
        synchronized (CONTROL_TOUCHES) {
            ControlTouchListener listener = existingControl(view);
            if (listener == null) {
                listener = new ControlTouchListener();
                CONTROL_TOUCHES.put(view, new WeakReference<>(listener));
            }
            view.setOnTouchListener(listener);
            return listener;
        }
    }

    /** The native handler stays strongly owned by its view even while the takeover is off. */
    private static final class ControlTouchListener implements View.OnTouchListener {
        View.OnTouchListener nativeListener;
        ControlState state;
        boolean blocking;
        boolean discardUntilRelease;

        void handBack(View view) {
            discardUntilRelease |= DISLIKE_TOUCH.forget(view);
            blocking = false;
            state = null;
        }

        @Override public boolean onTouch(View view, MotionEvent event) {
            if (blocking && !Settings.BLOCK_FROM_COMMENT.get()) releaseDislike(view);
            if (discardUntilRelease) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    discardUntilRelease = false;
                } else {
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        discardUntilRelease = false;
                    }
                    return true;
                }
            }
            if (blocking && Settings.BLOCK_FROM_COMMENT.get()) {
                return DISLIKE_TOUCH.onTouch(view, event);
            }
            return nativeListener != null && nativeListener.onTouch(view, event);
        }
    }

    /** Records what the takeover is about to overwrite, the first time it touches a control. */
    private static void rememberBeforeTakeover(View cell, View button, View icon) {
        if (button == null) return;
        ControlTouchListener listener = control(button);
        if (listener.state != null) return;
        ControlState state = new ControlState(button, icon, cell);
        listener.state = state;
        if (icon != null) control(icon).state = state;
    }

    /** Hands back whichever of a row's controls this took over, and only those. */
    private static void releaseDislike(View touched) {
        if (touched == null) return;
        try {
            ControlTouchListener listener = existingControl(touched);
            if (listener == null || listener.state == null) {
                // A bind supplies itemView; a touch supplies either indexed control.
                View button = touched.findViewById(identifier(touched, DISLIKE_BUTTON_ID));
                listener = existingControl(button);
            }
            if (listener != null && listener.state != null) unwireBlockControl(listener.state);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not hand the comment thumbs down back", ex);
        }
    }

    static void unwireBlockControl(ControlState state) {
        View button = state.button;
        if (button != null) {
            ControlTouchListener listener = existingControl(button);
            if (listener != null) listener.handBack(button);
            // The native jlk control uses touch handling; this click belongs to the takeover.
            button.setOnClickListener(null);
            button.setClickable(state.buttonClickable);
            button.setContentDescription(state.description);
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                button.setStateDescription(state.stateDescription);
            }
        }
        View icon = state.icon;
        if (icon != null) {
            ControlTouchListener listener = existingControl(icon);
            if (listener != null) listener.handBack(icon);
            icon.setImportantForAccessibility(state.iconImportance);
            if (icon instanceof ImageView) ((ImageView) icon).setColorFilter(state.iconFilter);
        }
        View cell = state.cell;
        if (cell != null && cell.getAlpha() != state.cellAlpha) cell.setAlpha(state.cellAlpha);
    }

    /**
     * Swallows every touch on the thumbs down so TikTok's dislike never fires, and turns a
     * clean tap into a block. A drag is left to the list (the RecyclerView intercepts it
     * before the control sees more than the first events).
     */
    private static final class DislikeTouchListener implements View.OnTouchListener {
        /** Where one control's press started, and whether it has since become a drag. */
        private static final class Gesture {
            final float downX;
            final float downY;
            boolean moved;

            Gesture(float downX, float downY) {
                this.downX = downX;
                this.downY = downY;
            }
        }

        /**
         * One press per control. The listener is shared by every comment on screen, so keeping
         * the press on the listener let a second finger, or a cell rebound between the press and
         * the release, decide what a release somewhere else did. A release with no press of its
         * own now does nothing rather than blocking whoever the other press was aimed at.
         */
        private final WeakHashMap<View, Gesture> gestures = new WeakHashMap<>();

        boolean forget(View view) {
            if (view == null) return false;
            synchronized (gestures) {
                return gestures.remove(view) != null;
            }
        }

        // Touches arrive on the main thread, but the cell maps in this class are all guarded, and
        // a WeakHashMap corrupts rather than fails if that ever stops being true.
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            boolean tapped = false;
            synchronized (gestures) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        gestures.put(view, new Gesture(event.getX(), event.getY()));
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        Gesture gesture = gestures.get(view);
                        if (gesture != null && !gesture.moved) {
                            int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                            gesture.moved = Math.abs(event.getX() - gesture.downX) > slop
                                    || Math.abs(event.getY() - gesture.downY) > slop;
                        }
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        gestures.remove(view);
                        break;
                    case MotionEvent.ACTION_UP: {
                        Gesture gesture = gestures.remove(view);
                        tapped = gesture != null && !gesture.moved;
                        break;
                    }
                    default:
                        break;
                }
            }

            // Outside the lock: blocking an account reaches well beyond this listener.
            if (tapped) {
                onDislikeTapped(view);
            }
            return true;
        }
    }

    /**
     * Takes the control over for both a finger and an accessibility service. TalkBack and Switch
     * Access activate a control with {@code performClick()}, which produces no MotionEvents at
     * all, so a touch listener on its own left them reaching TikTok's dislike instead of the
     * block. The touch listener always consumes, so a finger never reaches the click listener.
     */
    static void wireBlockControl(View button, View icon) {
        if (button == null) return;
        control(button).blocking = true;
        button.setOnClickListener(CommentTools::onDislikeTapped);
        if (icon != null) {
            control(icon).blocking = true;
            // One target for the row rather than two, so the label and the state are in one
            // place and a screen reader does not read the same control twice.
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /**
     * What the control is for and what it did. The label still said "dislike" for a control that
     * blocks, and a faded row was the only sign an account was blocked, which a screen reader
     * cannot see at all.
     */
    static void describeBlockControl(View button, boolean blocked) {
        if (button == null) return;
        button.setContentDescription(L10n.t(blocked
                ? "Unblock this commenter" : "Block this commenter"));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            button.setStateDescription(L10n.t(blocked ? "Blocked" : "Not blocked"));
        }
    }

    private static void onDislikeTapped(View touched) {
        if (!Settings.BLOCK_FROM_COMMENT.get()) {
            // A listener left on a cell from before the setting was turned off. One tap spent
            // handing the control back is the right answer; blocking someone the reader did not
            // choose to block is not.
            releaseDislike(touched);
            return;
        }
        try {
            View cell = cellOf(touched);
            if (cell == null) {
                Utils.showToastShort(L10n.t("Could not read who posted this comment"));
                return;
            }
            toggleBlock(cell);
        } catch (Throwable ex) {
            Logger.printException(() -> "Comment block tap failed", ex);
        }
    }

    /** The registered cell is the nearest ancestor of the control that was bound to a comment. */
    private static View cellOf(View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 12; depth++) {
            synchronized (CELL_COMMENTS) {
                if (CELL_COMMENTS.containsKey(current)) {
                    return current;
                }
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    /**
     * Every cell still registered, which is every comment on screen plus any pooled rows the
     * map has not let go of yet. Refreshing a pooled row costs nothing and it is bound again
     * before it is shown. Not only the one that was tapped: a thread usually holds several
     * comments by the same account, and refreshing one left the others reading "Block this
     * commenter, not blocked" for an account that is already blocked. Acting on that label did
     * the opposite of what it said, because the toggle reads the blocked set rather than the
     * label, so a screen reader user was told to block and unblocked instead.
     */
    private static void applyBlockedEverywhere() {
        java.util.List<View> cells;
        synchronized (CELL_COMMENTS) {
            cells = new java.util.ArrayList<>(CELL_COMMENTS.keySet());
        }
        for (View cell : cells) applyBlockedState(cell);
    }

    private static void applyBlockedState(View cell) {
        Object comment;
        synchronized (CELL_COMMENTS) {
            comment = CELL_COMMENTS.get(cell);
        }
        boolean blocked = comment != null && isBlocked(comment);

        float alpha = blocked ? BLOCKED_ROW_ALPHA : 1f;
        if (cell.getAlpha() != alpha) {
            cell.setAlpha(alpha);
        }

        // The label still said "dislike" for a control that blocks, and a faded row was the
        // only sign an account was blocked, which a screen reader cannot see at all.
        describeBlockControl(cell.findViewById(identifier(cell, DISLIKE_BUTTON_ID)), blocked);

        View icon = cell.findViewById(identifier(cell, DISLIKE_ICON_ID));
        if (icon instanceof ImageView) {
            ImageView image = (ImageView) icon;
            if (blocked) {
                image.setColorFilter(BLOCKED_TINT, PorterDuff.Mode.SRC_IN);
            } else {
                image.clearColorFilter();
            }
        }
    }

    private static boolean isBlocked(Object comment) {
        String uid = uidOf(comment);
        return uid != null && BLOCKED_UIDS.contains(uid);
    }

    private static String uidOf(Object comment) {
        Object user = Reflect.property(comment, "getUser", "user");
        return Reflect.string(user, "getUid", "uid");
    }

    // ---- blocking ----------------------------------------------------------------------

    private static void toggleBlock(View cell) {
        if (blockInFlight) {
            return;
        }

        Object comment;
        synchronized (CELL_COMMENTS) {
            comment = CELL_COMMENTS.get(cell);
        }
        Object user = comment == null ? null : Reflect.property(comment, "getUser", "user");
        if (user == null) {
            Utils.showToastShort(L10n.t("Could not read who posted this comment"));
            return;
        }

        VideoAuthor author = new VideoAuthor(
                Reflect.string(user, "getUid", "uid"),
                Reflect.string(user, "getSecUid", "secUid"),
                Reflect.firstNonBlank(
                        Reflect.string(user, "getUniqueId", "uniqueId"),
                        Reflect.string(user, "getNickname", "nickname")),
                Reflect.string(comment, "getCid", "cid"));
        if (!author.isUsable()) {
            Utils.showToastShort(L10n.t("Could not read who posted this comment"));
            return;
        }

        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (author.uid != null && BLOCKED_UIDS.contains(author.uid)) {
            unblock(cell, author);
        } else {
            block(cell, author);
        }
    }

    private static void block(View cell, VideoAuthor author) {
        blockInFlight = true;
        BlockAuthorService.block(author, (result, message) -> {
            blockInFlight = false;
            if (result != BlockAuthorService.Result.CONFIRMED) {
                if (result == BlockAuthorService.Result.UNCONFIRMED) {
                    Utils.showToastLong(L10n.f("Could not confirm block for %1$s", author.label()));
                    return;
                }
                Utils.showToastLong(message == null || message.isEmpty()
                        ? L10n.f("Could not block %1$s", author.label())
                        : L10n.f("Could not block %1$s: %2$s", author.label(), message));
                return;
            }

            if (author.uid != null) {
                BLOCKED_UIDS.add(author.uid);
            }
            // The cell reads its current comment, so a recycled row is never mis-styled.
            applyBlockedEverywhere();

            View root = cell.getRootView();
            BlockAuthorOverlay.showUndoBanner(root instanceof ViewGroup ? (ViewGroup) root : null,
                    L10n.f("Blocked %1$s", author.label()), () -> {
                        BlockAuthorService.unblock(author, (undoResult, undoMessage) -> {
                            if (undoResult == BlockAuthorService.Result.CONFIRMED) {
                                if (author.uid != null) {
                                    BLOCKED_UIDS.remove(author.uid);
                                }
                                applyBlockedEverywhere();
                            }
                            Utils.showToastShort(undoResult == BlockAuthorService.Result.CONFIRMED
                                        ? L10n.f("Unblocked %1$s", author.label())
                                        : undoResult == BlockAuthorService.Result.UNCONFIRMED
                                        ? L10n.f("Could not confirm unblock for %1$s", author.label())
                                        : L10n.f("Could not unblock %1$s", author.label()));
                    });
                    });
        });
    }

    private static void unblock(View cell, VideoAuthor author) {
        blockInFlight = true;
        BlockAuthorService.unblock(author, (result, message) -> {
            blockInFlight = false;
            if (result != BlockAuthorService.Result.CONFIRMED) {
                if (result == BlockAuthorService.Result.UNCONFIRMED) {
                    Utils.showToastLong(L10n.f("Could not confirm unblock for %1$s", author.label()));
                    return;
                }
                Utils.showToastLong(message == null || message.isEmpty()
                        ? L10n.f("Could not unblock %1$s", author.label())
                        : L10n.f("Could not unblock %1$s: %2$s", author.label(), message));
                return;
            }
            if (author.uid != null) {
                BLOCKED_UIDS.remove(author.uid);
            }
            applyBlockedEverywhere();
            Utils.showToastShort(L10n.f("Unblocked %1$s", author.label()));
        });
    }

    // ---- keyword filter ----------------------------------------------------------------

    private static int filterComments(List<?> comments,
            List<app.morphe.extension.tiktok.feedfilter.KeywordRules.Rule> keywords,
            List<String> users, boolean media, String self) {
        int removed = 0;
        Iterator<?> iterator = comments.iterator();
        while (iterator.hasNext()) {
            Object comment = iterator.next();
            if (comment == null) {
                continue;
            }

            if (matches(comment, keywords, users, media, self)) {
                try {
                    iterator.remove();
                    removed++;
                    continue;
                } catch (UnsupportedOperationException ex) {
                    Logger.printInfo(() -> "Comment list is immutable; the keyword filter cannot remove from it");
                    return removed;
                }
            }

            Object replies = Reflect.property(comment, "getReplyComments", "replyComments");
            if (replies instanceof List) {
                removed += filterComments((List<?>) replies, keywords, users, media, self);
            }
        }
        return removed;
    }

    private static boolean matches(Object comment,
            List<app.morphe.extension.tiktok.feedfilter.KeywordRules.Rule> keywords,
            List<String> users, boolean media, String self) {
        // Your own stickers and images stay. Hiding comments with pictures is about what other
        // people post, and having your own disappear from a thread you are in reads as the
        // comment having failed to send.
        if (media && hasMedia(comment) && !isOwnComment(comment, self)) {
            return true;
        }

        String text = Reflect.string(comment, "getText", "text");
        // Plain phrases as before, plus "a" & "b" and "a" !& "b".
        if (app.morphe.extension.tiktok.feedfilter.KeywordRules.anyMatches(keywords, text)) {
            return true;
        }

        if (!users.isEmpty()) {
            Object user = Reflect.property(comment, "getUser", "user");
            String uniqueId = Reflect.string(user, "getUniqueId", "uniqueId");
            String nickname = Reflect.string(user, "getNickname", "nickname");
            for (String blocked : users) {
                String wanted = blocked.startsWith("@") ? blocked.substring(1) : blocked;
                if (wanted.equalsIgnoreCase(uniqueId) || wanted.equalsIgnoreCase(nickname)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a comment carries a picture rather than words. TikTok keeps three shapes on
     * the model: the attached images, a sticker struct, and the post items a text-on-image
     * comment is built from.
     */
    /**
     * Whether this comment was written by the account that is signed in.
     *
     * <p>Answers false when nobody is signed in, when the account service is not reachable, or
     * when either side has no id, so a comment is only ever kept by a positive match.
     */
    private static boolean isOwnComment(Object comment, String self) {
        if (self == null || self.isEmpty()) {
            return false;
        }
        Object user = Reflect.property(comment, "getUser", "user");
        if (user == null) {
            return false;
        }
        String uid = Reflect.string(user, "getUid", "uid");
        if (self.equals(uid)) {
            return true;
        }
        String secUid = Reflect.string(user, "getSecUid", "secUid");
        return secUid != null && !secUid.isEmpty() && self.equals(secUid);
    }

    /**
     * The signed in account's id, or null when there is not one.
     *
     * <p>Both ids are worth having: a comment carries the plain uid and the sec uid, and which
     * of them is filled in varies by where the list came from.
     */
    private static String signedInUserId() {
        if (signedInUserIdForTests != null) {
            return signedInUserIdForTests.isEmpty() ? null : signedInUserIdForTests;
        }
        try {
            Class<?> serviceManagerClass = Class.forName(SERVICE_MANAGER_CLASS);
            Object serviceManager = serviceManagerClass.getMethod("get").invoke(null);
            Class<?> accountServiceClass = Class.forName(ACCOUNT_USER_SERVICE_CLASS);
            Object accountService = serviceManagerClass
                    .getMethod("getService", Class.class)
                    .invoke(serviceManager, accountServiceClass);
            if (accountService == null
                    || !Boolean.TRUE.equals(accountServiceClass.getMethod("isLogin").invoke(accountService))) {
                return null;
            }
            Object id = accountServiceClass.getMethod("getCurUserId").invoke(accountService);
            return id instanceof String && !((String) id).isEmpty() ? (String) id : null;
        } catch (Throwable ignored) {
            // Not signed in, or a build where the account service moved. Either way the filter
            // behaves as it did before: it hides every comment carrying a picture.
            return null;
        }
    }

    /** So a test can stand in for the account service, which needs the host to be running. */
    static String signedInUserIdForTests;

    private static final String SERVICE_MANAGER_CLASS =
            "com.ss.android.ugc.aweme.framework.services.ServiceManager";
    private static final String ACCOUNT_USER_SERVICE_CLASS =
            "com.ss.android.ugc.aweme.IAccountUserService";

    private static boolean hasMedia(Object comment) {
        Object images = Reflect.property(comment, "getImageList", "imageList");
        if (images instanceof List && !((List<?>) images).isEmpty()) {
            return true;
        }
        Object posts = Reflect.property(comment, "getTextImageCommentPostItemList", "textImageCommentPostItemList");
        if (posts instanceof List && !((List<?>) posts).isEmpty()) {
            return true;
        }
        return Reflect.property(comment, "getStickerStruct", "stickerStruct") != null;
    }

    // ---- model access ------------------------------------------------------------------

    /** The bound comment is the manager field whose value answers to {@code getCid}. */
    private static Object findComment(Object manager) throws IllegalAccessException {
        Class<?> type = manager.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(manager);
                if (value != null && Reflect.string(value, "getCid", "cid") != null
                        && hasMethod(value.getClass(), "getUser")) {
                    return value;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean hasMethod(Class<?> type, String name) {
        while (type != null && type != Object.class) {
            try {
                type.getDeclaredMethod(name);
                return true;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        return false;
    }

    /** Resolves a comment view id, saying so once when this build does not have it. */
    private static int identifier(View view, String name) {
        int id = RESOURCE_IDS.resolve(view == null ? null : view.getResources(), APP_PACKAGE, name, false);
        if (id == 0) HookStatus.missingViewId("comments", name);
        else HookStatus.bound("comments", name);
        return id;
    }

    private static List<String> entries(String stored) {
        List<String> entries = new ArrayList<>();
        if (stored == null) {
            return entries;
        }
        for (String part : stored.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }
}
