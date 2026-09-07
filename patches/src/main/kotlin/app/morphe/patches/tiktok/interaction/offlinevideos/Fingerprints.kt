package app.morphe.patches.tiktok.interaction.offlinevideos

import app.morphe.patcher.Fingerprint

internal object OfflineModeSheetOptionsFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.endsWith("/OfflineModeSheetPageAssem;") &&
            method.name == "<clinit>" &&
            method.parameterTypes.isEmpty()
    },
)

internal object OfflineModeOptionConfigFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "LX/0sIr;" &&
            method.name == "<clinit>" &&
            method.parameterTypes.isEmpty()
    },
)

internal object OfflineModeOptionEnumFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == "LX/0mE9;" &&
            method.name == "<clinit>" &&
            method.parameterTypes.isEmpty()
    },
)
