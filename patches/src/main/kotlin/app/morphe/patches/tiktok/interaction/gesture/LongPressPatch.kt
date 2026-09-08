/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
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
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/GestureActions;"
private const val SEEK_EXTENSION = "Lapp/morphe/extension/tiktok/interaction/FeedSeek;"
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
 * PlayerController is one of the classes TikTok did not rename, and neither is this callback
 * on it. It runs several times a second while a video plays and carries the source id, the
 * position and the length, which is what an edge seek needs to know, and the controller it
 * runs on is the one holding the player to seek. The other {@code onPlayProgressChange} on
 * the class takes a single float and is empty, so the parameters pick the wanted one.
 *
 * "Block the author", "Hide already seen videos" and "Stay paused after a scroll" also
 * hook this method. The first two prepend like this one; the third finds its own site by
 * searching the instructions, so a prepend at zero moves it along with everything else.
 */
private object PlayerProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "J", "J"),
    custom = { method, classDef ->
        method.name == "onPlayProgressChange" && classDef.endsWith("/PlayerController;")
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
    description = "Lets a long press on a video keep TikTok's own action, do nothing, open " +
        "the video's comments, save the original sound, or copy the link to the video or its " +
        "sound, and can turn a press on the left or right third of the screen into a jump back " +
        "or forward. Brings Double-tap controls with it, which supplies the comment control. " +
        "Supports TikTok 46.2.3.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(settingsPatch, blockAuthorPatch, doubleTapPatch)

    execute {
        FeedLongPressFingerprint.method.apply {
            // v0 is scratch; the check keeps it a local rather than a parameter register.
            check(implementation!!.registerCount > parameterTypes.size + 1) {
                "Long-press controls: onLongPress has no free local register."
            }
            addInstructionsWithLabels(
                0,
                """
                    invoke-static/range { p1 .. p1 }, $EXTENSION->onLongPress($MOTION_EVENT)Z
                    move-result v0
                    if-eqz v0, :original
                    return-void
                """,
                ExternalLabel("original", getInstruction(0)),
            )
        }

        // p0 is the controller, p1 the source id, then the position and the length, each a
        // pair of registers. The range form is what reaches them in a method this wide.
        PlayerProgressFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p5 }, " +
                "$SEEK_EXTENSION->recordProgress(Ljava/lang/Object;Ljava/lang/String;JJ)V",
        )

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableLongPress()V",
        )
    }
}
