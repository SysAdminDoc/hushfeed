package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

private const val VIDEO = "Lcom/ss/android/ugc/aweme/feed/model/Video;"
private const val URL = "Lcom/ss/android/ugc/aweme/base/model/UrlModel;"
private const val EXTENSION = "Lapp/morphe/extension/tiktok/download/"
private object DownloadAddressFingerprint : Fingerprint(
    definingClass = VIDEO, name = "getDownloadAddr", parameters = emptyList(), returnType = URL,
)
private object CleanDownloadAddressFingerprint : Fingerprint(
    definingClass = VIDEO, name = "getDownloadNoWatermarkAddr", parameters = emptyList(), returnType = URL,
)
/**
 * The profile fetch. Real class and method names, and the app reads the user out of it every
 * time a profile is opened, which is what tells the extension whose picture is on screen.
 */
private object ProfileUserResponseFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/profile/UserResponse;",
    name = "getUser",
    parameters = emptyList(),
    returnType = "Lcom/ss/android/ugc/aweme/profile/model/User;",
)

/**
 * The profile header's avatar. `IHeaderAvatarAbility` declares exactly one method and the
 * base component implements it, so the shape finds it without naming the obfuscated method.
 */
private object ProfileAvatarBindFingerprint : Fingerprint(
    definingClass = "/ProfileHeaderAvatarBaseComponent;",
    parameters = listOf("I", "Landroid/view/View;", "Ljava/lang/String;"),
    returnType = "V",
    // p2 is the View only on an instance method; on a static one it would be the String.
    custom = { method, _ -> method.accessFlags and AccessFlags.STATIC.value == 0 },
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
    dependsOn(sharedExtensionPatch)
    execute {
        listOf(DownloadAddressFingerprint, CleanDownloadAddressFingerprint).forEach { fingerprint ->
            fingerprint.method.apply {
                check(implementation!!.registerCount > 1)
                addInstructionsWithLabels(0, """
                    invoke-static/range { p0 .. p0 }, ${EXTENSION}QualitySelector;->download(Ljava/lang/Object;)$URL
                    move-result-object v0
                    if-eqz v0, :original
                    return-object v0
                """, ExternalLabel("original", getInstruction(0)))
            }
        }
        StartDownloadFingerprint.method.apply {
            check(implementation!!.registerCount > parameterTypes.size + 1)
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

        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAdvancedDownloads()V")
    }
}
