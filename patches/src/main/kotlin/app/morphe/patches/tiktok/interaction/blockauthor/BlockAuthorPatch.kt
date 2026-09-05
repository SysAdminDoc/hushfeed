/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Annotation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/blockauthor/BlockAuthorPatch;"

private const val VIDEO_ITEM_PARAMS_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;"

/**
 * Retrofit path annotations used by TikTok's networking layer. The block endpoint is
 * declared on an interface method carrying one of these with a `user/block` path.
 */
private val RETROFIT_PATH_ANNOTATIONS = setOf(
    "Lcom/bytedance/retrofit2/http/GET;",
    "Lcom/bytedance/retrofit2/http/POST;",
    "Lretrofit2/http/GET;",
    "Lretrofit2/http/POST;",
)

/** Endpoint fragments that identify the block/unblock API across TikTok regions. */
private val BLOCK_ENDPOINT_FRAGMENTS = listOf(
    "user/block/",
    "user/block",
    "/block/",
)

private data class BlockApiCandidate(
    val serviceClass: String,
    val methodName: String,
    val parameterTypes: List<String>,
    val endpointPath: String,
) {
    /** Rank candidates so the most specific endpoint wins. */
    val score: Int
        get() = when {
            endpointPath.contains("user/block/") -> 3
            endpointPath.contains("user/block") -> 2
            else -> 1
        }
}

@Suppress("unused")
val blockAuthorPatch = bytecodePatch(
    name = "Block author button",
    description = "Adds a block button to the video player that blocks the account that posted the " +
        "current video in one tap, with an undo action. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableBlockAuthor()V",
        )

        // Track the author of whichever video is currently on screen.
        val trackerMethod = VideoAuthorInfoParamsFingerprint.method
        val paramsRegister = trackerMethod.registerOfParameter(VIDEO_ITEM_PARAMS_DESCRIPTOR)
            ?: error("Could not locate the VideoItemParams parameter on paramSync2StateAccept")

        trackerMethod.addInstruction(
            0,
            "invoke-static {$paramsRegister}, " +
                "$EXTENSION_CLASS_DESCRIPTOR->setCurrentVideoParams(Ljava/lang/Object;)V",
        )

        // Resolve the block endpoint structurally rather than by obfuscated name, so the
        // patch survives the name churn between TikTok builds.
        val candidates = mutableListOf<BlockApiCandidate>()

        classDefForEach { classDef ->
            if (!classDef.isInterface()) return@classDefForEach

            for (method in classDef.methods) {
                val endpointPath = method.retrofitEndpointPath() ?: continue
                if (BLOCK_ENDPOINT_FRAGMENTS.none { endpointPath.contains(it) }) continue

                candidates += BlockApiCandidate(
                    serviceClass = classDef.type,
                    methodName = method.name,
                    parameterTypes = method.parameterTypes.map { it.toString() },
                    endpointPath = endpointPath,
                )
            }
        }

        // Prefer the most specific endpoint, then the signature carrying the most
        // arguments, which is the one that accepts both the user id and the sec user id.
        val candidate = candidates
            .sortedWith(
                compareByDescending<BlockApiCandidate> { it.score }
                    .thenByDescending { it.parameterTypes.size },
            )
            .firstOrNull()

        if (candidate == null) {
            // Leave the descriptors empty. The extension falls back to its reflective
            // strategies and reports the failure in the log rather than crashing the app.
            println(
                "WARNING: Block author button could not locate the block endpoint. " +
                    "The button will install but blocking will fall back to reflection.",
            )
        } else {
            BlockApiServiceClassFingerprint.method.returnEarly(candidate.serviceClass)
            BlockApiMethodNameFingerprint.method.returnEarly(candidate.methodName)
            BlockApiParameterTypesFingerprint.method.returnEarly(
                candidate.parameterTypes.joinToString(","),
            )
            BlockApiEndpointPathFingerprint.method.returnEarly(candidate.endpointPath)
        }
    }
}

private fun ClassDef.isInterface() = accessFlags and AccessFlags.INTERFACE.value != 0

private fun Method.retrofitEndpointPath(): String? {
    for (annotation in annotations) {
        if (annotation.type !in RETROFIT_PATH_ANNOTATIONS) continue
        annotation.stringElement("value")?.let { return it }
    }
    return null
}

private fun Annotation.stringElement(name: String): String? = elements
    .firstOrNull { it.name == name }
    ?.let { (it.value as? StringEncodedValue)?.value }

/**
 * Resolves the smali register holding the parameter of [descriptor].
 *
 * Wide parameters occupy two registers, so the offset cannot be derived from the
 * parameter index alone.
 */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.registerOfParameter(
    descriptor: String,
): String? {
    var register = if (accessFlags and AccessFlags.STATIC.value != 0) 0 else 1

    for (parameterType in parameterTypes) {
        val type = parameterType.toString()
        if (type == descriptor) return "p$register"
        register += if (type == "J" || type == "D") 2 else 1
    }

    return null
}
