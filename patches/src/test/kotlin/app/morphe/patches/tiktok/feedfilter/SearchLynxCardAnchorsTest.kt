package app.morphe.patches.tiktok.feedfilter

import app.morphe.Fixtures
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Hide mini dramas finds TikTok's Short Drama block in search, held to TikTok 47.0.3.
 *
 * The block is a Lynx card TikTok builds from a streamed chunk's patches and slots into the Top
 * results adapter, so it never passes the result list the other search filters read. The hook
 * sits at the start of the results list's own Lynx holder bind, passing (holder, fragment,
 * patch): that holds only while one method has the holder bind's shape, it is an instance method
 * of a RecyclerView view holder (so p0 is the holder with its row), its second parameter is the
 * patch, and the results adapter calls it. The other lists' Lynx cell keeps its real name and
 * one bind.
 */
class SearchLynxCardAnchorsTest {
    private val viewHolder = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;"

    @Test
    fun `47_0_3 binds each search Lynx card through one holder method the hook can read`() {
        val apk = Fixtures.apks().single { it.name.contains("47.0.3") }
        val container = DexFileFactory.loadDexContainer(apk, Opcodes.getDefault())
        val classes = container.dexEntryNames.asSequence()
            .flatMap { container.getEntry(it)!!.dexFile.classes.asSequence() }
        val byType = HashMap<String, ClassDef>()

        val binds = mutableListOf<Pair<ClassDef, com.android.tools.smali.dexlib2.iface.Method>>()
        var cellBinds = 0
        for (classDef in classes) {
            byType[classDef.type] = classDef
            for (method in classDef.methods) {
                val parameters = method.parameterTypes.map { it.toString() }
                if (method.returnType == "V" && parameters.size == 10 &&
                    parameters[1] == DYNAMIC_PATCH_DESCRIPTOR &&
                    parameters[4] == "Lcom/ss/android/ugc/aweme/search/pages/result/topsearch/core/model/LynxSSRInfo;" &&
                    parameters[8] == SEARCH_MIX_FEED_DESCRIPTOR
                ) binds += classDef to method
                if (classDef.type == "Lcom/ss/android/ugc/aweme/search/lynx/core/ui/component/SearchLynxCardCell;" &&
                    method.name == "onBindItemView" && method.returnType == "V"
                ) cellBinds++
            }
        }
        assertEquals("methods with the holder bind's shape: ${binds.map { "${it.first.type}->${it.second.name}" }}", 1, binds.size)
        val (holder, bind) = binds.single()
        assertFalse("the holder bind is static, so p0 is not the holder", AccessFlags.STATIC.isSet(bind.accessFlags))
        assertEquals("the holder is not a RecyclerView view holder", viewHolder, holder.superclass)
        assertEquals("the patch is not the bind's second parameter", DYNAMIC_PATCH_DESCRIPTOR, bind.parameterTypes[1].toString())
        assertEquals("the Lynx card cell's binds", 1, cellBinds)

        val callers = byType.values.asSequence().flatMap { it.methods.asSequence() }.filter { method ->
            method.implementation?.instructions?.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
                    it.definingClass == holder.type && it.name == bind.name &&
                        it.parameterTypes.map(Any::toString) == bind.parameterTypes.map(Any::toString)
                } == true
            } == true
        }.map { "${it.definingClass}->${it.name}" }.toList()
        assertEquals("callers of the holder bind: $callers", 1, callers.size)
        assertTrue("the holder bind's caller does not take a view holder and a card: $callers",
            byType.values.asSequence().flatMap { it.methods.asSequence() }.any { method ->
                "${method.definingClass}->${method.name}" == callers.single() &&
                    method.parameterTypes.map(Any::toString).containsAll(listOf(viewHolder, SEARCH_MIX_FEED_DESCRIPTOR))
            })
    }
}
