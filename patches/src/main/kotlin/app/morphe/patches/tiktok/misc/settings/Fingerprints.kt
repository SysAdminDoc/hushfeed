/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/settings/Fingerprints.kt
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.Fingerprint


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



internal object SettingsStatusLoadFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("Lapp/morphe/extension/tiktok/settings/SettingsStatus;") && method.name == "load"
    },
)

/**
 * The crash reporter's initialiser, which is handed the application context. The context is
 * what the injection passes on, so its position is part of the shape: first, and the only
 * parameter, or the register arithmetic in the patch names something else.
 */
internal object NpthExtentTaskInitFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    custom = { method, classDef ->
        classDef.endsWith("/NpthExtentTask;") &&
            method.name == "LIZ" &&
            method.parameterTypes.size == 1
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
