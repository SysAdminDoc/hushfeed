/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.resume

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/interaction/ResumeVideoAfterScrollPatch;"

@Suppress("unused")
val resumeVideoAfterScrollPatch = bytecodePatch(
    name = "Resume videos after scrolling",
    description = "Continues supported videos from where playback stopped when returning after a scroll.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableResumeVideoAfterScroll()V",
        )

        FeedProgressContinueGateFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_DESCRIPTOR->shouldResumeVideoAfterScroll()Z
                    move-result v0
                    if-eqz v0, :continue_gate
                    const/4 v0, 0x1
                    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                    move-result-object v0
                    return-object v0
                """,
                ExternalLabel("continue_gate", getInstruction(0)),
            )
        }

        FeedPlayCompletedFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION_DESCRIPTOR->shouldResumeVideoAfterScroll()Z
                    move-result v0
                    if-eqz v0, :continue_completion
                    sget-object v0, LX/0Lze;->LIZLLL:LX/01xP;
                    invoke-interface {v0}, LX/01xP;->getValue()Ljava/lang/Object;
                    move-result-object v0
                    check-cast v0, Landroid/util/LruCache;
                    move-object/from16 v1, p1
                    invoke-virtual {v0, v1}, Landroid/util/LruCache;->remove(Ljava/lang/Object;)Ljava/lang/Object;
                    const/4 v0, 0x0
                    sput-object v0, LX/0Lze;->LJ:LX/0LxV;
                    sput-object v0, LX/0Lze;->LJFF:Ljava/lang/String;
                """,
                ExternalLabel("continue_completion", getInstruction(0)),
            )
        }

        FeedPlayProgressFingerprint.method.apply {
            val cachePutIndex = indexOfFirstInstructionReversedOrThrow {
                val reference = getReference<MethodReference>()
                reference?.definingClass == "Landroid/util/LruCache;" &&
                    reference.name == "put" &&
                    reference.parameterTypes.size == 2
            }
            val continueInstruction = getInstruction(cachePutIndex + 1)

            addInstructionsWithLabels(
                cachePutIndex + 1,
                """
                    invoke-static/range {p2 .. p5}, $EXTENSION_DESCRIPTOR->shouldClearCompletedProgress(JJ)Z
                    move-result v5
                    if-eqz v5, :continue_progress
                    invoke-virtual {v14, v6}, Landroid/util/LruCache;->remove(Ljava/lang/Object;)Ljava/lang/Object;
                    const/4 v5, 0x0
                    sput-object v5, LX/0Lze;->LJ:LX/0LxV;
                    sput-object v5, LX/0Lze;->LJFF:Ljava/lang/String;
                """,
                ExternalLabel("continue_progress", continueInstruction),
            )
        }
    }
}
