/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/settings/Fingerprints.kt
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.Fingerprint

internal object AddSettingsEntryFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/SettingNewVersionFragment;") && method.name == "initUnitManger"
    },
)

internal object AdPersonalizationActivityOnCreateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/AdPersonalizationActivity;") && method.name == "onCreate"
    },
)

internal object AdPersonalizationActivityOnBackPressedFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/AdPersonalizationActivity;") &&
            method.name == "onBackPressed" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    },
)

internal object SettingsEntryFingerprint : Fingerprint(
    strings = listOf("pls pass item or extends the EventUnit"),
)

internal object SettingsEntryInfoFingerprint : Fingerprint(
    strings = listOf(
        "ExposeItem(title=",
        ", icon=",
    ),
)

internal object SettingsStatusLoadFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("Lapp/morphe/extension/tiktok/settings/SettingsStatus;") && method.name == "load"
    },
)

internal object NpthExtentTaskInitFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/NpthExtentTask;") &&
            method.name == "LIZ" &&
            method.returnType == "V"
    },
)

internal object SettingsComposeRowsFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/SettingsComposeRvmpFragment;") &&
            method.returnType == "V" &&
            (
                (method.name == "XN" && method.parameterTypes.size == 8) ||
                    (method.name == "ER" && method.parameterTypes.size == 11)
            )
    },
)

internal object SupportGroupDefaultStateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/SupportGroupVM;") && method.name == "defaultState"
    },
)

internal object OpenDebugCellVmDefaultStateFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/OpenDebugCellVM;") && method.name == "defaultState"
    },
)
