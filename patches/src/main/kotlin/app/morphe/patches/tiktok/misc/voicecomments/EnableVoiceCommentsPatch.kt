/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/0e4a6e1d
 */
package app.morphe.patches.tiktok.misc.voicecomments

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities

/**
 * A gate flip with no settings switch of its own, so selecting the patch is the switch. It ships
 * off by default because nobody here has seen what the entry points do on a real account: whether
 * a voice comment records, publishes and plays back is a device check.
 */
@Suppress("unused")
val enableVoiceCommentsPatch = bytecodePatch(
    name = "Enable voice comments",
    description = "Turns on TikTok's own voice comment recording and publishing entry points for " +
        "accounts that do not have them.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        VoiceCommentPublishGateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
