package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags

private const val VIDEO = "Lcom/ss/android/ugc/aweme/feed/model/Video;"
private const val URL = "Lcom/ss/android/ugc/aweme/base/model/UrlModel;"
private const val EXTENSION = "Lapp/morphe/extension/tiktok/download/"
private const val AVATAR_LISTENER = "LY/ACListenerS198S0100000_24;"

internal fun MutableMethod.interceptProfileAvatarLongPress() {
    check(accessFlags and AccessFlags.STATIC.value != 0
        && parameterTypes.map(CharSequence::toString) == listOf(definingClass, "Landroid/view/View;")
        && returnType == "V") { "Advanced downloads: unexpected avatar callback signature." }
    val registers = implementation!!.registerCount
    check(registers - numberOfParameterRegisters >= 1 && registers <= 16) {
        "Advanced downloads: avatar callback registers no longer fit the native gesture hook."
    }
    addInstructionsWithLabels(0, """
        iget-object v0, p0, $definingClass->l0:Ljava/lang/Object;
        invoke-static { v0, p1 }, ${EXTENSION}ProfileAvatarSaver;->onAvatarLongPress(Ljava/lang/Object;Landroid/view/View;)Z
        move-result v0
        if-eqz v0, :native_avatar_hold
        return-void
    """, ExternalLabel("native_avatar_hold", getInstruction(0)))
}

private object DownloadAddressFingerprint : Fingerprint(
    definingClass = VIDEO, name = "getDownloadAddr", parameters = emptyList(), returnType = URL,
)
private object CleanDownloadAddressFingerprint : Fingerprint(
    definingClass = VIDEO, name = "getDownloadNoWatermarkAddr", parameters = emptyList(), returnType = URL,
)
/**
 * Legacy profile fetch. Modern avatar gestures resolve the user from their own component.
 */
private object ProfileUserResponseFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/profile/UserResponse;",
    name = "getUser",
    parameters = emptyList(),
    returnType = "Lcom/ss/android/ugc/aweme/profile/model/User;",
)

/**
 * Older header insertion callback. Modern avatar gestures have separate native listeners below.
 */
private object ProfileAvatarBindFingerprint : Fingerprint(
    definingClass = "/ProfileHeaderAvatarBaseComponent;",
    parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/String;"),
    returnType = "V",
    // p2 is the View only on an instance method; on a static one it would be the String.
    custom = { method, _ -> method.accessFlags and AccessFlags.STATIC.value == 0 },
)

private object OwnProfileAvatarLongPressFingerprint : Fingerprint(
    definingClass = AVATAR_LISTENER,
    name = "onClick\$43",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf(AVATAR_LISTENER, "Landroid/view/View;"),
    returnType = "V",
    strings = listOf("photo", "video"),
)

private object OtherProfileAvatarLongPressFingerprint : Fingerprint(
    definingClass = AVATAR_LISTENER,
    name = "onClick\$46",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf(AVATAR_LISTENER, "Landroid/view/View;"),
    returnType = "V",
    strings = listOf("long_press", "long_hold_head"),
)

private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
private const val STORY_PLAY_AREA = "/StoryImmersivePlayAreaComponent;"

/** The story's own view, which is what a press and hold lands on. */
private object StoryPlayAreaViewFingerprint : Fingerprint(
    definingClass = STORY_PLAY_AREA,
    name = "onViewCreated",
    parameters = listOf("Landroid/view/View;"),
    returnType = "V",
)

/**
 * The play area is handed a story and the position it belongs to. Two methods take that shape,
 * both obfuscated, and both are hooked. The component is passed along with the story because the
 * viewer binds the pages either side of the one on screen: without knowing which play area was
 * told, a press would save whichever story was bound last.
 */
private object StoryPlayAreaBindFingerprint : Fingerprint(
    definingClass = STORY_PLAY_AREA,
    parameters = listOf("I", AWEME),
    returnType = "V",
    custom = { method, _ -> method.accessFlags and AccessFlags.STATIC.value == 0 },
)

private object StartDownloadFingerprint : Fingerprint(
    strings = listOf("download_method", "download_action"),
    parameters = listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;", "Landroid/content/Context;", "I", "Ljava/lang/String;", "Z", "Lcom/ss/android/ugc/aweme/sharer/model/SharePackage;"),
    returnType = "V",
)

@Suppress("unused")
val advancedDownloadsPatch = bytecodePatch(
    name = "Advanced downloads",
    description = "Adds download quality choices, saves Photo Mode images directly from their source URLs, keeps a video's sound as its own audio file, and saves a profile picture or a story from a long press.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(settingsPatch, sharedExtensionPatch)
    execute {
        listOf(DownloadAddressFingerprint, CleanDownloadAddressFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                check(implementation!!.registerCount - numberOfParameterRegisters >= 1) {
                    "Advanced downloads: ${fingerprint.method.name} has no free local register."
                }
                addInstructionsWithLabels(0, """
                    invoke-static/range { p0 .. p0 }, ${EXTENSION}QualitySelector;->download(Ljava/lang/Object;)$URL
                    move-result-object v0
                    if-eqz v0, :original
                    return-object v0
                """, ExternalLabel("original", getInstruction(0)))
            }
        }
        StartDownloadFingerprint.method.apply {
            check(implementation!!.registerCount - numberOfParameterRegisters >= 1) {
                "Advanced downloads: the photo download start has no free local register."
            }
            addInstructionsWithLabels(0, """
                invoke-static/range { p1 .. p2 }, ${EXTENSION}OriginalPhotos;->start(Ljava/lang/Object;Landroid/content/Context;)Z
                move-result v0
                if-eqz v0, :original
                return-void
            """, ExternalLabel("original", getInstruction(0)))
        }
        ProfileUserResponseFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, ${EXTENSION}ProfileAvatarSaver;->" +
                "recordProfileResponse(Ljava/lang/Object;)V",
        )
        ProfileAvatarBindFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p2 .. p2 }, ${EXTENSION}ProfileAvatarSaver;->" +
                "attachAvatar(Landroid/view/View;)V",
        )
        listOf(OwnProfileAvatarLongPressFingerprint, OtherProfileAvatarLongPressFingerprint).forEach {
            it.method.interceptProfileAvatarLongPress()
        }

        StoryPlayAreaViewFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p1 }, ${EXTENSION}StoryDownloads;->" +
                "attachPlayArea(Ljava/lang/Object;Landroid/view/View;)V",
        )
        StoryPlayAreaBindFingerprint.matchAll()
            .map { it.method }
            .filter { it.implementation != null }
            .forEach { method ->
                method.addInstruction(
                    0,
                    "invoke-static/range { p0 .. p2 }, ${EXTENSION}StoryDownloads;->" +
                        "recordStory(Ljava/lang/Object;ILjava/lang/Object;)V",
                )
            }

        StoryLongPressFingerprint.method.interceptStoryLongPress()

        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAdvancedDownloads()V")
    }
}
