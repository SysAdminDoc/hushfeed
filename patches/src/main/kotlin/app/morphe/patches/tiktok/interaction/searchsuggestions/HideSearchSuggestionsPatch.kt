/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.searchsuggestions

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.patches.tiktok.shared.callThroughLocals
import app.morphe.patches.tiktok.shared.objectIn
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

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
 * This covers ViewModel responses; the separate Lynx cache and visibility inputs are handled
 * below because they can populate the page without publishing through this LiveData.
 */
private object SuggestWordsPublishFingerprint : Fingerprint(
    definingClass = SUGGEST_WORDS_VIEW_MODEL,
    returnType = "V",
    custom = { method, _ -> method.marks("recommendPostValueStart") },
)

/** The AB key the first-screen gate reads, which its own class holds in the clear. */
private const val FIRST_SCREEN_KEY = "search_lynx_recommend_first_screen"

/**
 * The gate the first-screen cache consults, both from its raw JSON reader and its TemplateData
 * one, independently of the history gate and SuggestWordsViewModel.
 *
 * <p>Its class was written here as `LX/0OiL;`, which is `LX/0Or3;` on 46.7.3 and `LX/0Ol1;` on
 * 46.8.3. Unlike most of TikTok's AB gates it reads its key in its own `<clinit>` rather than
 * behind a lambda, and exactly one `<clinit>` in the app holds that key on all three builds.
 */
private object LynxFirstScreenCacheFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = emptyList(),
    returnType = "Z",
    custom = { _, classDef ->
        classDef.methods.any { candidate ->
            candidate.name == "<clinit>" &&
                candidate.implementation?.instructions?.any {
                    it.getReference<StringReference>()?.string == FIRST_SCREEN_KEY
                } == true
        }
    },
)

/**
 * The Lynx parameter helper, `LX/0Bav;` on 46.2.3 and `LX/09iY;` and `LX/09eO;` since.
 *
 * <p>Four statics together name it: the reader this hooks, the two that answer with a name and a
 * map, and one that writes a value into a gson object. That last signature is what makes the set
 * specific, and exactly one class in the app carries all four on each of the three builds.
 */
private val LYNX_PARAMETER_SHAPES = setOf(
    "Ljava/lang/String;[]",
    "Ljava/util/Map;[]",
    "V[Lcom/google/gson/n;, Ljava/lang/Object;, Ljava/lang/String;]",
)

private object LynxParameterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Ljava/lang/String;"),
    returnType = "Ljava/lang/Object;",
    custom = { _, classDef ->
        val statics = classDef.methods
            .filter { AccessFlags.STATIC.value and it.accessFlags != 0 }
            .mapTo(HashSet()) { "${it.returnType}${it.parameterTypes}" }
        statics.containsAll(LYNX_PARAMETER_SHAPES)
    },
)

private object SearchReentryFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/search/middle/DynamicSingleIntermediateFragmentNew;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    strings = listOf("show_suggest_search_words", "searchTransferEnter"),
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
        "",
    default = false,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
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
        LynxFirstScreenCacheFingerprint.method.hookFirstScreenCache()
        LynxParameterFingerprint.method.hookInitialLynxParameter()
        SearchReentryFingerprint.method.hookReentryVisibility()
    }
}

internal fun MutableMethod.hookFirstScreenCache() {
    implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN }
        .map { it.index }.asReversed().forEach { index ->
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            // Native branches target this return directly. Replacing it keeps their target
            // on the hook; inserting before it would leave those branches skipping the call.
            replaceInstruction(
                index,
                "invoke-static/range {v$register .. v$register}, $EXTENSION->filterCachedSuggestions(Z)Z",
            )
            addInstructions(index + 1, "move-result v$register\nreturn v$register")
        }
}

internal fun MutableMethod.hookInitialLynxParameter() {
    if (!AccessFlags.STATIC.isSet(accessFlags)) {
        throw PatchException("Hide search suggestions: the Lynx parameter reader is not static.")
    }
    implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
        .map { it.index }.asReversed().forEach { index ->
            val register = getInstruction<OneRegisterInstruction>(index).registerA
            val call = callThroughLocals(
                "Hide search suggestions", "invoke-static",
                "$EXTENSION->filterLynxParameter(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;",
                objectIn("p0"), objectIn("v$register"),
            )
            addInstructions(index, "$call\nmove-result-object v$register")
        }
}

internal fun MutableMethod.hookReentryVisibility() {
    val instructions = implementation!!.instructions.toList()
    val keyIndex = instructions.indices.singleOrNull {
        instructions[it].getReference<StringReference>()?.string == "show_suggest_search_words"
    } ?: throw PatchException("Hide search suggestions: search reentry has no unique visibility key.")
    val keyRegister = (instructions[keyIndex] as OneRegisterInstruction).registerA
    val read = instructions.getOrNull(keyIndex + 1)
    val reference = read?.getReference<MethodReference>()
    val result = instructions.getOrNull(keyIndex + 2)
    // The settings reader was named here as LX/0BZD;->LJIIJJI, both of which R8 assigns. Its
    // signature is what identifies it at this call site: two ints, the key this method just
    // loaded, a boolean, and an int back.
    if (read !is FiveRegisterInstruction || read.registerCount != 5 || read.registerF != keyRegister ||
        reference == null || reference.returnType != "I" ||
        reference.parameterTypes.map(CharSequence::toString) !=
        listOf("I", "I", "Ljava/lang/String;", "Z") ||
        result?.opcode != Opcode.MOVE_RESULT
    ) {
        throw PatchException("Hide search suggestions: search reentry visibility reader changed.")
    }
    val register = (result as OneRegisterInstruction).registerA
    // This is in the live native frame. A one-register range call needs no scratch register.
    addInstructions(
        keyIndex + 3,
        "invoke-static/range {v$register .. v$register}, $EXTENSION->filterReentryVisibility(I)I\n" +
            "move-result v$register",
    )
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
