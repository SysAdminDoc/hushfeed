/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.resume

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.requireLocals
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
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
            // v0 is scratch and the branch falls back into the host's own first instruction, so
            // the register has to be a local rather than one of the parameters.
            check(implementation!!.registerCount - numberOfParameterRegisters >= 1) {
                "Resume videos after scrolling: the continue gate has no free local register."
            }
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
            // v0 and v1 are written before the host's own first instruction runs.
            requireLocals("Resume videos after scrolling", 2)
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

        // TikTok records a position for every feed it plays, but it only hands one back when the
        // feed's event type is in FeedPlayProgressContinueConfig.event_type_list. On 46.2.3 that
        // list is built in the config's own constructor and holds homepage_hot, others_homepage,
        // personal_homepage, landscape_mode, account_history and collection_video. The Following
        // and Friends tabs are not in it, which is why the switch appeared to do nothing there
        // while the position was being stored all along.
        FeedProgressResumePositionFingerprint.method.apply {
            val eventTypeListIndex = indexOfFirstInstructionOrThrow {
                getReference<FieldReference>()?.name == "event_type_list"
            }
            val containsIndex = indexOfFirstInstructionOrThrow(eventTypeListIndex) {
                val reference = getReference<MethodReference>()
                reference?.definingClass == "Ljava/util/List;" && reference.name == "contains"
            }
            val resultInstruction = getInstruction(containsIndex + 1)
            if (resultInstruction.opcode != Opcode.MOVE_RESULT) {
                throw PatchException(
                    "Resume video after scroll: the event type check does not keep its answer.",
                )
            }
            val resultRegister = (resultInstruction as OneRegisterInstruction).registerA
            check(resultRegister <= 15) {
                "Resume video after scroll: the event type answer is above v15, which the " +
                    "override cannot name."
            }

            addInstructions(
                containsIndex + 2,
                """
                    invoke-static {v$resultRegister}, $EXTENSION_DESCRIPTOR->allowResumeInThisFeed(Z)Z
                    move-result v$resultRegister
                """,
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

            // The cache, the key and a register to work in all come out of the put itself: it
            // holds the cache and the key this has to remove, and the value it was given is
            // spent the moment it returns. Naming them by number was right on 46.2.3 and says
            // nothing about the next build.
            val put = getInstruction(cachePutIndex) as? FiveRegisterInstruction
                ?: throw PatchException(
                    "Resume video after scroll: the progress cache put is not a plain invoke.",
                )
            val cacheRegister = put.registerC
            val keyRegister = put.registerD
            val scratchRegister = put.registerE
            check(maxOf(cacheRegister, keyRegister, scratchRegister) <= 15) {
                "Resume video after scroll: the progress cache put reaches above v15, which the " +
                    "removal cannot name."
            }

            addInstructionsWithLabels(
                cachePutIndex + 1,
                """
                    invoke-static/range {p2 .. p5}, $EXTENSION_DESCRIPTOR->shouldClearCompletedProgress(JJ)Z
                    move-result v$scratchRegister
                    if-eqz v$scratchRegister, :continue_progress
                    invoke-virtual {v$cacheRegister, v$keyRegister}, Landroid/util/LruCache;->remove(Ljava/lang/Object;)Ljava/lang/Object;
                    const/4 v$scratchRegister, 0x0
                    sput-object v$scratchRegister, LX/0Lze;->LJ:LX/0LxV;
                    sput-object v$scratchRegister, LX/0Lze;->LJFF:Ljava/lang/String;
                """,
                ExternalLabel("continue_progress", continueInstruction),
            )
        }
    }
}
