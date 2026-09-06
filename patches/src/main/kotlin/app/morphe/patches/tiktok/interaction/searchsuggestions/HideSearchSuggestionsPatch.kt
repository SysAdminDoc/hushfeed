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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/search/SearchSuggestions;"
private const val SUGGEST_WORDS_VIEW_MODEL =
    "Lcom/ss/android/ugc/aweme/search/pages/middlepage/core/viewmodel/SuggestWordsViewModel;"
private const val SEARCH_MIDDLE_MONITOR =
    "Lcom/ss/android/ugc/aweme/search/performance/metrics/SearchMiddleMonitor;"

/** Whether the method calls the named method on the search page's own timing monitor. */
private fun Method.marks(monitorMethod: String) =
    implementation?.instructions?.any { instruction ->
        instruction.getReference<MethodReference>()?.let { reference ->
            reference.definingClass == SEARCH_MIDDLE_MONITOR && reference.name == monitorMethod
        } == true
    } == true

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
 * The request for suggested words. Its name is obfuscated but it opens by starting the search
 * page's own timer, `SearchMiddleMonitor.recommendRequestStart`, which is a real name on a real
 * class and says exactly what the method is for. Nothing else on the view model calls it.
 */
private object SuggestWordsRequestFingerprint : Fingerprint(
    definingClass = SUGGEST_WORDS_VIEW_MODEL,
    returnType = "V",
    custom = { method, _ -> method.marks("recommendRequestStart") },
)

/**
 * The hand-off to the screen. Same idea: it opens with
 * `SearchMiddleMonitor.recommendPostValueStart` and then posts to the view model's live data.
 * Guarding this as well as the request means anything that fetched by another route still shows
 * nothing.
 */
private object SuggestWordsPublishFingerprint : Fingerprint(
    definingClass = SUGGEST_WORDS_VIEW_MODEL,
    returnType = "V",
    custom = { method, _ -> method.marks("recommendPostValueStart") },
)

/**
 * Stops the search page filling itself with things to search for. TikTok asks for suggested
 * words before you have typed anything and shows them as "You might be interested in" and
 * "Popular searches"; with this on the page does not ask and does not show. What you searched
 * for before is a different list and is left alone.
 */
@Suppress("unused")
val hideSearchSuggestionsPatch = bytecodePatch(
    name = "Hide search suggestions",
    description = "Hides the suggested searches TikTok offers on the search page before you " +
        "type, and stops the page asking for them. Your own search history is left alone. " +
        "Supports TikTok 46.2.3.",
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

        IntermediatePreloadEnableFingerprint.method.guard("const/4 v0, 0x0\n                    return v0")
        SuggestWordsRequestFingerprint.method.guard("return-void")
        SuggestWordsPublishFingerprint.method.guard("return-void")
    }
}

/** Leaves the method before it does anything, when the extension says the feature is on. */
private fun MutableMethod.guard(returnInstruction: String) {
    if (implementation!!.registerCount - numberOfParameterRegisters < 1) {
        throw PatchException("Hide search suggestions: $name has no free local register.")
    }
    addInstructionsWithLabels(
        0,
        """
            invoke-static {}, $EXTENSION->shouldHide()Z
            move-result v0
            if-eqz v0, :morphe_keep_suggestions
            $returnInstruction
            :morphe_keep_suggestions
            nop
        """,
    )
}
