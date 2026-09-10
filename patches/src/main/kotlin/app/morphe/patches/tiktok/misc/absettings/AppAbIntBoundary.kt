/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.absettings

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.objectIn
import app.morphe.patches.tiktok.shared.valueIn
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.util.WeakHashMap

private const val STRING = "Ljava/lang/String;"

/**
 * A method's return type, parameter list and whether it is static, which is what tells the AB
 * getters apart.
 *
 * <p>The static bit is not decoration. Every boundary hook names the register its key arrives in,
 * and an instance method's `p0` is `this`, so the same parameter list is `p3` on one and `p2` on
 * the other. With the shape as the only anchor left for the app AB class, a static getter of the
 * same signature would be accepted and then read a boolean as the key.
 */
internal data class MethodShape(
    val returnType: String,
    val parameters: List<String>,
    val isStatic: Boolean = false,
)

internal fun Method.shape() = MethodShape(
    returnType,
    parameterTypes.map(CharSequence::toString),
    AccessFlags.STATIC.value and accessFlags != 0,
)

/**
 * The typed getters TikTok's app AB class carries, one per value type, plus the raw one. Each is
 * a different shape, and no other class in the build carries all seven, so together they name
 * the class without its name. On 46.2.3 it was `LX/0BYX;`, which was written into the patches
 * and became `LX/09h1;` on 46.7.3 and `LX/09cY;` on 46.8.3. All seven are instance methods on
 * all three, which is what puts the key one register further along than the parameter list says.
 */
internal val APP_AB_BOOLEAN = MethodShape("Z", listOf("I", STRING, "Z", "Z"))
internal val APP_AB_DOUBLE = MethodShape("D", listOf("D", "I", STRING, "Z"))
internal val APP_AB_FLOAT = MethodShape("F", listOf("I", STRING, "Z", "F"))
internal val APP_AB_INT = MethodShape("I", listOf("I", "I", STRING, "Z"))
internal val APP_AB_LONG = MethodShape("J", listOf("I", "J", STRING, "Z"))
internal val APP_AB_STRING = MethodShape(STRING, listOf("I", STRING, STRING, "Z"))
internal val APP_AB_RAW = MethodShape("Ljava/lang/Object;", listOf(STRING, "Z"))
internal val APP_AB_SHAPES = listOf(
    APP_AB_BOOLEAN, APP_AB_DOUBLE, APP_AB_FLOAT, APP_AB_INT, APP_AB_LONG, APP_AB_STRING, APP_AB_RAW,
)

/** The int getter's key sits after `this` and two ints, which [APP_AB_INT] holds it to. */
internal const val APP_AB_INT_KEY_REGISTER = "p3"

private val resolved = WeakHashMap<BytecodePatchContext, String>()

/**
 * The type of the app AB class in the build being patched, found by the getters it carries.
 *
 * <p>Walked once per patch run and remembered, because three patches ask and the walk visits
 * every class.
 */
internal fun BytecodePatchContext.appAbClassType(): String = synchronized(resolved) {
    resolved.getOrPut(this) {
        val carriers = mutableListOf<ClassDef>()
        classDefForEach { classDef ->
            val shapes = classDef.methods.mapTo(HashSet()) { it.shape() }
            if (APP_AB_SHAPES.all { it in shapes }) carriers += classDef
        }
        if (carriers.size != 1) {
            throw PatchException(
                "App AB boundary: expected one class carrying all seven AB getter shapes, " +
                    "found ${carriers.size}.",
            )
        }
        carriers.single().type
    }
}

internal fun BytecodePatchContext.appAbClass(): MutableClass = mutableClassDefBy(appAbClassType())

/**
 * The one method of the class with this shape. Two of them would mean the shape has stopped
 * identifying the getter, and that is a failure to say rather than a coin toss between them.
 */
internal fun MutableClass.methodOfShape(shape: MethodShape, what: String): MutableMethod {
    val matches = methods.filter { it.shape() == shape }
    if (matches.size != 1) {
        throw PatchException("$what: expected one ${shape.returnType}${shape.parameters} on $type, found ${matches.size}.")
    }
    return matches.single()
}

internal fun BytecodePatchContext.hookAppAbIntBoundary(
    extensionDescriptor: String,
    extensionMethod: String,
) {
    appAbClass().methodOfShape(APP_AB_INT, "App AB int boundary").apply {
        implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN }
            .map { it.index }
            .asReversed()
            .forEach { returnIndex ->
                val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                // The key register is a parameter, which a plain invoke cannot name on a host
                // method with enough locals. Behind a constant, so no search for the literal
                // finds it.
                val call = callThroughLocals(
                    "App AB int boundary",
                    "invoke-static",
                    "$extensionDescriptor->$extensionMethod(Ljava/lang/String;I)I",
                    objectIn(APP_AB_INT_KEY_REGISTER),
                    valueIn("v$register"),
                )
                addInstructions(returnIndex, "$call\nmove-result v$register")
            }
    }
}
