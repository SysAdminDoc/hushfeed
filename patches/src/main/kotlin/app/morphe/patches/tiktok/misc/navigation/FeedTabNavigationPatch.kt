/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.navigation

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.requireLocals
import app.morphe.util.addInstructionsAtControlFlowLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val TOP_TAB_LAYOUT_ABILITY =
    "Lcom/ss/android/ugc/aweme/homepage/ui/view/tab/top/TopTabLayoutAbility;"

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/navigation/NavigationTabsFilter;"
private const val TAB_BADGES_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/navigation/TabBadges;"
private const val FEED_REFRESH_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/navigation/FeedRefresh;"

/** A method reference the way smali writes it after the invoke's registers. */
private fun com.android.tools.smali.dexlib2.iface.reference.MethodReference.smali(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

/** A field reference the way smali writes it after the instruction's registers. */
private fun com.android.tools.smali.dexlib2.iface.reference.FieldReference.smali(): String =
    "$definingClass->$name:$type"

internal fun isTopTabLayoutConstructor(method: com.android.tools.smali.dexlib2.iface.Method): Boolean =
    method.definingClass == TOP_TAB_LAYOUT_ABILITY &&
        method.name == "<init>" &&
        method.returnType == "V" &&
        method.parameterTypes.size == 2 &&
        method.parameterTypes[0].startsWith("L") &&
        method.parameterTypes[1] == "Landroid/widget/FrameLayout;"

private object TopTabLayoutConstructorFingerprint : app.morphe.patcher.Fingerprint(
    definingClass = TOP_TAB_LAYOUT_ABILITY,
    returnType = "V",
    custom = { method, _ -> isTopTabLayoutConstructor(method) },
)

@Suppress("unused")
val feedTabNavigationPatch = bytecodePatch(
    name = "Feed tab navigation",
    description = "Controls which loaded top and bottom navigation tabs remain visible, blocks newly added tabs when requested, can hide the Tako AI bubble and the unread badges on the bottom tabs, and can keep For You from reloading on a Home tap or a pull down. Switch: Hushfeed settings > Feed tabs.",
    default = true,
) {
    category("Settings")
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableFeedNavigation()V",
        )

        // One method answering to both scenes would take both hooks and leave the other list
        // unfiltered, with nothing failing.
        if (TopTabModelListFingerprint.method.name == BottomTabModelListFingerprint.method.name) {
            throw PatchException(
                "Feed tab navigation: both tab lists resolved to " +
                    "${TopTabModelListFingerprint.method.name}.",
            )
        }

        TopTabModelListFingerprint.method.let { method ->
            val returnIndices = method.implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }

            returnIndices.asReversed().forEach { returnIndex ->
                val register = (method.implementation!!.instructions[returnIndex] as OneRegisterInstruction).registerA
                method.addInstructionsAtControlFlowLabel(
                    returnIndex,
                    """
                        invoke-static/range {v$register .. v$register}, $EXTENSION_CLASS_DESCRIPTOR->filterTopTabs(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$register
                    """,
                )
            }
        }

        BottomTabModelListFingerprint.method.let { method ->
            val returnIndices = method.implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index }

            returnIndices.asReversed().forEach { returnIndex ->
                val register = (method.implementation!!.instructions[returnIndex] as OneRegisterInstruction).registerA
                method.addInstructionsAtControlFlowLabel(
                    returnIndex,
                    """
                        invoke-static/range {v$register .. v$register}, $EXTENSION_CLASS_DESCRIPTOR->filterBottomTabs(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$register
                    """,
                )
            }
        }

        TopTabLayoutConstructorFingerprint.method.let { method ->
            val returns = method.implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
            val returnIndex = returns.singleOrNull()
                ?: throw PatchException(
                    "Feed tab navigation: expected one TopTabLayoutAbility constructor return, " +
                        "found ${returns.size}.",
                )
            method.addInstruction(
                returnIndex,
                "invoke-static {p1}, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "installLoneForYouHeaderHider(Landroid/view/View;)V",
            )
        }

        // The unread badges on the bottom tabs (the red count on Inbox, the dot on Profile):
        // every show of either passes through the tab icon's own setter, so the visibility is
        // answered there, and a repeated native update cannot bring a hidden badge back.
        val countSetter = TabCountDotVisibilityFingerprint.method
        val dotSetter = TabDotVisibilityFingerprint.method
        check(countSetter.definingClass == dotSetter.definingClass) {
            "Feed tab navigation: the badge setters sit on ${countSetter.definingClass} and " +
                "${dotSetter.definingClass}, not on one tab icon."
        }
        val icon = mutableClassDefBy(countSetter.definingClass)
        check(icon.methods.any { it.name == "getCountDotView" } && icon.methods.any { it.name == "getRedDotVIew" }) {
            "Feed tab navigation: ${icon.type} has no badge views, so it is not the tab icon."
        }
        listOf(countSetter to "countDotVisibility", dotSetter to "tabDotVisibility").forEach { (setter, hook) ->
            setter.addInstructions(
                0,
                """
                    invoke-static/range {p1 .. p1}, $TAB_BADGES_CLASS_DESCRIPTOR->$hook(I)I
                    move-result p1
                """,
            )
        }

        // A Home tap or a For You tab tap asks the For You fragment's refresh with its trigger. A
        // kept tap ends the way TikTok ends a refresh that arrives mid-load: the spinner stops,
        // the refresh-end event goes out (it turns the Home icon back from its refresh arrow),
        // and the answer is TikTok's own "handled".
        val ending = ForYouRefreshFingerprint.method.refreshEnding()
            ?: throw PatchException("Feed tab navigation: the For You refresh no longer ends a refresh it skips.")
        ForYouRefreshFingerprint.method.apply {
            requireLocals("Feed tab navigation", 2)
            addInstructionsWithLabels(
                0,
                """
                    invoke-static/range {p1 .. p1}, $FEED_REFRESH_CLASS_DESCRIPTOR->allowRefresh(Ljava/lang/Object;)Z
                    move-result v0
                    if-nez v0, :refresh
                    invoke-virtual {p0}, ${ending.panelGetter.smali()}
                    move-result-object v0
                    if-eqz v0, :panel_done
                    const/4 v1, 0x0
                    invoke-interface {v0, v1}, $REFRESH_ABILITY->setRefreshing(Z)V
                    :panel_done
                    new-instance v0, ${ending.eventInit.definingClass}
                    invoke-direct {v0}, ${ending.eventInit.smali()}
                    invoke-static {v0}, ${ending.post.smali()}
                    const/4 v0, 0x1
                    return v0
                """,
                ExternalLabel("refresh", getInstruction(0)),
            )
        }

        // A pull down is kept at the refresh panel's pull listener, before it asks the fragment
        // to refresh and before it tells the feed and the fragment's other refresh listeners, any
        // of which loads the feed. The listener reads its fragment the same way to begin with.
        PullRefreshListenerFingerprint.method.apply {
            requireLocals("Feed tab navigation", 2)
            val reads = pullPanelReads()
                ?: throw PatchException("Feed tab navigation: the pull listener no longer reads its fragment.")
            addInstructionsWithLabels(
                0,
                """
                    iget-object v0, p0, ${reads.panel.smali()}
                    invoke-virtual {v0}, ${reads.context.smali()}
                    move-result-object v1
                    iget-object v1, v1, ${reads.fragment.smali()}
                    invoke-static {v1}, $FEED_REFRESH_CLASS_DESCRIPTOR->keepPull(Ljava/lang/Object;)Z
                    move-result v1
                    if-eqz v1, :pull
                    const/4 v1, 0x0
                    invoke-virtual {v0, v1}, $REFRESH_PANEL->setRefreshing(Z)V
                    new-instance v0, ${ending.eventInit.definingClass}
                    invoke-direct {v0}, ${ending.eventInit.smali()}
                    invoke-static {v0}, ${ending.post.smali()}
                    return-void
                """,
                ExternalLabel("pull", getInstruction(0)),
            )
        }
    }
}
