/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.comment;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A box above the comments that narrows them to the ones you are looking for.
 *
 * <p>Nothing is taken out of TikTok's own list. A comment that does not match is collapsed
 * where it sits, the same way the inbox hides a row it was told to hide, so clearing the box
 * brings everything straight back and TikTok's paging, replies and counts never learn that
 * anything happened.
 */
public final class CommentSearch {
    /** Rows put back at the height they had, once the box is cleared again. */
    private static final Map<View, Integer> ORIGINAL_HEIGHTS = new WeakHashMap<>();
    /** What each bound row is showing, so typing can go over the rows already on screen. */
    private static final Map<View, Object> ROW_COMMENTS = new WeakHashMap<>();
    /** The list a box has already been put above, so it is only added once. */
    private static final Map<ViewGroup, Boolean> DECORATED = new WeakHashMap<>();

    /** How far above the list to look for something that stacks its children. */
    private static final int MAX_COLUMN_LEVELS = 4;

    private static volatile String query = "";
    private static WeakReference<ViewGroup> shown = new WeakReference<>(null);
    private static boolean warnedNoColumn;

    private CommentSearch() {}

    public static boolean enabled() {
        return Settings.COMMENT_SEARCH.get();
    }

    /** What is in the box, lower cased once so every comparison does not have to be. */
    static String query() {
        return query;
    }

    static void setQuery(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a comment stays on screen. An empty box keeps everything, and a comment matches
     * on what it says or on who said it, by handle or by the name they show.
     */
    static boolean matches(Object comment, String wanted) {
        if (wanted.isEmpty()) return true;
        if (comment == null) return false;

        String text = Reflect.string(comment, "getText", "text");
        if (contains(text, wanted)) return true;

        Object user = Reflect.property(comment, "getUser", "user");
        return contains(Reflect.string(user, "getUniqueId", "uniqueId"), wanted)
                || contains(Reflect.string(user, "getNickname", "nickname"), wanted);
    }

    private static boolean contains(String haystack, String wanted) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(wanted);
    }

    /** Called for each comment row as it is bound, with the comment that row is showing. */
    public static void onCellBound(View itemView, Object comment) {
        if (itemView == null || !enabled()) return;
        try {
            ROW_COMMENTS.put(itemView, comment);
            setRowHidden(itemView, !matches(comment, query));
            // A list binds a row before putting it in place, and detaches one it is about to
            // rebind, so the sheet is not reachable from the row while this runs. Waiting for
            // the row to be attached is the only time the list can be found.
            itemView.post(() -> decorate(itemView));
        } catch (Throwable exception) {
            Logger.printException(() -> "Could not narrow a comment row", exception);
        }
    }

    /** Runs once the bound row is in place, which is the first moment the list can be read. */
    private static void decorate(View itemView) {
        try {
            ViewParent parent = itemView.getParent();
            if (!(parent instanceof ViewGroup)) return;
            ViewGroup listView = (ViewGroup) parent;
            if (shown.get() != listView) {
                // A different sheet. Whatever was typed into the last one was about that
                // video's comments, so it does not follow the reader to this one.
                shown = new WeakReference<>(listView);
                setQuery("");
            }
            addSearchField(listView);
            narrowShownRows();
        } catch (Throwable exception) {
            Logger.printException(() -> "Could not put a box above the comments", exception);
        }
    }

    /**
     * Puts the box above the comments, once per list. Something above the list has to lay its
     * children out one under another for a box added there to land above the list rather than
     * across it, so the nearest few ancestors are tried and anything else is left alone.
     */
    private static void addSearchField(ViewGroup listView) {
        View anchor = listView;
        ViewParent parent = listView.getParent();
        for (int level = 0; level < MAX_COLUMN_LEVELS && parent instanceof ViewGroup; level++) {
            if (parent instanceof LinearLayout
                    && ((LinearLayout) parent).getOrientation() == LinearLayout.VERTICAL) {
                insertBox((LinearLayout) parent, anchor);
                return;
            }
            anchor = (View) parent;
            parent = parent.getParent();
        }
        if (!warnedNoColumn) {
            warnedNoColumn = true;
            ViewParent nearest = listView.getParent();
            String name = nearest == null ? "none" : nearest.getClass().getName();
            Logger.printInfo(() -> "Nothing above the comment list stacks its children, so the "
                    + "search box has nowhere to go. Nearest parent: " + name);
        }
    }

    /** Builds the box and puts it in {@code column}, directly above whatever holds the list. */
    private static void insertBox(LinearLayout column, View anchor) {
        if (Boolean.TRUE.equals(DECORATED.get(column))) return;
        DECORATED.put(column, Boolean.TRUE);

        Context context = column.getContext();
        EditText box = new EditText(context);
        box.setHint("Search these comments");
        box.setSingleLine(true);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        box.setBackgroundColor(Color.TRANSPARENT);
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        box.setPadding(padding, padding, padding, padding);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.setText(query);
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable typed) {
                setQuery(typed == null ? "" : typed.toString());
                narrowShownRows();
            }
        });
        column.addView(box, column.indexOfChild(anchor));
    }

    /**
     * Goes over the rows already on screen. Rows scrolled to afterwards are dealt with as
     * they bind, and a row the list recycles onto a different comment is put right by the
     * bind that recycled it.
     */
    static void narrowShownRows() {
        ViewGroup listView = shown.get();
        if (listView == null) return;
        for (int index = 0; index < listView.getChildCount(); index++) {
            View row = listView.getChildAt(index);
            if (!ROW_COMMENTS.containsKey(row)) continue;
            setRowHidden(row, !matches(ROW_COMMENTS.get(row), query));
        }
    }

    /**
     * A scrolling list measures its children itself and does not honour {@code GONE}, so a
     * hidden row also needs a zero height to collapse. Nothing is written when the row is
     * already in the wanted state.
     */
    private static void setRowHidden(View row, boolean hidden) {
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params == null) {
            int wanted = hidden ? View.GONE : View.VISIBLE;
            if (row.getVisibility() != wanted) row.setVisibility(wanted);
            return;
        }

        Integer original = ORIGINAL_HEIGHTS.get(row);
        if (original == null && params.height != 0) {
            original = params.height;
            ORIGINAL_HEIGHTS.put(row, original);
        }

        if (hidden) {
            if (row.getVisibility() != View.GONE || params.height != 0) {
                row.setVisibility(View.GONE);
                params.height = 0;
                row.setLayoutParams(params);
            }
        } else {
            int restored = original != null ? original : ViewGroup.LayoutParams.WRAP_CONTENT;
            if (row.getVisibility() != View.VISIBLE || params.height != restored) {
                row.setVisibility(View.VISIBLE);
                params.height = restored;
                row.setLayoutParams(params);
            }
        }
    }
}
