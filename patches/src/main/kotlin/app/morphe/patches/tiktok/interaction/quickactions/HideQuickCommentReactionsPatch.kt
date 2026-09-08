/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.valueIn
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import com.android.tools.smali.dexlib2.Opcode

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

        QuickCommentReactionGateFingerprint.method.apply {
            implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN }
                .map { it.index }
                .asReversed()
                .forEach { returnIndex ->
                    // p0 is a parameter register, which a plain invoke cannot name on a host
                    // method with enough locals to push it past v15.
                    val call = callThroughLocals(
                        "Hide quick comment reactions",
                        "invoke-static",
                        "$FEATURE_CONTROLS_DESCRIPTOR->overrideHideQuickCommentEmoji(ZI)Z",
                        valueIn("v0"),
                        valueIn("p0"),
                    )
                    addInstructions(returnIndex, "$call\nmove-result v0")
                }
        }
    }
}
