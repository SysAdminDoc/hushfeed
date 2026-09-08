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
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

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
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        for (fingerprint in listOf(DuetSettingFingerprint, StitchSettingFingerprint)) {
            fingerprint.method.apply {
                // The value is taken as the getter hands it back, rather than the getter being
                // answered before it reads anything, because the number it returns says which
                // of two refusals applies and only one of them is ours to answer.
                val returns = implementation!!.instructions.withIndex()
                    .filter { it.value.opcode == Opcode.RETURN }
                    .map { it.index }
                    .toList()
                check(returns.isNotEmpty()) {
                    "Allow Duet and Stitch: ${fingerprint.name} does not return a value."
                }
                returns.asReversed().forEach { index ->
                    val setting = getInstruction<OneRegisterInstruction>(index).registerA
                    check(setting <= 15) {
                        "Allow Duet and Stitch: ${fingerprint.name} returns from v$setting, " +
                            "which move-result cannot reach."
                    }
                    addInstructions(
                        index,
                        """
                            invoke-static { v$setting }, $EXTENSION->setting(I)I
                            move-result v$setting
                        """,
                    )
                }
            }
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDuetStitch()V",
        )
    }
}
