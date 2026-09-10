package app.morphe.patches.tiktok.interaction.captions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.interaction.blockauthor.blockAuthorPatch
import app.morphe.patches.tiktok.interaction.downloads.advancedDownloadsPatch
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
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
    dependsOn(settingsPatch, sharedExtensionPatch, advancedDownloadsPatch, blockAuthorPatch)
    execute {
        val rendererConstructor = CaptionViewFingerprint.method.implementation!!.instructions.mapNotNull {
            it.getReference<MethodReference>()
        }.filter {
            it.name == "<init>" && it.parameterTypes.size == 4 &&
                it.parameterTypes.first() == "Landroid/widget/FrameLayout;" &&
                it.parameterTypes.last() == "Ljava/lang/String;"
        }.distinctBy { it.toString() }.singleOrNull()
            ?: throw PatchException(
                "Subtitle tools: expected one caption renderer constructor taking a FrameLayout " +
                    "first and a String last, called from ${CaptionViewFingerprint.method.name}.",
            )
        val renderer = mutableClassDefBy(rendererConstructor.definingClass)
        val root = renderer.fields.singleOrNull { it.type == "Landroid/widget/FrameLayout;" }
            ?: throw PatchException(
                "Subtitle tools: ${renderer.type} does not hold exactly one FrameLayout to read " +
                    "the caption root from.",
            )
        // The fourth parameter is not read. It was a boolean on 46.2.3 and 46.7.3 and is an
        // edit-hint enum (NONE, TAP_TO_EDIT, EDIT_CTA, EDIT_CTA_HIGHLIGHTED) on 46.8.3, so
        // pinning it to Z found nothing there. The hook takes the first four registers and the
        // extension never wanted the fifth.
        val render = renderer.methods.singleOrNull {
            it.returnType == "V" && it.parameterTypes.size == 4 &&
                it.parameterTypes[0] == "Ljava/lang/String;" &&
                it.parameterTypes[2] == "Ljava/lang/String;"
        } ?: throw PatchException(
            "Subtitle tools: ${renderer.type} has no (String, ?, String, ?)V caption render.",
        )
        check(mutableClassDefBy(render.parameterTypes[1].toString()).fields.any { it.name == "EXPANDED" }) {
            "Subtitle tools: ${render.parameterTypes[1]} has no EXPANDED field, so it is not the " +
                "caption state this reads."
        }
        val layoutSetter = render.implementation!!.instructions.mapNotNull { it.getReference<MethodReference>() }
            .filter { it.name == "setTextLayout" && it.parameterTypes == listOf("Landroid/text/Layout;") }
            .distinctBy { it.toString() }.singleOrNull()
            ?: throw PatchException(
                "Subtitle tools: the caption render does not call exactly one " +
                    "setTextLayout(Layout).",
            )
        (mutableClassDefBy(layoutSetter.definingClass).methods.singleOrNull {
            it.name == layoutSetter.name && it.parameterTypes == layoutSetter.parameterTypes
        } ?: throw PatchException(
            "Subtitle tools: ${layoutSetter.definingClass} has no ${layoutSetter.name} to take " +
                "the caption layout through.",
        )).addInstructions(0, """
            invoke-static/range { p1 .. p1 }, ${EXTENSION}CaptionStyle;->layout(Landroid/text/Layout;)Landroid/text/Layout;
            move-result-object p1
        """)
        render.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }.reversed().forEach { index ->
                render.addInstruction(index,
                    "invoke-static/range { p0 .. p3 }, ${EXTENSION}CaptionTools;->onCaption(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;)V")
            }
        val extension = mutableClassDefBy("${EXTENSION}CaptionTools;")
        val original = extension.methods.singleOrNull { it.name == "rootOf" }
            ?: throw PatchException("Subtitle tools: the extension has no rootOf to rewrite.")
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
        check(mutableClassDefBy("Lcom/ss/android/ugc/aweme/feed/model/CaptionModel;").fields.any { it.name == "captionList" }) {
            "Subtitle tools: CaptionModel has no captionList field to download from."
        }
        val metadata = mutableClassDefBy("Lcom/ss/android/ugc/aweme/feed/model/CaptionItemModel;")
        listOf("format", "url", "urlList", "languageCode", "languageName", "isOriginalCaption").forEach { name ->
            check(metadata.fields.any { it.name == name }) { "Missing caption field: $name" }
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSubtitleTools()V")
    }
}
