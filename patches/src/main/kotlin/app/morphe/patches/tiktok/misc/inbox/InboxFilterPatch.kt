/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels as addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object InboxRowBindingFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I", "Ljava/lang/Object;"),
    strings = listOf("MultiBaseVH innerOnBind data type is not match!"),
)

private object InboxNoticeItemsFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/inbox/widget/multi/NoticeWidget;",
    name = "jp",
    parameters = emptyList(),
    returnType = "Ljava/util/List;",
)

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/inbox/InboxFilter;"
private const val CONTROLS_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/inbox/InboxControls;"

internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/main/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

@Suppress("unused")
val inboxFilterPatch = bytecodePatch(
    name = "Hide inbox items",
    description = "Adds a switch for each row and header control on the Inbox tab, so " +
        "message requests, TikTok Tako, TikTok Shop, the stories tray and the rest can be " +
        "hidden individually. Supports TikTok 46.2.3.",
    default = false,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        val binding = InboxRowBindingFingerprint.method
        check(binding.implementation!!.instructions.any { instruction ->
            instruction.getReference<FieldReference>()?.let {
                it.name == "itemView" && it.type == "Landroid/view/View;" &&
                    it.definingClass == "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;"
            } == true
        })
        binding.addInstruction(0,
            "invoke-static/range {p0 .. p2}, $EXTENSION_CLASS_DESCRIPTOR->onRowBound(Ljava/lang/Object;ILjava/lang/Object;)V")
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableInboxFilter()V",
        )

        // p0 is the activity. Uses invoke-static/range because a parameter register is
        // usually above v15, which the plain invoke-static cannot encode.
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, " +
                "$EXTENSION_CLASS_DESCRIPTOR->install(Landroid/app/Activity;)V",
        )

        // This list feeds both the classic and new entrance containers. Filter copies so
        // changing a switch can restore rows from the original view-model data.
        InboxNoticeItemsFingerprint.method.apply {
            implementation!!.instructions.indices.reversed()
                .filter { getInstruction(it).opcode == Opcode.RETURN_OBJECT }
                .forEach { index ->
                    val register = getInstruction<OneRegisterInstruction>(index).registerA
                    addInstructions(index, """
                        invoke-static/range { v$register .. v$register }, Lapp/morphe/extension/tiktok/inbox/InboxModelFilter;->filter(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$register
                    """)
                }
        }

        // Archive and Shop also have their own widget injectors.
        mapOf(
            ArchiveEntranceWidgetEnableFingerprint to "shouldShowArchiveEntrance",
            ShopEntranceWidgetEnableFingerprint to "shouldShowShopEntrance",
        ).forEach { (fingerprint, extensionMethodName) ->
            fingerprint.method.addInstructions(
                0,
                """
                    invoke-static {}, $CONTROLS_CLASS_DESCRIPTOR->$extensionMethodName()Z
                    move-result v0
                    if-nez v0, :morphe_show_inbox_entrance
                    const/4 v0, 0x0
                    return v0
                    :morphe_show_inbox_entrance
                    nop
                """,
            )
        }
    }
}
