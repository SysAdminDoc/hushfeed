/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/telemetry/Fingerprints.kt
 */
package app.morphe.patches.tiktok.misc.telemetry

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val APP_LOG_DESCRIPTOR = "Lcom/bytedance/applog/AppLog;"
private const val APPS_FLYER_LIB_DESCRIPTOR = "Lcom/appsflyer/AppsFlyerLib;"
private const val BD_LOCATION_CONFIG_DESCRIPTOR = "Lcom/bytedance/bdlocation/client/BDLocationConfig;"
private const val FIREBASE_ANALYTICS_DESCRIPTOR = "Lcom/google/firebase/analytics/FirebaseAnalytics;"
private const val MONITOR_CRASH_DESCRIPTOR = "Lcom/bytedance/crash/MonitorCrash;"

private val publicStaticAccessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC)

internal object AppLogOnEventFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onEvent",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

internal object AppLogOnEventV3StringFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onEventV3",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

internal object AppLogOnEventV3JsonFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onEventV3",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lorg/json/JSONObject;"),
)

internal object AppLogOnEventV3BundleFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onEventV3",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Landroid/os/Bundle;"),
)

internal object AppLogOnMiscEventFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onMiscEvent",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lorg/json/JSONObject;"),
)

internal object AppLogFlushFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "flush",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = emptyList(),
)

internal object AppLogFlushAsyncFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "flushAsync",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = emptyList(),
)

internal object AppLogOnActivityPauseFingerprint : Fingerprint(
    definingClass = APP_LOG_DESCRIPTOR,
    name = "onActivityPause",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = emptyList(),
)

internal object InitAppsFlyerClassFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("XY8Lpakui8g4kBcposRgxA"),
)

internal object InitAppsFlyerRunFingerprint : Fingerprint(
    classFingerprint = InitAppsFlyerClassFingerprint,
    name = "run",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
)

internal object AppsFlyerGetInstanceFingerprint : Fingerprint(
    definingClass = APPS_FLYER_LIB_DESCRIPTOR,
    name = "getInstance",
    accessFlags = publicStaticAccessFlags,
    returnType = APPS_FLYER_LIB_DESCRIPTOR,
    parameters = emptyList(),
)

internal object AppsFlyerLogEventFingerprint : Fingerprint(
    definingClass = APPS_FLYER_LIB_DESCRIPTOR,
    name = "logEvent",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
    ),
)

internal object AppsFlyerLogLocationFingerprint : Fingerprint(
    definingClass = APPS_FLYER_LIB_DESCRIPTOR,
    name = "logLocation",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "D", "D"),
)

internal object BDLocationSetUploadFingerprint : Fingerprint(
    definingClass = BD_LOCATION_CONFIG_DESCRIPTOR,
    name = "setUpload",
    accessFlags = publicStaticAccessFlags,
    returnType = "V",
    parameters = listOf("Z"),
)

internal object BDLocationIsUploadFingerprint : Fingerprint(
    definingClass = BD_LOCATION_CONFIG_DESCRIPTOR,
    name = "isUpload",
    accessFlags = publicStaticAccessFlags,
    returnType = "Z",
    parameters = emptyList(),
)

internal object BDLocationIsUploadGPSFingerprint : Fingerprint(
    definingClass = BD_LOCATION_CONFIG_DESCRIPTOR,
    name = "isUploadGPS",
    accessFlags = publicStaticAccessFlags,
    returnType = "Z",
    parameters = emptyList(),
)

internal object BDLocationIsUploadLocationFingerprint : Fingerprint(
    definingClass = BD_LOCATION_CONFIG_DESCRIPTOR,
    name = "isUploadLocation",
    accessFlags = publicStaticAccessFlags,
    returnType = "Z",
    parameters = emptyList(),
)

internal object FirebaseSetCurrentScreenFingerprint : Fingerprint(
    definingClass = FIREBASE_ANALYTICS_DESCRIPTOR,
    name = "setCurrentScreen",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(
        "Landroid/app/Activity;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
)

internal object MonitorCrashReportCustomErrFingerprint : Fingerprint(
    definingClass = MONITOR_CRASH_DESCRIPTOR,
    name = "reportCustomErr",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/Throwable;"),
)

internal object MonitorCrashReportEventFingerprint : Fingerprint(
    definingClass = MONITOR_CRASH_DESCRIPTOR,
    name = "reportEvent",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "I",
        "Lorg/json/JSONObject;",
        "Lorg/json/JSONObject;",
    ),
)
