package app.morphe.patches.tiktok.misc.follow

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val USER_SERVICE_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/userservice/api/IUserService;"
private const val COMMON_FOLLOW_API_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/userservice/CommonFollowApi;"
private const val JEDI_FOLLOW_API_DESCRIPTOR = "Lcom/ss/android/ugc/aweme/userservice/jedi/model/JediFollowApi;"
private const val CALL_SERVER_INTERCEPTOR_DESCRIPTOR = "Lcom/bytedance/retrofit2/CallServerInterceptor;"
private const val NETWORK_EXECUTE_CALL_METHOD =
    "com_bytedance_retrofit2_CallServerInterceptor_com_ss_android_ugc_aweme_feed_lancet_NetworkUtilsLancet_executeCall"
private const val NETWORK_PARSE_RESPONSE_METHOD =
    "com_bytedance_retrofit2_CallServerInterceptor_com_ss_android_ugc_aweme_feed_lancet_NetworkUtilsLancet_parseResponse"
private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/follow/FollowDiagnostics;"

private data class FollowCallPatch(
    val index: Int,
    val beforeInstructions: String,
)

@Suppress("unused")
val followDiagnosticsPatch = bytecodePatch(
    name = "Follow diagnostics",
    description = "Reads what the server said about a follow. A follow TikTok turns down comes " +
        "back looking like a success, so this reports the refusal and its reason once per session " +
        "and, with diagnostic logging on, writes the whole exchange to the report. Supports TikTok 46.2.3.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        val patchesByMethod = linkedMapOf<Method, ArrayDeque<FollowCallPatch>>()

        classDefForEach { classDef ->
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue

                if (classDef.type == COMMON_FOLLOW_API_DESCRIPTOR && method.name == "LIZ") {
                    val mutableMethod = mutableClassDefBy(classDef.type).findMutableMethodOf(method)
                    patchCommonFollowApi(mutableMethod)
                    continue
                }

                if (classDef.type == CALL_SERVER_INTERCEPTOR_DESCRIPTOR && method.name == NETWORK_EXECUTE_CALL_METHOD) {
                    val mutableMethod = mutableClassDefBy(classDef.type).findMutableMethodOf(method)
                    patchNetworkExecuteCall(mutableMethod)
                    continue
                }

                if (classDef.type == CALL_SERVER_INTERCEPTOR_DESCRIPTOR && method.name == NETWORK_PARSE_RESPONSE_METHOD) {
                    val mutableMethod = mutableClassDefBy(classDef.type).findMutableMethodOf(method)
                    patchNetworkParseResponse(mutableMethod)
                    continue
                }

                implementation.instructions.forEachIndexed { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_INTERFACE &&
                        instruction.opcode != Opcode.INVOKE_INTERFACE_RANGE
                    ) {
                        return@forEachIndexed
                    }

                    val methodReference = instruction.getReference<MethodReference>() ?: return@forEachIndexed

                    val beforeInstructions = when (methodReference.definingClass) {
                        USER_SERVICE_DESCRIPTOR -> when (methodReference.name) {
                            "LJ" -> simpleFollowRequestInstructions(instruction)
                            "LJFF" -> detailedFollowRequestInstructions(instruction)
                            else -> null
                        }

                        JEDI_FOLLOW_API_DESCRIPTOR -> when (methodReference.name) {
                            "followWithRetrofitPost" -> jediFollowRequestInstructions(instruction)
                            else -> null
                        }

                        else -> null
                    } ?: return@forEachIndexed

                    patchesByMethod.getOrPut(method) { ArrayDeque() }
                        .add(FollowCallPatch(index, beforeInstructions))
                }
            }
        }

        patchesByMethod.forEach { (method, patches) ->
            val mutableMethod = mutableClassDefBy(method.definingClass).findMutableMethodOf(method)

            while (patches.isNotEmpty()) {
                val patch = patches.removeLast()
                val moveResult = mutableMethod.implementation!!.instructions
                    .getOrNull(patch.index + 1)
                    ?.takeIf { it.opcode == Opcode.MOVE_RESULT_OBJECT } as? OneRegisterInstruction

                mutableMethod.addInstructions(
                    patch.index + 2,
                    moveResult?.let { result ->
                        if (patch.beforeInstructions.contains("logJediFollowRequest")) {
                            "invoke-static/range {v${result.registerA} .. v${result.registerA}}, " +
                                "$EXTENSION_CLASS_DESCRIPTOR->logFollowStream(Ljava/lang/Object;)V"
                        } else {
                            "invoke-static/range {v${result.registerA} .. v${result.registerA}}, " +
                                "$EXTENSION_CLASS_DESCRIPTOR->logFollowResult(Ljava/lang/Object;)V"
                        }
                    } ?: "nop",
                )

                mutableMethod.addInstructions(patch.index, patch.beforeInstructions)
            }
        }
    }
}

/**
 * Both network lancets have the same three anchors: the read of the request being sent, the
 * result of the call TikTok's own method makes, and the exception when it throws. Every register
 * is read off the instruction it belongs to, so the layout of the surrounding method is free to
 * move; only the anchors themselves have to be there, and a missing one fails the build.
 */
private fun patchNetworkLancet(
    method: MutableMethod,
    twinName: String,
    requestLogger: String?,
    responseLogger: String,
    throwableLogger: String,
) {
    val implementation = method.implementation
        ?: throw PatchException("Follow diagnostics: ${method.name} has no body.")
    val instructions = implementation.instructions.toList()

    val requestIndex = instructions.indexOfFirst { instruction ->
        instruction.opcode == Opcode.IGET_OBJECT &&
            instruction.getReference<FieldReference>()?.name == "mOriginalRequest"
    }
    if (requestIndex < 0) {
        throw PatchException("Follow diagnostics: mOriginalRequest is not read in ${method.name}.")
    }
    val requestRegister = (instructions[requestIndex] as OneRegisterInstruction).registerA

    val twinIndex = instructions.indexOfFirst { instruction ->
        instruction.opcode == Opcode.INVOKE_VIRTUAL &&
            instruction.getReference<MethodReference>()?.name == twinName
    }
    if (twinIndex < 0) {
        throw PatchException("Follow diagnostics: $twinName is not called in ${method.name}.")
    }
    val responseInstruction = instructions.getOrNull(twinIndex + 1)
    if (responseInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "Follow diagnostics: $twinName in ${method.name} is followed by " +
                "${responseInstruction?.opcode?.name ?: "nothing"}, not a move-result-object, " +
                "so there is no response to read.",
        )
    }
    val responseRegister = (responseInstruction as OneRegisterInstruction).registerA

    val exceptionIndex = instructions.indexOfFirst { it.opcode == Opcode.MOVE_EXCEPTION }
    if (exceptionIndex < 0) {
        throw PatchException("Follow diagnostics: ${method.name} catches nothing to report.")
    }
    val throwableRegister = (instructions[exceptionIndex] as OneRegisterInstruction).registerA

    // Highest index first. Every index above was read from the untouched method, and inserting
    // at a lower one moves all of them; this held only because the catch handler happens to sit
    // last on this build.
    listOfNotNull(
        (exceptionIndex + 1) to
            "invoke-static {v$requestRegister, v$throwableRegister}, $throwableLogger",
        (twinIndex + 2) to
            "invoke-static {v$requestRegister, v$responseRegister}, $responseLogger",
        requestLogger?.let {
            (requestIndex + 1) to "invoke-static/range {v$requestRegister .. v$requestRegister}, $it"
        },
    ).sortedByDescending { it.first }.forEach { (index, instruction) ->
        method.addInstructions(index, instruction)
    }
}

private fun patchNetworkParseResponse(method: MutableMethod) = patchNetworkLancet(
    method,
    twinName = "com_bytedance_retrofit2_CallServerInterceptor__parseResponse\$___twin___",
    // The parse lancet logs the request only alongside a response or a throwable: on its own it
    // says nothing the execute lancet has not already reported for the same request.
    requestLogger = null,
    responseLogger = "$EXTENSION_CLASS_DESCRIPTOR->logParsedResponse(Ljava/lang/Object;Ljava/lang/Object;)V",
    throwableLogger = "$EXTENSION_CLASS_DESCRIPTOR->logParseThrowable(Ljava/lang/Object;Ljava/lang/Throwable;)V",
)

/**
 * The ten parameters the extension's logCommonFollowRequest reads, in order. LIZ is matched by
 * name alone, so this is what says the method found is the one meant: another LIZ with a
 * different shape would otherwise be handed ten registers holding something else.
 */
private val COMMON_FOLLOW_PARAMETERS = listOf(
    "I", "I", "I", "I",
    "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;",
    "Ljava/lang/String;", "Ljava/util/Map;",
)

private fun patchCommonFollowApi(method: MutableMethod) {
    val implementation = method.implementation
        ?: throw PatchException("Follow diagnostics: CommonFollowApi.LIZ has no body.")

    val parameters = method.parameters.map { it.type }
    if (!AccessFlags.STATIC.isSet(method.accessFlags) || parameters != COMMON_FOLLOW_PARAMETERS) {
        throw PatchException(
            "Follow diagnostics: CommonFollowApi.LIZ is not the follow request this reads. " +
                "Expected a static method taking $COMMON_FOLLOW_PARAMETERS, found " +
                "${if (AccessFlags.STATIC.isSet(method.accessFlags)) "a static" else "an instance"} " +
                "method taking $parameters.",
        )
    }

    // Where the parameters actually sit. On 46.2.3 that is v5 to v14 of fifteen registers, and
    // it moves the moment TikTok's own method needs one more local.
    val firstParameter = implementation.registerCount - method.numberOfParameterRegisters
    val lastParameter = implementation.registerCount - 1

    val returnIndex = implementation.instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    if (returnIndex < 0) {
        throw PatchException("Follow diagnostics: CommonFollowApi.LIZ returns no object to read.")
    }
    val returnRegister = (implementation.instructions.elementAt(returnIndex) as OneRegisterInstruction).registerA

    method.addInstructions(
        returnIndex,
        "invoke-static/range {v$returnRegister .. v$returnRegister}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logFollowResult(Ljava/lang/Object;)V",
    )

    method.addInstructions(
        0,
        "invoke-static/range {v$firstParameter .. v$lastParameter}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logCommonFollowRequest(IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V",
    )
}

private fun patchNetworkExecuteCall(method: MutableMethod) = patchNetworkLancet(
    method,
    twinName = "com_bytedance_retrofit2_CallServerInterceptor__executeCall\$___twin___",
    requestLogger = "$EXTENSION_CLASS_DESCRIPTOR->logNetworkRequest(Ljava/lang/Object;)V",
    responseLogger = "$EXTENSION_CLASS_DESCRIPTOR->logNetworkResponse(Ljava/lang/Object;Ljava/lang/Object;)V",
    throwableLogger = "$EXTENSION_CLASS_DESCRIPTOR->logNetworkThrowable(Ljava/lang/Object;Ljava/lang/Throwable;)V",
)

private fun simpleFollowRequestInstructions(instruction: Instruction): String? {
    val actionRegister = instruction.argumentRegister(1) ?: return null
    val uidRegister = instruction.argumentRegister(2) ?: return null
    val secUidRegister = instruction.argumentRegister(3) ?: return null

    return if (instruction is Instruction3rc) {
        "invoke-static/range {v$actionRegister .. v$secUidRegister}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logSimpleFollowRequest(ILjava/lang/String;Ljava/lang/String;)V"
    } else {
        if (listOf(actionRegister, uidRegister, secUidRegister).any { it > 15 }) return null

        "invoke-static {v$actionRegister, v$uidRegister, v$secUidRegister}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logSimpleFollowRequest(ILjava/lang/String;Ljava/lang/String;)V"
    }
}

private fun detailedFollowRequestInstructions(instruction: Instruction): String? {
    val firstArgumentRegister = instruction.argumentRegister(1) ?: return null
    val lastArgumentRegister = instruction.argumentRegister(10) ?: return null

    if (instruction is Instruction3rc) {
        return "invoke-static/range {v$firstArgumentRegister .. v$lastArgumentRegister}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logDetailedFollowRequest(IIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V"
    }

    return null
}

private fun jediFollowRequestInstructions(instruction: Instruction): String? {
    val firstArgumentRegister = instruction.argumentRegister(1) ?: return null
    val lastArgumentRegister = instruction.argumentRegister(11) ?: return null

    if (instruction is Instruction3rc) {
        return "invoke-static/range {v$firstArgumentRegister .. v$lastArgumentRegister}, " +
            "$EXTENSION_CLASS_DESCRIPTOR->logJediFollowRequest(Ljava/lang/String;Ljava/lang/String;IILjava/lang/Integer;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;)V"
    }

    return null
}

private fun Instruction.argumentRegister(argumentIndex: Int): Int? =
    when (this) {
        is Instruction35c -> when (argumentIndex) {
            0 -> registerC
            1 -> registerD
            2 -> registerE
            3 -> registerF
            4 -> registerG
            else -> null
        }

        is Instruction3rc -> startRegister + argumentIndex
        else -> null
    }
