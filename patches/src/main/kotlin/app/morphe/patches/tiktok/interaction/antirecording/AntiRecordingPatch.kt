package app.morphe.patches.tiktok.interaction.antirecording

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
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
 * without.
 *
 * The sweep below matches call sites whose receiver is typed as Activity itself. A build that
 * called the API through a subclass reference, or that declared the permission, would need the
 * manifest cleanup back, so check both before widening compatibility past 46.2.3.
 */
@Suppress("unused")
val antiRecordingPatch = bytecodePatch(
    name = "Disable screen capture detection",
    description = "Prevents TikTok from reacting to screenshots and screen recordings.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        var returnedEarly = 0
        listOf(
            antiRecordingAddedFingerprint,
            antiRecordingRemovedFingerprint,
        ).forEach { fingerprint ->
            fingerprint.methodOrNull?.let {
                it.returnEarly()
                returnedEarly++
            }
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

        // Both anchors are optional and the sweep is allowed to find nothing, so with neither
        // resolving the patch reported Applied, added its setting, and left screen capture
        // detection exactly as it was. That is the case the header warns about: a build calling
        // the API through a subclass reference, or declaring the permission.
        if (returnedEarly == 0 && callSites.isEmpty()) {
            throw PatchException(
                "Screen capture detection was not found. Neither " +
                    "antiRecordingAddedFingerprint nor antiRecordingRemovedFingerprint resolved, " +
                    "and no call to Activity.registerScreenCaptureCallback or " +
                    "unregisterScreenCaptureCallback was reached. This build likely calls it " +
                    "through a subclass reference or declares DETECT_SCREEN_CAPTURE, either of " +
                    "which needs the manifest cleanup described above.",
            )
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
