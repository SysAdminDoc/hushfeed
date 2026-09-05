/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.commenttools

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.BlockServiceFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.translation.BaseCommentCellBindFingerprint
import app.morphe.patches.tiktok.misc.translation.CommentListLoadedFingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/comment/CommentTools;"
private const val COMMENT_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/comment/model/Comment;"
private const val COMMENT_LIST_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;"

/**
 * Hooks the same two places the comment translation patch does: the comment cell being
 * bound (to attach the block gesture) and the comment list response being handled (to
 * filter it). The fingerprints are shared with that patch; the injection is repeated here
 * so this patch works without it.
 */
@Suppress("unused")
val commentToolsPatch = bytecodePatch(
    name = "Comment tools",
    description = "Hides comments that contain chosen words or come from chosen accounts, " +
        "and turns the thumbs down on each comment into a block button. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCommentTools()V",
        )

        // Blocking a commenter goes through the same BlockApi as the block button. Fail
        // the build rather than ship a gesture that silently does nothing.
        BlockServiceFingerprint.method

        BaseCommentCellBindFingerprint.method.apply {
            val instructions = implementation!!.instructions
            val managerMatch = instructions.withIndex().mapNotNull { (index, instruction) ->
                val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
                if (instruction.opcode != Opcode.IPUT_OBJECT ||
                    field.type != COMMENT_DESCRIPTOR ||
                    instruction !is TwoRegisterInstruction
                ) {
                    return@mapNotNull null
                }

                // The cell's state holder is written in a burst of iput-objects into the
                // same register; the comment is one of them. Take the last write of the
                // burst so the holder is fully initialised when the hook runs.
                val managerRegister = instruction.registerB
                var matchingWrites = 0
                var lastWriteIndex = index
                val searchEnd = (index + 6).coerceAtMost(instructions.lastIndex)
                for (candidateIndex in (index + 1)..searchEnd) {
                    val candidate = instructions[candidateIndex]
                    val candidateField = candidate.getReference<FieldReference>()
                    if (candidate.opcode == Opcode.IPUT_OBJECT &&
                        candidate is TwoRegisterInstruction &&
                        candidate.registerB == managerRegister &&
                        candidateField?.definingClass == field.definingClass
                    ) {
                        matchingWrites++
                        lastWriteIndex = candidateIndex
                    }
                }

                if (matchingWrites >= 2) lastWriteIndex to managerRegister else null
            }.lastOrNull() ?: throw PatchException(
                "Comment tools: could not locate the initialised comment cell state holder.",
            )
            val (managerReadyIndex, managerRegister) = managerMatch

            // Plain invoke-static cannot encode a register above v15 (see the block button
            // patch); the translation patch relies on this register being low as well.
            if (managerRegister > 15) {
                throw PatchException(
                    "Comment tools: cell state register v$managerRegister is above v15.",
                )
            }

            addInstructions(
                managerReadyIndex + 1,
                """
                    move-object/from16 v0, p0
                    iget-object v0, v0, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                    invoke-static {v0, v$managerRegister}, $EXTENSION_CLASS_DESCRIPTOR->registerCommentCell(Landroid/view/View;Ljava/lang/Object;)V
                """,
            )
        }

        CommentListLoadedFingerprint.method.apply {
            val responseReadyIndex = implementation!!.instructions.withIndex()
                .firstOrNull { (_, instruction) ->
                    instruction.getReference<FieldReference>()?.let { reference ->
                        reference.definingClass == COMMENT_LIST_DESCRIPTOR &&
                            reference.name == "lazySplitItemsParseTask"
                    } == true
                }?.index ?: throw PatchException(
                "Comment tools: could not locate the loaded comment list response.",
            )

            addInstruction(
                responseReadyIndex,
                "invoke-static {v0}, $EXTENSION_CLASS_DESCRIPTOR->onCommentListLoaded(Ljava/lang/Object;)V",
            )
        }
    }
}
