/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.shared

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.numberOfParameterRegisters

/** One argument of a call: where it is, and whether it holds a reference. */
internal data class Argument(val register: String, val isObject: Boolean)

internal fun objectIn(register: String) = Argument(register, true)

internal fun valueIn(register: String) = Argument(register, false)

/** The four bit register field of a format 35c invoke reaches v0 to v15 and no further. */
private const val HIGHEST_35C_REGISTER = 15

/** Where an argument actually sits. `pN` is the Nth register above the locals. */
private fun Argument.number(locals: Int): Int {
    val index = register.drop(1).toIntOrNull()
        ?: throw PatchException("Not a register: $register")
    return if (register.startsWith("p")) locals + index else index
}

/**
 * Smali calling [target] with [arguments], whatever frame the host method has.
 *
 * A plain invoke is format 35c, which names each register in four bits and so cannot reach
 * past v15. A parameter register of a large method sits well above that, and dexlib's failure
 * to encode one arrives as an unrelated-looking build error.
 *
 * While every argument fits, this is that plain invoke and nothing changes. Only the arguments
 * that do not fit are copied down, into local registers no other argument is using, so a frame
 * that could always encode the call is left exactly as it was. A frame with too few locals to
 * hold the copies fails the build, rather than shipping a method that writes over its own
 * arguments.
 */
internal fun MutableMethod.callThroughLocals(
    patch: String,
    invoke: String,
    target: String,
    vararg arguments: Argument,
): String {
    val body = implementation ?: throw PatchException("$patch: $name has no implementation")
    val locals = body.registerCount - numberOfParameterRegisters

    val numbers = arguments.map { it.number(locals) }
    if (numbers.all { it <= HIGHEST_35C_REGISTER }) {
        return "$invoke {${arguments.joinToString(", ") { it.register }}}, $target"
    }

    // Free means a local this call is not already reading from. Writing one is safe here
    // because every argument is read into the call on the very next instruction.
    val taken = numbers.filter { it <= HIGHEST_35C_REGISTER }.toMutableSet()
    val moves = mutableListOf<String>()
    val named = arguments.mapIndexed { index, argument ->
        if (numbers[index] <= HIGHEST_35C_REGISTER) return@mapIndexed argument.register
        val free = (0 until minOf(locals, HIGHEST_35C_REGISTER + 1)).firstOrNull { it !in taken }
            ?: throw PatchException(
                "$patch: $name has $locals local registers, too few to bring " +
                    "${arguments.size} arguments below v16 without writing over one of them.",
            )
        taken.add(free)
        val move = if (argument.isObject) "move-object/from16" else "move/from16"
        moves.add("$move v$free, ${argument.register}")
        "v$free"
    }
    return (moves + "$invoke {${named.joinToString(", ")}}, $target").joinToString("\n")
}
