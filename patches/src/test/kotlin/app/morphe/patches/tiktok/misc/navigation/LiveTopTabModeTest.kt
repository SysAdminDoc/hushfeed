package app.morphe.patches.tiktok.misc.navigation

import app.morphe.Fixtures
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.takes
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The top LIVE mode read must pass through the filter before either corner-button comparison. */
class LiveTopTabModeTest {
    @Test
    fun `the button mode read has one anchor on every fixture and is answered before both comparisons`() {
        for (apk in Fixtures.apks()) {
            val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
            val button = container.dexEntryNames.asSequence().flatMap { entry ->
                container.getEntry(entry)!!.dexFile.classes.asSequence()
            }.firstOrNull { it.type == LIVE_ICON_GENERATOR }
                ?: error("${apk.name}: no LiveIconGenerator")
            val modes = button.methods.filter { LiveTopTabModeFingerprint.takes(it, button) }
            assertEquals("${apk.name}: ${modes.map { it.name }}", 1, modes.size)

            val method = MutableMethod(modes.single())
            method.answerLiveTopTabMode()
            val instructions = method.implementation!!.instructions.toList()
            val single = instructions.indexOfFirst {
                it.opcode == Opcode.CONST_STRING && it.getReference<StringReference>()?.string == "live_tab_single"
            }
            val double = instructions.indexOfFirst {
                it.opcode == Opcode.CONST_STRING && it.getReference<StringReference>()?.string == "live_tab_double"
            }
            assertTrue("${apk.name}: both LIVE modes must be compared after the hook", double > single)
            val call = instructions[single - 2]
            assertEquals(Opcode.INVOKE_STATIC_RANGE, call.opcode)
            assertEquals("liveTopTabMode", call.getReference<MethodReference>()!!.name)
            assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[single - 1].opcode)
            val modeRegister = (instructions[single - 1] as OneRegisterInstruction).registerA
            assertEquals(modeRegister, (instructions[single - 3] as OneRegisterInstruction).registerA)
        }
    }
}
