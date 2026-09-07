/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/Fingerprints.kt
 */
package app.morphe.patches.tiktok.interaction.speed

import app.morphe.patcher.Fingerprint
internal object PlaybackSpeedSelectionBoundaryFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "F",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    strings = listOf(
        "swipe_up_lock_persist",
        "click_share_button",
        "long_press",
    ),
)

internal object PlayerControllerSetSpeedFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    name = "setSpeed",
    returnType = "V",
    parameters = listOf("F"),
)

internal object PlaybackSpeedMenuFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/ss/android/ugc/aweme/share/base/model/BaseSharePackage;"),
    strings = listOf("is_highlight_fast_speed"),
)

internal object LongPressSpeedUpEnableFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("long_press_speed_up_enable"),
)

internal object LongPressSpeedUpLockFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    strings = listOf("long_press_speed_up_lock"),
)
