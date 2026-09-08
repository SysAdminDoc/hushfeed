package app.morphe.patches.tiktok.interaction.cleardisplay

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/cleardisplay/RememberClearDisplayPatch;"

@Suppress("unused")
val rememberClearDisplayPatch = bytecodePatch(
    name = "Remember clear display",
    description = "Remembers clear display between videos, or enters it automatically after a chosen delay.",
    default = true,
) {
    dependsOn(settingsPatch, sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok4623())
    execute {
        // Entering clear display by itself must not look to TikTok like the reader asked for
        // it, so the events its own code sends are stopped. Two of the three were written as bare
        // signatures, both had stopped matching anything on 46.2.3, and both call sites were
        // methodOrNull, so the patch skipped them and still reported Applied. They are found
        // inside the logging class now, which the one surviving signature identifies.
        val stateMethod = ClearModeLogStateFingerprint.method
        val loggerClass = mutableClassDefBy(stateMethod.definingClass)
        stateMethod.returnEarly()

        fun suppress(event: String, parameterPrefix: List<String>) {
            val matches = loggerClass.methods.filter { candidate ->
                candidate.returnType == "V" &&
                    AccessFlags.STATIC.isSet(candidate.accessFlags) &&
                    candidate.parameters.map { it.type }.take(parameterPrefix.size) == parameterPrefix
            }
            if (matches.isEmpty()) {
                throw PatchException(
                    "Remember clear display: ${stateMethod.definingClass} no longer logs the " +
                        "$event event, so entering clear display would be reported as a tap.",
                )
            }
            matches.forEach { it.returnEarly() }
        }

        suppress(
            "clear mode change",
            listOf(
                "Z",
                "Ljava/lang/String;",
                "Ljava/lang/String;",
                "Lcom/ss/android/ugc/aweme/feed/model/Aweme;",
                "Ljava/lang/String;",
                "J",
                "I",
            ),
        )
        suppress(
            "clear mode playtime",
            listOf("F", "I", "J", "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"),
        )
        val eventMethod = OnClearDisplayEventFingerprint.method
        val eventClass = eventMethod.parameters[0].type
        val frameMethod = OnRenderFirstFrameBodyFingerprint.method
        val awemeGetter = frameMethod.implementation!!.instructions.mapNotNull {
            it.getReference<MethodReference>()
        }.filter {
            it.definingClass == frameMethod.definingClass && it.parameterTypes.isEmpty() &&
                it.returnType == "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
        }.distinctBy { it.toString() }.single()
        val extension = mutableClassDefBy(EXTENSION)
        val postOriginal = extension.methods.single { it.name == "postClear" }
        val post = postOriginal.cloneMutable(additionalRegisters = 5)
        extension.methods.remove(postOriginal)
        extension.methods.add(post)
        post.addInstructions(0, """
            new-instance v0, $eventClass
            move/from16 v1, p0
            const/4 v2, 0x0
            const-string v3, ""
            const-string v4, "long_press"
            invoke-direct { v0, v1, v2, v3, v4 }, $eventClass-><init>(ZILjava/lang/String;Ljava/lang/String;)V
            invoke-virtual { v0 }, $eventClass->post()Lcom/ss/android/ugc/governance/eventbus/IEvent;
            return-void
        """)
        val readOriginal = extension.methods.single { it.name == "readCurrentAweme" }
        val read = readOriginal.cloneMutable(additionalRegisters = 1)
        extension.methods.remove(readOriginal)
        extension.methods.add(read)
        read.addInstructions(0, """
            check-cast p0, @OWNER@
            invoke-virtual/range { p0 .. p0 }, $awemeGetter
            move-result-object v0
            return-object v0
        """.replace("@OWNER@", frameMethod.definingClass))
        eventMethod.addInstruction(0,
            "invoke-static/range { p1 .. p1 }, $EXTENSION->rememberClearDisplayEvent(Ljava/lang/Object;)V")
        frameMethod.addInstruction(0,
            "invoke-static/range { p0 .. p0 }, $EXTENSION->onFirstFrame(Ljava/lang/Object;)V")
        SettingsStatusLoadFingerprint.method.addInstruction(0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableAutomaticClearDisplay()V")
    }
}
