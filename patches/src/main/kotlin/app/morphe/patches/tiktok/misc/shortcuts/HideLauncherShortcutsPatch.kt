/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.shortcuts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/misc/LauncherShortcuts;"
private const val SHORTCUT_MANAGER = "Landroid/content/pm/ShortcutManager;"
private const val SHORTCUT_SERVICE =
    "Lcom/ss/android/ugc/aweme/launcher/service/shortcut/IShortcutService;"
private const val SERVICE_MANAGER =
    "Lcom/ss/android/ugc/aweme/framework/services/ServiceManager;"

/**
 * The two platform calls that put a list of shortcuts in front of the launcher.
 *
 * <p>`updateShortcuts` is deliberately not here. It changes entries that are already published
 * and adds none, so with the published list empty there is nothing for it to change. Neither is
 * `requestPinShortcut`, which is somebody choosing to put a shortcut on their own home screen.
 */
private val PUBLISHERS = setOf("setDynamicShortcuts", "addDynamicShortcuts")

private fun publishesShortcuts(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return false
    val reference = instruction.getReference<MethodReference>() ?: return false
    return reference.definingClass == SHORTCUT_MANAGER &&
        reference.name in PUBLISHERS &&
        reference.parameterTypes.singleOrNull() == "Ljava/util/List;"
}

/**
 * Puts the list about to be published in front of the extension and publishes its answer.
 *
 * <p>`registerC` of the call is the `ShortcutManager` it is made on and `registerD` is its only
 * argument, the list. It is the argument that goes through the extension: routing the receiver
 * would hand the extension the manager and publish a manager. The answer goes back into that same
 * register, so the call itself is untouched and whatever the method does with its result still
 * works.
 */
internal fun MutableMethod.routeShortcutListThroughExtension(index: Int) {
    val list = (getInstruction(index) as Instruction35c).registerD
    addInstructions(
        index,
        """
            invoke-static/range {v$list .. v$list}, $EXTENSION->publish(Ljava/util/List;)Ljava/util/List;
            move-result-object v$list
        """,
    )
}

/**
 * Anything handing the platform a list of shortcuts. The method is Android's, so it is named in
 * full whatever TikTok called the code around it, and taking every one of them means the switch
 * does not depend on which of them the app happens to use. On 46.2.3 the one that publishes the
 * long-press menu is the `setDynamicShortcuts` inside `TiktokShortcutManager`'s refresh, and the
 * `addDynamicShortcuts` is the fallback inside the support library's own push.
 */
private object ShortcutPublishFingerprint : Fingerprint(
    custom = { method, _ ->
        method.implementation?.instructions?.any(::publishesShortcuts) == true
    },
)

@Suppress("unused")
val hideLauncherShortcutsPatch = bytecodePatch(
    name = "Hide the launcher shortcuts",
    description = "Empties the menu that opens on pressing and holding TikTok's icon on the " +
        "home screen. The entries are built while the app runs rather than declared in it, and " +
        "TikTok only rewrites them when it notices a difference, so this takes away what is " +
        "already published and answers the handover that would publish more. Turning it off " +
        "asks TikTok to build them again. Tapping the icon still opens the app, and a shortcut " +
        "pinned to a home screen is left alone.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        var patched = 0
        ShortcutPublishFingerprint.matchAll().forEach { match ->
            val method = match.method
            val implementation = method.implementation ?: return@forEach

            // Later calls first, because inserting ahead of one moves every index after it.
            val calls = implementation.instructions.withIndex()
                .filter { publishesShortcuts(it.value) }
                .map { it.index }
                .toList()

            calls.asReversed().forEach { index ->
                method.routeShortcutListThroughExtension(index)
                patched++
            }
        }

        if (patched == 0) {
            throw PatchException("Hide the launcher shortcuts: nothing publishes any.")
        }

        // The service keeps its name; its methods do not. The one wanted is the only one on it
        // taking a scene and a flag, so it is chosen by that shape rather than by a name that
        // means nothing here and would be something else in the next build. More than one of that
        // shape means the shape has stopped identifying it, which is a failure rather than a
        // coin toss between them.
        val service = mutableClassDefBy(SHORTCUT_SERVICE)
        val candidates = service.methods.filter {
            it.returnType == "V" && it.parameterTypes.toList() == listOf("Ljava/lang/String;", "Z")
        }
        if (candidates.size != 1) {
            throw PatchException(
                "Hide the launcher shortcuts: expected one rebuild taking a scene and a flag on " +
                    "$SHORTCUT_SERVICE, found ${candidates.size}.",
            )
        }
        val rebuild = candidates.single()

        val extension = mutableClassDefBy(EXTENSION)
        val stub = extension.methods.single { it.name == "askHostToRebuild" }
        val filled = stub.cloneMutable(additionalRegisters = 3)
        extension.methods.remove(stub)
        extension.methods.add(filled)
        filled.addInstructions(
            0,
            """
                invoke-static {}, $SERVICE_MANAGER->get()$SERVICE_MANAGER
                move-result-object v0
                const-class v1, $SHORTCUT_SERVICE
                invoke-virtual {v0, v1}, $SERVICE_MANAGER->getService(Ljava/lang/Class;)Ljava/lang/Object;
                move-result-object v0
                check-cast v0, $SHORTCUT_SERVICE
                const-string v1, "hushfeed"
                const/4 v2, 0x1
                invoke-interface {v0, v1, v2}, $SHORTCUT_SERVICE->${rebuild.name}(Ljava/lang/String;Z)V
                const/4 v0, 0x1
                return v0
            """,
        )

        // Every launch, because the switch can be changed while the app is not running and what is
        // published outlives the process. Index 1 rather than 0: the shared extension patch puts
        // its own setContext at the front of this method, and the preference store this reads is
        // not there until that has run.
        MainActivityOnCreateFingerprint.method.addInstruction(
            1,
            "invoke-static/range {p0 .. p0}, $EXTENSION->apply(Landroid/content/Context;)V",
        )

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableLauncherShortcuts()V",
        )
    }
}
