/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/inbox/InboxFilter;"

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/main/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

@Suppress("unused")
val inboxFilterPatch = bytecodePatch(
    name = "Hide inbox items",
    description = "Adds a switch for each row and header control on the Inbox tab, so " +
        "message requests, TikTok Tako, TikTok Shop, the stories tray and the rest can be " +
        "hidden individually. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableInboxFilter()V",
        )

        // p0 is the activity. Uses invoke-static/range because a parameter register is
        // usually above v15, which the plain invoke-static cannot encode.
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Activity;)V",
        )
    }
}
