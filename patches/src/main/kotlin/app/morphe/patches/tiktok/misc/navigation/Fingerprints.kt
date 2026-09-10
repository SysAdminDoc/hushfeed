package app.morphe.patches.tiktok.misc.navigation

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val TAB_ABILITY = "/TabAbilityAssem;"
private const val COPY_ON_WRITE = "Ljava/util/concurrent/CopyOnWriteArrayList;"
private const val TOP_TAB_SCENE = "scene_get_top_tab"
private const val BOTTOM_TAB_SCENE = "scene_get_bottom_tab"

/**
 * Whether the method is a tab list getter for [scene]: build the tabs, hand back the list.
 *
 * <p>`TabAbilityAssem` keeps its own name and carries five `()List` methods whose names R8
 * assigns, two of which the patch wants. They were written here as `W52` and `OA`, which is what
 * they are called on 46.2.3 and not on any build since. Three of the five do more than hand back a
 * list; the two that matter are three instructions long, and what tells those two apart is the
 * method each calls first to fill the list, one of which reports itself as `scene_get_top_tab` and
 * the other as `scene_get_bottom_tab`. Those two strings are on all three builds.
 */
private fun Method.isTabModelList(classDef: ClassDef, scene: String): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false
    if (instructions.size != 3) return false
    if (instructions[0].opcode != Opcode.INVOKE_VIRTUAL) return false
    if (instructions[1].opcode != Opcode.IGET_OBJECT) return false
    if (instructions[2].opcode != Opcode.RETURN_OBJECT) return false
    val list = instructions[1].getReference<FieldReference>() ?: return false
    if (list.definingClass != classDef.type || list.type != COPY_ON_WRITE) return false
    val ensure = instructions[0].getReference<MethodReference>() ?: return false
    if (ensure.definingClass != classDef.type) return false
    if (ensure.returnType != "V" || ensure.parameterTypes.isNotEmpty()) return false
    val filler = classDef.methods.firstOrNull {
        it.name == ensure.name && it.returnType == "V" && it.parameterTypes.none()
    } ?: return false
    return filler.implementation?.instructions?.any {
        it.getReference<StringReference>()?.string == scene
    } == true
}

/** The one getter for [scene], or nothing, so that a second appearing is a failure to read. */
private fun Method.isOnlyTabModelList(classDef: ClassDef, scene: String) =
    isTabModelList(classDef, scene) &&
        classDef.methods.count { it.isTabModelList(classDef, scene) } == 1

internal object TopTabModelListFingerprint : Fingerprint(
    definingClass = TAB_ABILITY,
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    custom = { method, classDef -> method.isOnlyTabModelList(classDef, TOP_TAB_SCENE) },
)

internal object BottomTabModelListFingerprint : Fingerprint(
    definingClass = TAB_ABILITY,
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    custom = { method, classDef -> method.isOnlyTabModelList(classDef, BOTTOM_TAB_SCENE) },
)
