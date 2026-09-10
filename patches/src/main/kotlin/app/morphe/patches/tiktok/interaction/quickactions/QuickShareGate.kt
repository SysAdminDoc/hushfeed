/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val IM_SHARE_SERVICE =
    "Lcom/ss/android/ugc/aweme/im/sharepanel/impl/service/IMShareService;"

/**
 * The gate that says whether a long press on the share button opens quick share.
 *
 * <p>It was `LX/0BJV;` and it is `LX/09Nm;` on 46.7.3 and `LX/09ON;` on 46.8.3. There is nothing
 * in the class to recognise: it is one of hundreds of two-member gates TikTok generates around a
 * lazily read AB integer, and the key it reads sits behind a lambda R8 has merged into a shared
 * group, where following it means decoding a packed switch that is numbered differently on each
 * build.
 *
 * <p>What names it is who asks. `IMShareService` keeps its own name and calls exactly one static
 * `()I` on every one of the three builds, and it is this gate.
 */
internal fun BytecodePatchContext.resolveLongPressQuickShareGate(): MutableMethod {
    val service = classDefByOrNull(IM_SHARE_SERVICE)
        ?: throw PatchException("Long press quick share: this build has no $IM_SHARE_SERVICE.")
    val called = service.methods
        .flatMap { it.implementation?.instructions?.toList() ?: emptyList() }
        .filter { it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_STATIC_RANGE }
        .mapNotNull { it.getReference<MethodReference>() }
        .filter { it.returnType == "I" && it.parameterTypes.isEmpty() }
        .distinctBy { "${it.definingClass}->${it.name}" }
    if (called.size != 1) {
        throw PatchException(
            "Long press quick share: expected $IM_SHARE_SERVICE to call one static ()I gate, " +
                "found ${called.size}.",
        )
    }
    val gate = called.single()
    val method = mutableClassDefBy(gate.definingClass).methods.singleOrNull {
        it.name == gate.name && it.returnType == "I" && it.parameterTypes.isEmpty()
    } ?: throw PatchException(
        "Long press quick share: ${gate.definingClass} has no ${gate.name}()I to override.",
    )
    // Who calls it says which method; this says it is the kind of method the patch rewrites. A
    // build that stopped calling the gate from here but left any other static ()I would otherwise
    // resolve to that one and have its return overridden.
    val references = method.implementation?.instructions
        ?.mapNotNull { it.getReference<MethodReference>() }
        .orEmpty()
    val readsLazyValue = references.any {
        it.name == "getValue" && it.returnType == "Ljava/lang/Object;" && it.parameterTypes.isEmpty()
    }
    val unwrapsInt = references.any {
        it.definingClass == "Ljava/lang/Number;" && it.name == "intValue" && it.returnType == "I"
    }
    if (!readsLazyValue || !unwrapsInt) {
        throw PatchException(
            "Long press quick share: ${gate.definingClass}->${gate.name} does not read a lazy " +
                "value and unwrap it as an int, so it is not the gate.",
        )
    }
    return method
}
