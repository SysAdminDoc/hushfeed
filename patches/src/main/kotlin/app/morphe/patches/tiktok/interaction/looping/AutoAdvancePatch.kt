package app.morphe.patches.tiktok.interaction.looping

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/AutoAdvance;"
private const val COMPONENT = "Lcom/ss/android/ugc/feed/platform/panel/autoscroll/AutoScrollComponent;"

private object Availability : Fingerprint(returnType = "Z", parameters = emptyList(), strings = listOf("fyp_auto_scroll"))
private object Completion : Fingerprint(definingClass = COMPONENT, name = "onPlayCompleted",
    returnType = "V", parameters = listOf("Ljava/lang/String;"))
private object Stop : Fingerprint(definingClass = COMPONENT, returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"), strings = listOf("turn_off_auto_scroll"))

@Suppress("unused")
val autoAdvancePatch = bytecodePatch(
    name = "Automatic video advance",
    description = "Keeps TikTok's automatic advance enabled while preserving its pause, dialog and gesture checks.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())
    execute {
        val completed = Completion.method
        val component = mutableClassDefBy(COMPONENT)
        val state = completed.implementation!!.instructions.firstNotNullOf { instruction ->
            instruction.getReference<FieldReference>()?.takeIf {
                instruction.opcode == Opcode.IGET_OBJECT && it.definingClass == COMPONENT
            }
        }
        val enumClass = mutableClassDefBy(state.type)
        check(enumClass.superclass == "Ljava/lang/Enum;" &&
            enumClass.fields.any { it.name == "AUTO_SCROLL_STATE_STOP" } &&
            enumClass.fields.any { it.name == "AUTO_SCROLL_STATE_PAUSE" })
        val aweme = completed.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .filter { it.parameterTypes.isEmpty() && it.returnType == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" }
            .distinctBy { it.toString() }.single()
        val startCore = component.methods.single {
            AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
                it.parameterTypes == listOf(COMPONENT, "Z", "Z", "Z", "I")
        }
        val start = component.methods.single { method ->
            method.parameterTypes == listOf("Z", "Z") && method.returnType == "V" &&
                method.implementation!!.instructions.any { it.getReference<MethodReference>()?.toString() == startCore.toString() }
        }
        val stop = Stop.method
        val extension = mutableClassDefBy(EXTENSION)
        fun bridge(name: String, registers: Int, code: String) {
            val original = extension.methods.single { it.name == name }
            val replacement = original.cloneMutable(additionalRegisters = registers)
            extension.methods.remove(original)
            extension.methods.add(replacement)
            replacement.addInstructions(0, code)
        }
        bridge("readState", 1, """
            check-cast p0, $COMPONENT
            iget-object v0, p0, $state
            return-object v0
        """)
        bridge("readAweme", 1, """
            check-cast p0, $COMPONENT
            invoke-virtual/range {p0 .. p0}, $aweme
            move-result-object v0
            return-object v0
        """)
        bridge("start", 3, """
            check-cast p0, $COMPONENT
            move-object/from16 v0, p0
            const/4 v1, 0x0
            const/4 v2, 0x0
            invoke-virtual/range {v0 .. v2}, $start
            return-void
        """)
        bridge("stop", 3, """
            check-cast p0, $COMPONENT
            move-object/from16 v0, p0
            const-string v1, ""
            const/4 v2, 0x0
            invoke-virtual/range {v0 .. v2}, $stop
            return-void
        """)
        component.methods.single { it.name == "onViewCreated" && it.parameterTypes == listOf("Landroid/view/View;") }
            .addInstruction(0, "invoke-static/range {p0 .. p1}, $EXTENSION->onView(Ljava/lang/Object;Landroid/view/View;)V")
        for (name in listOf("onResume", "onDestroy")) {
            component.methods.single { it.name == name && it.parameterTypes.isEmpty() }
                .addInstruction(0, "invoke-static/range {p0 .. p0}, $EXTENSION->$name(Ljava/lang/Object;)V")
        }
        completed.addInstruction(0,
            "invoke-static/range {p0 .. p1}, $EXTENSION->beforeCompletion(Ljava/lang/Object;Ljava/lang/String;)V")
        val available = Availability.method
        val returns = available.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN }
        check(returns.isNotEmpty())
        returns.asReversed().forEach { (index, instruction) ->
            val register = (instruction as OneRegisterInstruction).registerA
            available.addInstructions(index, """
                invoke-static/range {v$register .. v$register}, $EXTENSION->available(Z)Z
                move-result v$register
            """)
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutoAdvance()V")
    }
}
