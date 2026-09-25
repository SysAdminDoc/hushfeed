/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.blockauthor

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.inbox.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.requireLocals
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/blockauthor/BlockAuthorPatch;"

private const val VIDEO_ITEM_PARAMS_DESCRIPTOR =
    "Lcom/ss/android/ugc/aweme/feed/model/VideoItemParams;"

@Suppress("unused")
val blockAuthorPatch = bytecodePatch(
    name = "Block author button",
    description = "Adds one-tap controls for blocking the creator, hiding them locally and " +
        "blocking the current sound. A confirmed account block skips to the next video and " +
        "shows a small Unblock button at the top left for two seconds. " +
        "The local-hide and sound controls have separate switches. " +
        "Long press any visible control to move it. Hushfeed keeps it clear of system bars, " +
        "cutouts and TikTok's bottom tabs when the window changes. A local action shows Undo " +
        "only after its setting was saved. All controls hide while comments are open. Switch: " +
        "Hushfeed settings > Feed filter.",
    default = false,
) {
    category("Interaction")
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4703())

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

        // TikTok's daily screen-time reminder coming up over the feed, for the switch that
        // leaves the app instead. After the invoke-super: the method opens with a null-context
        // early return whose branch target the insert must not sit in front of, and past the
        // super call the slot is really attached.
        val reminderMethod = DailyScreenTimeReminderFingerprint.method
        val reminderSuperIndex = reminderMethod.indexOfFirstInstructionOrThrow {
            opcode == Opcode.INVOKE_SUPER
        }
        reminderMethod.addInstruction(
            reminderSuperIndex + 1,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/wellbeing/RestReminder;->reminderShown()V",
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
        // selects among the bound items. p1 is that id; p0 also lets the session hold
        // pause the owning player without retaining a global player instance.
        PlayerProgressAidFingerprint.method.capturePlayingAweme()

        // A replacement native activity can take focus from the hold itself. Observe its
        // actual grant and loss so release can distinguish that from an external audio owner.
        // The helper and its listener are found by their shape; their names were once written
        // here and were the first thing a newer build renamed.
        val nativeFocus = resolveNativeFocus()
        nativeFocus.request.captureNativeFocusRequest()
        nativeFocus.abandon.captureNativeFocusAbandon()
        nativeFocus.change.captureNativeFocusChange()

        // The hold's pause and resume, and the video it checks it is pausing. Their names
        // change with every build, so they are read off TikTok's own pauseVideo and the For You
        // feed's space-key toggle and written into the hold's three bridge methods.
        installNativePlayback(resolveNativePlayback())

        // Assert the block endpoint still looks the way the extension expects. The
        // extension calls it by reflection, so without this the patch would install a
        // button that silently fails on a build that reshaped the API.
        BlockServiceFingerprint.method

        val pager = mutableClassDefBy("Lcom/ss/android/ugc/aweme/common/widget/VerticalViewPager;")
        validateBlockPager(pager.methods)

        val detail = mutableClassDefBy("Lcom/ss/android/ugc/aweme/detail/ui/DetailPageFragment;")
        val visibility = "Lapp/morphe/extension/tiktok/blockauthor/FeedVisibility;"
        listOf(
            Triple("onViewCreated", listOf("Landroid/view/View;", "Landroid/os/Bundle;"), "onDetailView(Ljava/lang/Object;Landroid/view/View;)V"),
            Triple("onResume", emptyList(), "onDetailResume(Ljava/lang/Object;)V"),
            Triple("onPause", emptyList(), "onDetailPause(Ljava/lang/Object;)V"),
            Triple("onDestroyView", emptyList(), "onDetailDestroyed(Ljava/lang/Object;)V"),
            Triple("setUserVisibleHint", listOf("Z"), "onDetailVisibility(Ljava/lang/Object;Z)V"),
        ).forEach { (name, parameters, callback) ->
            val candidates = detail.methods.filter { it.name == name && it.parameterTypes == parameters }
            check(candidates.size == 1) {
                "Block author: expected DetailPageFragment to declare one $name$parameters, " +
                    "found ${candidates.size}. A lifecycle method it no longer overrides is " +
                    "inherited, and there is nothing on the class to hook."
            }
            val method = candidates.single()
            val endRegister = if (parameters.isEmpty()) "p0" else "p1"
            method.addInstruction(0, "invoke-static/range { p0 .. $endRegister }, $visibility->$callback")
        }

        // A video the reader paused stays paused when the app comes back. TikTok plays it again
        // through PlayerController's play method as the app returns, from the feed panel's resume
        // and again from the video's new surface, so the answer is asked at its start. A play it
        // turns down answers the way TikTok's own casting check does, with an empty string.
        PlayerPlayFingerprint.method.apply {
            requireLocals("Block author", 1)
            val aweme = "p${awemeParameterRegister()}"
            addInstructionsWithLabels(
                0,
                """
                    invoke-static/range { $aweme .. $aweme }, Lapp/morphe/extension/tiktok/playback/KeepPaused;->refusePlay(Ljava/lang/Object;)Z
                    move-result v0
                    if-eqz v0, :play
                    const-string v0, ""
                    return-object v0
                """,
                ExternalLabel("play", getInstruction(0)),
            )
        }
    }
}

/**
 * The p-register of the one Aweme parameter: past `this` in p0 when the method has one, and past
 * every parameter before it, wide ones twice.
 */
internal fun com.android.tools.smali.dexlib2.iface.Method.awemeParameterRegister(): Int {
    val parameters = parameterTypes.map(CharSequence::toString)
    val index = parameters.indexOf(PLAYED_AWEME)
    check(index >= 0) { "Block author: the play method takes no Aweme." }
    val receiver = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return receiver + parameters.take(index).sumOf { if (it == "J" || it == "D") 2 else 1 }
}

internal fun validateBlockPager(methods: Iterable<com.android.tools.smali.dexlib2.iface.Method>) {
    listOf(
        Triple("getCurrentItem", emptyList(), "I"),
        Triple("getScrollState", emptyList(), "I"),
        Triple("setCurrentItem", listOf("I"), "V"),
        Triple("canScrollVertically", listOf("I"), "Z"),
    ).forEach { (name, parameters, result) ->
        check(methods.count { it.name == name && it.parameterTypes == parameters &&
            it.returnType == result && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
            !AccessFlags.STATIC.isSet(it.accessFlags) && it.implementation != null } == 1) {
            "Block author: native pager contract changed: $name$parameters$result"
        }
    }
}

internal fun MutableMethod.capturePlayingAweme() {
    addInstruction(
        0,
        "invoke-static/range { p0 .. p1 }, " +
            "$EXTENSION_CLASS_DESCRIPTOR->setPlayingAweme(Ljava/lang/Object;Ljava/lang/String;)V",
    )
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
