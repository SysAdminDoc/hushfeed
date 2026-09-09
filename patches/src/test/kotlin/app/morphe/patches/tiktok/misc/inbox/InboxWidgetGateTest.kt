package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxWidgetGateTest {
    @Test
    fun `an off hide switch preserves the native gate and its work`() {
        for (callback in listOf("shouldShowSuggestedAccounts", "shouldShowInboxStories")) {
            for (nativeResult in listOf(false, true)) {
                for (resultRegister in listOf(0, 17)) {
                    val method = nativeGate(resultRegister)
                    method.hideInboxWidget(callback)
                    assertEquals(
                        "$callback native=$nativeResult register=$resultRegister",
                        nativeResult to 1,
                        runGate(method, callback, shouldShow = true, nativeResult),
                    )
                }
            }
        }
    }

    @Test
    fun `hiding a widget skips its native gate and returns false`() {
        for (callback in listOf("shouldShowSuggestedAccounts", "shouldShowInboxStories")) {
            for (nativeResult in listOf(false, true)) {
                val method = nativeGate(0)
                method.hideInboxWidget(callback)
                assertEquals(false to 0, runGate(method, callback, shouldShow = false, nativeResult))
            }
        }
    }

    private fun nativeGate(resultRegister: Int) = MutableMethod(
        ImmutableMethod(
            "Lcom/example/Widget;", "enable", emptyList(), "Z", AccessFlags.PUBLIC.value,
            null, null,
            ImmutableMethodImplementation(
                resultRegister + 2,
                listOf(
                    ImmutableInstruction35c(
                        Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0,
                        ImmutableMethodReference("Lcom/example/Availability;", "enabled", emptyList(), "Z"),
                    ),
                    ImmutableInstruction11x(Opcode.MOVE_RESULT, resultRegister),
                    ImmutableInstruction11x(Opcode.RETURN, resultRegister),
                ),
                null, null,
            ),
        ),
    )

    /** Executes the emitted branches so a return inserted ahead of the native body is observable. */
    private fun runGate(
        method: MutableMethod,
        callback: String,
        shouldShow: Boolean,
        nativeResult: Boolean,
    ): Pair<Boolean, Int> {
        val body = method.implementation!!
        var address = 0
        val instructions = body.instructions.associate { instruction ->
            val here = address
            address += instruction.codeUnits
            here to instruction
        }
        val registers = BooleanArray(body.registerCount)
        var pc = 0
        var result = false
        var nativeCalls = 0
        repeat(instructions.size * 2) {
            val instruction = instructions.getValue(pc)
            when (instruction.opcode) {
                Opcode.INVOKE_STATIC -> {
                    val target = (instruction as ReferenceInstruction).reference as MethodReference
                    result = when (target.definingClass) {
                        "Lapp/morphe/extension/tiktok/inbox/InboxControls;" -> {
                            assertEquals(callback, target.name)
                            shouldShow
                        }
                        "Lcom/example/Availability;" -> { nativeCalls++; nativeResult }
                        else -> error("Unexpected call: $target")
                    }
                }
                Opcode.MOVE_RESULT -> registers[(instruction as OneRegisterInstruction).registerA] = result
                Opcode.IF_NEZ -> if (registers[(instruction as OneRegisterInstruction).registerA]) {
                    pc += (instruction as OffsetInstruction).codeOffset
                    return@repeat
                }
                Opcode.RETURN -> return registers[(instruction as OneRegisterInstruction).registerA] to nativeCalls
                else -> error("Unexpected instruction: ${instruction.opcode}")
            }
            pc += instruction.codeUnits
        }
        error("Widget gate did not return")
    }
}
