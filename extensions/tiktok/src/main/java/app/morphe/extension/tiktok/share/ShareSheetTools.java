/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.share;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.shared.GlobalLayoutHook;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceIdCache;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.tiktok.interaction.TapConfirmation;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Share sheet tools: a confirm step before a video goes to a friend, and hiding of chosen
 * people or share options, or the whole "Send to" row.
 *
 * Ids were read off the live view hierarchy with the share sheet open. TikTok 47.0.3 moved
 * the panel into its own window and renamed all four anchors. The 46.x names (ibc, u3t, dqr,
 * a59) are not kept as fallbacks: on 47.0.3 each of them names some other view.
 * <pre>
 *   ip5   frame around the "Send to" contacts row
 *   v3j   the contacts list; each child is the contact cell, content description
 *         = the display name, and is itself clickable
 *   dwr   the share channels row (Repost, Copy link, SMS, Facebook, ...)
 *   a5t   the actions row (Report, Not interested, Download, Create group, ...)
 * </pre>
 * Every cell in the three rows carries its label as its content description, which is
 * what the hidden list matches against.
 *
 * The patch binds each recycled contact cell to the model's stable user or conversation id.
 * TikTok's common click dispatcher asks {@link #allowRecipientClick(View)} before it invokes
 * the native send callback, so touch, keyboard and accessibility activation all take the same
 * path and the native callback still runs exactly once after confirmation.
 */
public final class ShareSheetTools {
    private static final String APP_PACKAGE = "com.zhiliaoapp.musically";
    /** One family for everything that touches the sheet, so an export reads as one surface. */
    private static final String FAMILY = ShareModelFilter.FAMILY;
    private static final String[] CONTACTS_SECTION_IDS = {"ip5"};
    private static final String[] CONTACTS_LIST_IDS = {"v3j"};
    private static final String[] CHANNELS_LIST_IDS = {"dwr"};
    private static final String[] ACTIONS_LIST_IDS = {"a5t"};

    /**
     * How long a first tap stays armed before a second tap is needed again. The settings row
     * formats this number in, so the row and the window cannot drift apart.
     */
    public static final int ARM_WINDOW_SECONDS = 4;
    private static final long ARM_WINDOW_MS = ARM_WINDOW_SECONDS * 1000L;

    interface ConfirmationSettingReader {
        boolean enabled();
    }

    private static final ConfirmationSettingReader DEFAULT_CONFIRMATION_SETTING_READER =
            () -> Settings.SHARE_CONFIRM_SEND.get();
    private static ConfirmationSettingReader confirmationSettingReader =
            DEFAULT_CONFIRMATION_SETTING_READER;

    private static final ResourceIdCache RESOURCE_IDS = new ResourceIdCache();

    /** Original layout width of each cell this class has shrunk, so it can be restored. */
    private static final WeakHashMap<View, Integer> ORIGINAL_WIDTHS = new WeakHashMap<>();

    /** The current model identity for each recycled native contact cell. */
    private static final WeakHashMap<View, RecipientBinding> RECIPIENTS = new WeakHashMap<>();

    private static WeakReference<Activity> activityReference = new WeakReference<>(null);
    private static final GlobalLayoutHook LAYOUT_HOOK = new GlobalLayoutHook();

    private static String armedRecipientId;
    private static long armedAtMs;
    private static WeakReference<View> armedCell = new WeakReference<>(null);
    private static Drawable armedPreviousForeground;
    private static Drawable armedRing;
    private static int armGeneration;
    private static boolean applyPosted;

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
            HookStatus.threw(FAMILY, "install", ex);
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

            List<View> roots = windowRoots(activity);
            View contacts = find(activity, roots, CONTACTS_LIST_IDS);
            if (contacts == null) {
                // The sheet is closed. Its cells are gone, so the armed state is stale.
                disarm();
                RECIPIENTS.clear();
            }

            List<String> hidden = entries(ShareModelFilter.hiddenItems());
            boolean confirm = Settings.SHARE_CONFIRM_SEND.get();
            if (!confirm) {
                disarm();
            }

            View contactsSection = find(activity, roots, CONTACTS_SECTION_IDS);
            boolean hideContacts = Settings.HIDE_SHARE_CONTACTS.get();
            if (contactsSection != null) {
                setVisible(contactsSection, !hideContacts);
            }
            if (!hideContacts && contacts instanceof ViewGroup) {
                ViewGroup list = (ViewGroup) contacts;
                for (int index = 0; index < list.getChildCount(); index++) {
                    View cell = list.getChildAt(index);
                    setCellHidden(cell, matches(hidden, labelOf(cell)));
                }
            }

            hideByLabel(find(activity, roots, CHANNELS_LIST_IDS), hidden);
            hideByLabel(find(activity, roots, ACTIONS_LIST_IDS), hidden);
        } catch (Throwable ex) {
            HookStatus.threw(FAMILY, "layout pass", ex);
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

    /**
     * Called from the patched contact adapter each time a holder is bound. The AndroidX holder
     * stays an Object here because AndroidX is owned by the host and is not part of the extension
     * compile classpath. Its public {@code itemView} field is the native row the click dispatcher
     * later supplies.
     */
    public static void bindRecipient(Object holder, Object contact) {
        try {
            View cell = itemViewOf(holder);
            if (cell != null) {
                bindRecipientView(cell, contact);
            }
        } catch (Throwable ex) {
            HookStatus.threw(FAMILY, "recipient bind", ex);
            Logger.printException(() -> "Could not bind a share recipient", ex);
        }
    }

    /**
     * The one gate in front of TikTok's native recipient callback. Returning false ends the
     * native dispatcher before its Function0 is invoked; returning true lets that same dispatcher
     * invoke it. Nothing here synthesizes another click.
     */
    public static boolean allowRecipientClick(View touched) {
        final boolean confirmationEnabled;
        try {
            confirmationEnabled = confirmationSettingReader.enabled();
        } catch (Throwable ex) {
            try {
                disarm();
            } catch (Throwable cleanupEx) {
                Logger.printException(() -> "Could not clear unread share confirmation", cleanupEx);
            }
            HookStatus.threw(FAMILY, "confirm setting", ex);
            Logger.printException(() -> "Could not read share confirmation setting", ex);
            // Repost, Copy link, Save and the rest of the share channels come through this same
            // native dispatcher. A setting nobody can read is not a reason to eat their taps,
            // and a sheet whose every button does nothing is exactly what a broken patch looks
            // like from the outside. Hold the tap only where it is a person being sent to.
            return !isBoundRecipient(touched);
        }

        if (!confirmationEnabled) {
            try {
                disarm();
            } catch (Throwable ex) {
                Logger.printException(() -> "Could not clear disabled share confirmation", ex);
            }
            return true;
        }

        try {
            View cell = boundCellOf(touched);
            RecipientBinding binding = RECIPIENTS.get(cell);
            if (binding == null) {
                // Not a recipient we bound. Repost, Copy link, Save and the other
                // share-channel actions reach this same native dispatcher, and none of them
                // is a person to confirm before sending to. Let the first tap through
                // instead of arming a "tap again to send". Reposting a video used to arm
                // this step and ask for a second tap before it would repost.
                disarm();
                return true;
            }
            String recipientId = binding.id;
            String name = labelOf(cell);
            long now = SystemClock.uptimeMillis();
            boolean armed = cell == armedCell.get()
                    && recipientId.equals(armedRecipientId)
                    && now - armedAtMs < ARM_WINDOW_MS;
            if (armed) {
                disarm();
                Logger.printDebug(() -> "Share recipient confirmed");
                return true;
            }

            arm(cell, recipientId, name);
            return false;
        } catch (Throwable ex) {
            HookStatus.threw(FAMILY, "confirm step", ex);
            Logger.printException(() -> "Share confirm step failed", ex);
            // Confirmation failures must consume the activation instead of reaching native send.
            try {
                disarm();
            } catch (Throwable cleanupEx) {
                Logger.printException(() -> "Could not clear failed share confirmation", cleanupEx);
            }
            // Same reasoning as the setting read above: a share channel is not a person, so a
            // failure here leaves it to TikTok rather than making the sheet look broken.
            return !isBoundRecipient(touched);
        }
    }

    /**
     * Whether this activation is on a cell bound to a person, decided without touching anything
     * that can throw again. Called from the catch blocks, so it answers false on its own failure:
     * an unknown cell is treated as a share channel and left to TikTok.
     */
    private static boolean isBoundRecipient(View touched) {
        try {
            return RECIPIENTS.get(boundCellOf(touched)) != null;
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not tell a share recipient from a share action", ex);
            return false;
        }
    }

    static void bindRecipientView(View cell, Object contact) {
        if (cell == null) {
            return;
        }
        if (armedCell.get() == cell) {
            disarm();
        }
        if (contact == null) {
            RECIPIENTS.remove(cell);
            return;
        }
        RECIPIENTS.put(cell, new RecipientBinding(stableRecipientId(contact)));
        // 47.0.3 presents the panel in another WindowManager root, so the activity root's
        // global-layout listener does not reliably observe its first layout. A recipient bind
        // happens while that window is being built; run one coalesced pass after the bind.
        requestApply();
    }

    private static void arm(View cell, String recipientId, String name) {
        disarm();
        armedRecipientId = recipientId;
        armedAtMs = SystemClock.uptimeMillis();
        armedCell = new WeakReference<>(cell);
        armedPreviousForeground = cell.getForeground();

        armedRing = TapConfirmation.armedRing(cell);
        cell.setForeground(armedRing);
        cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        Utils.showToastShort(name == null
                ? L10n.t("Tap again to send")
                : L10n.f("Tap %1$s again to send", name));

        final int token = armGeneration;
        Utils.runOnMainThreadDelayed(() -> {
            if (armGeneration == token) {
                disarm();
            }
        }, ARM_WINDOW_MS);
    }

    private static void disarm() {
        View cell = armedCell.get();
        Drawable ring = armedRing;
        Drawable previousForeground = armedPreviousForeground;
        armedRecipientId = null;
        armedAtMs = 0;
        armedCell = new WeakReference<>(null);
        armedPreviousForeground = null;
        armedRing = null;
        armGeneration++;
        if (cell != null && cell.getForeground() == ring) {
            cell.setForeground(previousForeground);
        }
    }

    private static View itemViewOf(Object holder) throws ReflectiveOperationException {
        if (holder == null) {
            return null;
        }
        Class<?> type = holder.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("itemView");
                field.setAccessible(true);
                Object value = field.get(holder);
                return value instanceof View ? (View) value : null;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String stableRecipientId(Object contact) {
        String conversation = stringMethod(contact, "getConversationId");
        if (conversation != null) {
            return "conversation:" + conversation;
        }
        String user = stringMethod(contact, "getUid");
        if (user != null) {
            return "user:" + user;
        }
        // Special action contacts do not identify a person. Object identity is conservative:
        // it may ask again after a rebuild, but it can never confirm a different model instance.
        return "model:" + contact.getClass().getName() + ":" + System.identityHashCode(contact);
    }

    private static String stringMethod(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            Object value = method.invoke(target);
            if (value == null) {
                return null;
            }
            String text = value.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static View boundCellOf(View view) {
        View current = view;
        for (int depth = 0; current != null && depth < 5; depth++) {
            if (RECIPIENTS.containsKey(current)) {
                return current;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return cellOf(view);
    }

    static void resetForTests() {
        disarm();
        RECIPIENTS.clear();
        confirmationSettingReader = DEFAULT_CONFIRMATION_SETTING_READER;
    }

    static void setConfirmationSettingReaderForTests(ConfirmationSettingReader reader) {
        confirmationSettingReader = reader == null
                ? DEFAULT_CONFIRMATION_SETTING_READER
                : reader;
    }

    private static final class RecipientBinding {
        final String id;

        RecipientBinding(String id) {
            this.id = id;
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

    private static synchronized void requestApply() {
        if (applyPosted) return;
        applyPosted = true;
        Utils.runOnMainThread(() -> {
            synchronized (ShareSheetTools.class) {
                applyPosted = false;
            }
            apply();
        });
    }

    /** Chooses the newest candidate that occurs in one of the app's current windows. */
    private static View find(Activity activity, List<View> roots, String[] candidates) {
        if (activity == null) return null;
        boolean resolvedAny = false;
        String diagnostic = String.join("|", candidates);
        for (String name : candidates) {
            int id = RESOURCE_IDS.resolve(activity.getResources(), APP_PACKAGE, name, false);
            if (id == 0) continue;
            resolvedAny = true;
            for (View root : roots) {
                View found = root == null ? null : root.findViewById(id);
                if (found == null) continue;
                HookStatus.recoveredViewId(FAMILY, diagnostic);
                HookStatus.bound(FAMILY, name);
                return found;
            }
        }
        // The sheet is normally absent, so a resolved id with no current view is not a miss.
        if (!resolvedAny) HookStatus.missingViewId(FAMILY, diagnostic);
        return null;
    }

    /** Activity content plus dialog and bottom-sheet roots currently owned by this process. */
    @SuppressWarnings("unchecked")
    private static List<View> windowRoots(Activity activity) {
        List<View> roots = new ArrayList<>();
        Set<View> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        View decor = activity == null || activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        if (decor != null && seen.add(decor)) roots.add(decor);
        if (windowViewsUnavailable) return roots;
        try {
            Object value = windowViews();
            if (value instanceof List) {
                for (Object candidate : (List<Object>) value) {
                    if (candidate instanceof View && seen.add((View) candidate)) {
                        roots.add((View) candidate);
                    }
                }
            }
        } catch (Throwable ex) {
            // The activity root still covers retained 46.x builds and every share action filtered
            // at the model layer. A non-SDK lookup failure must not break the share sheet, and it
            // is not retried: this runs on every layout pass, and a refusal stays a refusal. Once
            // the lookup has worked, a failure is the read itself, a window list changing under
            // the walk say, and the next pass reads it again.
            if (windowViewsReader == null) windowViewsUnavailable = true;
            Logger.printDebug(() -> "Could not enumerate secondary share sheet windows: "
                    + ex.getClass().getSimpleName());
        }
        return roots;
    }

    /*
     * WindowManagerGlobal and the read of its window list, looked up once. apply() runs on every
     * layout pass of the main window, sheet open or not, and it used to repeat the class lookup,
     * two method lookups and the access changes each time. Main thread only.
     */
    private static Object windowGlobal;
    private static Object windowViewsReader;
    private static boolean windowViewsUnavailable;

    private static Object windowViews() throws ReflectiveOperationException {
        if (windowViewsReader == null) {
            Class<?> globalClass = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = globalClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            Object reader;
            try {
                Method getWindowViews = globalClass.getDeclaredMethod("getWindowViews");
                getWindowViews.setAccessible(true);
                reader = getWindowViews;
            } catch (NoSuchMethodException missingMethod) {
                Field views = globalClass.getDeclaredField("mViews");
                views.setAccessible(true);
                reader = views;
            }
            windowGlobal = global;
            windowViewsReader = reader;
        }
        return windowViewsReader instanceof Method
                ? ((Method) windowViewsReader).invoke(windowGlobal)
                : ((Field) windowViewsReader).get(windowGlobal);
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
