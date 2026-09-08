/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.feedtoolbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch

private const val LIVE_ICON_GENERATOR_DESCRIPTOR =
    "Lcom/bytedance/tiktok/homepage/mainfragment/toolbar/LiveIconGenerator;"

private object LiveIconEnabledFingerprint : Fingerprint(
    definingClass = LIVE_ICON_GENERATOR_DESCRIPTOR,
    name = "enabled",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val hideFeedLiveButtonPatch = bytecodePatch(
    name = "Hide feed LIVE button",
    description = "Adds an option to hide the LIVE button at the top left of video feeds. " +
        "Shares its switch with the Live entrance option of Hide video overlays, and stops the " +
        "button before it is built rather than hiding it once it is on screen.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideFeedLiveButton()V",
        )
        LiveIconEnabledFingerprint.method.overrideToolbarButtonEnabled(
            "hideFeedLiveButtonEnabled",
        )
    }
}
