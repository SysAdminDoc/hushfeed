package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.Fixtures
import app.morphe.takes
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Keep a paused video paused answers, held to every fixture.
 *
 * <p>TikTok plays the video on screen again as the app comes back through PlayerController's play
 * method, from the feed panel's resume and again from the video's new surface (both seen on the
 * S22 with 47.0.3). The patch asks at the start of that method, and a play it turns down answers
 * the way the method's own first check does while casting: an empty string, straight back.
 */
class KeepPausedAnchorsTest {
    @Test
    fun `the play method is one method on every fixture and turns a casting play down with an empty string`() {
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            val taken = build.methods.filter { (classDef, method) -> PlayerPlayFingerprint.takes(method, classDef) }.toList()
            assertEquals("${apk.name}: ${taken.map { "${it.first.type}->${it.second.name}" }}", 1, taken.size)
            val play = taken.single().second
            val registers = play.implementation!!.registerCount
            assertEquals("${apk.name}: the Aweme parameter", PLAYED_AWEME,
                play.parameterTypes[play.awemeParameterRegister() - 1].toString())
            assertTrue("${apk.name}: no local register for the answer", registers - play.parameterTypes.size - 1 >= 1)

            val head = play.implementation!!.instructions.take(12)
            val casting = head.indexOfFirst { it.call()?.name == "blockByCasting" }
            assertTrue("${apk.name}: the play no longer checks casting first: ${head.map { it.opcode }}", casting >= 0)
            val empty = head.filter { it.opcode == Opcode.CONST_STRING && it.string() == "" }
                .map { (it as OneRegisterInstruction).registerA }.toSet()
            val answered = head.drop(casting).any { it.opcode == Opcode.RETURN_OBJECT && (it as OneRegisterInstruction).registerA in empty }
            assertTrue("${apk.name}: a casting play is no longer answered with an empty string", answered)

            val callers = build.methods.filter { (_, method) -> method.calls(play) }.map { it.second.name }.toSet()
            assertTrue("${apk.name}: the play method has callers $callers", callers.size >= 2)
        }
    }

    @Test
    fun `the patch asks before the play and answers a refused one as casting does`() {
        val root = File("src/main/kotlin").takeIf { it.isDirectory } ?: File("patches/src/main/kotlin")
        val source = File(root, "app/morphe/patches/tiktok/interaction/blockauthor/BlockAuthorPatch.kt").readText()
        assertTrue("the play is not hooked", source.contains("PlayerPlayFingerprint.method.apply"))
        assertTrue("the hook does not hand over the video",
            source.contains("invoke-static/range { \$aweme .. \$aweme }, Lapp/morphe/extension/tiktok/playback/KeepPaused;->refusePlay(Ljava/lang/Object;)Z"))
        assertTrue("a refused play would still play or answer something else",
            source.contains("if-eqz v0, :play\n                    const-string v0, \"\"\n                    return-object v0"))
    }

    private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.call(): MethodReference? =
        (this as? ReferenceInstruction)?.reference as? MethodReference

    private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.string(): String? =
        ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

    private fun Method.calls(target: Method): Boolean = implementation?.instructions?.any { instruction ->
        instruction.call()?.let {
            it.definingClass == target.definingClass && it.name == target.name &&
                it.parameterTypes.map(Any::toString) == target.parameterTypes.map(Any::toString)
        } == true
    } == true

    /** One fixture's classes by type; methods are walked on each ask, never held (the dex has millions). */
    private class Build(apk: File) {
        val byType = HashMap<String, ClassDef>()

        init {
            val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
            for (entry in container.dexEntryNames) {
                for (classDef in container.getEntry(entry)!!.dexFile.classes) byType.putIfAbsent(classDef.type, classDef)
            }
        }

        val methods: Sequence<Pair<ClassDef, Method>>
            get() = byType.values.asSequence().flatMap { classDef -> classDef.methods.asSequence().map { classDef to it } }
    }
}
