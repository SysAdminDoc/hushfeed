/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/e1fb74c7
 */
package app.morphe.patches.tiktok.misc.commentsort

import app.morphe.patcher.Fingerprint

/**
 * The rollout gate. Anchored on the string rather than on the obfuscated lambda class upstream
 * names, because that name changes every build; the string does not. Parameter and return types
 * match by prefix, so "L" is any object.
 */
internal object CommentSortOptionStyleFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L"),
    strings = listOf("comment_sort_opt_style"),
)

/**
 * The per-post eligibility check. This one carries no string of its own, so there is nothing to
 * anchor on but the obfuscated owner and the Aweme parameter. It is the anchor most likely to
 * move on a new build, and the patch fails loudly rather than quietly if it does.
 */
internal object CommentSortEligibilityFingerprint : Fingerprint(
    definingClass = "LX/0nmj;",
    name = "LIZ",
    returnType = "Z",
    parameters = listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;"),
)
