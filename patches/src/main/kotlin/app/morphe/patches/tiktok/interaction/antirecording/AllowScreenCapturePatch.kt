package app.morphe.patches.tiktok.interaction.antirecording

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/capture/ScreenCapture;"
private data class CaptureSite(val owner: ClassDef, val method: Method, val index: Int, val replacement: String)

private object CircleSearchBlockFingerprint : Fingerprint(
    strings = listOf("circle_search_block"),
    name = "invoke", parameters = emptyList(), returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val allowScreenCapturePatch = bytecodePatch(
    name = "Allow screenshots and Circle to Search",
    description = "Removes secure window flags and disables the Circle to Search block. Off by default; restart after changing.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())
    execute {
        val signatures = mapOf(
            "Landroid/view/Window;->addFlags(I)V" to "addFlags(Landroid/view/Window;I)V",
            "Landroid/view/Window;->setFlags(II)V" to "setFlags(Landroid/view/Window;II)V",
            "Landroid/view/Window;->setAttributes(Landroid/view/WindowManager@LayoutParams;)V" to
                "setAttributes(Landroid/view/Window;Landroid/view/WindowManager@LayoutParams;)V",
        ).mapKeys { it.key.replace('@', '$') }.mapValues { it.value.replace('@', '$') }
        val sites = mutableListOf<CaptureSite>()
        classDefForEach { owner ->
            if (!owner.type.startsWith("Lapp/morphe/extension/")) owner.methods.forEach { method ->
                method.implementation?.instructions?.forEachIndexed { index, instruction ->
                    val reference = instruction.getReference<MethodReference>()
                    val target = signatures[reference?.toString()]
                    if (target != null && (instruction.opcode == Opcode.INVOKE_VIRTUAL ||
                                instruction.opcode == Opcode.INVOKE_VIRTUAL_RANGE)) {
                        val invoke = when (instruction) {
                            is FiveRegisterInstruction -> {
                                val regs = listOf(instruction.registerC, instruction.registerD, instruction.registerE,
                                    instruction.registerF, instruction.registerG).take(instruction.registerCount)
                                "invoke-static { " + regs.joinToString(", ") { "v$it" } + " }, "
                            }
                            is RegisterRangeInstruction -> "invoke-static/range { v" + instruction.startRegister +
                                " .. v" + (instruction.startRegister + instruction.registerCount - 1) + " }, "
                            else -> error("Unsupported capture call format")
                        }
                        sites += CaptureSite(owner, method, index, invoke + EXTENSION + "->" + target)
                    }
                }
            }
        }
        listOf("addFlags", "setFlags", "setAttributes").forEach { name ->
            require(sites.any { it.replacement.contains("->$name(") }) { "Missing native Window.$name call" }
        }
        sites.forEach { site ->
            mutableClassDefBy(site.owner).findMutableMethodOf(site.method).replaceInstruction(site.index, site.replacement)
        }
        CircleSearchBlockFingerprint.method.apply {
            findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(index, """
                    invoke-static/range { v$register .. v$register }, $EXTENSION->circleBlock(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                """)
            }
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableScreenCapture()V")
        println("Screen capture: replaced " + sites.size + " native window calls and the Circle to Search gate")
    }
}
