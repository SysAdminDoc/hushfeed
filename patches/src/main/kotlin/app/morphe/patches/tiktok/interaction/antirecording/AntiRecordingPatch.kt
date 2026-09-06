package app.morphe.patches.tiktok.interaction.antirecording

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.findMutableMethodOf
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private data class ScreenCaptureCallSite(
    val classDef: ClassDef,
    val method: Method,
    val instructionIndexes: List<Int>,
)

/**
 * Bytecode only. 46.2.3 does not declare android.permission.DETECT_SCREEN_CAPTURE, so there is
 * nothing in the manifest to take out, and touching the manifest at all makes the patcher decode
 * the whole resource table. That decode is what put patching over the memory Morphe Manager
 * allows by default: measured 2026-09-06, this patch alone needed 768 MB with it and 512 MB
 * without. A version that does declare the permission still gets the calls nopped, which leaves
 * the permission inert.
 */
@Suppress("unused")
val antiRecordingPatch = bytecodePatch(
    name = "Disable screen capture detection",
    description = "Prevents TikTok from reacting to screenshots and screen recordings.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        listOf(
            antiRecordingAddedFingerprint,
            antiRecordingRemovedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.methodOrNull?.returnEarly()
        }

        val callSites = mutableListOf<ScreenCaptureCallSite>()
        classDefForEach { classDef ->
            classDef.methods.forEach { method ->
                val indexes = method.implementation?.instructions
                    ?.mapIndexedNotNull { index, instruction ->
                        val reference = (instruction as? ReferenceInstruction)
                            ?.reference as? MethodReference
                            ?: return@mapIndexedNotNull null
                        if (reference.definingClass != "Landroid/app/Activity;") {
                            return@mapIndexedNotNull null
                        }
                        if (
                            reference.name != "registerScreenCaptureCallback" &&
                            reference.name != "unregisterScreenCaptureCallback"
                        ) {
                            return@mapIndexedNotNull null
                        }
                        index
                    }
                    .orEmpty()
                if (indexes.isNotEmpty()) {
                    callSites += ScreenCaptureCallSite(classDef, method, indexes)
                }
            }
        }

        callSites.forEach { callSite ->
            val mutableMethod = mutableClassDefBy(callSite.classDef)
                .findMutableMethodOf(callSite.method)
            callSite.instructionIndexes.forEach { index ->
                mutableMethod.replaceInstruction(index, "nop")
            }
        }
    }
}
