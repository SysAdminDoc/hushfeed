/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e1fb74c7
 */
package app.morphe.patches.tiktok.misc.commentsort

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/commentsort/CommentSortControls;"

@Suppress("unused")
val commentSortControlsPatch = bytecodePatch(
    name = "Comment sort controls",
    description = "Shows TikTok's own comment sort sheet on every post, with its hot, newest, " +
        "media and creator options, instead of the cut-down row an account outside the rollout " +
        "is given. Supports TikTok 46.2.3.",
    // Off by default, unlike upstream. It changes a surface every comment sheet shows and
    // nobody here has watched it on a device yet.
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCommentSortControls()V",
        )

        // The style the rollout hands out decides whether the full sheet or the cut-down row is
        // built, so the value is read on its way out of the settings lookup. The insert lands
        // inside the method's try block, so anything thrown out of the extension is swallowed by
        // the host's own handler and the stock style is used: it fails to the unpatched
        // behaviour rather than taking the comment sheet down, but it fails silently.
        val style = CommentSortOptionStyleFingerprint.method
        val styleInstructions = style.implementation!!.instructions.toList()
        val stringIndex = styleInstructions.indexOfFirst {
            it.opcode == Opcode.CONST_STRING &&
                it.getReference<StringReference>()?.string == "comment_sort_opt_style"
        }
        check(stringIndex >= 0) {
            "comment_sort_opt_style is not in the method the fingerprint matched"
        }
        val resultIndex = checkNotNull(
            styleInstructions.withIndex().firstOrNull { (index, instruction) ->
                index > stringIndex && instruction.opcode == Opcode.MOVE_RESULT
            },
        ) { "nothing reads a result after comment_sort_opt_style any more" }.index
        val styleRegister = (styleInstructions[resultIndex] as OneRegisterInstruction).registerA
        style.addInstructions(
            resultIndex + 1,
            """
                invoke-static {v$styleRegister}, $EXTENSION_CLASS_DESCRIPTOR->forceOptionStyle(I)I
                move-result v$styleRegister
            """,
        )

        // A post also has to pass an eligibility check of its own, so the style alone is not
        // enough. Answering true early leaves the original body in place for the switch-off case.
        CommentSortEligibilityFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->shouldForceSortEligibility()Z
                    move-result v0
                    if-eqz v0, :morphe_stock_comment_sort_eligibility
                    return v0
                """,
                ExternalLabel("morphe_stock_comment_sort_eligibility", getInstruction(0)),
            )
        }
    }
}
