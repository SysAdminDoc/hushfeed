/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.valueIn
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FEATURE_CONTROLS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;"

@Suppress("unused")
val hideQuickCommentReactionsPatch = bytecodePatch(
    name = "Hide quick comment reactions",
    description = "Hides TikTok's exposed quick emoji row in supported comment inputs.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideCommentQuickReactions()V",
        )

        resolveQuickCommentReactionGate().apply {
            // The register each return hands back is read off the return itself. It was
            // written as v0, which held only because the gate compiled to one register: a
            // gate returning in v1 would have had its override computed from v0 and written
            // to v0, and returned the untouched original.
            findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN }.forEach { returnIndex ->
                val gateRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                // p0 is a parameter register, which a plain invoke cannot name on a host
                // method with enough locals to push it past v15; the same goes for the gate's
                // own register, and callThroughLocals stages whichever does not fit.
                val call = callThroughLocals(
                    "Hide quick comment reactions",
                    "invoke-static",
                    "$FEATURE_CONTROLS_DESCRIPTOR->overrideHideQuickCommentEmoji(ZI)Z",
                    valueIn("v$gateRegister"),
                    valueIn("p0"),
                )
                addInstructions(returnIndex, "$call\nmove-result v$gateRegister")
            }
        }
    }
}
