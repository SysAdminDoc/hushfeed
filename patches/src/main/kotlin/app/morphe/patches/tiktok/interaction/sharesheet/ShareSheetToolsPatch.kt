/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.sharesheet

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.inbox.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/share/ShareSheetTools;"

@Suppress("unused")
val shareSheetToolsPatch = bytecodePatch(
    name = "Share sheet tools",
    description = "Asks for a second tap before a video is sent to a friend from the share " +
        "sheet, and hides chosen people or share options, or the whole Send to row. " +
        "Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        hookShareModel()
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableShareSheet()V",
        )

        // p0 is the activity. /range because a parameter register is usually above v15.
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Activity;)V",
        )
    }
}
