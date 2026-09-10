/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.videofit

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/interaction/VideoFit;"

/** The whole `com.ss.android.ugc.aweme.videoadaption` package kept its names, this one included. */
private const val RESULT = "Lcom/ss/android/ugc/aweme/videoadaption/adaptionparams/VideoAdaptionResult;"

/**
 * The story cell's way of putting the chosen size on a view: the result writes itself. It reads
 * its own width and height, writes them into the view's layout parameters and then applies the
 * offsets that go with a crop, so changing its fields before it runs changes all three.
 */
private object SaveAdaptionResultFingerprint : Fingerprint(
    definingClass = RESULT,
    name = "saveResultInner",
    returnType = "V",
    parameters = listOf("Landroid/view/View;"),
)

/**
 * The feed cell's way, which is the one the For You feed takes. The cell's adaption component
 * hands the result and the video view to a static helper that writes the size and the offsets
 * into the view, and to a second one that says whether the view already matches the result.
 * Both live in a file facade R8 names per build, so each is found by its parameter list, which
 * names the result class and is carried by one method in the app on every build seen.
 */
private object ApplyAdaptionResultFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/view/View;", RESULT, "Lkotlin/jvm/functions/Function0;"),
    custom = { method, _ -> method.isAdaptionHelper(3) },
)

private object AdaptionAppliedFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/view/View;", RESULT),
    custom = { method, _ -> method.isAdaptionHelper(2) },
)

/**
 * Static, exactly this many parameters, and a body that reads the result's size. The parameter
 * list is matched by prefix, so the count and the reads are what hold the shape to the helpers.
 */
private fun Method.isAdaptionHelper(parameters: Int): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    if (parameterTypes.size != parameters) return false
    val calls = implementation?.instructions
        ?.mapNotNull { it.getReference<MethodReference>() }
        ?.filter { it.definingClass == RESULT }
        ?.map { it.name }
        ?.toSet() ?: return false
    return "getWidth" in calls && "getHeight" in calls
}

@Suppress("unused")
val videoFitPatch = bytecodePatch(
    name = "Fit the video to the screen",
    description = "Puts the whole of a vertical video on screen instead of cropping it to " +
        "the window. On a 9:16 phone nothing changes, because the video already fills it. " +
        "On a Fold opened up, a squarer phone or a split view the sides or the ends stop " +
        "being cut off.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        // The feed cell. p0 is the view and p1 the result, in a static method; the result is
        // swapped for the fitted copy before the helper reads it, and cast back to what the
        // signature says it is so the reads that follow verify.
        for (helper in listOf(ApplyAdaptionResultFingerprint, AdaptionAppliedFingerprint)) {
            helper.method.addInstructions(
                0,
                """
                    invoke-static/range { p0 .. p1 }, ${EXTENSION}->fitted(Landroid/view/View;Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object p1
                    check-cast p1, $RESULT
                """,
            )
        }

        // The story cell, which sizes the view from inside the result.
        SaveAdaptionResultFingerprint.method.apply {
            // The result's own class, so an iput on its final fields is something the runtime
            // verifier allows. Nothing outside it can write them, which is why the size is
            // changed here and not in the extension.
            val result = definingClass
            val registers = implementation!!.registerCount
            check(registers - numberOfParameterRegisters >= 1) {
                "Fit video to the screen: saveResultInner has no free local register."
            }
            // The offset calls name p0 and v0 in the plain invoke form, which reaches v15 and no
            // further. A frame that puts p0 past it would assemble into nothing readable.
            check(registers <= 16) {
                "Fit video to the screen: saveResultInner holds $registers registers, and the " +
                    "plain invoke that hands the result over cannot name one past v15."
            }
            // The fields are written by name, and smali resolves a field it cannot find at run
            // time rather than here, as a NoSuchFieldError in the feed. Refuse that now.
            val fields = SaveAdaptionResultFingerprint.originalClassDef.fields
            for ((name, type) in listOf(
                "width" to "I", "height" to "I",
                "translateX" to "Ljava/lang/Float;", "translateY" to "Ljava/lang/Float;",
            )) {
                check(fields.any { it.name == name && it.type == type }) {
                    "Fit video to the screen: VideoAdaptionResult has no field $name:$type."
                }
            }
            // p0 is the result, p1 the video view. The width is asked for first because that is
            // where the fit is decided; the height that goes with it and the offsets are then
            // read back rather than worked out again from a width already changed.
            addInstructions(
                0,
                """
                    invoke-static/range { p0 .. p1 }, ${EXTENSION}->fitWidthFor(Ljava/lang/Object;Landroid/view/View;)I
                    move-result v0
                    iput v0, p0, $result->width:I
                    invoke-static/range { p0 .. p0 }, ${EXTENSION}->fittedHeightFor(Ljava/lang/Object;)I
                    move-result v0
                    iput v0, p0, $result->height:I
                    iget-object v0, p0, $result->translateX:Ljava/lang/Float;
                    invoke-static { p0, v0 }, ${EXTENSION}->fittedTranslation(Ljava/lang/Object;Ljava/lang/Float;)Ljava/lang/Float;
                    move-result-object v0
                    iput-object v0, p0, $result->translateX:Ljava/lang/Float;
                    iget-object v0, p0, $result->translateY:Ljava/lang/Float;
                    invoke-static { p0, v0 }, ${EXTENSION}->fittedTranslation(Ljava/lang/Object;Ljava/lang/Float;)Ljava/lang/Float;
                    move-result-object v0
                    iput-object v0, p0, $result->translateY:Ljava/lang/Float;
                """,
            )
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableVideoFit()V",
        )
    }
}
