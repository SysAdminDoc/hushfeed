/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.commenttools

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.numberOfParameterRegisters

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/comment/CommentTools;"

/**
 * The brand animation that plays over the comment sheet when a comment matches a campaign's
 * trigger. TikTok calls it a comment easter egg; the model behind it lives under
 * `CommerceConfigData`, and the trigger runs from the publish view model once the comment is
 * sent. The event name is the anchor because every class on the path is obfuscated.
 */
private object CommentEasterEggTriggerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/ss/android/ugc/aweme/comment/model/CommentSurprise;"),
    strings = listOf("comment_easter_egg_trigger"),
)

@Suppress("unused")
val hideCommentEggsPatch = bytecodePatch(
    name = "Hide comment popup ads",
    description = "Stops the brand animation that plays over the comment sheet when a comment " +
        "matches an advertiser's trigger word or emoji.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideCommentEggs()V",
        )

        CommentEasterEggTriggerFingerprint.method.apply {
            // v0 is scratch. With no local registers it would be a parameter instead, and the
            // early return would then run with the caller's receiver overwritten.
            val localRegisters = implementation!!.registerCount - numberOfParameterRegisters
            if (localRegisters < 1) {
                throw PatchException(
                    "Hide comment popup ads: the trigger has no local register to test with.",
                )
            }

            addInstructions(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldHideCommentEgg()Z
                    move-result v0
                    if-eqz v0, :morphe_show_comment_egg
                    return-void
                    :morphe_show_comment_egg
                    nop
                """,
            )
        }
    }
}
