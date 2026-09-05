/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.comment;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorService;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.blockauthor.VideoAuthor;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Comment list tools: a keyword filter on loaded comments, and blocking a commenter with a
 * two finger hold on their comment.
 *
 * Both entry points are called from the same places the comment translation patch hooks:
 * {@code BaseCommentCell} binding a cell, and the comment list response being handled.
 * The {@code Comment} and {@code CommentItemList} models keep their real names on TikTok
 * 46.2.3 ({@code getText}, {@code getUser}, {@code getCid}, {@code getReplyComments}, and
 * the public {@code items} list), so nothing here depends on an obfuscated name.
 *
 * The block gesture is a two finger hold rather than a long press because TikTok already
 * owns long press on a comment (copy, report). The touch listener never consumes an event,
 * so TikTok's own click and long press handling keeps working underneath it.
 */
public final class CommentTools {
    private static final long HOLD_MS = 700L;

    /** Comment model bound to each cell view, so the gesture knows whose comment it is on. */
    private static final WeakHashMap<View, Object> CELL_COMMENTS = new WeakHashMap<>();

    /** Cells that already carry the gesture listener. */
    private static final WeakHashMap<View, Boolean> INSTRUMENTED = new WeakHashMap<>();

    private static volatile boolean blockInFlight;

    private CommentTools() {
    }

    /**
     * Called as a comment cell is bound. {@code manager} is the cell's state holder, whose
     * fields include the bound {@code Comment}.
     */
    public static void registerCommentCell(View itemView, Object manager) {
        if (!Settings.BLOCK_FROM_COMMENT.get() || itemView == null || manager == null) {
            return;
        }

        try {
            Object comment = findComment(manager);
            if (comment == null) {
                return;
            }

            synchronized (CELL_COMMENTS) {
                CELL_COMMENTS.put(itemView, comment);
                if (!Boolean.TRUE.equals(INSTRUMENTED.get(itemView))) {
                    itemView.setOnTouchListener(new TwoFingerHold());
                    INSTRUMENTED.put(itemView, Boolean.TRUE);
                }
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not register a comment cell", ex);
        }
    }

    /**
     * Called with the {@code CommentItemList} once TikTok has parsed a page of comments,
     * before they are shown. Matching comments are removed from the list in place.
     */
    public static void onCommentListLoaded(Object commentItemList) {
        if (!Settings.COMMENT_KEYWORD_FILTER.get() || commentItemList == null) {
            return;
        }

        try {
            List<String> keywords = entries(Settings.COMMENT_BLOCKED_KEYWORDS.get());
            List<String> users = entries(Settings.COMMENT_BLOCKED_USERS.get());
            if (keywords.isEmpty() && users.isEmpty()) {
                return;
            }

            Object itemsObject = Reflect.readField(commentItemList, "items");
            if (!(itemsObject instanceof List)) {
                return;
            }

            int removed = filterComments((List<?>) itemsObject, keywords, users);
            if (removed > 0) {
                final int count = removed;
                Logger.printDebug(() -> "Comment filter removed " + count + " comment(s)");
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Comment filter failed", ex);
        }
    }

    private static int filterComments(List<?> comments, List<String> keywords, List<String> users) {
        int removed = 0;
        Iterator<?> iterator = comments.iterator();
        while (iterator.hasNext()) {
            Object comment = iterator.next();
            if (comment == null) {
                continue;
            }

            if (matches(comment, keywords, users)) {
                try {
                    iterator.remove();
                    removed++;
                    continue;
                } catch (UnsupportedOperationException ex) {
                    // Immutable list: fall through and leave it. Logged once below.
                    Logger.printInfo(() -> "Comment list is immutable; the keyword filter cannot remove from it");
                    return removed;
                }
            }

            Object replies = Reflect.property(comment, "getReplyComments", "replyComments");
            if (replies instanceof List) {
                removed += filterComments((List<?>) replies, keywords, users);
            }
        }
        return removed;
    }

    private static boolean matches(Object comment, List<String> keywords, List<String> users) {
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

    private static void blockCommenter(View cell) {
        if (blockInFlight) {
            return;
        }

        Object comment;
        synchronized (CELL_COMMENTS) {
            comment = CELL_COMMENTS.get(cell);
        }
        Object user = comment == null ? null : Reflect.property(comment, "getUser", "user");
        if (user == null) {
            Utils.showToastShort("Could not read who posted this comment");
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
            Utils.showToastShort("Could not read who posted this comment");
            return;
        }

        blockInFlight = true;
        cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        BlockAuthorService.block(author, (success, message) -> {
            blockInFlight = false;
            if (success) {
                BlockAuthorOverlay.showUndoBanner("Blocked " + author.label(),
                        () -> BlockAuthorService.unblock(author, (undone, ignored) -> Utils.showToastShort(
                                undone ? "Unblocked " + author.label() : "Could not unblock " + author.label())));
            } else {
                Utils.showToastLong("Could not block " + author.label()
                        + (message == null ? "" : ": " + message));
            }
        });
    }

    /**
     * Fires when two fingers rest on the cell for {@link #HOLD_MS} without moving. Never
     * consumes an event.
     */
    private static final class TwoFingerHold implements View.OnTouchListener {
        private long startedAt = -1L;
        private float startX;
        private float startY;
        private boolean cancelled;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        startedAt = event.getEventTime();
                        startX = event.getX(0) + event.getX(1);
                        startY = event.getY(0) + event.getY(1);
                        cancelled = false;
                    } else {
                        cancelled = true;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (startedAt >= 0 && !cancelled && event.getPointerCount() >= 2) {
                        float slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop() * 2f;
                        float dx = event.getX(0) + event.getX(1) - startX;
                        float dy = event.getY(0) + event.getY(1) - startY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                            cancelled = true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (startedAt >= 0 && !cancelled
                            && event.getActionMasked() != MotionEvent.ACTION_CANCEL
                            && event.getEventTime() - startedAt >= HOLD_MS) {
                        startedAt = -1L;
                        blockCommenter(view);
                    }
                    startedAt = -1L;
                    break;

                default:
                    break;
            }
            return false;
        }
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
