/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/telemetry/DisableTelemetryPatch.kt
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

context(BytecodePatchContext)
private fun Method.returnEarlyIfTelemetryDisabled(disabledInstructions: (register: Int) -> String) {
    val scratchRegister = implementation?.registerCount
        ?: throw PatchException("Cannot guard a method without an implementation: $this")
    val guardIndex = numberOfParameterRegistersLogical

    val guardedMethod = if (numberOfParameterRegisters == 0) {
        cloneMutable(additionalRegisters = 1).also { clonedMethod ->
            mutableClassDefBy(definingClass).methods.apply {
                remove(this@returnEarlyIfTelemetryDisabled)
                add(clonedMethod)
            }
        }
    } else {
        // Keep the original parameters intact when the guard uses a shifted parameter register
        cloneMutableAndPreserveParameters()
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
    description = "Adds an App behavior toggle that stops ByteDance AppLog analytics, AppsFlyer " +
        "attribution, BDLocation uploads, Firebase Analytics and crash reporting. TikTok's own " +
        "diagnostics go quiet with them. Off by default. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4623())

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
    }
}
