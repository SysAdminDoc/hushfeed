package app.morphe.util

import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The helpers under app.morphe.util, which nothing in this repository tested.
 *
 * <p>Each of these paths is unreachable from the patches here today: the first patch to lean on
 * one would have inherited the defect with nothing to say so. The methods are built by hand
 * rather than read out of an APK, so a case says what shape it needs and no more.
 */
class BytecodeUtilsTest {
    private fun method(
        returnType: String,
        vararg instructions: Instruction = arrayOf(ImmutableInstruction10x(Opcode.RETURN_VOID)),
    ): MutableMethod = MutableMethod(
        ImmutableMethod(
            "Lcom/example/Host;",
            "value",
            emptyList(),
            returnType,
            AccessFlags.PUBLIC.value,
            null,
            null,
            ImmutableMethodImplementation(
                1,
                instructions.toList(),
                null,
                null,
            ),
        ),
    )

    /** A body that hands an object back, which is what returnLate needs to find. */
    private fun returnsAnObject(returnType: String): MutableMethod = method(
        returnType,
        ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
        ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0),
    )

    private fun field(vararg flags: AccessFlags): MutableField = MutableField(
        ImmutableField(
            "Lcom/example/Host;",
            "value",
            "I",
            flags.fold(0) { all, flag -> all or flag.value },
            null,
            null,
            null,
        ),
    )

    @Test
    fun `a double returning method takes a double`() {
        // The check read 'J', so it rejected every method that returns a double and accepted
        // every method that returns a long, which is the opposite of what it is for. Its twin
        // returnLate(Double) two hundred lines down reads 'D'.
        val double = method("D")
        double.returnEarly(1.5)
        assertEquals(Opcode.CONST_WIDE, double.implementation!!.instructions.first().opcode)

        val long = method("J")
        val refused = assertThrows(IllegalStateException::class.java) { long.returnEarly(1.5) }
        assertTrue(refused.message!!.contains("return type"))
    }

    @Test
    fun `a null return is allowed for an array and refused for a primitive`() {
        // The check was `returnType == 'L' || returnType != '['`, which is true for I, V and Z
        // and false for the one case the second half exists to allow. Nothing calls this today;
        // the first caller would have been refused on an array and let through on an int, and
        // an int method would have been handed const/4 v0, 0x0 followed by return-object.
        val array = method("[Ljava/lang/String;")
        array.returnEarly(null)
        assertEquals(Opcode.CONST_4, array.implementation!!.instructions.first().opcode)

        val obj = method("Ljava/util/List;")
        obj.returnEarly(null)
        assertEquals(Opcode.CONST_4, obj.implementation!!.instructions.first().opcode)

        // String and CharSequence took a const-string path that is chosen before the first
        // character is looked at, so a null return on either wrote the text "0x0" and the caller
        // got a three character string where it asked for null. returnLate(Void?) did the same.
        for (text in listOf("Ljava/lang/String;", "Ljava/lang/CharSequence;")) {
            val early = method(text)
            early.returnEarly(null)
            assertEquals(
                "returnEarly(null) on a method returning $text",
                Opcode.CONST_4,
                early.implementation!!.instructions.first().opcode,
            )

            val late = returnsAnObject(text)
            late.returnLate(null)
            assertTrue(
                "returnLate(null) on a method returning $text wrote a string",
                late.implementation!!.instructions.none { it.opcode == Opcode.CONST_STRING },
            )
            assertEquals(
                "returnLate(null) on a method returning $text added no null",
                2,
                late.implementation!!.instructions.count { it.opcode == Opcode.CONST_4 },
            )
        }

        for (primitive in listOf("I", "V", "Z", "J")) {
            assertThrows(
                "a null return was allowed on a method returning $primitive",
                IllegalStateException::class.java,
            ) { method(primitive).returnEarly(null) }
        }
    }

    @Test
    fun `removing two flags removes both of them`() {
        // The mask was built with `and`, so two flags with no bits in common produced zero and
        // the method removed nothing at all. One flag worked, which is why it reads as fine.
        val two = field(AccessFlags.PRIVATE, AccessFlags.FINAL, AccessFlags.STATIC)
        two.removeFlags(AccessFlags.PRIVATE, AccessFlags.FINAL)
        assertEquals(AccessFlags.STATIC.value, two.accessFlags)

        val one = field(AccessFlags.PRIVATE, AccessFlags.STATIC)
        one.removeFlags(AccessFlags.PRIVATE)
        assertEquals(AccessFlags.STATIC.value, one.accessFlags)
    }
}
