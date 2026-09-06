/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.duetstitch

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val EXTENSION = "Lapp/morphe/extension/tiktok/misc/DuetStitch;"
private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

/**
 * The creator's own choice for the video, which the app reads straight off the model. Both
 * the model and these two getters kept their names. The account level pair on User is left
 * alone: that one is the owner's setting for their own posts and shows in their own
 * settings screen.
 */
private object DuetSettingFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "getDuetSetting",
    returnType = "I",
    parameters = emptyList(),
)

private object StitchSettingFingerprint : Fingerprint(
    definingClass = AWEME,
    name = "getStitchSetting",
    returnType = "I",
    parameters = emptyList(),
)

@Suppress("unused")
val duetStitchPatch = bytecodePatch(
    name = "Allow Duet and Stitch",
    description = "Ignores the creator's Duet and Stitch setting so the entries appear for " +
        "videos that closed them. Everything else the app checks still applies: a photo " +
        "post, a private video or one with music it may not reuse is still refused, and " +
        "whether the upload is accepted is the server's decision, not the app's. " +
        "Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        for (fingerprint in listOf(DuetSettingFingerprint, StitchSettingFingerprint)) {
            fingerprint.method.apply {
                // A getter this small can be compiled with only its own receiver, and then v0
                // is that receiver rather than a spare.
                check(implementation!!.registerCount > 1) {
                    "Allow Duet and Stitch: ${fingerprint.name} has no free local register."
                }
                addInstructions(
                    0,
                    """
                        invoke-static {}, $EXTENSION->allow()Z
                        move-result v0
                        if-eqz v0, :morphe_keep_setting
                        const/4 v0, 0x0
                        return v0
                        :morphe_keep_setting
                        nop
                    """,
                )
            }
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDuetStitch()V",
        )
    }
}
