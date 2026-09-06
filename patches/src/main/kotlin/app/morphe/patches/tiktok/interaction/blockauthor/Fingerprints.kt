/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.Fingerprint

/**
 * Fires whenever a video becomes the current item in the feed, carrying the
 * `VideoItemParams` that holds the Aweme and therefore the author.
 *
 * This is the same anchor the "Always show publish date" patch uses, so it is known to
 * resolve on TikTok 46.2.3.
 */
internal object VideoAuthorInfoParamsFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/assem/videoauthorinfo/VideoAuthorInfoVM;",
    custom = { method, _ ->
        method.name == "paramSync2StateAccept" &&
            "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;" in method.parameterTypes
    },
)

/**
 * TikTok's block endpoint, verified against TikTok 46.2.3:
 *
 * ```
 * @GET("/aweme/v1/user/block/")
 * Call<BlockStruct> block(@Query("user_id") String, @Query("sec_user_id") String,
 *                         @Query("block_type") int, @Query("source") int)
 * ```
 *
 * The extension calls this by reflection at runtime. The fingerprint exists so the patch
 * fails loudly at build time if a future TikTok build renames or reshapes it, rather than
 * installing a button that silently does nothing.
 */
internal object BlockServiceFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/profile/api/BlockApi\$BlockService;",
    name = "block",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "I", "I"),
)

/**
 * Reports playback progress for the video that is actually on screen, carrying its id in
 * the first parameter. The bind callback above fires for items the feed has prefetched, so
 * this is what decides which of them is current.
 *
 * The same method is hooked by "Hide already seen videos"; both injections are prepends
 * and do not interfere.
 */
internal object PlayerProgressAidFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "J", "J"),
    custom = { method, classDef ->
        method.name == "onPlayProgressChange" && classDef.endsWith("/PlayerController;")
    },
)
