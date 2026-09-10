/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.rawResourcePatch

/** The resource the Hushfeed row borrows for its icon. TikTok's own settings row uses it too. */
internal const val SETTINGS_ICON_TYPE = "raw"
internal const val SETTINGS_ICON_NAME = "icon_2pt_settings_stroke"

/**
 * The id of `raw/icon_2pt_settings_stroke` in the APK being patched. Filled in by
 * [settingsIconResourcePatch], which the Settings patch depends on, so it is set by the time
 * the Settings patch reads it. Zero until then, and zero is never a valid resource id.
 */
internal var settingsIconResourceId: Int = 0
    private set

/**
 * Looks the settings icon up by name in the resource table the APK actually carries.
 *
 * <p>The Settings patch used to write the id as a literal, `0x7f010088`. A resource id is
 * renumbered by every build, so that was the build the literal came from written into the patch,
 * and it would have gone wrong the first time another raw resource sorted ahead of this one. The
 * name is what a build keeps.
 *
 * <p>A raw resource patch rather than a resource patch: the table is read as bytes and walked for
 * one entry, which is a few milliseconds, where decoding every resource in the APK is the step
 * that gives the AMOLED patch a heap requirement nothing else in the bundle has. Settings is the
 * dependency of most of the bundle, so its cost is everybody's cost.
 *
 * <p>Unnamed, so it is not a patch anyone can select or deselect; it exists for the one that
 * depends on it.
 */
internal val settingsIconResourcePatch = rawResourcePatch {
    execute {
        val table = try {
            get("resources.arsc").readBytes()
        } catch (error: Exception) {
            throw PatchException("Settings: could not read resources.arsc from the APK: ${error.message}")
        }
        settingsIconResourceId = ResourceTable.parse(table).idOf(SETTINGS_ICON_TYPE, SETTINGS_ICON_NAME)
            ?: throw PatchException(
                "Settings: this build has no $SETTINGS_ICON_TYPE/$SETTINGS_ICON_NAME, so the " +
                    "Hushfeed row has no icon to borrow.",
            )
    }
}
