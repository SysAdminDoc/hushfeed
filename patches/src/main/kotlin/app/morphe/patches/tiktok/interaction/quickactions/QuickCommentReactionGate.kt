/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.tiktok.shared.isLazyAbRead
import app.morphe.patches.tiktok.shared.resolveLazyAbGate

/** The setting the quick comment reactions are shown behind. */
private const val QUICK_COMMENT_KEY = "comment_hide_quick_emoji_research"

/**
 * The gate that decides whether a comment box shows its row of quick reactions.
 *
 * <p>It was `LX/0BIZ;`. What names it on 46.2.3 and 46.7.3 is the settings key its lazily read
 * value reads, which sits behind a lambda R8 merged into a shared group. On 46.8.3 that key is in
 * no dex at all: the experiment was removed rather than renamed, so this resolves nothing there
 * and the patch fails by name, which is the right answer until the row's new switch is found.
 */
internal fun BytecodePatchContext.resolveQuickCommentReactionGate(): MutableMethod =
    resolveLazyAbGate("Hide quick comment reactions", QUICK_COMMENT_KEY) { method ->
        method.returnType == "Z" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("I") &&
            method.isLazyAbRead()
    }
