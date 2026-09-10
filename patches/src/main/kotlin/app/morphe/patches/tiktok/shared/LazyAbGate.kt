/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.shared

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31t
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** The factory a merged lambda group hands out instances from, indexed by the lambda's number. */
private const val GROUP_FACTORY = "get\$arr\$"

/** How deep R8's split switch is followed before giving up. */
private const val MAX_DISPATCH_DEPTH = 8

/** How far back the constant handed to the factory is looked for. */
private const val CONSTANT_LOOKBACK = 4

/** How far past a switch target the call it makes is looked for. */
private const val TARGET_LOOKAHEAD = 4

/**
 * The one class that reads the setting [key] behind a lazy value, and the method on it of the
 * shape [gate] accepts.
 *
 * <p>TikTok generates a small class per AB-backed switch: one static field holding a lazy value,
 * a `<clinit>` that builds it, and one getter that reads it and answers. Hundreds of them are the
 * same shape, so what identifies one is the settings key its lazy value reads, and that key is a
 * plain string TikTok wrote.
 *
 * <p>Getting to it takes one of three routes, all of which this follows. The `<clinit>` can hold
 * the key itself. It can build a lambda of its own class, in which case the key is in that class's
 * one no-argument method. Or R8 has merged the lambda into a shared group, in which case the
 * `<clinit>` asks the group's factory for the instance numbered n, and the group's entry point is
 * a switch on that number which reaches the one body belonging to it. R8 splits a large switch
 * into a tree of them, so the switch is followed until what it reaches is not another switch.
 *
 * <p>The number is followed rather than the leaf's name. On the builds seen, the leaf of index n
 * is called `invoke$n`, which would make this a one-line lookup and would be one more thing R8
 * chooses.
 */
internal fun BytecodePatchContext.resolveLazyAbGate(
    what: String,
    key: String,
    gate: (Method) -> Boolean,
): MutableMethod {
    val found = mutableListOf<Pair<ClassDef, Method>>()
    classDefForEach { classDef ->
        // The shape first: it costs one pass over the class's own method signatures and leaves a
        // handful of candidates for the walk that follows to read instructions for.
        val method = classDef.methods.singleOrNull(gate) ?: return@classDefForEach
        val clinit = classDef.methods.firstOrNull {
            it.name == "<clinit>" && it.implementation != null
        } ?: return@classDefForEach
        if (!readsSettingsKey(clinit, key)) return@classDefForEach
        found += classDef to method
    }
    if (found.size != 1) {
        throw PatchException(
            "$what: expected one class whose lazily read setting is \"$key\", found ${found.size}.",
        )
    }
    val (classDef, method) = found.single()
    return mutableClassDefBy(classDef).findMutableMethodOf(method)
}

/** Whether the static initialiser reaches [key], directly or through the lambda it builds. */
private fun BytecodePatchContext.readsSettingsKey(clinit: Method, key: String): Boolean {
    if (clinit.holdsString(key)) return true
    val instructions = clinit.implementation?.instructions?.toList() ?: return false
    instructions.forEachIndexed { index, instruction ->
        when (instruction.opcode) {
            Opcode.NEW_INSTANCE -> {
                val built = instruction.getReference<TypeReference>()?.type ?: return@forEachIndexed
                val body = classDefByOrNull(built)?.methods?.firstOrNull {
                    it.name != "<init>" && it.parameterTypes.none() && it.implementation != null
                }
                if (body?.holdsString(key) == true) return true
            }
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE -> {
                val factory = instruction.getReference<MethodReference>() ?: return@forEachIndexed
                if (factory.name != GROUP_FACTORY) return@forEachIndexed
                val number = constantBefore(instructions, index) ?: return@forEachIndexed
                val body = groupBody(factory.definingClass, number)
                if (body?.holdsString(key) == true) return true
            }
            else -> Unit
        }
    }
    return false
}

/** The constant the factory is handed, which is loaded just before the call. */
private fun constantBefore(instructions: List<Instruction>, at: Int): Int? {
    for (index in (at - 1) downTo maxOf(0, at - CONSTANT_LOOKBACK)) {
        val instruction = instructions[index]
        if (instruction is WideLiteralInstruction && instruction.opcode.name.startsWith("const")) {
            return instruction.wideLiteral.toInt()
        }
    }
    return null
}

/** The body the group's entry point dispatches to for [number]. */
private fun BytecodePatchContext.groupBody(groupType: String, number: Int): Method? {
    val group = classDefByOrNull(groupType) ?: return null
    var at = group.methods.firstOrNull { it.parameterTypes.none() && it.dispatchesOnIndex() }
    repeat(MAX_DISPATCH_DEPTH) {
        val current = at ?: return null
        val next = current.dispatchTarget(group, number) ?: return null
        if (!next.dispatchesOnIndex()) return next
        at = next
    }
    return null
}

/** Whether the method opens by switching on the group's own lambda number. */
internal fun Method.dispatchesOnIndex(): Boolean {
    val instructions = implementation?.instructions?.toList() ?: return false
    if (instructions.size < 2) return false
    if (instructions[0].opcode != Opcode.IGET) return false
    return instructions[1].opcode == Opcode.PACKED_SWITCH ||
        instructions[1].opcode == Opcode.SPARSE_SWITCH
}

/** The method the switch in this body calls for [number]. */
internal fun Method.dispatchTarget(group: ClassDef, number: Int): Method? {
    val instructions = implementation?.instructions?.toList() ?: return null
    val addresses = IntArray(instructions.size)
    var address = 0
    instructions.forEachIndexed { index, instruction ->
        addresses[index] = address
        address += instruction.codeUnits
    }
    instructions.forEachIndexed { index, instruction ->
        if (instruction !is Instruction31t) return@forEachIndexed
        if (instruction.opcode != Opcode.PACKED_SWITCH &&
            instruction.opcode != Opcode.SPARSE_SWITCH
        ) {
            return@forEachIndexed
        }
        val switchAddress = addresses[index]
        val payload = instructions.getOrNull(
            addresses.indexOf(switchAddress + instruction.codeOffset),
        ) as? SwitchPayload ?: return@forEachIndexed
        val element = payload.switchElements.firstOrNull { it.key == number }
            ?: return@forEachIndexed
        val target = addresses.indexOf(switchAddress + element.offset)
        if (target < 0) return@forEachIndexed
        for (candidate in target until minOf(instructions.size, target + TARGET_LOOKAHEAD)) {
            val call = instructions[candidate]
            if (call.opcode != Opcode.INVOKE_STATIC && call.opcode != Opcode.INVOKE_STATIC_RANGE) {
                continue
            }
            val reference = call.getReference<MethodReference>() ?: continue
            if (reference.definingClass != group.type) continue
            return group.methods.firstOrNull { it.name == reference.name }
        }
    }
    return null
}

/**
 * Whether the method is one of these gates' getters: read the lazy value, unwrap the number it
 * holds, answer from it.
 *
 * <p>The key says which class; this says the method on it is the one that reads the setting
 * rather than some other member of the same signature.
 */
internal fun Method.isLazyAbRead(): Boolean {
    val calls = implementation?.instructions
        ?.mapNotNull { it.getReference<MethodReference>() }
        ?.toList() ?: return false
    val readsValue = calls.any {
        it.name == "getValue" && it.returnType == "Ljava/lang/Object;" && it.parameterTypes.none()
    }
    val unwrapsNumber = calls.any {
        it.definingClass == "Ljava/lang/Number;" && it.name == "intValue" && it.returnType == "I"
    }
    return readsValue && unwrapsNumber
}

/** Whether any string constant of the method is exactly this one. */
private fun Method.holdsString(value: String) =
    implementation?.instructions?.any {
        it.getReference<StringReference>()?.string == value
    } == true
