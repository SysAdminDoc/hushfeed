/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.update

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities

private const val MAX_VERSION_CODE = Int.MAX_VALUE

@Suppress("unused")
val hidePlayStoreUpdatePatch = resourcePatch(
    name = "Hide Play Store update offer",
    description = "Gives the patched APK the highest Android version code so Play shows Open instead of Update. " +
        "TikTok's visible version stays 47.0.3 in Morphe Manager. Off by default. " +
        "Android won't install a lower-code APK over it, and uninstalling to return to a lower code can remove local TikTok data.",
    default = false,
) {
    category("Performance")
    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        document("AndroidManifest.xml").use { xml ->
            val manifest = xml.documentElement
            val attributes = manifest.attributes
            val versionCode = (0 until attributes.length)
                .map { attributes.item(it) }
                .singleOrNull { it.nodeName.substringAfterLast(':') == "versionCode" }
                ?: throw PatchException("TikTok manifest has no version code")
            if (versionCode.nodeValue != AppCompatibilities.TIKTOK_4703_VERSION_CODE.toString()) {
                throw PatchException("Unexpected TikTok version code ${versionCode.nodeValue}; refusing to change it")
            }
            versionCode.nodeValue = MAX_VERSION_CODE.toString()
        }
    }
}
