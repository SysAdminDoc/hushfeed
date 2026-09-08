package app.morphe.patches.tiktok.misc.translation

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.findMutableMethodOf
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/translation/CommentBatchTranslator;"

/** The line TikTok logs as a comment translation batch finishes. */
private const val COMPLETION_ANCHOR = "MultiCommentTranslationTask startTranslate onComplete "

/**
 * Every method that carries the completion anchor.
 *
 * <p>The shape is checked rather than assumed: the hook passes p0 as the batch runner, which is
 * the first parameter only in a static method, so a carrier that is not static, not void, or
 * does not take exactly one object would be hooked wrongly rather than not at all. A host that
 * changes any of that stops the build instead of shipping a hook that reads the wrong register.
 */
private fun BytecodePatchContext.completionCarriers(): List<MutableMethod> {
    val carriers = mutableListOf<MutableMethod>()
    val wrongShape = mutableListOf<String>()
    classDefForEach { classDef ->
        for (method in classDef.methods) {
            val carriesAnchor = method.implementation?.instructions?.any { instruction ->
                instruction.getReference<StringReference>()?.string == COMPLETION_ANCHOR
            } == true
            if (!carriesAnchor) continue

            val isStatic = AccessFlags.STATIC.isSet(method.accessFlags)
            if (!isStatic || method.returnType != "V" || method.parameterTypes.size != 1 ||
                !method.parameterTypes.single().startsWith("L")
            ) {
                wrongShape += "${method.definingClass}->${method.name}"
                continue
            }
            carriers += mutableClassDefBy(classDef).findMutableMethodOf(method)
        }
    }

    if (wrongShape.isNotEmpty()) {
        throw PatchException(
            "Translate comments: the batch completion anchor is on a method this cannot hook: " +
                wrongShape.joinToString(", ") + ".",
        )
    }
    if (carriers.isEmpty()) {
        throw PatchException("Translate comments: no method carries the batch completion anchor.")
    }
    // All of them have to take the same thing. The extension answers a runner with no results
    // field by standing the whole feature down for the session, so hooking a carrier that takes
    // some other object would turn the first comment list into the opposite of this fix.
    // toString because dexlib2 hands back CharSequence, which does not sort or compare.
    val parameterTypes = carriers.map { it.parameterTypes.single().toString() }.toSet()
    if (parameterTypes.size != 1) {
        throw PatchException(
            "Translate comments: the batch completion carriers take different things, so one of " +
                "them is not the batch runner: " + parameterTypes.sorted().joinToString(", ") + ".",
        )
    }
    return carriers
}

@Suppress("unused")
val commentTranslationPatch = bytecodePatch(
    name = "Translate comments",
    description = "Adds comment translation controls using TikTok's translation system, with selectable language exclusions.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCommentTranslation()V",
        )

        BaseCommentCellBindFingerprint.method.apply {
            val instructions = implementation!!.instructions
            val managerMatch = instructions.withIndex().mapNotNull { (index, instruction) ->
                val field = instruction.getReference<FieldReference>()
                    ?: return@mapNotNull null
                if (instruction.opcode != Opcode.IPUT_OBJECT ||
                    field.type != "Lcom/ss/android/ugc/aweme/comment/model/Comment;" ||
                    instruction !is TwoRegisterInstruction
                ) {
                    return@mapNotNull null
                }

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
                "Translate comments: could not locate initialized native comment translation manager.",
            )
            val (managerReadyIndex, managerRegister) = managerMatch

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
                        reference.definingClass == "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;" &&
                            reference.name == "lazySplitItemsParseTask"
                    } == true
                }?.index ?: throw PatchException(
                "Translate comments: could not locate loaded comment list response.",
            )

            addInstruction(
                responseReadyIndex,
                "invoke-static {v0}, $EXTENSION_CLASS_DESCRIPTOR->onCommentListLoaded(Ljava/lang/Object;)V",
            )
        }

        MultiCommentTranslationStartFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {v16 .. v18}, $EXTENSION_CLASS_DESCRIPTOR->onNativeBatchStart(Ljava/lang/Object;Ljava/lang/Object;Z)V
            """,
        )

        // Every method carrying the anchor, not the first one a fingerprint happened to
        // match. On 46.2.3 the string sits in two bodies of the same class, both static and
        // both V(L), and `.method` takes one of them without a word about the other. A batch
        // finishing through the unhooked path was never marked done or failed, so its key sat
        // pending and the batch was either refused for good or asked for again on every bind.
        completionCarriers().forEach { carrier ->
            carrier.addInstructions(
                0,
                """
                    invoke-static/range {p0 .. p0}, $EXTENSION_CLASS_DESCRIPTOR->onNativeBatchComplete(Ljava/lang/Object;)V
                """,
            )
        }
    }
}
