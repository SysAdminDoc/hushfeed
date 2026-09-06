package app.morphe.patches.tiktok.interaction.captions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.blockAuthorPatch
import app.morphe.patches.tiktok.interaction.downloads.advancedDownloadsPatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/captions/"
private object CaptionViewFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/autocaption/refactor/CLACaptionAssemV2;",
    name = "onViewCreated", parameters = listOf("Landroid/view/View;"), returnType = "V",
)
private object ClearTransitionFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/feed/platform/panel/clearmode/ClearModePanelComponent;",
    strings = listOf("triggerImmersiveClearMode   isClean: "),
    parameters = listOf("Lcom/ss/android/ugc/aweme/feed/model/Aweme;", "Z"), returnType = "V",
)

@Suppress("unused")
val subtitleToolsPatch = bytecodePatch(
    name = "Subtitle tools",
    description = "Saves subtitle files beside downloaded videos and adds caption size, background, and clear-display options.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(sharedExtensionPatch, advancedDownloadsPatch, blockAuthorPatch)
    execute {
        val rendererConstructor = CaptionViewFingerprint.method.implementation!!.instructions.mapNotNull {
            it.getReference<MethodReference>()
        }.filter {
            it.name == "<init>" && it.parameterTypes.size == 4 &&
                it.parameterTypes.first() == "Landroid/widget/FrameLayout;" &&
                it.parameterTypes.last() == "Ljava/lang/String;"
        }.distinctBy { it.toString() }.single()
        val renderer = mutableClassDefBy(rendererConstructor.definingClass)
        val root = renderer.fields.single { it.type == "Landroid/widget/FrameLayout;" }
        val render = renderer.methods.single {
            it.returnType == "V" && it.parameterTypes.size == 4 &&
                it.parameterTypes[0] == "Ljava/lang/String;" &&
                it.parameterTypes[2] == "Ljava/lang/String;" && it.parameterTypes[3] == "Z"
        }
        check(mutableClassDefBy(render.parameterTypes[1].toString()).fields.any { it.name == "EXPANDED" })
        val layoutSetter = render.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .filter { it.name == "setTextLayout" && it.parameterTypes == listOf("Landroid/text/Layout;") }
            .distinctBy { it.toString() }.single()
        mutableClassDefBy(layoutSetter.definingClass).methods.single {
            it.name == layoutSetter.name && it.parameterTypes == layoutSetter.parameterTypes
        }.addInstructions(0, """
            invoke-static/range { p1 .. p1 }, ${EXTENSION}CaptionStyle;->layout(Landroid/text/Layout;)Landroid/text/Layout;
            move-result-object p1
        """)
        render.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }.reversed().forEach { index ->
                render.addInstruction(index,
                    "invoke-static/range { p0 .. p4 }, ${EXTENSION}CaptionTools;->onCaption(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Z)V")
            }
        val extension = mutableClassDefBy("${EXTENSION}CaptionTools;")
        val original = extension.methods.single { it.name == "rootOf" }
        val bridge = original.cloneMutable(additionalRegisters = 1)
        extension.methods.remove(original)
        extension.methods.add(bridge)
        bridge.addInstructions(0, """
            check-cast p0, ${renderer.type}
            iget-object v0, p0, ${renderer.type}->${root.name}:${root.type}
            return-object v0
        """)
        ClearTransitionFingerprint.method.addInstruction(0,
            "invoke-static/range { p1 .. p2 }, ${EXTENSION}CaptionTools;->onClear(Ljava/lang/Object;Z)V")
        // Assert the download metadata fields exist on this target.
        check(mutableClassDefBy("Lcom/ss/android/ugc/aweme/feed/model/CaptionModel;").fields.any { it.name == "captionList" })
        val metadata = mutableClassDefBy("Lcom/ss/android/ugc/aweme/feed/model/CaptionItemModel;")
        listOf("format", "url", "urlList", "languageCode", "languageName", "isOriginalCaption").forEach { name ->
            check(metadata.fields.any { it.name == name }) { "Missing caption field: $name" }
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSubtitleTools()V")
    }
}
