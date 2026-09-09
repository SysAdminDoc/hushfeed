/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/spoof/sim/SpoofSimPatch.kt
 */

package app.morphe.patches.tiktok.misc.spoof.sim

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/spoof/sim/SpoofSimPatch;"

@Suppress("unused")
val simSpoofPatch = bytecodePatch(
    name = "SIM spoof",
    description = "Spoofs SIM country and operator information retrieved by TikTok, with country presets for easier setup.",
    default = true,
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        val replacements = mapOf(
            "getSimCountryIso" to "getCountryIso",
            "getNetworkCountryIso" to "getCountryIso",
            "getSimOperator" to "getOperator",
            "getNetworkOperator" to "getOperator",
            "getSimOperatorName" to "getOperatorName",
            "getNetworkOperatorName" to "getOperatorName",
        )

        val patchesByMethod = linkedMapOf<Method, ArrayDeque<Pair<Int, String>>>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@classDefForEach
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue
                implementation.instructions.forEachIndexed { index, instruction ->
                    if (
                        instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                    ) return@forEachIndexed

                    val methodReference = instruction.getReference<MethodReference>()
                        ?: return@forEachIndexed
                    val replacement = when {
                        methodReference.definingClass == "Landroid/telephony/TelephonyManager;" ->
                            replacements[methodReference.name]
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMccString" -> "getMcc"
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMncString" -> "getMnc"
                        else -> null
                    }
                    if (replacement == null || methodReference.returnType != "Ljava/lang/String;") {
                        return@forEachIndexed
                    }
                    if (implementation.instructions.elementAtOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT) {
                        patchesByMethod.getOrPut(method) { ArrayDeque() }.add(index to replacement)
                    }
                }
            }
        }

        patchesByMethod.forEach { (method, patches) ->
            val mutableMethod = mutableClassDefBy(method.definingClass).findMutableMethodOf(method)
            while (patches.isNotEmpty()) {
                val (index, replacement) = patches.removeLast()
                val resultRegister = mutableMethod.getInstruction<OneRegisterInstruction>(index + 1).registerA

                mutableMethod.addInstructions(
                    index + 2,
                    """
                        invoke-static/range { v$resultRegister .. v$resultRegister }, $EXTENSION_CLASS_DESCRIPTOR->$replacement(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$resultRegister
                    """,
                )
            }
        }

        // TikTok's startup SIM check reports a change to its own telemetry: which way the SIM
        // went, how long since the last check, how many SIMs the phone has, and the country and
        // carrier before and after. With the preset on, the before is the phone's real carrier
        // and the after is the preset, so the one event this task exists to send announces that
        // the preset was switched on. The count of SIMs is the one value here the spoof cannot
        // reach, since it comes from SubscriptionManager rather than from TelephonyManager.
        // Nothing else in the app reads what the task stores, so with the preset on it does not
        // run.
        CheckSimChangeTaskFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldSkipSimChangeReport()Z
                    move-result v0
                    if-eqz v0, :run_sim_check
                    return-void
                """,
                ExternalLabel("run_sim_check", getInstruction(0)),
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSimSpoof()V",
        )
    }
}
