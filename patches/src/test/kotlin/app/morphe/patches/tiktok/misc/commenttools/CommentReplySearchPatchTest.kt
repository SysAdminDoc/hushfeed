package app.morphe.patches.tiktok.misc.commenttools

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CommentReplySearchPatchTest {
    @Test
    fun `each reply bind return passes its own parent and native state without changing the frame`() {
        val cell = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/CommentMoreItemCell;"
        val model = "LX/0nlo;"
        val method = MutableMethod(
            ImmutableMethod(
                cell, "onBindItemView", listOf(ImmutableMethodParameter("LX/0lOS;", null, null)),
                "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
                ImmutableMethodImplementation(30, emptyList(), null, null),
            ),
        ).apply {
            // The native frame keeps the holder in p0=v28 and its model in p1=v29.
            // Both native branches render through Q5 before returning.
            addInstructions(
                """
                    move-object/from16 v2, p1
                    check-cast v2, $model
                    move-object/from16 v3, p0
                    invoke-super {v3, v2}, Lcom/bytedance/ies/powerlist/PowerCell;->onBindItemView(LX/0lOS;)V
                    const/4 v1, 0
                    if-eqz v1, :other
                    invoke-virtual {v3, v2, v1}, $cell->Q5(${model}Z)V
                    return-void
                    :other
                    invoke-virtual {v3, v2, v1}, $cell->Q5(${model}Z)V
                    return-void
                """,
            )
        }
        val original = method.implementation!!.instructions.toList()

        method.registerReplySearch()

        val after = method.implementation!!.instructions.toList()
        assertEquals(30, method.implementation!!.registerCount)
        assertEquals(original.size + 18, after.size)
        var next = 0
        for (instruction in original) {
            if (instruction.opcode == Opcode.RETURN_VOID) {
                val bridge = after.subList(next, next + 9)
                val holderMove = bridge[0] as TwoRegisterInstruction
                assertEquals(Opcode.MOVE_OBJECT_FROM16, bridge[0].opcode)
                assertEquals(28, holderMove.registerB)
                val viewRegister = holderMove.registerA
                val viewField = (bridge[1] as ReferenceInstruction).reference as FieldReference
                assertEquals("Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;", viewField.definingClass)
                assertEquals("itemView", viewField.name)
                assertEquals("Landroid/view/View;", viewField.type)
                assertEquals(viewRegister, (bridge[1] as TwoRegisterInstruction).registerA)
                assertEquals(viewRegister, (bridge[1] as TwoRegisterInstruction).registerB)

                val modelMove = bridge[2] as TwoRegisterInstruction
                assertEquals(Opcode.MOVE_OBJECT_FROM16, bridge[2].opcode)
                assertEquals(29, modelMove.registerB)
                val modelRegister = modelMove.registerA
                assertEquals(Opcode.CHECK_CAST, bridge[3].opcode)
                assertEquals(model, (bridge[3] as ReferenceInstruction).reference.toString())
                assertEquals(modelRegister, (bridge[3] as OneRegisterInstruction).registerA)
                val stateCall = (bridge[4] as ReferenceInstruction).reference as MethodReference
                assertEquals(model, stateCall.definingClass)
                assertEquals("LIZ", stateCall.name)
                assertEquals(emptyList<String>(), stateCall.parameterTypes)
                assertEquals("I", stateCall.returnType)
                assertEquals(Opcode.INVOKE_VIRTUAL, bridge[4].opcode)
                assertEquals(1, (bridge[4] as FiveRegisterInstruction).registerCount)
                assertEquals(modelRegister, (bridge[4] as FiveRegisterInstruction).registerC)
                assertEquals(Opcode.MOVE_RESULT, bridge[5].opcode)
                val stateRegister = (bridge[5] as OneRegisterInstruction).registerA

                val extra = (bridge[6] as ReferenceInstruction).reference as FieldReference
                assertEquals(model, extra.definingClass)
                assertEquals("LLILZIL", extra.name)
                assertEquals("LX/0nls;", extra.type)
                val parent = (bridge[7] as ReferenceInstruction).reference as FieldReference
                assertEquals("LX/0nls;", parent.definingClass)
                assertEquals("LJI", parent.name)
                assertEquals("Lcom/ss/android/ugc/aweme/comment/model/Comment;", parent.type)
                for (index in listOf(6, 7)) {
                    assertEquals(Opcode.IGET_OBJECT, bridge[index].opcode)
                    assertEquals(modelRegister, (bridge[index] as TwoRegisterInstruction).registerA)
                    assertEquals(modelRegister, (bridge[index] as TwoRegisterInstruction).registerB)
                }
                assertEquals(Opcode.INVOKE_STATIC, bridge[8].opcode)
                val call = bridge[8] as FiveRegisterInstruction
                assertEquals(3, call.registerCount)
                assertEquals(viewRegister, call.registerC)
                assertEquals(modelRegister, call.registerD)
                assertEquals(stateRegister, call.registerE)
                assertEquals(3, setOf(viewRegister, modelRegister, stateRegister).size)
                val target = (bridge[8] as ReferenceInstruction).reference as MethodReference
                assertEquals("Lapp/morphe/extension/tiktok/comment/CommentSearch;", target.definingClass)
                assertEquals("onReplyControlBound", target.name)
                assertEquals(
                    listOf("Landroid/view/View;", "Ljava/lang/Object;", "I"),
                    target.parameterTypes.map(CharSequence::toString),
                )
                assertEquals("V", target.returnType)
                next += 9
            }
            assertSame(instruction, after[next++])
        }
    }
}
