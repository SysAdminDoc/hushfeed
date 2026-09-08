package app.morphe.patches.tiktok.interaction.offlinevideos

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CUSTOM_OFFLINE_VIDEOS_HELPER =
    "Lapp/morphe/extension/tiktok/offline/CustomOfflineVideosLimitPatch;"

@Suppress("unused")
val customOfflineVideosLimitPatch = bytecodePatch(
    name = "Custom offline videos limit",
    description = "Adds a custom entry to TikTok's offline videos menu with a configurable limit from 1 to 1000 videos.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        // Without this the two switches were gated on the Downloads patch's flag, so
        // selecting this patch alone left the feature with no way to turn it on, and
        // selecting Downloads alone showed two switches that did nothing.
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCustomOfflineVideos()V",
        )

        OfflineModeSheetOptionsFingerprint.method.apply {
            val freezeListIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_STATIC &&
                    getReference<MethodReference>()?.let { reference ->
                        reference.parameterTypes == listOf("[Ljava/lang/Object;") &&
                            reference.returnType == "Ljava/util/List;"
                    } == true
            }
            val moveResultIndex = indexOfFirstInstructionOrThrow(freezeListIndex + 1) {
                opcode == Opcode.MOVE_RESULT_OBJECT
            }
            val optionsRegister = getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

            addInstructions(
                moveResultIndex + 1,
                """
                    invoke-static {v$optionsRegister}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getOfflineVideoOptions(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$optionsRegister
                """,
            )
        }

        OfflineModeOptionConfigFingerprint.method.apply {
            fun postProcessOptionsField(fieldName: String) {
                val fieldWriteIndex = indexOfFirstInstructionOrThrow {
                    opcode == Opcode.SPUT_OBJECT &&
                        getReference<FieldReference>()?.let { field ->
                            field.definingClass == "LX/0sIr;" &&
                                field.name == fieldName &&
                                field.type == "Ljava/util/List;"
                        } == true
                }
                val moveResultIndex = indexOfFirstInstructionReversedOrThrow(fieldWriteIndex - 1) {
                    opcode == Opcode.MOVE_RESULT_OBJECT
                }
                val optionsRegister = getInstruction<OneRegisterInstruction>(moveResultIndex).registerA

                addInstructions(
                    moveResultIndex + 1,
                    """
                        invoke-static {v$optionsRegister}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getOfflineVideoOptions(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$optionsRegister
                    """,
                )
            }

            postProcessOptionsField("LJ")
            postProcessOptionsField("LJFF")
        }

        OfflineModeOptionEnumFingerprint.method.apply {
            val customEnumFieldWriteIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.SPUT_OBJECT &&
                    getReference<FieldReference>()?.let { field ->
                        field.definingClass == "LX/0mE9;" &&
                            field.name == "DOWNLOAD_200_VIDEOS" &&
                            field.type == "LX/0mE9;"
                    } == true
            }
            val customEnumConstructorIndex = indexOfFirstInstructionReversedOrThrow(
                customEnumFieldWriteIndex - 1,
            ) {
                (opcode == Opcode.INVOKE_DIRECT || opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                    getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "LX/0mE9;" &&
                            reference.name == "<init>" &&
                            reference.returnType == "V" &&
                            reference.parameterTypes.size == 5
                    } == true
            }
            val constructor = getInstruction<RegisterRangeInstruction>(customEnumConstructorIndex)
            val limitRegister = constructor.startRegister + 3
            val minutesRegister = limitRegister + 1
            val sizeRegister = limitRegister + 2

            addInstructions(
                customEnumConstructorIndex,
                """
                    invoke-static/range {v$limitRegister .. v$limitRegister}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getCustomOfflineVideoLimitOrOriginal(I)I
                    move-result v$limitRegister
                    invoke-static/range {v$minutesRegister .. v$minutesRegister}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getCustomOfflineVideoMinutesOrOriginal(I)I
                    move-result v$minutesRegister
                    invoke-static/range {v$sizeRegister .. v$sizeRegister}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getCustomOfflineVideoSizeMbOrOriginal(I)I
                    move-result v$sizeRegister
                """,
            )
        }
    }
}
