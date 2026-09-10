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
 * the Settings patch reads it. Null rather than zero, because a shared-library package declares
 * itself with id zero and its ids are assigned at load time, so a zero id is a real answer for
 * some tables and would be indistinguishable from having read nothing.
 */
internal var settingsIconResourceId: Int? = null
    private set

/**
 * Looks the settings icon up by name in the resource table the APK actually carries.
 *
 * <p>The Settings patch used to write the id as a literal, `0x7f010088`. A resource id is
 * renumbered by every build, so that was the build the literal came from written into the patch,
 * and it would have gone wrong the first time another raw resource sorted ahead of this one. The
 * name is what a build keeps.
 *
 * <p>What this costs, measured on the 46.2.3 fixture: asking for any resource makes the patcher
 * decode in raw mode, which unpacks the APK before the first patch runs. That is 17 seconds and
 * 978 MB of temporary files, on every job, because Settings is the dependency of most of the
 * bundle. A raw resource patch is the cheapest way to read a resource at all: the full resource
 * mode decodes and re-encodes every table as well, which is where the AMOLED patch gets a heap
 * requirement nothing else in the bundle has. The alternative is not a cheaper read, it is
 * writing an id into the patch again, and that is the defect this replaced.
 *
 * <p>Unnamed, so it is not a patch anyone can select or deselect; it exists for the one that
 * depends on it.
 */
internal val settingsIconResourcePatch = rawResourcePatch {
    execute {
        // The parse and the lookup belong inside this too: every bound in the reader comes from
        // a size the table declares, so a truncated one throws out of the walk, and a raw
        // IndexOutOfBounds is not something a person can act on.
        val id = try {
            ResourceTable.parse(get("resources.arsc").readBytes())
                .idOf(SETTINGS_ICON_TYPE, SETTINGS_ICON_NAME, packageMetadata.packageName)
        } catch (error: Exception) {
            throw PatchException("Settings: could not read resources.arsc from the APK: ${error.message}")
        }
        settingsIconResourceId = id
            ?: throw PatchException(
                "Settings: this build has no $SETTINGS_ICON_TYPE/$SETTINGS_ICON_NAME, so the " +
                    "Hushfeed row has no icon to borrow.",
            )
    }
}
