/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/inbox/InboxControls;"

/** Shares the `hide_inbox_stories` switch with the row hiding in `Hide inbox items`. */
@Suppress("unused")
val hideInboxStoriesPatch = bytecodePatch(
    name = "Hide inbox stories",
    description = "Stops the stories tray at the top of the Inbox from being built. Shares " +
        "its switch with Hide inbox items. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideInboxStories()V",
        )

        InboxSkylightWidgetEnableFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldShowInboxStories()Z
                move-result v0
                return v0
            """,
        )
    }
}
