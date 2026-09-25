/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.interaction.systemfont

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/font/SystemFont;"
private const val TYPEFACE = "Landroid/graphics/Typeface;"

/** The variable-font build: `([FontVariationAxis, weight, italic) -> Typeface`. */
private const val VARIATION_SHAPE = "[Landroid/graphics/fonts/FontVariationAxis;IZ"

/** The static asset build: `(String path) -> Typeface`. */
private const val ASSET_SHAPE = "Ljava/lang/String;"

/** The variable font's asset name, present in every engine implementation's pool. */
private const val ENGINE_MARKER = "font/TikTokSans-VF.otf"

@Suppress("unused")
val systemFontPatch = bytecodePatch(
    name = "Use system font",
    description = "Draws TikTok's text in your device's font instead of TikTok Sans. The icons, " +
        "the gift animations and the @ and # glyphs keep their own fonts. Off by default. " +
        "Restart after changing. Switch: Hushfeed settings > App.",
    default = false,
) {
    category("Performance")
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSystemFont()V",
        )

        // TikTok's text font engine builds every interface typeface, and its two producing
        // methods are the variable-font builder and the static asset loader. The class holds no
        // name a patch can carry, so it is found by having both method shapes and naming the
        // variable font. Both implementations (the modern and the pre-26 fallback) match, which
        // is what we want: the running build uses one of them. The icon, gift and mention fonts
        // load elsewhere and never reach this engine.
        val engines = mutableListOf<ClassDef>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/extension/")) return@classDefForEach
            val hasVariation = classDef.methods.any { it.engineShape() == VARIATION_SHAPE }
            val hasAsset = classDef.methods.any { it.engineShape() == ASSET_SHAPE }
            if (!hasVariation || !hasAsset) return@classDefForEach
            val namesTheFont = classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    instruction.getReference<StringReference>()?.string == ENGINE_MARKER
                } == true
            }
            if (namesTheFont) engines += classDef
        }
        if (engines.isEmpty()) {
            throw PatchException(
                "Use system font: no font engine class found (a class building a Typeface from " +
                    "font variation axes and from an asset path, naming $ENGINE_MARKER).",
            )
        }

        engines.forEach { engine ->
            val mutableEngine = mutableClassDefBy(engine)
            engine.methods
                .filter { it.engineShape() == VARIATION_SHAPE || it.engineShape() == ASSET_SHAPE }
                .forEach { method ->
                    mutableEngine.methods
                        .first { it.name == method.name && it.parameterTypes == method.parameterTypes }
                        .systemizeReturns()
                }
        }
    }
}

/** The parameter signature of an engine build method that returns a Typeface, else null-ish. */
private fun Method.engineShape(): String? {
    if (returnType != TYPEFACE) return null
    return parameterTypes.joinToString("").takeIf { it == VARIATION_SHAPE || it == ASSET_SHAPE }
}

/** Sends every returned Typeface through the extension, which swaps it when the switch is on. */
private fun MutableMethod.systemizeReturns() {
    findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        addInstructionsAtControlFlowLabel(
            index,
            """
                invoke-static/range {v$register .. v$register}, $EXTENSION_CLASS_DESCRIPTOR->systemize($TYPEFACE)$TYPEFACE
                move-result-object v$register
            """,
        )
    }
}
