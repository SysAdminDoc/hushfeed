/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.privacy

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.returnEarly

@Suppress("unused")
val aiProfilingGovernorPatch = bytecodePatch(
    name = "Stop on-device AI profiling",
    description = "Kills the Pitaya on-device ML inference engine at startup so it cannot build a behavioral profile. The AI asset strip in the core de-bloat patch removes the native libraries. This patch stops the initialization code that would download replacements.",
    default = false,
) {
    category("Privacy")
    compatibleWith(*AppCompatibilities.tiktok4703())

    execute {
        val initMethod = PitayaInitFingerprint.methodOrNull
        if (initMethod != null) {
            initMethod.returnEarly()
            println("[AI profiling governor] Killed Pitaya init (${initMethod.definingClass}->${initMethod.name}).")
        } else {
            println("[AI profiling governor] Pitaya init fingerprint did not match; the engine may not be present.")
        }
        val startMethod = PitayaCoreStartFingerprint.methodOrNull
        if (startMethod != null) {
            startMethod.returnEarly()
            println("[AI profiling governor] Killed PitayaCore start (${startMethod.definingClass}->${startMethod.name}).")
        }
    }
}
