package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal object StoryLongPressFingerprint : Fingerprint(
    definingClass = "LX/0Qet;",
    name = "LIZ",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = emptyList(),
    returnType = "V",
)

internal fun MutableMethod.interceptStoryLongPress() {
    check(definingClass == "LX/0Qet;" && name == "LIZ" && parameterTypes.isEmpty()
        && returnType == "V" && accessFlags and AccessFlags.STATIC.value == 0
        && implementation!!.registerCount == 4) {
        "Advanced downloads: unexpected native story timer signature or registers."
    }
    val viewRead = getInstruction<TwoRegisterInstruction>(0)
    val viewField = getInstruction<ReferenceInstruction>(0).reference as? FieldReference
    val monitorRead = getInstruction<TwoRegisterInstruction>(1)
    check(viewRead.opcode == Opcode.IGET_OBJECT && viewRead.registerA == 1
        && viewRead.registerB == 3 && viewField != null && viewField.definingClass == definingClass
        && viewField.name == "LL" && viewField.type == "LX/0R9T;"
        && monitorRead.opcode == Opcode.IGET_OBJECT && monitorRead.registerA == 0
        && monitorRead.registerB == 1) {
        "Advanced downloads: native story timer no longer reads its view before its monitor."
    }
    addInstructionsWithLabels(1, """
        invoke-static { v1 }, Lapp/morphe/extension/tiktok/download/StoryDownloads;->onNativeLongPress(Landroid/view/View;)Z
        move-result v0
        if-eqz v0, :native_story_hold
        return-void
    """, ExternalLabel("native_story_hold", getInstruction(1)))
}
