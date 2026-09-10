package app.morphe.patches.tiktok.interaction.looping

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STRATEGY = "LX/0rap;"
private const val ENTRY = "LX/07Ii;"

class AutoScrollLoadStrategyTest {
    @Test
    fun `the strategy written into the registration is the one the extension answered`() {
        for (answer in listOf("IMMEDIATE", "LAZY")) {
            // iput-object addresses four-bit registers, so this is the whole range the
            // host can write its strategy straight back from.
            for (register in listOf(0, 5, 15)) {
                val method = lazyRegistration(register)
                method.chooseAutoAdvanceLoadStrategy(strategyIndex = 0, strategy = STRATEGY)
                assertEquals(
                    "answer=$answer register=v$register",
                    answer,
                    runRegistration(method, answer),
                )
            }
        }
    }

    @Test
    fun `the host's own constant is what the extension is asked about`() {
        val method = lazyRegistration(3)
        method.chooseAutoAdvanceLoadStrategy(strategyIndex = 0, strategy = STRATEGY)
        val asked = mutableListOf<String>()
        runRegistration(method, "IMMEDIATE", asked)
        assertEquals(listOf("LAZY"), asked)
    }

    @Test
    fun `the answer is cast back to the host's own strategy type before it is stored`() {
        val method = lazyRegistration(9)
        method.chooseAutoAdvanceLoadStrategy(strategyIndex = 0, strategy = STRATEGY)
        val casts = method.implementation!!.instructions
            .filter { it.opcode == Opcode.CHECK_CAST }
            .map { (it as ReferenceInstruction).reference as TypeReference }
        assertEquals(1, casts.size)
        assertEquals(STRATEGY, casts.single().type)
    }

    @Test
    fun `the call reaches the extension through the range form, so a high register still works`() {
        val method = lazyRegistration(255, storeIntoEntry = false)
        method.chooseAutoAdvanceLoadStrategy(strategyIndex = 0, strategy = STRATEGY)
        val call = method.implementation!!.instructions.single { it.opcode == Opcode.INVOKE_STATIC_RANGE }
        val target = (call as ReferenceInstruction).reference as MethodReference
        assertEquals("Lapp/morphe/extension/tiktok/interaction/AutoAdvance;", target.definingClass)
        assertEquals("loadStrategy", target.name)
        assertEquals(listOf("Ljava/lang/Object;"), target.parameterTypes.map { it.toString() })
        assertEquals("Ljava/lang/Object;", target.returnType)
        assertEquals(255, (call as RegisterRangeInstruction).startRegister)
        assertEquals(1, call.registerCount)
    }

    @Test
    fun `nothing ahead of the host's own load is disturbed`() {
        val method = lazyRegistration(2, leadingInstructions = 2)
        val before = method.implementation!!.instructions.take(2).map { it.opcode }
        method.chooseAutoAdvanceLoadStrategy(strategyIndex = 2, strategy = STRATEGY)
        assertEquals(before, method.implementation!!.instructions.take(2).map { it.opcode })
        assertTrue(method.implementation!!.instructions.toList()[3].opcode == Opcode.INVOKE_STATIC_RANGE)
    }

    /**
     * The shape the host registers a component with: load its strategy constant, then write it
     * into the entry the panel keeps. [leadingInstructions] puts unrelated work ahead of it.
     * [storeIntoEntry] drops that write, which is how a register above the four bits iput-object
     * can address is reached: the host would move it down before storing.
     */
    private fun lazyRegistration(
        register: Int,
        leadingInstructions: Int = 0,
        storeIntoEntry: Boolean = true,
    ) = MutableMethod(
        ImmutableMethod(
            "LX/0raq;", "invoke", listOf(ImmutableMethodParameter("Ljava/lang/Object;", null, null)),
            "Ljava/lang/Object;",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            ImmutableMethodImplementation(
                register + 2,
                buildList {
                    repeat(leadingInstructions) {
                        add(ImmutableInstruction10x(Opcode.NOP))
                    }
                    add(
                        ImmutableInstruction21c(
                            Opcode.SGET_OBJECT, register,
                            ImmutableFieldReference(STRATEGY, "LAZY", STRATEGY),
                        ),
                    )
                    if (storeIntoEntry) {
                        add(
                            ImmutableInstruction22c(
                                Opcode.IPUT_OBJECT, register, if (register == 0) 1 else 0,
                                ImmutableFieldReference(ENTRY, "LIZIZ", STRATEGY),
                            ),
                        )
                    }
                    add(ImmutableInstruction10x(Opcode.RETURN_VOID))
                },
                null, null,
            ),
        ),
    )

    /** Runs the emitted registration and reports the constant that reached the panel's entry. */
    private fun runRegistration(
        method: MutableMethod,
        answer: String,
        asked: MutableList<String> = mutableListOf(),
    ): String {
        val body = method.implementation!!
        val registers = arrayOfNulls<String>(body.registerCount)
        var result: String? = null
        for (instruction in body.instructions) {
            when (instruction.opcode) {
                Opcode.NOP -> Unit
                Opcode.SGET_OBJECT -> {
                    val field = (instruction as ReferenceInstruction).reference as FieldReference
                    registers[(instruction as OneRegisterInstruction).registerA] = field.name
                }
                Opcode.INVOKE_STATIC_RANGE -> {
                    val start = (instruction as RegisterRangeInstruction).startRegister
                    asked.add(registers[start] ?: error("The extension was handed nothing"))
                    result = answer
                }
                Opcode.MOVE_RESULT_OBJECT ->
                    registers[(instruction as OneRegisterInstruction).registerA] = result
                Opcode.CHECK_CAST -> Unit
                Opcode.IPUT_OBJECT ->
                    return registers[(instruction as TwoRegisterInstruction).registerA]
                        ?: error("Nothing was written into the entry")
                else -> error("Unexpected instruction: ${instruction.opcode}")
            }
        }
        error("The registration never wrote its strategy")
    }
}
