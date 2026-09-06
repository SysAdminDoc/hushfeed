/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.comment;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorService;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.blockauthor.VideoAuthor;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * drives it with a touch listener installed once per view, the first time the cell binds, so
 * this class installs its own touch listener after the bind has returned, which replaces
 * TikTok's and survives every later rebind. The icon stays; a tap now blocks the commenter
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
    private static final String DISLIKE_ICON_ID = "m3b";
    private static final float BLOCKED_ROW_ALPHA = 0.35f;
    private static final int BLOCKED_TINT = Color.rgb(254, 44, 85);

    /** Comment model bound to each cell view. */
    private static final WeakHashMap<View, Object> CELL_COMMENTS = new WeakHashMap<>();

    /** Accounts blocked this session, by uid, so a recycled cell shows the right state. */
    private static final Set<String> BLOCKED_UIDS = Collections.synchronizedSet(new HashSet<>());

    private static final Map<String, Integer> RESOLVED_IDS = new HashMap<>();
    private static final DislikeTouchListener DISLIKE_TOUCH = new DislikeTouchListener();

    private static volatile boolean blockInFlight;
    private static boolean warnedNoDislikeControl;

    private CommentTools() {
    }

    /**
     * Called as a comment cell is bound. {@code manager} is the cell's state holder, whose
     * fields include the bound {@code Comment}.
     */
    public static void registerCommentCell(View itemView, Object manager) {
        boolean block = Settings.BLOCK_FROM_COMMENT.get();
        if ((!block && !CommentSearch.enabled()) || itemView == null || manager == null) {
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

            synchronized (CELL_COMMENTS) {
                CELL_COMMENTS.put(itemView, comment);
            }

            // TikTok wires the thumbs down during this same bind, so the takeover runs once
            // the bind has returned. For a cell that is not attached yet, View.post runs the
            // work on attach, which is still after the bind.
            itemView.post(() -> takeOverDislike(itemView));
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
            List<String> keywords = byWord ? entries(Settings.COMMENT_BLOCKED_KEYWORDS.get()) : List.of();
            List<String> users = byWord ? entries(Settings.COMMENT_BLOCKED_USERS.get()) : List.of();
            if (keywords.isEmpty() && users.isEmpty() && !media) {
                return;
            }

            Object itemsObject = Reflect.readField(commentItemList, "items");
            if (!(itemsObject instanceof List)) {
                return;
            }

            int removed = filterComments((List<?>) itemsObject, keywords, users, media);
            if (removed > 0) {
                final int count = removed;
                Logger.printDebug(() -> "Comment filter removed " + count + " comment(s)");
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Comment filter failed", ex);
        }
    }

    // ---- thumbs down takeover ----------------------------------------------------------

    private static void takeOverDislike(View cell) {
        try {
            View button = cell.findViewById(identifier(cell, DISLIKE_BUTTON_ID));
            if (button == null) {
                if (!warnedNoDislikeControl) {
                    warnedNoDislikeControl = true;
                    Logger.printInfo(() -> "Comment thumbs down control '" + DISLIKE_BUTTON_ID
                            + "' not found in this TikTok build");
                }
                return;
            }

            // Replaces TikTok's listener on the control; the icon gets one too so a touch
            // that lands on it never reaches TikTok's handling either.
            button.setOnTouchListener(DISLIKE_TOUCH);
            View icon = cell.findViewById(identifier(cell, DISLIKE_ICON_ID));
            if (icon != null) {
                icon.setOnTouchListener(DISLIKE_TOUCH);
            }

            applyBlockedState(cell);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not take over the comment thumbs down", ex);
        }
    }

    /**
     * Swallows every touch on the thumbs down so TikTok's dislike never fires, and turns a
     * clean tap into a block. A drag is left to the list (the RecyclerView intercepts it
     * before the control sees more than the first events).
     */
    private static final class DislikeTouchListener implements View.OnTouchListener {
        private float downX;
        private float downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved) {
                        int slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                        moved = Math.abs(event.getX() - downX) > slop
                                || Math.abs(event.getY() - downY) > slop;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    moved = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        onDislikeTapped(view);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private static void onDislikeTapped(View touched) {
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
        BlockAuthorService.block(author, (success, message) -> {
            blockInFlight = false;
            if (!success) {
                Utils.showToastLong(message == null || message.isEmpty()
                        ? L10n.f("Could not block %1$s", author.label())
                        : L10n.f("Could not block %1$s: %2$s", author.label(), message));
                return;
            }

            if (author.uid != null) {
                BLOCKED_UIDS.add(author.uid);
            }
            // The cell reads its current comment, so a recycled row is never mis-styled.
            applyBlockedState(cell);

            View root = cell.getRootView();
            BlockAuthorOverlay.showUndoBanner(root instanceof ViewGroup ? (ViewGroup) root : null,
                    L10n.f("Blocked %1$s", author.label()), () -> {
                        if (author.uid != null) {
                            BLOCKED_UIDS.remove(author.uid);
                        }
                        applyBlockedState(cell);
                        BlockAuthorService.unblock(author, (undone, ignored) -> Utils.showToastShort(
                                undone
                                        ? L10n.f("Unblocked %1$s", author.label())
                                        : L10n.f("Could not unblock %1$s", author.label())));
                    });
        });
    }

    private static void unblock(View cell, VideoAuthor author) {
        blockInFlight = true;
        BlockAuthorService.unblock(author, (success, message) -> {
            blockInFlight = false;
            if (!success) {
                Utils.showToastLong(message == null || message.isEmpty()
                        ? L10n.f("Could not unblock %1$s", author.label())
                        : L10n.f("Could not unblock %1$s: %2$s", author.label(), message));
                return;
            }
            if (author.uid != null) {
                BLOCKED_UIDS.remove(author.uid);
            }
            applyBlockedState(cell);
            Utils.showToastShort(L10n.f("Unblocked %1$s", author.label()));
        });
    }

    // ---- keyword filter ----------------------------------------------------------------

    private static int filterComments(List<?> comments, List<String> keywords, List<String> users, boolean media) {
        int removed = 0;
        Iterator<?> iterator = comments.iterator();
        while (iterator.hasNext()) {
            Object comment = iterator.next();
            if (comment == null) {
                continue;
            }

            if (matches(comment, keywords, users, media)) {
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
                removed += filterComments((List<?>) replies, keywords, users, media);
            }
        }
        return removed;
    }

    private static boolean matches(Object comment, List<String> keywords, List<String> users, boolean media) {
        if (media && hasMedia(comment)) {
            return true;
        }

        String text = Reflect.string(comment, "getText", "text");
        if (text != null && !keywords.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
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

    private static int identifier(View view, String name) {
        Integer cached = RESOLVED_IDS.get(name);
        if (cached != null) {
            return cached;
        }
        int id;
        try {
            id = view.getResources().getIdentifier(name, "id", APP_PACKAGE);
        } catch (Throwable ignored) {
            id = 0;
        }
        RESOLVED_IDS.put(name, id);
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
