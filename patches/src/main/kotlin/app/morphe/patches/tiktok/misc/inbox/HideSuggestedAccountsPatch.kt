/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getFreeRegisterProvider

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/inbox/InboxControls;"

/**
 * Answers the same `hide_inbox_suggested_accounts` switch as the row hiding in
 * `Hide inbox items`, one layer earlier: the widget is never built, so nothing flashes,
 * and the Activity and New followers pages are covered as well as the Inbox tab.
 */
@Suppress("unused")
val hideSuggestedAccountsPatch = bytecodePatch(
    name = "Hide suggested accounts",
    description = "Stops the suggested accounts list from being built on the Activity, New " +
        "followers and Inbox pages. Shares its switch with Hide inbox items. " +
        "",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideSuggestedAccounts()V",
        )

        listOf(
            ActivityRecommendUserWidgetEnableFingerprint,
            NewFollowersRecommendUserWidgetEnableFingerprint,
            NewFollowersRecommendUserSkeletonEnableFingerprint,
            InboxRecommendUserWidgetEnableFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.hideInboxWidget("shouldShowSuggestedAccounts")
        }
    }
}

/** A disabled hide switch must leave the native rollout and app-availability checks intact. */
internal fun MutableMethod.hideInboxWidget(extensionMethod: String) {
    val answerRegister = getFreeRegisterProvider(0, 1).getFreeRegister4Bit()
    addInstructionsWithLabels(
        0,
        """
            invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->$extensionMethod()Z
            move-result v$answerRegister
            if-nez v$answerRegister, :native_widget_gate
            return v$answerRegister
        """,
        ExternalLabel("native_widget_gate", getInstruction(0)),
    )
}
