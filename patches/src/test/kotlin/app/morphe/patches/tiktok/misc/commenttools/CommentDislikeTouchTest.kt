package app.morphe.patches.tiktok.misc.commenttools

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CommentDislikeTouchTest {
    @Test
    fun `capture preserves the like listener and both native dislike operands`() {
        for (range in listOf(false, true)) {
            val method = nativeTouchInstalls(range)
            val before = method.implementation!!.instructions.toList()
            method.captureDislikeTouchListener()
            val after = method.implementation!!.instructions.toList()
            assertEquals(before.size, after.size)
            // Only the final native install changes. The preceding like install stays virtual.
            for (index in before.indices.filter { it != 11 }) assertSame(before[index], after[index])
            assertEquals(Opcode.INVOKE_VIRTUAL, after[5].opcode)
            val captured = after[11]
            assertEquals(if (range) Opcode.INVOKE_STATIC_RANGE else Opcode.INVOKE_STATIC, captured.opcode)
            val target = (captured as ReferenceInstruction).reference as MethodReference
            assertEquals("Lapp/morphe/extension/tiktok/comment/CommentTools;", target.definingClass)
            assertEquals("setDislikeTouchListener", target.name)
            assertEquals(
                listOf("Landroid/view/View;", "Landroid/view/View\$OnTouchListener;"),
                target.parameterTypes.map(CharSequence::toString),
            )
            assertEquals("V", target.returnType)
            if (range) {
                val call = captured as RegisterRangeInstruction
                assertEquals(1, call.startRegister)
                assertEquals(2, call.registerCount)
            } else {
                val call = captured as FiveRegisterInstruction
                assertEquals(2, call.registerC)
                assertEquals(1, call.registerD)
                assertEquals(2, call.registerCount)
            }
            assertEquals(before[11].codeUnits, captured.codeUnits)
        }
    }

    private fun nativeTouchInstalls(range: Boolean): MutableMethod {
        val method = MutableMethod(
            ImmutableMethod(
                "LX/0nvj;", "LIZIZ", emptyList(), "V", AccessFlags.PUBLIC.value,
                null, null, ImmutableMethodImplementation(12, emptyList(), null, null),
            ),
        )
        val receiver = if (range) 1 else 2
        val listener = if (range) 2 else 1
        val call = if (range) "invoke-virtual/range {v1 .. v2}" else "invoke-virtual {v2, v1}"
        method.addInstructions(
            """
                iget-object v2, p0, LX/0nvj;->LLJJ:Landroid/widget/RelativeLayout;
                if-eqz v2, :dislike
                new-instance v1, LY/ATListenerS437S0100000_22;
                const/16 v0, 8
                invoke-direct {v1, p0, v0}, LY/ATListenerS437S0100000_22;-><init>(Ljava/lang/Object;I)V
                invoke-virtual {v2, v1}, Landroid/view/View;->setOnTouchListener(Landroid/view/View${'$'}OnTouchListener;)V
                :dislike
                iget-object v$receiver, p0, LX/0nvj;->LLJJIJIIJIL:Landroid/widget/RelativeLayout;
                if-eqz v$receiver, :done
                new-instance v$listener, LY/ATListenerS437S0100000_22;
                const/16 v0, 9
                invoke-direct {v$listener, p0, v0}, LY/ATListenerS437S0100000_22;-><init>(Ljava/lang/Object;I)V
                $call, Landroid/view/View;->setOnTouchListener(Landroid/view/View${'$'}OnTouchListener;)V
                :done
                return-void
            """,
        )
        return method
    }
}
