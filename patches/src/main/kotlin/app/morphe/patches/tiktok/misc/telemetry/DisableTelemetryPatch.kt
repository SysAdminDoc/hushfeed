/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/telemetry/DisableTelemetryPatch.kt
 * Npth task coverage adapted from kveld9/kveld-morphe-patches at
 * fcb1768620b8f98a6dd31e801074589ce9a63356 (GPL-3.0):
 * https://github.com/kveld9/kveld-morphe-patches/tree/fcb1768620b8f98a6dd31e801074589ce9a63356
 */
package app.morphe.patches.tiktok.misc.telemetry

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.cloneMutable
import app.morphe.util.cloneMutableAndPreserveParameters
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.numberOfParameterRegistersLogical
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/telemetry/DisableTelemetryPatch;"
private const val BD_LOCATION_CONFIG_DESCRIPTOR = "Lcom/bytedance/bdlocation/client/BDLocationConfig;"

context(patchContext: BytecodePatchContext)
private fun Method.returnEarlyIfTelemetryDisabled(disabledInstructions: (register: Int) -> String) {
    val scratchRegister = implementation?.registerCount
        ?: throw PatchException("Cannot guard a method without an implementation: $this")
    val guardIndex = numberOfParameterRegistersLogical

    val guardedMethod = if (numberOfParameterRegisters == 0) {
        cloneMutable(additionalRegisters = 1).also { clonedMethod ->
            patchContext.mutableClassDefBy(definingClass).methods.apply {
                remove(this@returnEarlyIfTelemetryDisabled)
                add(clonedMethod)
            }
        }
    } else {
        // Keep the original parameters intact when the guard uses a shifted parameter register
        with(patchContext) { cloneMutableAndPreserveParameters() }
    }

    guardedMethod.addInstructionsWithLabels(
        guardIndex,
        """
            invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->isTelemetryDisabled()Z
            move-result v$scratchRegister
            if-eqz v$scratchRegister, :morphe_telemetry_enabled
            ${disabledInstructions(scratchRegister)}
        """,
        ExternalLabel(
            "morphe_telemetry_enabled",
            guardedMethod.getInstruction(guardIndex),
        ),
    )
}

@Suppress("unused")
val disableTelemetryPatch = bytecodePatch(
    name = "Disable telemetry",
    description = "Adds a switch on the Privacy page that stops ByteDance AppLog analytics, AppsFlyer " +
        "attribution, explicit Firebase screen reports and TikTok's Npth or MonitorCrash startup " +
        "reporting. TikTok's own diagnostics go quiet with them. Off by default. Switch: Hushfeed settings > Privacy.",
    default = false,
) {
    category("Privacy")
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDisableTelemetry()V",
        )

        listOf(
            AppLogOnEventFingerprint,
            AppLogOnEventV3StringFingerprint,
            AppLogOnEventV3JsonFingerprint,
            AppLogOnEventV3BundleFingerprint,
            AppLogOnMiscEventFingerprint,
            AppLogFlushFingerprint,
            AppLogFlushAsyncFingerprint,
            AppLogOnActivityPauseFingerprint,
        ).forEach { fingerprint ->
            fingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }
        }

        InitAppsFlyerRunFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }

        // AppsFlyer declares its API methods as abstract, so patch the singleton's concrete overrides.
        val appsFlyerImplementationClass = AppsFlyerGetInstanceFingerprint.method.implementation
            ?.instructions
            ?.firstNotNullOfOrNull { instruction -> instruction.getReference<MethodReference>() }
            ?.definingClass
            ?: throw PatchException("Could not resolve the AppsFlyer implementation class")
        val appsFlyerClass = mutableClassDefBy(appsFlyerImplementationClass)

        listOf(
            AppsFlyerLogEventFingerprint,
            AppsFlyerLogLocationFingerprint,
        ).forEach { fingerprint ->
            appsFlyerClass.findMutableMethodOf(fingerprint.originalMethod)
                .returnEarlyIfTelemetryDisabled { "return-void" }
        }

        // This SDK is not bundled in the global APK, but some package variants may include it.
        BDLocationSetUploadFingerprint.methodOrNull?.returnEarlyIfTelemetryDisabled { register ->
            """
                const/16 v$register, 0x0
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sIsUpload:Z
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sIsUploadGPS:Z
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sIsUploadLocation:Z
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sIsUploadBaseSite:Z
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sIsUploadWIFI:Z
                sput-boolean v$register, $BD_LOCATION_CONFIG_DESCRIPTOR->sUploadMccAndSystemRegionInfo:Z
                return-void
            """
        }

        listOf(
            BDLocationIsUploadFingerprint,
            BDLocationIsUploadGPSFingerprint,
            BDLocationIsUploadLocationFingerprint,
        ).forEach { fingerprint ->
            fingerprint.methodOrNull?.returnEarlyIfTelemetryDisabled { register ->
                """
                    const/16 v$register, 0x0
                    return v$register
                """
            }
        }

        FirebaseSetCurrentScreenFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }
        MonitorCrashReportCustomErrFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }
        MonitorCrashReportEventFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }
        NpthCoreInitTaskFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }
        NpthSecondInitTaskFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }

        // The AppLog packs themselves. With every entry point above guarded, the SDK still sent its
        // packs to the log hosts (10 requests, 134 KB over 20 videos on the S22 with the switch on):
        // the session's launch and terminate events, and events fed through entry points the
        // facade does not route. The send answers 200 without a request, so the worker takes the
        // pack as delivered and clears it, rather than keeping it to retry or upload later.
        AppLogSendPackFingerprint.method.returnEarlyIfTelemetryDisabled { register ->
            """
                const/16 v$register, 0xc8
                return v$register
            """
        }

        // The forward mirrors. The forward worker deletes its rows and then posts them through
        // the SDK's network client rather than the pack send, so a pair of forward requests (one
        // to the forward path, one to its backup, 26 KB together on the S22) outlived the guard
        // above. Returning before the post leaves nothing behind: the rows are already gone.
        AppLogForwardSendFingerprint.method.returnEarlyIfTelemetryDisabled { "return-void" }

        // The install SDK's activation check: a fetch of the log host's app_alert_check path once
        // a start, with the advertising id, carrier, SIM region and time zone in the query. The
        // job only reads the reply for the word success, so the helper answers success without
        // the fetch and the job marks itself done as it would have. Device registration is a
        // different job and is not touched.
        InstallActiveCheckFingerprint.method.returnEarlyIfTelemetryDisabled { register ->
            """
                const/4 v$register, 0x1
                return v$register
            """
        }
    }
}
