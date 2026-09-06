/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.blockAuthorPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/GestureActions;"
private const val MOTION_EVENT = "Landroid/view/MotionEvent;"

/**
 * The feed's gesture listener. Its class name is obfuscated and changes between builds, so
 * it is found by shape: the OnGestureListener whose {@code onDoubleTap} hands the event to
 * the real-named {@code handleDoubleClick(MotionEvent)}. Only that listener does, and it is
 * the one VideoViewCell installs on the cell's touch layer. The landscape cell has its own
 * listener, which does not reach handleDoubleClick and so is left alone.
 */
private object FeedLongPressFingerprint : Fingerprint(
    name = "onLongPress",
    parameters = listOf(MOTION_EVENT),
    returnType = "V",
    custom = { _, classDef ->
        classDef.methods.any { method ->
            method.name == "onDoubleTap" &&
                method.parameterTypes == listOf(MOTION_EVENT) &&
                method.implementation?.instructions?.any { instruction ->
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.name == "handleDoubleClick" &&
                            reference.parameterTypes == listOf(MOTION_EVENT) &&
                            reference.returnType == "V"
                    } == true
                } == true
        }
    },
)

/**
 * Runs before TikTok's own long press handling, which is the 2x hold and the quick share
 * sheet. When the setting asks for something else the gesture is swallowed here, so those
 * two features and their switches only ever see a long press when the setting is left on
 * TikTok's own action.
 */
@Suppress("unused")
val longPressPatch = bytecodePatch(
    name = "Long-press controls",
    description = "Lets a long press on a video keep TikTok's own action, do nothing, or " +
        "open the video's comments. Brings Double-tap controls with it, which supplies the " +
        "comment control. Supports TikTok 46.2.3.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(blockAuthorPatch, doubleTapPatch)

    execute {
        FeedLongPressFingerprint.method.apply {
            // v0 is scratch; the check keeps it a local rather than a parameter register.
            check(implementation!!.registerCount > parameterTypes.size + 1) {
                "Long-press controls: onLongPress has no free local register."
            }
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION->onLongPress()Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableLongPress()V",
        )
    }
}
