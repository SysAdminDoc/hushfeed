package app.morphe.patches.tiktok.interaction.sharesheet

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object ShareSnapshotFingerprint : Fingerprint(
    strings = listOf("click_to_respond_duration", "config_duration"),
    custom = { method, _ -> method.name == "<init>" && method.parameterTypes.size == 1 },
)

context(patchContext: BytecodePatchContext)
internal fun hookShareModel() {
    val method = ShareSnapshotFingerprint.method
    val builder = method.parameterTypes.single().toString()
    val fields = patchContext.mutableClassDefBy(builder).fields
    check(fields.any { it.type == "Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;" }) {
        "Share sheet: $builder holds no BaseSharePackage, so it is not the share model builder."
    }
    val callbacks = mapOf("LIZ" to "channels", "LJFF" to "actions", "LJJIIJZLJL" to "contacts")
    val found = mutableSetOf<String>()
    method.implementation!!.instructions.withIndex().toList().asReversed().forEach { (index, instruction) ->
        val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference ?: return@forEach
        val callback = callbacks[field.name] ?: return@forEach
        if (field.definingClass != builder) return@forEach
        val boolean = field.name == "LJJIIJZLJL"
        val opcode = if (boolean) Opcode.IGET_BOOLEAN else Opcode.IGET_OBJECT
        if (instruction.opcode != opcode) return@forEach
        val type = if (boolean) "Z" else "Ljava/util/List;"
        if (field.type != type) throw PatchException("Share builder ${field.name} changed type")
        val register = (instruction as TwoRegisterInstruction).registerA
        method.addInstructions(index + 1, """
            invoke-static/range { v$register .. v$register }, Lapp/morphe/extension/tiktok/share/ShareModelFilter;->$callback($type)$type
            move-result${if (boolean) "" else "-object"} v$register
        """)
        found.add(field.name)
    }
    if (found != callbacks.keys) throw PatchException("Share panel hooks are incomplete: $found")
}
