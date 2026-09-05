/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.inbox;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Hides individual rows and header controls on TikTok's Inbox tab.
 *
 * TikTok builds the Inbox as one RecyclerView, so rows are filtered as they lay out
 * rather than by editing TikTok's data. Every pass re-decides every row from its current
 * content, which keeps recycled views correct: a hidden row that gets reused for
 * something else is shown again on the next pass.
 *
 * Resource ids are from TikTok 46.2.3, read off the live view hierarchy:
 * <pre>
 *   kmx        the Inbox RecyclerView
 *   tyh        a system notice row, with its title in bo5
 *   v15        the message requests row
 *   vid        a direct message row, with its title in user_name
 *   vpj        a title inside the horizontal stories tray
 *   f8t        header, add people
 *   k_f        header, search
 *   kmz        header, activity status
 * </pre>
 */
public final class InboxFilter {
    private static final String LIST_ID = "kmx";
    private static final String SYSTEM_ROW_ID = "tyh";
    private static final String MESSAGE_REQUESTS_ID = "v15";
    private static final String DIRECT_MESSAGE_ID = "vid";
    private static final String SYSTEM_ROW_TITLE_ID = "bo5";
    private static final String USER_ROW_TITLE_ID = "user_name";
    private static final String STORIES_TITLE_ID = "vpj";

    private static final String INBOX_TAB_ID = "o1l";
    private static final String HEADER_ADD_PEOPLE_ID = "f8t";
    private static final String HEADER_SEARCH_ID = "k_f";
    private static final String HEADER_ACTIVITY_STATUS_ID = "kmz";

    /** Original row heights, so a hidden row can be restored exactly. */
    private static final WeakHashMap<View, Integer> ORIGINAL_HEIGHTS = new WeakHashMap<>();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener listener;

    private InboxFilter() {
    }

    /**
     * Called from the patched {@code MainActivity.onCreate}.
     *
     * @param activity the TikTok main activity
     */
    public static void install(Activity activity) {
        try {
            if (activity == null) {
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }

            if (listener != null && activityReference.get() == activity) {
                return;
            }

            listener = InboxFilter::apply;
            root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            activityReference = new WeakReference<>(activity);

            Logger.printDebug(() -> "Inbox filter installed");
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not install the inbox filter", ex);
        }
    }

    private static void apply() {
        try {
            Activity activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            // Cheap gate: do nothing unless the Inbox tab is the one on show.
            View inboxTab = find(activity, INBOX_TAB_ID);
            if (inboxTab == null || !inboxTab.isSelected()) {
                return;
            }

            applyHeader(activity);

            View list = find(activity, LIST_ID);
            if (!(list instanceof ViewGroup)) {
                return;
            }

            ViewGroup rows = (ViewGroup) list;
            for (int index = 0; index < rows.getChildCount(); index++) {
                View row = rows.getChildAt(index);
                setRowHidden(row, shouldHideRow(activity, row));
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Inbox filter failed", ex);
        }
    }

    private static void applyHeader(Activity activity) {
        setHidden(find(activity, HEADER_ADD_PEOPLE_ID), Settings.HIDE_INBOX_ADD_PEOPLE.get());
        setHidden(find(activity, HEADER_SEARCH_ID), Settings.HIDE_INBOX_SEARCH.get());
        setHidden(find(activity, HEADER_ACTIVITY_STATUS_ID), Settings.HIDE_INBOX_ACTIVITY_STATUS.get());
    }

    private static boolean shouldHideRow(Activity activity, View row) {
        // The message requests row carries its own id, so it needs no text matching.
        if (hasId(activity, row, MESSAGE_REQUESTS_ID)) {
            return Settings.HIDE_INBOX_MESSAGE_REQUESTS.get();
        }

        // The stories tray is the row that holds the horizontal avatar titles.
        if (findWithin(activity, row, STORIES_TITLE_ID) != null) {
            return Settings.HIDE_INBOX_STORIES.get();
        }

        // A system notice row: New followers, Activity, Archive, Tako, Shop.
        if (hasId(activity, row, SYSTEM_ROW_ID)) {
            return hideByTitle(textOf(findWithin(activity, row, SYSTEM_ROW_TITLE_ID)));
        }

        // Anything left carrying a user name is a conversation.
        if (hasId(activity, row, DIRECT_MESSAGE_ID)
                || findWithin(activity, row, DIRECT_MESSAGE_ID) != null
                || findWithin(activity, row, USER_ROW_TITLE_ID) != null) {
            if (Settings.HIDE_INBOX_CONVERSATIONS.get()) {
                return true;
            }
            return hideByTitle(textOf(findWithin(activity, row, USER_ROW_TITLE_ID)));
        }

        return false;
    }

    /**
     * Matches a row title against the built in categories and the user's own list.
     * Titles are the visible English labels, so this does not follow an app language change.
     */
    private static boolean hideByTitle(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }

        if (matches(title, "New followers", Settings.HIDE_INBOX_NEW_FOLLOWERS)
                || matches(title, "Activity", Settings.HIDE_INBOX_ACTIVITY)
                || matches(title, "Archive", Settings.HIDE_INBOX_ARCHIVE)
                || matches(title, "TikTok Tako", Settings.HIDE_INBOX_TAKO)
                || matches(title, "TikTok Shop", Settings.HIDE_INBOX_SHOP)
                || matches(title, "Suggested accounts", Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS)) {
            return true;
        }

        return matchesCustomList(title);
    }

    private static boolean matches(String title, String label, BooleanSetting setting) {
        return title.equalsIgnoreCase(label) && setting.get();
    }

    /** Lets the user hide a row this patch does not know about by typing its title. */
    private static boolean matchesCustomList(String title) {
        String custom = Settings.HIDE_INBOX_CUSTOM_TITLES.get();
        if (custom == null || custom.trim().isEmpty()) {
            return false;
        }

        for (String entry : custom.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty() && title.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * RecyclerView measures its children itself and does not honour {@code GONE}, so a
     * hidden row also needs a zero height to actually collapse.
     */
    private static void setRowHidden(View row, boolean hidden) {
        ViewGroup.LayoutParams params = row.getLayoutParams();
        if (params == null) {
            setHidden(row, hidden);
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

    private static void setHidden(View view, boolean hidden) {
        if (view == null) {
            return;
        }
        int wanted = hidden ? View.GONE : View.VISIBLE;
        if (view.getVisibility() != wanted) {
            view.setVisibility(wanted);
        }
    }

    private static String textOf(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) {
                // TikTok pads titles with a left to right mark.
                return text.toString().replace("‎", "").trim();
            }
        }
        return null;
    }

    private static boolean hasId(Activity activity, View view, String name) {
        int id = identifier(activity, name);
        return id != 0 && view.getId() == id;
    }

    private static View find(Activity activity, String name) {
        int id = identifier(activity, name);
        return id == 0 ? null : activity.findViewById(id);
    }

    private static View findWithin(Activity activity, View parent, String name) {
        int id = identifier(activity, name);
        return id == 0 ? null : parent.findViewById(id);
    }

    private static int identifier(Activity activity, String name) {
        try {
            return activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
