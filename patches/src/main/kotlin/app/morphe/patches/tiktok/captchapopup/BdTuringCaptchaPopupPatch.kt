/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows hxreborn/hxreborn-tiktok-patches (GPL-3.0).
 */
package app.morphe.patches.tiktok.captchapopup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.numberOfParameterRegisters

private const val FEATURE_CONTROLS_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;"

private object RiskControlServiceExecuteFingerprint : Fingerprint(
    definingClass = "Lcom/bytedance/bdturing/verify/RiskControlService;",
    name = "execute",
    returnType = "Z",
)

/**
 * The risk control dialog is a different path from the browsing CAPTCHA that
 * `Hide CAPTCHA popups` covers, so it needs its own hook. It answers the same setting and
 * refuses to touch the two service types that carry account security verification.
 *
 * Off by default on purpose: TikTok raises a real risk check here as well, and failing one
 * makes the action behind it (a follow, a like) fail with no explanation. Every suppression
 * is logged so the cause is findable.
 */
@Suppress("unused")
val bdTuringCaptchaPopupPatch = bytecodePatch(
    name = "Hide BdTuring CAPTCHA popups",
    description = "Hides TikTok's risk control CAPTCHA dialog, which the browsing CAPTCHA " +
        "patch does not cover. Answers the Hide CAPTCHA popups setting and never touches SMS " +
        "or two factor verification. A suppressed check can make the action behind it fail " +
        "silently, so this is off by default. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        RiskControlServiceExecuteFingerprint.method.apply {
            val requestType = parameterTypes[0]
            val callbackType = parameterTypes[1]

            // v0 to v2 are used as scratch. If the frame has fewer than three locals they
            // would be parameter registers instead, and writing v2 would destroy p2 (the
            // callback) before it is read. Fail the build rather than ship that.
            val localRegisters = implementation!!.registerCount - numberOfParameterRegisters
            if (localRegisters < 3) {
                throw PatchException(
                    "Hide BdTuring CAPTCHA popups: execute has only $localRegisters local " +
                        "registers, so v0 to v2 overlap its parameters.",
                )
            }

            addInstructions(
                0,
                """
                    move-object/from16 v2, p1
                    invoke-virtual {v2}, $requestType->getActivity()Landroid/app/Activity;
                    move-result-object v0
                    invoke-virtual {v2}, $requestType->getServiceType()Ljava/lang/String;
                    move-result-object v1
                    invoke-static {v0, v1}, $FEATURE_CONTROLS_CLASS_DESCRIPTOR->shouldHideTuringCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :morphe_show_bdturing_captcha_popup
                    const/4 v0, 0x3
                    move-object/from16 v1, p2
                    invoke-interface {v1, v0}, $callbackType->onFail(I)V
                    const/4 v0, 0x1
                    return v0
                    :morphe_show_bdturing_captcha_popup
                    nop
                """,
            )
        }
    }
}
