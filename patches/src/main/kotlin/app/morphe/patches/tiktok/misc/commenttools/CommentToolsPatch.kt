/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
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
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.misc.translation.BaseCommentCellBindFingerprint
import app.morphe.patches.tiktok.misc.translation.CommentListLoadedFingerprint
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.objectIn
import app.morphe.util.getFreeRegisterProvider
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
    description = "Hides comments that contain chosen words or come from chosen accounts, turns " +
        "the thumbs down on each comment into a block button, hides comments made of an image " +
        "or a sticker rather than words, and adds a box above the comments that narrows them " +
        "by what they say or who said it. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

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

            // A register nothing is holding here. This injects into the middle of the bind,
            // where v0 belongs to the host, and it was written over on the strength of being
            // dead on this one build.
            val cellRegister = getFreeRegisterProvider(
                managerReadyIndex + 1,
                1,
                listOf(managerRegister),
            ).getFreeRegister4Bit()

            addInstructions(
                managerReadyIndex + 1,
                """
                    move-object/from16 v$cellRegister, p0
                    iget-object v$cellRegister, v$cellRegister, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                    invoke-static {v$cellRegister, v$managerRegister}, $EXTENSION_CLASS_DESCRIPTOR->registerCommentCell(Landroid/view/View;Ljava/lang/Object;)V
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

            val responseRegister = (implementation!!.instructions.elementAt(responseReadyIndex)
                as? TwoRegisterInstruction)?.registerB ?: throw PatchException(
                "Comment tools: the loaded comment list is not read from a register.",
            )

            addInstructions(
                responseReadyIndex,
                callThroughLocals(
                    "Comment tools",
                    "invoke-static",
                    "$EXTENSION_CLASS_DESCRIPTOR->onCommentListLoaded(Ljava/lang/Object;)V",
                    false,
                    objectIn("v$responseRegister"),
                ),
            )
        }
    }
}
