/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 *
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

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
        "Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

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
            fingerprint.method.addInstructions(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldShowSuggestedAccounts()Z
                    move-result v0
                    return v0
                """,
            )
        }
    }
}
