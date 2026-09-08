/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/inbox/NotificationControls;"

/**
 * The one method on the push handler that hands a built notification to Android. The class
 * kept its name; the method did not, so it is found by the call it makes. Nothing else on
 * MessageShowHandler calls notify, and its parameters are checked below before the message
 * is read out of p1.
 */
private object PushNotifyFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/awemepushlib/manager/MessageShowHandler;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "V",
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/app/NotificationManager;" &&
                    reference.name == "notify"
            } == true
        } == true
    },
)

/**
 * Whether a conversation offers the streak button, and whether it carries the streak
 * reminder message. Both names survived on several copies of the same Kotlin model, so
 * every one of them is taken.
 */
private object ShowStreakButtonFingerprint : Fingerprint(
    name = "getShowStreakButton",
    returnType = "Z",
    parameters = emptyList(),
)

private object StreakReminderFingerprint : Fingerprint(
    name = "getHasStreakReminderInlineMsg",
    returnType = "Z",
    parameters = emptyList(),
)

@Suppress("unused")
val notificationControlsPatch = bytecodePatch(
    name = "Notification controls",
    description = "Adds a switch for the notification saying somebody new followed you, and " +
        "one for message streaks, neither of which TikTok lets you turn off. The follower " +
        "switch drops the notification before Android is asked to post it, so nothing else " +
        "in the drawer is affected. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        PushNotifyFingerprint.method.apply {
            // The method is static, so p1 is the second parameter. That is the push message,
            // whose own type is obfuscated and cannot be named here, so the parameters around
            // it stand in for its name: a Context first and the built Notification fifth. p1
            // itself only has to be an object for the call to pass it as one.
            check(
                parameterTypes.size >= 5 &&
                    parameterTypes[0] == "Landroid/content/Context;" &&
                    parameterTypes[1].startsWith("L") &&
                    parameterTypes[4] == "Landroid/app/Notification;",
            ) {
                "Notification controls: the push handler no longer takes the message in p1."
            }
            check(implementation!!.registerCount > parameterTypes.size) {
                "Notification controls: the push handler has no free local register."
            }
            addInstructions(
                0,
                """
                    invoke-static/range { p1 .. p1 }, $EXTENSION->shouldDropPush(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :morphe_post_notification
                    return-void
                    :morphe_post_notification
                    nop
                """,
            )
        }

        for (fingerprint in listOf(ShowStreakButtonFingerprint, StreakReminderFingerprint)) {
            val matches = fingerprint.matchAll().filter { it.method.implementation != null }
            check(matches.isNotEmpty()) {
                "Notification controls: no ${fingerprint.name} to take over."
            }
            matches.forEach { match ->
                // A Kotlin getter this small can be compiled with only its own receiver, and
                // then v0 is that receiver rather than a spare.
                check(match.method.implementation!!.registerCount > 1) {
                    "Notification controls: ${fingerprint.name} has no free local register."
                }
                match.method.addInstructions(
                    0,
                    """
                        invoke-static {}, $EXTENSION->hideMessageStreaks()Z
                        move-result v0
                        if-eqz v0, :morphe_keep_streaks
                        const/4 v0, 0x0
                        return v0
                        :morphe_keep_streaks
                        nop
                    """,
                )
            }
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableNotificationControls()V",
        )
    }
}
