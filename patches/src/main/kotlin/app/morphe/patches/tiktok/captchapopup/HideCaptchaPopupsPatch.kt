/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.captchapopup

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private object CaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptchaV2",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "LX/13fZ;",
        "Landroidx/fragment/app/Fragment;",
    ),
    strings = listOf("popCaptchaV2 - riskInfo ="),
)

private object LegacyCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/sec/SecApiImpl;",
    name = "popCaptcha",
    returnType = "V",
    parameters = listOf("Landroid/app/Activity;", "I", "LX/13fZ;"),
    strings = listOf("popCaptcha - errorcode = "),
)

private object OecCaptchaPopupFingerprint : Fingerprint(
    definingClass = "Lcom/tts/oecverify/verify/RiskControlService;",
    name = "execute",
    returnType = "Z",
    parameters = listOf("LX/13eU;", "Lcom/tts/oecverify/BdTuringCallback;"),
)

private object LiveHostCaptchaPopupFingerprint : Fingerprint(
    definingClass = "/live/livehostimpl/LiveHostUser;",
    name = "popCaptchaV2",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "LX/1Cc3;",
        "Landroidx/fragment/app/Fragment;",
    ),
)

private object BdTuringCaptchaPopupFingerprint : Fingerprint(
    definingClass = "Lcom/tts/oecverify/BdTuring;",
    name = "showVerifyDialog",
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "LX/13eU;",
        "Lcom/tts/oecverify/BdTuringCallback;",
    ),
)

@Suppress("unused")
val hideCaptchaPopupsPatch = bytecodePatch(
    name = "Hide CAPTCHA popups",
    description = "Adds a default-off setting to hide browsing and LIVE puzzle dialogs. Login and " +
        "account verification stay visible, and so does any puzzle the server raised over a follow, " +
        "like, comment or repost, because hiding one of those makes the action fail with no message.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, captchaRequestRecorderPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCaptchaPopupSuppression()V",
        )

        // Every invoke below is /range: the parameter registers of a large native method can
        // sit above v15, which format 35c cannot encode.
        CaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p2}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_captcha_popup
                if-eqz p3, :morphe_hide_captcha_popup_return
                invoke-virtual/range {p3 .. p3}, LX/13fZ;->LIZJ()V
                :morphe_hide_captcha_popup_return
                return-void
                :morphe_show_captcha_popup
                nop
            """,
        )

        LegacyCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p2}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideLegacyCaptchaPopup(Landroid/app/Activity;I)Z
                move-result v0
                if-eqz v0, :morphe_show_legacy_captcha_popup
                if-eqz p3, :morphe_hide_legacy_captcha_popup_return
                invoke-virtual/range {p3 .. p3}, LX/13fZ;->LIZJ()V
                :morphe_hide_legacy_captcha_popup_return
                return-void
                :morphe_show_legacy_captcha_popup
                nop
            """,
        )

        OecCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p1}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideOecCaptchaPopup(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :morphe_show_oec_captcha_popup
                const/4 v0, 0x3
                const/4 v1, 0x0
                move-object/from16 v2, p2
                invoke-interface {v2, v0, v1}, Lcom/tts/oecverify/BdTuringCallback;->onFail(ILorg/json/JSONObject;)V
                const/4 v0, 0x1
                return v0
                :morphe_show_oec_captcha_popup
                nop
            """,
        )

        LiveHostCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p2}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideCaptchaPopup(Landroid/app/Activity;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :morphe_show_live_captcha_popup
                if-eqz p3, :morphe_hide_live_captcha_popup_return
                invoke-interface/range {p3 .. p3}, LX/1Cc3;->LIZIZ()V
                :morphe_hide_live_captcha_popup_return
                return-void
                :morphe_show_live_captcha_popup
                nop
            """,
        )

        // Network verification can present Turing directly without passing through SecApiImpl.
        BdTuringCaptchaPopupFingerprint.method.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p2}, $CAPTCHA_GATE_CLASS_DESCRIPTOR->shouldHideTuringDialog(Landroid/app/Activity;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :morphe_show_turing_captcha_popup
                if-eqz p3, :morphe_hide_turing_captcha_popup_return
                const/4 v0, 0x3
                const/4 v1, 0x0
                invoke-interface {p3, v0, v1}, Lcom/tts/oecverify/BdTuringCallback;->onFail(ILorg/json/JSONObject;)V
                :morphe_hide_turing_captcha_popup_return
                return-void
                :morphe_show_turing_captcha_popup
                nop
            """,
        )
    }
}
