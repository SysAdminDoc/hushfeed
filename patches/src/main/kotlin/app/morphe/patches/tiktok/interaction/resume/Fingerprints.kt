/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.resume

import app.morphe.patcher.Fingerprint

internal object FeedProgressContinueGateFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == "LX/0Lyr;" &&
            method.name == "invoke"
    },
)

internal object FeedPlayCompletedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;" &&
            method.name == "onPlayCompleted"
    },
)

internal object FeedPlayProgressFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "J", "J"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;" &&
            method.name == "onPlayProgressChange"
    },
)

// The method that answers "where should this video start", read out of the 46.2.3 fixture. The
// signature alone is unique across the app's 1,872,612 methods; the string is one of three
// landscape tags it compares and is here so a build that renames the method still fails loudly
// rather than binding to something else.
internal object FeedProgressResumePositionFingerprint : Fingerprint(
    returnType = "J",
    parameters = listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;", "Ljava/lang/String;"),
    strings = listOf("landscape_change_keep_tag"),
)
