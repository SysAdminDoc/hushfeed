/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.inbox;

import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Hides individual rows and header controls on TikTok's Inbox tab, and adds a Clear all
 * control to the Suggested accounts section.
 *
 * TikTok builds the Inbox as one RecyclerView, so rows are filtered as they lay out
 * rather than by editing TikTok's data. Every pass re-decides every row from its current
 * content, which keeps recycled views correct: a hidden row that gets reused for
 * something else is shown again on the next pass.
 *
 * Resource ids are from TikTok 46.2.3, read off the live view hierarchy:
 * <pre>
 *   o1l        bottom navigation, the Inbox tab
 *   kmx        the Inbox RecyclerView
 *   tyh        a system notice row, with its title in bo5
 *   v15        the container shared by conversations and the message requests row
 *   vid        the title wrapper that only a real conversation has
 *   user_name  the title of a conversation or of the message requests row
 *   vpj        a title inside the horizontal stories tray
 *   pgu        the suggested accounts section header, holding t4g
 *   t4g        the suggested accounts section title
 *   fnc        remove an account from suggested accounts
 *   f8t        header, add people
 *   k_f        header, search
 *   kmz        header, activity status
 * </pre>
 */
public final class InboxFilter {
    private static final String INBOX_TAB_ID = "o1l";
    private static final String LIST_ID = "kmx";
    private static final String SYSTEM_ROW_ID = "tyh";
    private static final String MESSAGE_REQUESTS_ID = "v15";
    private static final String CONVERSATION_ID = "vid";
    private static final String SYSTEM_ROW_TITLE_ID = "bo5";
    private static final String USER_ROW_TITLE_ID = "user_name";
    private static final String STORIES_TITLE_ID = "vpj";
    private static final String SUGGESTED_HEADER_ID = "pgu";
    private static final String SUGGESTED_TITLE_ID = "t4g";
    private static final String SUGGESTED_REMOVE_ID = "fnc";
    private static final String HEADER_ADD_PEOPLE_ID = "f8t";
    private static final String HEADER_SEARCH_ID = "k_f";
    private static final String HEADER_ACTIVITY_STATUS_ID = "kmz";

    /** One dismissal at a time, so bulk clearing does not hammer TikTok's API. */
    private static final long DISMISS_INTERVAL_MS = 300L;

    /** Stops a runaway loop if TikTok keeps refilling the list while clearing. */
    private static final int MAX_CLEARED_PER_RUN = 60;

    /** Id for the injected Clear all control, so it is only added once. */
    private static final int CLEAR_ALL_VIEW_ID = View.generateViewId();

    /**
     * Resource ids resolved once. The filter runs on every layout pass of the whole
     * activity, and a resource name lookup is a string search through the resource
     * table, so without this the feed would pay for a dozen lookups per frame.
     */
    private static final Map<String, Integer> RESOLVED_IDS = new HashMap<>();

    /**
     * Accounts dismissed in the current Clear all run, keyed by the remove button's
     * description, which names the account. Keying on the view would skip an account
     * whose row reused a view already clicked, since the list recycles views as rows go.
     */
    private static final Set<String> DISMISSED_LABELS = new HashSet<>();

    /** Original row heights, so a hidden row can be restored exactly. */
    private static final WeakHashMap<View, Integer> ORIGINAL_HEIGHTS = new WeakHashMap<>();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static ViewTreeObserver.OnGlobalLayoutListener listener;

    private InboxFilter() {
    }

    /**
     * Called from the patched {@code MainActivity.onCreate}, before the activity's own
     * onCreate body has run. The work is posted so it happens once the window content
     * exists, whatever TikTok does in between.
     *
     * @param activity the TikTok main activity
     */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        Utils.runOnMainThread(() -> installNow(activity));
    }

    private static void installNow(Activity activity) {
        try {
            if (activity.isFinishing()) {
                return;
            }

            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                Logger.printInfo(() -> "Inbox filter found no content view to watch");
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

            addClearAllControl(activity);
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
        // The stories tray is the row that holds the horizontal avatar titles.
        if (findWithin(activity, row, STORIES_TITLE_ID) != null) {
            return Settings.HIDE_INBOX_STORIES.get();
        }

        // Suggested accounts is a section header followed by one row per account. The
        // header carries the section title, and each account row carries the remove
        // button. Neither uses the row title ids, so both are matched on their own marker.
        if (findWithin(activity, row, SUGGESTED_TITLE_ID) != null
                || findWithin(activity, row, SUGGESTED_REMOVE_ID) != null) {
            return Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.get();
        }

        // A real conversation and the message requests row share the same container id,
        // so the container alone cannot tell them apart. Only a conversation wraps its
        // title in the vid layout, so that is the discriminator, and it has to be tested
        // first. A conversation title is a person's name, so it is only ever matched
        // against the user's own list, never the system labels.
        if (findWithin(activity, row, CONVERSATION_ID) != null) {
            return Settings.HIDE_INBOX_CONVERSATIONS.get()
                    || matchesCustomList(textOf(findWithin(activity, row, USER_ROW_TITLE_ID)));
        }

        if (hasId(activity, row, MESSAGE_REQUESTS_ID)) {
            return Settings.HIDE_INBOX_MESSAGE_REQUESTS.get()
                    || matchesCustomList(textOf(findWithin(activity, row, USER_ROW_TITLE_ID)));
        }

        // A system notice row: New followers, Activity, Archive, Tako, Shop.
        if (hasId(activity, row, SYSTEM_ROW_ID)) {
            String title = textOf(findWithin(activity, row, SYSTEM_ROW_TITLE_ID));
            return matchesSystemLabel(title) || matchesCustomList(title);
        }

        return false;
    }

    /**
     * Matches a system row title against the built in categories. These are the visible
     * English labels, since the rows share one container and text is all that separates
     * them, so they stop matching if the app language changes.
     */
    private static boolean matchesSystemLabel(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        return matches(title, "New followers", Settings.HIDE_INBOX_NEW_FOLLOWERS)
                || matches(title, "Activity", Settings.HIDE_INBOX_ACTIVITY)
                || matches(title, "Archive", Settings.HIDE_INBOX_ARCHIVE)
                || matches(title, "TikTok Tako", Settings.HIDE_INBOX_TAKO)
                || matches(title, "TikTok Shop", Settings.HIDE_INBOX_SHOP);
    }

    private static boolean matches(String title, String label, BooleanSetting setting) {
        return title.equalsIgnoreCase(label) && setting.get();
    }

    /** Lets the user hide a row this patch does not know about by typing its title. */
    private static boolean matchesCustomList(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }

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
     * Puts a Clear all control at the right end of the Suggested accounts heading.
     *
     * Pressing it works through the remove buttons one at a time, which is the same
     * action as pressing each x by hand, so TikTok stops suggesting those accounts.
     */
    private static void addClearAllControl(Activity activity) {
        if (Settings.HIDE_INBOX_SUGGESTED_ACCOUNTS.get()) {
            return;
        }

        View header = find(activity, SUGGESTED_HEADER_ID);
        if (!(header instanceof ViewGroup)) {
            return;
        }

        ViewGroup headerGroup = (ViewGroup) header;
        if (headerGroup.findViewById(CLEAR_ALL_VIEW_ID) != null) {
            return;
        }

        TextView clearAll = new TextView(activity);
        clearAll.setId(CLEAR_ALL_VIEW_ID);
        clearAll.setText("Clear all");
        clearAll.setTextColor(Color.rgb(254, 44, 85));
        clearAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        clearAll.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        clearAll.setContentDescription("Clear all suggested accounts");
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = Math.round(16 * density);
        clearAll.setPadding(padding, 0, padding, 0);
        clearAll.setOnClickListener(view -> clearAllSuggested(activity));

        // Zero width with weight takes whatever the title and Learn more leave, and the
        // end gravity parks the text against the right edge.
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        headerGroup.addView(clearAll, params);

        Logger.printDebug(() -> "Clear all control added to the suggested accounts heading");
    }

    private static void clearAllSuggested(Activity activity) {
        DISMISSED_LABELS.clear();
        clearNextSuggested(activity, 0);
    }

    /**
     * Dismisses one account then schedules the next, rather than clicking everything at
     * once, so TikTok sees the same pacing as a person tapping.
     */
    private static void clearNextSuggested(Activity activity, int cleared) {
        try {
            if (cleared >= MAX_CLEARED_PER_RUN || activity.isFinishing()) {
                report(cleared);
                return;
            }

            View list = find(activity, LIST_ID);
            int removeId = identifier(activity, SUGGESTED_REMOVE_ID);
            View button = (list == null || removeId == 0) ? null : findUndismissed(list, removeId);

            if (button == null) {
                report(cleared);
                return;
            }

            DISMISSED_LABELS.add(labelOf(button));
            button.performClick();

            Utils.runOnMainThreadDelayed(
                    () -> clearNextSuggested(activity, cleared + 1), DISMISS_INTERVAL_MS);
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not clear suggested accounts", ex);
        }
    }

    private static void report(int cleared) {
        Utils.showToastShort(cleared == 0
                ? "No suggested accounts to clear"
                : "Dismissed " + cleared + " suggested account" + (cleared == 1 ? "" : "s"));
    }

    /** Depth first search for a remove button whose account has not been dismissed yet. */
    private static View findUndismissed(View view, int removeId) {
        if (view.getId() == removeId && view.isShown() && !DISMISSED_LABELS.contains(labelOf(view))) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findUndismissed(group.getChildAt(index), removeId);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /**
     * The remove button's description names the account ("Remove name from suggested
     * accounts"), which identifies the row however its view gets recycled. A button with
     * no description falls back to its identity.
     */
    private static String labelOf(View button) {
        CharSequence description = button.getContentDescription();
        if (description != null && description.length() > 0) {
            return description.toString();
        }
        return "view:" + System.identityHashCode(button);
    }

    /**
     * RecyclerView measures its children itself and does not honour {@code GONE}, so a
     * hidden row also needs a zero height to actually collapse. Nothing is written when
     * the row is already in the wanted state, which is what keeps the layout listener
     * from triggering itself.
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

    /** Resolves a resource id by name once and remembers it, including a miss. */
    private static int identifier(Activity activity, String name) {
        Integer cached = RESOLVED_IDS.get(name);
        if (cached != null) {
            return cached;
        }

        int id;
        try {
            id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        } catch (Throwable ignored) {
            id = 0;
        }
        if (id == 0) {
            Logger.printInfo(() -> "Inbox view id '" + name + "' not found in this TikTok build");
        }
        RESOLVED_IDS.put(name, id);
        return id;
    }
}
