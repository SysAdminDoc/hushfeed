/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.shared

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.numberOfParameterRegisters

/** How much of the frame an argument takes, and how it has to be copied. */
internal enum class Width(val move: String, val registers: Int) {
    OBJECT("move-object/from16", 1),
    VALUE("move/from16", 1),

    /** A long or a double: two consecutive registers, named by the first. */
    WIDE("move-wide/from16", 2),
}

/** One argument of a call: where it is, and what it holds. */
internal data class Argument(val register: String, val width: Width)

internal fun objectIn(register: String) = Argument(register, Width.OBJECT)

internal fun valueIn(register: String) = Argument(register, Width.VALUE)

internal fun wideIn(register: String) = Argument(register, Width.WIDE)

/** The four bit register field of a format 35c invoke reaches v0 to v15 and no further. */
private const val HIGHEST_35C_REGISTER = 15

/** Where an argument actually sits. `pN` is the Nth register above the locals. */
private fun Argument.number(locals: Int): Int {
    val index = register.drop(1).toIntOrNull()
        ?: throw PatchException("Not a register: $register")
    return if (register.startsWith("p")) locals + index else index
}

/** The register after this one, in the same numbering: the second half of a wide argument. */
private fun neighbour(register: String): String {
    val index = register.drop(1).toIntOrNull()
        ?: throw PatchException("Not a register: $register")
    return register.take(1) + (index + 1)
}

private fun Argument.names(): List<String> =
    if (width == Width.WIDE) listOf(register, neighbour(register)) else listOf(register)

/**
 * Smali calling [target] with [arguments], whatever frame the host method has.
 *
 * A plain invoke is format 35c, which names each register in four bits and so cannot reach
 * past v15. A parameter register of a large method sits well above that, and dexlib's failure
 * to encode one arrives as an unrelated-looking build error.
 *
 * While every argument fits, this is that plain invoke and nothing changes. Only the arguments
 * that do not fit are copied down, into local registers no other argument is using, so a frame
 * that could always encode the call is left exactly as it was. A long or a double is copied as
 * a pair, because half of one is not a number. A frame with too few locals to hold the copies
 * fails the build, rather than shipping a method that writes over its own arguments.
 *
 * Injecting at a point where a local still holds something the method needs afterwards would
 * lose it. Every caller injects either at index 0, where no local is live, or immediately
 * before the return that ends the method.
 */
internal fun MutableMethod.callThroughLocals(
    patch: String,
    invoke: String,
    target: String,
    vararg arguments: Argument,
): String = callThroughLocals(patch, invoke, target, true, *arguments)

/**
 * As above, where {@code mayStage} says whether a local may be written to bring an argument into
 * range.
 *
 * <p>Staging is only safe where no local is live: at index 0, or immediately before the return
 * that ends the method. The free local it picks is one no other argument is using, which is not
 * the same as one nothing else is holding. A caller injecting anywhere else passes false and gets
 * a build failure rather than an instruction written over a value the host still needs.
 */
internal fun MutableMethod.callThroughLocals(
    patch: String,
    invoke: String,
    target: String,
    mayStage: Boolean,
    vararg arguments: Argument,
): String {
    val body = implementation ?: throw PatchException("$patch: $name has no implementation")
    val locals = body.registerCount - numberOfParameterRegisters

    val numbers = arguments.map { it.number(locals) }
    val fits = arguments.indices.all {
        numbers[it] + arguments[it].width.registers - 1 <= HIGHEST_35C_REGISTER
    }
    if (fits) {
        return "$invoke {${arguments.flatMap { it.names() }.joinToString(", ")}}, $target"
    }

    if (!mayStage) {
        throw PatchException(
            "$patch: $name holds an argument above v15 and this call site cannot stage it, " +
                "because it injects where the host's own locals are live.",
        )
    }

    // Free means a local this call is not already reading from. Writing one is safe here
    // because every argument is read into the call on the very next instruction.
    val taken = mutableSetOf<Int>()
    arguments.indices
        .filter { numbers[it] + arguments[it].width.registers - 1 <= HIGHEST_35C_REGISTER }
        .forEach { index ->
            for (offset in 0 until arguments[index].width.registers) taken.add(numbers[index] + offset)
        }

    val moves = mutableListOf<String>()
    val named = mutableListOf<String>()
    for (index in arguments.indices) {
        val argument = arguments[index]
        if (numbers[index] + argument.width.registers - 1 <= HIGHEST_35C_REGISTER) {
            named.addAll(argument.names())
            continue
        }
        val ceiling = minOf(locals, HIGHEST_35C_REGISTER + 1) - argument.width.registers
        val free = (0..ceiling).firstOrNull { start ->
            (start until start + argument.width.registers).none { it in taken }
        } ?: throw PatchException(
            "$patch: $name has $locals local registers, too few to bring " +
                "${arguments.size} arguments below v16 without writing over one of them.",
        )
        for (offset in 0 until argument.width.registers) taken.add(free + offset)
        moves.add("${argument.width.move} v$free, ${argument.register}")
        named.add("v$free")
        if (argument.width == Width.WIDE) named.add("v${free + 1}")
    }
    return (moves + "$invoke {${named.joinToString(", ")}}, $target").joinToString("\n")
}
