/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/downloads/Fingerprints.kt
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object AclCommonShareFingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getCode",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

internal object AclCommonShare2Fingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getShowType",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

internal object AclCommonShare3Fingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getTranscode",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

// The five below name their class and not their method. An obfuscated method name is a
// counter-assigned label that the next TikTok build is free to reshuffle, and it was the only
// thing here that would have moved. Each is unique on 46.2.3 without it, checked method by method
// over the whole class on 2026-09-09: of the three (Context, String) methods returning a Uri, one
// holds "video/mp4" and one "video/*" and the third neither; of the two (Context, String, String)
// ones, one holds "/Camera" and the other "image/*"; and the six-parameter copy is the only
// method of its shape on its class at all. What stays is the class, the signature and the strings,
// which is what every other fingerprint in this tree is anchored on.

internal object VideoDownloadUriFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/", "video/mp4"),
)

internal object AwemeGetVideoFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Lcom/ss/android/ugc/aweme/feed/model/Video;",
    custom = { method, classDef ->
        classDef.endsWith("/Aweme;") &&
            method.name == "getVideo" &&
            method.parameterTypes.isEmpty()
    },
)

internal object CommentImageWatermarkFingerprint : Fingerprint(
    strings = listOf("[tiktok_logo]", "image/jpeg", "is_pending"),
    parameters = listOf("Landroid/graphics/Bitmap;"),
    returnType = "V",
)

internal object StickerPreviewBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L",
        "Z",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
    ),
    custom = { method, classDef ->
        if (!classDef.endsWith("/0ULN;") || method.name != "LIZ") {
            false
        } else {
            val instructions = method.implementation?.instructions
            if (instructions == null) {
                false
            } else {
                var readsUrlModel = false
                var bindsActionButton = false
                var loadsStickerImage = false

                instructions.forEach { instruction ->
                    instruction.getReference<FieldReference>()?.let { field ->
                        if (field.type == "Lcom/ss/android/ugc/aweme/base/model/UrlModel;") {
                            readsUrlModel = true
                        }
                    }

                    instruction.getReference<MethodReference>()?.let { methodReference ->
                        if (methodReference.definingClass == "LX/0ULN;" &&
                            methodReference.name == "LIZIZ" &&
                            methodReference.parameterTypes == listOf("LX/0GSy;", "LX/0ULU;") &&
                            methodReference.returnType == "V"
                        ) {
                            bindsActionButton = true
                        }

                        if (methodReference.definingClass == "LX/16zb;" &&
                            methodReference.name == "LIZJ"
                        ) {
                            loadsStickerImage = true
                        }
                    }
                }

                readsUrlModel && bindsActionButton && loadsStickerImage
            }
        }
    },
)

internal object PhotoDownloadUriFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/"),
)

internal object VideoLookupUriFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "video/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
)

internal object PhotoLookupUriFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "image/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
)

internal object VideoMediaStoreInsertFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Video\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object PhotoMediaStoreInsertFingerprint : Fingerprint(
    definingClass = "/0L4Q;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Images\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object ImagePostMediaCopyFingerprint : Fingerprint(
    definingClass = "/0L4G;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Ljava/lang/String;",
        "I",
    ),
    strings = listOf("/Camera/", "video/mp4", "image/jpeg"),
    custom = { method, _ ->
        val calls = method.implementation?.instructions?.mapNotNull { instruction ->
            instruction.getReference<MethodReference>()
        } ?: emptyList()
        calls.any { it.definingClass == "LX/0L4Q;" && it.name == "LJ" } &&
            calls.any { it.definingClass == "LX/0L4Q;" && it.name == "LIZJ" }
    },
)

internal object StickerPreviewSourceFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/ss/android/ugc/aweme/im/common/model/StickerItem;",
        "Landroid/view/View;",
        "Z",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
        "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function0;",
    ),
    custom = { method, classDef ->
        classDef.endsWith("/0UL9;") &&
            method.name == "LJ" &&
            method.implementation?.instructions?.any { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == "LX/0ULN;" &&
                        reference.name == "LIZ" &&
                        reference.parameterTypes == listOf(
                            "LX/0ULM;",
                            "Z",
                            "Ljava/lang/String;",
                            "Ljava/util/Map;",
                        ) &&
                        reference.returnType == "V"
                } == true
            } == true
    },
)

internal object DownloadSuccessCoroutineFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf(
        "DownloadAction@71a5.startDownload\$globalListener\$1\$onSuccess\$1",
        "filePath",
        "fileExist",
    ),
    custom = { method, _ -> method.name == "invokeSuspend" },
)

