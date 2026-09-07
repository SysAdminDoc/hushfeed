/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/shared/Fingerprints.kt
 */
package app.morphe.patches.tiktok.shared

import app.morphe.patcher.Fingerprint
internal object OnRenderFirstFrameFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    name = "onRenderFirstFrame",
    returnType = "V",
    parameters = listOf("LX/0pb0;"),
)
