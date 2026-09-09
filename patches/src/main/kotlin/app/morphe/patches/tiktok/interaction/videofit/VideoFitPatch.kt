/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.videofit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.numberOfParameterRegisters

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/VideoFit;"

/**
 * The one place TikTok's chosen video size reaches the view. The whole
 * {@code com.ss.android.ugc.aweme.videoadaption} package kept its names, so the result
 * object, its width and height getters and this method are all named outright. It writes
 * the size into the view's layout parameters and then applies the offsets that go with a
 * crop, which is why the replacement has to take over the whole method rather than only
 * the size.
 */
private object SaveAdaptionResultFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/videoadaption/adaptionparams/VideoAdaptionResult;",
    name = "saveResultInner",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

@Suppress("unused")
val videoFitPatch = bytecodePatch(
    name = "Fit video to the screen",
    description = "Puts the whole of a vertical video on screen instead of cropping it to " +
        "the window. On a 9:16 phone nothing changes, because the video already fills it. " +
        "On a Fold opened up, a squarer phone or a split view the sides or the ends stop " +
        "being cut off.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SaveAdaptionResultFingerprint.method.apply {
            // p0 is the result, p1 the video view. Two locals, so v0 is free.
            check(implementation!!.registerCount - numberOfParameterRegisters >= 1) {
                "Fit video to the screen: saveResultInner has no free local register."
            }
            addInstructions(
                0,
                """
                    invoke-static/range { p0 .. p1 }, ${EXTENSION}->fitInstead(Ljava/lang/Object;Landroid/view/View;)Z
                    move-result v0
                    if-eqz v0, :morphe_crop_video
                    return-void
                    :morphe_crop_video
                    nop
                """,
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableVideoFit()V",
        )
    }
}
