package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION = "Lapp/morphe/extension/tiktok/wellbeing/SessionPlaybackHold;"
private const val AUDIO_LISTENER = "Landroid/media/AudioManager\$OnAudioFocusChangeListener;"
private const val REQUEST = "LX/0UUp;->j4(Landroid/media/AudioManager;${AUDIO_LISTENER}II)I"
private const val ABANDON = "Landroid/media/AudioManager;->abandonAudioFocus($AUDIO_LISTENER)I"

internal fun MutableMethod.captureNativeFocusRequest() {
    checkNativeOwner("LIZIZ", 6)
    val instructions = implementation!!.instructions
    val index = instructions.withIndex().single {
        (it.value as? ReferenceInstruction)?.reference.toString() == REQUEST
    }.index
    val call = instructions[index] as FiveRegisterInstruction
    check(instructions[index].opcode == Opcode.INVOKE_STATIC && call.registerCount == 4
        && listOf(call.registerC, call.registerD, call.registerE, call.registerF) == listOf(3, 2, 1, 0)
        && instructions[index + 1].opcode == Opcode.RETURN_VOID)

    // The original return also owns the null branches and Exception handler. Insert at the
    // invoke, then remove its old copy, so those paths never enter an orphaned move-result.
    addInstructions(index, """
        invoke-static {v3, v2, v1, v0}, $REQUEST
        move-result v0
        invoke-static {v2, v0}, $EXTENSION->onNativeFocusRequestResult(Ljava/lang/Object;I)V
    """)
    implementation!!.removeInstruction(index + 3)
}

internal fun MutableMethod.captureNativeFocusChange() {
    check(definingClass == "LX/0q3s;" && name == "onAudioFocusChange"
        && parameterTypes.map(CharSequence::toString) == listOf("I") && returnType == "V"
        && accessFlags and AccessFlags.STATIC.value == 0 && implementation!!.registerCount == 2)
    addInstruction(0, "invoke-static/range {p0 .. p1}, " +
        "$EXTENSION->onNativeFocusChange(Ljava/lang/Object;I)V")
}

internal fun MutableMethod.captureNativeFocusAbandon() {
    checkNativeOwner("LIZ", 4)
    val instructions = implementation!!.instructions
    val index = instructions.withIndex().single {
        (it.value as? ReferenceInstruction)?.reference.toString() == ABANDON
    }.index
    val call = instructions[index] as FiveRegisterInstruction
    check(instructions[index].opcode == Opcode.INVOKE_VIRTUAL && call.registerCount == 2
        && call.registerC == 1 && call.registerD == 0)
    // Invalidate before calling Android, including when the native Exception handler runs.
    addInstruction(index, "invoke-static {v0}, $EXTENSION->onNativeFocusAbandon(Ljava/lang/Object;)V")
}

private fun MutableMethod.checkNativeOwner(expectedName: String, registers: Int) {
    check(definingClass == "LX/0q3r;" && name == expectedName
        && parameterTypes.map(CharSequence::toString) == listOf("Landroid/content/Context;")
        && returnType == "V" && accessFlags and AccessFlags.STATIC.value == 0
        && implementation!!.registerCount == registers)
}
