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
 * <p>It was `LX/0BIZ;`. On 46.2.3 and 46.7.3 it is the only static `(int)boolean`
 * `VideoQuickCommentAssem` calls, but on 46.8.3 neither that class nor any of its other callers
 * calls one at all, so who asks does not name it either. What does is the settings key it reads,
 * which sits behind a lambda R8 merged into a shared group.
 */
internal fun BytecodePatchContext.resolveQuickCommentReactionGate(): MutableMethod =
    resolveLazyAbGate("Hide quick comment reactions", QUICK_COMMENT_KEY) { method ->
        method.returnType == "Z" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("I") &&
            method.isLazyAbRead()
    }
