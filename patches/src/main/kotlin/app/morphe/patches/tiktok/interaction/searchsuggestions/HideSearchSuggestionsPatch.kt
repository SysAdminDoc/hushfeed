/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.searchsuggestions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.numberOfParameterRegisters

private const val EXTENSION = "Lapp/morphe/extension/tiktok/search/SearchSuggestions;"
private const val SUGGEST_WORDS_VIEW_MODEL =
    "Lcom/ss/android/ugc/aweme/search/pages/middlepage/core/viewmodel/SuggestWordsViewModel;"

/**
 * Whether the search page preloads its suggested words at all. A real name on a real class, and
 * a config gate the app already expects to be false, so refusing here is a path it handles.
 */
private object IntermediatePreloadEnableFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/search/pages/middlepage/core/utils/IntermediatePreload;",
    name = "enable",
    parameters = listOf("Landroid/os/Bundle;"),
    returnType = "Z",
)

/**
 * The three methods `SuggestWordsViewModel` hands its words to. Both fetches on that class, the
 * plain one and the suspending one, funnel into these, so cutting them covers whatever asked.
 * Their names are obfuscated; on this class the shape is unambiguous, because the two fetches
 * take five and six parameters and everything else that returns void takes one or two. One of
 * the three is static, so the guard is written without touching any parameter register.
 */
private object SuggestWordsPublishFingerprint : Fingerprint(
    definingClass = SUGGEST_WORDS_VIEW_MODEL,
    returnType = "V",
    custom = { method, _ ->
        method.name != "<init>" &&
            method.name != "<clinit>" &&
            method.parameterTypes.size in 1..2 &&
            method.parameterTypes.any { it.startsWith("L") }
    },
)

/**
 * Stops the search page filling itself with things to search for. TikTok asks for suggested
 * words before you have typed anything and shows them as "You might be interested in" and
 * "Popular searches"; with this on it neither asks nor shows. What you searched for before is
 * a different list and is left alone.
 */
@Suppress("unused")
val hideSearchSuggestionsPatch = bytecodePatch(
    name = "Hide search suggestions",
    description = "Hides the suggested searches TikTok offers on the search page before you " +
        "type. Your own search history is left alone. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableHideSearchSuggestions()V",
        )

        IntermediatePreloadEnableFingerprint.method.apply {
            check(implementation!!.registerCount - numberOfParameterRegisters >= 1) {
                "Hide search suggestions: the preload gate has no free local register."
            }
            addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION->shouldHide()Z
                    move-result v0
                    if-eqz v0, :morphe_keep_suggestions
                    const/4 v0, 0x0
                    return v0
                    :morphe_keep_suggestions
                    nop
                """,
            )
        }

        val publishes = SuggestWordsPublishFingerprint.matchAll()
            .map { it.method }
            .filter { it.implementation != null }
        if (publishes.isEmpty()) {
            throw PatchException("Hide search suggestions: no publish method on the view model.")
        }
        publishes.forEach { method ->
            if (method.implementation!!.registerCount - method.numberOfParameterRegisters < 1) {
                throw PatchException(
                    "Hide search suggestions: ${method.name} has no free local register.",
                )
            }
            method.addInstructionsWithLabels(
                0,
                """
                    invoke-static {}, $EXTENSION->shouldHide()Z
                    move-result v0
                    if-eqz v0, :morphe_keep_suggestions
                    return-void
                    :morphe_keep_suggestions
                    nop
                """,
            )
        }
    }
}
