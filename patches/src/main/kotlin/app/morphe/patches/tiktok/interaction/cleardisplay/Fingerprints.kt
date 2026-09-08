/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/cleardisplay/Fingerprints.kt
 */
package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal object OnClearDisplayEventFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/ClearModePanelComponent;") && method.name == "onClearModeEvent"
    },
)

/** The extracted body of PlayerController.onRenderFirstFrame in TikTok 46.2.3. */
internal object OnRenderFirstFrameBodyFingerprint : Fingerprint(
    definingClass = "/feed/controller/PlayerController;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    parameters = listOf(
        "Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;",
        "LX/0pb0;",
    ),
    custom = { method, _ -> method.name == "LLILZIL" },
)

/**
 * The one clear mode event this can still find by signature alone.
 *
 * On 46.2.3 exactly one method out of 1,872,612 matches, and its class exists only to log clear
 * mode: every caller is an onClearModeEvent, an onPageSelected or an onPageUnSelected. The other
 * two events are found inside that class rather than by their own bare signature, because that is
 * what the core and playtime fingerprints did and both had stopped matching anything at all.
 */
internal object ClearModeLogStateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Lcom/bytedance/common/utility/collection/WeakHandler;",
        "Z",
        "Ljava/lang/String;",
        "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
        "J",
        "I",
        "I",
    ),
)

