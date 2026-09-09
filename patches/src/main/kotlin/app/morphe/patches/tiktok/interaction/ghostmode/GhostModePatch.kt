/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 * Follows eduardo3677-ai/tiktok-patches-for-morphe.
 */
package app.morphe.patches.tiktok.interaction.ghostmode

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.numberOfParameterRegisters

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/ghostmode/GhostMode;"

/** Reports that a story was seen, opened or interacted with. */
private object StoryViewReportFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/StoryApi;") &&
            method.name in setOf("reportStoryViewed", "reportUserInteraction", "reportStoryReveal")
    },
)

/** Records a profile visit against the profile's viewer list. */
private object ProfileViewReportFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/ProfileViewerApiService;") && method.name == "reportView"
    },
)

/** Pushes the "typing…" indicator into a conversation. */
private object TypingStatusSenderFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.endsWith("/TypingStatusSenderTimer;") &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == "Ljava/lang/String;" &&
            method.returnType == "V"
    },
)

/**
 * Returns from the method before it reports anything, when the extension says to. The
 * return instruction is chosen from the method's own return type rather than assumed, and
 * a method that cannot take the guard is skipped:
 *
 * - an abstract method (a Retrofit interface declaration) has nothing to inject into,
 * - a frame with no local registers would have the guard clobber a parameter, and
 * - a wide return would need a register pair.
 *
 * @return true when the guard was injected.
 */
private fun MutableMethod.guardWith(extensionMethodName: String): Boolean {
    val implementation = implementation ?: return false
    if (implementation.registerCount - numberOfParameterRegisters < 1) return false

    val returnInstruction = when {
        returnType == "V" -> "return-void"
        returnType.startsWith("L") || returnType.startsWith("[") -> "const/4 v0, 0x0\n                    return-object v0"
        returnType == "J" || returnType == "D" -> return false
        else -> "const/4 v0, 0x0\n                    return v0"
    }

    addInstructionsWithLabels(
        0,
        """
            invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->$extensionMethodName()Z
            move-result v0
            if-eqz v0, :morphe_ghost_mode_off
            $returnInstruction
            :morphe_ghost_mode_off
            nop
        """,
    )
    return true
}

/**
 * Stops the reports that tell other people what you looked at: story views, profile
 * views and the typing indicator. It suppresses the client's own
 * reporting only; nothing here changes what the server already knows.
 */
@Suppress("unused")
val ghostModePatch = bytecodePatch(
    name = "Ghost mode",
    description = "Adds an option to stop TikTok reporting that you viewed a story or a " +
        "profile or that you are typing. Online status is unchanged.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableGhostMode()V",
        )

        listOf(
            StoryViewReportFingerprint to "shouldBlockStoryView",
            ProfileViewReportFingerprint to "shouldBlockProfileView",
            TypingStatusSenderFingerprint to "shouldBlockTypingStatus",
        ).forEach { (fingerprint, guard) ->
            // Retrofit declarations have no body. Every concrete reporting method is mandatory.
            val methods = fingerprint.matchAll().map { it.method }.filter { it.implementation != null }
            if (methods.isEmpty() || methods.any { !it.guardWith(guard) }) {
                throw PatchException("Ghost mode: could not install every $guard hook.")
            }
        }
    }
}
