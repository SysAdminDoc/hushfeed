package app.morphe.patches.tiktok.misc.spoof.sim

import app.morphe.patcher.Fingerprint

/**
 * The startup task that notices the SIM has changed and reports it.
 *
 * <p>Read out of the 46.2.3 fixture on 2026-09-09. `run` is gated on the `enable_sim_utils` AB
 * key, reads `TelephonyManager.getSimState`, builds the current SIM identity through
 * `LX/0Nif;->LIZ`, compares it with the one stored in Keva under `last_sim_info`, stores the new
 * one, and if the two differ reads `SubscriptionManager.getActiveSubscriptionInfoCount` (index 82)
 * and sends the event `ttac_sim_change_detect` carrying `sim_change_type`, `duration`, `n_sim`,
 * `prev_sim_country_code`, `prev_carrier`, `curr_sim_country_code` and `curr_carrier`. That event
 * is all it does: nothing else in the app reads `last_sim_info`, so the task can be skipped
 * whole.
 *
 * <p>The country and carrier it reports come from `LX/1AbB;->LLIIZ` and `LLILII`, which wrap
 * `TelephonyManager.getSimCountryIso` and `getSimOperatorName`, so the spoof already reaches them.
 * The subscription count does not go through TikTok's telephony wrappers at all.
 */
internal object CheckSimChangeTaskFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("last_sim_info", "last_check_time", "ttac_sim_change_detect"),
    custom = { method, classDef ->
        classDef.type == "Lcom/ss/android/ugc/aweme/legoImpl/task/CheckSIMChangeTask;" &&
            method.name == "run"
    },
)
