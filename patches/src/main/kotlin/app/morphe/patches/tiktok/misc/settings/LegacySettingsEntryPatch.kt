/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/settings/SettingsPatch.kt
 */
package app.morphe.patches.tiktok.misc.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/settings/TikTokActivityHook;"

context(patchContext: BytecodePatchContext)
internal fun addLegacySettingsEntryFallback() = with(patchContext) {
        val createSettingsEntryMethodDescriptor =
            "$EXTENSION_CLASS_DESCRIPTOR->createSettingsEntry(" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                ")Ljava/lang/Object;"

        fun String.toClassName(): String = substring(1, length - 1).replace("/", ".")

        val settingsButtonClass = SettingsEntryFingerprint.originalClassDef.type.toClassName()
        val settingsButtonInfoClass = SettingsEntryInfoFingerprint.originalClassDef.type.toClassName()

        // If this optional secondary row fingerprint does not match, skip it instead of failing the patch run.
        // If fingerprints don't match, skip instead of failing the whole patch run.
        AddSettingsEntryFingerprint.methodOrNull?.let { addSettingsMethod ->
            val implementation = addSettingsMethod.implementation ?: return@let
            val markIndex = implementation.instructions.indexOfFirst {
                it.opcode == Opcode.IGET_OBJECT &&
                    (it as? Instruction22c)?.reference?.let { ref -> ref is FieldReference && ref.name == "headerUnit" } == true
            }

            if (markIndex < 0) return@let

            val getUnitManager = addSettingsMethod.getInstruction(markIndex + 2)
            val addEntry = addSettingsMethod.getInstruction(markIndex + 1)

            addSettingsMethod.addInstructions(markIndex + 2, listOf(getUnitManager, addEntry))

            addSettingsMethod.addInstructions(
                markIndex + 2,
                """
                    const-string v0, "$settingsButtonClass"
                    const-string v1, "$settingsButtonInfoClass"
                    invoke-static {v0, v1}, $createSettingsEntryMethodDescriptor
                    move-result-object v0
                    check-cast v0, ${SettingsEntryFingerprint.originalClassDef.type}
                """,
            )
        }

    Unit
}
