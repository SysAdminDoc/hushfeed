/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.comment;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

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
                insertBox((LinearLayout) parent, anchor, listView);
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

    /**
     * Whether the sheet this box is going into is dark.
     *
     * <p>Read from the context the sheet itself was built with, not from the shared dark mode
     * flag: that flag is only ever written when Hushfeed's own settings screen opens, so in a
     * session that never opened it the answer comes from the system configuration instead,
     * which does not carry the night mode TikTok applied to itself. A white card in a black
     * comment sheet is the visible version of that.
     */
    private static boolean isDarkSheet(Context context) {
        int night = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * The field's background, which is also the only thing that shows it has focus. A plain
     * drawable would look the same focused and not, and replacing the platform background is
     * what took the underline away in the first place.
     */
    private static StateListDrawable fieldBackground(Context context, boolean dark) {
        float radius = 8 * context.getResources().getDisplayMetrics().density;
        int stroke = Math.max(1, Math.round(context.getResources().getDisplayMetrics().density));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(dark ? 0xFF1B1B21 : 0xFFFFFFFF);
        focused.setCornerRadius(radius);
        // Not SettingsUi.accent(): that reads the shared dark mode flag, which is the
        // one this class stopped trusting two lines up. TikTok's own pink carries on both.
        focused.setStroke(stroke * 2, dark ? SettingsUi.ACCENT : SettingsUi.LIGHT_ACCENT);

        GradientDrawable resting = new GradientDrawable();
        resting.setColor(dark ? 0xFF111115 : 0xFFFFFFFF);
        resting.setCornerRadius(radius);
        resting.setStroke(stroke, dark ? 0xFF35353E : 0xFFD2D2D2);

        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, resting);
        return states;
    }

    /** Builds the box and puts it in {@code column}, directly above whatever holds the list. */
    private static void insertBox(LinearLayout column, View anchor, ViewGroup listView) {
        if (Boolean.TRUE.equals(DECORATED.get(column))) return;

        Context context = column.getContext();
        EditText box = new EditText(context);
        box.setHint(L10n.t(context, "Search these comments"));
        box.setContentDescription(L10n.t(context, "Search these comments"));
        box.setSingleLine(true);
        // A single line field with a newline key is a dead end; this is a search.
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setInputType(InputType.TYPE_CLASS_TEXT);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        boolean dark = isDarkSheet(context);
        box.setTextColor(dark ? 0xFFF5F5F7 : 0xFF16161C);
        box.setHintTextColor(dark ? 0xFFA8A8B3 : 0xFF575762);
        // A transparent background takes the focus underline with it, which left nothing
        // saying this was a field at all, so the border does that job instead.
        box.setBackground(fieldBackground(context, dark));
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        box.setPadding(padding, padding, padding, padding);
        // A minimum rather than a height: 48dp is the touch target, but at a large font scale
        // the text needs more than that and a fixed height would cut the letters off.
        box.setMinimumHeight(Math.round(48 * context.getResources().getDisplayMetrics().density));
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.setText(query);
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable typed) {
                setQuery(typed == null ? "" : typed.toString());
                narrowShownRows();
            }
        });
        box.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            // Filtering already happened as it was typed, so the key's job is to get the
            // keyboard out of the way of the comments it just narrowed down.
            InputMethodManager keyboard = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
            return true;
        });
        column.addView(box, column.indexOfChild(anchor));
        // Only once it is really in. Marking the column first would blacklist it for good if
        // anything above threw, and the sheet would never get a box again.
        DECORATED.put(column, Boolean.TRUE);

        // The column can be further up than the sheet and outlive it, so the box leaves with
        // the list it belongs to rather than being left over the feed.
        listView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}

            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                DECORATED.remove(column);
                setQuery("");
                // Posted rather than done here: this runs while the parent is part way
                // through taking the list out, and taking a second child out underneath
                // that leaves it reading a list it has already changed.
                column.post(() -> column.removeView(box));
            }
        });
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
