package app.morphe.patches.tiktok.misc.foldable

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags

private const val EXTENSION = "Lapp/morphe/extension/tiktok/foldable/FoldableSplitView;"
private object LiveCheck : Fingerprint(
    strings = listOf("isOptCommentSplit"), returnType = "Z",
    parameters = listOf("Landroid/app/Activity;", "Landroid/content/res/Configuration;"),
)
private object ContainerCheck : Fingerprint(
    strings = listOf("isOptSplitContainer"), returnType = "Z", parameters = emptyList(),
)

@Suppress("unused")
val foldableSplitViewPatch = bytecodePatch(
    name = "Foldable split comment view",
    description = "Shows comments beside the video on windows wider than a configurable threshold. Off by default.",
    default = false,
) {
    compatibleWith(*AppCompatibilities.tiktok4623())
    dependsOn(sharedExtensionPatch, settingsPatch)
    execute {
        listOf(LiveCheck.method to true, ContainerCheck.method to false).forEach { (method, live) ->
            check(AccessFlags.STATIC.isSet(method.accessFlags))
            check(method.implementation!!.registerCount - method.numberOfParameterRegisters >= 1) {
                "Split check needs a local register"
            }
            val call = if (live) "invoke-static/range { p0 .. p1 }, $EXTENSION->shouldForce(Landroid/app/Activity;Landroid/content/res/Configuration;)Z"
                else "invoke-static {}, $EXTENSION->shouldForceContainer()Z"
            method.addInstructions(0, """
                $call
                move-result v0
                if-eqz v0, :stock_split_check
                return v0
                :stock_split_check
                nop
            """)
        }
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableFoldableSplitView()V")
    }
}
