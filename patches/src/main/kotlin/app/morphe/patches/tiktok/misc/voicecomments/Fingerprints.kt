/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe/commit/0e4a6e1d
 */
package app.morphe.patches.tiktok.misc.voicecomments

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The gate that decides whether the voice comment entry points are built. It carries no string,
 * so the obfuscated owner is the only name to hold on to; the shape check below is what stops a
 * renamed class of the same name matching something unrelated. It reads a settings value and
 * turns it into an int, and both of those calls have to be there.
 */
internal object VoiceCommentPublishGateFingerprint : Fingerprint(
    returnType = "Z",
    parameters = emptyList(),
    custom = { method, classDef ->
        classDef.type == "LX/0AkX;" &&
            method.name == "LIZ" &&
            method.implementation?.instructions?.let { instructions ->
                instructions.any { instruction ->
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "LX/01xP;" &&
                            reference.name == "getValue" &&
                            reference.parameterTypes.isEmpty() &&
                            reference.returnType == "Ljava/lang/Object;"
                    } == true
                } && instructions.any { instruction ->
                    instruction.getReference<MethodReference>()?.let { reference ->
                        reference.definingClass == "Ljava/lang/Number;" &&
                            reference.name == "intValue" &&
                            reference.parameterTypes.isEmpty() &&
                            reference.returnType == "I"
                    } == true
                }
            } == true
    },
)
