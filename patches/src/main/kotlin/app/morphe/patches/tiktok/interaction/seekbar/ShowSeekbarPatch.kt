/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/seekbar/ShowSeekbarPatch.kt
 */
package app.morphe.patches.tiktok.interaction.seekbar

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.tiktok.misc.absettings.hookAppAbIntBoundary
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/seekbar/SeekbarPatch;"

@Suppress("unused")
val showSeekbarPatch = bytecodePatch(
    name = "Show the progress bar",
    description = "Shows TikTok's native video seekbar where it would normally be hidden.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableShowSeekbar()V",
        )

        // This target is TikTok's short predicate used by the feed progress UI.
        ShouldShowProgressBarFingerprint.method.addInstructions(
            0,
            """
                if-eqz p0, :show_seekbar_original
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->isEnabled()Z
                move-result v0
                if-eqz v0, :show_seekbar_original
                const/4 v0, 0x1
                return v0
                :show_seekbar_original
            """,
        )

        SetSeekBarShowTypeFingerprint.method.apply {
            // The last parameter, which the fingerprint holds to being the int. An int takes one
            // register, so it is the top of the frame; range form, because that can sit past v15.
            val typeRegister = implementation!!.registerCount - 1
            addInstructions(
                0,
                """
                    invoke-static/range {v$typeRegister .. v$typeRegister}, $EXTENSION_CLASS_DESCRIPTOR->overrideSeekbarShowType(I)I
                    move-result v$typeRegister
                """,
            )
        }
    }
}

@Suppress("unused")
val showSeekbarThumbnailPatch = bytecodePatch(
    name = "Show the progress bar thumbnail",
    description = "Shows TikTok's video preview thumbnail while dragging the seekbar.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSeekbarThumbnail()V",
        )
        hookAppAbIntBoundary(
            EXTENSION_CLASS_DESCRIPTOR,
            "overrideThumbnailGate",
        )
    }
}
