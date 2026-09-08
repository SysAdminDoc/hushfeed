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
 *
 * <p>This is the looser of the two anchors and so the one that could bind quietly: the patcher
 * takes the first match and does not complain about a second. In 46.2.3 exactly two methods carry
 * the string and only one has this shape, so there is nothing to pick between; on a build with
 * more, this would bind to whichever came first rather than fail. The eligibility anchor below is
 * the opposite: pinned by name, so it fails loudly instead.
 */
internal object CommentSortOptionStyleFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L"),
    strings = listOf("comment_sort_opt_style"),
)

/**
 * The per-post eligibility check. This one carries no string of its own, so there is nothing to
 * anchor on but the obfuscated owner and the Aweme parameter. It is the anchor most likely to
 * move on a new build; because it is pinned by name it fails the patch rather than binding to
 * something else.
 */
internal object CommentSortEligibilityFingerprint : Fingerprint(
    definingClass = "LX/0nmj;",
    name = "LIZ",
    returnType = "Z",
    parameters = listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;"),
)
