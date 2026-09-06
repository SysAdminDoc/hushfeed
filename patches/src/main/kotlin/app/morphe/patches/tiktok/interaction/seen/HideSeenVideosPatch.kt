/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows BlueDragon4251/tiktok-patches-for-morphe.
 */
package app.morphe.patches.tiktok.interaction.seen

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.feedfilter.feedFilterPatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val HISTORY_DESCRIPTOR = "Lapp/morphe/extension/tiktok/seen/SeenVideoHistory;"

/** The player's progress callback, which carries the video id, position and duration. */
private object PlayerProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "J", "J"),
    custom = { method, classDef ->
        method.name == "onPlayProgressChange" && classDef.endsWith("/PlayerController;")
    },
)

/**
 * Records what has been watched and drops it from later feed pages.
 *
 * Only the recording side needs bytecode. The filtering rides on the feed filter's own
 * list, so every feed path that patch already covers gets it for free, instead of a second
 * pass over the same responses.
 */
@Suppress("unused")
val hideSeenVideosPatch = bytecodePatch(
    name = "Hide already seen videos",
    description = "Keeps a local record of the videos you have watched and drops them from " +
        "later feed pages. The record never leaves the device and can be cleared from " +
        "settings. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch, feedFilterPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSeenVideoFilter()V",
        )

        // p1 is the video id, p2 and p4 the wide position and duration.
        PlayerProgressFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p1 .. p5}, " +
                "$HISTORY_DESCRIPTOR->onPlayProgressChange(Ljava/lang/String;JJ)V",
        )
    }
}
