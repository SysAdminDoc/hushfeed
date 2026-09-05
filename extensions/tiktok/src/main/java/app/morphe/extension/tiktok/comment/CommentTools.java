/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.comment;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorOverlay;
import app.morphe.extension.tiktok.blockauthor.BlockAuthorService;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.blockauthor.VideoAuthor;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * The block gesture is watched at the window, not on the cell. A finger resting on a
 * comment lands on the comment's own children (text, avatar, like button), and Android
 * only hands a touch to the cell's listener when no child takes it, so a listener on the
 * cell never saw the gesture. The window's callback sees every touch before any view
 * does; wrapping it with a proxy that forwards everything and merely observes
 * {@code dispatchTouchEvent} costs TikTok nothing.
 */
public final class CommentTools {
    private static final long HOLD_MS = 700L;

    /** Comment model bound to each cell view, so a hit test can name whose comment it is. */
    private static final WeakHashMap<View, Object> CELL_COMMENTS = new WeakHashMap<>();

    /** Windows already wrapped, keyed by the activity that owns them. */
    private static final WeakHashMap<Activity, Boolean> OBSERVED = new WeakHashMap<>();

    private static volatile boolean blockInFlight;
    private static volatile boolean warnedOtherWindow;

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
                Logger.printDebug(() -> "Comment cell bound but no comment found on " + manager.getClass().getName());
                return;
            }

            synchronized (CELL_COMMENTS) {
                CELL_COMMENTS.put(itemView, comment);
            }

            Activity activity = activityOf(itemView.getContext());
            if (activity == null) {
                activity = Utils.getActivity();
            }
            if (activity != null) {
                observeWindow(activity, itemView);
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

    // ---- touch observation -------------------------------------------------------------

    /**
     * Wraps the window callback once per activity. Also checks that the cell actually
     * lives in that window: if TikTok ever moves the comment panel into a dialog, its
     * touches go through the dialog's window instead, and this logs that rather than
     * silently watching the wrong one.
     */
    private static void observeWindow(Activity activity, View itemView) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        View decor = window.peekDecorView();
        if (decor != null && itemView.getRootView() != decor && !warnedOtherWindow) {
            warnedOtherWindow = true;
            Logger.printInfo(() -> "Comment cells live in a window other than the activity's ("
                    + itemView.getRootView().getClass().getName()
                    + "); the two finger block gesture will not see them");
        }

        synchronized (OBSERVED) {
            if (Boolean.TRUE.equals(OBSERVED.get(activity))) {
                return;
            }

            Window.Callback existing = window.getCallback();
            if (existing == null || Proxy.isProxyClass(existing.getClass())
                    && Proxy.getInvocationHandler(existing) instanceof TouchObserver) {
                OBSERVED.put(activity, Boolean.TRUE);
                return;
            }

            Window.Callback wrapped = (Window.Callback) Proxy.newProxyInstance(
                    Window.Callback.class.getClassLoader(),
                    new Class<?>[]{Window.Callback.class},
                    new TouchObserver(existing));
            window.setCallback(wrapped);
            OBSERVED.put(activity, Boolean.TRUE);
            Logger.printInfo(() -> "Comment block gesture is watching the window");
        }
    }

    /**
     * Forwards every window callback untouched and watches {@code dispatchTouchEvent} for
     * two fingers resting still for {@link #HOLD_MS}.
     */
    private static final class TouchObserver implements InvocationHandler {
        private final Window.Callback wrapped;
        private long startedAt = -1L;
        private float startX;
        private float startY;
        private boolean cancelled;

        TouchObserver(Window.Callback wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof MotionEvent) {
                try {
                    observe((MotionEvent) args[0]);
                } catch (Throwable ex) {
                    Logger.printException(() -> "Comment gesture observer failed", ex);
                }
            }
            try {
                return method.invoke(wrapped, args);
            } catch (java.lang.reflect.InvocationTargetException ex) {
                throw ex.getCause() != null ? ex.getCause() : ex;
            }
        }

        private void observe(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        startedAt = event.getEventTime();
                        startX = midX(event);
                        startY = midY(event);
                        cancelled = false;
                    } else {
                        cancelled = true;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (startedAt >= 0 && !cancelled && event.getPointerCount() >= 2) {
                        Activity activity = Utils.getActivity();
                        float slop = activity == null ? 24f
                                : ViewConfiguration.get(activity).getScaledTouchSlop() * 2f;
                        if (Math.abs(midX(event) - startX) > slop || Math.abs(midY(event) - startY) > slop) {
                            cancelled = true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    if (startedAt >= 0 && !cancelled && event.getPointerCount() == 2
                            && event.getEventTime() - startedAt >= HOLD_MS) {
                        float x = midX(event);
                        float y = midY(event);
                        startedAt = -1L;
                        cancelled = true;
                        View cell = cellAt(x, y);
                        if (cell != null) {
                            blockCommenter(cell);
                        } else {
                            Logger.printDebug(() -> "Two finger hold landed on no registered comment");
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    startedAt = -1L;
                    cancelled = false;
                    break;

                default:
                    break;
            }
        }

        private static float midX(MotionEvent event) {
            return (event.getX(0) + event.getX(1)) / 2f;
        }

        private static float midY(MotionEvent event) {
            return (event.getY(0) + event.getY(1)) / 2f;
        }
    }

    /** @return the registered comment cell under a point in window coordinates, or null. */
    private static View cellAt(float x, float y) {
        Activity activity = Utils.getActivity();
        View decor = activity == null ? null : activity.getWindow().peekDecorView();
        int[] decorOrigin = new int[2];
        if (decor != null) {
            decor.getLocationOnScreen(decorOrigin);
        }
        float screenX = x + decorOrigin[0];
        float screenY = y + decorOrigin[1];

        List<View> cells;
        synchronized (CELL_COMMENTS) {
            cells = new ArrayList<>(CELL_COMMENTS.keySet());
        }

        int[] location = new int[2];
        for (View cell : cells) {
            if (cell == null || !cell.isShown()) {
                continue;
            }
            cell.getLocationOnScreen(location);
            if (screenX >= location[0] && screenX <= location[0] + cell.getWidth()
                    && screenY >= location[1] && screenY <= location[1] + cell.getHeight()) {
                return cell;
            }
        }
        return null;
    }

    private static Activity activityOf(Context context) {
        while (context != null) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    // ---- blocking ----------------------------------------------------------------------

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
        cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
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

    // ---- keyword filter ----------------------------------------------------------------

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
