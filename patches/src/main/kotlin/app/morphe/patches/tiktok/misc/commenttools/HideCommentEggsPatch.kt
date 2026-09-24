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
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.Reference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/comment/CommentTools;"
private const val COMMENT_SURPRISE = "Lcom/ss/android/ugc/aweme/comment/model/CommentSurprise;"
private const val COMMENT_SURPRISE_STRUCT = "Lcom/ss/android/ugc/aweme/comment/model/CommentSurpriseStruct;"
private const val COMMENT_ITEM_LIST = "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;"
private const val COMMENT_RESPONSE = "Lcom/ss/android/ugc/aweme/comment/model/CommentResponse;"

/**
 * The brand animation that plays over the comment sheet when a comment matches a campaign's
 * trigger. TikTok's server answers such a comment, or a comment page, with a CommentSurprise,
 * and every way the sheet shows one reads it out of a CommentSurpriseStruct: the publish view
 * model and the list helper that start the animation, and the observer that plays it. The
 * struct's one constructor is the only writer of that field, and every read checks it for null
 * straight away (CommentSurpriseAnchorsTest), so a constructor handed null leaves nothing to
 * play. Both classes keep their real names on every fixture.
 *
 * <p>Which surprise is a campaign's is decided by where the struct is built, not by what the
 * server wrote on the surprise: TikTok's own code reads neither the keyword nor, past the
 * publish path's analytics, the type before playing one. The struct is built in three places,
 * and each says so just before the constructor, on the same thread, so the constructor's hook
 * can decide by path. The comment-page loader builds it from the page's surprise and hands it,
 * with the scene the page was fetched for, to the one method that plays it: the default scene
 * is a campaign's, and TikTok's own celebrations (the first-comment milestone and the author's
 * own first comment) ask for scenes of their own. The publish response builds it from the
 * surprise sent with a typed comment, where type 1 is the first-comment celebration. The
 * milestone builder replays a cached first-comment surprise. Each site is a fingerprint of its
 * own shape, so a build that adds a fourth stops here, and the anchors test pins the three.
 *
 * <p>Through 0.58.0 this hooked the method that names comment_easter_egg_trigger. That method
 * only reports the animation to analytics, and its callers start the animation after it
 * returns, so returning from it early left the animation playing.
 */
private object CommentSurpriseStructFingerprint : Fingerprint(
    definingClass = COMMENT_SURPRISE_STRUCT,
    name = "<init>",
    returnType = "V",
    parameters = listOf("Lcom/ss/android/ugc/aweme/comment/model/Comment;", COMMENT_SURPRISE, "Z"),
)

private fun Method.references(): List<Reference> =
    implementation?.instructions?.mapNotNull { (it as? ReferenceInstruction)?.reference }.orEmpty()

private fun List<Reference>.constructTheStruct() =
    any { it is MethodReference && it.definingClass == COMMENT_SURPRISE_STRUCT && it.name == "<init>" }

private fun List<Reference>.readField(owner: String, field: String) =
    any { it is FieldReference && it.definingClass == owner && it.name == field }

/** The one method that plays a comment-page surprise: (struct, scene, string) returning nothing. */
private fun MethodReference.isPlayCall() =
    parameterTypes.map { it.toString() } == listOf(COMMENT_SURPRISE_STRUCT, "I", "Ljava/lang/String;") && returnType == "V"

/** The comment-page loader: builds the struct from the page's surprise and hands it, with the scene, to the play method. */
private object CommentPageSurpriseFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ ->
        val references = method.references()
        references.constructTheStruct() && references.readField(COMMENT_ITEM_LIST, "commentSurprise") &&
            references.any { it is MethodReference && it.isPlayCall() }
    },
)

/** The publish response: builds the struct from the surprise the server sent with a typed comment. */
private object PublishedSurpriseFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    custom = { method, _ ->
        val references = method.references()
        references.constructTheStruct() && references.readField(COMMENT_RESPONSE, "commentSurprise")
    },
)

/** The milestone builder: replays a cached first-comment surprise for the scene it names. */
private object MilestoneSurpriseFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    custom = { method, _ ->
        val references = method.references()
        references.constructTheStruct() &&
            references.any { it is FieldReference && it.name == "FIRST_COMMENT_MILESTONE" } &&
            references.any { it is MethodReference && it.definingClass == "Landroid/util/LruCache;" && it.name == "get" }
    },
)

private fun MutableMethod.indexOfStructConstructor() = indexOfFirstInstructionOrThrow {
    getReference<MethodReference>()?.let { it.definingClass == COMMENT_SURPRISE_STRUCT && it.name == "<init>" } == true
}

/** Says where this site is, just before the constructor: the site is a different method from the constructor, so the mark is the same thread's. */
private fun MutableMethod.markBeforeConstructor(mark: String) = addInstructions(
    indexOfStructConstructor(),
    "invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->$mark()V",
)

private val MOVES = setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)

@Suppress("unused")
val hideCommentEggsPatch = bytecodePatch(
    name = "Hide comment popup ads",
    description = "Stops the brand animation that plays over the comment sheet when a comment " +
        "matches an advertiser's trigger word or emoji. Switch: Hushfeed settings > Comments.",
    default = false,
) {
    category("Comments")
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideCommentEggs()V",
        )

        // p2 is the surprise. Replacing it before the super call is allowed: only `this` has to
        // wait for the constructor it calls.
        CommentSurpriseStructFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p2 .. p2},$EXTENSION_CLASS_DESCRIPTOR->commentSurprise(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object p2
                check-cast p2, $COMMENT_SURPRISE
            """,
        )

        // Each site says where it is just before the constructor, so the hook above can decide
        // by path. The marks take no register, and the page loader's takes the scene.
        PublishedSurpriseFingerprint.method.markBeforeConstructor("surpriseFromPublish")
        MilestoneSurpriseFingerprint.method.markBeforeConstructor("surpriseFromMilestone")
        CommentPageSurpriseFingerprint.method.apply {
            val constructor = indexOfStructConstructor()
            val play = indexOfFirstInstructionOrThrow(constructor) { getReference<MethodReference>()?.isPlayCall() == true }
            // (this, struct, scene, string): the scene is the call's third register. It is a
            // copy made after the constructor, so the register it was copied from is the one
            // that holds the scene before it; a build that passes it uncopied hands the register
            // itself.
            val sceneAtPlay = when (val call = getInstruction(play)) {
                is FiveRegisterInstruction -> call.registerE
                is RegisterRangeInstruction -> call.startRegister + 2
                else -> throw IllegalStateException("Hide comment popup ads: the play call is not an invoke this reads.")
            }
            val scene = ((constructor + 1) until play).firstNotNullOfOrNull { index ->
                val instruction = getInstruction(index)
                if (instruction.opcode in MOVES && instruction is TwoRegisterInstruction && instruction.registerA == sceneAtPlay) {
                    instruction.registerB
                } else {
                    null
                }
            } ?: sceneAtPlay
            addInstructions(
                constructor,
                "invoke-static/range {v$scene .. v$scene}, $EXTENSION_CLASS_DESCRIPTOR->surpriseFromPage(I)V",
            )
        }
    }
}
