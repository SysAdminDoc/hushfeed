package app.morphe.patches.tiktok.interaction.confirmation

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.blockAuthorPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private object FollowClickFingerprint : Fingerprint(
    strings = listOf("VIDEO_CANCEL_REPORT_SKIP_BEHAVIOR", "click_add", "guide_dm"),
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
    custom = { method, _ -> method.name == "onClick" },
)

private object LikeClickFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/assem/digg/VideoDiggAssem;",
    strings = listOf("click_like", "long_press_like"),
    parameters = listOf("Landroid/view/View;", "Ljava/lang/String;"),
    returnType = "V",
)

@Suppress("unused")
val confirmInteractionsPatch = bytecodePatch(
    name = "Confirm feed interactions",
    description = "Adds optional second-tap protection to the feed Follow button and like heart. A red ring marks the armed button.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(blockAuthorPatch)
    execute {
        listOf(FollowClickFingerprint to "follow", LikeClickFingerprint to "like").forEach { (fingerprint, callback) ->
            val method = fingerprint.method
            check(method.implementation!!.registerCount > method.parameterTypes.size + 1)
            method.addInstructionsWithLabels(0, """
                invoke-static/range { p1 .. p1 }, Lapp/morphe/extension/tiktok/interaction/TapConfirmation;->$callback(Landroid/view/View;)Z
                move-result v0
                if-nez v0, :original
                return-void
            """, ExternalLabel("original", method.getInstruction(0)))
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableConfirmInteractions()V")
    }
}
