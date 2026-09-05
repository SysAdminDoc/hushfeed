/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.comment;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Comment list tools: a keyword filter on loaded comments, and a block button drawn
 * beside each comment that blocks its author in one tap.
 *
 * Both entry points are called from the same places the comment translation patch hooks:
 * {@code BaseCommentCell} binding a cell, and the comment list response being handled.
 * The {@code Comment} and {@code CommentItemList} models keep their real names on TikTok
 * 46.2.3 ({@code getText}, {@code getUser}, {@code getCid}, {@code getReplyComments}, and
 * the public {@code items} list), so nothing here depends on an obfuscated name.
 *
 * The button is not added to the comment cell. The cell's layout type and the position of
 * TikTok's own like control are not known, and a child dropped into an unknown layout can
 * land anywhere. Instead one transparent layer is added on top of the window the comments
 * live in, and each visible cell gets a small button positioned over it before every
 * frame. Feedback (the dimmed row and the undo banner) is drawn in that same window,
 * because the comment panel is not always in the activity's window and anything added to
 * the activity's content root then sits underneath it.
 *
 * A cell is bound before it is attached to a window, so at bind time its root view is
 * just the top of a detached subtree (a LinearLayout on 46.2.3). The layer is therefore
 * attached from an attach listener, and only to a root whose parent is the window itself.
 */
public final class CommentTools {
    private static final String BLOCK_GLYPH = "⊘";
    private static final String BLOCKED_GLYPH = "✓";
    private static final int BUTTON_SIZE_DP = 28;
    private static final int BUTTON_TOP_DP = 6;

    /**
     * Distance from the cell's right edge to the button's right edge. TikTok's like heart
     * sits against the right edge on the username row; this parks the button just left
     * of it.
     */
    private static final int BUTTON_RIGHT_INSET_DP = 56;
    private static final float BLOCKED_ROW_ALPHA = 0.35f;

    /** Comment model bound to each cell view. */
    private static final WeakHashMap<View, Object> CELL_COMMENTS = new WeakHashMap<>();

    /** One button layer per window root the comments have been seen in. */
    private static final WeakHashMap<View, ButtonLayer> LAYERS = new WeakHashMap<>();

    /** Cells that already have an attach listener, so each gets exactly one. */
    private static final WeakHashMap<View, Boolean> ATTACH_HOOKED = new WeakHashMap<>();

    private static boolean warnedNoWindowRoot;

    /** Accounts blocked this session, by uid, so a recycled cell shows the right state. */
    private static final Set<String> BLOCKED_UIDS = Collections.synchronizedSet(new HashSet<>());

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
                Logger.printDebug(() -> "Comment cell bound but no comment found on " + manager.getClass().getName());
                return;
            }

            synchronized (CELL_COMMENTS) {
                CELL_COMMENTS.put(itemView, comment);
            }

            attachWhenReady(itemView);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not register a comment cell", ex);
        }
    }

    /**
     * Binds happen before the cell is attached, and a detached cell's root view is not a
     * window. Attach the layer now if the cell is already in a window, otherwise once it
     * gets there. Recycled cells are detached and reattached, so the listener stays on.
     */
    private static void attachWhenReady(View cell) {
        if (cell.isAttachedToWindow()) {
            attachLayer(cell);
            return;
        }
        synchronized (ATTACH_HOOKED) {
            if (ATTACH_HOOKED.put(cell, Boolean.TRUE) != null) {
                return;
            }
        }
        cell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                attachLayer(view);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
            }
        });
    }

    private static void attachLayer(View cell) {
        try {
            View root = cell.getRootView();
            if (!(root instanceof ViewGroup) || !isWindowRoot(root)) {
                if (!warnedNoWindowRoot) {
                    warnedNoWindowRoot = true;
                    Logger.printInfo(() -> "Comment cell root is not a window: "
                            + (root == null ? "null" : root.getClass().getName()));
                }
                return;
            }
            layerFor((ViewGroup) root).requestLayoutPass();
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not attach the comment block buttons", ex);
        }
    }

    /** The window's decor view is the only view whose parent is not itself a view. */
    private static boolean isWindowRoot(View view) {
        ViewParent parent = view.getParent();
        return parent != null && !(parent instanceof View);
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

    // ---- button layer ------------------------------------------------------------------

    private static ButtonLayer layerFor(ViewGroup root) {
        synchronized (LAYERS) {
            ButtonLayer layer = LAYERS.get(root);
            if (layer == null) {
                layer = new ButtonLayer(root);
                LAYERS.put(root, layer);
                Logger.printInfo(() -> "Comment block buttons attached to " + root.getClass().getName());
            }
            return layer;
        }
    }

    /**
     * A transparent full-window layer holding one button per visible comment cell.
     * It is not clickable itself, so touches on empty parts of it fall through to
     * TikTok; only the buttons take a tap.
     */
    private static final class ButtonLayer implements ViewTreeObserver.OnPreDrawListener {
        private final ViewGroup root;
        private final FrameLayout layer;
        private final WeakHashMap<View, TextView> buttons = new WeakHashMap<>();
        private final int size;
        private final int topInset;
        private final int rightInset;

        ButtonLayer(ViewGroup root) {
            this.root = root;
            float density = root.getResources().getDisplayMetrics().density;
            size = Math.round(BUTTON_SIZE_DP * density);
            topInset = Math.round(BUTTON_TOP_DP * density);
            rightInset = Math.round(BUTTON_RIGHT_INSET_DP * density);

            layer = new FrameLayout(root.getContext());
            layer.setClickable(false);
            layer.setFocusable(false);
            root.addView(layer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.getViewTreeObserver().addOnPreDrawListener(this);
        }

        void requestLayoutPass() {
            layer.invalidate();
        }

        @Override
        public boolean onPreDraw() {
            try {
                place();
            } catch (Throwable ex) {
                Logger.printException(() -> "Comment block buttons failed to place", ex);
            }
            return true;
        }

        private void place() {
            // The layer must stay the top child so buttons draw over the list.
            if (layer.getParent() == root && root.getChildAt(root.getChildCount() - 1) != layer) {
                root.removeView(layer);
                root.addView(layer);
            }

            List<View> cells;
            synchronized (CELL_COMMENTS) {
                cells = new ArrayList<>(CELL_COMMENTS.keySet());
            }

            int[] layerOrigin = new int[2];
            layer.getLocationInWindow(layerOrigin);
            int[] location = new int[2];
            Set<View> live = new HashSet<>();

            for (View cell : cells) {
                if (cell == null || !cell.isAttachedToWindow() || cell.getRootView() != root
                        || !cell.isShown() || cell.getWidth() == 0) {
                    continue;
                }
                Object comment;
                synchronized (CELL_COMMENTS) {
                    comment = CELL_COMMENTS.get(cell);
                }
                if (comment == null) {
                    continue;
                }
                live.add(cell);

                TextView button = buttons.get(cell);
                if (button == null) {
                    button = createButton(cell);
                    buttons.put(cell, button);
                    layer.addView(button, new FrameLayout.LayoutParams(size, size));
                }

                cell.getLocationInWindow(location);
                int left = location[0] - layerOrigin[0] + cell.getWidth() - rightInset - size;
                int top = location[1] - layerOrigin[1] + topInset;
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) button.getLayoutParams();
                if (params.leftMargin != left || params.topMargin != top) {
                    params.leftMargin = Math.max(0, left);
                    params.topMargin = Math.max(0, top);
                    button.setLayoutParams(params);
                }

                boolean blocked = isBlocked(comment);
                applyState(cell, button, blocked);
                if (button.getVisibility() != View.VISIBLE) {
                    button.setVisibility(View.VISIBLE);
                }
            }

            // Hide buttons whose cells scrolled away or were recycled into something else.
            for (View cell : new ArrayList<>(buttons.keySet())) {
                if (!live.contains(cell)) {
                    TextView button = buttons.get(cell);
                    if (button != null && button.getVisibility() != View.GONE) {
                        button.setVisibility(View.GONE);
                    }
                }
            }
        }

        private TextView createButton(View cell) {
            TextView button = new TextView(root.getContext());
            button.setText(BLOCK_GLYPH);
            button.setTextColor(Color.WHITE);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            button.setGravity(Gravity.CENTER);
            button.setContentDescription("Block this commenter");

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(Color.argb(170, 0, 0, 0));
            background.setStroke(Math.round(root.getResources().getDisplayMetrics().density),
                    Color.argb(110, 255, 255, 255));
            button.setBackground(background);

            button.setOnClickListener(view -> blockCommenter(cell, root));
            return button;
        }

        private void applyState(View cell, TextView button, boolean blocked) {
            String glyph = blocked ? BLOCKED_GLYPH : BLOCK_GLYPH;
            if (!glyph.contentEquals(button.getText())) {
                button.setText(glyph);
            }
            if (button.isEnabled() == blocked) {
                button.setEnabled(!blocked);
            }
            float alpha = blocked ? BLOCKED_ROW_ALPHA : 1f;
            if (cell.getAlpha() != alpha) {
                cell.setAlpha(alpha);
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

    private static void blockCommenter(View cell, ViewGroup root) {
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
            if (!success) {
                Utils.showToastLong("Could not block " + author.label()
                        + (message == null ? "" : ": " + message));
                return;
            }

            if (author.uid != null) {
                BLOCKED_UIDS.add(author.uid);
            }
            root.invalidate();
            BlockAuthorOverlay.showUndoBanner(root, "Blocked " + author.label(), () -> {
                if (author.uid != null) {
                    BLOCKED_UIDS.remove(author.uid);
                }
                root.invalidate();
                BlockAuthorService.unblock(author, (undone, ignored) -> Utils.showToastShort(
                        undone ? "Unblocked " + author.label() : "Could not unblock " + author.label()));
            });
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
