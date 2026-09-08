package app.morphe.patches.tiktok.misc.spoof.region

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.misc.spoof.sim.simSpoofPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/spoof/region/RegionSpoof;"
private object RegionService : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/app/services/RegionService;",
    name = "getRegion", parameters = emptyList(), returnType = "Ljava/lang/String;",
)

@Suppress("unused")
val regionSpoofPatch = bytecodePatch(
    name = "Region spoof",
    description = "Matches locale, timezone and native region getters to the SIM preset, with a separate experimental store-region switch.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(settingsPatch, simSpoofPatch)
    execute {
        val replacements = mapOf("Ljava/util/Locale;" to "locale", "Ljava/util/TimeZone;" to "timeZone")
        val counts = mutableMapOf<String, Int>()
        classDefForEach { definition ->
            if (definition.type.startsWith("Lapp/morphe/extension/")) return@classDefForEach
            definition.methods.forEach { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@forEach
                val calls = instructions.withIndex().mapNotNull { (index, instruction) ->
                    if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) return@mapNotNull null
                    val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
                    val wrapper = replacements[reference.definingClass] ?: return@mapNotNull null
                    if (reference.name != "getDefault" || reference.returnType != reference.definingClass) return@mapNotNull null
                    val result = instructions.getOrNull(index + 1)
                    if (result?.opcode != Opcode.MOVE_RESULT_OBJECT) return@mapNotNull null
                    counts[wrapper] = (counts[wrapper] ?: 0) + 1
                    Triple(index + 2, (result as OneRegisterInstruction).registerA, wrapper to reference.returnType)
                }
                if (calls.isNotEmpty()) {
                    val mutable = mutableClassDefBy(method.definingClass).findMutableMethodOf(method)
                    calls.asReversed().forEach { (index, register, target) ->
                        mutable.addInstructions(index, """
                            invoke-static/range { v$register .. v$register }, $EXTENSION->${target.first}(${target.second})${target.second}
                            move-result-object v$register
                        """)
                    }
                }
            }
        }
        check(replacements.values.all { (counts[it] ?: 0) > 0 }) { "Missing locale or timezone calls" }

        val hubReference = RegionService.method.implementation!!.instructions.mapNotNull {
            it.getReference<MethodReference>()
        }.single { it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty() }
        val hub = mutableClassDefBy(hubReference.definingClass)
        val account = mutableClassDefBy("Lcom/ss/android/ugc/aweme/AccountService;")
        val store = mutableClassDefBy(account.superclass!!).methods.single {
            it.name == "getStoreRegionUpperCase" && it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty()
        }
        val storeField = store.implementation!!.instructions.filter { it.opcode == Opcode.SGET_OBJECT }
            .mapNotNull { it.getReference<FieldReference>() }.first { it.type == "Ljava/lang/String;" }
        val getters = hub.methods.filter {
            AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "Ljava/lang/String;" && it.parameterTypes.isEmpty()
        }
        check(getters.size == 6) { "Unexpected native region hub shape" }
        val priority = getters.single { method -> method.implementation!!.instructions.any {
            it.getReference<FieldReference>()?.toString() == storeField.toString()
        } }
        (getters + store).forEach { method ->
            val wrapper = if (method == priority || method == store) "storeCountry" else "country"
            method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
                .map { it.index to (it.value as OneRegisterInstruction).registerA }.reversed().forEach { (index, register) ->
                    method.addInstructions(index, """
                        invoke-static/range { v$register .. v$register }, $EXTENSION->$wrapper(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$register
                    """)
                }
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableRegionSpoof()V")
    }
}
