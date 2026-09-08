package app.morphe.patches.tiktok.interaction.gesture

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.blockAuthorPatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/GestureActions;"
private const val COMMENT_CLASS = "Lcom/ss/android/ugc/aweme/feed/assem/videocomment/VideoCommentAssem;"

private object DoubleTapFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/panel/BaseListFragmentPanel;",
    name = "handleDoubleClick", parameters = listOf("Landroid/view/MotionEvent;"), returnType = "V",
)
private object CommentViewFingerprint : Fingerprint(
    definingClass = COMMENT_CLASS, name = "onViewCreated",
    parameters = listOf("Landroid/view/View;"), returnType = "V",
)
private object CommentBindFingerprint : Fingerprint(
    definingClass = COMMENT_CLASS,
    // This generic callback is erased to Object in DEX, despite jadx displaying VideoItemParams.
    parameters = listOf("Ljava/lang/Object;"), returnType = "V",
)

@Suppress("unused")
val doubleTapPatch = bytecodePatch(
    name = "Double-tap controls",
    description = "Lets double taps do nothing or open the current video's comments.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(settingsPatch, sharedExtensionPatch, blockAuthorPatch)
    execute {
        DoubleTapFingerprint.method.apply {
            check(implementation!!.registerCount > parameterTypes.size + 1)
            addInstructionsWithLabels(0, """
                invoke-static {}, $EXTENSION->onDoubleTap()Z
                move-result v0
                if-eqz v0, :original
                return-void
            """, ExternalLabel("original", getInstruction(0)))
        }
        CommentViewFingerprint.method.addInstruction(0,
            "invoke-static/range { p0 .. p1 }, $EXTENSION->registerCommentView(Ljava/lang/Object;Landroid/view/View;)V")
        CommentBindFingerprint.method.addInstruction(0,
            "invoke-static/range { p0 .. p1 }, $EXTENSION->bindCommentView(Ljava/lang/Object;Ljava/lang/Object;)V")
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDoubleTap()V")
    }
}
