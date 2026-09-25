/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
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

/**
 * The bottom tab icon's badge setters. The icon class is renamed on every build (`LX/0ru7;` on
 * 46.2.3, `LX/066P;` on 47.0.3), but its setters keep their names, and each is the one method of
 * that name and shape in the app. Every show of the red count (Inbox) or the dot (Profile) goes
 * through them; the callers that fetch the views themselves animate them and write no visibility.
 */
internal object TabCountDotVisibilityFingerprint : Fingerprint(
    name = "setCountDotVisibility",
    returnType = "V",
    parameters = listOf("I"),
)

internal object TabDotVisibilityFingerprint : Fingerprint(
    name = "setTabDotVisibility",
    returnType = "V",
    parameters = listOf("I"),
)

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

internal const val FOR_YOU_FRAGMENT = "Lcom/ss/android/ugc/aweme/feed/ui/FeedRecommendFragment;"

/**
 * The For You feed's refresh. A tap on Home or on the For You tab while For You is showing, and a
 * pull down at the top of the feed, all come here with TikTok's trigger enum (CLICK_BOTTOM,
 * CLICK_TOP or PULL_DOWN_REFRESH), and the answer says whether a refresh started. Its name and the
 * enum's change every build (Nj, Jt, BO, vq and qN from 46.2.3 to 47.0.3); its log lines don't.
 */
internal object ForYouRefreshFingerprint : Fingerprint(
    definingClass = FOR_YOU_FRAGMENT,
    returnType = "Z",
    strings = listOf("[tryRefresh] view invalide", "[tryRefresh] presenter is null"),
    custom = { method, _ -> method.parameterTypes.size == 1 },
)

internal const val REFRESH_ABILITY = "Lcom/ss/android/ugc/feed/platform/panel/refreshpanel/IRefreshAbility;"
internal const val EVENT_BUS_EVENT = "Lcom/ss/android/ugc/governance/eventbus/IEvent;"

/**
 * How the For You refresh ends when it starts nothing: a refresh asked for while the feed is still
 * loading stops the refresh panel's spinner and posts TikTok's refresh-end event, which turns the
 * Home icon back from its refresh arrow. The getter, the event and the bus call are read off the
 * method itself, since R8 renames all three every build.
 */
internal class RefreshEnding(val panelGetter: MethodReference, val eventInit: MethodReference, val post: MethodReference)

internal fun Method.refreshEnding(): RefreshEnding? {
    val instructions = implementation?.instructions?.toList() ?: return null
    val stop = instructions.indexOfFirst {
        it.getReference<MethodReference>()?.let { call -> call.definingClass == REFRESH_ABILITY && call.name == "setRefreshing" } == true
    }
    if (stop < 0) return null
    val getter = instructions.subList(0, stop).lastOrNull {
        it.opcode == Opcode.INVOKE_VIRTUAL && it.getReference<MethodReference>()?.let { call ->
            call.returnType == REFRESH_ABILITY && call.parameterTypes.isEmpty()
        } == true
    }?.getReference<MethodReference>() ?: return null
    val after = instructions.drop(stop + 1)
    val init = after.firstOrNull {
        it.opcode == Opcode.INVOKE_DIRECT && it.getReference<MethodReference>()?.let { call ->
            call.name == "<init>" && call.parameterTypes.isEmpty()
        } == true
    }?.getReference<MethodReference>() ?: return null
    val post = after.firstOrNull {
        it.opcode == Opcode.INVOKE_STATIC && it.getReference<MethodReference>()?.let { call ->
            call.returnType == EVENT_BUS_EVENT && call.parameterTypes.map(CharSequence::toString) == listOf(EVENT_BUS_EVENT)
        } == true
    }?.getReference<MethodReference>() ?: return null
    return RefreshEnding(getter, init, post)
}

internal const val REFRESH_PANEL = "Lcom/ss/android/ugc/feed/platform/panel/refreshpanel/RefreshPanelComponent;"
private const val FRAGMENT = "Landroidx/fragment/app/Fragment;"

/**
 * The refresh panel's pull listener. A pull down that lets go past the threshold lands here: it
 * reads the panel's fragment and asks that to refresh, then tells the feed and the fragment's other
 * refresh listeners. Stopping only the fragment's refresh left those listeners loading the feed
 * anyway (seen on the S22), so a kept pull ends here, before any of it.
 */
internal object PullRefreshListenerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("slide_down", "pull_refresh"),
    custom = { method, _ -> method.pullPanelReads() != null },
)

/** How the pull listener reaches its fragment: its panel field, the panel's context getter, the context's fragment. */
internal class PullPanelReads(val panel: FieldReference, val context: MethodReference, val fragment: FieldReference)

internal fun Method.pullPanelReads(): PullPanelReads? {
    val instructions = implementation?.instructions?.toList() ?: return null
    val panel = instructions.firstOrNull {
        it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>()?.let { field ->
            field.type == REFRESH_PANEL && field.definingClass == definingClass
        } == true
    }?.getReference<FieldReference>() ?: return null
    val context = instructions.firstOrNull {
        it.opcode == Opcode.INVOKE_VIRTUAL && it.getReference<MethodReference>()?.let { call ->
            call.name == "getPanelContext" && call.parameterTypes.isEmpty()
        } == true
    }?.getReference<MethodReference>() ?: return null
    val fragment = instructions.firstOrNull {
        it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>()?.let { field ->
            field.type == FRAGMENT && field.definingClass == context.returnType
        } == true
    }?.getReference<FieldReference>() ?: return null
    return PullPanelReads(panel, context, fragment)
}
