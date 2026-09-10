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

/**
 * The download limit choices, which are an enum. Its class was written here as `LX/0mE9;` and is
 * `LX/14Jp;` on 46.7.3 and `LX/189v;` on 46.8.3; the constants it declares are the same eight on
 * all three and no other enum in the app declares them.
 */
private val OFFLINE_OPTION_CONSTANTS = listOf(
    "DOWNLOAD_50_VIDEOS",
    "DOWNLOAD_60_VIDEOS",
    "DOWNLOAD_100_VIDEOS",
    "DOWNLOAD_120_VIDEOS",
    "DOWNLOAD_150_VIDEOS",
    "DOWNLOAD_200_VIDEOS",
    "DOWNLOAD_240_VIDEOS",
    "DOWNLOAD_480_VIDEOS",
)

internal object OfflineModeOptionEnumFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, classDef ->
        method.name == "<clinit>" &&
            method.parameterTypes.isEmpty() &&
            classDef.superclass == "Ljava/lang/Enum;" &&
            classDef.fields.mapTo(HashSet()) { it.name }.containsAll(OFFLINE_OPTION_CONSTANTS)
    },
)
