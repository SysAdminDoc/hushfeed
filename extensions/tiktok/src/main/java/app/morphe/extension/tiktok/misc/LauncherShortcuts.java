/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.misc;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Build;

import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * What TikTok ends up offering the launcher when its icon is pressed and held.
 *
 * <p>Those entries are not in the manifest, so there is no file to take them out of: the app
 * builds them while it runs and hands them to {@code ShortcutManager}.
 *
 * <p>Sitting on that handover is not enough on its own, which a phone had to say. TikTok publishes
 * the list about once, on the first start after an install, and then skips the write whenever what
 * it would publish matches what is already published. Measured on 46.2.3: with the published list
 * cleared, asking for a refresh wrote two entries; with two already published, asking for a refresh
 * left the platform's own timestamps untouched, so the handover never happened. Anyone who has had
 * the app installed for more than a moment is in the second case, so a switch that only answered
 * the handover would look broken to all of them.
 *
 * <p>So the switch removes what is published, and puts it back. Removing is the app's own call to
 * make about its own shortcuts. Putting it back is TikTok's to make, so that goes through
 * {@link #askHostToRebuild()}, which the patch fills in with a call to the app's own shortcut
 * service: it recomputes, finds the published list empty where its own list is not, and writes.
 *
 * <p>Only the app's own dynamic entries are involved. A shortcut somebody pinned to their home
 * screen is the launcher's, not the app's, and is left where they put it.
 */
public final class LauncherShortcuts {

    private LauncherShortcuts() {}

    /**
     * The list to publish, given the list TikTok built.
     *
     * <p>The handover it sits on is inside the app's own try block, and the setting read is the
     * first touch of the preference store on that path, so a throw here would surface as TikTok's
     * own "add shortcut error" and a reader would never see a cause. It answers with the list
     * TikTok built instead.
     */
    public static List<?> publish(List<?> shortcuts) {
        try {
            if (!Settings.HIDE_LAUNCHER_SHORTCUTS.get()) return shortcuts;
            return Collections.emptyList();
        } catch (Throwable error) {
            Logger.printException(() -> "Could not choose what to offer the launcher", error);
            return shortcuts;
        }
    }

    /**
     * Brings what is published into line with the switch, once per launch.
     *
     * <p>Called from the main activity's onCreate, which is where the extension already takes its
     * context, so the preference store is readable by the time this runs.
     */
    public static void apply(Context context) {
        try {
            // ShortcutManager arrived in 25 and the payload's floor is 23. There is nothing to do
            // below that: a launcher there has no shortcut menu to empty.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return;
            if (context == null) return;
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager == null) return;

            if (Settings.HIDE_LAUNCHER_SHORTCUTS.get()) {
                if (manager.getDynamicShortcuts().isEmpty()) {
                    // Already empty. Still record it, because the switch may have been turned on
                    // while the app was not running.
                    Settings.LAUNCHER_SHORTCUTS_REMOVED.save(true);
                    return;
                }
                manager.removeAllDynamicShortcuts();
                Settings.LAUNCHER_SHORTCUTS_REMOVED.save(true);
                return;
            }

            // Off. TikTok only rebuilds what it publishes when it notices a difference, and it
            // will not notice one it did not make, so it has to be asked. Only asked when this
            // took them away: a reader who never turned the switch on gets TikTok's own behaviour,
            // whatever that is.
            if (!Settings.LAUNCHER_SHORTCUTS_REMOVED.get()) return;
            Settings.LAUNCHER_SHORTCUTS_REMOVED.save(false);
            if (!askHostToRebuild()) {
                Logger.printException(() -> "Could not ask TikTok to rebuild its launcher shortcuts");
            }
        } catch (Throwable error) {
            Logger.printException(() -> "Could not apply the launcher shortcut setting", error);
        }
    }

    /**
     * Asks TikTok to work out its launcher shortcuts again and publish them.
     *
     * <p>The patch replaces this with a call to the app's own shortcut service. Left as it is when
     * the patch is not applied, and when it answers false the caller says so rather than leaving a
     * reader with an empty menu and no reason for it.
     */
    private static boolean askHostToRebuild() {
        return false;
    }
}
