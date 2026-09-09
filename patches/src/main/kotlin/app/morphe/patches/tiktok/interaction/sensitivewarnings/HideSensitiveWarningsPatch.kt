/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.sensitivewarnings

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.VideoAuthorInfoParamsFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.interaction.blockauthor.registerOfParameter
import app.morphe.patches.tiktok.misc.settings.settingsPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/feed/SensitiveWarnings;"
private const val VIDEO_ITEM_PARAMS_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;"

/**
 * Hooks the item bind rather than the overlay itself: the masks live on the Aweme, and
 * clearing them there happens before anything has read the model to build the interstitial.
 * This is the same anchor the block button uses for its tracking, so the injection is a
 * second prepend on that method.
 */
@Suppress("unused")
val hideSensitiveWarningsPatch = bytecodePatch(
    name = "Skip content warnings",
    description = "Adds an option to play videos TikTok has classified without the warning " +
        "overlay asking to be tapped through first.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSensitiveWarnings()V",
        )

        val method = VideoAuthorInfoParamsFingerprint.method
        val paramsRegister = method.registerOfParameter(VIDEO_ITEM_PARAMS_DESCRIPTOR)
            ?: error("Could not locate the VideoItemParams parameter on paramSync2StateAccept")

        // /range: a parameter register on a method this size sits well above v15.
        method.addInstruction(
            0,
            "invoke-static/range { $paramsRegister .. $paramsRegister }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->clear(Ljava/lang/Object;)V",
        )
    }
}
