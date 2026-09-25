package app.morphe.patches.tiktok.misc.navigation

import app.morphe.Fixtures
import app.morphe.takes
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the start page asks, held to every fixture, and the tags it answers with, held to the
 * handlers TikTok runs for them.
 *
 * <p>A cold start works out its first tab in the main activity's onCreate and every path meets at
 * a comparison with "HOME". The hook goes right after that string, reads the tag and the activity
 * from the registers TikTok itself uses there, and passes the saved state from p1. The tags it
 * can answer with are the ones TikTok's own notification handlers for Friends, Inbox and Profile
 * compare with, so those comparisons are held here too.
 */
class StartPageAnchorsTest {
    @Test
    fun `the first tab is worked out in one place on every fixture, and the hook's registers reach it`() {
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            val taken = build.methods.filter { (classDef, method) -> ColdStartTabFingerprint.takes(method, classDef) }.toList()
            assertEquals("${apk.name}: ${taken.map { "${it.first.type}->${it.second.name}" }}", 1, taken.size)
            val onCreate = taken.single().second
            val start = onCreate.coldStartTab()!!
            val instructions = onCreate.implementation!!.instructions.toList()
            assertEquals("${apk.name}: the hook goes after the one \"HOME\"", "HOME",
                (instructions[start.insertAt - 1] as ReferenceInstruction).reference.let { (it as StringReference).string })
            assertEquals("${apk.name}: \"HOME\" appears once", 1, instructions.count {
                ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "HOME"
            })
            val savedState = onCreate.implementation!!.registerCount - 1
            assertTrue("${apk.name}: the activity v${start.activity}, the tag v${start.tag} or p1 (v$savedState) is past v15",
                maxOf(start.activity, start.tag, savedState) <= 15)
            assertTrue("${apk.name}: nothing may write p1 before the hook reads it", instructions.take(start.insertAt).none {
                it.opcode.setsRegister() && (it as? OneRegisterInstruction)?.registerA == savedState
            })
        }
    }

    @Test
    fun `TikTok's handlers for Friends, Inbox and Profile compare with the tags the start page answers with`() {
        val handlers = mapOf(
            "Lcom/ss/android/ugc/aweme/assem/FriendsChangeTabInterceptor;" to listOf("FRIENDS_FEED"),
            "Lcom/ss/android/ugc/aweme/inbox/InboxChangeTabInterceptor;" to listOf("NOTIFICATION", "HOME"),
            "Lcom/ss/android/ugc/profile/platform/framework/aweme/profile/ProfileChangeTabInterceptor;" to listOf("USER", "HOME"),
        )
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            for ((type, tags) in handlers) {
                val handler = build.byType[type]
                assertTrue("${apk.name}: $type is gone", handler != null)
                val decide = handler!!.methods.single { method ->
                    val parameters = method.parameterTypes.map(CharSequence::toString)
                    method.returnType == "Z" && parameters.size == 4 &&
                        parameters[1] == "Landroid/content/Intent;" && parameters[2] == "Ljava/lang/String;"
                }
                val strings = decide.implementation!!.instructions.strings()
                tags.forEach { assertTrue("${apk.name}: $type no longer compares with $it: $strings", it in strings) }
            }
            // The Friends feed's handler goes through the Friends bottom tab when there is one.
            val friends = build.byType.getValue("Lcom/ss/android/ugc/aweme/assem/FriendsChangeTabInterceptor;")
            val friendsStrings = friends.methods.flatMap { it.implementation?.instructions?.strings().orEmpty() }
            assertTrue("${apk.name}: the Friends handler never names FRIENDS_TAB", "FRIENDS_TAB" in friendsStrings)
        }
    }

    @Test
    fun `the patch asks the start page right after the comparison and keeps its answer`() {
        val root = File("src/main/kotlin").takeIf { it.isDirectory } ?: File("patches/src/main/kotlin")
        val source = File(root, "app/morphe/patches/tiktok/misc/navigation/FeedTabNavigationPatch.kt").readText()
        assertTrue("the first tab is not hooked", source.contains("ColdStartTabFingerprint.method.apply"))
        assertTrue("the hook does not hand over the activity, the tag and the saved state",
            source.contains("invoke-static {v\${start.activity}, v\${start.tag}, p1}, \$START_PAGE_CLASS_DESCRIPTOR->" +
                "coldStartTag(Landroid/app/Activity;Ljava/lang/String;Landroid/os/Bundle;)Ljava/lang/String;"))
        assertTrue("the answer does not replace the tag", source.contains("move-result-object v\${start.tag}"))
        assertTrue("the hook does not go where the helper says", source.contains("start.insertAt,"))
    }

    @Test
    fun `a synthetic onCreate shaped like TikTok's gives the registers TikTok uses`() {
        val start = onCreate().coldStartTab()
        assertTrue("the synthetic onCreate was not read", start != null)
        assertEquals(2, start!!.insertAt)
        assertEquals(4, start.tag)
        assertEquals(7, start.activity)
    }

    @Test
    fun `a jump onto the comparison, a restore of another register or a comparison of another tag is refused`() {
        assertNull("a jump lands on the comparison, so the hook would be skipped", onCreate(ifOffset = -4).coldStartTab())
        assertNull("the theme restore gets another register", onCreate(restoreRegister = 8).coldStartTab())
        assertNull("the comparison reads another register", onCreate(comparedRegister = 5).coldStartTab())
        assertNull("the switch is not told COLD_BOOT", onCreate(bootField = "ON_NEW_INTENT").coldStartTab())
    }

    /**
     * TikTok's shape in miniature: the tag in v4, the activity in v7, the join's "HOME" in v0, the
     * theme restore, then getIntent and the cold-boot switch. The if-nez at index 4 skips the
     * restore by default (+5 code units, to getIntent).
     */
    private fun onCreate(
        ifOffset: Int = 5,
        restoreRegister: Int = 7,
        comparedRegister: Int = 4,
        bootField: String = "COLD_BOOT",
    ): Method {
        val assem = "LX/Assem;"
        val boot = "LX/Boot;"
        val instructions = listOf<Instruction>(
            ImmutableInstruction21c(Opcode.CONST_STRING, 4, ImmutableStringReference("SHOP_MALL")),
            ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("HOME")),
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 0, comparedRegister, 0, 0, 0,
                ImmutableMethodReference("LX/Kt;", "eq", listOf("Ljava/lang/Object;", "Ljava/lang/Object;"), "Z")),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction21t(Opcode.IF_NEZ, 0, ifOffset),
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, restoreRegister, 6, 0, 0, 0,
                ImmutableMethodReference("LX/Theme;", "restore", listOf("LX/Act;", "Z"), "V")),
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 7, 0, 0, 0, 0,
                ImmutableMethodReference("Landroid/app/Activity;", "getIntent", listOf(), "Landroid/content/Intent;")),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1),
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, ImmutableFieldReference(boot, bootField, boot)),
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 4, 14, 1, 0, 4, 0,
                ImmutableMethodReference(assem, "switchTab", listOf("Landroid/content/Intent;", boot, "Ljava/lang/String;"), "V")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )
        return ImmutableMethod(
            assem, "onCreate", listOf(ImmutableMethodParameter("Landroid/os/Bundle;", null, null)), "V",
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            ImmutableMethodImplementation(16, instructions, null, null),
        )
    }

    private fun Iterable<Instruction>.strings(): List<String> =
        mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }

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
