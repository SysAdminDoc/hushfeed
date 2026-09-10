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
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.BlockServiceFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.misc.translation.BaseCommentCellBindFingerprint
import app.morphe.patches.tiktok.misc.translation.CommentListLoadedFingerprint
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.constantBefore
import app.morphe.patches.tiktok.shared.dispatchTarget
import app.morphe.patches.tiktok.shared.dispatchesOnIndex
import app.morphe.patches.tiktok.shared.objectIn
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.writeRegister
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/comment/CommentTools;"
private const val COMMENT_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/comment/model/Comment;"
private const val COMMENT_LIST_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;"
private const val TOUCH_LISTENER_DESCRIPTOR = "Landroid/view/View\$OnTouchListener;"
private const val RELATIVE_LAYOUT = "Landroid/widget/RelativeLayout;"

/**
 * The comment like-and-hate view's bind. The view keeps no name from build to build (`LX/0nvj;`
 * on 46.2.3, `LX/0lNa;` on 46.8.3), but the line its bind logs is the same on every build and in
 * no other method of the app.
 */
private object CommentLikeAndHateBindFingerprint : Fingerprint(
    strings = listOf("diggView bind: comment id "),
)

/**
 * The cell keeps its own name and so does `onBindItemView`; the model it is handed does not. That
 * was `LX/0lOS;` on 46.2.3 and is `LX/0U5F;` on 46.8.3, and the class has one `onBindItemView`
 * on every build, so the parameter is taken as any object.
 */
private object CommentMoreCellBindFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/CommentMoreItemCell;",
    returnType = "V",
    parameters = listOf("L"),
    custom = { method, _ -> method.name == "onBindItemView" },
)

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
        "by what they say or who said it.",
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
        // The view installs its like and dislike touch listeners from one parameterless method,
        // each on a RelativeLayout it keeps. Found by that shape inside the view's own class.
        val likeAndHate = CommentLikeAndHateBindFingerprint.originalClassDef
        val installers = likeAndHate.methods.filter {
            it.returnType == "V" && it.parameterTypes.isEmpty() && it.touchInstalls().isNotEmpty()
        }
        if (installers.size != 1) {
            throw PatchException(
                "Comment tools: expected one method of ${likeAndHate.type} to install its touch " +
                    "listeners, found ${installers.size}",
            )
        }
        mutableClassDefBy(likeAndHate.type).findMutableMethodOf(installers.single())
            .captureDislikeTouchListener { classDefByOrNull(it) }
        CommentMoreCellBindFingerprint.method.registerReplySearch { classDefByOrNull(it) }

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

/**
 * The reply control's bound model and the three members the hook reads off it: its state, the
 * data it keeps about the parent comment, and the parent itself. Every one is renamed per build
 * (`LX/0nlo;`, `LIZ()I`, `LLILZIL` and `LX/0nls;->LJI` on 46.2.3; `LX/0lDH;`, `LIZ()I`,
 * `LLJILLL` and `LX/0lDJ;->LJI` on 46.8.3), so none is written down: the model is what the bind
 * casts its item to, the state is the one `()I` the cell's own relayout of that model reads, and
 * the parent is the one `Comment` held by one of the model's fields.
 */
private class ReplyModel(
    val type: String,
    val state: MethodReference,
    val data: FieldReference,
    val parent: FieldReference,
)

private fun MutableMethod.replyModel(classOf: (String) -> ClassDef?): ReplyModel {
    fun fail(why: String): Nothing = throw PatchException("Comment tools: $why")
    val instructions = implementation?.instructions?.toList() ?: fail("reply bind has no body")
    val item = implementation!!.registerCount - 1

    // The model: what the bind casts its item to, directly or through the copy made just before.
    val type = instructions.withIndex().firstNotNullOfOrNull { (index, instruction) ->
        if (instruction.opcode != Opcode.CHECK_CAST) return@firstNotNullOfOrNull null
        val register = (instruction as OneRegisterInstruction).registerA
        val copy = instructions.getOrNull(index - 1)
        val holdsItem = register == item ||
            (copy is TwoRegisterInstruction && copy.opcode.name.startsWith("move-object") &&
                copy.registerA == register && copy.registerB == item)
        if (holdsItem) instruction.getReference<TypeReference>()?.type else null
    } ?: fail("the reply bind does not cast its item to a model")

    // The state: the one no-argument int the cell's own (model, boolean) relayout reads off it.
    val cell = classOf(definingClass) ?: fail("the reply cell $definingClass is not in the app")
    val relayouts = cell.methods.filter {
        it.parameterTypes.map(CharSequence::toString) == listOf(type, "Z") && it.returnType == "V"
    }
    if (relayouts.size != 1) fail("expected one ($type, boolean) method on the reply cell, found ${relayouts.size}")
    val states = relayouts.single().implementation?.instructions?.toList().orEmpty()
        .filter { it.opcode == Opcode.INVOKE_VIRTUAL }
        .mapNotNull { it.getReference<MethodReference>() }
        .filter {
            it.definingClass == type && it.parameterTypes.isEmpty() && it.returnType == "I" &&
                it.name != "hashCode"
        }
        .distinctBy { it.name }
    if (states.size != 1) fail("expected the reply cell to read one state off $type, found ${states.size}")

    // The parent: the one Comment held by one of the model's fields.
    val model = classOf(type) ?: fail("the reply model $type is not in the app")
    val holders = model.fields.filter { !AccessFlags.STATIC.isSet(it.accessFlags) }.mapNotNull { field ->
        val comments = classOf(field.type)?.fields
            ?.filter { it.type == COMMENT_DESCRIPTOR && !AccessFlags.STATIC.isSet(it.accessFlags) }
            .orEmpty()
        if (comments.size == 1) field to comments.single() else null
    }
    if (holders.size != 1) fail("expected one field of $type to hold the parent comment, found ${holders.size}")
    val (data, parent) = holders.single()
    return ReplyModel(type, states.single(), data, parent)
}

private val FieldReference.smali get() = "$definingClass->$name:$type"

/** The reply control is a separate holder whose bound model carries its parent comment. */
internal fun MutableMethod.registerReplySearch(classOf: (String) -> ClassDef?) {
    val native = implementation ?: throw PatchException("Comment tools: reply bind has no body")
    val model = replyModel(classOf)
    val returns = native.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
    if (returns.isEmpty()) throw PatchException("Comment tools: reply bind has no return")
    val holderRegister = native.registerCount - 2
    val itemRegister = native.registerCount - 1
    for (index in returns.reversed()) {
        val registers = getFreeRegisterProvider(index, 3, listOf(holderRegister, itemRegister))
        val viewRegister = registers.getFreeRegister4Bit()
        val modelRegister = registers.getFreeRegister4Bit()
        val stateRegister = registers.getFreeRegister4Bit()
        // The relayout has finished changing the control's native height. Its model owns the
        // parent Comment and computes state4 for a control that must stay collapsed when search
        // clears.
        addInstructions(
            index,
            """
                move-object/from16 v$viewRegister, p0
                iget-object v$viewRegister, v$viewRegister, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                move-object/from16 v$modelRegister, p1
                check-cast v$modelRegister, ${model.type}
                invoke-virtual {v$modelRegister}, ${model.type}->${model.state.name}()I
                move-result v$stateRegister
                iget-object v$modelRegister, v$modelRegister, ${model.data.smali}
                iget-object v$modelRegister, v$modelRegister, ${model.parent.smali}
                invoke-static {v$viewRegister, v$modelRegister, v$stateRegister}, Lapp/morphe/extension/tiktok/comment/CommentSearch;->onReplyControlBound(Landroid/view/View;Ljava/lang/Object;I)V
            """,
        )
    }
}

/**
 * One `setOnTouchListener` on a RelativeLayout the view keeps, with the merged listener group and
 * the number it was built with.
 */
private class TouchInstall(val index: Int, val group: String, val number: Int)

/**
 * Every touch listener the method installs on a RelativeLayout field of its own class, built
 * just above the install as `new Group(view, number)`. Anything else is not one of these.
 */
private fun Method.touchInstalls(): List<TouchInstall> {
    val instructions = implementation?.instructions?.toList() ?: return emptyList()
    return instructions.withIndex().mapNotNull { (index, instruction) ->
        val target = instruction.getReference<MethodReference>()
        if ((instruction.opcode != Opcode.INVOKE_VIRTUAL && instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE) ||
            target?.definingClass != "Landroid/view/View;" || target.name != "setOnTouchListener" ||
            target.parameterTypes.map(CharSequence::toString) != listOf(TOUCH_LISTENER_DESCRIPTOR) ||
            target.returnType != "V"
        ) {
            return@mapNotNull null
        }
        val (receiver, listener) = when (instruction) {
            is FiveRegisterInstruction -> instruction.registerC to instruction.registerD
            is RegisterRangeInstruction -> instruction.startRegister to instruction.startRegister + 1
            else -> return@mapNotNull null
        }
        val field = instructions.getOrNull(instructions.writerOf(receiver, index))
            ?.takeIf { it.opcode == Opcode.IGET_OBJECT }
            ?.getReference<FieldReference>()
        if (field?.definingClass != definingClass || field.type != RELATIVE_LAYOUT) return@mapNotNull null

        // The listener: the constructor call on the register handed over, taking the view and
        // then the number that picks its body.
        val initIndex = (index - 1 downTo 0).firstOrNull { at ->
            val call = instructions[at]
            val first = when (call) {
                is FiveRegisterInstruction -> call.registerC
                is RegisterRangeInstruction -> call.startRegister
                else -> null
            }
            (call.opcode == Opcode.INVOKE_DIRECT || call.opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                call.getReference<MethodReference>()?.name == "<init>" && first == listener
        } ?: return@mapNotNull null
        val init = instructions[initIndex]
        val constructor = init.getReference<MethodReference>()!!
        if (constructor.parameterTypes.lastOrNull()?.toString() != "I") return@mapNotNull null
        val numberRegister = when (init) {
            is FiveRegisterInstruction -> listOf(init.registerC, init.registerD, init.registerE, init.registerF, init.registerG)[init.registerCount - 1]
            is RegisterRangeInstruction -> init.startRegister + init.registerCount - 1
            else -> return@mapNotNull null
        }
        val number = constantBefore(instructions, initIndex, numberRegister) ?: return@mapNotNull null
        TouchInstall(index, constructor.definingClass, number)
    }
}

/** The body the group's own `onTouch` dispatches to for the install's number. */
private fun TouchInstall.body(classOf: (String) -> ClassDef?): Method? {
    val listeners = classOf(group) ?: return null
    var at: Method = listeners.methods.firstOrNull {
        it.name == "onTouch" && it.returnType == "Z" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/View;", "Landroid/view/MotionEvent;")
    } ?: return null
    repeat(4) {
        val next = at.dispatchTarget(listeners, number) ?: return null
        if (!next.dispatchesOnIndex()) return next
        at = next
    }
    return null
}

/** Whether the method asks the comment the named question. */
private fun Method.asksComment(question: String) =
    implementation?.instructions?.any {
        val call = it.getReference<MethodReference>()
        call?.definingClass == COMMENT_DESCRIPTOR && call.name == question
    } == true

/** The nearest instruction above [index] that writes [register], or -1. */
private fun List<Instruction>.writerOf(register: Int, index: Int): Int {
    for (at in index - 1 downTo 0) {
        if (this[at].writeRegister == register) return at
    }
    return -1
}

/**
 * The dislike's native touch listener, handed to the extension instead of installed.
 *
 * <p>The view installs the like and the dislike back to back, identical in shape, differing only
 * by the RelativeLayout each lands on (both R8 names) and by the number the one merged listener
 * group is built with. Taking the last install worked on 46.2.3 and would have put the block
 * gesture on the like button the day the order flipped. Which is which is read off what the
 * listener does when touched: the like asks the comment `isUserDigged`, the dislike
 * `isUserBuried`, and both names are the named model's own. 46.2.3 installs the listener once
 * in one rollout and on every bind in the other; both come through here.
 */
internal fun MutableMethod.captureDislikeTouchListener(classOf: (String) -> ClassDef?) {
    val instructions = implementation!!.instructions.toList()
    val dislikes = touchInstalls().filter { install ->
        val body = install.body(classOf) ?: return@filter false
        body.asksComment("isUserBuried") && !body.asksComment("isUserDigged")
    }
    if (dislikes.size != 1) {
        throw PatchException("Comment tools: expected one native dislike touch install, found ${dislikes.size}")
    }
    val index = dislikes.single().index
    val instruction = instructions[index]
    val (receiver, count, operands) = when (instruction) {
        is FiveRegisterInstruction -> Triple(
            instruction.registerC, instruction.registerCount,
            "{v${instruction.registerC}, v${instruction.registerD}}",
        )
        is RegisterRangeInstruction -> Triple(
            instruction.startRegister, instruction.registerCount,
            "{v${instruction.startRegister} .. v${instruction.startRegister + 1}}",
        )
        else -> throw PatchException("Comment tools: unexpected native touch invocation")
    }
    if (count != 2) throw PatchException("Comment tools: native dislike touch receiver changed")
    val range = if (instruction is RegisterRangeInstruction) "/range" else ""
    replaceInstruction(
        index,
        "invoke-static$range $operands, $EXTENSION_CLASS_DESCRIPTOR->setDislikeTouchListener(Landroid/view/View;$TOUCH_LISTENER_DESCRIPTOR)V",
    )
}
