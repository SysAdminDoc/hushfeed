package app.morphe.patches.tiktok.misc.navigation

import app.morphe.Fixtures
import app.morphe.takes
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the Feed tabs page's refresh switches stop For You from reloading, held to every fixture.
 *
 * <p>A Home tap and a For You tab tap reach the For You fragment's refresh from the home pager,
 * and a pull down reaches it from the refresh panel through an interface the fragment implements.
 * Each passes TikTok's trigger enum, and the extension tells them apart by the enum's names, so
 * those names are held here too.
 */
class ForYouRefreshAnchorsTest {
    private val triggers = listOf("CLICK_TOP", "CLICK_BOTTOM", "PULL_DOWN_REFRESH")

    @Test
    fun `the For You refresh is one method on every fixture, and its trigger carries the names the extension reads`() {
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            val taken = build.methods.filter { (classDef, method) -> ForYouRefreshFingerprint.takes(method, classDef) }.toList()
            assertEquals("${apk.name}: ${taken.map { "${it.first.type}->${it.second.name}" }}", 1, taken.size)
            val refresh = taken.single().second
            val trigger = build.byType[refresh.parameterTypes.single().toString()]
            assertTrue("${apk.name}: the refresh takes ${refresh.parameterTypes.single()}, which the fixture lacks", trigger != null)
            assertEquals("${apk.name}: ${trigger!!.type} is not an enum", "Ljava/lang/Enum;", trigger.superclass)
            val names = trigger.methods.single { it.name == "<clinit>" }.implementation!!.instructions
                .mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }
            triggers.forEach { assertTrue("${apk.name}: ${trigger.type} has no $it: $names", it in names) }
        }
    }

    /**
     * A kept refresh replays TikTok's own "nothing started" ending, read off the method: the
     * panel getter, the refresh-end event and its bus call. The getter is called on p0 in a plain
     * invoke, which reaches only v0 to v15, and the ending uses two locals.
     */
    @Test
    fun `the refresh's own ending is there to replay on every fixture`() {
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            val refresh = build.methods.single { (classDef, method) -> ForYouRefreshFingerprint.takes(method, classDef) }.second
            val ending = refresh.refreshEnding()
            assertTrue("${apk.name}: the refresh has no ending to replay", ending != null)
            assertEquals("${apk.name}: the getter", REFRESH_ABILITY, ending!!.panelGetter.returnType)
            val event = build.byType[ending.eventInit.definingClass]
            assertTrue("${apk.name}: ${ending.eventInit.definingClass} is not an event", event?.interfaces?.contains(EVENT_BUS_EVENT) == true)
            assertEquals("${apk.name}: the bus call", EVENT_BUS_EVENT, ending.post.returnType)
            val registers = refresh.implementation!!.registerCount
            assertTrue("${apk.name}: p0 is past v15", registers - 2 <= 15)
            assertTrue("${apk.name}: fewer than two locals", registers - 2 >= 2)
        }
    }

    /**
     * A pull is kept at the refresh panel's pull listener. The hook reads the panel off p0 and the
     * fragment off the panel's context with iget-object, whose registers reach only v0 to v15, and
     * uses two locals.
     */
    @Test
    fun `the pull listener is one method on every fixture and reads its fragment the way the hook does`() {
        for (apk in Fixtures.apks()) {
            val build = Build(apk)
            val taken = build.methods.filter { (classDef, method) -> PullRefreshListenerFingerprint.takes(method, classDef) }.toList()
            assertEquals("${apk.name}: ${taken.map { "${it.first.type}->${it.second.name}" }}", 1, taken.size)
            val listener = taken.single().second
            val reads = listener.pullPanelReads()!!
            assertEquals("${apk.name}: the panel field", REFRESH_PANEL, reads.panel.type)
            assertEquals("${apk.name}: the fragment field", "Landroidx/fragment/app/Fragment;", reads.fragment.type)
            val registers = listener.implementation!!.registerCount
            assertTrue("${apk.name}: p0 is past v15", registers - 1 <= 15)
            assertTrue("${apk.name}: fewer than two locals", registers - 1 >= 2)
        }
    }

    /**
     * The panel's context holds more than the fragment. A listener that reads another of its object
     * fields first must still hand the hook the Fragment field, or the pull check would get a view.
     */
    @Test
    fun `the pull hook reads the context's Fragment field, not the first field it meets`() {
        val listener = "LX/PullListener;"
        val context = "LX/PanelContext;"
        val instructions = listOf(
            ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 2, ImmutableFieldReference(listener, "panel", REFRESH_PANEL)),
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 0, 0, 0, 0, 0,
                ImmutableMethodReference(REFRESH_PANEL, "getPanelContext", listOf(), context)),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction22c(Opcode.IGET_OBJECT, 1, 0, ImmutableFieldReference(context, "view", "Landroid/view/View;")),
            ImmutableInstruction22c(Opcode.IGET_OBJECT, 1, 0, ImmutableFieldReference(context, "fragment", "Landroidx/fragment/app/Fragment;")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        )
        val method = ImmutableMethod(
            listener, "onPull", listOf(), "V", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null,
            ImmutableMethodImplementation(3, instructions, null, null),
        )
        val reads = method.pullPanelReads()
        assertTrue("the listener's reads were not found", reads != null)
        assertEquals("fragment", reads!!.fragment.name)
        assertEquals("getPanelContext", reads.context.name)
        assertEquals("panel", reads.panel.name)
    }

    /** The home pager passes each tap's trigger by name, so the switch knows a tap from a pull. */
    @Test
    fun `on 47_0_3 the home pager hands the refresh both taps and a pull`() {
        val build = Build(Fixtures.apks().single { it.name.contains("47.0.3") })
        val refresh = build.methods.single { (classDef, method) -> ForYouRefreshFingerprint.takes(method, classDef) }.second
        val trigger = refresh.parameterTypes.single().toString()
        val pager = build.byType.getValue("Lcom/ss/android/ugc/aweme/main/assems/mainfragment/HomeViewPagerAssem;")
        val handed = pager.methods.filter { it.calls(refresh) }.flatMap { method ->
            method.implementation!!.instructions.mapNotNull { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.takeIf { it.definingClass == trigger }?.name
            }
        }.toSet()
        assertEquals(triggers.toSet(), handed)
    }

    @Test
    fun `the navigation patch asks before the refresh runs`() {
        val root = File("src/main/kotlin").takeIf { it.isDirectory } ?: File("patches/src/main/kotlin")
        val source = File(root, "app/morphe/patches/tiktok/misc/navigation/FeedTabNavigationPatch.kt").readText()
        assertTrue("the refresh is not hooked", source.contains("ForYouRefreshFingerprint.method.apply"))
        assertTrue("the hook does not hand over the trigger",
            source.contains("invoke-static/range {p1 .. p1}, \$FEED_REFRESH_CLASS_DESCRIPTOR->allowRefresh(Ljava/lang/Object;)Z"))
        assertTrue("a kept refresh does not stop the spinner", source.contains("invoke-interface {v0, v1}, \$REFRESH_ABILITY->setRefreshing(Z)V"))
        assertTrue("a kept refresh does not post the refresh-end event", source.contains("invoke-static {v0}, \${ending.post.smali()}"))
        assertTrue("a kept refresh does not answer TikTok's handled", source.contains("const/4 v0, 0x1\n                    return v0"))
        assertTrue("the pull listener is not hooked", source.contains("PullRefreshListenerFingerprint.method.apply"))
        assertTrue("the pull hook does not hand over the fragment",
            source.contains("invoke-static {v1}, \$FEED_REFRESH_CLASS_DESCRIPTOR->keepPull(Ljava/lang/Object;)Z"))
        assertTrue("a kept pull does not stop the panel's spinner", source.contains("invoke-virtual {v0, v1}, \$REFRESH_PANEL->setRefreshing(Z)V"))
        assertTrue("a pull the switch lets through does not go on to TikTok's listener", source.contains("if-eqz v1, :pull"))
        assertTrue("a tap the switch lets through does not go on to TikTok's refresh", source.contains("if-nez v0, :refresh"))
    }

    private fun Method.calls(target: Method): Boolean = implementation?.instructions?.any { instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let {
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
