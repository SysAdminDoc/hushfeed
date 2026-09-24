package app.morphe.patches.tiktok.misc.theme

import app.morphe.Fixtures
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where TikTok 47.0.3's two sheets take their dark fill, read off the fixture, so the name the
 * AMOLED patch rewrites for them is held to the build it is declared for.
 *
 * <p>The comment panel's shapes and the Tux sheet behind the share sheet fill with one theme
 * attribute: the comment page sets drawable af4 or af9 on its container, whose solid is
 * ?attr/a24, and the Tux sheet reads TuxSheet's tux_sheetBackgroundColor, which its style points
 * at ?attr/a24 as well. The dark themes send a24 to the palette token aia, a dark literal
 * (#1E1E1E on the S22, 2026-09-23), and the light ones to axi, white. The names are the build's
 * own: 46.x reached the same sheets through agk and c3, which 47.0.3 keeps as unrelated tokens.
 * a24 is what TikTok's own token table calls UISheetFlat1, the fill of its sheets, panels, modals
 * and text cells as well, so all of those go black with it.
 */
class AmoledSheetTokensTest {
    @Test
    fun `47_0_3 fills both sheets through a24, which the dark themes send to aia`() {
        val apk = Fixtures.apks().single { it.name.contains("47.0.3") }
        val table = FixtureResourceTable(apk)

        val sheetAttr = tuxSheetBackgroundAttr(apk)
        assertEquals("the Tux sheet background attribute", "b79", table.attrNames[sheetAttr])
        val sheetValues = table.styleValues("b79").toSet()
        assertTrue("b79 reaches a24 in a Tux sheet style: $sheetValues", "?attr/a24" in sheetValues)

        listOf("af4", "af9").forEach { drawable ->
            assertTrue("drawable/$drawable fills with ?attr/a24", table.drawableReferencesAttr(drawable, "a24"))
        }

        val a24 = table.styleValues("a24")
        assertEquals("what the themes point a24 at: $a24", setOf("?attr/aia", "?attr/axi"), a24.toSet())
        val dark = table.styleValues("aia")
        val light = table.styleValues("axi")
        assertTrue("aia is a dark opaque literal wherever it is set: $dark", dark.isNotEmpty() && dark.all(::isDarkOpaqueLiteral))
        assertTrue("axi is light wherever it is set: $light", light.isNotEmpty() && light.none(::isDarkOpaqueLiteral))
        assertTrue("the patch rewrites aia on 47.0.3", "aia" in sheetStyleItems("47.0.3", declaredVersions()))
    }

    /** R$styleable.TuxSheet[TuxSheet__tux_sheetBackgroundColor]: the int field and the array <clinit> fills. */
    private fun tuxSheetBackgroundAttr(apk: File): Int {
        val styleable = "Lcom/ss/android/ugc/aweme/app/R\$styleable;"
        val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
        val classDef = container.dexEntryNames.asSequence().mapNotNull { entry ->
            container.getEntry(entry)!!.dexFile.classes.firstOrNull { it.type == styleable }
        }.first()
        val index = (classDef.staticFields.single { it.name == "TuxSheet__tux_sheetBackgroundColor" }
            .initialValue as IntEncodedValue).value
        val instructions = classDef.methods.single { it.name == "<clinit>" }.implementation!!.instructions.toList()
        val address = IntArray(instructions.size + 1)
        for (i in instructions.indices) address[i + 1] = address[i] + instructions[i].codeUnits
        val filled = HashMap<Int, List<Number>>()
        for (i in instructions.indices) {
            val instruction = instructions[i]
            if (instruction.opcode == Opcode.FILL_ARRAY_DATA) {
                val payload = instructions[address.indexOf(address[i] + (instruction as OffsetInstruction).codeOffset)] as ArrayPayload
                filled[(instruction as OneRegisterInstruction).registerA] = payload.arrayElements
            } else if (instruction.opcode == Opcode.SPUT_OBJECT) {
                val field = (instruction as ReferenceInstruction).reference as FieldReference
                if (field.definingClass == styleable && field.name == "TuxSheet") {
                    return filled.getValue((instruction as OneRegisterInstruction).registerA)[index].toInt()
                }
            }
        }
        error("no TuxSheet array in $styleable")
    }
}
