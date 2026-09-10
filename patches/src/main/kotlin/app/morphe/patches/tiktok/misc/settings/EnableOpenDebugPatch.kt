package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val CRASH_CAPTURE_INITIALIZER =
    "Lapp/morphe/extension/tiktok/diagnostics/JavaCrashCapture;->initialize(Landroid/content/Context;)V"

@Suppress("unused")
val enableOpenDebugPatch = bytecodePatch(
    name = "Diagnostic tools",
    description = "Adds optional Morphe diagnostic logging, filtered reports, and local TikTok crash capture.",
    default = false,
) {
    dependsOn(sharedExtensionPatch, settingsPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDiagnostics()V",
        )

        val npthExtent = NpthExtentTaskInitFingerprint.method
        // The fingerprint holds the context to the first and only parameter, so it is p0 on a
        // static method and p1 behind `this` on an instance one. Both are named here rather
        // than assumed: a frame past v15 cannot be reached by the plain invoke below.
        val contextRegister =
            if (npthExtent.accessFlags and AccessFlags.STATIC.value != 0) "p0" else "p1"
        val contextIndex = npthExtent.implementation!!.registerCount -
            npthExtent.numberOfParameterRegisters +
            (if (npthExtent.accessFlags and AccessFlags.STATIC.value != 0) 0 else 1)
        check(contextIndex <= 15) {
            "Diagnostics: NpthExtentTask.LIZ keeps its context in v$contextIndex, past what " +
                "the plain invoke can name."
        }
        val returnIndices = npthExtent.implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }

        returnIndices.asReversed().forEach { returnIndex ->
            npthExtent.addInstruction(
                returnIndex,
                "invoke-static {$contextRegister}, $CRASH_CAPTURE_INITIALIZER",
            )
        }
    }
}
