package app.morphe.patches.tiktok.misc.translation

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object MultiCommentTranslationStartFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/List;", "L", "Z"),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.definingClass ==
                "Lcom/ss/android/ugc/aweme/comment/translation/MultiTranslationBody;"
        } == true
    },
)

internal object BaseCommentCellBindFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;",
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf("comment_panel"),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass ==
                    "Lcom/ss/android/ugc/aweme/commentv2/commentlist/powercell/BaseCommentCell;" &&
                    reference.returnType == "Lcom/ss/android/ugc/aweme/comment/model/Comment;"
            } == true
        } == true
    },
)

internal object CommentListLoadedFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ ->
        val fields = method.implementation?.instructions?.mapNotNull { instruction ->
            instruction.getReference<FieldReference>()
        } ?: emptyList()
        fields.any { reference ->
            reference.definingClass == "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;" &&
                reference.name == "items" &&
                reference.type == "Ljava/util/List;"
        } && fields.any { reference ->
            reference.definingClass == "Lcom/ss/android/ugc/aweme/comment/model/CommentItemList;" &&
                reference.name == "lazySplitItemsParseTask"
        }
    },
)

internal object MultiCommentTranslationCompleteFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf("MultiCommentTranslationTask startTranslate onComplete "),
)
