package app.morphe.patches.tiktok.interaction.offlinevideos

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CUSTOM_OFFLINE_VIDEOS_HELPER =
    "Lapp/morphe/extension/tiktok/offline/CustomOfflineVideosLimitPatch;"

/** The option enum's constructor: the name, the ordinal, the limit, the minutes and the size. */
private val OPTION_CONSTRUCTOR = listOf("Ljava/lang/String;", "I", "I", "I", "I")

@Suppress("unused")
val customOfflineVideosLimitPatch = bytecodePatch(
    name = "Custom offline videos limit",
    description = "Adds a custom entry to TikTok's offline videos menu with a configurable limit from 1 to 1000 videos.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        // Everything the writes below need is found and checked before the first of them, because
        // the patcher does not take a failed patch's writes back out.

        // Without this the two switches were gated on the Downloads patch's flag, so
        // selecting this patch alone left the feature with no way to turn it on, and
        // selecting Downloads alone showed two switches that did nothing.
        val settingsStatus = SettingsStatusLoadFingerprint.method

        // The sheet's own choices, frozen from an array in its initialiser.
        val sheet = OfflineModeSheetOptionsFingerprint.method
        val freezeListIndex = sheet.indexOfFirstInstructionOrThrow {
            opcode == Opcode.INVOKE_STATIC &&
                getReference<MethodReference>()?.let { reference ->
                    reference.parameterTypes == listOf("[Ljava/lang/Object;") &&
                        reference.returnType == "Ljava/util/List;"
                } == true
        }
        val sheetResultIndex = sheet.indexOfFirstInstructionOrThrow(freezeListIndex + 1) {
            opcode == Opcode.MOVE_RESULT_OBJECT
        }
        val sheetRegister = sheet.getInstruction<OneRegisterInstruction>(sheetResultIndex).registerA

        // The lists the offline settings read. Their holder is renamed per build and was rebuilt
        // between them, so it is found through the detail page, which asks it a static question
        // as its view is made: the one class it asks a no-argument boolean whose initialiser
        // builds static Lists out of boxed counts.
        val detail = OfflineModeDetailViewCreatedFingerprint.method
        val asked = detail.implementation!!.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.INVOKE_STATIC) return@mapNotNull null
            val call = instruction.getReference<MethodReference>() ?: return@mapNotNull null
            call.definingClass.takeIf { call.parameterTypes.isEmpty() && call.returnType == "Z" }
        }.distinct()
        val holders = asked.filter { type ->
            classDefByOrNull(type)?.methods?.firstOrNull { it.name == "<clinit>" }
                ?.let { optionListWrites(it, type).isNotEmpty() && it.boxesCounts() } == true
        }
        if (holders.size != 1) {
            throw PatchException(
                "Custom offline videos limit: expected the offline detail page to ask one class " +
                    "that builds the option lists, found ${holders.size} among $asked.",
            )
        }
        val holder = holders.single()
        val holderInit = mutableClassDefBy(holder).methods.single { it.name == "<clinit>" }
        val listResults = optionListResults(holderInit, holder)

        // The download limit choices: an enum found by the constants it declares, whose custom
        // entry is built with the limit, the minutes and the size the extension overrides.
        val options = OfflineModeOptionEnumFingerprint.method
        // The enum's own type, off the match. It was written here as LX/0mE9;, which is the
        // 46.2.3 name and nothing else.
        val optionEnum = options.definingClass
        val customEnumFieldWriteIndex = options.indexOfFirstInstructionOrThrow {
            opcode == Opcode.SPUT_OBJECT &&
                getReference<FieldReference>()?.let { field ->
                    field.definingClass == optionEnum &&
                        field.name == "DOWNLOAD_200_VIDEOS" &&
                        field.type == optionEnum
                } == true
        }
        // Six registers (the new value, the name, the ordinal and three numbers) only fit the
        // range form, which is the one the three builds use. A plain invoke used to be accepted
        // here and then cast to a range, which would have thrown instead of saying so.
        val customEnumConstructorIndex = options.indexOfFirstInstructionReversedOrThrow(
            customEnumFieldWriteIndex - 1,
        ) {
            opcode == Opcode.INVOKE_DIRECT_RANGE &&
                getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == optionEnum &&
                        reference.name == "<init>" &&
                        reference.returnType == "V" &&
                        reference.parameterTypes.map(CharSequence::toString) == OPTION_CONSTRUCTOR
                } == true
        }
        val constructor = options.getInstruction<RegisterRangeInstruction>(customEnumConstructorIndex)
        val limitRegister = constructor.startRegister + 3
        val minutesRegister = limitRegister + 1
        val sizeRegister = limitRegister + 2

        settingsStatus.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableCustomOfflineVideos()V",
        )

        sheet.addInstructions(sheetResultIndex + 1, wrapOptions(sheetRegister))

        // Highest first, so the indices below stay where they were read.
        for (resultIndex in listResults.sortedDescending()) {
            val register = holderInit.getInstruction<OneRegisterInstruction>(resultIndex).registerA
            holderInit.addInstructions(resultIndex + 1, wrapOptions(register))
        }

        options.addInstructions(
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

/** The list in [register], handed to the extension to have the custom limit added to it. */
private fun wrapOptions(register: Int) =
    """
        invoke-static/range {v$register .. v$register}, $CUSTOM_OFFLINE_VIDEOS_HELPER->getOfflineVideoOptions(Ljava/util/List;)Ljava/util/List;
        move-result-object v$register
    """

/** Whether the initialiser boxes ints, which is what the counts in the option lists are. */
private fun Method.boxesCounts() = implementation?.instructions?.any { instruction ->
    instruction.getReference<MethodReference>()?.let {
        it.definingClass == "Ljava/lang/Integer;" && it.name == "valueOf" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("I")
    } == true
} == true

/** Every `sput-object` in [init] to a static List of [holder]'s own. */
internal fun optionListWrites(init: Method, holder: String): List<Int> =
    init.implementation?.instructions?.withIndex()?.filter { (_, instruction) ->
        instruction.opcode == Opcode.SPUT_OBJECT &&
            instruction.getReference<FieldReference>()?.let {
                it.definingClass == holder && it.type == "Ljava/util/List;"
            } == true
    }?.map { it.index }.orEmpty()

/**
 * Where each option list is handed over in [init]: the `move-result-object` of the call that
 * freezes an array into a List, right before it is stored. Two lists on 46.2.3 and four on
 * 46.8.3, and every one of them is a list the offline settings may show, so every one gets the
 * custom limit. A static List of the holder's stored any other way is refused rather than
 * skipped, because a skipped one is a menu that silently lacks the entry.
 */
internal fun optionListResults(init: Method, holder: String): List<Int> {
    val instructions = init.implementation?.instructions?.toList()
        ?: throw PatchException("Custom offline videos limit: the option holder has no initialiser body.")
    val writes = optionListWrites(init, holder)
    if (writes.isEmpty()) {
        throw PatchException("Custom offline videos limit: $holder stores no option list.")
    }
    return writes.map { writeIndex ->
        val stored = (instructions[writeIndex] as OneRegisterInstruction).registerA
        val result = instructions.getOrNull(writeIndex - 1)
        val freeze = instructions.getOrNull(writeIndex - 2)?.getReference<MethodReference>()
        val field = instructions[writeIndex].getReference<FieldReference>()!!.name
        if (result?.opcode != Opcode.MOVE_RESULT_OBJECT ||
            (result as OneRegisterInstruction).registerA != stored ||
            freeze?.returnType != "Ljava/util/List;" ||
            freeze.parameterTypes.map(CharSequence::toString) != listOf("[Ljava/lang/Object;")
        ) {
            throw PatchException(
                "Custom offline videos limit: $holder stores $field without freezing it from an " +
                    "array straight before, so there is no list to add the custom limit to.",
            )
        }
        writeIndex - 1
    }
}
