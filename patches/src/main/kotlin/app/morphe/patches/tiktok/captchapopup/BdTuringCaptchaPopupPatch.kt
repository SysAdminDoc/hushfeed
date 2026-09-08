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
 * Off by default on purpose: TikTok raises a real risk check here as well. The gate never
 * hides one that arrived over a write the user just asked for, and logs every suppression,
 * but a check this service raises for some other reason still has consequences.
 */
@Suppress("unused")
val bdTuringCaptchaPopupPatch = bytecodePatch(
    name = "Hide BdTuring CAPTCHA popups",
    description = "Hides TikTok's risk control CAPTCHA dialog, which the browsing CAPTCHA " +
        "patch does not cover. Answers the Hide CAPTCHA popups setting, never touches SMS or " +
        "two factor verification, and never hides a check the server raised over a follow, " +
        "like, comment or repost. Off by default. Supports TikTok 46.2.3.",
    default = false,
) {
    // The recorder is what lets the gate tell a browsing puzzle from one raised over a write.
    // Selecting this patch without it would hide both.
    // It answers the Hide CAPTCHA popups setting, whose switch only exists when that
    // patch is selected. Selected on its own it would install a hook reading a setting
    // with nowhere to turn it on, so it brings the switch with it.
    dependsOn(sharedExtensionPatch, captchaRequestRecorderPatch, hideCaptchaPopupsPatch)
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
                    invoke-static {v0, v1}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideTuringCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
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
