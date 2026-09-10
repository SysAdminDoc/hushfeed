package app.morphe.patches.tiktok.interaction.looping

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.cloneMutable
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/AutoAdvance;"
private const val COMPONENT = "Lcom/ss/android/ugc/feed/platform/panel/autoscroll/AutoScrollComponent;"
private const val LAZY = "LAZY"
private const val IMMEDIATE = "IMMEDIATE"

/** Where the panel records how the host should load one of its components. */
private class Registration(
    val owner: String,
    val method: Method,
    val strategyIndex: Int,
    val strategy: String,
)

/**
 * Finds where the panel registers the auto scroll component, and where in that registration it
 * writes the lazy load strategy. Lazy means the host builds the component only once the video
 * panel asks for it by hand, and every hook below hangs off that component's own lifecycle, so
 * until somebody opened the panel there was nothing to hook. The registration is found by the two
 * things only it carries together: the component's own class, and a strategy constant beside it.
 *
 * <p>There is more than one on 46.7.3 and 46.8.3, and they are the same registration twice: R8
 * keeps the lambda that performs it and also copies it into a merged lambda group, and the two
 * copies name the same component, the same view id and the same strategy, differing only in how
 * each fetches the factory it stores. Which of the two the panel calls is not something this can
 * read, so every copy is taken. They are required to agree on the strategy type, because two
 * different ones would mean these are not copies of one registration after all.
 */
private fun BytecodePatchContext.resolveLazyRegistrations(): List<Registration> {
    val found = mutableListOf<Registration>()
    classDefForEach { classDef ->
        for (method in classDef.methods) {
            val instructions = method.implementation?.instructions?.toList() ?: continue
            val namesComponent = instructions.indexOfFirst {
                it.opcode == Opcode.CONST_CLASS && it.getReference<TypeReference>()?.type == COMPONENT
            }
            if (namesComponent < 0) continue
            instructions.forEachIndexed { index, instruction ->
                // LAZY is a common name for a common idea: this build has it on a coroutine
                // start and on two other enums as well. What tells the registration apart is
                // that it writes the constant it just loaded straight into the entry beside
                // the component it is registering.
                if (index <= namesComponent) return@forEachIndexed
                if (instruction.opcode != Opcode.SGET_OBJECT) return@forEachIndexed
                val field = instruction.getReference<FieldReference>() ?: return@forEachIndexed
                if (field.name != LAZY) return@forEachIndexed
                val store = instructions.getOrNull(index + 1) ?: return@forEachIndexed
                if (store.opcode != Opcode.IPUT_OBJECT) return@forEachIndexed
                if ((store as TwoRegisterInstruction).registerA !=
                    (instruction as OneRegisterInstruction).registerA) return@forEachIndexed
                if (store.getReference<FieldReference>()?.type != field.type) return@forEachIndexed
                found.add(Registration(classDef.type, method, index, field.type))
            }
        }
    }
    if (found.isEmpty()) {
        throw PatchException("Auto advance: found no lazy auto scroll registration.")
    }
    // One per method. The scan takes every `LAZY` write after a single const-class of the
    // component, so two in one method would mean it is reading a registration that is not this
    // component's, and requiring exactly one overall used to be what ruled that out.
    val perMethod = found.groupBy { "${it.owner}->${it.method.name}${it.method.parameterTypes}" }
    val crowded = perMethod.filterValues { it.size > 1 }
    if (crowded.isNotEmpty()) {
        throw PatchException(
            "Auto advance: ${crowded.keys.first()} carries ${crowded.values.first().size} lazy " +
                "auto scroll registrations, so the component they belong to is not clear.",
        )
    }
    val strategies = found.mapTo(LinkedHashSet()) { it.strategy }
    if (strategies.size != 1) {
        throw PatchException(
            "Auto advance: the ${found.size} lazy auto scroll registrations write " +
                "${strategies.size} different load strategy types: $strategies.",
        )
    }
    return found
}

/**
 * Hands the host's own strategy constant to the extension and writes back whatever it answers,
 * so the switch decides whether this component is built with the rest of them or left to the
 * panel. The register the host loaded the constant into is the one that carries the answer.
 */
internal fun MutableMethod.chooseAutoAdvanceLoadStrategy(strategyIndex: Int, strategy: String) {
    val register = (getInstruction(strategyIndex) as OneRegisterInstruction).registerA
    addInstructions(
        strategyIndex + 1,
        """
            invoke-static/range {v$register .. v$register}, $EXTENSION->loadStrategy(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object v$register
            check-cast v$register, $strategy
        """,
    )
}

private object Availability : Fingerprint(returnType = "Z", parameters = emptyList(), strings = listOf("fyp_auto_scroll"))

// The feed gate and the panel entry are two different flags. Both are anchored on the string
// rather than on the class that reads it, because those names change every build. Parameter and
// return types match by prefix, so "L" is any object: the factory that builds the panel entry
// takes one and returns one, and the facade gate beside it takes none and returns Z.
private object PanelAction : Fingerprint(returnType = "L", parameters = listOf("L"), strings = listOf("panel_auto_scroll"))
private object PanelGate : Fingerprint(returnType = "Z", parameters = emptyList(), strings = listOf("panel_auto_scroll"))
private object Completion : Fingerprint(definingClass = COMPONENT, name = "onPlayCompleted",
    returnType = "V", parameters = listOf("Ljava/lang/String;"))
private object Stop : Fingerprint(definingClass = COMPONENT, returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Z"), strings = listOf("turn_off_auto_scroll"))

@Suppress("unused")
val autoAdvancePatch = bytecodePatch(
    name = "Automatic video advance",
    description = "Keeps TikTok's automatic advance enabled while preserving its pause, dialog " +
        "and gesture checks, and shows TikTok's own Auto scroll action in the video panel for " +
        "accounts outside its rollout.",
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
            enumClass.fields.any { it.name == "AUTO_SCROLL_STATE_PAUSE" }
        ) {
            "Auto advance: ${state.type} is not the auto scroll state enum. Expected an enum " +
                "with AUTO_SCROLL_STATE_STOP and AUTO_SCROLL_STATE_PAUSE."
        }
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
        val registrations = resolveLazyRegistrations()
        val strategy = registrations.first().strategy
        val strategyEnum = mutableClassDefBy(strategy)
        check(strategyEnum.superclass == "Ljava/lang/Enum;" &&
            strategyEnum.fields.any { it.name == IMMEDIATE }
        ) {
            "Auto advance: $strategy is not the load strategy enum. Expected an enum with " +
                "$IMMEDIATE."
        }
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
        bridge("immediateLoad", 1, """
            sget-object v0, $strategy->$IMMEDIATE:$strategy
            return-object v0
        """)
        // Later sites first, so that inserting into one does not move the next one's index if
        // two copies ever land in the same method.
        registrations.sortedByDescending { it.strategyIndex }.forEach { registration ->
            mutableClassDefBy(registration.owner).findMutableMethodOf(registration.method)
                .chooseAutoAdvanceLoadStrategy(registration.strategyIndex, registration.strategy)
        }
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
        check(returns.isNotEmpty()) {
            "Auto advance: ${available.name} returns nothing this can answer for."
        }
        returns.asReversed().forEach { (index, instruction) ->
            val register = (instruction as OneRegisterInstruction).registerA
            available.addInstructions(index, """
                invoke-static/range {v$register .. v$register}, $EXTENSION->available(Z)Z
                move-result v$register
            """)
        }
        // TikTok's own Auto scroll action in the video panel hangs off a second flag, so an
        // account outside the rollout never sees the entry however the feed gate answers. Both
        // reads go through the same switch.
        val panelAction = PanelAction.method
        val panelInstructions = panelAction.implementation!!.instructions.toList()
        val panelStringIndex = panelInstructions.indexOfFirst {
            it.opcode == Opcode.CONST_STRING &&
                it.getReference<StringReference>()?.string == "panel_auto_scroll"
        }
        check(panelStringIndex >= 0) {
            "Auto advance: the panel_auto_scroll string is gone from the settings panel."
        }
        val panelResultIndex = panelInstructions.withIndex().first { (index, instruction) ->
            index > panelStringIndex && instruction.opcode == Opcode.MOVE_RESULT
        }.index
        val panelRegister = (panelInstructions[panelResultIndex] as OneRegisterInstruction).registerA
        panelAction.addInstructions(
            panelResultIndex + 1,
            """
                invoke-static/range {v$panelRegister .. v$panelRegister}, $EXTENSION->available(Z)Z
                move-result v$panelRegister
            """,
        )

        val panelGate = PanelGate.method
        val panelReturns = panelGate.implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN }
        check(panelReturns.isNotEmpty()) {
            "Auto advance: ${panelGate.name} returns nothing this can answer for."
        }
        panelReturns.asReversed().forEach { (index, instruction) ->
            val register = (instruction as OneRegisterInstruction).registerA
            panelGate.addInstructions(
                index,
                """
                    invoke-static/range {v$register .. v$register}, $EXTENSION->available(Z)Z
                    move-result v$register
                """,
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutoAdvance()V")
    }
}
