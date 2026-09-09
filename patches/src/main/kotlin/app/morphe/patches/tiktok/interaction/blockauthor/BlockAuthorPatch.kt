/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.inbox.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/blockauthor/BlockAuthorPatch;"

private const val VIDEO_ITEM_PARAMS_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;"

@Suppress("unused")
val blockAuthorPatch = bytecodePatch(
    name = "Block author button",
    description = "Adds a block button to the video player that blocks the account that posted the " +
        "current video in one tap, with an undo action.",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableBlockAuthor()V",
        )

        // The activity, so the pause switches can follow the app going away and coming back.
        // p0 is the activity, and /range because a parameter register is usually above v15.
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "Lapp/morphe/extension/tiktok/playback/PausePlayback;->" +
                "install(Landroid/app/Activity;)V",
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

        // A bind is not "this video is on screen": the feed binds the items either side of
        // the current one before the user reaches them, so the tracker above would arm the
        // next creator. The player names the video that is actually playing, which is what
        // selects among the bound items. p1 is that id.
        PlayerProgressAidFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p1 .. p1 }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->setPlayingAweme(Ljava/lang/String;)V",
        )

        // Assert the block endpoint still looks the way the extension expects. The
        // extension calls it by reflection, so without this the patch would install a
        // button that silently fails on a build that reshaped the API.
        BlockServiceFingerprint.method

        val detail = mutableClassDefBy("Lcom/ss/android/ugc/aweme/detail/ui/DetailPageFragment;")
        val visibility = "Lapp/morphe/extension/tiktok/blockauthor/FeedVisibility;"
        listOf(
            Triple("onViewCreated", listOf("Landroid/view/View;", "Landroid/os/Bundle;"), "onDetailView(Ljava/lang/Object;Landroid/view/View;)V"),
            Triple("onResume", emptyList(), "onDetailResume(Ljava/lang/Object;)V"),
            Triple("onPause", emptyList(), "onDetailPause(Ljava/lang/Object;)V"),
            Triple("onDestroyView", emptyList(), "onDetailDestroyed(Ljava/lang/Object;)V"),
            Triple("setUserVisibleHint", listOf("Z"), "onDetailVisibility(Ljava/lang/Object;Z)V"),
        ).forEach { (name, parameters, callback) ->
            val method = detail.methods.single { it.name == name && it.parameterTypes == parameters }
            val endRegister = if (parameters.isEmpty()) "p0" else "p1"
            method.addInstruction(0, "invoke-static/range { p0 .. $endRegister }, $visibility->$callback")
        }
    }
}

/**
 * Resolves the smali register holding the parameter of [descriptor].
 *
 * Wide parameters occupy two registers, so the offset cannot be derived from the
 * parameter index alone.
 */
internal fun app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.registerOfParameter(
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
