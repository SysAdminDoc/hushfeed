/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Fires whenever a video becomes the current item in the feed, carrying the
 * [VideoItemParams] that holds the Aweme and therefore the author.
 *
 * This is the same anchor the "Always show publish date" patch uses, so it is
 * known to resolve on TikTok 46.2.3.
 */
internal object VideoAuthorInfoParamsFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/assem/videoauthorinfo/VideoAuthorInfoVM;",
    custom = { method, _ ->
        method.name == "paramSync2StateAccept" &&
            "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;" in method.parameterTypes
    },
)

/** Extension accessors that the patch rewrites with the discovered block API descriptors. */
internal object BlockApiServiceClassFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/tiktok/blockauthor/BlockApiDescriptors;",
    name = "patchedServiceClassName",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

internal object BlockApiMethodNameFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/tiktok/blockauthor/BlockApiDescriptors;",
    name = "patchedMethodName",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

internal object BlockApiParameterTypesFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/tiktok/blockauthor/BlockApiDescriptors;",
    name = "patchedParameterTypes",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)

internal object BlockApiEndpointPathFingerprint : Fingerprint(
    definingClass = "Lapp/morphe/extension/tiktok/blockauthor/BlockApiDescriptors;",
    name = "patchedEndpointPath",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
)
