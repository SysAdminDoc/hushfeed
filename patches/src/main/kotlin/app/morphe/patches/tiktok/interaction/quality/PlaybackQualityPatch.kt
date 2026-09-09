package app.morphe.patches.tiktok.interaction.quality

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION = "Lapp/morphe/extension/tiktok/playback/PlaybackQuality;"
private class QualityGetter(owner: String, getter: String, result: String) : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/feed/model/$owner;",
    name = getter, parameters = emptyList(), returnType = result,
)

@Suppress("unused")
val playbackQualityPatch = bytecodePatch(
    name = "Playback quality",
    description = "Selects the lowest, highest or a target video quality for playback, adaptive " +
        "streams included. A second choice caps quality on mobile data and only ever lowers " +
        "it. Download quality has its own setting.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(settingsPatch, sharedExtensionPatch)
    execute {
        listOf("Video", "VideoUrlModel").forEach { owner ->
            val prefix = if (owner == "Video") "getVideoModel" else "getDashVideoModel"
            listOf(
                Triple("getBitRate", "Ljava/util/List;", "filter"),
                Triple(prefix + "Str", "Ljava/lang/String;", "filterJson"),
                Triple(prefix + "Map", "Ljava/util/Map;", "filterMap"),
            ).plus(if (owner == "Video") listOf(Triple("getVideoModelObject", "Ljava/lang/Object;", "cacheModel")) else emptyList())
                .forEach { (getter, type, callback) ->
                QualityGetter(owner, getter, type).method.apply {
                    findInstructionIndicesReversedOrThrow { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
                        val register = getInstruction<OneRegisterInstruction>(index).registerA
                        addInstructions(index, """
                            invoke-static/range { v$register .. v$register }, $EXTENSION->$callback($type)$type
                            move-result-object v$register
                        """)
                    }
                }
            }
        }
        // Keep raw gears intact so the native JSON/map cache and downloads retain every variant.
        Fingerprint(
            definingClass = "Lcom/ss/android/ugc/aweme/feed/model/Video;", name = "setVideoModelObject",
            parameters = listOf("Ljava/lang/Object;"), returnType = "V",
        ).method.addInstructions(0, """
            invoke-static/range { p1 .. p1 }, $EXTENSION->cacheModel(Ljava/lang/Object;)Ljava/lang/Object;
            move-result-object p1
        """)
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enablePlaybackQuality()V")
    }
}
