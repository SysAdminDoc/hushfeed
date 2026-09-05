/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
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
