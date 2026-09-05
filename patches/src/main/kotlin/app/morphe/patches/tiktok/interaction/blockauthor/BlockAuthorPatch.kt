/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/blockauthor/BlockAuthorPatch;"

private const val VIDEO_ITEM_PARAMS_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;"

@Suppress("unused")
val blockAuthorPatch = bytecodePatch(
    name = "Block author button",
    description = "Adds a block button to the video player that blocks the account that posted the " +
        "current video in one tap, with an undo action. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableBlockAuthor()V",
        )

        // Track the author of whichever video is currently on screen.
        val trackerMethod = VideoAuthorInfoParamsFingerprint.method
        val paramsRegister = trackerMethod.registerOfParameter(VIDEO_ITEM_PARAMS_DESCRIPTOR)
            ?: error("Could not locate the VideoItemParams parameter on paramSync2StateAccept")

        // Must be invoke-static/range. Parameter registers sit at the top of the frame, so
        // on a method with a large frame this register is well above v15, which the plain
        // invoke-static (format 35c) cannot encode. The smali assembler drops the whole
        // method when that happens, and the patcher then fails with "Collection is empty".
        trackerMethod.addInstruction(
            0,
            "invoke-static/range { $paramsRegister .. $paramsRegister }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->setCurrentVideoParams(Ljava/lang/Object;)V",
        )

        // Assert the block endpoint still looks the way the extension expects. The
        // extension calls it by reflection, so without this the patch would install a
        // button that silently fails on a build that reshaped the API.
        BlockServiceFingerprint.method
    }
}

/**
 * Resolves the smali register holding the parameter of [descriptor].
 *
 * Wide parameters occupy two registers, so the offset cannot be derived from the
 * parameter index alone.
 */
private fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.registerOfParameter(
    descriptor: String,
): String? {
    var register = if (accessFlags and AccessFlags.STATIC.value != 0) 0 else 1

    for (parameterType in parameterTypes) {
        val type = parameterType.toString()
        if (type == descriptor) return "p$register"
        register += if (type == "J" || type == "D") 2 else 1
    }

    return null
}
