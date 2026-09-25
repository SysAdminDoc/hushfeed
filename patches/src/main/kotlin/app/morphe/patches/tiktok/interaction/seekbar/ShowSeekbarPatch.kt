/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/seekbar/ShowSeekbarPatch.kt
 */
package app.morphe.patches.tiktok.interaction.seekbar

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.tiktok.misc.absettings.hookAppAbIntBoundary
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.requireLocals
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/seekbar/SeekbarPatch;"

/**
 * Inserts a call to [SeekbarPatch.forceSeekbarRefresh] between the stored show-type read and
 * the native equality shortcut, so a zero rewrite from [SeekbarPatch.overrideSeekbarShowType]
 * cannot match a stored zero and skip the visible branch.
 */
internal fun MutableMethod.hookSeekbarTypeRefresh() {
    val instructions = implementation!!.instructions.toList()
    data class Candidate(val igetIndex: Int, val storedReg: Int, val paramReg: Int)
    val candidates = mutableListOf<Candidate>()
    for (i in instructions.indices) {
        val insn = instructions[i]
        if (insn.opcode != Opcode.IGET) continue
        val field = insn.getReference<FieldReference>() ?: continue
        if (field.type != "I") continue
        val two = insn as? TwoRegisterInstruction ?: continue
        val storedReg = two.registerA
        if (i + 1 >= instructions.size) continue
        val next = instructions[i + 1]
        if (next.opcode != Opcode.IF_EQ) continue
        val cmp = next as? TwoRegisterInstruction ?: continue
        val paramReg = when {
            cmp.registerA == storedReg -> cmp.registerB
            cmp.registerB == storedReg -> cmp.registerA
            else -> continue
        }
        candidates.add(Candidate(i, storedReg, paramReg))
    }
    if (candidates.size != 1) {
        throw PatchException(
            "Expected exactly one stored-type equality check, found ${candidates.size}."
        )
    }
    val (igetIndex, storedReg, paramReg) = candidates.single()
    addInstructions(
        igetIndex + 1,
        """
            invoke-static {v$storedReg, v$paramReg}, $EXTENSION_CLASS_DESCRIPTOR->forceSeekbarRefresh(II)I
            move-result v$storedReg
        """,
    )
}

@Suppress("unused")
val showSeekbarPatch = bytecodePatch(
    name = "Show the progress bar",
    description = "Shows TikTok's native video seekbar where it would normally be hidden, including when one of TikTok's experiments takes it off every video but paid content. Switch: Hushfeed settings > App.",
    default = true,
) {
    category("Playback")
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        // Checked before the first write: the predicate's injection writes v0 and then falls
        // into TikTok's own first instruction, so v0 has to be a local rather than p0.
        ShouldShowProgressBarFingerprint.method.requireLocals("Show the progress bar", 1)

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableShowSeekbar()V",
        )

        // This target is TikTok's short predicate used by the feed progress UI.
        ShouldShowProgressBarFingerprint.method.addInstructions(
            0,
            """
                if-eqz p0, :show_seekbar_original
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->isEnabled()Z
                move-result v0
                if-eqz v0, :show_seekbar_original
                const/4 v0, 0x1
                return v0
                :show_seekbar_original
            """,
        )

        SetSeekBarShowTypeFingerprint.method.apply {
            // The last parameter, which the fingerprint holds to being the int. An int takes one
            // register, so it is the top of the frame; range form, because that can sit past v15.
            val typeRegister = implementation!!.registerCount - 1
            addInstructions(
                0,
                """
                    invoke-static/range {v$typeRegister .. v$typeRegister}, $EXTENSION_CLASS_DESCRIPTOR->overrideSeekbarShowType(I)I
                    move-result v$typeRegister
                """,
            )
            hookSeekbarTypeRefresh()
        }

        // TikTok's reasons for keeping the bar off a video run before the show type is asked.
        // Two of them are experiments; both are found from their log line and answered here.
        val gates = SeekbarGateLogFingerprint.method
        val inverse = gates.gateBefore(INVERSE_EXPERIMENT_LOG)
        val drag = gates.gateBefore(CANNOT_DRAG_LOG)
        mutableClassDefBy(inverse.definingClass).methods.named(inverse).answerInverseExperiment()
        mutableClassDefBy(drag.definingClass).methods.named(drag).answerDraggable()
    }
}

@Suppress("unused")
val showSeekbarThumbnailPatch = bytecodePatch(
    name = "Show the progress bar thumbnail",
    description = "Shows TikTok's video preview thumbnail while dragging the seekbar.",
    default = true,
) {
    category("Playback")
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSeekbarThumbnail()V",
        )
        hookAppAbIntBoundary(
            EXTENSION_CLASS_DESCRIPTOR,
            "overrideThumbnailGate",
        )
    }
}
