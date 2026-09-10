package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/wellbeing/SessionPlaybackHold;"
private const val AUDIO_MANAGER = "Landroid/media/AudioManager;"
private const val AUDIO_LISTENER = "Landroid/media/AudioManager\$OnAudioFocusChangeListener;"
private const val CONTEXT = "Landroid/content/Context;"

/**
 * The host's player audio-focus helper, found by what it is rather than what it is called.
 *
 * <p>On 46.2.3 this was `LX/0q3r;` with its listener `LX/0q3s;`, and both names were written into
 * the patch, along with the name of the wrapper the request goes through and the exact registers
 * the host happened to use. All of that is renumbered by the next build. What does not change is
 * the shape: one class holding exactly one `AudioManager` and one listener, built from a Context,
 * with one `(Context)V` method that abandons focus and one that requests it. That shape names
 * exactly one class on 46.2.3, 46.7.3 and 46.8.3, and the same one the literals named.
 */
internal class NativeFocus(
    val helper: String,
    val listener: String,
    val request: MutableMethod,
    val abandon: MutableMethod,
    val change: MutableMethod,
)

internal fun BytecodePatchContext.resolveNativeFocus(): NativeFocus {
    val found = mutableListOf<Triple<ClassDef, Method, Method>>()
    val listenerOf = mutableMapOf<String, String>()
    classDefForEach { classDef ->
        val fields = classDef.fields.toList()
        if (fields.count { it.type == AUDIO_MANAGER } != 1) return@classDefForEach
        val listener = fields.firstOrNull { field ->
            field.type != AUDIO_MANAGER &&
                classDefByOrNull(field.type)?.interfaces?.contains(AUDIO_LISTENER) == true
        }?.type ?: return@classDefForEach
        if (classDef.methods.none { it.name == "<init>" && it.parameterTypes.toList() == listOf(CONTEXT) }) {
            return@classDefForEach
        }
        val contextMethods = classDef.methods.filter {
            it.name != "<init>" && it.returnType == "V" &&
                it.parameterTypes.toList() == listOf(CONTEXT) &&
                AccessFlags.STATIC.value and it.accessFlags == 0
        }
        val abandon = contextMethods.singleOrNull { method -> method.calls { it.name == "abandonAudioFocus" } }
        val request = contextMethods.singleOrNull { method -> method.calls(::requestsFocus) }
        if (abandon == null || request == null || abandon == request) return@classDefForEach
        listenerOf[classDef.type] = listener
        found += Triple(classDef, request, abandon)
    }
    if (found.size != 1) {
        throw PatchException(
            "Block author button: expected one audio focus helper holding an AudioManager and a " +
                "listener, with a (Context)V that abandons focus and one that requests it; found " +
                "${found.size}.",
        )
    }
    val (classDef, request, abandon) = found.single()
    val listenerType = listenerOf.getValue(classDef.type)
    val helper = mutableClassDefBy(classDef)
    val listenerClass = mutableClassDefBy(listenerType)
    val change = listenerClass.methods.singleOrNull {
        it.name == "onAudioFocusChange" && it.returnType == "V" &&
            it.parameterTypes.toList() == listOf("I") &&
            AccessFlags.STATIC.value and it.accessFlags == 0
    } ?: throw PatchException("Block author button: $listenerType has no onAudioFocusChange(I)V.")
    return NativeFocus(
        helper = classDef.type,
        listener = listenerType,
        request = helper.findMutableMethodOf(request),
        abandon = helper.findMutableMethodOf(abandon),
        change = change,
    )
}

/** Whether any instruction of the method invokes something the predicate accepts. */
private fun Method.calls(predicate: (MethodReference) -> Boolean): Boolean =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<MethodReference>()?.let(predicate) == true
    } == true

/**
 * TikTok does not call `requestAudioFocus` itself. It goes through a static wrapper taking the
 * manager, the listener and two ints, which is the platform call's own argument list with the
 * manager in front, so that is what identifies it. A build that called the platform directly
 * would be taken too.
 */
private fun requestsFocus(reference: MethodReference): Boolean =
    reference.name == "requestAudioFocus" ||
        (reference.returnType == "I" &&
            reference.parameterTypes.toList() == listOf(AUDIO_MANAGER, AUDIO_LISTENER, "I", "I"))

internal fun MutableMethod.captureNativeFocusRequest() {
    val instructions = implementation!!.instructions
    val index = instructions.withIndex().single {
        it.value.getReference<MethodReference>()?.let(::requestsFocus) == true
    }.index
    val call = instructions[index] as FiveRegisterInstruction
    val reference = instructions[index].getReference<MethodReference>()!!
    check(instructions[index].opcode == Opcode.INVOKE_STATIC && call.registerCount == 4) {
        "Block author button: the focus request is not a four-argument static call."
    }
    // The original return also owns the null branches and Exception handler. Insert at the
    // invoke, then remove its old copy, so those paths never enter an orphaned move-result.
    // The host discards the request's answer, which is what makes the last argument's register
    // free to hold it: nothing reads that register again before the return.
    check(instructions[index + 1].opcode == Opcode.RETURN_VOID) {
        "Block author button: the focus request is no longer the last thing its method does."
    }
    val manager = call.registerC
    val listener = call.registerD
    val result = call.registerF
    addInstructions(
        index,
        """
            invoke-static {v$manager, v$listener, v${call.registerE}, v${call.registerF}}, $reference
            move-result v$result
            invoke-static {v$listener, v$result}, $EXTENSION->onNativeFocusRequestResult(Ljava/lang/Object;I)V
        """,
    )
    implementation!!.removeInstruction(index + 3)
}

internal fun MutableMethod.captureNativeFocusChange() {
    addInstruction(
        0,
        "invoke-static/range {p0 .. p1}, $EXTENSION->onNativeFocusChange(Ljava/lang/Object;I)V",
    )
}

internal fun MutableMethod.captureNativeFocusAbandon() {
    val instructions = implementation!!.instructions
    val index = instructions.withIndex().single {
        it.value.getReference<MethodReference>()?.name == "abandonAudioFocus"
    }.index
    val call = instructions[index] as FiveRegisterInstruction
    check(instructions[index].opcode == Opcode.INVOKE_VIRTUAL && call.registerCount == 2) {
        "Block author button: abandonAudioFocus is not called on a manager with one listener."
    }
    // Invalidate before calling Android, including when the native Exception handler runs.
    // registerD is the listener the host hands to the platform.
    addInstruction(index, "invoke-static {v${call.registerD}}, $EXTENSION->onNativeFocusAbandon(Ljava/lang/Object;)V")
}
