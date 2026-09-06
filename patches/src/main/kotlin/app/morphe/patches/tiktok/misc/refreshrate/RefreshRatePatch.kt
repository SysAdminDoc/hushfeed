/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.misc.refreshrate

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/misc/RefreshRate;"
private const val LAYOUT_PARAMS = "Landroid/view/WindowManager\$LayoutParams;"

private fun writesRefreshRate(instruction: com.android.tools.smali.dexlib2.iface.instruction.Instruction) =
    instruction.opcode == Opcode.IPUT &&
        instruction.getReference<FieldReference>()?.let { reference ->
            reference.definingClass == LAYOUT_PARAMS && reference.name == "preferredRefreshRate"
        } == true

/**
 * Anything that asks the window for a refresh rate. The field is Android's, so the write is
 * named in full whatever TikTok called the method around it, and taking every one of them
 * means the switch does not depend on which surface asked. On 46.2.3 the feed's is in
 * PlayerController, where the rate it asks for is the frame rate of the video that just
 * started, which is what drops a 120 Hz screen to the video's 30.
 */
private object RefreshRateWriteFingerprint : Fingerprint(
    custom = { method, _ ->
        method.implementation?.instructions?.any(::writesRefreshRate) == true
    },
)

@Suppress("unused")
val refreshRatePatch = bytecodePatch(
    name = "Uncap the refresh rate",
    description = "Stops TikTok asking the screen to run at the frame rate of the video, " +
        "which on a 90 or 120 Hz phone means everything else in the app runs at that rate " +
        "too. With the switch on the window states no preference and the phone decides. " +
        "Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        var patched = 0
        RefreshRateWriteFingerprint.matchAll().forEach { match ->
            val method = match.method
            val implementation = method.implementation ?: return@forEach

            // Later writes first, because inserting in front of one moves the ones after it.
            val writes = implementation.instructions.withIndex()
                .filter { writesRefreshRate(it.value) }
                .map { it.index }
                .toList()

            writes.asReversed().forEach { index ->
                val rate = (implementation.instructions.elementAt(index) as TwoRegisterInstruction).registerA
                if (rate > 15) {
                    throw PatchException(
                        "Uncap the refresh rate: the rate sits in v$rate in ${method.name}, " +
                            "which move-result cannot reach.",
                    )
                }
                method.addInstructions(
                    index,
                    """
                        invoke-static { v$rate }, $EXTENSION->preferredRefreshRate(F)F
                        move-result v$rate
                    """,
                )
                patched++
            }
        }

        if (patched == 0) throw PatchException("Uncap the refresh rate: nothing asks for one.")

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableRefreshRate()V",
        )
    }
}
