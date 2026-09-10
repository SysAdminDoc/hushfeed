package app.morphe.patches.tiktok.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walk through R8's merged-lambda dispatch.
 *
 * <p>A group holds many lambdas as one class and tells them apart by a number in a field. Its
 * entry point switches on that number and calls the body belonging to it, so following the switch
 * is what says which body a given lambda is. The alternative is trusting that R8 keeps naming the
 * body after the number, which is what this exists not to do.
 */
class LazyAbGateTest {
    private val group = "Lkotlin/jvm/internal/AFwS194S0000000_4;"

    @Test
    fun `the switch says which body belongs to each number`() {
        val entry = dispatcher(
            "invoke",
            firstKey = 1472,
            targets = listOf("invoke\$1472", "invoke\$1473", "invoke\$1474"),
        )
        val classDef = group(entry, body("invoke\$1472"), body("invoke\$1473"), body("invoke\$1474"))

        assertEquals("invoke\$1472", entry.dispatchTarget(classDef, 1472)?.name)
        assertEquals("invoke\$1473", entry.dispatchTarget(classDef, 1473)?.name)
        assertEquals("invoke\$1474", entry.dispatchTarget(classDef, 1474)?.name)
    }

    @Test
    fun `a number the switch does not carry has no body`() {
        val entry = dispatcher("invoke", firstKey = 1472, targets = listOf("invoke\$1472", "invoke\$1473"))
        val classDef = group(entry, body("invoke\$1472"), body("invoke\$1473"))

        assertNull(entry.dispatchTarget(classDef, 1471))
        assertNull(entry.dispatchTarget(classDef, 1474))
    }

    @Test
    fun `a body named after some other number is not what the switch reaches`() {
        // R8 names the body of number n `invoke$n` on every build seen. Reading the switch has to
        // disagree with that name when the two are made to disagree, or it is not reading it.
        val entry = dispatcher("invoke", firstKey = 7, targets = listOf("invoke\$999", "invoke\$998"))
        val classDef = group(entry, body("invoke\$999"), body("invoke\$998"))

        assertEquals("invoke\$999", entry.dispatchTarget(classDef, 7)?.name)
        assertEquals("invoke\$998", entry.dispatchTarget(classDef, 8)?.name)
        assertNull(entry.dispatchTarget(classDef, 999))
    }

    @Test
    fun `only a body that opens on the number is a dispatcher`() {
        val entry = dispatcher("invoke", firstKey = 1, targets = listOf("invoke\$1"))
        assertTrue(entry.dispatchesOnIndex())
        assertFalse(body("invoke\$1").dispatchesOnIndex())
    }

    @Test
    fun `a getter is only the lazy read when it unwraps the number it holds`() {
        assertTrue(lazyRead(unwraps = true).isLazyAbRead())
        assertFalse(lazyRead(unwraps = false).isLazyAbRead())
    }

    /** `iget $t; packed-switch` onto one call per key, which is what the group's entry point is. */
    private fun dispatcher(name: String, firstKey: Int, targets: List<String>): MutableMethod {
        val cases = targets.mapIndexed { index, target ->
            """
                :case_$index
                invoke-static { v1 }, $group->$target($group)Ljava/lang/Object;
                move-result-object v0
                return-object v0
            """.trimIndent()
        }.joinToString("\n")
        val labels = targets.indices.joinToString("\n") { "        :case_$it" }
        return method(name, emptyList(), "Ljava/lang/Object;", 2).apply {
            addInstructionsWithLabels(
                0,
                """
                    iget v0, v1, $group->${'$'}t:I
                    packed-switch v0, :switch_data
                    const/4 v0, 0x0
                    return-object v0
                    $cases
                    :switch_data
                    .packed-switch $firstKey
                    $labels
                    .end packed-switch
                """,
            )
        }
    }

    private fun body(name: String) = method(name, listOf(group), "Ljava/lang/Object;", 2).apply {
        addInstructionsWithLabels(
            0,
            """
                const-string v0, "a_settings_key"
                return-object v0
            """,
        )
    }

    private fun lazyRead(unwraps: Boolean) = method("LIZ", emptyList(), "Z", 2).apply {
        val unwrap = if (unwraps) {
            "invoke-virtual { v0 }, Ljava/lang/Number;->intValue()I\nmove-result v0"
        } else {
            "const/4 v0, 0x1"
        }
        addInstructionsWithLabels(
            0,
            """
                sget-object v0, $group->LIZ:LX/01xP;
                invoke-interface { v0 }, LX/01xP;->getValue()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Ljava/lang/Number;
                $unwrap
                return v0
            """,
        )
    }

    private fun method(name: String, parameters: List<String>, returnType: String, registers: Int) =
        MutableMethod(
            ImmutableMethod(
                group,
                name,
                parameters.map { ImmutableMethodParameter(it, null, null) },
                returnType,
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                ImmutableMethodImplementation(registers, emptyList(), null, null),
            ),
        )

    private fun group(vararg methods: Method): ClassDef = ImmutableClassDef(
        group,
        AccessFlags.PUBLIC.value,
        "Ljava/lang/Object;",
        null,
        null,
        null,
        null,
        methods.toList(),
    )
}
