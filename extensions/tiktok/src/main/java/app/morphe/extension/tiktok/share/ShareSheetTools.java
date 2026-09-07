/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.share;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Share sheet tools: a confirm step before a video goes to a friend, and hiding of chosen
 * people or share options, or the whole "Send to" row.
 *
 * Ids were read off the live view hierarchy of TikTok 46.2.3 with the share sheet open.
 * The sheet lives in the activity's own window, under the content view:
 * <pre>
 *   ibc   frame around the "Send to" contacts row
 *   u3t   the contacts RecyclerView; each child is the contact cell, content description
 *         = the display name, and is itself clickable
 *   bku   the avatar inside a cell, clickable
 *   p78   the name under the avatar, clickable
 *   dqr   the share channels row (Repost, Copy link, SMS, Facebook, ...)
 *   a59   the actions row (Report, Not interested, Download, Create group, ...)
 * </pre>
 * Every cell in the three rows carries its label as its content description, which is
 * what the hidden list matches against.
 *
 * The confirm step is a touch listener on the three clickable views of each contact
 * cell. It swallows the tap so TikTok's own click never runs, and only forwards a second
 * tap on the same person within a few seconds by calling {@code performClick} on the view
 * the user touched. Horizontal scrolling of the row still works, because the parent
 * RecyclerView intercepts a drag before it reaches the cell.
 */
public final class ShareSheetTools {
    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    private static final String CONTACTS_SECTION_ID = "ibc";
    private static final String CONTACTS_LIST_ID = "u3t";
    private static final String CONTACT_AVATAR_ID = "bku";
    private static final String CONTACT_NAME_ID = "p78";
    private static final String CHANNELS_LIST_ID = "dqr";
    private static final String ACTIONS_LIST_ID = "a59";

    /** How long a first tap stays armed before a second tap is needed again. */
    private static final long ARM_WINDOW_MS = 4000;

    private static final ResourceIdCache RESOURCE_IDS = new ResourceIdCache();

    /** Original layout width of each cell this class has shrunk, so it can be restored. */
    private static final WeakHashMap<View, Integer> ORIGINAL_WIDTHS = new WeakHashMap<>();

    private static final ConfirmTouchListener CONFIRM = new ConfirmTouchListener();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static final GlobalLayoutHook LAYOUT_HOOK = new GlobalLayoutHook();

    private static String armedName;
    private static long armedAtMs;
    private static WeakReference<View> armedCell = new WeakReference<>(null);

    private ShareSheetTools() {
    }

    /** Called from the patched {@code MainActivity.onCreate}; the work is posted. */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        Utils.runOnMainThread(() -> installNow(activity));
    }

    private static void installNow(Activity activity) {
        try {
            if (activity.isFinishing()) {
                LAYOUT_HOOK.detach();
                return;
            }
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) {
                LAYOUT_HOOK.detach();
                Logger.printInfo(() -> "Share sheet tools found no content view to watch");
                return;
            }
            boolean installed = LAYOUT_HOOK.install(root, ShareSheetTools::apply);
            activityReference = new WeakReference<>(activity);
            if (installed) {
                Logger.printDebug(() -> "Share sheet tools installed");
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not install the share sheet tools", ex);
        }
    }

    private static void apply() {
        try {
            Activity activity = activityReference.get();
            if (activity == null) {
                LAYOUT_HOOK.detach();
                return;
            }
            if (activity.isFinishing()) {
                LAYOUT_HOOK.detach();
                return;
            }

            View contacts = find(activity, CONTACTS_LIST_ID);
            if (contacts == null) {
                // The sheet is closed. Its cells are gone, so the armed state is stale.
                if (armedName != null) {
                    disarm();
                }
            }

            List<String> hidden = entries(Settings.SHARE_HIDDEN_ITEMS.get());
            boolean confirm = Settings.SHARE_CONFIRM_SEND.get();

            View contactsSection = find(activity, CONTACTS_SECTION_ID);
            boolean hideContacts = Settings.HIDE_SHARE_CONTACTS.get();
            if (contactsSection != null) {
                setVisible(contactsSection, !hideContacts);
            }
            if (!hideContacts && contacts instanceof ViewGroup) {
                ViewGroup list = (ViewGroup) contacts;
                for (int index = 0; index < list.getChildCount(); index++) {
                    View cell = list.getChildAt(index);
                    setCellHidden(cell, matches(hidden, labelOf(cell)));
                    if (confirm) {
                        guard(activity, cell);
                    }
                }
            }

            hideByLabel(find(activity, CHANNELS_LIST_ID), hidden);
            hideByLabel(find(activity, ACTIONS_LIST_ID), hidden);
        } catch (Throwable ex) {
            Logger.printException(() -> "Share sheet tools failed", ex);
        }
    }

    // ---- hiding ------------------------------------------------------------------------

    private static void hideByLabel(View list, List<String> hidden) {
        if (!(list instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) list;
        for (int index = 0; index < group.getChildCount(); index++) {
            View cell = group.getChildAt(index);
            setCellHidden(cell, matches(hidden, labelOf(cell)));
        }
    }

    /**
     * A RecyclerView lays a GONE child out at its full size, so the width goes to zero as
     * well. Cells are recycled, so the original width is kept and put back when the same
     * view later shows something that is not hidden.
     */
    static void setCellHidden(View cell, boolean hidden) {
        if (cell == null) {
            return;
        }
        ViewGroup.LayoutParams params = cell.getLayoutParams();
        if (params == null) {
            setVisible(cell, !hidden);
            return;
        }

        Integer original = ORIGINAL_WIDTHS.get(cell);
        if (hidden) {
            if (original == null) {
                ORIGINAL_WIDTHS.put(cell, params.width);
            }
            if (cell.getVisibility() != View.GONE || params.width != 0) {
                cell.setVisibility(View.GONE);
                params.width = 0;
                cell.setLayoutParams(params);
            }
        } else if (original != null) {
            if (cell.getVisibility() != View.VISIBLE || params.width != original) {
                cell.setVisibility(View.VISIBLE);
                params.width = original;
                cell.setLayoutParams(params);
            }
        }
    }

    private static void setVisible(View view, boolean visible) {
        int wanted = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != wanted) {
            view.setVisibility(wanted);
        }
    }

    private static boolean matches(List<String> hidden, String label) {
        if (label == null || hidden.isEmpty()) {
            return false;
        }
        for (String entry : hidden) {
            if (entry.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    // ---- confirm before sending --------------------------------------------------------

    private static void guard(Activity activity, View cell) {
        if (cell == null) {
            return;
        }
        cell.setOnTouchListener(CONFIRM);
        View avatar = cell.findViewById(identifier(activity, CONTACT_AVATAR_ID));
        if (avatar != null) {
            avatar.setOnTouchListener(CONFIRM);
        }
        View name = cell.findViewById(identifier(activity, CONTACT_NAME_ID));
        if (name != null) {
            name.setOnTouchListener(CONFIRM);
        }
    }

    /**
     * Takes every touch on a contact so TikTok's click listener never sees the first tap.
     * A clean tap arms that person; a second clean tap on the same person inside the
     * window forwards the click. A drag is left alone (the row's RecyclerView has already
     * intercepted it by the time the finger moves far enough).
     */
    private static final class ConfirmTouchListener implements View.OnTouchListener {
        private float downX;
        private float downY;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (!Settings.SHARE_CONFIRM_SEND.get()) {
                return false;
            }
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
                        onTap(view);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    static void onTap(View touched) {
        try {
            View cell = cellOf(touched);
            String name = labelOf(cell);
            if (name == null) {
                // Nothing to confirm against, so behave as TikTok would.
                touched.performClick();
                return;
            }

            boolean armed = name.equals(armedName)
                    && SystemClock.elapsedRealtime() - armedAtMs <= ARM_WINDOW_MS;
            if (armed) {
                disarm();
                Logger.printDebug(() -> "Share confirmed for " + name);
                if (!touched.performClick() && cell != touched) {
                    cell.performClick();
                }
                return;
            }

            arm(cell, name);
        } catch (Throwable ex) {
            Logger.printException(() -> "Share confirm step failed", ex);
        }
    }

    private static void arm(View cell, String name) {
        disarm();
        armedName = name;
        armedAtMs = SystemClock.elapsedRealtime();
        armedCell = new WeakReference<>(cell);

        float density = cell.getResources().getDisplayMetrics().density;
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.RECTANGLE);
        ring.setCornerRadius(12 * density);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.round(2 * density), Color.rgb(254, 44, 85));
        cell.setForeground(ring);
        cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        Utils.showToastShort(L10n.f("Tap %1$s again to send", name));

        final long stamp = armedAtMs;
        Utils.runOnMainThreadDelayed(() -> {
            if (armedAtMs == stamp) {
                disarm();
            }
        }, ARM_WINDOW_MS);
    }

    private static void disarm() {
        armedName = null;
        armedAtMs = 0;
        View cell = armedCell.get();
        armedCell = new WeakReference<>(null);
        if (cell != null) {
            cell.setForeground(null);
        }
    }

    /** The contact cell is the nearest ancestor (or the view itself) that carries a label. */
    static View cellOf(View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 5; depth++) {
            if (labelOf(current) != null) {
                return current;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return view;
    }

    static String labelOf(View view) {
        if (view == null) {
            return null;
        }
        CharSequence description = view.getContentDescription();
        if (description == null) {
            return null;
        }
        String label = description.toString().trim();
        return label.isEmpty() ? null : label;
    }

    // ---- lookup ------------------------------------------------------------------------

    private static View find(Activity activity, String name) {
        int id = identifier(activity, name);
        return id == 0 ? null : activity.findViewById(id);
    }

    private static int identifier(Activity activity, String name) {
        int id = RESOURCE_IDS.resolve(
                activity == null ? null : activity.getResources(), APP_PACKAGE, name, false);
        if (id == 0) {
            HookStatus.missingViewId("share sheet", name);
        } else {
            HookStatus.bound("share sheet", "view id '" + name + "'");
        }
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
                entries.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return entries;
    }
}
