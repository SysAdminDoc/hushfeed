/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/speed/PlaybackSpeedPatch.kt
 */
package app.morphe.patches.tiktok.interaction.speed

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.OnRenderFirstFrameFingerprint
import app.morphe.patches.tiktok.interaction.cleardisplay.OnRenderFirstFrameBodyFingerprint
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/speed/PlaybackSpeedPatch;"
private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

@Suppress("unused")
val playbackSpeedPatch = bytecodePatch(
    name = "Playback speed",
    description = "Remembers playback speed or applies a default to each new video, with custom menu choices up to 3x.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        val selection = PlaybackSpeedSelectionBoundaryFingerprint.method
        check(AccessFlags.STATIC.isSet(selection.accessFlags)) {
            "Playback speed: ${selection.name} is no longer static, so p0 is not the speed."
        }
        selection.addInstruction(
            0,
            "invoke-static/range {p0 .. p3}, $EXTENSION->onSelection(F${AWEME}Ljava/lang/String;Ljava/lang/String;)V",
        )

        val controllerSetSpeed = PlayerControllerSetSpeedFingerprint.method
        val playerManagerSetSpeed = controllerSetSpeed.implementation!!.instructions
            .firstNotNullOfOrNull { instruction ->
                instruction.getReference<MethodReference>()?.takeIf { reference ->
                    instruction.opcode == Opcode.INVOKE_INTERFACE &&
                        reference.name == "setSpeed" &&
                        reference.parameterTypes == listOf("F") &&
                        reference.returnType == "V"
                }
            } ?: throw PatchException(
            "Playback speed: player-manager speed boundary was not resolved.",
        )

        val playerControllerClass = mutableClassDefBy(controllerSetSpeed.definingClass)
        val transitionResetCandidates = playerControllerClass.methods.filter { method ->
            method.parameterTypes == listOf(
                "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
                "Z",
            ) && method.returnType == "V" && method.implementation?.instructions?.count { instruction ->
                instruction.getReference<MethodReference>() == playerManagerSetSpeed
            } == 3
        }
        val transitionReset = transitionResetCandidates.singleOrNull() ?: throw PatchException(
            "Playback speed: expected one three-branch feed transition reset, " +
                "found ${transitionResetCandidates.size}.",
        )

        val transitionCallers = playerControllerClass.methods.filter { method ->
            val instructions = method.implementation?.instructions ?: return@filter false
            val callsTransitionReset = instructions.any { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == transitionReset.definingClass &&
                        reference.name == transitionReset.name &&
                        reference.parameterTypes == transitionReset.parameterTypes &&
                        reference.returnType == transitionReset.returnType
                } == true
            }
            callsTransitionReset && instructions.any { instruction ->
                instruction.getReference<MethodReference>() == playerManagerSetSpeed
            }
        }
        if (transitionCallers.isEmpty()) {
            throw PatchException(
                "Playback speed: no feed transition caller with a direct speed write was found.",
            )
        }

        (listOf(transitionReset) + transitionCallers).forEach { method ->
            val directCalls = method.implementation!!.instructions.withIndex().mapNotNull { (index, instruction) ->
                if (instruction.getReference<MethodReference>() != playerManagerSetSpeed) {
                    return@mapNotNull null
                }
                val speedRegister = when (instruction) {
                    is FiveRegisterInstruction -> instruction.registerD
                    is RegisterRangeInstruction -> instruction.startRegister + 1
                    else -> throw PatchException(
                        "Playback speed: unsupported feed transition speed invocation shape.",
                    )
                }
                index to speedRegister
            }
            directCalls.asReversed().forEach { (index, speedRegister) ->
                method.addInstructions(
                    index,
                    """
                        invoke-static/range {v$speedRegister .. v$speedRegister}, $EXTENSION->preserveTransitionSpeed(F)F
                        move-result v$speedRegister
                    """,
                )
            }
        }

        check(!AccessFlags.STATIC.isSet(transitionReset.accessFlags)) {
            "Playback speed: ${transitionReset.name} became static, so p1 is not the video."
        }
        transitionReset.addInstruction(0, "invoke-static/range {p1 .. p1}, $EXTENSION->beginVideo($AWEME)V")

        val frame = OnRenderFirstFrameBodyFingerprint.method
        // Keep native menu highlighting and its same-speed guard aligned with the player.
        val stateWrites = selection.implementation!!.instructions.takeWhile {
            !it.opcode.name.startsWith("if-")
        }.filter { it.opcode == Opcode.SPUT || it.opcode == Opcode.SPUT_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass == selection.definingClass }
        val currentAwemeField = stateWrites.single { it.type == AWEME }
        val speedFields = stateWrites.filter { it.type == "F" }.distinctBy { it.toString() }
        check(speedFields.size == 2) {
            "Playback speed: expected two float fields written before the first branch of " +
                "${selection.name}, found ${speedFields.size}."
        }
        check(mutableClassDefBy(selection.definingClass).fields.filter { field ->
            stateWrites.any { it.name == field.name }
        }.all { AccessFlags.PUBLIC.isSet(it.accessFlags) }) {
            "Playback speed: the state fields on ${selection.definingClass} are not all public, " +
                "so the extension cannot read them."
        }
        val awemeGetter = frame.implementation!!.instructions.mapNotNull {
            it.getReference<MethodReference>()
        }.filter {
            it.definingClass == frame.definingClass && it.parameterTypes.isEmpty() && it.returnType == AWEME
        }.distinctBy { it.toString() }.single()
        val extension = mutableClassDefBy(EXTENSION)
        val original = extension.methods.single { it.name == "onFirstFrame" }
        val bridge = original.cloneMutable(additionalRegisters = 2)
        extension.methods.remove(original)
        extension.methods.add(bridge)
        bridge.addInstructions(0, """
            check-cast p0, ${frame.definingClass}
            invoke-virtual/range {p0 .. p0}, $awemeGetter
            move-result-object v0
            invoke-static/range {v0 .. v0}, $EXTENSION->getPlaybackSpeedForVideo($AWEME)F
            move-result v1
            sput-object v0, $currentAwemeField
            ${speedFields.joinToString("\n") { "sput v1, $it" }}
            move-object/from16 v0, p0
            invoke-virtual/range {v0 .. v1}, $controllerSetSpeed
            return-void
        """)

        OnRenderFirstFrameFingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, $EXTENSION->onFirstFrame(Ljava/lang/Object;)V",
        )

        // Resolve the menu's lazy Float-list factory from its own constructor references.
        val menuClass = mutableClassDefBy(PlaybackSpeedMenuFingerprint.method.definingClass)
        val factoryOwners = menuClass.methods.filter { it.name == "<init>" }.flatMap { method ->
            method.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
        }.filter {
            it.parameterTypes == listOf("I") && it.returnType == it.definingClass &&
                it.definingClass.startsWith("Lkotlin/jvm/internal/")
        }.map { it.definingClass }.distinct()
        val expected = listOf(0.5f, 1f, 1.5f, 2f, 3f).map { it.toRawBits() }
        val factories = factoryOwners.flatMap { mutableClassDefBy(it).methods }.filter { method ->
            method.returnType == "Ljava/lang/Object;" && AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.implementation?.instructions?.filterIsInstance<NarrowLiteralInstruction>()
                    ?.map { it.narrowLiteral }?.containsAll(expected) == true
        }
        val factory = factories.singleOrNull() ?: throw PatchException(
            "Playback speed: expected one menu list factory, found ${factories.size}.")
        val returns = factory.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        check(returns.isNotEmpty()) {
            "Playback speed: the menu list factory returns no object to replace."
        }
        returns.asReversed().forEach { (index, instruction) ->
            val register = (instruction as OneRegisterInstruction).registerA
            factory.addInstructions(index, """
                invoke-static/range {v$register .. v$register}, $EXTENSION->menuSpeeds(Ljava/lang/Object;)Ljava/lang/Object;
                move-result-object v$register
            """)
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enablePlaybackSpeed()V")
    }
}
